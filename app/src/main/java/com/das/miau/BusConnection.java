package com.das.miau;

public class BusConnection {
    private String line;
    private BusStop originStop;
    private BusStop destinationStop;
    private double totalTimeSec;

    public BusConnection(String line, BusStop originStop, BusStop destinationStop) {
        this.line = line;
        this.originStop = originStop;
        this.destinationStop = destinationStop;
    }

    public String getLine() { return line; }
    public BusStop getOriginStop() { return originStop; }
    public BusStop getDestinationStop() { return destinationStop; }
    public double getTotalTimeSec() { return totalTimeSec; }
    public void setTotalTimeSec(double t) { this.totalTimeSec = t; }
}