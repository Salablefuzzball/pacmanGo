package com.example.toukolohilahti.pacmango_native;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
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
import android.sax.RootElement;
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
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.toukolohilahti.pacmango_native.overpass.Overpass;
import com.example.toukolohilahti.pacmango_native.overpass.Position;
import com.example.toukolohilahti.pacmango_native.overpass.Road;
import com.example.toukolohilahti.pacmango_native.util.DistanceUtil;
import com.example.toukolohilahti.pacmango_native.util.HighScoreRow;
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
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.LocationComponentOptions;
import com.mapbox.mapboxsdk.location.modes.CameraMode;
import com.mapbox.mapboxsdk.location.modes.RenderMode;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import rx.Observable;
import timber.log.Timber;

public class MapActivity extends AppCompatActivity implements LocationEngineListener, PermissionsListener, GameStateListener {

    private MapView mapView;
    private MapboxMap mapboxMap;
    private PermissionsManager permissionsManager;
    private LocationComponent locationComponent;
    private LocationEngine locationEngine;
    private SparseArray<Marker> markers;
    private Icon pacdotIcon;

    ProgressDialog pd;

    GhostAnimationHandler ghostAnimationHandler;
    GameDataHandler gameDataHandler;
    GameStateHandler gameStateHandler;

    private static final int EATING_FOV = 100;
    private static final int BONUS_TIME = 5;
    private static final int EATING_DISTANCE = 20;
    private static final int [] GHOSTS = new int [] {R.mipmap.green_ghost, R.mipmap.red_ghost,
            R.mipmap.pink_ghost, R.mipmap.yellow_ghost};
    private static final int [] GHOST_ARROWS = new int [] {R.mipmap.ghost_arrow_blue, R.mipmap.ghost_arrow_green,
            R.mipmap.ghost_arrow_pink, R.mipmap.ghost_arrow_red};

    public MapActivity() {
        this.gameDataHandler = new GameDataHandler();
        this.ghostAnimationHandler = new GhostAnimationHandler(this.gameDataHandler, this);
        this.gameStateHandler = new GameStateHandler();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        MapActivity self = this;
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_map);

        Mapbox.getInstance(this, getString(R.string.mapbox_access_token));

