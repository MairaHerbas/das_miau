package com.das.miau;

public class RutaBus {
    private String idRuta;
    private String nombreCorto; // Ej: "A3" o "56"
    private String nombreLargo; // Ej: "Plaza Sagrado Corazón - La Peña"

    public RutaBus(String idRuta, String nombreCorto, String nombreLargo) {
        this.idRuta = idRuta;
        this.nombreCorto = nombreCorto;
        this.nombreLargo = nombreLargo;
    }

    public String getIdRuta() { return idRuta; }
    public String getNombreCorto() { return nombreCorto; }
    public String getNombreLargo() { return nombreLargo; }
}