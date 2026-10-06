package com.mongoutils.sendgpsdata;

import android.location.Location;
import android.location.LocationManager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Speed source selection. The OBS overlay was stuck at 0 because every fix
 * worse than 15 m was discarded and speed was only ever derived from position
 * deltas. These tests pin the replacement rules:
 * Doppler speed from the GPS provider is primary (even at poor accuracy),
 * haversine is the fallback (accuracy gate 50 m), and a latched stop is 0.
 *
 * Locations are mocks, matching the rest of this suite: the android.jar on
 * the unit-test classpath is a stub whose methods throw, so
 * {@code new Location(provider)} cannot carry the fields these cases need.
 */
class GpsCaptureSpeedTest {

    private static final double PARIS_LAT = 48.8566;
    private static final double PARIS_LON = 2.3522;
    private static final long T0 = 1_700_000_000_000L;

    private final GpsCapture capture = new GpsCapture(new GpsCapture.Callback() {
        @Override public void onLocationUpdate(Location location, float speedMps) {}
        @Override public void onGpsStatusChanged(String status) {}
        @Override public void onError(String message) {}
    });

    @Test
    void gpsFixWithProviderSpeedIgnoresPoorAccuracy() {
        // 40 m used to be rejected outright, which is why a moving bike under
        // trees reported 0. The chip's own speed must come through instead.
        Location fix = gpsFix(PARIS_LAT, PARIS_LON, 40f, T0, 5.5f);

        float speed = capture.computeReliableSpeed(fix);

        assertEquals(5.5f * 0.7f, speed, 0.01f);
        assertTrue(speed > 0f, "provider speed must not collapse to 0 at 40 m accuracy");
    }

    @Test
    void gpsFixWithoutSpeedFallsBackToPositionDeltas() {
        // ~11.1 m north per second ≈ 40 km/h, accuracy well inside the gate.
        Location first = gpsFix(PARIS_LAT, PARIS_LON, 8f, T0, null);
        Location second = gpsFix(PARIS_LAT + metersNorthToDegrees(22.2), PARIS_LON, 8f, T0 + 2000, null);

        assertEquals(0f, capture.computeReliableSpeed(first), 0f);
        float speed = capture.computeReliableSpeed(second);

        assertEquals(11.1f * 0.7f, speed, 0.3f);
    }

    @Test
    void inaccurateFixWithoutSpeedStaysAtZero() {
        Location fix = gpsFix(PARIS_LAT, PARIS_LON, 100f, T0, null);

        assertEquals(0f, capture.computeReliableSpeed(fix), 0f);
    }

    @Test
    void networkProviderSpeedIsNotTrusted() {
        // Network "speed" is itself a position delta in disguise. Without a
        // GPS provider it must take the accuracy-gated fallback, which rejects 100 m.
        Location fix = fix(LocationManager.NETWORK_PROVIDER, PARIS_LAT, PARIS_LON, 100f, T0, 9f);

        assertEquals(0f, capture.computeReliableSpeed(fix), 0f);
    }

    @Test
    void stationaryClusterReportsExactlyZero() {
        // A few metres of drift, held longer than the 4 s stationary window.
        // Accuracy is large on purpose: the detector must still latch, and
        // the Doppler reading must not keep the overlay alive.
        float[] drifts = {0f, 2f, 3f, 1.5f, 2.5f};
        float last = -1f;
        for (int i = 0; i < drifts.length; i++) {
            Location fix = gpsFix(
                    PARIS_LAT + metersNorthToDegrees(drifts[i]), PARIS_LON, 30f, T0 + i * 1500L, 1.2f);
            last = capture.computeReliableSpeed(fix);
        }

        assertEquals(0f, last, 0f);
    }

    @Test
    void speedDropsToZeroOnceStopped() {
        Location moving = gpsFix(PARIS_LAT, PARIS_LON, 40f, T0, 8f);
        float movingSpeed = capture.computeReliableSpeed(moving);
        assertTrue(movingSpeed > 1f, "precondition: the moving fix must report motion");

        float last = movingSpeed;
        for (int i = 1; i <= 4; i++) {
            Location stopped = gpsFix(
                    PARIS_LAT + metersNorthToDegrees(1.0), PARIS_LON, 12f, T0 + i * 1500L, 1.4f);
            last = capture.computeReliableSpeed(stopped);
        }

        assertEquals(0f, last, 0f);
    }

    private static Location gpsFix(double lat, double lon, float accuracy, long time, Float speed) {
        return fix(LocationManager.GPS_PROVIDER, lat, lon, accuracy, time, speed);
    }

    /** {@code speed == null} leaves {@link Location#hasSpeed()} false. */
    private static Location fix(String provider, double lat, double lon, float accuracy, long time, Float speed) {
        Location location = mock(Location.class);
        when(location.getProvider()).thenReturn(provider);
        when(location.getLatitude()).thenReturn(lat);
        when(location.getLongitude()).thenReturn(lon);
        when(location.hasAccuracy()).thenReturn(true);
        when(location.getAccuracy()).thenReturn(accuracy);
        when(location.getTime()).thenReturn(time);
        if (speed == null) {
            when(location.hasSpeed()).thenReturn(false);
        } else {
            when(location.hasSpeed()).thenReturn(true);
            when(location.getSpeed()).thenReturn(speed);
        }
        return location;
    }

    /** Metres of northing to degrees of latitude (spherical, good to ~1e-4 here). */
    private static double metersNorthToDegrees(double meters) {
        return meters / 111_320.0;
    }
}
