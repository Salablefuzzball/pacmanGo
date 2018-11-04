package com.example.toukolohilahti.pacmango_native.overpass;

import android.location.Location;
import android.util.SparseArray;

import com.example.toukolohilahti.pacmango_native.HttpPostQuery;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

public class Overpass {

    private static final String url = "http://overpass-api.de/api/interpreter";

    public Overpass() {

    }

    /**
     * Calculates bounds for area starting from currentPosition.
     * @param currentPosition Location object of current position.
     * @return Area object containing bounds for the area.
     */
    private Area calculateArea(Location currentPosition) {
        final double LAT = currentPosition.getLatitude();
        final double LON = currentPosition.getLongitude();

        final double RADIUS = 2; // km
        final double LAT_IN_KM = 110.574; // One latitude degree in km.
        final double LON_IN_KM = 111.320; // One longitude degree in km.

        double north = LAT + RADIUS/LAT_IN_KM;
        double east = LON + RADIUS/LON_IN_KM*Math.cos(LAT);
        double south = LAT - RADIUS/LAT_IN_KM;
        double west = LON - RADIUS/LON_IN_KM*Math.cos(LAT);

        Area area = new Area(north, east, south, west);
        return area;
    }

    /**
     * Get roads from overpass api for certain area for given position.
     * @param currentPosition Location object of current position.
     * @return List of roads.
     */
    public SparseArray<Road> getRoads(Location currentPosition) {
        Area area = calculateArea(currentPosition);
        final String QUERY = "<osm-script output='json' output-config='' timeout='60'> <union into='_'>"+
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
        SparseArray<Road> roadMap = new SparseArray<>();
        try {
            JSONObject response = overpass.execute(url, QUERY).get();
            JSONArray roads = response.getJSONArray("elements");
            for (int index = 0; index < roads.length(); index++) {
                JSONObject roadObj = roads.getJSONObject(index);
                if (roadObj.getJSONArray("geometry").length() > 1) {
                    Road road = new Road(roadObj.getString("type"), roadObj.getInt("id"), roadObj.getJSONArray("geometry"));
                    roadMap.put(road.hashCode(), road);
                }
            }

        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return roadMap;
    }

}
