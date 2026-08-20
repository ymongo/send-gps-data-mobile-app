package com.mongoutils.sendgpsdata;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.os.Looper;

import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.pm.PackageManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/**
 * Native GPS capture using FusedLocationProviderClient.
 * Assumes location permissions are already granted by the caller.
 */
public class GpsCapture {

    private static final String TAG = "GpsCapture";
    private static final long LOCATION_INTERVAL_MS = 1000;

    private final Callback callback;
    private FusedLocationProviderClient fusedClient;
    private LocationCallback locationCallback;
    private boolean firstLocationReceived = false;
    private boolean lastAvailability = true;

    // Reliable speed computation state.
    private Location lastReliableLocation = null;   // last position with good accuracy
    private long lastReliableTime = 0;
    private float lastComputedSpeed = 0f;

    /** Max accepted horizontal accuracy (meters) for a "reliable" position used in speed calc. */
    private static final float MAX_ACCURACY_METERS = 15f;
    /** Positions closer in time than this (ms) are too noisy to derive speed from. */
    private static final long MIN_SPEED_DELTA_MS = 2000;
    /** Below this speed (m/s) we report exactly 0 — kills stationary jitter. */
    private static final float SPEED_DEADBAND_MPS = 0.5f;
    /** Exponential smoothing factor for speed (0..1). Higher = more responsive. */
    private static final float SPEED_SMOOTHING = 0.7f;

    public interface Callback {
        void onLocationUpdate(Location location, float speedMps);
        void onGpsStatusChanged(String status);  // "active", "unavailable"
        void onError(String message);
    }

    public GpsCapture(Callback callback) {
        this.callback = callback;
    }

    /**
     * Start requesting location updates.
     * Checks for ACCESS_FINE_LOCATION permission — if not granted, emits error via callback.
     */
    public void start(Context context) {
        // Check permission before requesting updates
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            callback.onError("Location permission denied");
            return;
        }

        try {
            fusedClient = LocationServices.getFusedLocationProviderClient(context);
        } catch (Exception e) {
            LogSink.e(TAG, "Play Services not available", e);
            callback.onError("Google Play Services not available: " + e.getMessage());
            return;
        }

        firstLocationReceived = false;
        lastAvailability = true;

        LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS)
                .setMinUpdateIntervalMillis(LOCATION_INTERVAL_MS)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;

                for (Location location : locationResult.getLocations()) {
                    if (!firstLocationReceived) {
                        firstLocationReceived = true;
                        callback.onGpsStatusChanged("active");
                    }
                    float speed = computeReliableSpeed(location);
                    callback.onLocationUpdate(location, speed);
                }
            }

            @Override
            public void onLocationAvailability(LocationAvailability availability) {
                boolean available = availability.isLocationAvailable();
                if (available && !lastAvailability) {
                    lastAvailability = true;
                    callback.onGpsStatusChanged("active");
                } else if (!available && lastAvailability) {
                    lastAvailability = false;
                    callback.onGpsStatusChanged("unavailable");
                }
            }
        };

        requestUpdates(locationRequest);
    }

    @SuppressLint("MissingPermission")
    private void requestUpdates(LocationRequest locationRequest) {
        fusedClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        LogSink.d(TAG, "Location updates requested at " + LOCATION_INTERVAL_MS + "ms interval");
    }

    /**
     * Computes a reliable speed (m/s) from consecutive positions with good accuracy.
     * - Ignores positions whose accuracy is too poor (noise → phantom speed).
     * - Derives speed from haversine distance / elapsed time between reliable fixes.
     * - Applies a deadband so a stationary device reports exactly 0 (no jitter).
     * - Smooths with exponential moving average for stability while staying responsive.
     */
    private float computeReliableSpeed(Location location) {
        long now = location.getTime();

        // If accuracy is poor, keep last reliable state but don't update position;
        // return smoothed speed decayed toward 0 so an inaccurate fix can't fabricate speed.
        if (!location.hasAccuracy() || location.getAccuracy() > MAX_ACCURACY_METERS) {
            lastComputedSpeed = applyDeadband(lastComputedSpeed * 0.5f);
            return lastComputedSpeed;
        }

        if (lastReliableLocation != null) {
            long deltaMs = now - lastReliableTime;
            if (deltaMs >= MIN_SPEED_DELTA_MS) {
                float distMeters = haversineDistanceMeters(
                        lastReliableLocation.getLatitude(), lastReliableLocation.getLongitude(),
                        location.getLatitude(), location.getLongitude());
                float instantSpeed = distMeters / (deltaMs / 1000f);
                lastComputedSpeed = applyDeadband(
                        SPEED_SMOOTHING * instantSpeed + (1f - SPEED_SMOOTHING) * lastComputedSpeed);
            }
        }

        lastReliableLocation = location;
        lastReliableTime = now;
        return lastComputedSpeed;
    }

    private float applyDeadband(float speed) {
        return speed < SPEED_DEADBAND_MPS ? 0f : speed;
    }

    /** Great-circle distance between two lat/lon points in meters (haversine). */
    private static float haversineDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (float) (R * c);
    }

    /**
     * Stop location updates and release resources.
     */
    public void stop() {
        if (fusedClient != null && locationCallback != null) {
            fusedClient.removeLocationUpdates(locationCallback);
            LogSink.d(TAG, "Location updates removed");
        }
        fusedClient = null;
        locationCallback = null;
        firstLocationReceived = false;
    }
}
