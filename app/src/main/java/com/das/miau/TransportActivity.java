package com.das.miau;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class TransportActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_transport);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        findViewById(R.id.btnPie).setOnClickListener(v -> startDestinosWithMode("foot"));
        findViewById(R.id.btnBici).setOnClickListener(v -> startDestinosWithMode("bike"));
        findViewById(R.id.btnAutobus).setOnClickListener(v -> startDestinosWithMode("bus"));
        findViewById(R.id.btnTranvia).setOnClickListener(v -> startDestinosWithMode("tram"));
    }

    private void startDestinosWithMode(String mode) {
        Intent intent = new Intent(this, DestinosActivity.class);
        intent.putExtra("TRANSPORT_MODE", mode);
        startActivity(intent);
    }
}