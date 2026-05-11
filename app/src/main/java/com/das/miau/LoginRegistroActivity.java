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
    private static final String TAG = "LoginRegistroDEBUG"; // Etiqueta para buscar rápido en Logcat

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

        Log.d(TAG, "Lanzando worker tipo: " + tipo + " para usuario: " + u);

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
                .putString("tipo", tipo)
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
        Log.d(TAG, "Respuesta cruda del servidor: " + result); // Ver qué JSON llega exactamente

        if (result == null) {
            Log.e(TAG, "El resultado del servidor es null");
            Toast.makeText(this,getString(R.string.err_conexion), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(result);

            String status = (String) json.get("status");
            String mensaje = (String) json.get("mensaje");

            Log.d(TAG, "Status parseado: " + status + " | Mensaje: " + mensaje);

            if ("ok".equals(status)) {
                android.content.SharedPreferences prefs = getSharedPreferences("MisPreferencias", android.content.Context.MODE_PRIVATE);
                android.content.SharedPreferences.Editor editor = prefs.edit();
                editor.putString("nombre_usuario", usuario.getText().toString());
                editor.putString("password_usuario", contrasena.getText().toString());

                if (json.containsKey("id_usuario")) editor.putString("id_usuario", String.valueOf(json.get("id_usuario")));
                if(json.containsKey("puntos_usuario")) editor.putInt("puntos_usuario", Integer.parseInt(String.valueOf(json.get("puntos_usuario"))));
                if (json.containsKey("nombre_completo")) editor.putString("nombre_completo", (String) json.get("nombre_completo"));
                if (json.containsKey("email")) editor.putString("email", (String) json.get("email"));

                // BLOQUE DE COMPROBACIÓN DE FACULTADES
                if (json.containsKey("facultad_id")) {
                    Object facObj = json.get("facultad_id");
                    Log.d(TAG, "Valor de facultad_id recibido en JSON: " + facObj);

                    if (facObj != null) {
                        editor.putInt("facultad_id", Integer.parseInt(facObj.toString()));
                        Log.d(TAG, "Facultad guardada correctamente en SharedPreferences: " + facObj.toString());
                    } else {
                        Log.w(TAG, "facultad_id viene como NULL desde la base de datos.");
                    }
                } else {
                    Log.w(TAG, "El JSON devuelto NO contiene la clave 'facultad_id'.");
                }

                editor.apply();

                Intent intent = new Intent(LoginRegistroActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            } else {
                Log.e(TAG, "Error del servidor (status no es ok): " + mensaje);
                Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al parsear el JSON: " + e.getMessage(), e);
            Toast.makeText(this, getString(R.string.err_procesar), Toast.LENGTH_SHORT).show();
        }
    }
}