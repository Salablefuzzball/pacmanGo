package com.example.toukolohilahti.pacmango_native.overpass;

public class Position extends Overpass {
    public double lat;
    public double lon;

    public Position(double lat, double lon) {
        this.lat = lat;
        this.lon = lon;
    }

    @Override
    public String toString() {
        return "Position{" +
                "lat=" + lat +
                ", lon=" + lon +
                '}';
    }
}