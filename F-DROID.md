# Submitting to F-Droid

This standalone repo (`send-gps-data-mobile-app`) is the F-Droid-ready source.
F-Droid builds it on their infra, so the repo must be **self-contained and
reproducible** (it is — verified: `npm ci` → `vite build` → `cap sync` →
`gradle assembleRelease` produces a working APK).

## Package
- **appId:** `com.mongoutils.sendgpsdata`
- **minSdk / targetSdk / compileSdk:** 26 / 36 / 36
- **License:** MIT
- **Source:** this repo

## Submit
1. Push this repo to GitHub: `github.com/ymongo/send-gps-data-mobile-app`.
2. Tag a release (e.g. `v1.0`) — F-Droid tracks Tags.
3. Open a **Request For Packaging** at
   https://gitlab.com/fdroid/rfp (or use the F-Droid submission form):
   - Repo: `https://github.com/ymongo/send-gps-data-mobile-app`
   - License: MIT
   - App name: Send GPS Data
   - Package name: `com.mongoutils.sendgpsdata`
4. Point them to `.fdroid.yml` in this repo (the metadata/recipe reference).

## Build recipe (metadata)
The full recipe is in [`.fdroid.yml`](./.fdroid.yml). It goes in the
fdroidserver `metadata/ymongo.sendgpsdata.yml` on approval.

Key points for the F-Droid maintainer:
- Capacitor app → the **web bundle must be built before gradle**:
  ```bash
  cd apps/mobile
  npm ci
  NODE_OPTIONS="--max-old-space-size=4096" npm run prod   # vite build → build/
  npx cap sync android                                     # copies web into android/
  cd android && ./gradlew assembleRelease
  ```
- `gradleProject` = `apps/mobile/android`, `gradleCommand` = `assembleRelease`.
- No proprietary SDKs: ZXing (Apache-2.0) for QR, standard Android Location
  API for GPS. No Google Play Services. F-Droid friendly.

## Notes / anti-features
- No tracking SDKs, no analytics. Remote log POST is **off by default**
  (`PROD_DEBUG_LOGS=true` enables it at build time only).
- Lightning tip uses Alby's public LNURL endpoint (no account required for the
  receiver); optional, disabled by default.
