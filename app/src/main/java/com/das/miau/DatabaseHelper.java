package com.das.miau;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "transporte_bizkaia.db";
    private static final int DB_VERSION = 1;
    private final Context context;
    private SQLiteDatabase database;

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
                // The database is located in assets/databases/ folder
                InputStream is = context.getAssets().open("databases/" + DB_NAME);
                OutputStream os = new FileOutputStream(dbFile);
                byte[] buffer = new byte[1024];
                int length;
                while ((length = is.read(buffer)) > 0) {
                    os.write(buffer, 0, length);
                }
                os.flush();
                os.close();
                is.close();
                Log.d("DB_HELPER", "Base de datos copiada con éxito desde assets.");
            } catch (Exception e) {
                Log.e("DB_HELPER", "Error copiando la base de datos", e);
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
        if (database != null) {
            database.close();
        }
        super.close();
    }

    // Método para sacar las líneas de Bilbobus
    public List<RutaBus> getLineasBilbobus() {
        List<RutaBus> lista = new ArrayList<>();
        openDatabase();
        // Asegúrate de que el nombre de tu tabla es "routes_bilbobus_limpio"
        Cursor cursor = database.rawQuery("SELECT route_id, route_short_name, route_long_name FROM routes_bilbobus_limpio LIMIT 50", null);

        if (cursor.moveToFirst()) {
            do {
                lista.add(new RutaBus(cursor.getString(0), cursor.getString(1), cursor.getString(2)));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return lista;
    }

    // Método para sacar las líneas de Bizkaibus
    public List<RutaBus> getLineasBizkaibus() {
        List<RutaBus> lista = new ArrayList<>();
        openDatabase();
        // Asegúrate de que el nombre de tu tabla es "routes_bizkaibus_limpio"
        Cursor cursor = database.rawQuery("SELECT route_id, route_short_name, route_long_name FROM routes_bizkaibus_limpio LIMIT 50", null);

        if (cursor.moveToFirst()) {
            do {
                lista.add(new RutaBus(cursor.getString(0), cursor.getString(1), cursor.getString(2)));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return lista;
    }

    @Override
    public void onCreate(SQLiteDatabase db) { }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

    // Método para buscar buses cerca de un destino (¡Esto da muchos puntos de complejidad!)
    public List<RutaBus> getBusesCercaDe(double latitudDestino, double longitudDestino) {
        List<RutaBus> lista = new ArrayList<>();
        openDatabase();

        // Creamos una "caja" de unos 500 metros alrededor del destino
        double radioBusqueda = 0.005;
        double latMin = latitudDestino - radioBusqueda;
        double latMax = latitudDestino + radioBusqueda;
        double lonMin = longitudDestino - radioBusqueda;
        double lonMax = longitudDestino + radioBusqueda;

        // CONSULTA SQL CON JOIN:
        // 1. Filtra las paradas (stops) dentro de la caja.
        // 2. Mira qué viajes (trips) pasan por ahí en stop_times.
        // 3. Saca la ruta (routes) de esos viajes.
        String queryBilbobus =
                "SELECT DISTINCT r.route_id, r.route_short_name, r.route_long_name " +
                        "FROM routes_bilbobus_limpio r " +
                        "JOIN trips_bilbobus_limpio t ON r.route_id = t.route_id " +
                        "JOIN stop_times_bilbobus_limpio st ON t.trip_id = st.trip_id " +
                        "JOIN stops_bilbobus_limpio s ON st.stop_id = s.stop_id " +
                        "WHERE s.stop_lat BETWEEN ? AND ? AND s.stop_lon BETWEEN ? AND ?";

        // Metemos los parámetros de nuestra caja en la consulta
        String[] parametros = {
                String.valueOf(latMin), String.valueOf(latMax),
                String.valueOf(lonMin), String.valueOf(lonMax)
        };

        // Ejecutamos la búsqueda (Aquí podrías hacer lo mismo para Bizkaibus si quieres)
        Cursor cursor = database.rawQuery(queryBilbobus, parametros);

        if (cursor.moveToFirst()) {
            do {
                lista.add(new RutaBus(cursor.getString(0), cursor.getString(1), cursor.getString(2)));
            } while (cursor.moveToNext());
        }
        cursor.close();

        return lista;
    }
}