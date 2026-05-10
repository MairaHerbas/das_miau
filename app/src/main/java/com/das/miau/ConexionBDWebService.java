package com.das.miau;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;

public class ConexionBDWebService extends Worker {

    public ConexionBDWebService(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String usuario = getInputData().getString("usuario");
        String contrasena = getInputData().getString("contrasena");
        String tipo = getInputData().getString("tipo"); // si es inicio de sesión o registro

        String dir = "http://34.175.63.186:81/" + tipo + ".php";
        HttpURLConnection urlConnection = null;
        String rdo = "";

        try {
            // Configuración de la conexión
            URL destino = new URL(dir);
            urlConnection = (HttpURLConnection) destino.openConnection();
            urlConnection.setConnectTimeout(5000);
            urlConnection.setReadTimeout(5000);

            // Preparamos los parámetros
            Uri.Builder builder = new Uri.Builder()
                    .appendQueryParameter("usuario", usuario)
                    .appendQueryParameter("contrasena", contrasena);
            String parametros = builder.build().getEncodedQuery();

            // Envío
            urlConnection.setRequestMethod("POST");
            urlConnection.setDoOutput(true);
            urlConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            PrintWriter out = new PrintWriter(urlConnection.getOutputStream());
            out.print(parametros);
            out.close();

            // Leer respuesta
            int statusCode = urlConnection.getResponseCode();
            if (statusCode == 200) {
                BufferedInputStream inputStream = new BufferedInputStream(urlConnection.getInputStream());
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    rdo += line;
                }
                inputStream.close();
            }

            // Mandamos respuesta a la actividad
            Data outputData = new Data.Builder().putString("resultado", rdo).build();
            return Result.success(outputData);

        } catch (Exception e) {
            e.printStackTrace();
            return Result.failure();
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }
}