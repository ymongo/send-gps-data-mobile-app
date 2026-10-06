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
    // Updated only when a speed sample is actually derived, so short fixes
    // do not shift the haversine baseline and freeze the last speed.
    private Location lastReliableLocation = null;   // last position used as speed baseline
    private long lastReliableTime = 0;
    private float lastComputedSpeed = 0f;

    // Stationary detector: GPS noise of a few metres must not keep the EMA
    // above the deadband (~1.4 m/s ≈ 5 km/h) while the device is stopped.
    private Location stationaryAnchor = null;
    private long stationaryAnchorTime = 0;
    private boolean stationary = false;

    /**
     * Max accepted horizontal accuracy (meters) for a position used as a
     * haversine speed baseline. 50 m, not 15 m: an urban or tree-covered fix
     * is routinely 20–50 m, and rejecting it left the overlay at 0 while the
     * device was moving. Doppler speed does not use this gate.
     */
    private static final float MAX_ACCURACY_METERS = 50f;
    /** Positions closer in time than this (ms) are too noisy to derive speed from. */
    private static final long MIN_SPEED_DELTA_MS = 2000;
    /** Below this speed (m/s) we report exactly 0 — kills stationary jitter. */
    private static final float SPEED_DEADBAND_MPS = 0.5f;
    /** Exponential smoothing factor for speed (0..1). Higher = more responsive. */
    private static final float SPEED_SMOOTHING = 0.7f;
    /**
     * Floor radius (m) around the stationary anchor. The effective radius grows
     * with announced accuracy so GPS drift while stopped still latches.
     */
    private static final float STATIONARY_RADIUS_METERS = 4f;
    /**
     * Multiplier on announced accuracy for the effective stationary radius.
     * Stopped GPS noise grows with reported imprecision; a fixed radius is
     * re-anchored forever and never latches.
     */
    private static final float STATIONARY_ACCURACY_FACTOR = 1.5f;
    /**
     * Extra factor required to leave the stationary state. A plain crossing of
     * the radius flickers when the fix oscillates on the boundary.
     */
    private static final float STATIONARY_EXIT_HYSTERESIS = 1.5f;
    /**
     * Minimum time (ms) the device must remain inside {@link #STATIONARY_RADIUS_METERS}
     * before speed is forced to 0. Shorter than a real slow move, long enough
     * that a single noisy jump does not latch the detector.
     */
    private static final long STATIONARY_WINDOW_MS = 4000;

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
     * Computes a reliable speed (m/s). Package-private so the JVM unit tests
     * can drive it without a {@link LocationManager}; production calls it
     * only from {@code onLocationChanged}.
     * - Primary source is the GPS chip's Doppler speed
     *   ({@link Location#getSpeed()}), which stays valid when the position
     *   itself is only good to tens of metres. Used only for
     *   {@link LocationManager#GPS_PROVIDER} fixes that report a speed.
     * - Fallback is haversine distance / elapsed time, and only from fixes
     *   whose accuracy is within {@link #MAX_ACCURACY_METERS}. A worse fix
     *   keeps the last state and decays the smoothed speed toward 0, so a
     *   100 m network fix cannot fabricate motion.
     * - The speed baseline moves only when a delta sample is actually
     *   computed, so fixes closer than {@link #MIN_SPEED_DELTA_MS} cannot
     *   shrink dt and freeze the last speed.
     * - Forces exactly 0 while the device stays inside an accuracy-scaled
     *   stationary radius for {@link #STATIONARY_WINDOW_MS}: the detector
     *   outranks both sources, so Doppler noise cannot hold the EMA above
     *   the deadband (~5 km/h) when stopped.
     * - Applies a deadband so a near-stop reports exactly 0 (no jitter).
     * - Smooths with an exponential moving average for stability while
     *   staying responsive.
     */
    float computeReliableSpeed(Location location) {
        long now = location.getTime();

        // Stopped wins over every speed source. A GPS chip can still report a
        // small Doppler speed from multipath while the device has not moved.
        if (isStationary(location, now)) {
            // Drop the speed baseline so the first post-stop sample is not
            // inflated by the distance accumulated while we were "stopped".
            lastReliableLocation = null;
            lastReliableTime = 0;
            lastComputedSpeed = 0f;
            return 0f;
        }

        // Doppler speed from the GPS chip. Horizontal accuracy does not gate
        // this: that is the whole point — a 40 m fix can still carry a
        // trustworthy speed, which the old delta-only path reported as 0.
        if (hasGpsProviderSpeed(location)) {
            lastComputedSpeed = smooth(location.getSpeed());
            // A Doppler sample is not a position baseline. Leave the haversine
            // anchor untouched so a later speed-less fix still measures dt
            // from the last accurate position, not from this one.
            return lastComputedSpeed;
        }

        // Delta fallback. Poor accuracy (or none) must not invent a speed
        // from position noise; decay what we had so the overlay does not
        // stick on the last moving sample either.
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
                lastComputedSpeed = smooth(instantSpeed);
                lastReliableLocation = location;
                lastReliableTime = now;
            }
            // else: keep the previous baseline so a short interval cannot
            // shrink dt and freeze the last computed speed.
        } else {
            lastReliableLocation = location;
            lastReliableTime = now;
        }

        return lastComputedSpeed;
    }

    /**
     * True when this fix carries the GPS chip's own speed. Network and fused
     * providers also expose {@link Location#hasSpeed()}, but that value is
     * itself derived from coarse positions and is not trusted here.
     */
    private static boolean hasGpsProviderSpeed(Location location) {
        return location.hasSpeed()
                && LocationManager.GPS_PROVIDER.equals(location.getProvider());
    }

    /**
     * Runs the stationary detector and reports whether it has latched.
     * Accuracy-blind on purpose: a stopped device under trees still drifts
     * inside a large accuracy circle, and that is exactly when we must
     * force 0 rather than trust a noisy Doppler sample.
     */
    private boolean isStationary(Location location, long now) {
        updateStationaryState(location, now);
        return stationary;
    }

    /** EMA then deadband, shared by the Doppler path and the delta fallback. */
    private float smooth(float instantSpeed) {
        return applyDeadband(
                SPEED_SMOOTHING * instantSpeed + (1f - SPEED_SMOOTHING) * lastComputedSpeed);
    }

    /**
     * Tracks whether the device has stayed within an accuracy-scaled radius of
     * an anchor for at least {@link #STATIONARY_WINDOW_MS}. Leaving requires a
     * clear overshoot ({@link #STATIONARY_EXIT_HYSTERESIS}) so boundary noise
     * does not drop the stationary state.
     */
    private void updateStationaryState(Location location, long now) {
        if (stationaryAnchor == null) {
            stationaryAnchor = location;
            stationaryAnchorTime = now;
            stationary = false;
            return;
        }

        float radius = stationaryRadiusMeters(location);
        float driftMeters = haversineDistanceMeters(
                stationaryAnchor.getLatitude(), stationaryAnchor.getLongitude(),
                location.getLatitude(), location.getLongitude());

        // Already stopped: demand a clear exit so oscillation around the
        // radius cannot flicker the state. Entering still uses the raw radius.
        float exitRadius = stationary
                ? radius * STATIONARY_EXIT_HYSTERESIS
                : radius;
        if (driftMeters > exitRadius) {
            stationaryAnchor = location;
            stationaryAnchorTime = now;
            stationary = false;
            return;
        }

        if (!stationary && now - stationaryAnchorTime >= STATIONARY_WINDOW_MS) {
            stationary = true;
            lastComputedSpeed = 0f;
        }
    }

    /**
     * Effective stationary radius: at least {@link #STATIONARY_RADIUS_METERS},
     * otherwise {@link #STATIONARY_ACCURACY_FACTOR} times announced accuracy.
     * Without accuracy, fall back to the floor so a missing report cannot
     * disable the detector.
     */
    private static float stationaryRadiusMeters(Location location) {
        if (!location.hasAccuracy()) {
            return STATIONARY_RADIUS_METERS;
        }
        return Math.max(STATIONARY_RADIUS_METERS, location.getAccuracy() * STATIONARY_ACCURACY_FACTOR);
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
