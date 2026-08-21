package com.mongoutils.sendgpsdata;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import static org.junit.jupiter.api.Assertions.*;

class ReconnectionHandlerDelayTest {

    @Property
    boolean delayFollowsExponentialBackoff(
            @ForAll @IntRange(min = 1, max = 10) int attempt
    ) {
        long expected = Math.min(2000L * (1L << (attempt - 1)), 60000L);
        return expected == ReconnectionHandler.calculateDelay(attempt);
    }

    @Property
    boolean delayIsNeverNegative(
            @ForAll @IntRange(min = -100, max = 100) int attempt
    ) {
        return ReconnectionHandler.calculateDelay(attempt) >= 0;
    }

    @Property
    boolean delayIsNeverAboveMaxCap(
            @ForAll @IntRange(min = 1, max = 100) int attempt
    ) {
        return ReconnectionHandler.calculateDelay(attempt) <= 60000L;
    }

    @Property
    boolean delayIsAtLeastBaseDelay(
            @ForAll @IntRange(min = 1, max = 10) int attempt
    ) {
        return ReconnectionHandler.calculateDelay(attempt) >= 2000L;
    }

    @Example
    void attempt1Returns2000() {
        assertEquals(2000L, ReconnectionHandler.calculateDelay(1));
    }

    @Example
    void attempt2Returns4000() {
        assertEquals(4000L, ReconnectionHandler.calculateDelay(2));
    }

    @Example
    void attempt3Returns8000() {
        assertEquals(8000L, ReconnectionHandler.calculateDelay(3));
    }

    @Example
    void attempt4Returns16000() {
        assertEquals(16000L, ReconnectionHandler.calculateDelay(4));
    }

    @Example
    void attempt5Returns32000() {
        assertEquals(32000L, ReconnectionHandler.calculateDelay(5));
    }

    @Example
    void attempt6ReturnsCapped60000() {
        assertEquals(60000L, ReconnectionHandler.calculateDelay(6));
    }

    @Example
    void attempt10ReturnsCapped60000() {
        assertEquals(60000L, ReconnectionHandler.calculateDelay(10));
    }

    @Example
    void attempt0ReturnsDefault2000() {
        assertEquals(2000L, ReconnectionHandler.calculateDelay(0));
    }

    @Example
    void negativeAttemptReturnsDefault2000() {
        assertEquals(2000L, ReconnectionHandler.calculateDelay(-1));
    }
}
