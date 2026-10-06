package com.mongoutils.sendgpsdata;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the decision that drives notification removal: every condition that
 * means tracking has actually stopped must be classified as fatal, so the
 * service (and its "GPS tracking is active" notification) is torn down.
 */
class TrackingLifecycleTest {

    @Test
    void deliberateCloseStopsTracking() {
        assertTrue(TrackingLifecycle.isFatalDisconnect("Connection closed (code=1000)"));
        assertTrue(TrackingLifecycle.isFatalDisconnect("Connection closed (code=1000, reason=No heartbeat)"));
    }

    @Test
    void transientDropKeepsReconnecting() {
        assertFalse(TrackingLifecycle.isFatalDisconnect("Connection failed: timeout"));
        assertFalse(TrackingLifecycle.isFatalDisconnect("Connection closed (code=1006)"));
        assertFalse(TrackingLifecycle.isFatalDisconnect(null));
    }

    @Test
    void deviceDisconnectedMessageStopsTracking() {
        assertTrue(TrackingLifecycle.isDeviceDisconnectedMessage(
                "{\"type\":\"device-disconnected\",\"deviceId\":\"abc\"}"));
        assertTrue(TrackingLifecycle.isDeviceDisconnectedMessage(
                "{ \"type\" : \"device-disconnected\" }"));
    }

    @Test
    void otherMessagesDoNotStopTracking() {
        assertFalse(TrackingLifecycle.isDeviceDisconnectedMessage("{\"type\":\"server-ping\"}"));
        assertFalse(TrackingLifecycle.isDeviceDisconnectedMessage("not json"));
        assertFalse(TrackingLifecycle.isDeviceDisconnectedMessage(""));
        assertFalse(TrackingLifecycle.isDeviceDisconnectedMessage(null));
    }

    @Test
    void fatalErrorsStopTracking() {
        assertTrue(TrackingLifecycle.isFatalTrackingError("Max reconnection attempts reached"));
        assertTrue(TrackingLifecycle.isFatalTrackingError("Location permission denied"));
        assertTrue(TrackingLifecycle.isFatalTrackingError("GPS provider not available on this device"));
        assertTrue(TrackingLifecycle.isFatalTrackingError("No location providers available"));
        assertTrue(TrackingLifecycle.isFatalTrackingError("LocationManager not available"));
        assertTrue(TrackingLifecycle.isFatalTrackingError("Missing url or deviceId"));
    }

    @Test
    void transientErrorsDoNotStopTracking() {
        assertFalse(TrackingLifecycle.isFatalTrackingError("WebSocket error: timeout"));
        assertFalse(TrackingLifecycle.isFatalTrackingError("Failed to send ping: closed"));
        assertFalse(TrackingLifecycle.isFatalTrackingError(null));
    }
}
