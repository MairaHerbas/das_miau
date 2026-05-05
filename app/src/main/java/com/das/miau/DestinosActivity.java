package com.das.miau;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class DestinosActivity extends AppCompatActivity {

    private String transportMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_destinos);

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

        // Centros EHU con coordenadas exactas
        centros.add(new CentroUniversitario("EHU - Escuela de Ingeniería de Bilbao Edificio I", "Bilbao", 43.2627363120995, -2.949407232061388));
        centros.add(new CentroUniversitario("EHU - Escuela de Ingeniería de Bilbao Edificio II", "Bilbao", 43.26334695434439, -2.950252858753442));
        centros.add(new CentroUniversitario("EHU - Escuela de Ingeniería de Bilbao Edificio Náutica", "Portugalete", 43.326902400000016, -3.0227318000235543));
        centros.add(new CentroUniversitario("EHU - Facultad de Bellas Artes", "Leioa", 43.331147405281776, -2.9731440259314432));
        centros.add(new CentroUniversitario("EHU - Facultad de Ciencia y Tecnología", "Leioa", 43.330760638874615, -2.9700380139524176));
        centros.add(new CentroUniversitario("EHU - Facultad de Ciencias Sociales y Comunicación", "Leioa", 43.33148457713639, -2.9674339108502132));
        centros.add(new CentroUniversitario("EHU - Facultad de Derecho", "Leioa", 43.330992718356114, -2.9656974547163495));
        centros.add(new CentroUniversitario("EHU - Facultad de Economía y Empresa", "Sarriko", 43.273409411285776, -2.958675881749486));
        centros.add(new CentroUniversitario("EHU - Facultad de Economía y Empresa. Sección Elkano", "Bilbao", 43.260101515788286, -2.933114186402578));
        centros.add(new CentroUniversitario("EHU - Facultad de Educación de Bilbao", "Leioa", 43.33305408769437, -2.972720529918126));
        centros.add(new CentroUniversitario("EHU - Facultad de Medicina y Enfermería", "Leioa", 43.32947045019163, -2.965872609627126));
        centros.add(new CentroUniversitario("EHU - Unidad Docente Medicina - Galdakao", "Galdakao", 43.223428019813404, -2.8179111089534383));
        centros.add(new CentroUniversitario("EHU - Unidad Docente Medicina - Basurto", "Bilbao", 43.26099953694176, -2.951380476431241));
        centros.add(new CentroUniversitario("EHU - Unidad Docente Medicina - Cruces", "Cruces", 43.28256601694907, -2.98438082949724));
        centros.add(new CentroUniversitario("EHU - Aulas de la Experiencia", "Bilbao", 43.25778899843017, -2.9231484716201708));

        // Centros Mondragon Unibertsitatea
        centros.add(new CentroUniversitario("MONDRAGON - Bilbao Berrikuntza Faktoria (BBF)", "Bilbao", 43.2644152135323, -2.9271438253439004));
        centros.add(new CentroUniversitario("MONDRAGON - As Fabrik: Zorrotzaurre", "Bilbao", 43.27192748361638, -2.9639685));

        // Centros Deusto
        centros.add(new CentroUniversitario("DEUSTO - Deusto Business School", "Deusto", 43.27120414694327, -2.939702686790459));
        centros.add(new CentroUniversitario("DEUSTO - Facultad de Derecho", "Deusto", 43.27053079157015, -2.936470292003417));
        centros.add(new CentroUniversitario("DEUSTO - Facultad de Ciencias Sociales y Humanas", "Deusto", 43.270514592228494, -2.936464505171852));
        centros.add(new CentroUniversitario("DEUSTO - Facultad de Ingeniería", "Deusto", 43.27181329907745, -2.9397152537821767));
        centros.add(new CentroUniversitario("DEUSTO - Facultad de Educación y Deporte", "Deusto", 43.2705247668362, -2.9364613155356936));
        centros.add(new CentroUniversitario("DEUSTO - Facultad de Ciencias de la Salud", "Deusto", 43.2705247668362, -2.9364613155356936));

        return centros;
    }
}