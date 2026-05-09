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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.bottomsheet.BottomSheetDialog;

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
    private TextView tvDestinoCard, tvTiempoTotal, tvLineaNumero, tvProximosBuses, tvRutaResumen;
    private ImageView btnInfoDetalles;
    private DatabaseHelper dbHelper;
    private BusConnection lastConnection;

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
        tvTiempoTotal = findViewById(R.id.tv_tiempo_total);
        tvLineaNumero = findViewById(R.id.tv_linea_numero);
        tvProximosBuses = findViewById(R.id.tv_proximos_buses);
        tvRutaResumen = findViewById(R.id.tv_ruta_resumen);
        btnInfoDetalles = findViewById(R.id.btn_info_detalles);

        if (btnInfoDetalles != null) {
            btnInfoDetalles.setOnClickListener(v -> {
                if (lastConnection != null) {
                    showRouteDetailsBottomSheet(lastConnection);
                }
            });
        }

        transportMode = getIntent().getStringExtra("TRANSPORT_MODE");
        if (transportMode == null) transportMode = "foot";
        
        destinoNombre = getIntent().getStringExtra("DESTINO_NOMBRE");
        destinoLat = getIntent().getDoubleExtra("DESTINO_LAT", 0);
        destinoLon = getIntent().getDoubleExtra("DESTINO_LON", 0);

        if (destinoNombre != null && tvDestinoCard != null) {
            tvDestinoCard.setText(destinoNombre);
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
            RouteResult r1 = fetchOSRMRoute(userLocation, stop1, "walking");
            
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
            RouteResult r3 = fetchOSRMRoute(stop2, destinationPoint, "walking");

            segments.add(r1.points);
            segments.add(busPoints);
            segments.add(r3.points);
            totalDurationSec = connection.getTotalTimeSec();
        } else if ("bus".equals(transportMode)) {
            // Si no hay conexión de bus, no queremos mostrar ruta alternativa de coche
            totalDurationSec = -1;
        } else {
            String osrmMode;
            if ("bike".equals(transportMode)) {
                // Para bici, la API de OSRM exige la palabra "cycling"
                osrmMode = "cycling";
            } else if ("foot".equals(transportMode)) {
                // Para ir a pie, la API de OSRM exige la palabra "walking"
                osrmMode = "walking";
            } else {
                osrmMode = "driving";
            }

            RouteResult result = fetchOSRMRoute(userLocation, destinationPoint, osrmMode);
            segments.add(result.points);
            
            double distanceMeters = 0;
            if (result.points != null && !result.points.isEmpty()) {
                for (int i = 0; i < result.points.size() - 1; i++) {
                    distanceMeters += result.points.get(i).distanceToAsDouble(result.points.get(i + 1));
                }
            }

            if ("foot".equals(transportMode)) {
                totalDurationSec = distanceMeters / 1.1;
            } else if ("bike".equals(transportMode)) {
                totalDurationSec = distanceMeters / 4.16;
            } else {
                totalDurationSec = result.duration;
            }
            Log.d("ROUTE_DEBUG", "Modo: " + transportMode + ", Distancia: " + (int)distanceMeters + "m, Duración: " + (int)totalDurationSec + "s");
        }

        final BusConnection finalConnection = connection;
        final double finalDuration = totalDurationSec;
        mainHandler.post(() -> updateUI(segments, destinationPoint, finalConnection, finalDuration));
    }

    private RouteResult fetchOSRMRoute(GeoPoint start, GeoPoint end, String mode) throws Exception {
        String urlStr;

        if ("foot".equals(transportMode)) {
            // Servidor exclusivo de peatones (conoce aceras, plazas y el Casco Viejo)
            urlStr = "https://routing.openstreetmap.de/routed-foot/route/v1/foot/" +
                    start.getLongitude() + "," + start.getLatitude() + ";" +
                    end.getLongitude() + "," + end.getLatitude() + "?overview=full&geometries=geojson";

        } else if ("bike".equals(transportMode)) {
            // Servidor exclusivo de bicicletas (prioriza bidegorris)
            urlStr = "https://routing.openstreetmap.de/routed-bike/route/v1/bike/" +
                    start.getLongitude() + "," + start.getLatitude() + ";" +
                    end.getLongitude() + "," + end.getLatitude() + "?overview=full&geometries=geojson";

        } else {
            // Servidor por defecto (coches)
            urlStr = "https://router.project-osrm.org/route/v1/driving/" +
                    start.getLongitude() + "," + start.getLatitude() + ";" +
                    end.getLongitude() + "," + end.getLatitude() + "?overview=full&geometries=geojson";
        }

        String response = downloadUrl(urlStr);
        JSONObject json = new JSONObject(response);
        if (!"Ok".equals(json.getString("code"))) {
            Log.e("ROUTE_DEBUG", "OSRM error " + json.optString("code") + " for mode " + mode);
            return new RouteResult(new ArrayList<>(), 0);
        }

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
        this.lastConnection = connection;
        // Limpiar previos
        for (Polyline p : routePolylines) map.getOverlays().remove(p);
        for (Marker m : routeMarkers) map.getOverlays().remove(m);
        routePolylines.clear();
        routeMarkers.clear();

        // Mostrar tiempo estimado
        if (durationSec >= 0) {
            int minutes = (int) Math.ceil(durationSec / 60);
            String timeText;
            if (minutes < 60) {
                timeText = minutes + " min";
            } else {
                timeText = (minutes / 60) + " h " + (minutes % 60) + " min";
            }
            if (tvTiempoTotal != null) {
                tvTiempoTotal.setText(timeText);
                tvTiempoTotal.setVisibility(View.VISIBLE);
            }
        } else {
            if (tvTiempoTotal != null) tvTiempoTotal.setVisibility(View.GONE);
        }

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

            if (tvLineaNumero != null) {
                tvLineaNumero.setText(connection.getLine());
                tvLineaNumero.setVisibility(View.VISIBLE);
            }
            if (tvRutaResumen != null) {
                tvRutaResumen.setText(connection.getOriginStop().getStopName() + " → " + connection.getDestinationStop().getStopName());
            }
            if (btnInfoDetalles != null) btnInfoDetalles.setVisibility(View.VISIBLE);

            // Próximos buses
            List<Long> next = connection.getNextDeparturesMin();
            if (tvProximosBuses != null) {
                if (next != null && !next.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < next.size(); i++) {
                        sb.append(next.get(i)).append("'");
                        if (i < next.size() - 1) sb.append("  ");
                    }
                    tvProximosBuses.setText(sb.toString());
                    tvProximosBuses.setVisibility(View.VISIBLE);
                } else {
                    tvProximosBuses.setVisibility(View.GONE);
                }
            }
        } else {
            // Ruta única
            if (!segments.isEmpty()) {
                addPolyline(segments.get(0), Color.BLUE);
            }
            if (tvLineaNumero != null) tvLineaNumero.setVisibility(View.GONE);
            if (btnInfoDetalles != null) btnInfoDetalles.setVisibility(View.GONE);
            if (tvProximosBuses != null) tvProximosBuses.setVisibility(View.GONE);
            if (tvRutaResumen != null) {
                if ("bus".equals(transportMode)) {
                    tvRutaResumen.setText("No se encontró línea directa disponible");
                } else if ("bike".equals(transportMode)) {
                    tvRutaResumen.setText("En bicicleta hasta el destino");
                } else if ("tram".equals(transportMode)) {
                    tvRutaResumen.setText("En coche hasta el destino");
                } else {
                    tvRutaResumen.setText("Caminando hasta el destino");
                }
            }
        }

        // Marcador Destino Final
        addMarker(destinationPoint, "Destino: " + (destinoNombre != null ? destinoNombre : ""), "");

        // Ajustar Cámara
        if (!allPointsForCamera.isEmpty()) {
            BoundingBox bbox = BoundingBox.fromGeoPoints(allPointsForCamera);
            map.zoomToBoundingBox(bbox.increaseByScale(1.3f), true);
        }

        map.invalidate();
    }

    private void showRouteDetailsBottomSheet(BusConnection bc) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_route_details, null);
        
        TextView tvTitle = view.findViewById(R.id.tv_bs_title);
        TextView tvWalkOrigin = view.findViewById(R.id.tv_bs_walk_origin);
        TextView tvOriginStop = view.findViewById(R.id.tv_bs_origin_stop);
        TextView tvWaitTime = view.findViewById(R.id.tv_bs_wait_time);
        TextView tvRideTime = view.findViewById(R.id.tv_bs_ride_time);
        TextView tvDestStop = view.findViewById(R.id.tv_bs_dest_stop);
        TextView tvWalkDest = view.findViewById(R.id.tv_bs_walk_dest);
        TextView tvNextBusesValues = view.findViewById(R.id.tv_bs_next_buses_values);
        View divider = view.findViewById(R.id.divider_bs);
        TextView tvNextTitle = view.findViewById(R.id.tv_bs_next_buses_title);

        int totalMin = (int) Math.ceil(bc.getTotalTimeSec() / 60);
        if (tvTitle != null) tvTitle.setText("Línea " + bc.getLine() + " • " + totalMin + " min total");

        if (tvWalkOrigin != null) tvWalkOrigin.setText("Camina hasta parada • " + (int)(bc.getWalkToOriginSec()/60) + " min (" + (int)bc.getWalkOriginMeters() + "m)");
        if (tvOriginStop != null) tvOriginStop.setText(bc.getOriginStop().getStopName());
        if (tvWaitTime != null) tvWaitTime.setText("Espera • " + (int)(bc.getWaitTimeSec()/60) + " min");
        if (tvRideTime != null) tvRideTime.setText("Trayecto en bus • " + (int)(bc.getRideTimeSec()/60) + " min");
        if (tvDestStop != null) tvDestStop.setText(bc.getDestinationStop().getStopName());
        if (tvWalkDest != null) tvWalkDest.setText("Hasta destino • " + (int)(bc.getWalkToDestSec()/60) + " min (" + (int)bc.getWalkDestMeters() + "m)");

        List<Long> next = bc.getNextDeparturesMin();
        if (next != null && !next.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < next.size(); i++) {
                sb.append(next.get(i)).append("'");
                if (i < next.size() - 1) sb.append(" • ");
            }
            if (tvNextBusesValues != null) tvNextBusesValues.setText(sb.toString());
        } else {
            if (tvNextBusesValues != null) tvNextBusesValues.setVisibility(View.GONE);
            if (divider != null) divider.setVisibility(View.GONE);
            if (tvNextTitle != null) tvNextTitle.setVisibility(View.GONE);
        }

        dialog.setContentView(view);
        dialog.show();
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
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            return result.toString();
        }
    }

    private void showToast(String message) {
        mainHandler.post(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }
}