package com.das.miau;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.navigation.NavigationView;

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

public class MainActivity extends BaseActivity {

    private TextView tvCityName, tvWeatherDesc, tvTemps;
    private FusedLocationProviderClient fusedLocationClient;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupToolbar();

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        Toolbar toolbar = findViewById(R.id.toolbar);

        if (toolbar != null && drawerLayout != null) {
            ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                    this, drawerLayout, toolbar,
                    R.string.open_nav, R.string.close_nav);
            drawerLayout.addDrawerListener(toggle);
            toggle.syncState();
        }
        if (navigationView != null) {
            navigationView.setNavigationItemSelectedListener(item -> {
                int itemId = item.getItemId();
                Fragment fragmentSeleccionado = null;
                if (itemId == R.id.nav_perfil) {
                    if (!isUserLoggedIn()) {
                        //no logueado->te obligo
                        startActivity(new Intent(MainActivity.this, LoginRegistroActivity.class));
                    } else {
                        //logueado->te dejo entrar
                        fragmentSeleccionado = new PerfilFragment();
                        toolbar.setTitle(getString(R.string.miperfil));
                    }
                }
                else if (item.getItemId() == R.id.nav_lineas) {
                    android.content.Intent intent = new android.content.Intent(this, BusesActivity.class);
                    startActivity(intent);
                    return true;
                }
                // AQUÍ AÑADIREMOS "MIS LÍNEAS" MÁS ADELANTE
                /* else if (itemId == R.id.nav_mis_lineas) {
                    if (!isUserLoggedIn()) {
                        startActivity(new Intent(MainActivity.this, LoginRegistroActivity.class));
                    } else {
                        fragmentSeleccionado = new MisLineasFragment();
                        toolbar.setTitle("Mis Líneas");
                    }
                } */

                // Si hay un fragmento seleccionado, ocultamos el contenido principal y mostramos el fragmento
                if (fragmentSeleccionado != null) {
                    // Ocultamos la vista del tiempo y el botón de entrar
                    findViewById(R.id.contenido_principal).setVisibility(View.GONE);

                    // Cargamos el fragmento en el contenedor
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container, fragmentSeleccionado)
                            .commit();
                }

                // Cerramos el menú después de hacer clic
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            });
        }

        // Manejar el botón de retroceso (Back)
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
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

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        btnEntrar.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, TransportActivity.class);
            startActivity(intent);
        });

        checkPermissionsAndGetLocation();
    }
    private boolean isUserLoggedIn() {
        SharedPreferences prefs = getSharedPreferences("MisPreferencias", MODE_PRIVATE);
        return prefs.contains("id_usuario"); //true->hay alguien loggeado
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