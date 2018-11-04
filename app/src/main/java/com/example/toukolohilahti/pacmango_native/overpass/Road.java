package com.example.toukolohilahti.pacmango_native.overpass;

import android.os.Parcel;
import android.os.Parcelable;

import com.example.toukolohilahti.pacmango_native.util.DistanceUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Data holder class for road. Contains type, id and geometry of road.
 */
public class Road extends Overpass implements Parcelable {
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
                if(DistanceUtil.distance(positionArrayList.get(index-1), positionArrayList.get(index)) > 15) {
                    positionArrayList.add(index, DistanceUtil.calculateMidPoint(positionArrayList.get(index-1), positionArrayList.get(index)));
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

    @Override
    public String toString() {
        return "Road{" +
                "type='" + type + '\'' +
                ", id=" + id +
                ", geometry=" + geometry +
                '}';
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {

    }
}
