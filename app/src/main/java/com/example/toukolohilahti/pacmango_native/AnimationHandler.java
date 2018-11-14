package com.example.toukolohilahti.pacmango_native;

import android.animation.ObjectAnimator;
import android.animation.TypeEvaluator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.location.Location;
import android.os.Handler;
import android.util.SparseArray;

import com.example.toukolohilahti.pacmango_native.overpass.Position;
import com.example.toukolohilahti.pacmango_native.overpass.Road;
import com.example.toukolohilahti.pacmango_native.util.DistanceUtil;
import com.example.toukolohilahti.pacmango_native.util.LoopDirection;
import com.github.davidmoten.rtree.Entry;
import com.github.davidmoten.rtree.RTree;
import com.github.davidmoten.rtree.geometry.Geometries;
import com.github.davidmoten.rtree.geometry.Point;
import com.mapbox.android.core.location.LocationEngine;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.geometry.LatLng;

import java.util.ArrayList;
import java.util.List;

import rx.Observable;
import timber.log.Timber;

public class AnimationHandler {

    private LocationEngine locationEngine;

    private GameDataHandler gameDataHandler;

    private GameOverListener gameOverListener;

    private List<Runnable> ghostRunnables = new ArrayList<>();

    private List<Handler> ghostHandlers = new ArrayList<>();

    private static final int DEATH_RADIUS = 10;

    public AnimationHandler(GameDataHandler gameDataHandler, GameOverListener gameOverListener) {
        this.gameDataHandler = gameDataHandler;
        this.gameOverListener = gameOverListener;
    }

    public void animate(Marker ghost, Road currentRoad, LoopDirection drct, int indx) {
        Handler handler = new Handler();

        ghostHandlers.add(handler);
        int delay = 1000; //milliseconds

        final Runnable runnable = new Runnable(){

            int index = indx;
            int searchDist = 75;
            int searchCount = 5;
            int prevMarkerIndex = 0;

            Road road = currentRoad;
            LatLng ghostPos = ghost.getPosition();
            LoopDirection direction = drct;
            ArrayList<Entry<String, Point>> prevMarkers = new ArrayList<>();

            public void run() {
                SparseArray<Road> roadMap = gameDataHandler.getRoadMap();
                Location location = new Location("DummyProvider");
                location.setLatitude(ghostPos.getLatitude());
                location.setLongitude(ghostPos.getLongitude());

                Iterable<Entry<String, Point>> nearestMarkers = findNearestMarkers(location, searchDist, searchCount);
                Iterable<Entry<String, Point>> filteredMarkers  = filterNearestMarkers(nearestMarkers, prevMarkers);
                Entry<String, Point> roadMarker = findNearestRoadToUser(filteredMarkers, currentRoad);

                if (roadMarker != null) {
                    Road newRoad = roadMap.get(Integer.parseInt(roadMarker.value()));

                    //If it is a new road then calculate new index and direction
                    if (!newRoad.equals(road)) {
                        road = newRoad;
                        index = getIndexFromRoad(roadMarker);
                        direction = findDirection(road.geometry, index);
                    }
                }

                //Lets make sure our app does not crash
                if ((direction.equals(LoopDirection.BACKWARD) && index >= 0) || (direction.equals(LoopDirection.FORWARD) && road.geometry.size() > index)) {
                    searchDist = 75;
                    searchCount = 5;

                    prevMarkers.add(prevMarkerIndex, roadMarker);

                    if (prevMarkerIndex == 9) {
                        prevMarkerIndex = 0;
                    } else {
                        prevMarkerIndex++;
                    }

                    Position loc = road.geometry.get(index);
                    ghostPos = new LatLng(loc.lat, loc.lon);

                    ValueAnimator markerAnimator = ObjectAnimator.ofObject(ghost, "position",
                            new LatLngEvaluator(), ghost.getPosition(), ghostPos);
                    markerAnimator.setDuration(3000);
                    markerAnimator.start();

                    //Loop the arrayList in the correct direction
                    if (direction.equals(LoopDirection.BACKWARD)) {
                        index--;
                    } else {
                        index++;
                    }
                } else {
                    //Cheap tricks to prevent ghosts getting stuck.
                    Timber.d("Stuck");
                    searchDist += 125;
                    searchCount += 15;
                }

                if (checkIfGameOver(ghostPos)) {
                    gameOverListener.gameOver();
                } else {
                    handler.postDelayed(this, delay);
                }
            }
        };

        ghostRunnables.add(runnable);
        handler.postDelayed(runnable, delay);
    }

    /**
     * Filter markers so ghosts cant go to previous direction.
     *
     * @param nearestMarkers nearest markers that were found.
     * @param prevMarkers previous locations.
     * @return filtered markers.
     */
    private Iterable<Entry<String, Point>> filterNearestMarkers(Iterable<Entry<String, Point>> nearestMarkers, ArrayList<Entry<String, Point>> prevMarkers) {
        ArrayList<Entry<String, Point>> filteredMarkers = new ArrayList<>();
        for (Entry<String, Point> marker: nearestMarkers) {
            if (!prevMarkers.contains(marker)) {
                filteredMarkers.add(marker);
            }
        }

        return filteredMarkers;
    }

    /**
     * Class is used to interpolate the marker animation.
     */
    private static class LatLngEvaluator implements TypeEvaluator<LatLng> {
        private LatLng latLng = new LatLng();

