package com.example.toukolohilahti.pacmango_native.Overpass;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Data holder class for road. Contains type, id and geometry of road.
 */
public class Road extends Overpass {
    public String type;
    public int id;
    public ArrayList<Position> geometry;

    public Road(String type, int id, JSONArray geometry) {
        this.type = type;
        this.id = id;
        this.geometry = this.convertToPositionArray(geometry);
        this.geometry = this.fillRoads(this.geometry);
    }

    /**
     * Generates gps points between start and end of road vector.
     * @param positionArrayList List of positions for road.
     * @return List with added positions.
     */
    private ArrayList<Position> fillRoads(ArrayList<Position> positionArrayList) {
        int addedItems = -1;
        // Keep looping until there is no elements added.
        while (addedItems != 0) {
            int tempAddedItems = positionArrayList.size();
            for(int index = 1; index < positionArrayList.size(); index++) {
                if(this.distance(positionArrayList.get(index-1), positionArrayList.get(index)) > 15) {
                    positionArrayList.add(index, this.calculateMidPoint(positionArrayList.get(index-1), positionArrayList.get(index)));
                    index++;
                }
                addedItems = positionArrayList.size() - tempAddedItems;
            }
        }

        return positionArrayList;
    }

    /**
     * Convert given JSONArray to position arraylist.
     * @param geometry JSONArray containing JSONObjects of gps locations.
     * @return Position arrayList.
     */
    private ArrayList<Position> convertToPositionArray(JSONArray geometry) {
        ArrayList<Position> posArray = new ArrayList<>();
        for (int index = 0; index < geometry.length(); index++) {
            JSONObject geometryObj = null;
            try {
                geometryObj = geometry.getJSONObject(index);
                Position pos = new Position(geometryObj.getDouble("lat"), geometryObj.getDouble("lon"));
                posArray.add(pos);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return posArray;
    }

    /**
     * Calcilate middle point between given two locations.
     * @param pos1 First position.
     * @param pos2 Second position.
     * @return Calculated position in middle point of two given positions.
     */
    private Position calculateMidPoint(Position pos1, Position pos2){

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
    private double distance(Position pos1, Position pos2) {

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


    @Override
    public String toString() {
        return "Road{" +
                "type='" + type + '\'' +
                ", id=" + id +
                ", geometry=" + geometry +
                '}';
    }
}
