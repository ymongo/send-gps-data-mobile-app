package com.mongoutils.sendgpsdata;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Exponential backoff reconnection handler using ScheduledExecutorService.
 * Thread-safe: all shared state access is synchronized.
 */
public class ReconnectionHandler {

    public static final int MAX_ATTEMPTS = 10;

    public interface Callback {
        void onReconnectAttempt(int attempt, long delayMs);
        void onReconnect();
        void onMaxAttemptsReached();
    }

    private final Callback callback;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pendingReconnect;
    private int attemptCount;

    public ReconnectionHandler(Callback callback) {
        this.callback = callback;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.attemptCount = 0;
    }

    /**
     * Pure function: calculates reconnection delay using exponential backoff.
     * Formula: min(2000 * 2^(attempt-1), 60000) ms
     * Sequence: 2s, 4s, 8s, 16s, 32s, 60s (capped)
     *
     * @param attempt attempt number starting at 1
     * @return delay in milliseconds
     */
    public static long calculateDelay(int attempt) {
        if (attempt < 1) {
            return 2000;
        }
        if (attempt > 30) {
            return 60000L;
        }
        long delay = 2000L * (1L << (attempt - 1));
        return Math.min(delay, 60000L);
    }

    /**
     * Schedules the next reconnection attempt with exponential backoff.
     * If max attempts reached, calls onMaxAttemptsReached and stops.
     */
    public synchronized void scheduleReconnect() {
        attemptCount++;

        if (attemptCount > MAX_ATTEMPTS) {
            callback.onMaxAttemptsReached();
            return;
        }

        long delayMs = calculateDelay(attemptCount);
        callback.onReconnectAttempt(attemptCount, delayMs);

        pendingReconnect = scheduler.schedule(() -> {
            callback.onReconnect();
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Resets the attempt counter on successful connection.
     */
    public synchronized void onConnectionSuccess() {
        attemptCount = 0;
    }

    /**
     * Cancels any pending scheduled reconnection and resets the attempt counter.
     */
    public synchronized void cancel() {
        if (pendingReconnect != null) {
            pendingReconnect.cancel(false);
            pendingReconnect = null;
        }
        attemptCount = 0;
    }
}
