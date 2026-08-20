package com.mongoutils.sendgpsdata;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import android.location.Location;

import org.json.JSONException;
import org.json.JSONObject;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebSocketManagerGpsSerializationTest {

    @Property
    boolean gpsJsonContainsAllFieldsWithNonNullValues(
            @ForAll @DoubleRange(min = -90.0, max = 90.0) double latitude,
            @ForAll @DoubleRange(min = -180.0, max = 180.0) double longitude,
            @ForAll @FloatRange(min = 0f, max = 100f) float accuracy,
            @ForAll @DoubleRange(min = -1000f, max = 50000f) double altitude,
            @ForAll @FloatRange(min = 0f, max = 50f) float speed,
            @ForAll @FloatRange(min = 0f, max = 360f) float bearing,
            @ForAll @LongRange(min = 0, max = 2000000000000L) long timestamp,
            @ForAll @StringLength(min = 1, max = 200) String deviceId
    ) throws JSONException {
        Location location = mock(Location.class);
        when(location.getLatitude()).thenReturn(latitude);
        when(location.getLongitude()).thenReturn(longitude);
        when(location.getAccuracy()).thenReturn(accuracy);
        when(location.hasSpeed()).thenReturn(true);
        when(location.getSpeed()).thenReturn(speed);
        when(location.hasAltitude()).thenReturn(true);
        when(location.getAltitude()).thenReturn(altitude);
        when(location.hasVerticalAccuracy()).thenReturn(true);
        when(location.getVerticalAccuracyMeters()).thenReturn(accuracy);
        when(location.hasBearing()).thenReturn(true);
        when(location.getBearing()).thenReturn(bearing);
        when(location.getTime()).thenReturn(timestamp);

        String json = WebSocketManager.buildGpsJson(location, deviceId, speed);
        JSONObject parsed = new JSONObject(json);

        return latitude == parsed.getDouble("latitude")
                && longitude == parsed.getDouble("longitude")
                && accuracy == (float) parsed.getDouble("accuracy")
                && speed == (float) parsed.getDouble("speed")
                && altitude == parsed.getDouble("altitude")
                && timestamp == parsed.getLong("timestamp")
                && deviceId.equals(parsed.getString("deviceId"))
                && parsed.has("heading")
                && parsed.has("altitudeAccuracy");
    }

    @Property
    boolean gpsJsonContainsDeviceId(
            @ForAll @StringLength(min = 1, max = 200) String deviceId
    ) throws JSONException {
        Location location = mock(Location.class);
        when(location.getLatitude()).thenReturn(48.8566);
        when(location.getLongitude()).thenReturn(2.3522);
        when(location.getAccuracy()).thenReturn(5.0f);
        when(location.hasSpeed()).thenReturn(false);
        when(location.hasAltitude()).thenReturn(false);
        when(location.hasVerticalAccuracy()).thenReturn(false);
        when(location.hasBearing()).thenReturn(false);
        when(location.getTime()).thenReturn(1000000L);

        String json = WebSocketManager.buildGpsJson(location, deviceId, 0f);
        JSONObject parsed = new JSONObject(json);
        return deviceId.equals(parsed.getString("deviceId"));
    }

    @Property
    boolean gpsJsonHasNullForMissingOptionalFields(
            @ForAll @StringLength(min = 1, max = 100) String deviceId
    ) throws JSONException {
        Location location = mock(Location.class);
        when(location.getLatitude()).thenReturn(0.0);
        when(location.getLongitude()).thenReturn(0.0);
        when(location.getAccuracy()).thenReturn(1.0f);
        when(location.hasSpeed()).thenReturn(false);
        when(location.hasAltitude()).thenReturn(false);
        when(location.hasVerticalAccuracy()).thenReturn(false);
        when(location.hasBearing()).thenReturn(false);
        when(location.getTime()).thenReturn(0L);

        String json = WebSocketManager.buildGpsJson(location, deviceId, 0f);
        JSONObject parsed = new JSONObject(json);

        return parsed.getDouble("speed") == 0.0
                && parsed.isNull("altitude")
                && parsed.isNull("altitudeAccuracy")
                && parsed.isNull("heading");
    }

    @Property
    boolean gpsJsonIsValidJson(
            @ForAll @DoubleRange(min = -90.0, max = 90.0) double latitude,
            @ForAll @DoubleRange(min = -180.0, max = 180.0) double longitude,
            @ForAll @StringLength(min = 1, max = 100) String deviceId
    ) {
        Location location = mock(Location.class);
        when(location.getLatitude()).thenReturn(latitude);
        when(location.getLongitude()).thenReturn(longitude);
        when(location.getAccuracy()).thenReturn(1.0f);
        when(location.hasSpeed()).thenReturn(false);
        when(location.hasAltitude()).thenReturn(false);
        when(location.hasVerticalAccuracy()).thenReturn(false);
        when(location.hasBearing()).thenReturn(false);
        when(location.getTime()).thenReturn(0L);

        String json = WebSocketManager.buildGpsJson(location, deviceId, 0f);
        try {
            new JSONObject(json);
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    @Example
    void gpsJsonWithAllFieldsPresent() throws JSONException {
        Location location = mock(Location.class);
        when(location.getLatitude()).thenReturn(48.8566);
        when(location.getLongitude()).thenReturn(2.3522);
        when(location.getAccuracy()).thenReturn(5.0f);
        when(location.hasSpeed()).thenReturn(true);
        when(location.getSpeed()).thenReturn(12.5f);
        when(location.hasAltitude()).thenReturn(true);
        when(location.getAltitude()).thenReturn(35.0);
        when(location.hasVerticalAccuracy()).thenReturn(true);
        when(location.getVerticalAccuracyMeters()).thenReturn(3.0f);
        when(location.hasBearing()).thenReturn(true);
        when(location.getBearing()).thenReturn(180.0f);
        when(location.getTime()).thenReturn(1700000000000L);

        String json = WebSocketManager.buildGpsJson(location, "test-device", 12.5f);
        JSONObject parsed = new JSONObject(json);

        assertEquals(48.8566, parsed.getDouble("latitude"));
        assertEquals(2.3522, parsed.getDouble("longitude"));
        assertEquals(5.0, parsed.getDouble("accuracy"), 0.01);
        assertEquals(12.5, parsed.getDouble("speed"), 0.01);
        assertEquals(35.0, parsed.getDouble("altitude"), 0.01);
        assertEquals(3.0, parsed.getDouble("altitudeAccuracy"), 0.01);
        assertEquals(180.0, parsed.getDouble("heading"), 0.01);
        assertEquals(1700000000000L, parsed.getLong("timestamp"));
        assertEquals("test-device", parsed.getString("deviceId"));
    }
}
