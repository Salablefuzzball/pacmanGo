package com.example.toukolohilahti.pacmango_native.overpass;

/**
 * Data holder class for boundaries of area.
 */
public class Area extends Overpass {
    public double north;
    public double east;
    public double south;
    public double west;

    Area(double north, double east, double south, double west) {
        this.north = north;
        this.east = east;
        this.south = south;
        this.west = west;
    }
}
