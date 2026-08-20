package com.mongoutils.sendgpsdata;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Static log sink: drop-in replacement for Log.d/i/w/e that also POSTs each log
 * entry to a remote log server. Fire-and-forget: sends are asynchronous (OkHttp
 * enqueue), failures are silently discarded, and no call ever throws — the
 * tracking service must never be affected by telemetry.
 *
 * Local logcat output is always preserved (the original Log.x is called first).
 */
public class LogSink {

    private static final String PREFS_NAME = "gps_prefs";
    private static final String PREF_KEY_LOG_SERVER_URL = "log_server_url";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json");

    // In-app log console: broadcast each log line to the WebView so the Settings
    // "Console Logs" block can show a live terminal. Also keep a small in-memory
    // ring buffer so the UI can display recent lines even before a listener is attached.
    static final String ACTION_LOG = "com.mongoutils.sendgpsdata.LOG";
    static final String EXTRA_LEVEL = "level";
    static final String EXTRA_TAG = "tag";
    static final String EXTRA_MESSAGE = "message";
    static final String EXTRA_TIMESTAMP = "timestamp";
    static final int LOG_BUFFER_CAPACITY = 50;

    private static Context appContext = null;
    private static final LinkedList<String> logBuffer = new LinkedList<>();

    // Package-private (not final) so unit tests can inject a mock client.
    static OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .writeTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build();

    // Package-private so unit tests can set/inspect configuration directly.
    static String serverUrl = null;
    static String deviceId = null;

    private LogSink() {
        // Static utility — no instances.
    }

    /**
     * Initialize with server URL and device ID. Call once at service startup.
     * SharedPreferences "log_server_url" takes precedence over the provided URL
     * (BuildConfig default), so the server address can be changed without rebuild.
     */
    public static void init(Context context, String url, String id) {
        try {
            deviceId = id;
            String prefUrl = null;
            if (context != null) {
                appContext = context.getApplicationContext();
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                if (prefs != null) {
                    prefUrl = prefs.getString(PREF_KEY_LOG_SERVER_URL, null);
                }
            }
            serverUrl = (prefUrl != null && !prefUrl.isEmpty()) ? prefUrl : url;
        } catch (Throwable t) {
            // Never throw from the log sink — keep whatever was configured before.
        }
    }

    /** DEBUG log: logcat + remote send. */
    public static void d(String tag, String message) {
        Log.d(tag, message);
        sendLog("DEBUG", tag, message);
    }

    /** INFO log: logcat + remote send. */
    public static void i(String tag, String message) {
        Log.i(tag, message);
        sendLog("INFO", tag, message);
    }

    /** WARN log: logcat + remote send. */
    public static void w(String tag, String message) {
        Log.w(tag, message);
        sendLog("WARN", tag, message);
    }

    /** ERROR log: logcat + remote send. */
    public static void e(String tag, String message) {
        Log.e(tag, message);
        sendLog("ERROR", tag, message);
    }

    /** ERROR log with throwable: logcat + remote send (throwable message appended). */
    public static void e(String tag, String message, Throwable tr) {
        Log.e(tag, message, tr);
        sendLog("ERROR", tag, message + " | " + tr.getMessage());
    }

    /**
     * Builds the log entry JSON with the five required fields.
     * Package-private for unit tests.
     */
    static String buildLogJson(String level, String tag, String message) {
        try {
            JSONObject json = new JSONObject();
            json.put("deviceId", deviceId);
            json.put("ts", Instant.now().toString()); // ISO 8601, UTC
            json.put("level", level);
            json.put("tag", tag);
            json.put("message", message);
            return json.toString();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sends one log entry immediately (no batching, no buffering, no retry).
     * No-op when the server URL is not configured — local Log output still works.
     * Remote sending is disabled entirely unless the app was built with the
     * PROD_DEBUG_LOGS environment variable set (BuildConfig.PROD_DEBUG_LOGS).
     */
    static void sendLog(String level, String tag, String message) {
        // Always surface the line in the in-app console + buffer (regardless of remote flag).
        emitLocal(level, tag, message);

        // Network send gated at build time — local logcat output is unaffected.
        if (!BuildConfig.PROD_DEBUG_LOGS) return;
        if (serverUrl == null || serverUrl.isEmpty() || deviceId == null) return;

        try {
            RequestBody body = RequestBody.create(
                    buildLogJson(level, tag, message),
                    JSON_MEDIA_TYPE);
            Request request = new Request.Builder()
                    .url(serverUrl + "/logs")
                    .post(body)
                    .build();

            // Fire-and-forget: enqueue is async (OkHttp thread pool), callbacks discard.
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    // Silently discard — server unreachable, entry is lost.
                }

                @Override
                public void onResponse(Call call, Response response) {
                    response.close();
                }
            });
        } catch (Throwable t) {
            // Silently discard — the log sink must never crash the service.
            // Throwable (not just Exception): on API < 26, java.time.Instant is absent
            // and its use raises NoClassDefFoundError, an Error — it must be contained.
        }
    }

    /**
     * Broadcasts a log line to the WebView (in-app console) and appends it to the
     * in-memory ring buffer. Always runs, even when remote telemetry is disabled.
     */
    private static void emitLocal(String level, String tag, String message) {
        try {
            long now = System.currentTimeMillis();
            String line = "[" + formatTime(now) + "] [" + tag + "] " + message;
            synchronized (logBuffer) {
                logBuffer.addLast(line);
                while (logBuffer.size() > LOG_BUFFER_CAPACITY) {
                    logBuffer.removeFirst();
                }
            }
            if (appContext != null) {
                Intent intent = new Intent(ACTION_LOG);
                intent.putExtra(EXTRA_LEVEL, level);
                intent.putExtra(EXTRA_TAG, tag);
                intent.putExtra(EXTRA_MESSAGE, message);
                intent.putExtra(EXTRA_TIMESTAMP, now);
                LocalBroadcastManager.getInstance(appContext).sendBroadcast(intent);
            }
        } catch (Throwable t) {
            // Never throw — the console is best-effort.
        }
    }

    /** Formats a Unix ms timestamp as HH:mm:ss.SSS. */
    private static String formatTime(long millis) {
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US);
            return sdf.format(new java.util.Date(millis));
        } catch (Throwable t) {
            return String.valueOf(millis);
        }
    }

    /**
     * Returns the most recent log lines (oldest → newest), capped at LOG_BUFFER_CAPACITY.
     */
    public static String[] getRecentLogs() {
        synchronized (logBuffer) {
            return logBuffer.toArray(new String[0]);
        }
    }

    /**
     * Clears the in-memory log buffer.
     */
    public static void clearLogs() {
        synchronized (logBuffer) {
            logBuffer.clear();
        }
    }
}
