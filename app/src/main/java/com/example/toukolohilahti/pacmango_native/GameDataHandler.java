package com.example.toukolohilahti.pacmango_native;

import android.util.SparseArray;

import com.example.toukolohilahti.pacmango_native.overpass.Road;
import com.github.davidmoten.rtree.Entry;
import com.github.davidmoten.rtree.RTree;
import com.github.davidmoten.rtree.geometry.Point;

public class GameDataHandler {

    //We need two trees because when you eat pac-dots markers need to be removed.
    private RTree<String, Point> markerTree;
    //Navigation tree that will be preserved. Not optimal, try to just use one tree.
    private RTree<String, Point> navigationTree;
    //Key is road.HashCode and the RTree has it as value for position.
    private SparseArray<Road> roadMap;


    public void createRTrees() {
        markerTree = RTree.create();
        navigationTree = RTree.create();
    }

    public void addValueToTrees(Road road, Point point) {
        markerTree = markerTree.add(Integer.toString(road.hashCode()), point);
        navigationTree = navigationTree.add(Integer.toString(road.hashCode()), point);
    }

    public void removeFromTreeMarker(Entry<String, Point> point) {
        markerTree.delete(point);
    }

    public RTree<String, Point> getMarkerTree() {
        return markerTree;
    }

    public RTree<String, Point> getNavigationTree() {
        return navigationTree;
    }

    public SparseArray<Road> getRoadMap() {
        return roadMap;
    }

    public void setRoadMap(SparseArray<Road> roads) {
        roadMap = roads;
    }
}
