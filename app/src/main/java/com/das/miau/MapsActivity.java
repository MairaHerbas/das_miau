package com.das.miau;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MapsActivity extends BaseActivity {

    private MapView map = null;
    private MyLocationNewOverlay locationOverlay;
    private FusedLocationProviderClient fusedLocationClient;
    private String transportMode;
    private String destinoNombre;
    private double destinoLat, destinoLon;
    private GeoPoint userLocation;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private List<Polyline> routePolylines = new ArrayList<>();
    private List<Marker> routeMarkers = new ArrayList<>();

    // UI Card Elements
    private TextView tvDestinoCard, tvTiempoEstimado, tvLineaRecomendada, tvParadaOrigen, tvParadaDestino;
    private LinearLayout layoutBusInfo;
    private DatabaseHelper dbHelper;

    private static class RouteResult {
        List<GeoPoint> points;
        double duration;

        RouteResult(List<GeoPoint> points, double duration) {
            this.points = points;
            this.duration = duration;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_maps);
        setupToolbar();

        dbHelper = new DatabaseHelper(this);

        tvDestinoCard = findViewById(R.id.tv_destino_card);
        tvTiempoEstimado = findViewById(R.id.tv_tiempo_estimado);
        tvLineaRecomendada = findViewById(R.id.tv_linea_recomendada);
        tvParadaOrigen = findViewById(R.id.tv_parada_origen);
        tvParadaDestino = findViewById(R.id.tv_parada_destino);
        layoutBusInfo = findViewById(R.id.layout_bus_info);

        transportMode = getIntent().getStringExtra("TRANSPORT_MODE");
        if (transportMode == null) transportMode = "foot";
        
        destinoNombre = getIntent().getStringExtra("DESTINO_NOMBRE");
        destinoLat = getIntent().getDoubleExtra("DESTINO_LAT", 0);
        destinoLon = getIntent().getDoubleExtra("DESTINO_LON", 0);

        if (destinoNombre != null) {
            tvDestinoCard.setText("Destino: " + destinoNombre);
        }

        map = findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 101);
        } else {
            initLocation();
        }
    }

    private void initLocation() {
        locationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), map);
        locationOverlay.enableMyLocation();
        map.getOverlays().add(locationOverlay);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    userLocation = new GeoPoint(location.getLatitude(), location.getLongitude());
                    map.getController().setZoom(15.0);
                    map.getController().setCenter(userLocation);

                    if (destinoLat != 0 && destinoLon != 0) {
                        calculateRoute(new GeoPoint(destinoLat, destinoLon));
                    }
                }
            });
        }
    }

    private void calculateRoute(GeoPoint destinationPoint) {
        if (userLocation == null) return;

        executorService.execute(() -> {
            try {
                BusConnection connection = null;

                if ("bus".equals(transportMode)) {
                    connection = dbHelper.findBusConnection(
                            userLocation.getLatitude(), userLocation.getLongitude(),
                            destinationPoint.getLatitude(), destinationPoint.getLongitude()
                    );
                }

                // Procesar ruta encontrada o ruta por defecto
                processRouteResult(connection, destinationPoint);

            } catch (Exception e) {
                Log.e("BUS_DEBUG", "Error al calcular ruta", e);
                showToast("Error al calcular ruta");
            }
        });
    }

    private void processRouteResult(BusConnection connection, GeoPoint destinationPoint) throws Exception {
        List<List<GeoPoint>> segments = new ArrayList<>();
        double totalDurationSec = 0;

        if (connection != null) {
            GeoPoint stop1 = new GeoPoint(connection.getOriginStop().getLat(), connection.getOriginStop().getLon());
            GeoPoint stop2 = new GeoPoint(connection.getDestinationStop().getLat(), connection.getDestinationStop().getLon());

            // Tramo 1: Caminar a la parada (OSRM)
            RouteResult r1 = fetchOSRMRoute(userLocation, stop1, "foot");
            
            // Tramo 2: Autobús (GTFS Shapes)
            List<GeoPoint> busPoints;
            double busDuration;

            String shapeId = dbHelper.getShapeIdForConnection(
                    connection.getOriginStop().getStopId(),
                    connection.getDestinationStop().getStopId(),
                    connection.getLine(),
                    connection.getOriginStop().getNetwork()
            );

            busPoints = dbHelper.getTrimmedShape(
                    shapeId,
                    connection.getOriginStop(),
                    connection.getDestinationStop(),
                    connection.getOriginStop().getNetwork()
            );

            if (busPoints != null && !busPoints.isEmpty()) {
                Log.d("BUS_DEBUG", ">>> SHAPE REAL ENCONTRADO para " + connection.getLine() + " (shape_id: " + shapeId + ")");
                busDuration = dbHelper.getBusDuration(
                        connection.getOriginStop().getStopId(),
                        connection.getDestinationStop().getStopId(),
                        connection.getLine(),
                        connection.getOriginStop().getNetwork()
                );
                // Si no hay horario disponible para calcular duración, estimamos por distancia (aprox 30km/h)
                if (busDuration <= 0) busDuration = stop1.distanceToAsDouble(stop2) / 8.3;
            } else {
                Log.d("BUS_DEBUG", ">>> SHAPE NO ENCONTRADO para " + connection.getLine() + ". Usando OSRM driving fallback.");
                RouteResult r2 = fetchOSRMRoute(stop1, stop2, "driving");
                busPoints = r2.points;
                busDuration = r2.duration;
            }

            // Tramo 3: Caminar al destino (OSRM)
            RouteResult r3 = fetchOSRMRoute(stop2, destinationPoint, "foot");

            segments.add(r1.points);
            segments.add(busPoints);
            segments.add(r3.points);
            totalDurationSec = connection.getTotalTimeSec();
        } else {
            String osrmMode = "bike".equals(transportMode) ? "bicycle" :
                             "bus".equals(transportMode) ? "driving" : "foot";
            
            RouteResult result = fetchOSRMRoute(userLocation, destinationPoint, osrmMode);
            segments.add(result.points);
            totalDurationSec = result.duration;
        }

        final BusConnection finalConnection = connection;
        final double finalDuration = totalDurationSec;
        mainHandler.post(() -> updateUI(segments, destinationPoint, finalConnection, finalDuration));
    }

    private RouteResult fetchOSRMRoute(GeoPoint start, GeoPoint end, String mode) throws Exception {
        String urlStr = "https://router.project-osrm.org/route/v1/" + mode + "/" +
                start.getLongitude() + "," + start.getLatitude() + ";" +
                end.getLongitude() + "," + end.getLatitude() + "?overview=full&geometries=geojson";

        String response = downloadUrl(urlStr);
        JSONObject json = new JSONObject(response);
        if (!"Ok".equals(json.getString("code"))) return new RouteResult(new ArrayList<>(), 0);

        JSONObject route = json.getJSONArray("routes").getJSONObject(0);
        double duration = route.getDouble("duration");
        JSONArray coordinates = route.getJSONObject("geometry").getJSONArray("coordinates");

        List<GeoPoint> points = new ArrayList<>();
        for (int i = 0; i < coordinates.length(); i++) {
            JSONArray c = coordinates.getJSONArray(i);
            points.add(new GeoPoint(c.getDouble(1), c.getDouble(0)));
        }
        return new RouteResult(points, duration);
    }

    private void updateUI(List<List<GeoPoint>> segments, GeoPoint destinationPoint, BusConnection connection, double durationSec) {
        // Limpiar previos
        for (Polyline p : routePolylines) map.getOverlays().remove(p);
        for (Marker m : routeMarkers) map.getOverlays().remove(m);
        routePolylines.clear();
        routeMarkers.clear();

        // Mostrar tiempo estimado
        int minutes = (int) Math.ceil(durationSec / 60);
        String timeText;
        if (minutes < 60) {
            timeText = minutes + " min";
        } else {
            timeText = (minutes / 60) + " h " + (minutes % 60) + " min";
        }
        tvTiempoEstimado.setText("Tiempo estimado: " + timeText);

        List<GeoPoint> allPointsForCamera = new ArrayList<>();
        allPointsForCamera.add(userLocation);
        allPointsForCamera.add(destinationPoint);

        if (connection != null && segments.size() == 3) {
            // Dibujar 3 segmentos con colores distintos
            // Tramo 1: Caminando (Gris)
            addPolyline(segments.get(0), Color.GRAY);
            // Tramo 2: Autobús (Azul)
            addPolyline(segments.get(1), Color.BLUE);
            // Tramo 3: Caminando (Gris)
            addPolyline(segments.get(2), Color.GRAY);

            // Marcadores de paradas
            addMarker(new GeoPoint(connection.getOriginStop().getLat(), connection.getOriginStop().getLon()),
                     "Subir: " + connection.getOriginStop().getStopName(), 
                     "Línea " + connection.getLine());
            
            addMarker(new GeoPoint(connection.getDestinationStop().getLat(), connection.getDestinationStop().getLon()), 
                     "Bajar: " + connection.getDestinationStop().getStopName(), 
                     "Línea " + connection.getLine());
            
            allPointsForCamera.add(new GeoPoint(connection.getOriginStop().getLat(), connection.getOriginStop().getLon()));
            allPointsForCamera.add(new GeoPoint(connection.getDestinationStop().getLat(), connection.getDestinationStop().getLon()));

            layoutBusInfo.setVisibility(View.VISIBLE);

            tvLineaRecomendada.setText("Línea recomendada: " + connection.getLine());
            tvParadaOrigen.setText("Parada origen: " + connection.getOriginStop().getStopName());
            tvParadaDestino.setText("Parada destino: " + connection.getDestinationStop().getStopName());
        } else {
            // Ruta única
            if (!segments.isEmpty()) {
                addPolyline(segments.get(0), Color.BLUE);
            }
            layoutBusInfo.setVisibility("bus".equals(transportMode) ? View.VISIBLE : View.GONE);
            if ("bus".equals(transportMode)) {
                tvLineaRecomendada.setText("No se encontró línea directa disponible");
                tvParadaOrigen.setText("");
                tvParadaDestino.setText("");
            }
        }

        // Marcador Destino Final
        addMarker(destinationPoint, "Destino: " + destinoNombre, "");

        // Ajustar Cámara
        if (!allPointsForCamera.isEmpty()) {
            BoundingBox bbox = BoundingBox.fromGeoPoints(allPointsForCamera);
            map.zoomToBoundingBox(bbox.increaseByScale(1.3f), true);
        }

        map.invalidate();
    }

    private void addPolyline(List<GeoPoint> points, int color) {
        Polyline line = new Polyline();
        line.setPoints(points);
        line.getOutlinePaint().setColor(color);
        line.getOutlinePaint().setStrokeWidth(12f);
        map.getOverlays().add(line);
        routePolylines.add(line);
    }

    private void addMarker(GeoPoint point, String title, String snippet) {
        Marker m = new Marker(map);
        m.setPosition(point);
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        m.setTitle(title);
        m.setSnippet(snippet);
        map.getOverlays().add(m);
        routeMarkers.add(m);
    }

    private String downloadUrl(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", getPackageName());
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
            return result.toString();
        } finally {
            conn.disconnect();
        }
    }

    private void showToast(String message) {
        mainHandler.post(() -> Toast.makeText(MapsActivity.this, message, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onResume() { super.onResume(); map.onResume(); }

    @Override
    public void onPause() { super.onPause(); map.onPause(); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) dbHelper.close();
        executorService.shutdown();
    }
}
