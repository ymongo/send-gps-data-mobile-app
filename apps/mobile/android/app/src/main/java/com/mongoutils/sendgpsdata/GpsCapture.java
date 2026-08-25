package com.mongoutils.sendgpsdata;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.pm.PackageManager;

/**
 * Native GPS capture using Android LocationManager.
 * No Google Play Services dependency — F-Droid compatible.
 */
public class GpsCapture {

    private static final String TAG = "GpsCapture";
    private static final long LOCATION_INTERVAL_MS = 1000;

    private final Callback callback;
    private LocationManager locationManager;
    private LocationListener locationListener;
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

        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            callback.onError("LocationManager not available");
            return;
        }

        // Check that the GPS provider exists
        if (locationManager.getProvider(LocationManager.GPS_PROVIDER) == null) {
            callback.onError("GPS provider not available on this device");
            return;
        }

        firstLocationReceived = false;
        lastAvailability = true;

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (location == null) return;

                if (!firstLocationReceived) {
                    firstLocationReceived = true;
                    callback.onGpsStatusChanged("active");
                }
                float speed = computeReliableSpeed(location);
                callback.onLocationUpdate(location, speed);
            }

            @Override
            public void onStatusChanged(String provider, int status, android.os.Bundle extras) {
                // status: OUT_OF_SERVICE, TEMPORARILY_UNAVAILABLE, AVAILABLE
                if (LocationManager.GPS_PROVIDER.equals(provider)) {
                    if (status == LocationProvider.OUT_OF_SERVICE || status == LocationProvider.TEMPORARILY_UNAVAILABLE) {
                        if (lastAvailability) {
                            lastAvailability = false;
                            callback.onGpsStatusChanged("unavailable");
                        }
                    } else if (status == LocationProvider.AVAILABLE) {
                        if (!lastAvailability) {
                            lastAvailability = true;
                            callback.onGpsStatusChanged("active");
                        }
                    }
                }
            }

            @Override
            public void onProviderEnabled(String provider) {
                if (LocationManager.GPS_PROVIDER.equals(provider) && !lastAvailability) {
                    lastAvailability = true;
                    callback.onGpsStatusChanged("active");
                }
            }

            @Override
            public void onProviderDisabled(String provider) {
                if (LocationManager.GPS_PROVIDER.equals(provider) && lastAvailability) {
                    lastAvailability = false;
                    callback.onGpsStatusChanged("unavailable");
                }
            }
        };

        requestUpdates();
    }

    @SuppressLint("MissingPermission")
    private void requestUpdates() {
        // Use both GPS and network providers to get a fast fix (like the old
        // FusedLocationProvider PRIORITY_HIGH_ACCURACY): network gives a quick
        // coarse position indoors, GPS refines it when satellites are visible.
        List<String> providers = new ArrayList<>();
        if (locationManager.getProvider(LocationManager.GPS_PROVIDER) != null) {
            providers.add(LocationManager.GPS_PROVIDER);
        }
        if (locationManager.getProvider(LocationManager.NETWORK_PROVIDER) != null) {
            providers.add(LocationManager.NETWORK_PROVIDER);
        }

        if (providers.isEmpty()) {
            callback.onError("No location providers available");
            return;
        }

        for (String provider : providers) {
            locationManager.requestLocationUpdates(
                    provider,
                    LOCATION_INTERVAL_MS,
                    0f,
                    locationListener,
                    Looper.getMainLooper()
            );
        }
        LogSink.d(TAG, "Location updates requested at " + LOCATION_INTERVAL_MS + "ms interval via providers: " + providers);
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
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
            LogSink.d(TAG, "Location updates removed");
        }
        locationManager = null;
        locationListener = null;
        firstLocationReceived = false;
    }
}
