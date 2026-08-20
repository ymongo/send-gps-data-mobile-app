package com.mongoutils.sendgpsdata;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Capacitor plugin that bridges the WebView frontend to the native
 * GpsTrackingService. Exposes start/stop methods and forwards service
 * events (connectionState, gpsStatus, error) as Capacitor listener events.
 */
@CapacitorPlugin(name = "NativeService")
public class NativeServicePlugin extends Plugin {

    private boolean receiverRegistered = false;

    private final BroadcastReceiver serviceEventReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            // Log lines come through a dedicated action; service events through the
            // shared ACTION_SERVICE_EVENT (with EXTRA_EVENT_NAME).
            String action = intent.getAction();
            String eventName = (LogSink.ACTION_LOG.equals(action))
                    ? "log"
                    : intent.getStringExtra(GpsTrackingService.EXTRA_EVENT_NAME);
            if (eventName == null) return;

            JSObject data = new JSObject();

            switch (eventName) {
                case "connectionState":
                    data.put("state", intent.getStringExtra(GpsTrackingService.EXTRA_STATE));
                    data.put("message", intent.getStringExtra(GpsTrackingService.EXTRA_MESSAGE));
                    break;
                case "gpsStatus":
                    data.put("status", intent.getStringExtra(GpsTrackingService.EXTRA_STATUS));
                    break;
                case "error":
                    data.put("message", intent.getStringExtra(GpsTrackingService.EXTRA_MESSAGE));
                    break;
                case "log":
                    data.put("level", intent.getStringExtra(LogSink.EXTRA_LEVEL));
                    data.put("tag", intent.getStringExtra(LogSink.EXTRA_TAG));
                    data.put("message", intent.getStringExtra(LogSink.EXTRA_MESSAGE));
                    data.put("timestamp", intent.getLongExtra(LogSink.EXTRA_TIMESTAMP, 0));
                    break;
                default:
                    return;
            }

            notifyListeners(eventName, data);
        }
    };

    @PluginMethod
    public void start(PluginCall call) {
        String url = call.getString("url");
        String deviceId = call.getString("deviceId");

        if (url == null || url.isEmpty()) {
            call.reject("url is required");
            return;
        }
        if (deviceId == null || deviceId.isEmpty()) {
            call.reject("deviceId is required");
            return;
        }

        Intent intent = new Intent(getContext(), GpsTrackingService.class);
        intent.putExtra(GpsTrackingService.EXTRA_URL, url);
        intent.putExtra(GpsTrackingService.EXTRA_DEVICE_ID, deviceId);
        ContextCompat.startForegroundService(getContext(), intent);

        // Request battery optimization exemption so the service survives in background.
        BatteryOptimizationHelper.requestBatteryOptimizationExemption(getContext());

        registerReceiver();
        call.resolve();
    }

    @PluginMethod
    public void stop(PluginCall call) {
        Intent intent = new Intent(getContext(), GpsTrackingService.class);
        getContext().stopService(intent);

        // NOTE: do NOT unregister the receiver here. The log console must keep
        // receiving log events across start/stop cycles; the receiver lives for
        // the whole plugin lifetime (only released in handleOnDestroy).
        call.resolve();
    }

    @PluginMethod
    public void getRecentLogs(PluginCall call) {
        JSObject data = new JSObject();
        data.put("logs", LogSink.getRecentLogs());
        call.resolve(data);
    }

    @PluginMethod
    public void clearLogs(PluginCall call) {
        LogSink.clearLogs();
        call.resolve();
    }

    @Override
    protected void handleOnDestroy() {
        unregisterReceiver();
        super.handleOnDestroy();
    }

    private void registerReceiver() {
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter(GpsTrackingService.ACTION_SERVICE_EVENT);
            filter.addAction(LogSink.ACTION_LOG);
            LocalBroadcastManager.getInstance(getContext()).registerReceiver(serviceEventReceiver, filter);
            receiverRegistered = true;
        }
    }

    private void unregisterReceiver() {
        if (receiverRegistered) {
            LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(serviceEventReceiver);
            receiverRegistered = false;
        }
    }
}
