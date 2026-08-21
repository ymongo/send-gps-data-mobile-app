package com.mongoutils.sendgpsdata;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;

/**
 * Helper to request battery optimization exemption so the foreground service
 * (GPS + WebSocket) keeps running reliably in the background.
 *
 * The permission REQUEST_IGNORE_BATTERY_OPTIMIZATIONS is declared in the
 * manifest but must be requested at runtime via an intent.
 */
public class BatteryOptimizationHelper {

    private BatteryOptimizationHelper() {
        // static utility
    }

    /**
     * Returns true if the app is already exempted from battery optimization.
     */
    public static boolean isIgnoringBatteryOptimizations(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    /**
     * Requests battery optimization exemption. No-op if already exempted.
     * Launches the system ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS screen,
     * with a fallback to the generic battery settings if that fails.
     */
    public static void requestBatteryOptimizationExemption(Context context) {
        if (context == null) return;
        if (isIgnoringBatteryOptimizations(context)) return;

        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            // Fallback: open the generic battery optimization settings screen
            try {
                Intent fallback = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
            } catch (Exception ignored) {
                // Nothing else we can do — battery optimization stays enabled.
            }
        }
    }
}
