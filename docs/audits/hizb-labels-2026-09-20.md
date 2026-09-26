# Printed hizb label anchors

The temporary reading-position label now anchors to the printed red hizb star.
It uses that star's starting ayah metadata, including division changes inside a
page (not the equal-page reading plan used by the progress header).

## Coordinates

- `mushaf_app/tools/build_hizb_markers.py` generates `assets/data/hizb_markers.json`
  from the shipped ayah metadata and unmodified page images.
- The reference is the image-inspected 24 x 24 pixel star at (1058, 76) on page 17.
  Red-mask template matching searches only near actual division-start ayahs.
- There are 199 matched stars. The other 41 division starts coincide with surah
  openings without a separate matched star; these retain the page-level fallback.
- The minimum accepted match score is 0.70; the current minimum is 0.744.
- Unit tests validate all 240 starts, the division attached to every anchor,
  coordinate bounds, and SHA-256 hashes of source metadata and anchored images.
- Regenerate with `python mushaf_app/tools/build_hizb_markers.py`; add `--check`
  for read-only reproducibility validation. Requires numpy and Pillow offline,
  with no new runtime dependency in the app.

## Display behavior

The existing setting remains opt-in. A label lasts three seconds and does not
intercept gestures. It follows whole-page fit, portrait stretch, and wide-screen
vertical scrolling. A marker below the viewport starts its timer upon entry.
Labels move horizontally to remain inside the image, and above a star if there
is insufficient space below it (notably page 371). Pages without a matched star
keep the existing bottom label. Reader visibility and store-update suppression
continue to apply.

## Verification

- Final debug build, all 106 JVM tests, and lint passed. Lint reports zero
  errors, 26 warnings and 19 hints; none target the new anchor/label files or
  the modified page renderer/repository.
- Emulator checks covered pages 7, 17, 121, 201 and 371, portrait whole-page
  fitting, portrait stretch, light/night themes, and landscape width-fill
  scrolling. The label appeared after an initially offscreen star entered
  the viewport, and disappeared after its three-second interval.
- The page 17 label reads half of hizb 2 / juz 1; page 121 reads hizb 13 / juz 7;
  page 201 reads hizb 21 / juz 11. Page 371 uses the above-star fallback.
- Emulator verification caught and fixed an empty-measurement case during
  the visibility transition. The final tested preview process produced no
  Android runtime crash log.
- Initial testing used a separate preview application ID on the local test
  emulator, preserving the existing installed app. No release build or store
  upload was performed during that implementation phase. Screenshots are in
  the local `.codex/hizb-display` directory.

## Authorized release follow-up

The user subsequently requested a Google Play update. Version 0.6.6 (code 47)
was built as a signed release, with successful assembleRelease, bundleRelease,
testDebugUnitTest and lintRelease tasks. The 106 test results have zero failures
or errors; release lint has zero errors, 26 warnings and 19 hints.

The release APK was installed over the existing emulator app with `adb install
-r`, preserving its data. The release version opened successfully, displayed
the temporary label below the page 7 star, and removed it after the interval.
The release process had no AndroidRuntime error output. See the separate
0.6.6 release record for artifact and Google Play submission evidence.
