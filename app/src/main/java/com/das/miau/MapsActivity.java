package com.das.miau;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
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

public class MapsActivity extends AppCompatActivity {

    private MapView map = null;
    private MyLocationNewOverlay locationOverlay;
    private FusedLocationProviderClient fusedLocationClient;
    private String transportMode;
    private String destinoNombre;
    private double destinoLat, destinoLon;
    private Polyline currentRouteOverlay;
    private GeoPoint userLocation;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_maps);

        transportMode = getIntent().getStringExtra("TRANSPORT_MODE");
        if (transportMode == null) transportMode = "foot";
        
        destinoNombre = getIntent().getStringExtra("DESTINO_NOMBRE");
        destinoLat = getIntent().getDoubleExtra("DESTINO_LAT", 0);
        destinoLon = getIntent().getDoubleExtra("DESTINO_LON", 0);

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

                    // Calculamos la ruta directamente usando las coordenadas recibidas
                    if (destinoLat != 0 && destinoLon != 0) {
                        calculateRoute(new GeoPoint(destinoLat, destinoLon));
                    }
                }
            });
        }
    }

    private void calculateRoute(GeoPoint destinationPoint) {
        if (userLocation == null) {
            Toast.makeText(this, "Obteniendo tu ubicación...", Toast.LENGTH_SHORT).show();
            return;
        }

        executorService.execute(() -> {
            try {
                String osrmMode = "foot".equals(transportMode) ? "foot" :
                                 "bike".equals(transportMode) ? "bicycle" : "driving";

                String routeUrlStr = "https://router.project-osrm.org/route/v1/" + osrmMode + "/" +
                        userLocation.getLongitude() + "," + userLocation.getLatitude() + ";" +
                        destinationPoint.getLongitude() + "," + destinationPoint.getLatitude() + "?overview=full&geometries=geojson";

                String routeResponse = downloadUrl(routeUrlStr);
                JSONObject routeJson = new JSONObject(routeResponse);

                if (!"Ok".equals(routeJson.getString("code"))) {
                    showToast("No se pudo calcular la ruta");
                    return;
                }

                JSONObject route = routeJson.getJSONArray("routes").getJSONObject(0);
                JSONArray coordinates = route.getJSONObject("geometry").getJSONArray("coordinates");
                double duration = route.getDouble("duration");

                List<GeoPoint> routePoints = new ArrayList<>();
                for (int i = 0; i < coordinates.length(); i++) {
                    JSONArray coord = coordinates.getJSONArray(i);
                    routePoints.add(new GeoPoint(coord.getDouble(1), coord.getDouble(0)));
                }

                mainHandler.post(() -> {
                    if (currentRouteOverlay != null) {
                        map.getOverlays().remove(currentRouteOverlay);
                    }

                    currentRouteOverlay = new Polyline();
                    currentRouteOverlay.setPoints(routePoints);
                    currentRouteOverlay.getOutlinePaint().setColor(0xFF0000FF); // Azul
                    currentRouteOverlay.getOutlinePaint().setStrokeWidth(10f);
                    map.getOverlays().add(currentRouteOverlay);

                    Marker destMarker = new Marker(map);
                    destMarker.setPosition(destinationPoint);
                    destMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                    destMarker.setTitle(destinoNombre);
                    map.getOverlays().add(destMarker);

                    map.invalidate();
                    Toast.makeText(MapsActivity.this, "Destino: " + destinoNombre + "\nLlegada en: " + (int)(duration / 60) + " min", Toast.LENGTH_LONG).show();
                });

            } catch (Exception e) {
                e.printStackTrace();
                showToast("Error al conectar con el servidor de rutas");
            }
        });
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
        } finally {
            conn.disconnect();
        }
    }

    private void showToast(String message) {
        mainHandler.post(() -> Toast.makeText(MapsActivity.this, message, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initLocation();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        map.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        map.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}