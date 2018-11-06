package com.example.toukolohilahti.pacmango_native;

import android.animation.ObjectAnimator;
import android.animation.TypeEvaluator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.provider.CalendarContract;
import android.support.annotation.NonNull;
import android.support.multidex.MultiDex;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.toukolohilahti.pacmango_native.overpass.Overpass;
import com.example.toukolohilahti.pacmango_native.overpass.Position;
import com.example.toukolohilahti.pacmango_native.overpass.Road;
import com.example.toukolohilahti.pacmango_native.util.DistanceUtil;
import com.example.toukolohilahti.pacmango_native.util.LoopDirection;
import com.github.davidmoten.rtree.Entry;
import com.github.davidmoten.rtree.RTree;
import com.github.davidmoten.rtree.geometry.Geometries;
import com.github.davidmoten.rtree.geometry.Point;
import com.mapbox.android.core.location.LocationEngine;
import com.mapbox.android.core.location.LocationEngineListener;
import com.mapbox.android.core.location.LocationEnginePriority;
import com.mapbox.android.core.location.LocationEngineProvider;
import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerOptions;
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerPlugin;
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.CameraMode;
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.RenderMode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import rx.Observable;

public class MapActivity extends AppCompatActivity implements LocationEngineListener, PermissionsListener {

    private MapView mapView;
    private MapboxMap mapboxMap;
    private PermissionsManager permissionsManager;
    private LocationLayerPlugin locationLayerPlugin;
    private LocationEngine locationEngine;
    private Location originLocation;
    private SparseArray<MarkerOptions> markers;
    private int gameTime = 60;

    //We need two trees because when you eat pac-dots
    //markers need to be removed.
    private RTree<String, Point> rTreeMarker;
    //Navigation tree that will be preserved. Not optimal, try to just use one tree.
    private RTree<String, Point> rTreeNavigation;

    //Key is road.HashCode and the RTree has it as value for position.
    private SparseArray<Road> roadMap;

    ProgressDialog pd;

    //meters
    private static final int EATING_DISTANCE = 5;
    private static final int DEATH_RADIUS = 20;
    private static final String FONT_URL = "fonts/ARCADE.ttf";
    private static final int [] GHOSTS = new int [] {R.mipmap.green_ghost, R.mipmap.red_ghost,
            R.mipmap.pink_ghost, R.mipmap.yellow_ghost};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        MapActivity self = this;
        super.onCreate(savedInstanceState);

        pd = new ProgressDialog(this);

        Mapbox.getInstance(this, getString(R.string.mapbox_access_token));
        setContentView(R.layout.activity_map);

        //Set custom toolbar
        Toolbar mTopToolbar = findViewById(R.id.toolbar_top);
        setSupportActionBar(mTopToolbar);

