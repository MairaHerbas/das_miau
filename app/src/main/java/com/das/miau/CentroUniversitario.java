package com.das.miau;

public class CentroUniversitario {
    private String nombre;
    private String ubicacion;
    private double latitud;
    private double longitud;

    public CentroUniversitario(String nombre, String ubicacion, double latitud, double longitud) {
        this.nombre = nombre;
        this.ubicacion = ubicacion;
        this.latitud = latitud;
        this.longitud = longitud;
    }

    public String getNombre() { return nombre; }
    public String getUbicacion() { return ubicacion; }
    public double getLatitud() { return latitud; }
    public double getLongitud() { return longitud; }
}