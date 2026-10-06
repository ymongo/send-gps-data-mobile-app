package com.mongoutils.sendgpsdata;

/**
 * Pure decision logic for when GPS tracking must be torn down.
 *
 * The persistent "GPS tracking is active" notification is posted by
 * {@link GpsTrackingService} and only disappears when that service is
 * destroyed. So every path that means "tracking is no longer actually
 * running" must stop the service — otherwise the notification keeps claiming
 * the user is being tracked.
 *
 * Kept free of Android framework types so it can be unit-tested on the JVM.
 */
public final class TrackingLifecycle {

    /** Close code meaning the peer deliberately ended the session. */
    public static final int DELIBERATE_CLOSE_CODE = 1000;

    /** Marker the desktop server broadcasts when a device drops off the session. */
    public static final String DEVICE_DISCONNECTED_TYPE = "device-disconnected";

    private static final String[] FATAL_ERROR_MESSAGES = {
            "Max reconnection attempts reached",
            "Location permission denied",
            "LocationManager not available",
            "GPS provider not available on this device",
            "No location providers available",
            "Missing url or deviceId"
    };

    private TrackingLifecycle() {
    }

    /**
     * A socket close means "stop tracking" only when the peer closed it on
     * purpose (code 1000, e.g. "No heartbeat" or "New connection from same
     * device"). Transport failures (1006 and friends) are transient and must
     * keep the reconnection backoff running.
     */
    public static boolean isFatalDisconnect(String reason) {
        if (reason == null) {
            return false;
        }
        return reason.contains("code=" + DELIBERATE_CLOSE_CODE);
    }

    /**
     * The desktop announces a dead device with {@code { type: "device-disconnected" }}.
     * Receiving it means this phone is no longer part of the session.
     */
    public static boolean isDeviceDisconnectedMessage(String raw) {
        if (raw == null || raw.isEmpty()) {
            return false;
        }
        String compact = raw.replaceAll("\\s+", "");
        return compact.contains("\"type\":\"" + DEVICE_DISCONNECTED_TYPE + "\"");
    }

    /** True when an error means tracking cannot continue and must be torn down. */
    public static boolean isFatalTrackingError(String message) {
        if (message == null) {
            return false;
        }
        for (String fatal : FATAL_ERROR_MESSAGES) {
            if (fatal.equals(message)) {
                return true;
            }
        }
        return false;
    }
}
