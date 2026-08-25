import { registerPlugin } from '@capacitor/core';

export interface NativeLocationPlugin {
  /** Returns whether the app currently holds the location permission. */
  checkPermission(): Promise<{ granted: boolean }>;
  /**
   * Requests the location permission and reports both the grant result and
   * whether the system GPS provider is enabled (so the caller can distinguish
   * "services disabled" from "permission denied").
   */
  requestPermission(): Promise<{ granted: boolean; servicesEnabled: boolean; error?: string }>;
}

export const NativeLocation = registerPlugin<NativeLocationPlugin>('NativeLocation');
