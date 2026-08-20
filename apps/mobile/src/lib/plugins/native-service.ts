import { registerPlugin } from '@capacitor/core';

export interface NativeServicePlugin {
  start(options: { url: string; deviceId: string }): Promise<void>;
  stop(): Promise<void>;
  addListener(
    eventName: 'connectionState',
    callback: (event: { state: 'connected' | 'disconnected' | 'reconnecting'; message: string }) => void
  ): Promise<{ remove: () => void }>;
  addListener(
    eventName: 'gpsStatus',
    callback: (event: { status: 'active' | 'unavailable' | 'permissionDenied' }) => void
  ): Promise<{ remove: () => void }>;
  addListener(
    eventName: 'error',
    callback: (event: { message: string }) => void
  ): Promise<{ remove: () => void }>;
  addListener(
    eventName: 'log',
    callback: (event: { level: string; tag: string; message: string; timestamp: number }) => void
  ): Promise<{ remove: () => void }>;
  getRecentLogs(): Promise<{ logs: string[] }>;
  clearLogs(): Promise<void>;
  removeAllListeners(): Promise<void>;
}

export const NativeService = registerPlugin<NativeServicePlugin>('NativeService');
