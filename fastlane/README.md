# Fastlane / F-Droid screenshots

This `fastlane/` directory provides the metadata F-Droid uses for the app
listing. **It must live at the repo root** (F-Droid only reads
`fastlane/metadata/android/` at the repo root — not inside a gradle module).

## Structure
```
fastlane/
  Fastfile
  metadata/android/en-US/
    title.txt                App title shown in F-Droid (max 50 chars)
    short_description.txt    One-liner (max 80 chars, mandatory)
    full_description.txt     Full store description (max 4000 chars, mandatory)
    changelogs/1.txt         Changelog for versionCode 1 (max 500 chars)
    images/
      icon.png               App icon
      featureGraphic.png     Promo banner (landscape, shown on top)
      phoneScreenshots/      <-- ADD SCREENSHOTS HERE
        1.png, 2.png, ...
```

## Adding screenshots (required for F-Droid)
F-Droid strongly recommends screenshots. Add PNG screenshots here:

```
fastlane/metadata/android/en-US/images/phoneScreenshots/
  01-home.png
  02-scan-qr.png
  03-tipping.png
  ...
```

Screenshots should be:
- Real phone screenshots (or high-fidelity mockups at the target resolution)
- Portrait orientation (e.g. 1080x2340 for a modern phone)
- No text overlaid that isn't in the app

## Notes
- F-Droid's buildbot reads `fastlane/metadata` for the listing; it does NOT
  run this Fastfile.
- Keep the changelog filename matching the Android `versionCode` (e.g. `1.txt`
  for versionCode 1).
- When syncing to the F-Droid standalone repo, keep `fastlane/` at the root
  there too.
