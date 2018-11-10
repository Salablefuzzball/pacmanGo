package com.example.toukolohilahti.pacmango_native;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Location;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
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
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.toukolohilahti.pacmango_native.overpass.Overpass;
import com.example.toukolohilahti.pacmango_native.overpass.Position;
import com.example.toukolohilahti.pacmango_native.overpass.Road;
import com.example.toukolohilahti.pacmango_native.util.DistanceUtil;
import com.example.toukolohilahti.pacmango_native.util.HighScores;
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
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerOptions;
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerPlugin;
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.CameraMode;
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.RenderMode;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

import rx.Observable;
import timber.log.Timber;

public class MapActivity extends AppCompatActivity implements LocationEngineListener, PermissionsListener, GameOverListener {

    private MapView mapView;
    private MapboxMap mapboxMap;
    private PermissionsManager permissionsManager;
    private LocationLayerPlugin locationLayerPlugin;
    private LocationEngine locationEngine;
    private SparseArray<MarkerOptions> markers;

    ProgressDialog pd;

    AnimationHandler animationHandler;
    GameDataHandler gameDataHandler;
    GameStateHandler gameStateHandler;

    private static final int EATING_DISTANCE = 5;
    private static final int [] GHOSTS = new int [] {R.mipmap.green_ghost, R.mipmap.red_ghost,
            R.mipmap.pink_ghost, R.mipmap.yellow_ghost};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        MapActivity self = this;
        super.onCreate(savedInstanceState);

        Mapbox.getInstance(this, getString(R.string.mapbox_access_token));
        setContentView(R.layout.activity_map);