        @Override
        public LatLng evaluate(float fraction, LatLng startValue, LatLng endValue) {
            latLng.setLatitude(startValue.getLatitude()
                    + ((endValue.getLatitude() - startValue.getLatitude()) * fraction));
            latLng.setLongitude(startValue.getLongitude()
                    + ((endValue.getLongitude() - startValue.getLongitude()) * fraction));
            return latLng;
        }
    }

    private boolean checkIfGameOver(LatLng loc) {
        @SuppressLint("MissingPermission") Location userLoc = locationEngine.getLastLocation();
        double lat1 = userLoc.getLatitude();
        double lon1 = userLoc.getLongitude();
        double lat2 = loc.getLatitude();
        double lon2 = loc.getLongitude();

        return DEATH_RADIUS > DistanceUtil.distance(lat1, lon1, lat2, lon2);

    }

    /**
     * Find which road is nearest to the user based on the markers found near.
     * Prioritize new roads so does not get stuck so easily. Use same road if no options.
     * Clean up this also.
     * @param markers nearest markers to the user.
     * @return RTree node which contains roads key so we find it from the Map.
     */
    private Entry<String, Point> findNearestRoadToUser(Iterable<Entry<String, Point>> markers, Road currentRoad) {
        @SuppressLint("MissingPermission") Location userLoc = locationEngine.getLastLocation();
        SparseArray<Road> roadMap = gameDataHandler.getRoadMap();

        double userLat = userLoc.getLatitude();
        double userLon = userLoc.getLongitude();
        double minDist = 0.0;
        boolean initialized = false;

        Entry<String, Point> nearestRoadMarker = null;
        Entry<String, Point> backupMarker = null;

        for(Entry<String, Point> marker: markers) {
            double markerLat = marker.geometry().x();
            double markerLon = marker.geometry().y();
            double dist = DistanceUtil.distance(userLat, userLon, markerLat, markerLon);

            Road road = roadMap.get(Integer.parseInt(marker.value()));
            backupMarker = marker;

            if (!currentRoad.equals(road)) {
                if (initialized && dist < minDist) {
                    minDist = dist;
                    nearestRoadMarker = marker;
                } else if (!initialized){
                    minDist = dist;
                    nearestRoadMarker = marker;
                    initialized = true;
                }
            }
        }

        if (nearestRoadMarker == null) {
            Timber.d("No new roads found");
            return backupMarker;
        }

        return nearestRoadMarker;
    }

    /**
     * When we turn to new road find out where that is in the list.
     * @param marker The point we go next.
     * @return Index to find correct position in the list.
     */
    private int getIndexFromRoad(Entry<String, Point> marker) {
        SparseArray<Road> roadMap = gameDataHandler.getRoadMap();
        Road road = roadMap.get(Integer.parseInt(marker.value()));
        Point point = marker.geometry();

        int index = 0;
        for(Position pos: road.geometry) {
            if (pos.lat == point.x() && pos.lon == point.y()) {
                return index;
            }
            index++;
        }

        return 0;
    }

    /**
     * Find the nearest markers to a location from a rTree.
     *
     * @param loc Ghost location.
     * @param dist Distance we want to search from.
     * @param count How many we want to search for.
     * @return found markers.
     */
    private Iterable<Entry<String, Point>> findNearestMarkers(Location loc, double dist, int count) {
        RTree<String, Point> tree = gameDataHandler.getNavigationTree();
        Observable<Entry<String, Point>> entries = tree.nearest(Geometries.point(loc.getLatitude(),loc.getLongitude()), dist, count);

        return entries.toBlocking().toIterable();
    }

    /**
     * When game ends stop ghost animation threads.
     */
    public void stopGhostAnimation() {
        for (int i = 0; i<ghostHandlers.size(); i++) {
            Handler handler = ghostHandlers.get(i);
            Runnable runnable = ghostRunnables.get(i);
            handler.removeCallbacks(runnable);
        }
    }

    /**
     * Find in which direction the ghost must start moving. Refactor.
     *
     * @param roadMarkers points on road.
     * @param index nearest markers index.
     * @return the direction where to loop.
     */
    public LoopDirection findDirection(ArrayList<Position> roadMarkers, int index) {
        @SuppressLint("MissingPermission") Location userLoc = locationEngine.getLastLocation();

        double userLat = userLoc.getLatitude();
        double userLon = userLoc.getLongitude();

        if (index != 0) {
            Position start = roadMarkers.get(index);
            Position end = roadMarkers.get(index - 1);
            if (DistanceUtil.distance(userLat, userLon, start.lat, start.lon) > DistanceUtil.distance(userLat, userLon, end.lat, end.lon)) {
                return LoopDirection.BACKWARD;
            } else {
                return LoopDirection.FORWARD;
            }
        } else {
            Position start = roadMarkers.get(index);
            Position end = roadMarkers.get(index + 1);
            if (DistanceUtil.distance(userLat, userLon, start.lat, start.lon) > DistanceUtil.distance(userLat, userLon, end.lat, end.lon)) {
                return LoopDirection.FORWARD;
            } else {
                return LoopDirection.BACKWARD;
            }
        }

    }

    public void setLocationEngine(LocationEngine locationEngine) {
        this.locationEngine = locationEngine;
    }
}
