package com.mongoutils.sendgpsdata;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import static org.junit.jupiter.api.Assertions.*;

class HeartbeatTimerTimingTest {

    @Property
    boolean returnsTrueWhenEnoughTimeElapsed(
            @ForAll @LongRange(min = 0, max = 1000000000L) long lastActivity,
            @ForAll @LongRange(min = 30000L, max = 2000000000L) long offset
    ) {
        long now = lastActivity + offset;
        return HeartbeatTimer.shouldSendHeartbeat(lastActivity, now);
    }

    @Property
    boolean returnsFalseWhenNotEnoughTimeElapsed(
            @ForAll @LongRange(min = 30000L, max = 1000000000L) long lastActivity,
            @ForAll @LongRange(min = 0, max = 29999L) long offset
    ) {
        long now = lastActivity + offset;
        return !HeartbeatTimer.shouldSendHeartbeat(lastActivity, now);
    }

    @Property
    boolean returnsTrueAtExactBoundary(
            @ForAll @LongRange(min = 0, max = 1000000000L) long lastActivity
    ) {
        long now = lastActivity + HeartbeatTimer.HEARTBEAT_INTERVAL_MS;
        return HeartbeatTimer.shouldSendHeartbeat(lastActivity, now);
    }

    @Property
    boolean returnsFalseJustBeforeBoundary(
            @ForAll @LongRange(min = 1, max = 1000000000L) long lastActivity
    ) {
        long now = lastActivity + HeartbeatTimer.HEARTBEAT_INTERVAL_MS - 1;
        return !HeartbeatTimer.shouldSendHeartbeat(lastActivity, now);
    }

    @Property
    boolean resultIsConsistentWithThreshold(
            @ForAll @LongRange(min = 0, max = 1000000000L) long lastActivity,
            @ForAll @LongRange(min = 0, max = 2000000000L) long now
    ) {
        boolean expected = (now - lastActivity) >= HeartbeatTimer.HEARTBEAT_INTERVAL_MS;
        return expected == HeartbeatTimer.shouldSendHeartbeat(lastActivity, now);
    }

    @Example
    void exactly30SecondsReturnsTrue() {
        assertTrue(HeartbeatTimer.shouldSendHeartbeat(0, 30000));
    }

    @Example
    void justUnder30SecondsReturnsFalse() {
        assertFalse(HeartbeatTimer.shouldSendHeartbeat(0, 29999));
    }

    @Example
    void zeroDifferenceReturnsFalse() {
        assertFalse(HeartbeatTimer.shouldSendHeartbeat(1000, 1000));
    }

    @Example
    void largeDifferenceReturnsTrue() {
        assertTrue(HeartbeatTimer.shouldSendHeartbeat(0, 60000));
    }
}
