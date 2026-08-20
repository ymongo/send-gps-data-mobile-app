# Send GPS Data — Android APK

**Self-hosted GPS tracking app for Android.** Stream your live location to your own server over WebSocket. Privacy-first, no account, no cloud.

> ⚡ Get the APK: scroll down to **Assets** and download `app-release-*.apk`.

---

## 📱 What you get

- Live GPS streaming (lat/long/accuracy/speed/altitude/heading) to your own WebSocket server
- Self-hosted desktop server (Electron) + in-app map viewer
- Secure `wss://` transport via Tailscale with automatic TLS
- Save & switch between favorite server URLs
- Scan a server URL / Lightning tip with the camera (open-source ZXing)
- Real-time console log viewer (last 50 lines)
- ⚡ Bitcoin (Lightning) tips — 3$, 5$, 7$ (built-in SvelteKit widget, Alby direct)

## 🔧 Install

1. Download the APK from **Assets** below.
2. Enable "Install from unknown sources" for the downloader if prompted.
3. Open the APK to install.
4. Enter your server address (`host:port`) and press **Start**.

> ℹ️ On Android, cleartext `ws://` is blocked in production — expose your server as `wss://` (e.g. via Tailscale Serve). See the README for the full setup.

## 🧪 Tested

- Playwright e2e (mobile UI) ✅
- Vitest / JUnit (unit + native service) ✅

## 📚 Docs

Full setup, architecture, and privacy notes: see the [README](../README.md).

## ⚖️ License

MIT — open source, self-hosted, privacy-first. Bitcoin tips accepted ⚡
