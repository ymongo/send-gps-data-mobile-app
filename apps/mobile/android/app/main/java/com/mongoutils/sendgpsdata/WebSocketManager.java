package com.mongoutils.sendgpsdata;

import android.location.Location;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

/**
 * Manages OkHttp WebSocket connection for GPS data transmission and heartbeat pings.
 * Handles server-ping/pong protocol. Does NOT handle reconnection or heartbeat timing —
 * those are delegated to ReconnectionHandler and HeartbeatTimer respectively.
 */
public class WebSocketManager {

    private final Callback callback;
    private OkHttpClient httpClient;
    private WebSocket webSocket;
    private boolean connected = false;
    private boolean manuallyStopped = false;

    public WebSocketManager(Callback callback) {
        this.callback = callback;
    }

    // -------------------------------------------------------------------------
    // URL normalization
    // -------------------------------------------------------------------------

    /**
     * Normalizes a raw URL to produce a valid WebSocket URL.
     * Default scheme is wss://, but ws:// is preserved if explicitly provided.
     * Does NOT append any path — the user provides the full URL.
     *
     * Rules:
     * - "hostname:port/path"      → "wss://hostname:port/path"
     * - "wss://hostname:port/ws"  → "wss://hostname:port/ws" (no change)
     * - "ws://hostname:port/ws"   → "ws://hostname:port/ws" (preserved)
     * - No path appended automatically
     */
    public static String normalizeUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return "wss://";
        }

        String url = rawUrl.trim();

        // If no scheme, default to wss://
        if (!url.startsWith("wss://") && !url.startsWith("ws://")) {
            url = "wss://" + url;
        }

        // Strip trailing slashes
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        return url;
    }

    // -------------------------------------------------------------------------
    // Connection management
    // -------------------------------------------------------------------------

    /**
     * Opens an OkHttp WebSocket connection.
     * URL should already be normalized by the caller.
     * On open: sends initial ping with deviceId and timestamp.
     * On message: responds to server-ping with pong.
     * On close/failure: notifies callback.
     */
    public void connect(String url, String deviceId) {
        synchronized (this) {
            manuallyStopped = false;

            // Idempotent: close any existing connection before opening a new one.
            // Prevents duplicate concurrent connections that cause the server
            // to close the previous one (code 1000) in a reconnect loop.
            if (webSocket != null) {
                WebSocket old = webSocket;
                webSocket = null;
                connected = false;
                try {
                    old.close(1000, "Reconnecting");
                } catch (Exception ignored) {
                }
            }

            if (httpClient == null) {
                httpClient = new OkHttpClient.Builder()
                        .pingInterval(30, TimeUnit.SECONDS)
                        .build();
            }
        }

        Request request = new Request.Builder().url(url).build();
        WebSocket ws = httpClient.newWebSocket(request, new InternalWebSocketListener(deviceId));

        synchronized (this) {
            webSocket = ws;
        }
    }

    /**
     * Closes the WebSocket connection. Sets manuallyStopped flag.
     */
    public void disconnect() {
        WebSocket ws;
        synchronized (this) {
            manuallyStopped = true;
            connected = false;
            ws = webSocket;
            webSocket = null;
        }

        if (ws != null) {
            ws.close(1000, "Disconnected by user");
        }
    }

    /**
     * Returns whether the WebSocket is currently connected.
     */
    public synchronized boolean isConnected() {
        return connected;
    }

    // -------------------------------------------------------------------------
    // JSON builders (static, testable)
    // -------------------------------------------------------------------------

    static String buildPingJson(String deviceId) {
        try {
            JSONObject json = new JSONObject();
            json.put("type", "ping");
            json.put("deviceId", deviceId);
            json.put("timestamp", System.currentTimeMillis());
            return json.toString();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    static String buildPongJson(String deviceId) {
        try {
            JSONObject json = new JSONObject();
            json.put("type", "pong");
            json.put("deviceId", deviceId);
            json.put("timestamp", System.currentTimeMillis());
            return json.toString();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    static String buildGpsJson(Location location, String deviceId, float speedMps) {
        try {
            JSONObject json = new JSONObject();
            json.put("latitude", location.getLatitude());
            json.put("longitude", location.getLongitude());
            json.put("accuracy", location.getAccuracy());
            json.put("speed", speedMps);
            json.put("altitude", location.hasAltitude() ? location.getAltitude() : JSONObject.NULL);
            json.put("altitudeAccuracy",
                    location.hasVerticalAccuracy() ? location.getVerticalAccuracyMeters() : JSONObject.NULL);
            json.put("heading", location.hasBearing() ? location.getBearing() : JSONObject.NULL);
            json.put("timestamp", location.getTime());
            json.put("deviceId", deviceId);
            return json.toString();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    // -------------------------------------------------------------------------
    // Sending
    // -------------------------------------------------------------------------

    /**
     * Serializes a Location to JSON and sends it over the WebSocket.
     * Fields: latitude, longitude, accuracy, speed, altitude, altitudeAccuracy, heading, timestamp, deviceId.
     * speed, altitude, heading may be null if not available from Location.
     */
    public void sendGpsData(Location location, String deviceId, float speedMps) {
        WebSocket ws;
        synchronized (this) {
            ws = webSocket;
            if (!connected || ws == null) {
                return;
            }
        }

        try {
            ws.send(buildGpsJson(location, deviceId, speedMps));
        } catch (RuntimeException e) {
            if (callback != null) {
                callback.onError("Failed to serialize GPS data: " + e.getMessage());
            }
        }
    }

    /**
     * Sends a heartbeat ping message with deviceId and current timestamp.
     */
    public void sendPing(String deviceId) {
        WebSocket ws;
        synchronized (this) {
            ws = webSocket;
            if (!connected || ws == null) {
                return;
            }
        }

        try {
            ws.send(buildPingJson(deviceId));
        } catch (RuntimeException e) {
            if (callback != null) {
                callback.onError("Failed to send ping: " + e.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Callback interface
    // -------------------------------------------------------------------------

    /**
     * Callback interface for WebSocket connection events.
     */
    public interface Callback {
        void onConnected();
        void onDisconnected(String reason);
        void onError(String message);
    }

    // -------------------------------------------------------------------------
    // Internal WebSocket listener
    // -------------------------------------------------------------------------

    private class InternalWebSocketListener extends WebSocketListener {

        private final String deviceId;

        InternalWebSocketListener(String deviceId) {
            this.deviceId = deviceId;
        }

        @Override
        public void onOpen(WebSocket ws, Response response) {
            synchronized (WebSocketManager.this) {
                connected = true;
            }

            try {
                ws.send(buildPingJson(deviceId));
            } catch (RuntimeException e) {
                // Unlikely with simple puts, but handle gracefully
            }

            if (callback != null) {
                callback.onConnected();
            }
        }

        @Override
        public void onMessage(WebSocket ws, String text) {
            try {
                JSONObject msg = new JSONObject(text);
                String type = msg.optString("type", "");

                if ("server-ping".equals(type)) {
                    ws.send(buildPongJson(deviceId));
                }
            } catch (JSONException e) {
                // Ignore malformed messages
            }
        }

        @Override
        public void onClosing(WebSocket ws, int code, String reason) {
            ws.close(1000, null);
        }

        @Override
        public void onClosed(WebSocket ws, int code, String reason) {
            boolean stopped;
            boolean isCurrent;
            synchronized (WebSocketManager.this) {
                connected = false;
                stopped = manuallyStopped;
                isCurrent = (webSocket == ws);
            }

            // Only signal disconnect if this socket is still the active one.
            // A socket closed during a reconnect (idempotent connect) is stale.
            if (!stopped && isCurrent && callback != null) {
                callback.onDisconnected("Connection closed (code=" + code + ")");
            }
        }

        @Override
        public void onFailure(WebSocket ws, Throwable t, Response response) {
            boolean stopped;
            boolean isCurrent;
            synchronized (WebSocketManager.this) {
                connected = false;
                stopped = manuallyStopped;
                isCurrent = (webSocket == ws);
            }

            if (callback != null) {
                callback.onError("WebSocket error: " + t.getMessage());
            }

            if (!stopped && isCurrent && callback != null) {
                callback.onDisconnected("Connection failed: " + t.getMessage());
            }
        }
    }
}
