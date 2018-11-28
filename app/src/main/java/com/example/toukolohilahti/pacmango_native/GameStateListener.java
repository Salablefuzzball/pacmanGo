package com.example.toukolohilahti.pacmango_native;

import com.mapbox.mapboxsdk.geometry.LatLng;

/**
 * This interface is used for callback from GhostAnimationHandler
 */
public interface GameStateListener {

    void gameOver();

    void ghostLocationChange(LatLng ghostPos, int ghostId);
}
