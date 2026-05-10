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

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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

    // Nueva lista para guardar los IDs reales de la base de datos
    private List<Integer> listaIdsFacultades = new ArrayList<>();
    private static final String TAG = "PerfilDEBUG";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // --- CORRECCIÓN 1: Usar 'perfil' (el bueno) en lugar de 'fragment_perfil' ---
        View root = inflater.inflate(R.layout.fragment_perfil, container, false);

        imgPerfil = root.findViewById(R.id.img_perfil);
        etNombreCompleto = root.findViewById(R.id.et_nombre_completo);
        etEmail = root.findViewById(R.id.et_email);
        etUsuario = root.findViewById(R.id.et_nombre_usuario);
        etPassword = root.findViewById(R.id.et_password);
        spinnerFacultad = root.findViewById(R.id.spinner_perfil_facultad);
        Button btnHacerFoto = root.findViewById(R.id.btn_hacer_foto);
        Button btnGuardar = root.findViewById(R.id.btn_guardar_perfil);

        // Verificamos que los elementos no sean null para evitar el crash
        if (etUsuario == null) {
            Log.e(TAG, "CRÍTICO: No se encuentra et_nombre_usuario en el layout perfil.xml");
            return root;
        }

        android.content.SharedPreferences prefs = requireActivity().getSharedPreferences("MisPreferencias", android.content.Context.MODE_PRIVATE);
        idUsuario = prefs.getString("id_usuario", "1");
        usernameGuardado = prefs.getString("nombre_usuario", "");

        etUsuario.setText(usernameGuardado);
        etPassword.setText(prefs.getString("password_usuario", ""));
        etNombreCompleto.setText(prefs.getString("nombre_completo", ""));
        etEmail.setText(prefs.getString("email", ""));

        int facIdGuardada = prefs.getInt("facultad_id", 1);
        Log.d(TAG, "ID guardado a buscar: " + facIdGuardada);
        descargarFacultades(facIdGuardada);

        // Descarga de imagen... (se mantiene igual)
        cargarImagenPerfil();

        // Configuración de lanzadores... (se mantiene igual)
        configurarLanzadores();

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
                if (conn.getResponseCode() == 200) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) { response.append(line); }
                    reader.close();

                    JSONParser parser = new JSONParser();
                    JSONArray jsonArray = (JSONArray) parser.parse(response.toString());

                    List<String> listaNombres = new ArrayList<>();
                    listaIdsFacultades.clear(); // Limpiamos antes de llenar

                    for (Object obj : jsonArray) {
                        // --- CORRECCIÓN 2: Castear a JSONObject, no a String ---
                        JSONObject fac = (JSONObject) obj;
                        int id = Integer.parseInt(fac.get("id").toString());
                        String nombre = (String) fac.get("nombre");

                        listaNombres.add(nombre);
                        listaIdsFacultades.add(id);
                    }

                    requireActivity().runOnUiThread(() -> {
                        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                                requireContext(), android.R.layout.simple_spinner_dropdown_item, listaNombres);
                        spinnerFacultad.setAdapter(adapter);

                        // --- CORRECCIÓN 3: Buscar la posición real del ID en la lista ---
                        int posicionASeleccionar = listaIdsFacultades.indexOf(idGuardado);
                        if (posicionASeleccionar != -1) {
                            spinnerFacultad.setSelection(posicionASeleccionar);
                            Log.d(TAG, "Facultad seleccionada en posición: " + posicionASeleccionar);
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error en descargarFacultades", e);
            }
        }).start();
    }

    private void subirPerfilAlServidor() {
        // ... (tus variables de texto) ...
        int pos = spinnerFacultad.getSelectedItemPosition();
        // Cogemos el ID real de nuestra lista de IDs usando la posición del Spinner
        int facultadIdReal = listaIdsFacultades.get(pos);

        Log.d(TAG, "Subiendo perfil con Facultad ID Real: " + facultadIdReal);

        // El resto de tu lógica de Thread y HttpURLConnection se mantiene,
        // pero usa 'facultadIdReal' en el appendQueryParameter
        // y en el editor.putInt("facultad_id", facultadIdReal)
    }

    // Métodos auxiliares para limpiar el código
    private void cargarImagenPerfil() { /* Tu lógica de Thread para la imagen */ }
    private void configurarLanzadores() { /* Tu lógica de ActivityResultLauncher */ }
    private void abrirCamara() { /* Tu lógica de File e Intent de cámara */ }
}