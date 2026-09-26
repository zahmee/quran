# Quran Al-Qari 0.6.6 release

## Scope and authorization

On 2026-09-20 the user explicitly requested uploading the update to Google Play.
The production application is `com.mushaf.reader`, not the temporary
`com.mushaf.reader.hizbpreview` package used during development.

This release contains the printed-hizb-star label improvement documented in
`hizb-labels-2026-09-20.md`. Version name is 0.6.6 and version code is 47.
The changelog and Arabic release notes were updated; Play notes are 386
characters. Store listing text and screenshots were not uploaded or replaced.

## Build and verification

Command, from `mushaf_app`:

```powershell
.\gradlew.bat :app:assembleRelease :app:bundleRelease :app:testDebugUnitTest :app:lintRelease --console=plain
```

- Exit code 0: BUILD SUCCESSFUL in 54 seconds.
- 106 JVM test results, zero failures and zero errors (test task reused its
  valid up-to-date results from the already-tested feature implementation).
- Release lint: zero errors, 26 warnings, 19 hints.
- `git diff --check` passed.
- APK signature verified with apksigner; certificate matches release 0.6.5.
- APK metadata: package `com.mushaf.reader`, code 47, name 0.6.6, min SDK 24,
  target/compile SDK 36. No INTERNET or advertising-ID permission.
- AAB signature verified by jarsigner and signing certificate checked with
  keytool. Jarsigner emits self-signed/no-timestamp and ZIP entry-order warnings
  also reproduced on the previously published 0.6.5 AAB; these did not prevent
  Google Play from accepting and validating the new bundle.
- Signed release installed as an update on QuranTest054, with no uninstall or
  data clearing. Opened and verified the page 7 label in the minified release;
  it disappeared after its interval and no AndroidRuntime error was logged.

## Artifacts

- AAB: `publishing/QuranAlQari-0.6.6.aab`, 70,196,331 bytes.
- AAB SHA-256:
  `f25f4ce12ef90fff3ee8745f46e090f22fca2b4e2877a64f1ccd1230273fb9d1`
- APK: `publishing/QuranAlQari-0.6.6.apk`, 67,254,048 bytes.
- APK SHA-256:
  `66a4fdcdc93fe25cc886546d711bc5b53edcc98ed4b8fcd3fc07823c364f1c63`
- Signing certificate SHA-256:
  `9080782c6b3ba0c57988569df35726561eef65bca4b637c402458bdf4a462ea7`
- A versioned APK also exists at the repository root via the existing Gradle
  distribution task. Artifacts and all signing credentials remain gitignored.

## Google Play transaction

- Live preflight showed production 0.6.5 / code 46 as PUBLISHED.
- Uploaded current AAB; Google returned code 47 and the matching SHA-256 above.
- Production release configured as 0.6.6 with Arabic notes and full rollout.
- Country targeting unchanged: 177 countries, restOfWorld true.
- All non-production tracks were compared before/after staging and unchanged.
- Edit `01441971573786502664` validated and committed successfully.
- Commit used `changesNotSentForReview=false` and
  `changesInReviewBehavior=ERROR_IF_IN_REVIEW`, preventing replacement of any
  concurrent existing review. The commit was sent exactly once.
- A transient release-list quota limit affected a read-only preflight before
  commit, not the upload. A later lifecycle query remained quota-limited, so
  the final publishing state was verified directly in the signed-in Console.
- No source commit, push, tag, or GitHub release was performed.

## Verified final Console state

Google Play Console, Publishing overview, verified on 2026-09-20:

- "Changes in review" contains one production change: "0.6.6 — Start full
  rollout".
- "Running quick checks for commonly found issues" was at 21%, with up to
  11 minutes shown. Console states changes will be sent for review as soon as
  checks complete successfully. No additional submission button/action needed.
- "Managed publishing off"; approval is configured to trigger the full rollout.
- "Last published on September 9, 2026"; 0.6.6 is NOT yet publicly available.
- The initial app-list summary briefly said "Not yet sent for review"; the
  detailed publishing page is the more specific, current result above.

Final page (left open as the user-facing deliverable):

https://play.google.com/console/u/1/developers/8104646540488178010/app/4974626125183356915/publishing
