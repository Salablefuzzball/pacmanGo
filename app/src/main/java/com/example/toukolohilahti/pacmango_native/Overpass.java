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
            this.geometry = posArray;
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

    private Area calculateArea(Location currentPosition) {
        double lat = currentPosition.getLatitude();
        double lon = currentPosition.getLongitude();

        double radius = 2; // km

        double north = lat + radius/110.574;
        double east = lon + radius/111.320*Math.cos(lat);
        double south = lat - radius/110.574;
        double west = lon - radius/111.320*Math.cos(lat);

        Area area = new Area(north, east, south, west);
        return area;
    }

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
