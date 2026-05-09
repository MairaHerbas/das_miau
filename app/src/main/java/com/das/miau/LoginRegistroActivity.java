package com.das.miau;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class LoginRegistroActivity extends AppCompatActivity {


    private EditText usuario;
    private EditText contrasena;
    private Button btnEntrar, btnRegistrar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login_registro);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        usuario = findViewById(R.id.et_usuario);
        contrasena = findViewById(R.id.et_contrasena);
        btnEntrar = findViewById(R.id.btn_iniciar);
        btnRegistrar = findViewById(R.id.btn_registrar);

        btnEntrar.setOnClickListener(v -> lanzarWorker("login"));
        btnRegistrar.setOnClickListener(v -> {
            Intent intent = new Intent(LoginRegistroActivity.this, RegistroActivity.class);
            startActivity(intent);
        });
    }

    private void lanzarWorker(String tipo) {
        String u = usuario.getText().toString().trim();
        String c = contrasena.getText().toString().trim();
        //campos no vacíos
        if (u.isEmpty()) {
            usuario.setError(getString(R.string.us_novacio));
            usuario.requestFocus();
            return;
        }
        if (c.isEmpty()) {
            contrasena.setError(getString(R.string.pas_novacia));
            contrasena.requestFocus();
            return;
        }

        Data datos = new Data.Builder()
                .putString("usuario", u)
                .putString("contrasena", c)
                .putString("tipo", tipo) //login o registro
                .build();
        OneTimeWorkRequest otwr = new OneTimeWorkRequest.Builder(ConexionBDWebService.class)
                .setInputData(datos)
                .build();

        WorkManager.getInstance(this).getWorkInfoByIdLiveData(otwr.getId())
                .observe(this, workInfo -> {
                    if (workInfo != null && workInfo.getState().isFinished()) {
                        procesarResultado(workInfo.getOutputData().getString("resultado"));
                    }
                });
        WorkManager.getInstance(this).enqueue(otwr);
    }

    private void procesarResultado(String result) {
        if (result == null) {
            Toast.makeText(this,getString(R.string.err_conexion), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(result);

            String status = (String) json.get("status");
            String mensaje = (String) json.get("mensaje");

            if ("ok".equals(status)) {
                android.content.SharedPreferences prefs = getSharedPreferences("MisPreferencias", android.content.Context.MODE_PRIVATE);
                android.content.SharedPreferences.Editor editor = prefs.edit();
                editor.putString("nombre_usuario", usuario.getText().toString());
                editor.putString("password_usuario", contrasena.getText().toString());

                if (json.containsKey("id_usuario")) editor.putString("id_usuario", String.valueOf(json.get("id_usuario")));
                if (json.containsKey("nombre_completo")) editor.putString("nombre_completo", (String) json.get("nombre_completo"));
                if (json.containsKey("email")) editor.putString("email", (String) json.get("email"));
                if (json.containsKey("facultad_id")) {
                    Object facObj = json.get("facultad_id");
                    if (facObj != null) editor.putInt("facultad_id", Integer.parseInt(facObj.toString()));
                }
                editor.apply();

                Intent intent = new Intent(LoginRegistroActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.err_procesar), Toast.LENGTH_SHORT).show();
        }
    }
}