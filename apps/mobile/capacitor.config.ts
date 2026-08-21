import type { CapacitorConfig } from '@capacitor/cli';

// Use HTTP in dev (allows ws:// WebSockets), HTTPS in prod (requires wss://)
const isDev = process.env.NODE_ENV !== 'production';

const config: CapacitorConfig = {
  appId: 'com.mongoutils.sendgpsdata',
  appName: 'Send GPS Data',
  webDir: 'build',
  server: {
    androidScheme: isDev ? 'http' : 'https'
  },
  android: {
    useLegacyBridge: true
  }
};

export default config;
