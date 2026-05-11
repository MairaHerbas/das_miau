package com.das.miau;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.view.GravityCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

// --- AÑADIDOS PARA EL RANKING ---
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
// ---------------------------------

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class MainActivity extends BaseActivity {

    private TextView tvCityName, tvWeatherDesc, tvTemps;

    // --- VARIABLES DEL RANKING ---
    private TextView tvTop1, tvTop2, tvTop3;

    private FusedLocationProviderClient fusedLocationClient;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Al llamar a setupToolbar(), la BaseActivity se encarga de crear el Drawer
        setupToolbar();

        Toolbar toolbar = findViewById(R.id.toolbar);

        // Manejar el botón de retroceso (Back)
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // drawerLayout viene heredado de BaseActivity
                if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    // Si el usuario da a atrás y está en el Perfil, lo devolvemos a la pantalla del Tiempo
                    Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
                    if (fragment != null) {
                        getSupportFragmentManager().beginTransaction().remove(fragment).commit();
                        findViewById(R.id.contenido_principal).setVisibility(View.VISIBLE);
                        if(toolbar != null) toolbar.setTitle("UniGo"); // Restaurar título original
                    } else {
                        setEnabled(false);
                        getOnBackPressedDispatcher().onBackPressed();
                    }
                }
            }
        });

        tvCityName = findViewById(R.id.tvCityName);
        tvWeatherDesc = findViewById(R.id.tvWeatherDesc);
        tvTemps = findViewById(R.id.tvTemps);
        Button btnEntrar = findViewById(R.id.btnEntrar);

        // --- ENLAZAR TEXTVIEWS DEL RANKING ---
        tvTop1 = findViewById(R.id.tvTop1);
        tvTop2 = findViewById(R.id.tvTop2);
        tvTop3 = findViewById(R.id.tvTop3);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        btnEntrar.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, TransportActivity.class);
            startActivity(intent);
        });

        checkPermissionsAndGetLocation();

        // --- DESCARGAMOS EL RANKING DE LA BASE DE DATOS ---
        fetchTopRanking();
    }

    private void fetchTopRanking() {
        executorService.execute(() -> {
            try {
                URL url = new URL("http://34.175.63.186:81/get_ranking.php");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) { response.append(line); }
                reader.close();

                JSONArray jsonArray = new JSONArray(response.toString());

                String top1 = "1. -";
                String top2 = "2. -";
                String top3 = "3. -";

                // Leemos los 3 primeros (o los que haya)
                if (jsonArray.length() > 0) {
                    JSONObject f1 = jsonArray.getJSONObject(0);
                    top1 = "1. 🥇 " + f1.getString("nombre") + " (" + String.format(Locale.US, "%.1f", f1.getDouble("media_puntos")) + " pts/usr)";
                }
                if (jsonArray.length() > 1) {
                    JSONObject f2 = jsonArray.getJSONObject(1);
                    top2 = "2. 🥈 " + f2.getString("nombre") + " (" + String.format(Locale.US, "%.1f", f2.getDouble("media_puntos")) + " pts/usr)";
                }
                if (jsonArray.length() > 2) {
                    JSONObject f3 = jsonArray.getJSONObject(2);
                    top3 = "3. 🥉 " + f3.getString("nombre") + " (" + String.format(Locale.US, "%.1f", f3.getDouble("media_puntos")) + " pts/usr)";
                }

                // Las variables que pasamos a runOnUiThread deben ser finales (o efectivamente finales)
                final String finalTop1 = top1;
                final String finalTop2 = top2;
                final String finalTop3 = top3;

                runOnUiThread(() -> {
                    if (tvTop1 != null) tvTop1.setText(finalTop1);
                    if (tvTop2 != null) tvTop2.setText(finalTop2);
                    if (tvTop3 != null) tvTop3.setText(finalTop3);
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    if (tvTop1 != null) tvTop1.setText("Aún no hay puntos registrados");
                    if (tvTop2 != null) tvTop2.setVisibility(View.GONE);
                    if (tvTop3 != null) tvTop3.setVisibility(View.GONE);
                });
            }
        });
    }

    private void checkPermissionsAndGetLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
        } else {
            getLastLocation();
        }
    }

    private void getLastLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    String city = getNearestCity(location.getLatitude(), location.getLongitude());
                    fetchWeather(city);
                } else {
                    fetchWeather("Bilbao");
                }
            });
        }
    }

    private String getNearestCity(double lat, double lon) {
        if (lat > 43.2 && lon < -2.5) return "Bilbao";
        if (lat > 43.2 && lon > -2.1) return "Donostia-San Sebastián";
        if (lat < 43.0 && lon < -2.4) return "Vitoria-Gasteiz";
        if (lat < 43.0 && lon > -1.8) return "Pamplona";
        if (lat > 43.0 && lat < 43.15) return "Mondragón";
        return "Bilbao";
    }

    private void fetchWeather(String city) {
        executorService.execute(() -> {
            try {
                URL url = new URL("https://opendata.euskadi.eus/contenidos/prevision_tiempo/met_forecast/opendata/met_forecast.xml");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                InputStream is = conn.getInputStream();

                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(is);

                NodeList cities = doc.getElementsByTagName("cityForecastData");
                for (int i = 0; i < cities.getLength(); i++) {
                    Element element = (Element) cities.item(i);
                    if (element.getAttribute("cityName").equalsIgnoreCase(city)) {
                        String max = element.getElementsByTagName("tempMax").item(0).getTextContent();
                        String min = element.getElementsByTagName("tempMin").item(0).getTextContent();

                        Element symbol = (Element) element.getElementsByTagName("symbol").item(0);
                        String desc = symbol.getElementsByTagName("es").item(0).getTextContent();

                        runOnUiThread(() -> updateUI(city, min, max, desc));
                        break;
                    }
                }
                is.close();
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error al cargar el clima", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void updateUI(String city, String min, String max, String desc) {
        tvCityName.setText(city);
        tvWeatherDesc.setText(desc.trim());
        tvTemps.setText("Min: " + min + "ºC | Max: " + max + "ºC");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getLastLocation();
        }
    }
}