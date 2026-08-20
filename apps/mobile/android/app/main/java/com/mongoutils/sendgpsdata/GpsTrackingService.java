package com.mongoutils.sendgpsdata;

import android.content.pm.ServiceInfo;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

/**
 * Foreground service that orchestrates GPS capture, WebSocket management,
 * reconnection, and heartbeat. Runs independently of the WebView lifecycle.
 */
public class GpsTrackingService extends Service implements
        WebSocketManager.Callback,
        GpsCapture.Callback,
        ReconnectionHandler.Callback,
        HeartbeatTimer.Callback {

    private static final String TAG = "GpsTrackingService";
    private static final String NOTIFICATION_CHANNEL_ID = "gps_tracking_channel";
    private static final String NOTIFICATION_CHANNEL_NAME = "GPS Tracking";
    private static final int NOTIFICATION_ID = 1;

    public static final String ACTION_SERVICE_EVENT = "com.mongoutils.sendgpsdata.SERVICE_EVENT";
    public static final String EXTRA_EVENT_NAME = "eventName";
    public static final String EXTRA_STATE = "state";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_DEVICE_ID = "deviceId";

    private GpsCapture gpsCapture;
    private WebSocketManager webSocketManager;
    private ReconnectionHandler reconnectionHandler;
    private HeartbeatTimer heartbeatTimer;

    private String normalizedUrl;
    private String deviceId;

    // Throttle very chatty log sources so the 50-line console keeps meaningful
    // lifecycle events (connect/disconnect/data-sent) instead of being flooded.
    private static final long GPS_STATUS_LOG_COOLDOWN_MS = 10_000L;
    private static final long DATA_SENT_LOG_COOLDOWN_MS = 10_000L;
    private long lastGpsStatusLogTime = 0L;
    private long lastDataSentLogTime = 0L;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Must call startForeground() ASAP to avoid ForegroundServiceStartNotAllowedException
        Notification notification = createNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION | ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        if (intent == null) {
            LogSink.e(TAG, "Received null intent, stopping service");
            stopSelf();
            return START_NOT_STICKY;
        }

        String url = intent.getStringExtra(EXTRA_URL);
        deviceId = intent.getStringExtra(EXTRA_DEVICE_ID);

        if (url == null || url.isEmpty() || deviceId == null || deviceId.isEmpty()) {
            LogSink.e(TAG, "Missing url or deviceId in intent");
            broadcastError("Missing url or deviceId");
            stopSelf();
            return START_NOT_STICKY;
        }

        LogSink.init(this, BuildConfig.LOG_SERVER_URL, deviceId);

        normalizedUrl = WebSocketManager.normalizeUrl(url);
        LogSink.d(TAG, "Starting service with url=" + normalizedUrl + " deviceId=" + deviceId);

        // Initialize all components
        gpsCapture = new GpsCapture(this);
        webSocketManager = new WebSocketManager(this);
        reconnectionHandler = new ReconnectionHandler(this);
        heartbeatTimer = new HeartbeatTimer(this);

        // Start components
        gpsCapture.start(this);
        webSocketManager.connect(normalizedUrl, deviceId);
        heartbeatTimer.start();

        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        LogSink.d(TAG, "Service onDestroy");

        if (gpsCapture != null) {
            gpsCapture.stop();
        }
        if (webSocketManager != null) {
            webSocketManager.disconnect();
        }
        if (reconnectionHandler != null) {
            reconnectionHandler.cancel();
        }
        if (heartbeatTimer != null) {
            heartbeatTimer.stop();
        }

        stopForeground(true);
        super.onDestroy();
    }

    // -------------------------------------------------------------------------
    // WebSocketManager.Callback
    // -------------------------------------------------------------------------

    @Override
    public void onConnected() {
        LogSink.d(TAG, "WebSocket connected");
        broadcastConnectionState("connected", "");
        reconnectionHandler.onConnectionSuccess();
        heartbeatTimer.resume();
    }

    @Override
    public void onDisconnected(String reason) {
        LogSink.d(TAG, "WebSocket disconnected: " + reason);
        broadcastConnectionState("disconnected", reason);
        heartbeatTimer.pause();
        reconnectionHandler.scheduleReconnect();
    }

    @Override
    public void onError(String message) {
        LogSink.e(TAG, "Error: " + message);
        broadcastError(message);
    }

    // -------------------------------------------------------------------------
    // GpsCapture.Callback
    // -------------------------------------------------------------------------

    @Override
    public void onLocationUpdate(Location location, float speedMps) {
        webSocketManager.sendGpsData(location, deviceId, speedMps);
        heartbeatTimer.onDataSent();
        maybeLogDataSent();
    }

    /** Logs "data sent" at most once per cooldown window to avoid flooding the console. */
    private void maybeLogDataSent() {
        long now = System.currentTimeMillis();
        if (now - lastDataSentLogTime >= DATA_SENT_LOG_COOLDOWN_MS) {
            lastDataSentLogTime = now;
            LogSink.d(TAG, "Data sent");
        }
    }

    @Override
    public void onGpsStatusChanged(String status) {
        long now = System.currentTimeMillis();
        if (now - lastGpsStatusLogTime >= GPS_STATUS_LOG_COOLDOWN_MS) {
            lastGpsStatusLogTime = now;
            LogSink.d(TAG, "GPS status changed: " + status);
        }
        broadcastGpsStatus(status);
    }

    // -------------------------------------------------------------------------
    // ReconnectionHandler.Callback
    // -------------------------------------------------------------------------

    @Override
    public void onReconnectAttempt(int attempt, long delayMs) {
        long delaySec = delayMs / 1000;
        String message = "Reconnecting in " + delaySec + "s (attempt " + attempt + "/" + ReconnectionHandler.MAX_ATTEMPTS + ")";
        LogSink.d(TAG, message);
        broadcastConnectionState("reconnecting", message);
    }

    @Override
    public void onReconnect() {
        LogSink.d(TAG, "Attempting reconnection to " + normalizedUrl);
        webSocketManager.connect(normalizedUrl, deviceId);
    }

    @Override
    public void onMaxAttemptsReached() {
        LogSink.e(TAG, "Max reconnection attempts reached");
        broadcastError("Max reconnection attempts reached");
    }

    // -------------------------------------------------------------------------
    // HeartbeatTimer.Callback
    // -------------------------------------------------------------------------

    @Override
    public void onHeartbeatNeeded() {
        webSocketManager.sendPing(deviceId);
    }

    // -------------------------------------------------------------------------
    // Notification
    // -------------------------------------------------------------------------

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    NOTIFICATION_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Intent tapIntent = new Intent(this, MainActivity.class);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, tapIntent, PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder
                .setContentTitle("Sending GPS data...")
                .setContentText("GPS tracking is active")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    // -------------------------------------------------------------------------
    // Broadcasting events to Capacitor plugin
    // -------------------------------------------------------------------------

    private void broadcastConnectionState(String state, String message) {
        Intent intent = new Intent(ACTION_SERVICE_EVENT);
        intent.putExtra(EXTRA_EVENT_NAME, "connectionState");
        intent.putExtra(EXTRA_STATE, state);
        intent.putExtra(EXTRA_MESSAGE, message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void broadcastGpsStatus(String status) {
        Intent intent = new Intent(ACTION_SERVICE_EVENT);
        intent.putExtra(EXTRA_EVENT_NAME, "gpsStatus");
        intent.putExtra(EXTRA_STATUS, status);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void broadcastError(String message) {
        Intent intent = new Intent(ACTION_SERVICE_EVENT);
        intent.putExtra(EXTRA_EVENT_NAME, "error");
        intent.putExtra(EXTRA_MESSAGE, message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
}
