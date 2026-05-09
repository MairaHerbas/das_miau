package com.das.miau;

import android.Manifest;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PerfilFragment extends Fragment {

    private ImageView imgPerfil;
    private EditText etNombreCompleto, etEmail, etUsuario, etPassword;
    private Spinner spinnerFacultad;
    private Uri uriImagen;
    private ActivityResultLauncher<Intent> takePictureLauncher;
    private Bitmap bitmapFotoActual;
    private ActivityResultLauncher<String> pedirPermisoCamara;
    private String idUsuario, usernameGuardado;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_perfil, container, false);

        imgPerfil = root.findViewById(R.id.img_perfil);
        etNombreCompleto = root.findViewById(R.id.et_nombre_completo);
        etEmail = root.findViewById(R.id.et_email);
        etUsuario = root.findViewById(R.id.et_nombre_usuario);
        etPassword = root.findViewById(R.id.et_password);
        spinnerFacultad = root.findViewById(R.id.spinner_perfil_facultad);
        Button btnHacerFoto = root.findViewById(R.id.btn_hacer_foto);
        Button btnGuardar = root.findViewById(R.id.btn_guardar_perfil);

        android.content.SharedPreferences prefs = requireActivity().getSharedPreferences("MisPreferencias", android.content.Context.MODE_PRIVATE);
        idUsuario = prefs.getString("id_usuario", "1");
        usernameGuardado = prefs.getString("nombre_usuario", "");

        etUsuario.setText(usernameGuardado);
        etPassword.setText(prefs.getString("password_usuario", ""));
        etNombreCompleto.setText(prefs.getString("nombre_completo", ""));
        etEmail.setText(prefs.getString("email", ""));

        //leer id y descargar facultades
        int facIdGuardada = prefs.getInt("facultad_id", 1);
        descargarFacultades(facIdGuardada);

        //descargar img
        String direccion = "http://34.175.63.186:81/uploads/" + usernameGuardado + ".jpg?v=" + System.currentTimeMillis();
        new Thread(() -> {
            try {
                java.net.URL destino = new java.net.URL(direccion);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) destino.openConnection();
                if (conn.getResponseCode() == java.net.HttpURLConnection.HTTP_OK) {
                    Bitmap elBitmap = BitmapFactory.decodeStream(conn.getInputStream());
                    requireActivity().runOnUiThread(() -> imgPerfil.setImageBitmap(elBitmap));
                }
            } catch (IOException e) { e.printStackTrace(); }
        }).start();

        //coger foto y escalar
        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == requireActivity().RESULT_OK) {
                        try {
                            InputStream is = requireActivity().getContentResolver().openInputStream(uriImagen);
                            Bitmap bitmapOriginal = BitmapFactory.decodeStream(is);
                            is.close();
                            bitmapFotoActual = Bitmap.createScaledBitmap(bitmapOriginal, 500, 500, true);
                            imgPerfil.setImageBitmap(bitmapFotoActual);
                        } catch (Exception e) { Log.e("PERFIL", "Error cargando imagen", e); }
                    }
                });

        pedirPermisoCamara = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) { abrirCamara(); }
                    else { Toast.makeText(getContext(), getString(R.string.permisodenegado), Toast.LENGTH_SHORT).show(); }
                });

        btnHacerFoto.setOnClickListener(v -> {
            if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                abrirCamara();
            } else {
                pedirPermisoCamara.launch(Manifest.permission.CAMERA);
            }
        });

        btnGuardar.setOnClickListener(v -> subirPerfilAlServidor());

        return root;
    }

    private void descargarFacultades(int idGuardado) {
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

                    java.util.List<String> listaNombres = new java.util.ArrayList<>();
                    for (Object obj : jsonArray) {
                        listaNombres.add((String) obj);
                    }

                    requireActivity().runOnUiThread(() -> {
                        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                                requireContext(), android.R.layout.simple_spinner_dropdown_item, listaNombres);
                        spinnerFacultad.setAdapter(adapter);

                        // Si el usuario tiene guardado el ID 5, seleccionamos la posición 4 (5 - 1)
                        if (idGuardado > 0 && idGuardado <= listaNombres.size()) {
                            spinnerFacultad.setSelection(idGuardado - 1);
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void abrirCamara() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File directorio = requireActivity().getFilesDir();
        try {
            File fichImg = File.createTempFile("IMG_" + timeStamp + "_", ".jpg", directorio);
            uriImagen = FileProvider.getUriForFile(requireContext(), "com.das.miau.provider", fichImg);
            Intent elIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            elIntent.putExtra(MediaStore.EXTRA_OUTPUT, uriImagen);
            takePictureLauncher.launch(elIntent);
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void subirPerfilAlServidor() {
        String nuevoNombre = etNombreCompleto.getText().toString();
        String nuevoEmail = etEmail.getText().toString();
        String nuevoUsername = etUsuario.getText().toString();
        String nuevaPass = etPassword.getText().toString();

        // Sumamos 1 a la posición para obtener el ID real de la base de datos
        int facultadId = spinnerFacultad.getSelectedItemPosition() + 1;

        Toast.makeText(getContext(), "Guardando cambios...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                String fotoEnBase64 = "";
                if (bitmapFotoActual != null) {
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    bitmapFotoActual.compress(Bitmap.CompressFormat.JPEG, 70, stream);
                    fotoEnBase64 = Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT);
                }

                Uri.Builder builder = new Uri.Builder()
                        .appendQueryParameter("id_usuario", idUsuario)
                        .appendQueryParameter("nombre", nuevoNombre)
                        .appendQueryParameter("email", nuevoEmail)
                        .appendQueryParameter("username", nuevoUsername)
                        .appendQueryParameter("contrasena", nuevaPass)
                        .appendQueryParameter("facultad_id", String.valueOf(facultadId))
                        .appendQueryParameter("imagen", fotoEnBase64);

                // ACTUALIZAR SHAREDPREFERENCES PARA LA PRÓXIMA VEZ
                android.content.SharedPreferences prefs = requireActivity().getSharedPreferences("MisPreferencias", android.content.Context.MODE_PRIVATE);
                android.content.SharedPreferences.Editor editor = prefs.edit();
                editor.putString("nombre_usuario", nuevoUsername);
                editor.putString("nombre_completo", nuevoNombre);
                editor.putString("email", nuevoEmail);
                editor.putInt("facultad_id", facultadId);
                if (!nuevaPass.isEmpty()) editor.putString("password_usuario", nuevaPass);
                editor.apply();

                String parametrosURL = builder.build().getEncodedQuery();

                java.net.URL url = new java.net.URL("http://34.175.63.186:81/actualizar_perfil.php");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);

                java.io.OutputStream os = conn.getOutputStream();
                java.io.BufferedWriter writer = new java.io.BufferedWriter(new java.io.OutputStreamWriter(os, "UTF-8"));
                writer.write(parametrosURL);
                writer.flush();
                writer.close();
                os.close();

                if (conn.getResponseCode() == 200) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), getString(R.string.perfilact), Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> Toast.makeText(getContext(), getString(R.string.err_conexion), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}