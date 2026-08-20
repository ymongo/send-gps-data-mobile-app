package com.mongoutils.sendgpsdata;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Sends a heartbeat ping when no data has been sent for 30 seconds.
 * Uses a one-shot ScheduledExecutorService approach: schedules a 30s delayed task,
 * cancels and reschedules on each onDataSent().
 * Thread-safe: all shared state access is synchronized.
 */
public class HeartbeatTimer {

    public static final long HEARTBEAT_INTERVAL_MS = 30_000;

    public interface Callback {
        void onHeartbeatNeeded();
    }

    private final Callback callback;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pendingHeartbeat;
    private long lastActivityMs;
    private boolean running;
    private boolean paused;

    public HeartbeatTimer(Callback callback) {
        this.callback = callback;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.running = false;
        this.paused = false;
    }

    /**
     * Pure function: returns true if enough time has elapsed since last activity
     * to warrant sending a heartbeat.
     *
     * @param lastActivityMs timestamp of last data sent (ms)
     * @param nowMs          current timestamp (ms)
     * @return true if nowMs - lastActivityMs >= HEARTBEAT_INTERVAL_MS
     */
    public static boolean shouldSendHeartbeat(long lastActivityMs, long nowMs) {
        return nowMs - lastActivityMs >= HEARTBEAT_INTERVAL_MS;
    }

    /**
     * Starts the heartbeat timer. Records current time as last activity
     * and schedules the first heartbeat check after 30 seconds.
     */
    public synchronized void start() {
        running = true;
        paused = false;
        lastActivityMs = System.currentTimeMillis();
        scheduleHeartbeat();
    }

    /**
     * Stops the heartbeat timer and cancels any pending task.
     */
    public synchronized void stop() {
        running = false;
        paused = false;
        cancelPending();
    }

    /**
     * Resets the 30-second countdown. Called when GPS data is sent
     * over the WebSocket, indicating the connection is active.
     */
    public synchronized void onDataSent() {
        lastActivityMs = System.currentTimeMillis();
        if (running && !paused) {
            cancelPending();
            scheduleHeartbeat();
        }
    }

    /**
     * Pauses the heartbeat timer on WebSocket disconnect.
     * Cancels the pending scheduled task.
     */
    public synchronized void pause() {
        paused = true;
        cancelPending();
    }

    /**
     * Resumes the heartbeat timer on WebSocket reconnect.
     * Resets last activity to now and restarts the countdown.
     */
    public synchronized void resume() {
        if (!running) {
            return;
        }
        paused = false;
        lastActivityMs = System.currentTimeMillis();
        scheduleHeartbeat();
    }

    /**
     * Schedules a one-shot delayed task that fires after HEARTBEAT_INTERVAL_MS.
     * When it fires, it checks if a heartbeat is needed and reschedules itself.
     */
    private void scheduleHeartbeat() {
        cancelPending();
        pendingHeartbeat = scheduler.schedule(() -> {
            synchronized (HeartbeatTimer.this) {
                if (!running || paused) {
                    return;
                }
                long now = System.currentTimeMillis();
                if (shouldSendHeartbeat(lastActivityMs, now)) {
                    callback.onHeartbeatNeeded();
                    lastActivityMs = now;
                }
                // Reschedule for the next interval
                if (running && !paused) {
                    scheduleHeartbeat();
                }
            }
        }, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Cancels the currently pending heartbeat task, if any.
     */
    private void cancelPending() {
        if (pendingHeartbeat != null) {
            pendingHeartbeat.cancel(false);
            pendingHeartbeat = null;
        }
    }
}
