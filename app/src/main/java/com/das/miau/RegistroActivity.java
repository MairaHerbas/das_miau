package com.das.miau;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

public class RegistroActivity extends BaseActivity {

    private ImageView imgPerfil;
    private EditText etNombre, etEmail, etUsuario, etPass;
    private Spinner spinnerFacultad;
    private Bitmap bitmapFotoActual = null;
    private Uri uriImagen;

    private ActivityResultLauncher<Intent> takePictureLauncher;
    private ActivityResultLauncher<String> pedirPermisoCamara;

    private List<Integer> listaIdsFacultades = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_registro);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        imgPerfil = findViewById(R.id.img_registro_perfil);
        etNombre = findViewById(R.id.et_registro_nombre);
        etEmail = findViewById(R.id.et_registro_email);
        etUsuario = findViewById(R.id.et_registro_usuario);
        etPass = findViewById(R.id.et_registro_pass);
        spinnerFacultad = findViewById(R.id.spinner_facultad);
        Button btnFoto = findViewById(R.id.btn_registro_foto);
        Button btnRegistrar = findViewById(R.id.btn_completar_registro);

        descargarFacultades();

        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        procesarFotoTomada();
                    }
                });

        pedirPermisoCamara = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) { abrirCamara(); }
                    else { Toast.makeText(this, getString(R.string.permisodenegado), Toast.LENGTH_SHORT).show(); }
                });

        btnFoto.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                abrirCamara();
            } else {
                pedirPermisoCamara.launch(Manifest.permission.CAMERA);
            }
        });

        btnRegistrar.setOnClickListener(v -> validarYRegistrar());
    }

    private void descargarFacultades() {
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL("http://34.175.63.186:81/get_facultades.php");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                if (conn.getResponseCode() == 200) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) { response.append(line); }
                    reader.close();

                    org.json.simple.parser.JSONParser parser = new org.json.simple.parser.JSONParser();
                    org.json.simple.JSONArray jsonArray = (org.json.simple.JSONArray) parser.parse(response.toString());

                    List<String> listaNombres = new ArrayList<>();
                    listaIdsFacultades.clear(); //limpiamos la lista por si aca

                    //parsear el JSON como Objeto, igual que en el perfil ---
                    for (Object obj : jsonArray) {
                        org.json.simple.JSONObject fac = (org.json.simple.JSONObject) obj;
                        int id = Integer.parseInt(fac.get("id").toString());
                        String nombre = (String) fac.get("nombre");

                        listaNombres.add(nombre);
                        listaIdsFacultades.add(id);
                    }

                    runOnUiThread(() -> {
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                                RegistroActivity.this, android.R.layout.simple_spinner_dropdown_item, listaNombres);
                        spinnerFacultad.setAdapter(adapter);
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void abrirCamara() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File directorio = getFilesDir();
        try {
            File fichImg = File.createTempFile("IMG_" + timeStamp + "_", ".jpg", directorio);
            uriImagen = FileProvider.getUriForFile(this, "com.das.miau.provider", fichImg);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uriImagen);
            takePictureLauncher.launch(intent);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void procesarFotoTomada() {
        try {
            InputStream is = getContentResolver().openInputStream(uriImagen);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, options);
            is.close();

            int inSampleSize = 1;
            if (options.outHeight > 500 || options.outWidth > 500) {
                inSampleSize = Math.max(options.outHeight / 500, options.outWidth / 500);
            }

            options.inJustDecodeBounds = false;
            options.inSampleSize = inSampleSize;
            InputStream is2 = getContentResolver().openInputStream(uriImagen);
            Bitmap bitmapReducido = BitmapFactory.decodeStream(is2, null, options);
            is2.close();

            bitmapFotoActual = bitmapReducido;
            imgPerfil.setImageBitmap(bitmapFotoActual);

        } catch (Exception e) {
            Log.e("REGISTRO", "Error al procesar la imagen", e);
        }
    }

    private void validarYRegistrar() {
        String nombre = etNombre.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String usuario = etUsuario.getText().toString().trim();
        String pass = etPass.getText().toString().trim();
        int pos = spinnerFacultad.getSelectedItemPosition();

        if (pos == -1 || listaIdsFacultades.isEmpty()) {
            Toast.makeText(this, getString(R.string.cargando), Toast.LENGTH_SHORT).show();
            return;
        }

        int facultadId = listaIdsFacultades.get(pos);

        if (nombre.isEmpty() || email.isEmpty() || usuario.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, getString(R.string.rellenacampos), Toast.LENGTH_SHORT).show();
            return;
        }

        if (!pass.matches("^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z]).{8,}$")) {
            etPass.setError(getString(R.string.contramin));
            etPass.requestFocus();
            return;
        }

        enviarDatosServidor(nombre, email, usuario, pass, facultadId);
    }

    private void enviarDatosServidor(String nombre, String email, String usuario, String pass, int facultadId) {
        Toast.makeText(this, getString(R.string.registrando), Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                String fotoBase64 = "";
                if (bitmapFotoActual != null) {
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    bitmapFotoActual.compress(Bitmap.CompressFormat.JPEG, 70, stream);
                    fotoBase64 = Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT);
                }

                Uri.Builder builder = new Uri.Builder()
                        .appendQueryParameter("nombre", nombre)
                        .appendQueryParameter("email", email)
                        .appendQueryParameter("username", usuario)
                        .appendQueryParameter("password", pass)
                        .appendQueryParameter("facultad_id", String.valueOf(facultadId))
                        .appendQueryParameter("foto", fotoBase64);

                String parametros = builder.build().getEncodedQuery();

                java.net.URL url = new java.net.URL("http://34.175.63.186:81/register.php");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);

                java.io.OutputStream os = conn.getOutputStream();
                java.io.BufferedWriter writer = new java.io.BufferedWriter(new java.io.OutputStreamWriter(os, "UTF-8"));
                writer.write(parametros);
                writer.flush();
                writer.close();
                os.close();

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) { sb.append(line); }
                String respuestaCompleta = sb.toString();
                reader.close();

                runOnUiThread(() -> {
                    try {
                        org.json.simple.parser.JSONParser parser = new org.json.simple.parser.JSONParser();
                        org.json.simple.JSONObject json = (org.json.simple.JSONObject) parser.parse(respuestaCompleta);

                        String status = (String) json.get("status");
                        String mensaje = (String) json.get("mensaje");

                        if ("ok".equals(status)) {
                            //extraemos el id_usuario del json que nos da php
                            String idUsuario = String.valueOf(json.get("id_usuario"));

                            SharedPreferences prefs = getSharedPreferences("MisPreferencias", MODE_PRIVATE);
                            SharedPreferences.Editor editor = prefs.edit();

                            //aseguramos que guarde "1" temporalmente si php no lo devuelve
                            if (idUsuario == null || idUsuario.equals("null")) {
                                idUsuario = "1";
                            }
                            editor.putString("id_usuario", idUsuario);
                            editor.putString("nombre_usuario", usuario);
                            editor.putString("password_usuario", pass);
                            editor.putString("nombre_completo", nombre);
                            editor.putString("email", email);
                            editor.putInt("facultad_id", facultadId);
                            //puntos=0
                            editor.putInt("puntos_usuario", 0);
                            editor.apply();

                            Toast.makeText(RegistroActivity.this, mensaje, Toast.LENGTH_LONG).show();
                            Intent intent = new Intent(RegistroActivity.this, MainActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        } else {
                            Toast.makeText(RegistroActivity.this, "Error: " + mensaje, Toast.LENGTH_LONG).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(RegistroActivity.this, getString(R.string.err_procesar), Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.err_conexion), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}