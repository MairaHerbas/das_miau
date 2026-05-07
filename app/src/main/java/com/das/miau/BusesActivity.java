package com.das.miau;

import android.os.Bundle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.tabs.TabLayout;
import java.util.List;

public class BusesActivity extends BaseActivity {

    private RecyclerView recyclerRutas;
    private BusesAdapter adapter;
    private DatabaseHelper dbHelper;
    private TabLayout tabLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Enlazamos con tu layout buses.xml
        setContentView(R.layout.buses);
        setupToolbar(); // Aprovechamos tu Toolbar personalizada

        // 1. Inicializar la Base de Datos
        dbHelper = new DatabaseHelper(this);

        // 2. Configurar la lista (RecyclerView)
        recyclerRutas = findViewById(R.id.recycler_rutas);
        recyclerRutas.setLayoutManager(new LinearLayoutManager(this));

         //3. Cargar Bilbobus por defecto al abrir la pantalla
        List<RutaBus> rutasBilbobus = dbHelper.getLineasBilbobus();
        adapter = new BusesAdapter(rutasBilbobus);
        recyclerRutas.setAdapter(adapter);

        // 4. Darle vida a las pestañas
        tabLayout = findViewById(R.id.tab_layout_redes);
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    // Si tocan la primera pestaña (Bilbobus)
                    adapter.setRutas(dbHelper.getLineasBilbobus());
                } else {
                    // Si tocan la segunda pestaña (Bizkaibus)
                    adapter.setRutas(dbHelper.getLineasBizkaibus());
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }
}