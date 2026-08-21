# Send GPS Data (Android) — Self-Hosted GPS Location Streaming

**Standalone source for the Android app** that streams your live GPS location
to a server **you own** over WebSocket — private, self-hosted, no account, no
cloud, no third party.

Built with **SvelteKit + Capacitor**, QR scanning via **ZXing** (F-Droid
friendly), and the standard Android **Location API**.

> A do-it-yourself alternative to third-party location trackers — where
> **you** keep the keys.

---

## 🤖 Built with AI assistance

This app was developed with the help of several AI coding assistants and
large language models:

- **AWS Kiro** — [coding agent](https://kiro.dev/)
- **Claude** (Anthropic) — multiple models
- **Hermes Agent** — [NousResearch](https://github.com/NousResearch/hermes-agent) — with **DeepSeek** and **Kimi** models

The human author designed, reviewed, and validated the architecture and
every release.

---

## ✨ What it does

- 📡 Streams GPS (lat/long/accuracy/speed/altitude/heading) over **WebSocket** to your own server
- 🔒 Privacy-first: your location goes to *your* machine, over *your* network
- 🔍 **QR scan** — point the camera at a **server URL** to fill it in (**ZXing**, open-source)
- ⚡ **Bitcoin (Lightning) tips** — built-in SvelteKit widget (Alby direct), with a **QR code** generated locally to pay with your wallet

### Connect to any server you own

The app sends to **any** server that exposes a **WebSocket (`ws://` or `wss://`)**
endpoint — the Electron desktop app in the main repo, a Raspberry Pi, a VPS,
or any self-hosted endpoint.

- **Tailscale is one optional convenience**, not bundled and not required: it
  gives you an encrypted mesh VPN with automatic TLS so you can reach your
  home server over the internet without opening ports. You can also use any
  other tunnel, a reverse proxy (Caddy/nginx), a public VPS, or a LAN-only
  server.
- On Android, cleartext `ws://` is blocked in production builds, so for
  internet access you'll want **`wss://`** (TLS).

## 📦 Tech stack & credits

- **Frontend:** [SvelteKit](https://svelte.dev/) (`ssr=false`, prerender) · Tailwind + [DaisyUI](https://daisyui.com/) (synthwave theme)
- **Mobile runtime:** [Capacitor 7](https://capacitorjs.com/) · native Android service (Java)
- **GPS:** `@capacitor/geolocation` + standard Android Location API
- **QR scanning:** [`@capacitor/barcode-scanner`](https://github.com/capacitor-community/barcode-scanner) (**ZXing** on Android, Apache-2.0)
- **QR generation:** [`qrcode`](https://github.com/soldair/node-qrcode) (local, no server)
- **Lightning tips:** [Alby](https://getalby.com/) LNURL (direct, no server) — widget inspired by [Twenty Uno's Lightning widget](https://github.com/reneaaron/lightning-widget)
- **Tests:** [Playwright](https://playwright.dev/) (e2e) · [Vitest](https://vitest.dev/) (unit) · JUnit (native)

---

## 🛠️ Build the APK

Requires: Node.js ≥ 20, Android SDK (JDK 17+).

```bash
npm ci                       # install workspace deps (mobile + shared)
cd apps/mobile

npm run build:prod           # vite build && playwright e2e tests
npx cap sync                 # copy web bundle into android/
cd android
./gradlew assembleRelease    # or: set JAVA_HOME then gradle
# APK: apps/mobile/android/app/build/outputs/apk/release/app-release-*.apk
```

Or one command from `apps/mobile`:
```bash
npm run build:apk            # = bash scripts/build-apk.sh (build + sync + gradle)
```

---

## 🔒 Privacy

- **No tracking SDKs, no analytics, no account.**
- GPS uses the standard Android Location API.
- Remote log POST (`LogSink`) is **off by default**; only enabled per-build
  with `PROD_DEBUG_LOGS=true`.
- QR scanner is **ZXing** (open source, Apache-2.0) — **F-Droid friendly**.

---

## 📦 F-Droid / Play

This repo is the **F-Droid-ready source**. See:
- [`.fdroid.yml`](./.fdroid.yml) — F-Droid build recipe reference
- [`F-DROID.md`](./F-DROID.md) — submission guide

**Package:** `com.mongoutils.sendgpsdata` · minSdk 26 · targetSdk 36
**License:** MIT

---

## ⚖️ License

MIT © the contributors. The built-in Lightning tip widget is a SvelteKit
component (open source) talking directly to Alby's LNURL endpoint.
