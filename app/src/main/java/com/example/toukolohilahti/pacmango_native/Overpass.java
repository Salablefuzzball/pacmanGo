package com.example.toukolohilahti.pacmango_native;

import android.location.Location;

import java.util.ArrayList;

public class Overpass {

    private static final String url = "https://overpass-api.de/api/interpreter'";

    public class Area {
        public Position north;
        public Position east;
        public Position south;
        public Position west;

        Area(Position north, Position east, Position south, Position west) {
            this.north = north;
            this.east = east;
            this.south = south;
            this.west = west;
        }

        @Override
        public String toString() {
            return "Area{" +
                    "north=" + north +
                    ", east=" + east +
                    ", south=" + south +
                    ", west=" + west +
                    '}';
        }
    }

    public class Position {
        public double lat;
        public double lon;

        Position(double lat, double lon) {
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

    public Overpass() {

    }

    private Area calculateArea(Location currentPosition) {
        double lat = currentPosition.getLatitude();
        double lon = currentPosition.getLongitude();

        double radius = 2; // km

        double northLat = lat + radius/110.574;
        double northLon = lon;
        Position north = new Position(northLat, northLon);

        double eastLat = lat;
        double eastLon = lon + radius/111.320*Math.cos(lat);
        Position east = new Position(eastLat, eastLon);

        double southLat = lat - radius/110.574;
        double southLon = lon;
        Position south = new Position(southLat, southLon);

        double westLat = lat;
        double westLon = lon - radius/111.320*Math.cos(lat);
        Position west = new Position(westLat, westLon);

        Area area = new Area(north, east, south, west);
        return area;
    }

    public void getRoads(Location currentPosition) {
        ArrayList params = new ArrayList<>();
        params.add(url);
        params.add(calculateArea(currentPosition));

        /*OverpassQuery query = new OverpassQuery();
        query.execute(url, "");*/
    }

}
