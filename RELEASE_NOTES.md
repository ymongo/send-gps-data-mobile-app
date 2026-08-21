# Send GPS Data — Android APK

**Self-hosted GPS tracking app for Android.** Stream your live location to your own server over WebSocket. Privacy-first, no account, no cloud.

> ⚡ Get the APK: scroll down to **Assets** and download `app-release-*.apk`.

---

## 🤖 Built with AI assistance

This app was developed with the help of AI coding assistants and large
language models: **AWS Kiro**, **Claude** (Anthropic, multiple models), and
**Hermes Agent** (with **DeepSeek** and **Kimi** models). The human author
designed, reviewed, and validated the architecture and every release.

---

## 📱 What you get

- Live GPS streaming (lat/long/accuracy/speed/altitude/heading) to your own WebSocket server
- Self-hosted desktop server (Electron) + in-app map viewer
- Secure `wss://` transport (works with Tailscale Serve or any TLS WebSocket endpoint)
- Save & switch between favorite server URLs
- Scan a server URL / Lightning tip with the camera (open-source ZXing)
- Real-time console log viewer (last 50 lines)
- ⚡ Bitcoin (Lightning) tips — 3$, 5$, 7$ (built-in SvelteKit widget, Alby direct)

## 🔧 Install

1. Download the APK from **Assets** below.
2. Enable "Install from unknown sources" for the downloader if prompted.
3. Open the APK to install.
4. Enter your server address (`host:port`) and press **Start**.

> ℹ️ On Android, cleartext `ws://` is blocked in production — expose your server as `wss://`. **Tailscale Serve is one convenient option**, but you can use any server that exposes a TLS WebSocket (`wss://`) endpoint. See the README for the full setup.

## 🧪 Tested

- Playwright e2e (mobile UI) ✅
- Vitest / JUnit (unit + native service) ✅

## 📚 Docs

Full setup, architecture, and privacy notes: see the [README](../README.md).

## ⚖️ License

MIT — open source, self-hosted, privacy-first. Bitcoin tips accepted ⚡
