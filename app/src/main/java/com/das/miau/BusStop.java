package com.das.miau;

import java.util.ArrayList;
import java.util.List;

public class BusStop {
    private String stopId;
    private String stopName;
    private double lat;
    private double lon;
    private List<String> lines;
    private String network;

    public BusStop(String stopId, String stopName, double lat, double lon) {
        this.stopId = stopId;
        this.stopName = stopName;
        this.lat = lat;
        this.lon = lon;
        this.lines = null;
    }

    public String getStopId() { return stopId; }
    public String getStopName() { return stopName; }
    public double getLat() { return lat; }
    public double getLon() { return lon; }
    public List<String> getLines() { return lines; }
    public void setLines(List<String> lines) { this.lines = lines; }

    public String getNetwork() { return network; }
    public void setNetwork(String network) { this.network = network; }
}