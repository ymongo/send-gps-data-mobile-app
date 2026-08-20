package com.mongoutils.sendgpsdata;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import org.json.JSONException;
import org.json.JSONObject;

import static org.junit.jupiter.api.Assertions.*;

class WebSocketManagerMessageDeviceIdTest {

    @Property
    boolean pingJsonContainsDeviceId(
            @ForAll @StringLength(min = 1, max = 200) String deviceId
    ) throws JSONException {
        String json = WebSocketManager.buildPingJson(deviceId);
        JSONObject parsed = new JSONObject(json);
        return deviceId.equals(parsed.getString("deviceId"))
                && "ping".equals(parsed.getString("type"))
                && parsed.has("timestamp");
    }

    @Property
    boolean pongJsonContainsDeviceId(
            @ForAll @StringLength(min = 1, max = 200) String deviceId
    ) throws JSONException {
        String json = WebSocketManager.buildPongJson(deviceId);
        JSONObject parsed = new JSONObject(json);
        return deviceId.equals(parsed.getString("deviceId"))
                && "pong".equals(parsed.getString("type"))
                && parsed.has("timestamp");
    }

    @Property
    boolean pingJsonIsValidJson(
            @ForAll @StringLength(min = 1, max = 200) String deviceId
    ) {
        String json = WebSocketManager.buildPingJson(deviceId);
        try {
            new JSONObject(json);
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    @Property
    boolean pongJsonIsValidJson(
            @ForAll @StringLength(min = 1, max = 200) String deviceId
    ) {
        String json = WebSocketManager.buildPongJson(deviceId);
        try {
            new JSONObject(json);
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    @Property
    boolean pingJsonTimestampIsPositive(
            @ForAll @StringLength(min = 1, max = 100) String deviceId
    ) throws JSONException {
        String json = WebSocketManager.buildPingJson(deviceId);
        JSONObject parsed = new JSONObject(json);
        return parsed.getLong("timestamp") > 0;
    }

    @Example
    void pingJsonWithSimpleDeviceId() throws JSONException {
        String json = WebSocketManager.buildPingJson("device-123");
        JSONObject parsed = new JSONObject(json);
        assertEquals("ping", parsed.getString("type"));
        assertEquals("device-123", parsed.getString("deviceId"));
    }

    @Example
    void pongJsonWithSimpleDeviceId() throws JSONException {
        String json = WebSocketManager.buildPongJson("device-456");
        JSONObject parsed = new JSONObject(json);
        assertEquals("pong", parsed.getString("type"));
        assertEquals("device-456", parsed.getString("deviceId"));
    }
}
