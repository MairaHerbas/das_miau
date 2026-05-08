package com.das.miau;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "BUS_DEBUG";
    private static final String DB_NAME = "transporte_bizkaia.db";
    private static final int DB_VERSION = 1;
    private final Context context;
    private SQLiteDatabase database;

    // Clase auxiliar para guardar paradas con su distancia
    public static class NearbyStop {
        BusStop stop;
        float distance;

        NearbyStop(BusStop stop, float distance) {
            this.stop = stop;
            this.distance = distance;
        }
    }

    // Clase auxiliar para evaluar combinaciones de paradas
    private static class CandidateConnection {
        BusStop originStop;
        BusStop destinationStop;
        float originDistance;
        float destinationDistance;
        float totalDistance;

        CandidateConnection(BusStop originStop, BusStop destinationStop, float originDistance, float destinationDistance) {
            this.originStop = originStop;
            this.destinationStop = destinationStop;
            this.originDistance = originDistance;
            this.destinationDistance = destinationDistance;
            this.totalDistance = originDistance + destinationDistance;
        }
    }

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        this.context = context;
        copiarBaseDeDatosSiNoExiste();
    }

    private void copiarBaseDeDatosSiNoExiste() {
        File dbFile = context.getDatabasePath(DB_NAME);
        if (!dbFile.exists()) {
            dbFile.getParentFile().mkdirs();
            try {
                InputStream is = context.getAssets().open("databases/" + DB_NAME);
                OutputStream os = new FileOutputStream(dbFile);
                byte[] buffer = new byte[1024];
                int length;
                while ((length = is.read(buffer)) > 0) {
                    os.write(buffer, 0, length);
                }
                os.flush(); os.close(); is.close();
                Log.d(TAG, "Base de datos copiada con éxito.");
            } catch (Exception e) {
                Log.e(TAG, "Error al copiar base de datos", e);
            }
        }
    }

    public void openDatabase() {
        String dbPath = context.getDatabasePath(DB_NAME).getPath();
        if (database == null || !database.isOpen()) {
            database = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY);
        }
    }

    @Override
    public synchronized void close() {
        if (database != null) database.close();
        super.close();
    }

    @Override public void onCreate(SQLiteDatabase db) { }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

    private double parseDirtyCoordinate(String raw) {
        if (raw == null || raw.isEmpty()) return 0;
        boolean isNegative = raw.trim().startsWith("-");
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 0;

        try {
            String res;
            if (digits.startsWith("43") || digits.startsWith("42")) {
                res = digits.substring(0, 2) + "." + digits.substring(2);
            } else if (digits.startsWith("2") || digits.startsWith("3")) {
                res = (isNegative ? "-" : "") + digits.substring(0, 1) + "." + digits.substring(1);
            } else {
                res = raw.replaceFirst("\\.", "X").replace(".", "").replace("X", ".");
            }
            return Double.parseDouble(res);
        } catch (Exception e) {
            return 0;
        }
    }

    private String normalizeLine(String line) {
        if (line == null) return "";
        return line.trim().toUpperCase().replaceFirst("^0+", "");
    }

    //Detecta si una línea es de tipo Gautxori (servicio nocturno)
    public boolean isGautxori(String line) {
        if (line == null) return false;
        String n = normalizeLine(line);
        return n.startsWith("G");
    }

    //Detecta si el momento actual está dentro del horario operativo de Gautxori
    public boolean isNightBusTime() {
        Calendar now = Calendar.getInstance();
        int day = now.get(Calendar.DAY_OF_WEEK);
        int hour = now.get(Calendar.HOUR_OF_DAY);
        int minute = now.get(Calendar.MINUTE);
        if (day == Calendar.FRIDAY) {
            return hour >= 23;
        } else if (day == Calendar.SATURDAY) {
            if (hour < 2) return true;
            if (hour == 2 && minute <= 30) return true;
            return hour >= 23;
        } else if (day == Calendar.SUNDAY) {
            if (hour < 2) return true;
            return hour == 2 && minute <= 30;
        }
        return false;
    }

    // Convierte tiempo GTFS (HH:mm:ss) a segundos. Soporta > 24h
    private long getGtfSeconds(String gtfsTime) {
        if (gtfsTime == null) return -1;
        try {
            String[] parts = gtfsTime.split(":");
            if (parts.length < 2) return -1;
            int h = Integer.parseInt(parts[0].trim());
            int m = Integer.parseInt(parts[1].trim());
            int s = parts.length > 2 ? Integer.parseInt(parts[2].trim()) : 0;
            return h * 3600L + m * 60L + s;
        } catch (Exception e) {
            return -1;
        }
    }

    // Obtiene el próximo horario válido GTFS para una conexión
    // Implementa normalización de tiempos y validación de dirección (stop_sequence)
    private long[] getTripTiming(String originStopId, String destinationStopId, String line, String network, long minDepartureTime, long currentSecs) {
        openDatabase();
        String tableSuffix = network.equalsIgnoreCase("bizkaibus") ? "_bizkaibus_limpio" : "_bilbobus_limpio";

        // El SQL ya filtra por dirección correcta usando stop_sequence
        String sql = "SELECT st1.departure_time, st2.arrival_time " +
                "FROM stop_times" + tableSuffix + " st1 " +
                "JOIN stop_times" + tableSuffix + " st2 ON st1.trip_id = st2.trip_id " +
                "JOIN trips" + tableSuffix + " t ON st1.trip_id = t.trip_id " +
                "JOIN routes" + tableSuffix + " r ON t.route_id = r.route_id " +
                "WHERE st1.stop_id = ? " +
                "AND st2.stop_id = ? " +
                "AND st1.stop_sequence < st2.stop_sequence " +
                "AND r.route_short_name = ? ";

        long bestDep = -1;
        long bestArr = -1;
        long minWait = Long.MAX_VALUE;

        try (Cursor cursor = database.rawQuery(sql, new String[]{originStopId, destinationStopId, line})) {
            while (cursor.moveToNext()) {
                long depSecs = getGtfSeconds(cursor.getString(0));
                long arrSecs = getGtfSeconds(cursor.getString(1));

                if (depSecs == -1 || arrSecs == -1) continue;

                // NORMALIZACIÓN GTFS
                if (depSecs < currentSecs) {
                    depSecs += 86400;
                    arrSecs += 86400;
                }

                // Asegurar que la llegada es siempre posterior a la salida
                if (arrSecs < depSecs) arrSecs += 86400;

                long waitTime = depSecs - minDepartureTime;
                
                // Buscar la próxima salida válida
                if (waitTime >= 0 && waitTime < minWait) {
                    minWait = waitTime;
                    bestDep = depSecs;
                    bestArr = arrSecs;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error en getTripTiming", e);
        }
        
        if (bestDep != -1) {
            return new long[]{bestDep, bestArr};
        }
        return null;
    }

    //Busca paradas cercanas y las devuelve ordenadas por distancia
    public List<NearbyStop> getNearbyStops(double lat, double lon) {
        List<NearbyStop> stops = new ArrayList<>();
        openDatabase();

        Log.d(TAG, ">>> Escaneando paradas cerca de: " + lat + ", " + lon);

        String query = "SELECT stop_id, stop_name, stop_lat, stop_lon, 'bilbobus' FROM stops_bilbobus_limpio " +
                "UNION ALL " +
                "SELECT stop_id, stop_name, stop_lat, stop_lon, 'bizkaibus' FROM stops_bizkaibus_limpio";

        Cursor cursor = database.rawQuery(query, null);

        if (cursor.moveToFirst()) {
            do {
                String id = cursor.getString(0);
                String name = cursor.getString(1);
                double sLat = parseDirtyCoordinate(cursor.getString(2));
                double sLon = parseDirtyCoordinate(cursor.getString(3));
                String red = cursor.getString(4);

                float[] dist = new float[1];
                Location.distanceBetween(lat, lon, sLat, sLon, dist);

                if (dist[0] < 500) {
                    BusStop stop = new BusStop(id, name, sLat, sLon);
                    stop.setNetwork(red); // Guardar la red para validaciones posteriores
                    stop.setLines(getLinesForStop(id, red));
                    stops.add(new NearbyStop(stop, dist[0]));
                    Log.d(TAG, "   [CANDIDATA] " + name + " (" + red + ") a " + (int)dist[0] + "m. Líneas: " + stop.getLines());
                }
            } while (cursor.moveToNext());
        }
        cursor.close();

        Collections.sort(stops, new Comparator<NearbyStop>() {
            @Override
            public int compare(NearbyStop o1, NearbyStop o2) {
                return Float.compare(o1.distance, o2.distance);
            }
        });

        Log.d(TAG, "<<< Fin. Encontradas " + stops.size() + " en radio de 500m.");
        return stops;
    }

    public List<String> getLinesForStop(String stopId, String red) {
        List<String> lines = new ArrayList<>();
        String table = red.equals("bizkaibus") ? "_bizkaibus_limpio" : "_bilbobus_limpio";
        String sql = "SELECT DISTINCT r.route_short_name FROM routes" + table + " r " +
                "JOIN trips" + table + " t ON r.route_id = t.route_id " +
                "JOIN stop_times" + table + " st ON t.trip_id = st.trip_id " +
                "WHERE st.stop_id = ?";

        Cursor c = database.rawQuery(sql, new String[]{stopId});
        while (c.moveToNext()) lines.add(c.getString(0));
        c.close();
        return lines;
    }

    //Valida si un autobús va realmente del origen al destino comprobando stop_sequence en GTFS
    private boolean isCorrectDirection(String originStopId, String destinationStopId, String line, String red) {
        openDatabase();
        String table = red.equalsIgnoreCase("bizkaibus") ? "_bizkaibus_limpio" : "_bilbobus_limpio";

        // SQL que busca un trip_id donde el origen ocurra antes que el destino
        String sql = "SELECT st1.trip_id " +
                "FROM stop_times" + table + " st1 " +
                "JOIN stop_times" + table + " st2 ON st1.trip_id = st2.trip_id " +
                "JOIN trips" + table + " t ON st1.trip_id = t.trip_id " +
                "JOIN routes" + table + " r ON t.route_id = r.route_id " +
                "WHERE st1.stop_id = ? " +
                "AND st2.stop_id = ? " +
                "AND st1.stop_sequence < st2.stop_sequence " +
                "AND r.route_short_name = ? " +
                "LIMIT 1";

        try (Cursor cursor = database.rawQuery(sql, new String[]{originStopId, destinationStopId, line})) {
            boolean exists = cursor.moveToFirst();
            if (exists) {
                Log.d(TAG, "   [DIRECCIÓN OK] Línea " + line + " (" + red + "): " + originStopId + " -> " + destinationStopId);
            } else {
                Log.d(TAG, "   [DIRECCIÓN KO] Línea " + line + " (" + red + "): " + originStopId + " -> " + destinationStopId + " (No hay trip directo)");
            }
            return exists;
        } catch (Exception e) {
            Log.e(TAG, "Error validando dirección de línea", e);
            return false;
        }
    }

    public BusConnection findBusConnection(double oLat, double oLon, double dLat, double dLon, boolean allowNightBus) {
        Log.d(TAG, "========== BÚSQUEDA DE MEJOR CONEXIÓN (DIRECCIÓN + TIEMPO REAL) ==========");
        List<NearbyStop> oStops = getNearbyStops(oLat, oLon);
        List<NearbyStop> dStops = getNearbyStops(dLat, dLon);

        if (oStops.isEmpty() || dStops.isEmpty()) {
            Log.d(TAG, "No se hallaron paradas en el origen o destino.");
            return null;
        }

        List<CandidateConnection> pairs = new ArrayList<>();
        for (NearbyStop os : oStops) {
            for (NearbyStop ds : dStops) {
                pairs.add(new CandidateConnection(os.stop, ds.stop, os.distance, ds.distance));
            }
        }

        // Ordenar inicialmente por distancia geográfica total
        Collections.sort(pairs, new Comparator<CandidateConnection>() {
            @Override
            public int compare(CandidateConnection c1, CandidateConnection c2) {
                return Float.compare(c1.totalDistance, c2.totalDistance);
            }
        });

        // Clase local para evaluar el tiempo total de una ruta
        class RouteOption {
            BusConnection connection;
            double totalTime;
            RouteOption(BusConnection c, double t) { this.connection = c; this.totalTime = t; }
        }

        List<RouteOption> options = new ArrayList<>();
        Calendar now = Calendar.getInstance();
        long currentSecs = now.get(Calendar.HOUR_OF_DAY) * 3600L + now.get(Calendar.MINUTE) * 60L + now.get(Calendar.SECOND);
        double walkSpeed = 1.2; // m/s

        int candidatesChecked = 0;
        for (CandidateConnection cand : pairs) {
            // Solo evaluar máximo 15 combinaciones de paradas más cercanas
            if (candidatesChecked >= 15 && !options.isEmpty()) break;

            BusStop oStop = cand.originStop;
            BusStop dStop = cand.destinationStop;

            if (oStop.getNetwork() == null || !oStop.getNetwork().equals(dStop.getNetwork())) {
                continue;
            }

            for (String oL : oStop.getLines()) {
                String nO = normalizeLine(oL);
                if (nO.isEmpty() || (!allowNightBus && isGautxori(nO))) continue;

                // Solo considerar si la línea llega al destino
                if (dStop.getLines().contains(oL)) {
                    candidatesChecked++;
                    double walkToOrigin = cand.originDistance / walkSpeed;
                    double walkToDest = cand.destinationDistance / walkSpeed;

                    // Buscar el PRÓXIMO viaje real en GTFS (getTripTiming valida dirección y normaliza tiempo)
                    long[] timings = getTripTiming(oStop.getStopId(), dStop.getStopId(), oL, oStop.getNetwork(), (long)(currentSecs + walkToOrigin), currentSecs);

                    if (timings != null) {
                        long depSecs = timings[0];
                        long arrSecs = timings[1];

                        long arrivalAtStop = currentSecs + (long) walkToOrigin;
                        // waitTime = tiempo desde que llegas a la parada hasta que sale el bus
                        long waitTime = Math.max(0, depSecs - arrivalAtStop);
                        // rideTime = tiempo dentro del bus
                        long rideTime = arrSecs >= depSecs ? arrSecs - depSecs : arrSecs + 86400 - depSecs;
                        
                        // TOTAL_TIME = caminar_origen + espera + bus + caminar_destino
                        double totalTime = walkToOrigin + waitTime + rideTime + walkToDest;

                        Log.d(TAG, "   [GTFS TRIP CHECK] Línea " + oL + ": " + oStop.getStopName() + " -> " + dStop.getStopName());
                        Log.d(TAG, "   [NEXT DEPARTURE FOUND] " + (depSecs/3600) + ":" + String.format("%02d", (depSecs%3600)/60) + " (Espera: " + (waitTime/60) + " min)");
                        Log.d(TAG, "   [TOTAL TIME CALCULATED] " + (int)totalTime + "s");

                        options.add(new RouteOption(new BusConnection(oL, oStop, dStop), totalTime));
                    } else {
                        // Fallback: si no hay horarios pero la dirección es correcta
                        if (isCorrectDirection(oStop.getStopId(), dStop.getStopId(), oL, oStop.getNetwork())) {
                            // Penalizamos la falta de horario dándole un tiempo estimado alto basado en distancia
                            double estimatedTime = 1000000 + cand.totalDistance * 10;
                            options.add(new RouteOption(new BusConnection(oL, oStop, dStop), estimatedTime));
                            Log.d(TAG, "   [GTFS TRIP CHECK] Sin horario para " + oL + ". Usando fallback espacial.");
                        }
                    }
                }
            }
            // Limitar a las mejores opciones finales para comparar
            if (options.size() >= 5) break; 
        }

        if (options.isEmpty()) {
            Log.d(TAG, "========== SIN CONEXIÓN DIRECTA ENCONTRADA ==========");
            return null;
        }

        // SELECCIÓN DE MEJOR RUTA: Ranking por tiempo real (la que llega antes gana)
        Collections.sort(options, new Comparator<RouteOption>() {
            @Override
            public int compare(RouteOption o1, RouteOption o2) {
                return Double.compare(o1.totalTime, o2.totalTime);
            }
        });

        BusConnection best = options.get(0).connection;
        Log.d(TAG, "!!! CONEXIÓN ÉXITOSA !!! Mejor ruta: " + best.getLine() + " (Tiempo total est: " + (int)options.get(0).totalTime + "s)");
        return best;
    }

    public List<RutaBus> getLineasBilbobus() {
        List<RutaBus> lista = new ArrayList<>();
        openDatabase();
        Cursor c = database.rawQuery("SELECT route_id, route_short_name, route_long_name FROM routes_bilbobus_limpio", null);
        while (c.moveToNext()) lista.add(new RutaBus(c.getString(0), c.getString(1), c.getString(2)));
        c.close(); return lista;
    }

    public List<RutaBus> getLineasBizkaibus() {
        List<RutaBus> lista = new ArrayList<>();
        openDatabase();
        Cursor c = database.rawQuery("SELECT route_id, route_short_name, route_long_name FROM routes_bizkaibus_limpio", null);
        while (c.moveToNext()) lista.add(new RutaBus(c.getString(0), c.getString(1), c.getString(2)));
        c.close(); return lista;
    }
}
