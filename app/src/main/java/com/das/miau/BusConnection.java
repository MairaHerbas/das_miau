package com.das.miau;

import java.util.List;

public class BusConnection {
    private String line;
    private BusStop originStop;
    private BusStop destinationStop;
    private double totalTimeSec;
    
    // Nuevos campos para UI detallada
    private double walkToOriginSec;
    private double waitTimeSec;
    private double rideTimeSec;
    private double walkToDestSec;
    private float walkOriginMeters;
    private float walkDestMeters;
    private List<Long> nextDeparturesMin;

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

    public double getWalkToOriginSec() { return walkToOriginSec; }
    public void setWalkToOriginSec(double walkToOriginSec) { this.walkToOriginSec = walkToOriginSec; }

    public double getWaitTimeSec() { return waitTimeSec; }
    public void setWaitTimeSec(double waitTimeSec) { this.waitTimeSec = waitTimeSec; }

    public double getRideTimeSec() { return rideTimeSec; }
    public void setRideTimeSec(double rideTimeSec) { this.rideTimeSec = rideTimeSec; }

    public double getWalkToDestSec() { return walkToDestSec; }
    public void setWalkToDestSec(double walkToDestSec) { this.walkToDestSec = walkToDestSec; }

    public float getWalkOriginMeters() { return walkOriginMeters; }
    public void setWalkOriginMeters(float walkOriginMeters) { this.walkOriginMeters = walkOriginMeters; }

    public float getWalkDestMeters() { return walkDestMeters; }
    public void setWalkDestMeters(float walkDestMeters) { this.walkDestMeters = walkDestMeters; }

    public List<Long> getNextDeparturesMin() { return nextDeparturesMin; }
    public void setNextDeparturesMin(List<Long> nextDeparturesMin) { this.nextDeparturesMin = nextDeparturesMin; }
}