package com.das.miau;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class DestinosActivity extends BaseActivity {

    private String transportMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_destinos);
        setupToolbar();

        transportMode = getIntent().getStringExtra("TRANSPORT_MODE");

        RecyclerView rvDestinos = findViewById(R.id.rvDestinos);
        rvDestinos.setLayoutManager(new LinearLayoutManager(this));

        List<CentroUniversitario> listaCentros = getListaCentros();
        DestinosAdapter adapter = new DestinosAdapter(listaCentros, centro -> {
            Toast.makeText(this, "Seleccionado: " + centro.getNombre(), Toast.LENGTH_SHORT).show();
            
            Intent intent = new Intent(this, MapsActivity.class);
            intent.putExtra("TRANSPORT_MODE", transportMode);
            intent.putExtra("DESTINO_NOMBRE", centro.getNombre());
            intent.putExtra("DESTINO_LAT", centro.getLatitud());
            intent.putExtra("DESTINO_LON", centro.getLongitud());
            startActivity(intent);
        });

        rvDestinos.setAdapter(adapter);
    }

    private List<CentroUniversitario> getListaCentros() {
        List<CentroUniversitario> centros = new ArrayList<>();
        try {
            // Leer el archivo JSON desde la carpeta assets
            InputStream is = getAssets().open("coordenadas_universidades.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            
            String json = new String(buffer, StandardCharsets.UTF_8);
            JSONArray jsonArray = new JSONArray(json);

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                
                String universidad = obj.getString("universidad");
                String centroNombre = obj.getString("centro");
                String ubicacion = obj.getString("ubicacion");
                
                JSONObject coords = obj.getJSONObject("coordenadas");
                double lat = coords.getDouble("latitud");
                double lon = coords.getDouble("longitud");

                String nombreDisplay = universidad + " - " + centroNombre;
                
                centros.add(new CentroUniversitario(nombreDisplay, ubicacion, lat, lon));
            }
            
        } catch (Exception e) {
            Log.e("DestinosActivity", "Error al cargar centros desde JSON", e);
            Toast.makeText(this, "Error al cargar la lista de centros", Toast.LENGTH_SHORT).show();
        }
        return centros;
    }
}
