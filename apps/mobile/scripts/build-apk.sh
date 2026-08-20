#!/usr/bin/env bash
# Build the production APK: web bundle + e2e + cap sync + gradle assembleRelease.
# Source of truth for release builds (replaces the old Makefile target).
# Usage: npm run build:apk  (or: bash scripts/build-apk.sh)
set -euo pipefail

cd "$(dirname "$0")/.."

# 1. Build web bundle + run e2e tests
echo "▶ Building web bundle + e2e tests..."
NODE_ENV=production NODE_OPTIONS="--max-old-space-size=4096" npm run prod && npm run test:e2e

# 2. Sync Capacitor
echo "▶ Syncing Capacitor..."
npx cap sync

# 3. Build release APK
echo "▶ Building release APK (Gradle)..."
cd android
export JAVA_HOME="${JAVA_HOME:-C:/Program Files/Android/Android Studio/jbr}"
export PROD_DEBUG_LOGS=true
./gradlew assembleRelease
cd ..

# 4. Timestamp the APK (keep at most 10)
APK_DIR="android/app/build/outputs/apk/release"
BASE="$APK_DIR/app-release.apk"
if [ -f "$BASE" ]; then
  TS=$(date +%Y-%m-%d_%H-%M-%S)
  TS_APK="$APK_DIR/app-release-$TS.apk"
  cp "$BASE" "$TS_APK"
  echo "✅ Timestamped APK: $TS_APK"
  # prune old timestamped APKs
  COUNT=$(ls -1 "$APK_DIR"/app-release-*.apk 2>/dev/null | wc -l)
  if [ "$COUNT" -gt 10 ]; then
    TO_DELETE=$((COUNT - 10))
    ls -1t "$APK_DIR"/app-release-*.apk | tail -n "$TO_DELETE" | while read -r old; do
      echo "   deleting $old"; rm -f "$old"
    done
  fi
fi
echo "✅ Build complete: $APK_DIR"
