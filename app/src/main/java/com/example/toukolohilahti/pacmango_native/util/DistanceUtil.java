package com.example.toukolohilahti.pacmango_native.util;

import com.example.toukolohilahti.pacmango_native.overpass.Position;

public class DistanceUtil {

    /**
     * Calcilate middle point between given two locations.
     * @param pos1 First position.
     * @param pos2 Second position.
     * @return Calculated position in middle point of two given positions.
     */
    public static Position calculateMidPoint(Position pos1, Position pos2){

        double dLon = Math.toRadians(pos2.lon - pos1.lon);

        //convert to radians
        double lat1 = Math.toRadians(pos1.lat);
        double lat2 = Math.toRadians(pos2.lat);
        double lon1 = Math.toRadians(pos1.lon);

        double Bx = Math.cos(lat2) * Math.cos(dLon);
        double By = Math.cos(lat2) * Math.sin(dLon);
        double lat3 = Math.atan2(Math.sin(lat1) + Math.sin(lat2), Math.sqrt((Math.cos(lat1) + Bx) * (Math.cos(lat1) + Bx) + By * By));
        double lon3 = lon1 + Math.atan2(By, Math.cos(lat1) + Bx);

        return new Position(Math.toDegrees(lat3), Math.toDegrees(lon3));
    }

    /**
     * Calculate distance between two locations.
     * @param pos1 Position of the first place.
     * @param pos2 Position of the second place.
     * @return Returns distance in meters.
     */
    public static double distance(Position pos1, Position pos2) {

        final int R = 6371; // Radius of the earth

        double latDistance = Math.toRadians(pos2.lat - pos1.lat);
        double lonDistance = Math.toRadians(pos2.lon - pos1.lon);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(pos1.lat)) * Math.cos(Math.toRadians(pos2.lat))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c * 1000; // convert to meters

        double height = 0;

        distance = Math.pow(distance, 2) + Math.pow(height, 2);

        return Math.sqrt(distance);
    }
}
