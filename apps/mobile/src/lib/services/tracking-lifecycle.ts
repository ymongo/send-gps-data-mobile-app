/**
 * Pure decision logic for when GPS tracking must be torn down.
 *
 * The persistent "GPS tracking is active" notification is owned by the native
 * foreground service and only disappears when that service is destroyed
 * (stopForeground in onDestroy). So every path that means "tracking is no
 * longer actually running" must stop the service — otherwise the notification
 * keeps claiming the user is being tracked, which is a privacy leak.
 *
 * Kept free of Capacitor/Svelte so it can be unit-tested without a device.
 */

/** Close codes that mean the peer deliberately ended the session. */
export const DELIBERATE_CLOSE_CODE = 1000;

/** Marker the desktop server broadcasts when a device drops off the session. */
export const DEVICE_DISCONNECTED_TYPE = "device-disconnected";

/** Error messages that mean tracking cannot continue and must be torn down. */
const FATAL_ERROR_MESSAGES = [
  "Max reconnection attempts reached",
  "Location permission denied",
  "LocationManager not available",
  "GPS provider not available on this device",
  "No location providers available",
  "Missing url or deviceId",
];

export interface DisconnectInfo {
  code?: number;
  reason?: string;
}

/**
 * A socket close means "stop tracking" only when the peer closed it on
 * purpose (code 1000). Transport failures (1006 and friends) are transient
 * and must keep the native reconnection backoff running.
 */
export function isFatalDisconnect(info: DisconnectInfo): boolean {
  if (info.code === DELIBERATE_CLOSE_CODE) return true;
  // The native layer reports the close as a reason string, e.g.
  // "Connection closed (code=1000)".
  return (
    typeof info.reason === "string" &&
    info.reason.includes(`code=${DELIBERATE_CLOSE_CODE}`)
  );
}

/**
 * The desktop announces a dead device with `{ type: 'device-disconnected' }`.
 * Receiving it means this phone is no longer part of the session.
 */
export function isDeviceDisconnectedMessage(raw: string): boolean {
  if (!raw) return false;
  try {
    const parsed = JSON.parse(raw) as { type?: unknown };
    return parsed?.type === DEVICE_DISCONNECTED_TYPE;
  } catch {
    return false;
  }
}

/** True when a native error event means tracking has actually stopped. */
export function isFatalTrackingError(message: string): boolean {
  return FATAL_ERROR_MESSAGES.includes(message);
}
