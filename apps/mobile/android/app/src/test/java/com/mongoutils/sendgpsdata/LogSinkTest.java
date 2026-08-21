package com.mongoutils.sendgpsdata;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okio.Buffer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LogSinkTest {

    private static final String TEST_URL = "http://localhost:3004";
    private static final String TEST_DEVICE_ID = "device-42";

    @BeforeEach
    void resetState() {
        LogSink.serverUrl = null;
        LogSink.deviceId = null;
        LogSink.httpClient = mock(OkHttpClient.class);
    }

    // -------------------------------------------------------------------------
    // JSON structure
    // -------------------------------------------------------------------------

    @Test
    void jsonContainsExactlyTheFiveRequiredFields() throws JSONException {
        LogSink.deviceId = TEST_DEVICE_ID;

        JSONObject parsed = new JSONObject(LogSink.buildLogJson("DEBUG", "MyTag", "hello"));

        assertEquals(5, parsed.length());
        assertTrue(parsed.has("deviceId"));
        assertTrue(parsed.has("ts"));
        assertTrue(parsed.has("level"));
        assertTrue(parsed.has("tag"));
        assertTrue(parsed.has("message"));
    }

    @Test
    void jsonContainsConfiguredDeviceId() throws JSONException {
        LogSink.deviceId = TEST_DEVICE_ID;

        JSONObject parsed = new JSONObject(LogSink.buildLogJson("INFO", "Tag", "msg"));

        assertEquals(TEST_DEVICE_ID, parsed.getString("deviceId"));
    }

    @Test
    void jsonContainsTagAndMessage() throws JSONException {
        LogSink.deviceId = TEST_DEVICE_ID;

        JSONObject parsed = new JSONObject(LogSink.buildLogJson("WARN", "GpsCapture", "updates removed"));

        assertEquals("GpsCapture", parsed.getString("tag"));
        assertEquals("updates removed", parsed.getString("message"));
    }

    @Test
    void tsIsValidIso8601() throws JSONException {
        LogSink.deviceId = TEST_DEVICE_ID;

        String ts = new JSONObject(LogSink.buildLogJson("DEBUG", "Tag", "msg")).getString("ts");

        // Instant.parse throws DateTimeParseException if not ISO 8601
        assertDoesNotThrow(() -> Instant.parse(ts));
    }

    // -------------------------------------------------------------------------
    // Level mapping (through the public d/i/w/e API)
    // -------------------------------------------------------------------------

    @Test
    void levelMappingIsCorrect() throws Exception {
        LogSink.serverUrl = TEST_URL;
        LogSink.deviceId = TEST_DEVICE_ID;
        Call call = mock(Call.class);
        when(LogSink.httpClient.newCall(any())).thenReturn(call);

        LogSink.d("T", "m");
        LogSink.i("T", "m");
        LogSink.w("T", "m");
        LogSink.e("T", "m");

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(LogSink.httpClient, times(4)).newCall(captor.capture());

        String[] expectedLevels = {"DEBUG", "INFO", "WARN", "ERROR"};
        List<Request> requests = captor.getAllValues();
        for (int i = 0; i < expectedLevels.length; i++) {
            JSONObject parsed = new JSONObject(readBody(requests.get(i)));
            assertEquals(expectedLevels[i], parsed.getString("level"));
        }
    }

    // -------------------------------------------------------------------------
    // HTTP behavior
    // -------------------------------------------------------------------------

    @Test
    void configuredServerSendsPostToLogsEndpoint() throws Exception {
        LogSink.serverUrl = TEST_URL;
        LogSink.deviceId = TEST_DEVICE_ID;
        Call call = mock(Call.class);
        when(LogSink.httpClient.newCall(any())).thenReturn(call);

        LogSink.d("Tag", "msg");

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(LogSink.httpClient).newCall(captor.capture());
        verify(call).enqueue(any());

        Request request = captor.getValue();
        assertEquals(TEST_URL + "/logs", request.url().toString());
        assertEquals("POST", request.method());
    }

    @Test
    void nullServerUrlSkipsHttpSend() {
        LogSink.serverUrl = null;
        LogSink.deviceId = TEST_DEVICE_ID;

        LogSink.d("Tag", "msg");
        LogSink.e("Tag", "err");

        verify(LogSink.httpClient, never()).newCall(any());
    }

    @Test
    void emptyServerUrlSkipsHttpSend() {
        LogSink.serverUrl = "";
        LogSink.deviceId = TEST_DEVICE_ID;

        LogSink.d("Tag", "msg");

        verify(LogSink.httpClient, never()).newCall(any());
    }

    @Test
    void nullDeviceIdSkipsHttpSend() {
        LogSink.serverUrl = TEST_URL;
        LogSink.deviceId = null;

        LogSink.d("Tag", "msg");

        verify(LogSink.httpClient, never()).newCall(any());
    }

    // -------------------------------------------------------------------------
    // init() — SharedPreferences precedence over BuildConfig URL
    // -------------------------------------------------------------------------

    @Test
    void initPrefersSharedPreferencesOverProvidedUrl() {
        Context context = mock(Context.class);
        SharedPreferences prefs = mock(SharedPreferences.class);
        when(context.getSharedPreferences("gps_prefs", Context.MODE_PRIVATE)).thenReturn(prefs);
        when(prefs.getString("log_server_url", null)).thenReturn("http://pref-host:1234");

        LogSink.init(context, "http://buildconfig-host:3004", TEST_DEVICE_ID);

        assertEquals("http://pref-host:1234", LogSink.serverUrl);
        assertEquals(TEST_DEVICE_ID, LogSink.deviceId);
    }

    @Test
    void initFallsBackToProvidedUrlWhenNoPreference() {
        Context context = mock(Context.class);
        SharedPreferences prefs = mock(SharedPreferences.class);
        when(context.getSharedPreferences("gps_prefs", Context.MODE_PRIVATE)).thenReturn(prefs);
        when(prefs.getString("log_server_url", null)).thenReturn(null);

        LogSink.init(context, "http://buildconfig-host:3004", TEST_DEVICE_ID);

        assertEquals("http://buildconfig-host:3004", LogSink.serverUrl);
    }

    @Test
    void initFallsBackToProvidedUrlWhenPreferenceIsEmpty() {
        Context context = mock(Context.class);
        SharedPreferences prefs = mock(SharedPreferences.class);
        when(context.getSharedPreferences("gps_prefs", Context.MODE_PRIVATE)).thenReturn(prefs);
        when(prefs.getString("log_server_url", null)).thenReturn("");

        LogSink.init(context, "http://buildconfig-host:3004", TEST_DEVICE_ID);

        assertEquals("http://buildconfig-host:3004", LogSink.serverUrl);
    }

    // -------------------------------------------------------------------------

    private static String readBody(Request request) throws IOException {
        Buffer buffer = new Buffer();
        request.body().writeTo(buffer);
        return buffer.readUtf8();
    }
}
