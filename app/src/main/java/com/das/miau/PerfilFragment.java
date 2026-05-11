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
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PerfilFragment extends Fragment {

    private ImageView imgPerfil;
    private EditText etNombreCompleto, etEmail, etUsuario, etPassword;
    private Spinner spinnerFacultad;
    private TextView tvMisPuntos;
    private Uri uriImagen;
    private ActivityResultLauncher<Intent> takePictureLauncher;
    private Bitmap bitmapFotoActual;
    private ActivityResultLauncher<String> pedirPermisoCamara;
    private String idUsuario, usernameGuardado;

    private List<Integer> listaIdsFacultades = new ArrayList<>();
    private static final String TAG = "PerfilDEBUG";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_perfil, container, false);

        imgPerfil = root.findViewById(R.id.img_perfil);
        etNombreCompleto = root.findViewById(R.id.et_nombre_completo);
        etEmail = root.findViewById(R.id.et_email);
        etUsuario = root.findViewById(R.id.et_nombre_usuario);
        etPassword = root.findViewById(R.id.et_password);
        spinnerFacultad = root.findViewById(R.id.spinner_perfil_facultad);
        tvMisPuntos = root.findViewById(R.id.tvMisPuntos);

        Button btnHacerFoto = root.findViewById(R.id.btn_hacer_foto);
        Button btnGuardar = root.findViewById(R.id.btn_guardar_perfil);

        android.content.SharedPreferences prefs = requireActivity().getSharedPreferences("MisPreferencias", android.content.Context.MODE_PRIVATE);
        idUsuario = prefs.getString("id_usuario", "1");
        usernameGuardado = prefs.getString("nombre_usuario", "");

        etUsuario.setText(usernameGuardado);
        etPassword.setText(prefs.getString("password_usuario", ""));
        etNombreCompleto.setText(prefs.getString("nombre_completo", ""));
        etEmail.setText(prefs.getString("email", ""));

        int puntosGuardados = prefs.getInt("puntos_usuario", 0);
        if (tvMisPuntos != null) {
            tvMisPuntos.setText("Puntos contribuidos: " + puntosGuardados);
        }

        int facIdGuardada = prefs.getInt("facultad_id", 1);
        descargarFacultades(facIdGuardada);

        // =========================================================
        // --- DESCARGAR IMAGEN DEL SERVIDOR (IGUAL QUE EN DAS_proyecto) ---
        // =========================================================
        // El timestamp (?v=...) evita que Android guarde en caché la foto antigua
        String urlImagen = "http://34.175.63.186:81/uploads/" + usernameGuardado + ".jpg?v=" + System.currentTimeMillis();

        new Thread(() -> {
            try {
                java.net.URL destino = new java.net.URL(urlImagen);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) destino.openConnection();
                int responseCode = conn.getResponseCode();
                if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                    Bitmap elBitmap = BitmapFactory.decodeStream(conn.getInputStream());
                    requireActivity().runOnUiThread(() -> {
                        if (imgPerfil != null && elBitmap != null) {
                            imgPerfil.setImageBitmap(elBitmap);
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error descargando la foto del servidor", e);
            }
        }).start();
        // =========================================================

        // --- SOLUCIÓN DE CÁMARA IGUAL QUE EN TU OTRO PROYECTO ---
        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == requireActivity().RESULT_OK) {
                        try {
                            int reqWidth = 500;
                            int reqHeight = 500;

                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inJustDecodeBounds = true;
                            InputStream is = requireActivity().getContentResolver().openInputStream(uriImagen);
                            BitmapFactory.decodeStream(is, null, options);
                            is.close();

                            int width = options.outWidth;
                            int height = options.outHeight;
                            int inSampleSize = 1;

                            if (height > reqHeight || width > reqWidth) {
                                int halfHeight = height / 2;
                                int halfWidth = width / 2;
                                while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                                    inSampleSize *= 2;
                                }
                            }

                            options.inJustDecodeBounds = false;
                            options.inSampleSize = inSampleSize;

                            InputStream is2 = requireActivity().getContentResolver().openInputStream(uriImagen);
                            Bitmap bitmapReducido = BitmapFactory.decodeStream(is2, null, options);
                            is2.close();

                            int anchoImagen = bitmapReducido.getWidth();
                            int altoImagen = bitmapReducido.getHeight();
                            float ratioImagen = (float) anchoImagen / (float) altoImagen;
                            float ratioDestino = (float) reqWidth / (float) reqHeight;
                            int anchoFinal = reqWidth;
                            int altoFinal = reqHeight;

                            if (ratioDestino > ratioImagen) {
                                anchoFinal = (int) (reqHeight * ratioImagen);
                            } else {
                                altoFinal = (int) (reqWidth / ratioImagen);
                            }

                            bitmapFotoActual = Bitmap.createScaledBitmap(bitmapReducido, anchoFinal, altoFinal, true);
                            imgPerfil.setImageBitmap(bitmapFotoActual);
                            bitmapReducido.recycle();
                        } catch (Exception e) {
                            Log.e(TAG, "Error cargando imagen", e);
                        }
                    }
                });

        pedirPermisoCamara = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) { abrirCamara(); }
                    else { Toast.makeText(getContext(), "Permisos de cámara denegados", Toast.LENGTH_SHORT).show(); }
                });

        // Listeners
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

    private void abrirCamara() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String nombrefich = "IMG_" + timeStamp + "_";
        File directorio = requireActivity().getFilesDir(); // Método usado en tu otro proyecto
        try {
            File fichImg = File.createTempFile(nombrefich, ".jpg", directorio);
            // IMPORTANTE: Aquí se enlaza con el AndroidManifest de miau
            uriImagen = FileProvider.getUriForFile(requireContext(), "com.das.miau.provider", fichImg);
            Intent elIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            elIntent.putExtra(MediaStore.EXTRA_OUTPUT, uriImagen);
            takePictureLauncher.launch(elIntent);
        } catch (IOException e) {
            e.printStackTrace();
        }
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
                    listaIdsFacultades.clear();

                    for (Object obj : jsonArray) {
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

                        int posicionASeleccionar = listaIdsFacultades.indexOf(idGuardado);
                        if (posicionASeleccionar != -1) {
                            spinnerFacultad.setSelection(posicionASeleccionar);
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error en descargarFacultades", e);
            }
        }).start();
    }

    private void subirPerfilAlServidor() {
        String nuevoUsername = etUsuario.getText().toString();
        String nuevoNombre = etNombreCompleto.getText().toString();
        String nuevoEmail = etEmail.getText().toString();
        String nuevaPass = etPassword.getText().toString();
        int pos = spinnerFacultad.getSelectedItemPosition();
        int facultadIdReal = listaIdsFacultades.get(pos);

        Toast.makeText(getContext(), "Guardando cambios...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                String fotoEnBase64 = "";
                if (bitmapFotoActual != null) {
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    bitmapFotoActual.compress(Bitmap.CompressFormat.JPEG, 80, stream);
                    byte[] fototransformada = stream.toByteArray();
                    fotoEnBase64 = Base64.encodeToString(fototransformada, Base64.DEFAULT);
                }

                Uri.Builder builder = new Uri.Builder()
                        .appendQueryParameter("id_usuario", idUsuario)
                        .appendQueryParameter("username", nuevoUsername)
                        .appendQueryParameter("nombre", nuevoNombre)
                        .appendQueryParameter("email", nuevoEmail)
                        .appendQueryParameter("contrasena", nuevaPass)
                        .appendQueryParameter("facultad_id", String.valueOf(facultadIdReal))
                        .appendQueryParameter("imagen", fotoEnBase64);

                String parametrosURL = builder.build().getEncodedQuery();

                java.net.URL url = new java.net.URL("http://34.175.63.186:81/actualizar_perfil.php");
                java.net.HttpURLConnection conexion = (java.net.HttpURLConnection) url.openConnection();
                conexion.setRequestMethod("POST");
                conexion.setDoOutput(true);

                java.io.OutputStream os = conexion.getOutputStream();
                java.io.BufferedWriter writer = new java.io.BufferedWriter(new java.io.OutputStreamWriter(os, "UTF-8"));
                writer.write(parametrosURL);
                writer.flush();
                writer.close();
                os.close();

                if (conexion.getResponseCode() == 200) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Perfil actualizado correctamente", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}