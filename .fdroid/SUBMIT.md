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
2. Tag a release — F-Droid tracks tags (`v0.1.0` exists).
3. Open a **Request For Packaging** at
   https://gitlab.com/fdroid/rfp (or use the F-Droid submission form):
   - Repo: `https://github.com/ymongo/send-gps-data-mobile-app`
   - License: MIT
   - App name: Send GPS Data
   - Package name: `com.mongoutils.sendgpsdata`
4. Or package it yourself: the metadata merge request is
   https://gitlab.com/fdroid/fdroiddata/-/merge_requests/52838

## Build recipe (metadata)
The authoritative recipe is F-Droid's own metadata file,
`metadata/com.mongoutils.sendgpsdata.yml` in `fdroiddata` (MR !52838). There is
no recipe copy in this repo.

Key points for the F-Droid maintainer:
- Capacitor app → the **web bundle must be built before gradle**, and `npm ci`
  must run at the **repo root** (npm workspace: `apps/mobile` depends on the
  shared `gps-shared` package):
  ```bash
  cd ../../../..
  npm ci --ignore-scripts
  cd apps/mobile
  NODE_OPTIONS=--max-old-space-size=4096 npm run prod
  npx cap sync android
  ```
- `subdir` = `apps/mobile/android/app` (the Gradle module dir, where `app/build/`
  is generated) and `gradle: - yes` lets fdroidserver run `assembleRelease`.
  The buildserver has no Node.js, hence
  `sudo: apt-get install -y npm nodejs` in the recipe.
- No proprietary SDKs: ZXing (Apache-2.0) for QR, standard Android Location
  API for GPS. F-Droid friendly.

## Notes / anti-features
- No tracking SDKs, no analytics. Remote log POST is **off by default**
  (`PROD_DEBUG_LOGS=true` enables it at build time only).
- Lightning tip uses Alby's public LNURL endpoint (no account required for the
  receiver); optional, disabled by default.
