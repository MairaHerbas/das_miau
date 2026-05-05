package com.das.miau;

import android.content.Intent;
import android.os.Bundle;

public class TransportActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transport);
        setupToolbar();

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