        //Set custom Arcade font
        TextView title = findViewById(R.id.toolbar_title);
        setTypeface(title);

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);

        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(MapboxMap mapboxMap) {
                self.mapboxMap = mapboxMap;
                hideMapboxAttributes();
                enableLocationPlugin();
                //Consider using these, maybe more immersed gameplay?
                //mapboxMap.getUiSettings().setZoomControlsEnabled(false);
                //mapboxMap.getUiSettings().setZoomGesturesEnabled(false);
                rTreeMarker = RTree.create();
                rTreeNavigation = RTree.create();
                markers = new SparseArray<MarkerOptions>();
                createMarkers();
            }
        });
    }

    private void createGhosts() {
        for (int ghost: GHOSTS) {
            createGhost(ghost);
        }
    }

    private void createGhost(int ghost) {
        //Add it to a random location
        int randomRoad = ThreadLocalRandom.current().nextInt(0, roadMap.size());
        Road road = roadMap.valueAt(randomRoad);
        int randomRoadSection = ThreadLocalRandom.current().nextInt(0, road.geometry.size());
        Position loc = road.geometry.get(randomRoadSection);

        LoopDirection direction = findDirection(road.geometry, randomRoadSection);

        MarkerOptions options = new MarkerOptions();
        LatLng pos = new LatLng();
        pos.setLatitude(loc.lat);
        pos.setLongitude(loc.lon);
        options.setPosition(pos);
        IconFactory iconFactory = IconFactory.getInstance(MapActivity.this);
        Icon icon = iconFactory.fromResource(ghost);
        options.setIcon(icon);
        Marker ghostMarker = mapboxMap.addMarker(options);

        animateGhost(ghostMarker, road, direction, randomRoadSection);
    }

    private void animateGhost(Marker ghost, Road currentRoad, LoopDirection drct, int indx) {
        Handler handler = new Handler();
        int delay = 1000; //milliseconds

        handler.postDelayed(new Runnable(){
            int index = indx;
            Road road = currentRoad;
            LatLng ghostPos = ghost.getPosition();
            LoopDirection direction = drct;
            int searchDist = 75;
            int searchCount = 5;

            public void run() {
                //We have way too many 'Location' objects that
                Location location = new Location("DummyProvider");
                location.setLatitude(ghostPos.getLatitude());
                location.setLongitude(ghostPos.getLongitude());

                Iterable<Entry<String, Point>> nearestMarkers = findNearestMarkers(location, searchDist, searchCount);
                Entry<String, Point> roadMarker = findNearestRoadToUser(nearestMarkers, currentRoad);

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

                    Position loc = road.geometry.get(index);
                    ghostPos = new LatLng(loc.lat, loc.lon);

                    ValueAnimator markerAnimator = ObjectAnimator.ofObject(ghost, "position",
                            new LatLngEvaluator(), ghost.getPosition(), ghostPos);
                    markerAnimator.setDuration(3000);
                    markerAnimator.start();

                    //checkIfGameOver(ghostPos);

                    //Loop the arrayList in the correct direction
                    if (direction.equals(LoopDirection.BACKWARD)) {
                        index--;
                    } else {
                        index++;
                    }
                } else {
                    //Cheap tricks to prevent ghosts getting stuck.
                    System.out.println("Stuck");
                    searchDist += 125;
                    searchCount += 15;
                }

                handler.postDelayed(this, delay);
            }
        }, delay);
    }

    private void checkIfGameOver(LatLng loc) {
        @SuppressLint("MissingPermission") Location userLoc = locationEngine.getLastLocation();
        double lat1 = userLoc.getLatitude();
        double lon1 = userLoc.getLongitude();
        double lat2 = loc.getLatitude();
        double lon2 = loc.getLongitude();

        if (DEATH_RADIUS > DistanceUtil.distance(lat1, lon1, lat2, lon2)) {
            gameOver();
        }
    }

    private void gameOver() {
        //Just show something for now.
        AlertDialog.Builder builder;
        builder = new AlertDialog.Builder(MapActivity.this, android.R.style.Theme_Material_Dialog_Alert);

        builder.setTitle("YOU ARE DEAD")
                .setMessage("DO YOU UNDERSTAND??")
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // continue with delete
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // do nothing
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    /**
     * When we turn to new road find out where that is in the list.
     * @param marker The point we go next.
     * @return Index to find correct position in the list.
     */
    private int getIndexFromRoad(Entry<String, Point> marker) {
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
     * Find in which direction the ghost must start moving. Refactor.
     *
     * @param roadMarkers points on road.
     * @param index nearest markers index.
     * @return the direction where to loop.
     */
    private LoopDirection findDirection(ArrayList<Position> roadMarkers, int index) {
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

    /**
     * Find which road is nearest to the user based on the markers found near.
     * Prioritize new roads so does not get stuck so easily. Use same road if no options.
     * Clean up this also.
     * @param markers nearest markers to the user.
     * @return RTree node which contains roads key so we find it from the Map.
     */
    private Entry<String, Point> findNearestRoadToUser(Iterable<Entry<String, Point>> markers, Road currentRoad) {
        @SuppressLint("MissingPermission") Location userLoc = locationEngine.getLastLocation();
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
            System.out.println("No new roads found");
            return backupMarker;
        }

        return nearestRoadMarker;
    }



    private void hideMapboxAttributes() {
        mapboxMap.getUiSettings().setAttributionEnabled(false);
        mapboxMap.getUiSettings().setLogoEnabled(false);
    }

    private void createMarkers() {
        @SuppressLint("HandlerLeak")
        final Handler handler = new Handler() {

            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                pd.setMessage(getString(R.string.generating_spinner_title));
                Road road = msg.getData().getParcelable("road");
                int isLast = msg.getData().getInt("last");
                if(road != null) {
                    for (Position loc : road.geometry) {
                        MarkerOptions options = createMarkerOptions(loc);
                        mapboxMap.addMarker(options);
                        Point point = Geometries.point(loc.lat, loc.lon);
                        rTreeMarker = rTreeMarker.add(Integer.toString(road.hashCode()), point);
                        rTreeNavigation = rTreeNavigation.add(Integer.toString(road.hashCode()), point);
                        markers.put(point.hashCode(), options);
                    }
                }

                if (isLast == 1) {
                    pd.dismiss();
                    startGame();
                }
            }
        };

        pd.setMessage(getString(R.string.loading_spinner_title));
        pd.show();

        new Thread() {
            public void run() {
                Overpass pass = new Overpass();
                roadMap = pass.getRoads(originLocation);
                for (int index = 0; index < roadMap.size(); index++) {
                    Message msg = new Message();
                    Bundle bundle = new Bundle();
                    bundle.putParcelable("road", roadMap.valueAt(index));
                    int isLast = index == roadMap.size()-1 ? 1 : 0;
                    bundle.putInt("last", isLast);
                    msg.setData(bundle);
                    handler.sendMessage(msg);
                }
            }
        }.start();
    }

    private void startGame() {
        //RELEASE THEM!!!
        createGhosts();
        createTimer();
    }

    private void createTimer() {
        Toolbar bar = findViewById(R.id.toolbar_top);
        int barHeight = bar.getHeight();
        TextView timer = findViewById(R.id.timeScore);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ActionBar.LayoutParams.WRAP_CONTENT, ActionBar.LayoutParams.WRAP_CONTENT);
        params.setMargins(0,10 + barHeight,10,0);
        params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        timer.setLayoutParams(params);
        setTypeface(timer);

        Animation animation = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.blink);

        //Could use some other method here but Long.MAX_VALUE is pretty damn long.
        new CountDownTimer(Long.MAX_VALUE, 1000) {

            public void onTick(long millisUntilFinished) {
                timer.setText(String.valueOf(gameTime));

                if (gameTime == 0) {
                    gameOver();
                }

                if (gameTime <= 10) {
                    timer.setAnimation(animation);
                    timer.setTextColor(Color.RED);
                } else {
                    timer.setTextColor(Color.WHITE);
                    timer.clearAnimation();
                }

                gameTime--;
            }

            public void onFinish() {
                System.out.println("Well you have played for a very long time");
            }

        }.start();
    }

    private MarkerOptions createMarkerOptions(Position loc) {
        MarkerOptions options = new MarkerOptions();
        LatLng pos = new LatLng();
        pos.setLatitude(loc.lat);
        pos.setLongitude(loc.lon);
        options.setPosition(pos);
        Icon icon = getScaledIconFromResource();
        options.setIcon(icon);

        return options;
    }



    private Icon getScaledIconFromResource() {
        IconFactory iconFactory = IconFactory.getInstance(MapActivity.this);
        Bitmap b = BitmapFactory.decodeResource(MapActivity.this.getResources(), R.mipmap.pacdot);
        Bitmap smallMarker = Bitmap.createScaledBitmap(b, 30, 30, false);

        return iconFactory.fromBitmap(smallMarker);
    }

    private void setTypeface(TextView view) {
        Typeface typeface = Typeface.createFromAsset(getAssets(), FONT_URL);
        view.setTypeface(typeface);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_leaderboard) {
            return true;
        } else if (id == R.id.action_location) {
            animateCameraToMyLocation();
        }

        return super.onOptionsItemSelected(item);
    }


    @SuppressWarnings({"MissingPermission"})
    private void animateCameraToMyLocation() {
        Location myLoc = locationEngine.getLastLocation();

        if (myLoc != null) {
            CameraPosition position = new CameraPosition.Builder()
                    .target(new LatLng(myLoc.getLatitude(), myLoc.getLongitude())) // Sets the new camera position
                    .zoom(17) // Sets the zoom
                    .tilt(30) // Set the camera tilt
                    .build(); // Creates a CameraPosition from the builder

            mapboxMap.animateCamera(CameraUpdateFactory
                    .newCameraPosition(position), 500);
        }
    }


    @SuppressWarnings({"MissingPermission"})
    private void enableLocationPlugin() {
        // Check if permissions are enabled and if not request
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            initializeLocationEngine();
            // Create an instance of the plugin. Adding in LocationLayerOptions is also an optional
            // parameter
            locationLayerPlugin = new LocationLayerPlugin(mapView, mapboxMap);

            // Set plugin settings
            locationLayerPlugin.setRenderMode(RenderMode.GPS);
            locationLayerPlugin.setCameraMode(CameraMode.TRACKING_GPS);
            pacmanOpenMouth();

            getLifecycle().addObserver(locationLayerPlugin);

        } else {
            permissionsManager = new PermissionsManager(this);
            permissionsManager.requestLocationPermissions(this);
        }
    }

    @SuppressWarnings( {"MissingPermission"})
    private void initializeLocationEngine() {
        LocationEngineProvider locationEngineProvider = new LocationEngineProvider(this);
        locationEngine = locationEngineProvider.obtainBestLocationEngineAvailable();
        locationEngine.setPriority(LocationEnginePriority.HIGH_ACCURACY);
        locationEngine.addLocationEngineListener(this);
        locationEngine.requestLocationUpdates();
        locationEngine.activate();

        Location lastLocation = locationEngine.getLastLocation();
        if (lastLocation != null) {
            originLocation = lastLocation;
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onExplanationNeeded(List<String> permissionsToExplain) {
        Toast.makeText(this, R.string.user_location_permission_explanation, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onPermissionResult(boolean granted) {
        if (granted) {
            enableLocationPlugin();
        } else {
            Toast.makeText(this, R.string.user_location_permission_not_granted, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    /**
     * Open and close pacmans mouth by changing the drawable.
     */
    private void animatePacman() {
        pacmanCloseMouth();
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                pacmanOpenMouth();
            }
        }, 1000);
    }

    private void pacmanOpenMouth() {
        locationLayerPlugin.applyStyle(LocationLayerOptions.builder(MapActivity.this).gpsDrawable(R.mipmap.pacman_open_icon).build());
    }

    private void pacmanCloseMouth() {
        locationLayerPlugin.applyStyle(LocationLayerOptions.builder(this).gpsDrawable(R.mipmap.pacman_close_icon).build());
    }

    /**
     * Find nearest marker from RTree and get it from the observable.
     * If observable is empty just return null.
     *
     * @param loc User location
     * @return Marker to be removed.
     */
    private Entry<String, Point> findNearestMarker(Location loc) {
        Observable<Entry<String, Point>> entries = rTreeMarker.nearest(Geometries.point(loc.getLatitude(),loc.getLongitude()), 100.0, 1);

        //Well this is certainly is not clean. I dont like throwing null checks everywhere
        //But it is nice that it works.. clean later on.
        return entries.toBlocking().firstOrDefault(null);
    }

    private Iterable<Entry<String, Point>> findNearestMarkers(Location loc, double dist, int count) {
        Observable<Entry<String, Point>> entries = rTreeNavigation.nearest(Geometries.point(loc.getLatitude(),loc.getLongitude()), dist, count);

        return entries.toBlocking().toIterable();
    }

    /**
     * When user location is updated get the nearest marker
     * and check if it is close enough to the user and then 'eat it'.
     *
     * @param location user Location.
     */
    @Override
    public void onLocationChanged(Location location) {
       Entry<String, Point> point = findNearestMarker(location);
       if (point != null) {
           int key = point.geometry().hashCode();
           MarkerOptions options = markers.get(key);
           Marker marker = options.getMarker();
           LatLng markerPos = marker.getPosition();
           double distance = DistanceUtil.distance(location.getLatitude(), location.getLongitude(), markerPos.getLatitude(), markerPos.getLongitude());

           //Nom Nom
           if (distance < EATING_DISTANCE) {
               mapboxMap.removeMarker(marker);
               markers.remove(key);
               rTreeMarker.delete(point);
               animatePacman();
               gameTime += 5;
           }
       }
    }

    private static class LatLngEvaluator implements TypeEvaluator<LatLng> {
        // Method is used to interpolate the marker animation.

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

    @Override
    public void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    public void onConnected() {

    }
}
