package com.das.miau;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class MainActivity extends AppCompatActivity {

    private TextView tvCityName, tvWeatherDesc, tvTemps;
    private FusedLocationProviderClient fusedLocationClient;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvCityName = findViewById(R.id.tvCityName);
        tvWeatherDesc = findViewById(R.id.tvWeatherDesc);
        tvTemps = findViewById(R.id.tvTemps);
        Button btnEntrar = findViewById(R.id.btnEntrar);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        btnEntrar.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, TransportActivity.class);
            startActivity(intent);
        });

        checkPermissionsAndGetLocation();
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
                    // Si no hay ubicación, por defecto Bilbao
                    fetchWeather("Bilbao");
                }
            });
        }
    }

    // Mapeo simple de coordenadas a las ciudades del XML
    private String getNearestCity(double lat, double lon) {
        // Coordenadas aproximadas
        if (lat > 43.2 && lon < -2.5) return "Bilbao";
        if (lat > 43.2 && lon > -2.1) return "Donostia-San Sebastián";
        if (lat < 43.0 && lon < -2.4) return "Vitoria-Gasteiz";
        if (lat < 43.0 && lon > -1.8) return "Pamplona";
        if (lat > 43.0 && lat < 43.15) return "Mondragón";
        return "Bilbao"; // Por defecto
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
                        
                        // Obtener descripción dentro de <symbol> <es>
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
        tvWeatherDesc.setText(desc);
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