        setupProgressDialog();
        setCustomToolbar();

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);

        mapView.getMapAsync(mapboxMap -> {
            self.mapboxMap = mapboxMap;
            hideMapboxAttributes();
            disableControls();
            enableLocationPlugin();
        });
    }

    private void disableControls() {
        mapboxMap.getUiSettings().setZoomControlsEnabled(false);
        mapboxMap.getUiSettings().setZoomGesturesEnabled(false);
    }

    public MapActivity() {
        this.gameDataHandler = new GameDataHandler();
        this.animationHandler = new AnimationHandler(this.gameDataHandler, this);
        this.gameStateHandler = new GameStateHandler();
    }

    private void setCustomToolbar() {
        Toolbar mTopToolbar = findViewById(R.id.toolbar_top);
        setSupportActionBar(mTopToolbar);
    }

    private void setupProgressDialog() {
        pd = new ProgressDialog(this, R.style.MyTheme);
        pd.setCancelable(false);
        String loadMessage = getString(R.string.loading_spinner_title);
        pd.setMessage(loadMessage);
    }

    private void createGhosts() {
        for (int ghost: GHOSTS) {
            createGhost(ghost);
        }
    }

    /**
     * Create ghost in to a random location and start animating it.
     *
     * @param ghost
     */
    private void createGhost(int ghost) {
        SparseArray<Road> roadMap = gameDataHandler.getRoadMap();
        int randomRoad = ThreadLocalRandom.current().nextInt(0, roadMap.size());
        Road road = roadMap.valueAt(randomRoad);
        int randomRoadSection = ThreadLocalRandom.current().nextInt(0, road.geometry.size());
        Position loc = road.geometry.get(randomRoadSection);

        LoopDirection direction = animationHandler.findDirection(road.geometry, randomRoadSection);

        Marker ghostMarker = createGhostMarker(ghost, loc);

        animationHandler.animate(ghostMarker, road, direction, randomRoadSection);
    }

    private void initializeStartGameButton() {
        Button button = findViewById(R.id.startGameButton);
        button.setVisibility(View.VISIBLE);

        Animation animation = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.fadein);
        button.setAnimation(animation);
    }

    //TODO: fix highscores
    private void submitScore(String username, int points) {
        HighScores highScores =  new HighScores();
        //thread is not runable, msg ignore, state:WAITING
        //highScores.SendHighScore(username, points, points, 1);
    }

    /**
     * Init data structures again. Clear map from pac-dots and ghosts.
     */
    private void startGame() {
        mapboxMap.clear();
        gameStateHandler.newGame();
        gameDataHandler.createRTrees();
        markers = new SparseArray<MarkerOptions>();
        Button button = findViewById(R.id.startGameButton);
        button.setClickable(true);
        createMarkers();
    }

    /**
     * Hides MapBox Logo and Info button.
     * IF PUBLISHED DO NOT HIDE
     */
    private void hideMapboxAttributes() {
        mapboxMap.getUiSettings().setAttributionEnabled(false);
        mapboxMap.getUiSettings().setLogoEnabled(false);
    }

    private void createMarkers() {
        Handler handler = processRoadData();
        queryRoadDataInThread(handler);
    }

    @SuppressLint("HandlerLeak")
    private Handler processRoadData() {
        return new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                Road road = msg.getData().getParcelable("road");
                int isLast = msg.getData().getInt("last");
                if(road != null) {
                    for (Position loc : road.geometry) {
                        MarkerOptions options = createMarkerOptions(loc);
                        mapboxMap.addMarker(options);
                        Point point = Geometries.point(loc.lat, loc.lon);
                        gameDataHandler.addValueToTrees(road, point);
                        markers.put(point.hashCode(), options);
                    }
                }

                if (isLast == 1) {
                    pd.dismiss();
                    initializeStartGameButton();
                }
            }
        };
    }

    /**
     * Query road data in separate thead so UI thread does not get overloaded.
     *
     * @param handler Where markers will be added to the map.
     */
    private void queryRoadDataInThread(Handler handler) {
        pd.show();
        new Thread() {
            public void run() {
                Overpass pass = new Overpass();
                Location location = getUserLocation();
                SparseArray<Road> roadMap = pass.getRoads(location);
                gameDataHandler.setRoadMap(roadMap);
                for (int index = 0; index < roadMap.size(); index++) {
                    Message msg = createMessage(roadMap, index);
                    handler.sendMessage(msg);
                }
            }
        }.start();
    }

    /**
     * Query location few times because sometimes
     * it does not update the location correctly.
     * This might not fix that but who knows.
     *
     * @return Hopefully correct location.
     */
    @SuppressLint("MissingPermission")
    private Location getUserLocation() {
        Location location = null;
        for(int i = 0; i<5; i++) {
            location = locationEngine.getLastLocation();
        }
        return location;

    }

    @SuppressLint("SetTextI18n")
    private void showGameOverDialog() {
        int points = gameStateHandler.getPoints();
        String surviveText = getString(R.string.survive);
        String secondsText = getString(R.string.seconds);
        final Dialog dialog = new Dialog(this);
        dialog.setCancelable(false);
        dialog.setContentView(R.layout.game_over_dialog);
        TextView scoreText = dialog.findViewById(R.id.scoreText);
        Button submit = dialog.findViewById(R.id.submit);
        EditText username = dialog.findViewById(R.id.username);
        scoreText.setText(surviveText + points + secondsText);

        Button newGame = dialog.findViewById(R.id.newGame);
        Button saveScore = dialog.findViewById(R.id.saveScore);

        // if button is clicked, close the custom dialog
        newGame.setOnClickListener(v -> {
            dialog.dismiss();
            startGame();
        });

        saveScore.setOnClickListener(v -> {
            saveScore.setVisibility(View.GONE);
            username.setVisibility(View.VISIBLE);
            submit.setVisibility(View.VISIBLE);

            submit.setOnClickListener(view -> {
                String usernameText = username.getText().toString();

                if (!usernameText.isEmpty()) {
                    submitScore(username.getText().toString(), points);
                    username.setVisibility(View.GONE);
                    submit.setVisibility(View.GONE);
                }
            });
        });

        dialog.show();
    }

    private void createTimer() {
        Toolbar bar = findViewById(R.id.toolbar_top);
        int barHeight = bar.getHeight();
        TextView timer = findViewById(R.id.timeScore);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ActionBar.LayoutParams.WRAP_CONTENT, ActionBar.LayoutParams.WRAP_CONTENT);
        params.setMargins(0,10 + barHeight,10,0);
        params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        timer.setLayoutParams(params);
        Animation animation = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.blink);

        //Could use some other method here but Long.MAX_VALUE is pretty damn long.
        new CountDownTimer(Long.MAX_VALUE, 1000) {

            public void onTick(long millisUntilFinished) {
                timer.setText(String.valueOf(gameStateHandler.getGameTime()));

                if (gameStateHandler.getGameTime() == 0) {
                    gameOver();
                    cancel();
                }

                if (gameStateHandler.isGameOver()) {
                    cancel();
                }

                if (gameStateHandler.getGameTime() <= 10) {
                    timer.setAnimation(animation);
                    timer.setTextColor(Color.RED);
                } else {
                    timer.setTextColor(Color.WHITE);
                    timer.clearAnimation();
                }

                gameStateHandler.reduceGameTime(1);
                gameStateHandler.addPoints();
            }

            public void onFinish() {
                Timber.i("Maybe you should get a life?");
            }

        }.start();
    }

    @SuppressWarnings({"MissingPermission"})
    private void enableLocationPlugin() {
        // Check if permissions are enabled and if not request
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            CompletableFuture<Void> future = initializeLocationEngine();
            // Create an instance of the plugin. Adding in LocationLayerOptions is also an optional
            // parameter
            locationLayerPlugin = new LocationLayerPlugin(mapView, mapboxMap);

            // Set plugin settings
            locationLayerPlugin.setRenderMode(RenderMode.GPS);
            locationLayerPlugin.setCameraMode(CameraMode.TRACKING_GPS);
            pacmanOpenMouth();

            getLifecycle().addObserver(locationLayerPlugin);

            //Make sure locationEngine is initialized before doing anything
            future.thenRun(this::startGame);
        } else {
            permissionsManager = new PermissionsManager(this);
            permissionsManager.requestLocationPermissions(this);
        }
    }

    @SuppressWarnings( {"MissingPermission"})
    private CompletableFuture<Void> initializeLocationEngine() {
        MapActivity context = this;
        return CompletableFuture.supplyAsync(() -> {
            LocationEngineProvider locationEngineProvider = new LocationEngineProvider(context);
            locationEngine = locationEngineProvider.obtainBestLocationEngineAvailable();
            locationEngine.setPriority(LocationEnginePriority.HIGH_ACCURACY);
            locationEngine.addLocationEngineListener(context);
            locationEngine.requestLocationUpdates();
            locationEngine.activate();
            animationHandler.setLocationEngine(locationEngine);

            return null;
        });
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

    public void startGame(View view) {
        gameStateHandler.startGame();
        Button button = findViewById(R.id.startGameButton);
        Animation animation = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.fadeout);
        button.startAnimation(animation);
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                button.setVisibility(View.GONE);
                button.setClickable(false);
                //RELEASE THEM!!!
                createGhosts();
                createTimer();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
    }

    private Marker createGhostMarker(int ghost, Position loc) {
        MarkerOptions options = new MarkerOptions();
        LatLng pos = new LatLng();
        pos.setLatitude(loc.lat);
        pos.setLongitude(loc.lon);
        options.setPosition(pos);
        IconFactory iconFactory = IconFactory.getInstance(MapActivity.this);
        Icon icon = iconFactory.fromResource(ghost);
        options.setIcon(icon);
        return mapboxMap.addMarker(options);
    }

    private Message createMessage(SparseArray<Road> roadMap, int index) {
        Message msg = new Message();
        Bundle bundle = new Bundle();
        bundle.putParcelable("road", roadMap.valueAt(index));
        int isLast = index == roadMap.size()-1 ? 1 : 0;
        bundle.putInt("last", isLast);
        msg.setData(bundle);

        return msg;
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


    /**
     * Find nearest marker from RTree and get it from the observable.
     * If observable is empty just return null.
     *
     * @param loc User location
     * @return Marker to be removed.
     */
    private Entry<String, Point> findNearestMarker(Location loc) {
        RTree<String, Point> tree = gameDataHandler.getMarkerTree();
        Observable<Entry<String, Point>> entries = tree.nearest(Geometries.point(loc.getLatitude(),loc.getLongitude()), 100.0, 1);

        //Well this is certainly is not clean. I dont like throwing null checks everywhere
        //But it is nice that it works.. clean later on.
        return entries.toBlocking().firstOrDefault(null);
    }

    private Icon getScaledIconFromResource() {
        IconFactory iconFactory = IconFactory.getInstance(MapActivity.this);
        Bitmap b = BitmapFactory.decodeResource(MapActivity.this.getResources(), R.mipmap.pacdot);
        Bitmap smallMarker = Bitmap.createScaledBitmap(b, 30, 30, false);

        return iconFactory.fromBitmap(smallMarker);
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

    @Override
    public void gameOver() {
        boolean gameOver = gameStateHandler.isGameOver();
        if (!gameOver) {
            gameStateHandler.setGameOver(true);
            animationHandler.stopGhostAnimation();
            showGameOverDialog();
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
     * When user location is updated get the nearest marker
     * and check if it is close enough to the user and then 'eat it'.
     *
     * @param location user Location.
     */
    @Override
    public void onLocationChanged(Location location) {
       if (gameStateHandler.isGameStarted()) {
           Entry<String, Point> point = findNearestMarker(location);
           if (point != null) {
               int key = point.geometry().hashCode();
               MarkerOptions options = markers.get(key);
               //For some reason the point might not always be in the map.
               //Very hard to debug so at least for now just make sure app does not crash.
               if (options != null) {
                   Marker marker = options.getMarker();
                   LatLng markerPos = marker.getPosition();
                   double distance = DistanceUtil.distance(location.getLatitude(), location.getLongitude(), markerPos.getLatitude(), markerPos.getLongitude());

                   //Nom Nom
                   if (distance < EATING_DISTANCE) {
                       mapboxMap.removeMarker(marker);
                       markers.remove(key);
                       gameDataHandler.removeFromTreeMarker(point);
                       animatePacman();
                       gameStateHandler.addGameTime(5);
                   }
               }
           }
       }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
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
