<div align="center">

# Send GPS Data — Self-Hosted GPS Tracking

**Open-source GPS tracking for Android. Stream your live location to your own server over WebSocket — private, self-hosted, no third party.**

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![Platform](https://img.shields.io/badge/platform-Android-black)
![Stack](https://img.shields.io/badge/stack-SvelteKit%20%2B%20Capacitor%20%2B%20Electron-ff3e00)

**Privacy-first location sharing · self-hosted · free · no account**

</div>

---

## ✨ What is this?

**Send GPS Data** lets you **track your location in real time** and send it to a **server you own**. It's built for people who want live location sharing **without** handing their position to a cloud provider.

- 📱 **Android app** (SvelteKit + Capacitor) reads GPS and streams it over **WebSocket**
- 🖥️ **Desktop app** (Electron) acts as the WebSocket server + a live map/overlay viewer
- 🔒 **Self-hosted & private** — your location goes to *your* machine, over *your* network
- 🌐 **Works over the internet** via **Tailscale** (WireGuard mesh VPN) with automatic TLS

> It's a **do-it-yourself alternative** to Google Find My Device / third-party trackers, where **you** keep the keys.

---

## 🚀 Features

| | |
|---|---|
| **Live GPS streaming** | Location sent over WebSocket to your server (latitude, longitude, accuracy, speed, altitude, heading) |
| **Self-hosted server** | Electron desktop app = the receiving server + viewer |
| **Secure transport** | `wss://` over Tailscale with automatic MagicDNS TLS certs |
| **Favorite servers** | Save multiple server URLs and switch instantly (localStorage) |
| **QR code scan** | Point the camera at a server URL / Lightning tip to fill it in (open-source ZXing) |
| **Console logs** | Real-time in-app log viewer (buffer capped at last 50 lines) |
| **Data format preview** | See exactly what JSON payload is sent |
| **Bitcoin tipping** | ⚡ Lightning tips via a built-in SvelteKit widget (Alby direct, 3$, 5$, 7$) |

---

## 🏗️ Architecture

```
┌────────────────────┐        WebSocket         ┌─────────────────────┐
│  Android app       │ ───────────────────────▶ │  Desktop app        │
│  (SvelteKit +      │     wss://your-tailnet    │  (Electron)         │
│   Capacitor)       │      :8443/ws             │  WS server :3003    │
└────────────────────┘                           └─────────────────────┘
        │  GPS (Geolocation)
        ▼
   native Android service
   (WebSocketManager.java)
```

**Monorepo (npm workspaces):**

| Path | Package | Role |
|---|---|---|
| `apps/mobile` | `gps-mobile` | Android app (SvelteKit + Capacitor, native GPS/WS service) |
| `apps/desktop` | `gps-desktop` | Electron app (WebSocket server + renderer viewer) |
| `packages/*` | `gps-shared` | Shared types/constants |
| `tools/log-server` | `@gps/log-server` | Standalone real-time log sink (dev) |

---

## 🧱 Tech stack

- **Mobile:** SvelteKit (`ssr=false`) · Capacitor 7 · native Android service (Java) · `@capacitor/geolocation`
- **Desktop:** Electron 28 · `ws` (WebSocket) · Svelte renderer
- **Scanning:** `@capacitor/barcode-scanner` (official plugin, ZXing on Android — Apache-2.0, F-Droid friendly)
- **Tests:** Playwright (mobile e2e) · Vitest (unit) · JUnit (native service)

---

## 🛠️ Getting started

### Prerequisites

- Node.js ≥ 20 (npm workspaces), JDK 17+ (Android build)
- Android Studio / Android SDK (for `cap sync` + Gradle)
- (optional) [Tailscale](https://tailscale.com) on your machine + phone for internet tracking

### Build the Android APK

```bash
npm install                    # hoist workspace deps at root
cd apps/mobile

# Build the release APK (web bundle + e2e tests + cap sync + gradle)
npm run build:apk              # = bash scripts/build-apk.sh
# APK: apps/mobile/android/app/build/outputs/apk/release/app-release-*.apk

# Or step by step:
npm run build:prod             # vite build && playwright test
npm run sync                   # cap sync
```

> **Note (Windows):** the `--script-shell="C:\Program Files\Git\usr\bin\bash.exe"` flag is used for `npm run build:prod` on Windows/MSYS.

### Run the desktop server

```bash
cd apps/desktop
npm run build && npm run start   # WebSocket server on :3003 by default
```

### Secure transport over the internet (Tailscale)

Android blocks cleartext `ws://` in production, so expose the desktop WebSocket as `wss://`:

```bash
tailscale serve --bg --https=8443 http://localhost:3003   # expose WS as wss
tailscale serve status
```

Then in the app enter: `your-tailnet-host:8443` (the app prefixes `wss://` and adds `/ws`).

### Development

```bash
npm run dev:mobile     # Vite dev server (apps/mobile)
npm run dev:desktop    # Electron dev
npm run dev:log-server # real-time log sink (:3004)
```

---

## 🧪 Testing

```bash
npm run test:all        # all workspaces unit tests (Vitest/JUnit)
cd apps/mobile && npm run test:e2e   # Playwright mobile UI tests (in build:prod)
```

E2E tests are wired into the build so every APK is verified for UI regressions before release.

---

## 🔒 Privacy

- **Your location stays yours** — it's sent over your own network/WebSocket to your own server.
- **No third-party analytics, no tracking SDKs, no account required.**
- Remote log POST (`LogSink`) is **off by default**; enable per-build with `PROD_DEBUG_LOGS=true` (dev telemetry only).

> ✅ **F-Droid:** the QR scanner uses **ZXing** (open-source, Apache-2.0) — no proprietary ML Kit. GPS uses the standard Android Location API (no Google Play Services). The app is F-Droid friendly.

---

## ⚖️ License

MIT © the contributors.

The app is fully open source — including the built-in Lightning tip widget (SvelteKit component talking directly to Alby's LNURL endpoint).

---

## 🤝 Contributing

Open an issue or PR. This is a personal project — feedback, bug reports, and ideas welcome.

---

*Self-hosted GPS tracking · privacy first · Bitcoin tips accepted ⚡*
