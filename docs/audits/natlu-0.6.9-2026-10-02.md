# Natlu 0.6.9 release verification

Date: 2026-10-02 (Asia/Riyadh).

## Release identity

- App and Arabic store title: **نتلو**.
- Version: `0.6.9`; version code: `50`.
- Package: `com.mushaf.reader`; minimum SDK 24, target SDK 36.
- Approved icon: concept 2, the emerald noon/book mark on ivory.
- Application ID, signing identity, storage names, database schemas and backup format are unchanged by the rebrand.

## Current build and signing

The current checkout passed:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:bundleRelease :app:lintRelease --console=plain
```

- 106 unit tests; zero failures, errors or skipped tests.
- Debug and release lint: zero errors, 26 warnings and 19 hints each, matching the previous release's counts.
- APK signature verification passed, using the existing signing certificate.
- AAB signature verification passed; existing self-signed/no-timestamp warnings remain.
- Artifact metadata confirms the package, name, version, version code and SDK levels above.
- The signed APK runs on the Android 15 emulator, and the crash log was empty after the smoke checks. No uninstall or data clearing was performed.
- The earlier in-place upgrade and reading-data comparison are recorded in [the 0.6.8 verification](natlu-0.6.8-2026-10-02.md).

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `Natlu-0.6.9.apk` | 66904516 | `18e72b5115595464826e1dffb3018088c2fcb72776acb2ed86c68288e7564040` |
| `Natlu-0.6.9.aab` | 69848945 | `549c696159d3642b04f05c9ebb4b347d888ef969ee65736863f02c84c0479de0` |

## Store assets and website

- Eight current phone screenshots at 1080×1920 and eight current tablet screenshots at 1440×2560 were captured from version 0.6.9.
- The tablet captures are supplied to both the 7-inch and 10-inch listing slots.
- Screenshots are RGB PNGs without transparency. The reader, index, ayah actions, tafsir, About, statistics, khatma map and backup screens were inspected.
- The store icon is 512×512; the feature graphic is 1024×500. Both use the approved identity.
- The icon and feature graphic were declared as created or edited using AI in Play Console. The screenshots are actual application captures.
- Arabic store copy includes Quran, Medina mushaf, interpretation, search and khatma terms naturally. Search ranking has not been measured.
- The website and README use current screenshots, the new brand, and the signed release download link.
- Local website checks at 1280×900 and 390×844 found no horizontal overflow; all six referenced images loaded. Page title and main heading are exactly **نتلو**.

## Google Play submission

Nine changes were submitted together through the authorized owner Console: the production release, title, short description, full description, icon, feature graphic, and three screenshot groups.

At the submission verification on 2026-10-02, the Console showed **Changes in review**. Quick pre-review checks were still running. The Play Developer API confirmed:

| Release | Version code | Lifecycle |
| --- | ---: | --- |
| `0.6.9` | 50 | `RELEASE_LIFECYCLE_STATE_IN_REVIEW` |
| `0.6.7` | 48 | `RELEASE_LIFECYCLE_STATE_PUBLISHED` |

This records a review submission, not public availability of 0.6.9. Managed publishing is off and the release is configured for a 100% rollout after approval.

Post-save readback confirmed the title, all 26 image slots and their file order and SHA-256 hashes. Country targeting was unchanged (177 API country entries); non-production tracks were unchanged. Console support comparison showed no devices losing support.

The only bundle validation warning was the existing absence of native debug symbols. It did not block submission. The automated publisher's edit validation returned a permission error; publication was completed through the authorized owner browser session without changing account permissions.

## Distribution links

- [Application on Google Play](https://play.google.com/store/apps/details?id=com.mushaf.reader)
- [Natlu website](https://zahmee.github.io/quran/)
- [Version 0.6.9 release](https://github.com/zahmee/quran/releases/tag/v0.6.9)
- [Signed APK](https://github.com/zahmee/quran/releases/download/v0.6.9/Natlu-0.6.9.apk)

Signed binaries are distributed as release attachments rather than Git source files. Signing keys, service-account credentials, local publisher state, and Console screenshots remain excluded from Git.
