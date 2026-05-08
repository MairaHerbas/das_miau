package com.das.miau;

public class BusConnection {
    private String line;
    private BusStop originStop;
    private BusStop destinationStop;

    public BusConnection(String line, BusStop originStop, BusStop destinationStop) {
        this.line = line;
        this.originStop = originStop;
        this.destinationStop = destinationStop;
    }

    public String getLine() { return line; }
    public BusStop getOriginStop() { return originStop; }
    public BusStop getDestinationStop() { return destinationStop; }
}