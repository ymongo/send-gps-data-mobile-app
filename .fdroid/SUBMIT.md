# Submitting to F-Droid

This repo (`send-gps-data-mobile-app`) is the **F-Droid-ready mirror** of the
mobile app. F-Droid builds it on their infrastructure, so the synced trees must
stay **self-contained and reproducible** (they are — verified: `npm ci` →
`vite build` → `cap sync` → `gradle assembleRelease` produces a working APK).

The monorepo [`ymongo/send-gps-data`](https://github.com/ymongo/send-gps-data) is
the single source of truth. The synced trees here are **generated**: do not edit
them by hand, they are replaced on every release. Only the files this repo owns
(`README.md`, `F-DROID.md`, `.fdroid/`, `.github/`) are never touched by the sync.

## Package
- **appId:** `com.mongoutils.sendgpsdata`
- **minSdk / targetSdk / compileSdk:** 26 / 36 / 36
- **License:** MIT
- **Source:** this repo

## Release flow
1. In the monorepo: bump `versionCode` / `versionName`, then preview with
   `npm run fdroid:release -- --dry-run`.
2. `npm run fdroid:release` copies the trees here, commits and tags **locally**,
   and prints the push commands for the human.
3. Push the tag in the monorepo. `.github/workflows/fdroid-sync.yml` then syncs
   this repo and pushes the same tag with the `MIRROR_DEPLOY_KEY` deploy key.
   Mirror history is **append-only** — never force-push it.
4. Once the tag exists here, generate the fdroiddata metadata and open the merge
   request from the monorepo: `bash scripts/fdroiddata-mr.sh --dry-run`, then the
   same command without the flag. It resolves the full commit hash of the pushed
   tag and creates the MR against `fdroid/fdroiddata`.

## Metadata
The authoritative recipe is F-Droid's own metadata file,
`metadata/com.mongoutils.sendgpsdata.yml` in `fdroiddata` — there is no recipe
copy in this repo. The packaging request is tracked by RFP
[fdroid/rfp#4302](https://gitlab.com/fdroid/rfp/-/work_items/4302) and the merge
request
[fdroid/fdroiddata!52838](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/52838).

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