        setupProgressDialog(getString(R.string.load_user_location));
        setCustomToolbar();

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);

        mapView.getMapAsync(mapboxMap -> {
            self.mapboxMap = mapboxMap;
            hideMapboxAttributes();
            //disableControls();
            enableLocationComponent();
        });
    }

    private void disableControls() {
        mapboxMap.getUiSettings().setZoomControlsEnabled(false);
        mapboxMap.getUiSettings().setZoomGesturesEnabled(false);
    }

    private void setCustomToolbar() {
        Toolbar mTopToolbar = findViewById(R.id.toolbar_top);
        setSupportActionBar(mTopToolbar);
    }

    private void setupProgressDialog(String loadMessage) {
        pd = new ProgressDialog(this, R.style.MyTheme);
        pd.setCancelable(false);
        pd.setMessage(loadMessage);
        pd.show();
    }

    private void createGhosts() {
        for (int ghost: GHOSTS) {
            createGhost(ghost);
        }
    }

    private void createGhostArrows() {
        for (int arrow: GHOST_ARROWS) {
            createGhostArrow(arrow);
        }
    }

    private void createGhost(int ghost) {
        SparseArray<Road> roadMap = gameDataHandler.getRoadMap();
        int randomRoad = ThreadLocalRandom.current().nextInt(0, roadMap.size());
        Road road = roadMap.valueAt(randomRoad);
        int randomRoadSection = ThreadLocalRandom.current().nextInt(0, road.geometry.size());
        Position loc = road.geometry.get(randomRoadSection);

        LoopDirection direction = ghostAnimationHandler.findDirection(road.geometry, randomRoadSection);

        Marker ghostMarker = mapboxMap.addMarker(createGhostMarkerOptions(ghost, loc));

        ghostAnimationHandler.animate(ghostMarker, road, direction, randomRoadSection, ghost);
    }

    private void initializeStartGameButton() {
        Button button = findViewById(R.id.startGameButton);
        button.setVisibility(View.VISIBLE);

        Animation animation = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.fadein);
        button.setAnimation(animation);
    }


    private void submitScore(String username, int points) {
        HighScores highScores =  new HighScores();
        highScores.SendHighScore(username, points, points, 1);
    }

    /**
     * Init data structures again. Clear map from pac-dots and ghosts.
     */
    private void initializeNewGame() {
        animateCameraToMyLocation();
        mapboxMap.clear();
        gameStateHandler.newGame();
        gameDataHandler.createRTrees();
        markers = new SparseArray<>();
        Button button = findViewById(R.id.startGameButton);
        button.setClickable(true);
        createMarkers();
        pd.setMessage(getString(R.string.loading_spinner_title));
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
        initPacdotIcon();
        Handler handler = processRoadData();
        queryRoadDataInThread(handler);
    }

    private void showLoadingDialog() {
        if (!pd.isShowing()) {
            pd.show();
        }
    }

    @SuppressLint("HandlerLeak")
    private Handler processRoadData() {
        showLoadingDialog();

        return new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                Road road = msg.getData().getParcelable("road");
                int isLast = msg.getData().getInt("last");
                if(road != null) {
                    for (Position loc : road.geometry) {
                        MarkerOptions options = createDotMarkerOptions(loc);
                        Marker marker = mapboxMap.addMarker(options);
                        Point point = Geometries.point(loc.lat, loc.lon);
                        gameDataHandler.addValueToTrees(road, point);
                        markers.put(point.hashCode(), marker);
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
        showLoadingDialog();
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

    @SuppressLint("MissingPermission")
    private Location getUserLocation() {
        return locationEngine.getLastLocation();
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
        Button leaderboard = dialog.findViewById(R.id.leaderBoardButton);

        leaderboard.setOnClickListener(v -> {
            showLeaderBoard();
        });

        newGame.setOnClickListener(v -> {
            initializeNewGame();
            dialog.dismiss();
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

    @SuppressLint("ResourceType")
    private void createGhostArrow(int arrow) {
        Toolbar bar = findViewById(R.id.toolbar_top);
        int barHeight = bar.getHeight();

        RelativeLayout layout = findViewById(R.id.map_layout);

        int layoutHeight = layout.getHeight();

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ActionBar.LayoutParams.WRAP_CONTENT, ActionBar.LayoutParams.WRAP_CONTENT);

        ImageView myImage = new ImageView(this);

        switch (arrow) {
            case R.mipmap.ghost_arrow_blue:
                params.setMargins(0,10 + barHeight,10,0);
                params.addRule(RelativeLayout.CENTER_HORIZONTAL);
                myImage.setId(R.mipmap.yellow_ghost);
                break;
            case R.mipmap.ghost_arrow_red:
                params.setMargins(0, (layoutHeight - barHeight) / 2,0,0);
                params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                myImage.setId(R.mipmap.red_ghost);
                break;
            case R.mipmap.ghost_arrow_green:
                params.setMargins(0, (layoutHeight - barHeight) / 2,0,0);
                params.addRule(RelativeLayout.ALIGN_LEFT);
                myImage.setId(R.mipmap.green_ghost);
                break;
            case R.mipmap.ghost_arrow_pink:
                params.addRule(RelativeLayout.CENTER_HORIZONTAL);
                params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                myImage.setId(R.mipmap.pink_ghost);
                break;
        }

        myImage.setImageResource(arrow);
        myImage.setLayoutParams(params);
        layout.addView(myImage);
        layout.requestLayout();
    }

    @SuppressLint("MissingPermission")
    @Override
    public void ghostLocationChange(LatLng ghostPos, int ghostId) {
        ImageView arrow = findViewById(ghostId);

        LatLng centerOfCamera = mapboxMap.getCameraPosition().target;

        double lat1 = centerOfCamera.getLatitude();
        double lon1 = centerOfCamera.getLongitude();
        double lat2 = ghostPos.getLatitude();
        double lon2 = ghostPos.getLongitude();

        double bearing = DistanceUtil.bearing(lat1, lon1, lat2, lon2);

        double distance = DistanceUtil.distance(lat1, lon1, lat2, lon2);

        double cameraBearing = mapboxMap.getCameraPosition().bearing;

        float finalBear = (float) bearing  + (float) cameraBearing;
        arrow.setRotation(finalBear);
        //arrow.getLayoutParams().height = (int) distance;
        //arrow.getLayoutParams().width = (int) distance;

        arrow.requestLayout();

    }

    @SuppressWarnings({"MissingPermission"})
    private void enableLocationComponent() {
        // Check if permissions are enabled and if not request
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            locationComponent = mapboxMap.getLocationComponent();
            locationComponent.activateLocationComponent(this);
            locationComponent.setLocationComponentEnabled(true);

            locationComponent.setRenderMode(RenderMode.GPS);
            locationComponent.setCameraMode(CameraMode.TRACKING_GPS_NORTH);

            initializeLocationEngine();

            pacmanOpenMouth();

        } else {
            permissionsManager = new PermissionsManager(this);
            permissionsManager.requestLocationPermissions(this);
        }
    }

    @SuppressWarnings( {"MissingPermission"})
    private void initializeLocationEngine() {
        MapActivity context = this;
        LocationEngineProvider locationEngineProvider = new LocationEngineProvider(context);
        locationEngine = locationEngineProvider.obtainBestLocationEngineAvailable();
        locationEngine.setPriority(LocationEnginePriority.HIGH_ACCURACY);
        locationEngine.addLocationEngineListener(context);
        locationEngine.requestLocationUpdates();
        locationEngine.activate();

        ghostAnimationHandler.setLocationEngine(locationEngine);
    }

    @SuppressWarnings({"MissingPermission"})
    private void animateCameraToMyLocation() {
        Location myLoc = locationEngine.getLastLocation();

        if (myLoc != null) {
            CameraPosition position = new CameraPosition.Builder()
                    .target(new LatLng(myLoc.getLatitude(), myLoc.getLongitude())) // Sets the new camera position
                    .zoom(16.5) // Sets the zoom
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
        handler.postDelayed(this::pacmanOpenMouth, 500);
    }

    private void pacmanOpenMouth() {
        locationComponent.applyStyle(LocationComponentOptions.builder(MapActivity.this).gpsDrawable(R.mipmap.pacman_open_icon).build());
    }

    private void pacmanCloseMouth() {
        locationComponent.applyStyle(LocationComponentOptions.builder(this).gpsDrawable(R.mipmap.pacman_close_icon).build());
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
                createGhostArrows();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
    }

    private MarkerOptions createGhostMarkerOptions(int resource, Position loc) {
        MarkerOptions options = new MarkerOptions();
        LatLng pos = new LatLng();
        pos.setLatitude(loc.lat);
        pos.setLongitude(loc.lon);
        options.setPosition(pos);

        IconFactory iconFactory = IconFactory.getInstance(MapActivity.this);
        Icon icon = iconFactory.fromResource(resource);

        options.setIcon(icon);

        return options;
    }

    private void initPacdotIcon() {
        IconFactory iconFactory = IconFactory.getInstance(MapActivity.this);
        Bitmap b = BitmapFactory.decodeResource(MapActivity.this.getResources(), R.mipmap.pacdot);
        Bitmap smallMarker = Bitmap.createScaledBitmap(b, 20, 20, false);

        pacdotIcon = iconFactory.fromBitmap(smallMarker);
    }

    private MarkerOptions createDotMarkerOptions(Position loc) {
        MarkerOptions options = new MarkerOptions();
        LatLng pos = new LatLng();
        pos.setLatitude(loc.lat);
        pos.setLongitude(loc.lon);
        options.setPosition(pos);
        options.setIcon(pacdotIcon);

        return options;
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

    private Iterable<Entry<String, Point>> findNearestMarkers(Location loc, double dist, int count) {
        RTree<String, Point> tree = gameDataHandler.getNavigationTree();
        Observable<Entry<String, Point>> entries = tree.nearest(Geometries.point(loc.getLatitude(),loc.getLongitude()), dist, count);

        return entries.toBlocking().toIterable();
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
        if (id == R.id.action_leaderboard && !gameStateHandler.isLeaderBoardOpen()) {
            showLeaderBoard();
            return true;
        } else if (id == R.id.action_location) {
            animateCameraToMyLocation();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void showLeaderBoard() {
        pd.show();
        gameStateHandler.setLeaderBoardOpen(true);
        final Dialog dialog = new Dialog(this);
        dialog.setOnShowListener(arg0 -> pd.dismiss());
        dialog.setCancelable(false);
        dialog.setContentView(R.layout.high_score_table);
        ListView scoreList = dialog.findViewById(R.id.leaderboard);
        Button close = dialog.findViewById(R.id.btn_close);
        HighScores hs = new HighScores();
        ArrayList<HighScoreRow> scoreArray = hs.getHighScores();
        scoreArray.sort(Comparator.comparingInt(HighScoreRow::getScore));
        Collections.reverse(scoreArray);
        scoreList.setAdapter(new ArrayAdapter<HighScoreRow>(this,R.layout.high_score_item, R.id.player_name, scoreArray) {
            @SuppressLint("SetTextI18n")
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView name = (TextView) view.findViewById(R.id.player_name);
                TextView score = (TextView) view.findViewById(R.id.player_score);

                HighScoreRow row = scoreArray.get(position);
                name.setText(row.getPlayer());
                score.setText(Integer.toString(row.getScore()));
                return view;
            }
        });

        // if button is clicked, close the custom dialog
        close.setOnClickListener(v -> {
            gameStateHandler.setLeaderBoardOpen(false);
            dialog.dismiss();
        });

        dialog.show();
    }

    @Override
    public void gameOver() {
        boolean gameOver = gameStateHandler.isGameOver();
        if (!gameOver) {
            gameStateHandler.setGameOver(true);
            ghostAnimationHandler.stopGhostAnimation();
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
            enableLocationComponent();
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
        if (gameStateHandler.isGameRunning()) {
            Iterable<Entry<String, Point>> nearestMarkers = findNearestMarkers(location, 50, 5);

            for (Entry<String, Point> point: nearestMarkers) {
                int key = point.geometry().hashCode();
                Marker marker = markers.get(key);
                if (marker != null) {
                    LatLng markerPos = marker.getPosition();
                    double distance = DistanceUtil.distance(location.getLatitude(), location.getLongitude(), markerPos.getLatitude(), markerPos.getLongitude());

                    Location loc = new Location("dummyprovider");
                    loc.setLongitude(markerPos.getLongitude());
                    loc.setLatitude(markerPos.getLatitude());

                    float bearing = location.bearingTo(loc);

                    if (distance < EATING_DISTANCE && bearing < EATING_FOV) {
                        markers.remove(key);
                        gameDataHandler.removeFromTreeMarker(point);
                        animatePacmanEating(marker, location);
                    }
                }
            }

       } else {
           if (!gameStateHandler.isGameInitialized()) {
               //Start game here because the user location has for sure updated
               initializeNewGame();
           }
       }
    }

    private void animatePacmanEating(Marker targetDot, Location userLoc) {
        pacmanOpenMouth();
        LatLng destLoc = new LatLng(userLoc.getLatitude(), userLoc.getLongitude());

        ValueAnimator markerAnimator = ObjectAnimator.ofObject(targetDot, "position",
                new GhostAnimationHandler.LatLngEvaluator(), targetDot.getPosition(), destLoc);
        markerAnimator.setDuration(100);

        markerAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mapboxMap.removeMarker(targetDot);
                gameStateHandler.addGameTime(BONUS_TIME);
                animatePacman();
            }
        });

        markerAnimator.start();
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
        pd.dismiss();
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    public void onConnected() {

    }
}
