package com.mongoutils.sendgpsdata;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.NativePlugin;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;

/**
 * Native Capacitor plugin that manages the location permission using ONLY the
 * Android native API (ContextCompat/ActivityCompat + LocationManager).
 *
 * This deliberately replaces @capacitor/geolocation, which pulled Google Play
 * Services (play-services-location) into the APK. This plugin imports NO
 * com.google.android.gms classes, so no Google/Play Services code is bundled.
 *
 * The @NativePlugin (legacy) annotation is required here: with the modern
 * @CapacitorPlugin annotation the Bridge routes permission results through the
 * AndroidX ActivityResult launcher instead of onRequestPermissionsResult, so
 * using ActivityCompat.requestPermissions(getActivity(), ...) directly would
 * leave the pending call unresolved. The legacy annotation makes the Bridge
 * deliver the result to handleRequestPermissionsResult() (i.e. onRequestPermissionsResult).
 */
@NativePlugin(
    name = "NativeLocation",
    permissionRequestCode = 4117
)
public class NativeLocationPlugin extends Plugin {

    private static final int REQUEST_LOCATION_PERMISSION = 4117;

    private PluginCall pendingPermissionCall;

    /**
     * Returns whether the app currently holds the location permission.
     * Uses only ContextCompat.checkSelfPermission.
     */
    @PluginMethod
    public void checkPermission(PluginCall call) {
        JSObject data = new JSObject();
        data.put("granted", hasLocationPermission());
        call.resolve(data);
    }

    /**
     * Requests the location permission and, once resolved, reports both the
     * grant result and whether the system location services (GPS provider)
     * are enabled. Uses ActivityCompat.requestPermissions against the
     * plugin's activity; the result is resolved in handleRequestPermissionsResult.
     */
    @PluginMethod
    public void requestPermission(PluginCall call) {
        if (getActivity() == null) {
            call.reject("Activity is not available");
            return;
        }

        if (hasLocationPermission()) {
            // No dialog needed; report the current state directly.
            call.resolve(buildResult(true));
            return;
        }

        // Store the pending call so it can be resolved when the OS returns the
        // permission dialog result via handleRequestPermissionsResult.
        pendingPermissionCall = call;

        ActivityCompat.requestPermissions(
                getActivity(),
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                REQUEST_LOCATION_PERMISSION);
    }

    /**
     * Invoked by the Bridge when the OS reports the permission dialog result
     * (the plugin's onRequestPermissionsResult handler). Resolves the promise
     * stored in requestPermission.
     */
    @Override
    protected void handleRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != REQUEST_LOCATION_PERMISSION) {
            return;
        }

        if (pendingPermissionCall == null) {
            return;
        }

        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;

        PluginCall call = pendingPermissionCall;
        pendingPermissionCall = null;
        call.resolve(buildResult(granted));
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(
                getContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Builds the shared JS result object for granted/denied responses,
     * including whether the GPS provider is currently enabled so the frontend
     * can distinguish "location services disabled" from "permission denied".
     */
    private JSObject buildResult(boolean granted) {
        LocationManager lm = (LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE);
        boolean servicesEnabled = lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER);

        JSObject data = new JSObject();
        data.put("granted", granted);
        data.put("servicesEnabled", servicesEnabled);
        return data;
    }
}
