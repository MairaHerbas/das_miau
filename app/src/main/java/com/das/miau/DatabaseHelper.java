package com.das.miau;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;
import android.util.Log;

import org.osmdroid.util.GeoPoint;

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

    //clase auxiliar para guardar paradas con su distancia
    public static class NearbyStop {
        BusStop stop;
        float distance;

        NearbyStop(BusStop stop, float distance) {
            this.stop = stop;
            this.distance = distance;
        }
    }

    //clase auxiliar para evaluar combinaciones de paradas
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
            //cambiamos a READ_WRITE para poder crear los índices
            database = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READWRITE);
            crearIndices(); //llamamos al optimizador
        }
    }
    private void crearIndices() {
        try {
            //estos índices hacen que buscar paradas y shapes pase de tardar segundos a milisegundos
            database.execSQL("CREATE INDEX IF NOT EXISTS idx_st_stop_bilbo ON stop_times_bilbobus_limpio(stop_id)");
            database.execSQL("CREATE INDEX IF NOT EXISTS idx_st_trip_bilbo ON stop_times_bilbobus_limpio(trip_id)");
            database.execSQL("CREATE INDEX IF NOT EXISTS idx_shape_bilbo ON shapes_bilbobus_limpio(shape_id)");

            database.execSQL("CREATE INDEX IF NOT EXISTS idx_st_stop_bizkai ON stop_times_bizkaibus_limpio(stop_id)");
            database.execSQL("CREATE INDEX IF NOT EXISTS idx_st_trip_bizkai ON stop_times_bizkaibus_limpio(trip_id)");
            database.execSQL("CREATE INDEX IF NOT EXISTS idx_shape_bizkai ON shapes_bizkaibus_limpio(shape_id)");
            Log.d(TAG, "Índices optimizados creados con éxito");
        } catch (Exception e) {
            Log.e(TAG, "Error creando índices", e);
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

    private long getGtfSeconds(String gtfsTime) { //conversion tiempo GTFS (HH:mm:ss) a segundos
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

    private long[] getTripTiming(String originStopId, String destinationStopId, String line, String network, long minDepartureTime, long currentSecs) {//obtener el próximo horario válido gtfs
        openDatabase();
        String tableSuffix = network.equalsIgnoreCase("bizkaibus") ? "_bizkaibus_limpio" : "_bilbobus_limpio";

        Calendar now = Calendar.getInstance();
        String[] days = {"sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"};
        String todayWeekday = days[now.get(Calendar.DAY_OF_WEEK) - 1];
        String todayDate = String.format("%04d%02d%02d", now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH));

        // SQL que filtra por dirección correcta y valida calendario GTFS (calendar + calendar_dates)
        String sql = "SELECT st1.departure_time, st2.arrival_time " +
                "FROM stop_times" + tableSuffix + " st1 " +
                "JOIN stop_times" + tableSuffix + " st2 ON st1.trip_id = st2.trip_id " +
                "JOIN trips" + tableSuffix + " t ON st1.trip_id = t.trip_id " +
                "JOIN routes" + tableSuffix + " r ON t.route_id = r.route_id " +
                "LEFT JOIN calendar" + tableSuffix + " c ON t.service_id = c.service_id " +
                "LEFT JOIN calendar_dates" + tableSuffix + " cd ON t.service_id = cd.service_id AND cd.date = ? " +
                "WHERE st1.stop_id = ? " +
                "AND st2.stop_id = ? " +
                "AND CAST(st1.stop_sequence AS INTEGER) < CAST(st2.stop_sequence AS INTEGER) " +
                "AND r.route_short_name = ? " +
                "AND (" +
                "  (cd.exception_type = '1') OR " +
                "  (c." + todayWeekday + " = '1' AND ? BETWEEN c.start_date AND c.end_date AND (cd.exception_type IS NULL OR cd.exception_type != '2'))" +
                ")";

        long bestDep = -1;
        long bestArr = -1;
        long minWait = Long.MAX_VALUE;
        long MAX_WAIT = 45 * 60; //45 min

        try (Cursor cursor = database.rawQuery(sql, new String[]{todayDate, originStopId, destinationStopId, line, todayDate})) {
            while (cursor.moveToNext()) {
                long depSecs = getGtfSeconds(cursor.getString(0));
                long arrSecs = getGtfSeconds(cursor.getString(1));

                if (depSecs == -1 || arrSecs == -1) continue;

                if (depSecs < minDepartureTime) {
                    continue;
                }

                //asegurar que la llegada es siempre después d la salida
                if (arrSecs < depSecs) arrSecs += 86400;

                long waitTime = depSecs - minDepartureTime;
                //buscar la próxima salida válida
                if (waitTime >= 0 && waitTime < MAX_WAIT && waitTime < minWait) {
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

    //obtener próximos buses
    public List<Long> getNextDepartures(String originStopId, String destinationStopId, String line, String network, long minDepartureTime) {
        openDatabase();
        String tableSuffix = network.equalsIgnoreCase("bizkaibus") ? "_bizkaibus_limpio" : "_bilbobus_limpio";
        Calendar now = Calendar.getInstance();
        long currentSecs = now.get(Calendar.HOUR_OF_DAY) * 3600L + now.get(Calendar.MINUTE) * 60L + now.get(Calendar.SECOND);
        String[] days = {"sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"};
        String todayWeekday = days[now.get(Calendar.DAY_OF_WEEK) - 1];
        String todayDate = String.format("%04d%02d%02d", now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH));

        String sql = "SELECT st1.departure_time " +
                "FROM stop_times" + tableSuffix + " st1 " +
                "JOIN stop_times" + tableSuffix + " st2 ON st1.trip_id = st2.trip_id " +
                "JOIN trips" + tableSuffix + " t ON st1.trip_id = t.trip_id " +
                "JOIN routes" + tableSuffix + " r ON t.route_id = r.route_id " +
                "LEFT JOIN calendar" + tableSuffix + " c ON t.service_id = c.service_id " +
                "LEFT JOIN calendar_dates" + tableSuffix + " cd ON t.service_id = cd.service_id AND cd.date = ? " +
                "WHERE st1.stop_id = ? " +
                "AND st2.stop_id = ? " +
                "AND CAST(st1.stop_sequence AS INTEGER) < CAST(st2.stop_sequence AS INTEGER) " +
                "AND r.route_short_name = ? " +
                "AND (" +
                "  (cd.exception_type = '1') OR " +
                "  (c." + todayWeekday + " = '1' AND ? BETWEEN c.start_date AND c.end_date AND (cd.exception_type IS NULL OR cd.exception_type != '2'))" +
                ") " +
                "ORDER BY st1.departure_time ASC";

        List<Long> departures = new ArrayList<>();
        try (Cursor cursor = database.rawQuery(sql, new String[]{todayDate, originStopId, destinationStopId, line, todayDate})) {
            while (cursor.moveToNext() && departures.size() < 3) {
                long depSecs = getGtfSeconds(cursor.getString(0));
                if (depSecs == -1) continue;
                if (depSecs < currentSecs) continue; 
                
                long waitMin = (depSecs - currentSecs) / 60;
                if (waitMin >= 0 && waitMin <= 45) {
                    departures.add(waitMin);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error en getNextDepartures", e);
        }
        return departures;
    }

    //busca paradas cercanas y las devuelve ordenadas por distancia
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

                if (sLat == 0 || sLon == 0) continue;

                float[] dist = new float[1];
                Location.distanceBetween(lat, lon, sLat, sLon, dist);

                if (dist[0] < 1000) {
                    BusStop stop = new BusStop(id, name, sLat, sLon);
                    stop.setNetwork(red);
                    stop.setLines(getLinesForStop(id, red));
                    stops.add(new NearbyStop(stop, dist[0]));
                }
            } while (cursor.moveToNext());
        }
        cursor.close();

        Collections.sort(stops, (o1, o2) -> Float.compare(o1.distance, o2.distance));
        Log.d(TAG, "<<< Fin. Encontradas " + stops.size() + " en radio de 1000m.");
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

    //valida si un autobús va realmente del origen al destino comprobando stop_sequence en GTFS
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
                "AND CAST(st1.stop_sequence AS INTEGER) < CAST(st2.stop_sequence AS INTEGER) " +
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

    public BusConnection findBusConnection(double oLat, double oLon, double dLat, double dLon) {
        Log.d(TAG, "========== BÚSQUEDA DE MEJOR CONEXIÓN (GTFS REAL) ==========");
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

        //ordenar inicialmente por distancia geográfica total
        Collections.sort(pairs, new Comparator<CandidateConnection>() {
            @Override
            public int compare(CandidateConnection c1, CandidateConnection c2) {
                return Float.compare(c1.totalDistance, c2.totalDistance);
            }
        });

        //clase local para evaluar el tiempo total de una ruta
        class RouteOption {
            BusConnection connection;
            double totalTime;
            RouteOption(BusConnection c, double t) { this.connection = c; this.totalTime = t; }
        }

        List<RouteOption> options = new ArrayList<>();
        Calendar now = Calendar.getInstance();
        long currentSecs = now.get(Calendar.HOUR_OF_DAY) * 3600L + now.get(Calendar.MINUTE) * 60L + now.get(Calendar.SECOND);
        double walkSpeed = 1.1; // m/s

        int candidatesChecked = 0;
        for (CandidateConnection cand : pairs) {
            //solo evaluar máximo 15 combinaciones de paradas más cercanas
            if (candidatesChecked >= 15 && !options.isEmpty()) break;

            BusStop oStop = cand.originStop;
            BusStop dStop = cand.destinationStop;

            if (oStop.getNetwork() == null || !oStop.getNetwork().equals(dStop.getNetwork())) {
                continue;
            }

            for (String oL : oStop.getLines()) {
                String nO = normalizeLine(oL);
                if (nO.isEmpty()) continue;

                //solo consideramos si la línea llega al destino
                if (dStop.getLines().contains(oL)) {
                    //validar sentido real
                    if (!isCorrectDirection(oStop.getStopId(), dStop.getStopId(), oL, oStop.getNetwork())) {
                        continue;
                    }
                    candidatesChecked++;
                    double walkToOrigin = cand.originDistance / walkSpeed;
                    double walkToDest = cand.destinationDistance / walkSpeed;
                    //buscar el próximo viaje real en GTFS (getTripTiming valida dirección, calendario y normaliza tiempo)
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
                        Log.d(TAG, "   [NEXT DEPARTURE FOUND] " + (depSecs/3600) + ":" + String.format("%02d", (depSecs%3600)/60) + " (Espera en la parada: " + (waitTime/60) + " min)");
                        Log.d(TAG, "   [TOTAL TIME] walkToOrigin: " + (walkToOrigin/60) + "min. waitTime: " + (waitTime/60) + "min. rideTime: " + (rideTime/60) + "min. walkToDest: " + (walkToDest/60) + "min.");
                        Log.d(TAG, "   [TOTAL TIME CALCULATED] " + (int)totalTime + "s = " + (int)(totalTime/60) + "min.");

                        BusConnection bc = new BusConnection(oL, oStop, dStop);
                        bc.setTotalTimeSec(totalTime);
                        
                        //guardar datos detallados para la UI
                        bc.setWalkToOriginSec(walkToOrigin);
                        bc.setWaitTimeSec(waitTime);
                        bc.setRideTimeSec(rideTime);
                        bc.setWalkToDestSec(walkToDest);
                        bc.setWalkOriginMeters(cand.originDistance);
                        bc.setWalkDestMeters(cand.destinationDistance);
                        bc.setNextDeparturesMin(getNextDepartures(oStop.getStopId(), dStop.getStopId(), oL, oStop.getNetwork(), (long)(currentSecs + walkToOrigin)));

                        options.add(new RouteOption(bc, totalTime));
                    }
                }
            }
            //limitar a las mejores opciones finales para comparar
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
        Log.d(TAG, "!!! CONEXIÓN ÉXITOSA !!! Mejor ruta: " + best.getLine() + ": " + best.getOriginStop().getStopName() + " -> " + best.getDestinationStop().getStopName() +" (Tiempo total est: " + (int)options.get(0).totalTime + "s = " + ((int)options.get(0).totalTime)/60 + "min)");
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

    // --- MÉTODOS PARA GTFS SHAPES ---

    public String getShapeIdForConnection(String originStopId, String destinationStopId, String line, String network) {
        openDatabase();
        String tableSuffix = network.equalsIgnoreCase("bizkaibus") ? "_bizkaibus_limpio" : "_bilbobus_limpio";

        String sql = "SELECT t.shape_id " +
                "FROM stop_times" + tableSuffix + " st1 " +
                "JOIN stop_times" + tableSuffix + " st2 ON st1.trip_id = st2.trip_id " +
                "JOIN trips" + tableSuffix + " t ON st1.trip_id = t.trip_id " +
                "JOIN routes" + tableSuffix + " r ON t.route_id = r.route_id " +
                "WHERE st1.stop_id = ? " +
                "AND st2.stop_id = ? " +
                "AND CAST(st1.stop_sequence AS INTEGER) < CAST(st2.stop_sequence AS INTEGER) " +
                "AND r.route_short_name = ? " +
                "ORDER BY (SELECT COUNT(*) FROM stop_times" + tableSuffix + " WHERE trip_id = t.trip_id) DESC " +
                "LIMIT 1";

        try (Cursor cursor = database.rawQuery(sql, new String[]{originStopId, destinationStopId, line})) {
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error en getShapeIdForConnection", e);
        }
        return null;
    }

    public List<GeoPoint> getShapePoints(String shapeId, String network) {
        List<GeoPoint> points = new ArrayList<>();
        if (shapeId == null) return points;
        openDatabase();
        String table = network.equalsIgnoreCase("bizkaibus") ? "shapes_bizkaibus_limpio" : "shapes_bilbobus_limpio";

        String sql = "SELECT shape_pt_lat, shape_pt_lon " +
                "FROM " + table + " " +
                "WHERE shape_id = ? " +
                "ORDER BY CAST(shape_pt_sequence AS INTEGER)";

        try (Cursor cursor = database.rawQuery(sql, new String[]{shapeId})) {
            while (cursor.moveToNext()) {
                double lat = parseDirtyCoordinate(cursor.getString(0));
                double lon = parseDirtyCoordinate(cursor.getString(1));

                if (lat == 0 || lon == 0) continue;

                points.add(new GeoPoint(lat, lon));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error en getShapePoints", e);
        }
        return points;
    }

    public List<GeoPoint> getTrimmedShape(String shapeId, BusStop origin, BusStop destination, String network) {
        if (shapeId == null) return null;
        List<GeoPoint> allPoints = getShapePoints(shapeId, network);
        if (allPoints == null || allPoints.size() < 2) return null;

        int startIndex = -1;
        int endIndex = -1;
        float minDistanceOrigin = 150; //radio máximo de 150m para considerar que "pasa por la parada"
        float minDistanceDest = 150;

        //encontrar el punto más cercano al origen en toda la lista
        for (int i = 0; i < allPoints.size(); i++) {
            float dist = getFastDistanceMeters(origin.getLat(), origin.getLon(), allPoints.get(i).getLatitude(), allPoints.get(i).getLongitude());            if (dist < minDistanceOrigin) {
                minDistanceOrigin = dist;
                startIndex = i;
            }
        }

        if (startIndex == -1) return null; //si no encontramos el origen cerca del shape, abortamos

        // Encontrar el punto más cercano al destino pero después del origen
        for (int i = startIndex; i < allPoints.size(); i++) {
            float dist = getDistance(destination.getLat(), destination.getLon(), allPoints.get(i));
            if (dist < minDistanceDest) {
                minDistanceDest = dist;
                endIndex = i;
            }
        }

        //si no encontramos destino después del origen, intentamos buscar el más cercano en toda la lista
        if (endIndex == -1) {
            for (int i = 0; i < startIndex; i++) {
                float dist = getDistance(destination.getLat(), destination.getLon(), allPoints.get(i));
                if (dist < minDistanceDest) {
                    minDistanceDest = dist;
                    endIndex = i;
                }
            }
        }

        if (startIndex != -1 && endIndex != -1) {
            if (startIndex < endIndex) {
                return new ArrayList<>(allPoints.subList(startIndex, endIndex + 1));
            } else {
                Log.w(TAG, "Shape direction reversed or circular for ID: " + shapeId);
                return new ArrayList<>(allPoints.subList(endIndex, startIndex + 1));
            }
        }

        return null;
    }

    //distancia rápida aprox
    private float getFastDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = lat1 - lat2;
        double dLon = lon1 - lon2;
        return (float) (Math.sqrt((dLat * dLat) + (dLon * dLon)) * 111000);
    }

    private float getDistance(double lat, double lon, GeoPoint gp) {
        float[] res = new float[1];
        Location.distanceBetween(lat, lon, gp.getLatitude(), gp.getLongitude(), res);
        return res[0];
    }

    public double getBusDuration(String originStopId, String destinationStopId, String line, String network) {
        Calendar now = Calendar.getInstance();
        long currentSecs = now.get(Calendar.HOUR_OF_DAY) * 3600L + now.get(Calendar.MINUTE) * 60L + now.get(Calendar.SECOND);
        long[] timings = getTripTiming(originStopId, destinationStopId, line, network, currentSecs, currentSecs);
        if (timings != null) {
            long duration = timings[1] - timings[0];
            if (duration < 0) duration += 86400; //ajuste por cambio de día
            return (double) duration;
        }
        return 0;
    }
}
