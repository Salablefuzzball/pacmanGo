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
    }

    public class Position {
        public double lat;
        public double lon;
    }

    private Overpass() {

    }

    private Area calculateArea(Location currentPosition) {
        double lat = currentPosition.getLatitude();
        double lon = currentPosition.getLongitude();

        double R = 6371;  // earth radius in km

        double radius = 2; // km
        double x1 = lon - Math.toDegrees(radius/R/Math.cos(Math.toRadians(lat)));
        double x2 = lon + Math.toDegrees(radius/R/Math.cos(Math.toRadians(lat)));
        double y1 = lat + Math.toDegrees(radius/R);
        double y2 = lat - Math.toDegrees(radius/R);

        Area area = new Area();
    }

    public void getRoads(Location currentPosition) {
        ArrayList params = new ArrayList<>();
        params.add(url);
        params.add(calculateArea(currentPosition));

        /*OverpassQuery query = new OverpassQuery();
        query.execute(url, "");*/
    }

}
