package com.example.toukolohilahti.pacmango_native;

import android.content.Context;
import android.graphics.Typeface;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.multidex.MultiDex;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.example.toukolohilahti.pacmango_native.Overpass.Overpass;
import com.example.toukolohilahti.pacmango_native.Overpass.Position;
import com.example.toukolohilahti.pacmango_native.Overpass.Road;
import com.mapbox.android.core.location.LocationEngine;
import com.mapbox.android.core.location.LocationEngineListener;
import com.mapbox.android.core.location.LocationEnginePriority;
import com.mapbox.android.core.location.LocationEngineProvider;
import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.IconFactory;
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

public class MapActivity extends AppCompatActivity implements LocationEngineListener, PermissionsListener {

    private MapView mapView;
    private MapboxMap mapboxMap;
    private PermissionsManager permissionsManager;
    private LocationLayerPlugin locationLayerPlugin;
    private LocationEngine locationEngine;
    private Location originLocation;
    private Toolbar mTopToolbar;

    private static final String FONT_URL = "fonts/ARCADE.ttf";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        MapActivity self = this;
        super.onCreate(savedInstanceState);

        Mapbox.getInstance(this, getString(R.string.mapbox_access_token));
        setContentView(R.layout.activity_map);

        //Set custom toolbar
        mTopToolbar = findViewById(R.id.toolbar_top);
        setSupportActionBar(mTopToolbar);

        //Set custom Arcade font
        setTitleTypeface();

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
                createMarkers();
            }
        });
    }

    private void hideMapboxAttributes() {
        mapboxMap.getUiSettings().setAttributionEnabled(false);
        mapboxMap.getUiSettings().setLogoEnabled(false);
    }

    private void createMarkers() {
        Overpass pass = new Overpass();
        ArrayList<Road> roadList = pass.getRoads(originLocation);
        for (int index = 0; index < roadList.size(); index++) {
            Road road = roadList.get(index);
            for(int i = 0; i < road.geometry.size(); i++) {
                Position loc = road.geometry.get(i);
                MarkerOptions options = new MarkerOptions();
                LatLng pos = new LatLng();
                pos.setLatitude(loc.lat);
                pos.setLongitude(loc.lon);
                options.setPosition(pos);
                IconFactory iconFactory = IconFactory.getInstance(MapActivity.this);
                Icon icon = iconFactory.fromResource(R.drawable.pellet);
                options.setIcon(icon);
                mapboxMap.addMarker(options);
            }
        }
    }

    private void setTitleTypeface() {
        TextView textView = findViewById(R.id.toolbar_title);
        Typeface typeface = Typeface.createFromAsset(getAssets(), FONT_URL);
        textView.setTypeface(typeface);
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
            locationLayerPlugin.applyStyle(LocationLayerOptions.builder(this).gpsDrawable(R.mipmap.pacman_open_icon).build());

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
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    @Override
    public void onConnected() {

    }

    @Override
    public void onLocationChanged(Location location) {
       
    }
}
