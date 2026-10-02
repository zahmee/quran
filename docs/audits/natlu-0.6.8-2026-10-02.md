# Natlu 0.6.8 rebrand verification

Date: 2026-10-02 (Asia/Riyadh).

## Approved identity

The app label and Arabic store title are exactly:

**نتلو**

The icon is approved concept 2: the emerald noon/book mark on ivory. The selected original, transparent adaptive foreground, feature artwork, and generation prompts are preserved in `publishing/branding/`. Mechanical platform exports are reproducible with `mushaf_app/tools/export_natlu_branding.ps1`.

Updated app surfaces include the launcher, About identity, developer description, and feedback email subject. The website, privacy-page branding, store copy, artwork, and current project documentation use the new identity. Historical release records, copyright attribution, stable URLs, package identity, and storage identifiers are retained.

## Build and package verification

- Version: `0.6.8`, version code `49`.
- Package: `com.mushaf.reader`; minimum SDK 24, target SDK 36.
- Debug APK, release APK, and release AAB built successfully.
- All 106 debug unit tests passed; zero failures, errors, or skipped tests.
- Debug and release lint: zero errors, 26 warnings and 19 hints each. The previous 0.6.7 release report recorded the same warning/hint counts.
- APK signing verification passed with the same signing certificate as the previous release.
- AAB signing verification passed. The existing self-signed/no-timestamp and archive signature warnings remain; no store upload was performed in this task.
- Store icon: 512×512. Feature graphic: 1024×500. Both have opaque edges.
- Adaptive foreground: 432×432, transparent background, nonempty alpha bounds `(106, 97, 327, 338)` within the central safe region.
- `git diff --check` passed.

Validation command, from `mushaf_app/`:

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug :app:testDebugUnitTest :app:assembleRelease :app:bundleRelease :app:lintRelease --console=plain
```

Final artifact SHA-256:

| Artifact | SHA-256 |
| --- | --- |
| `publishing/Natlu-0.6.8.apk` | `3475a3ca65f124f7b4e51bb353ed6813b599c81e96b14494ccf77944ce838037` |
| `publishing/Natlu-0.6.8.aab` | `c75e8b8066db384a053e5b1f6a924ba8f0184406c45932511596bc779716cec8` |

## Upgrade and visual verification

A signed 0.6.7 installation on the Android 15 emulator was updated in place using the signed 0.6.8 APK. The app was not uninstalled and its data was not cleared.

Backups exported through the app before and after the update confirmed:

- The complete `reading` payload was identical, including page 7, preferences, visited/read pages, and start-of-khatma timestamp.
- All six existing session records were retained byte-for-byte at the JSON value level. Opening the new version added one normal reading session.
- The backup schema remained version 1.
- Bookmark and completed-khatma collections were empty in this emulator baseline and remained empty; this comparison does not establish a populated-record migration test for those collections.

The reader reopened at page 7. The app drawer showed the new icon and Arabic label; About showed the new icon, name, and version 0.6.8. The emulator crash log was empty after the smoke test. The About screenshot is saved at `publishing/branding/about-preview-0.6.8.png`.

The local website was checked at 1280×900 and 390×844. The title and main heading use the approved name; all six page images loaded, and neither viewport had horizontal overflow. The privacy page also showed the new name without horizontal overflow. The live website was not deployed.

## Store materials

Native publishing metadata is saved under `mushaf_app/app/src/main/play/listings/ar/`, alongside icon and feature-graphic exports. The reviewable copy is `publishing/store-listing-ar.md`.

- Title: 4 characters.
- Short description: 80 characters.
- Full description: 1203 characters including its final newline.
- Quran, Medina mushaf, interpretation, search, and khatma terms appear naturally in the descriptions. Search ranking has not been measured.
- Existing phone/tablet screenshots remain the previously documented 0.5.6 captures; this task does not relabel them as new captures.

These are locally prepared changes and signed artifacts. No Git commit, remote push, store upload, review submission, or public release was made by this task.

Detailed local logs, emulator captures, before/after backups, and machine-readable results are in the ignored `.codex/natlu-rebrand/` directory.
