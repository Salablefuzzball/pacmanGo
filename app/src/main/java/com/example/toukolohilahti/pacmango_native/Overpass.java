package com.example.toukolohilahti.pacmango_native;

import android.location.Location;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

public class Overpass {

    private static final String url = "https://overpass-api.de/api/interpreter";

    public class Area {
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

    public class Road {
        public String type;
        public int id;
        public ArrayList<Position> geometry;

        public Road(String type, int id, JSONArray geometry) {
            this.type = type;
            this.id = id;


            this.geometry = this.convertToPositionArray(geometry);
            this.geometry = this.fillRoads(this.geometry);
        }

        private ArrayList<Position> fillRoads(ArrayList<Position> positionArrayList) {
            ArrayList<Position> temp = new ArrayList<>();
            for(int index = 1; index < positionArrayList.size(); index++) {
                if(this.distance(positionArrayList.get(index-1), positionArrayList.get(index)) > 30) {
                    temp.add(this.calculateMidPoint(positionArrayList.get(index-1), positionArrayList.get(index)));
                }
            }
            positionArrayList.addAll(temp);
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

    public class Position {
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

    public Overpass() {

    }

    /**
     * Calculates bounds for area starting from currentPosition.
     * @param currentPosition Location object of current position.
     * @return Area object containing bounds for the area.
     */
    private Area calculateArea(Location currentPosition) {
        double lat = currentPosition.getLatitude();
        double lon = currentPosition.getLongitude();

        double radius = 0.3; // km

        double north = lat + radius/110.574;
        double east = lon + radius/111.320*Math.cos(lat);
        double south = lat - radius/110.574;
        double west = lon - radius/111.320*Math.cos(lat);

        Area area = new Area(north, east, south, west);
        return area;
    }

    /**
     * Get roads from overpass api for certain area for given position.
     * @param currentPosition Location object of current position.
     * @return List of roads.
     */
    public ArrayList<Road> getRoads(Location currentPosition) {
        Area area = calculateArea(currentPosition);
        String query = "<osm-script output='json' output-config='' timeout='60'> <union into='_'>"+
                "<query into='_' type='way'><has-kv k='highway' modv='' v='pedestrian'/>"+
                "<bbox-query s='"+ area.south +"' w='"+ area.west +"' n='"+ area.north +"' e='"+ area.east +"'/>"+
                "</query><query into='_' type='way'><has-kv k='highway' modv='' v='residential'/>"+
                "<bbox-query s='"+ area.south +"' w='"+ area.west +"' n='"+ area.north +"' e='"+ area.east +"'/>"+
                "</query><query into='_' type='way'><has-kv k='highway' modv='' v='secondary'/>"+
                "<bbox-query s='"+ area.south +"' w='"+ area.west +"' n='"+ area.north +"' e='"+ area.east +"'/>"+
                "</query><query into='_' type='way'><has-kv k='highway' modv='' v='tertiary'/>"+
                "<bbox-query s='"+ area.south +"' w='"+ area.west +"' n='"+ area.north +"' e='"+ area.east +"'/>"+
                "</query></union><print e='' from='_' geometry='full' ids='yes' limit='' mode='ids_only' n='' order='id' s='' w=''/>"+
                "<recurse from='_' into='_' type='down'/><print e='' from='_' geometry='skeleton' ids='yes' limit='' mode='skeleton' n='' order='quadtile' s='' w=''/></osm-script>";

        HttpPostQuery overpass = new HttpPostQuery();
        ArrayList<Road> roadList = new ArrayList<>();
        try {
            JSONObject response = overpass.execute(url, query).get();
            JSONArray roads = response.getJSONArray("elements");
            for (int index = 0; index < roads.length(); index++) {
                JSONObject roadObj = roads.getJSONObject(index);
                Road road = new Road(roadObj.getString("type"), roadObj.getInt("id"), roadObj.getJSONArray("geometry"));
                roadList.add(road);
            }

        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return roadList;
    }

}
