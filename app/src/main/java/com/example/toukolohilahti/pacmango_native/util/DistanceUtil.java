package com.example.toukolohilahti.pacmango_native.util;

import com.example.toukolohilahti.pacmango_native.overpass.Position;

public class DistanceUtil {

    /**
     * Calcilate middle point between given two locations.
     * @param lat1 first loc lat
     * @param lon1 first loc lon
     * @param lat2 second loc lat
     * @param lon2 second loc lon
     * @return Calculated position in middle point of two given positions.
     */
    public static Position calculateMidPoint(double lat1, double lon1, double lat2, double lon2){
        double dLon = Math.toRadians(lon2 - lon1);

        //convert to radians
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);
        double radLon1 = Math.toRadians(lon1);

        double Bx = Math.cos(radLat2) * Math.cos(dLon);
        double By = Math.cos(radLat2) * Math.sin(dLon);
        double lat3 = Math.atan2(Math.sin(radLat1) + Math.sin(radLat2), Math.sqrt((Math.cos(radLat1) + Bx) * (Math.cos(radLat1) + Bx) + By * By));
        double lon3 = radLon1 + Math.atan2(By, Math.cos(radLat1) + Bx);

        return new Position(Math.toDegrees(lat3), Math.toDegrees(lon3));
    }

    /**
     * Calculate distance between two locations.
     * @param lat1 first loc lat
     * @param lon1 first loc lon
     * @param lat2 second loc lat
     * @param lon2 second loc lon
     * @return Returns distance in meters
     */
    public static double distance(double lat1, double lon1, double lat2, double lon2) {
        Position pos1 = new Position(lat1, lon1);
        Position pos2 = new Position(lat2, lon2);
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
