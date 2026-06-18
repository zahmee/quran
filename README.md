# قرآن القارئ — Quran Al-Qari

تطبيق أندرويد لقراءة المصحف الشريف، مبني على صور صفحات المصحف مع طبقة إحداثيات للآيات
تتيح اختيار الآية وتظليلها، إضافةً إلى فهرس للسور والأجزاء، وبحث في النص، وإحصائيات قراءة.

An Android Mushaf (Qur'an) reader built on page images with an ayah-coordinate
overlay for tap-to-select/highlight, plus a surah/juz index, full-text search,
bookmarks, and reading statistics.

---

## ✨ Features

- 📖 Page-image Mushaf with right-to-left, paper-like page turning (all 604 pages).
- 👆 Tap an ayah to select and highlight the whole verse across its lines.
- 🔎 Search the Qur'anic text and surah names (diacritic-insensitive); also jumps
  by verse key like `2:255`.
- 🗂️ Surah & Juz navigation index.
- 🔖 Bookmark with persistent highlight.
- 📊 Reading statistics: streak, daily/weekly progress, khatma %, history.
- 🌙 Light/dark themes (separate image sets) and a distraction-free full-screen mode.

## 🛠️ Tech stack

- Kotlin + Jetpack Compose (Material 3)
- Coil 3 (asset image loading)
- Room (reading-session stats) + DataStore (preferences/bookmarks)
- AGP 8.7.3 · Kotlin 2.0.21 · compileSdk 35 · minSdk 24

## 📁 Project layout

```
quran_01/
├─ mushaf_app/                      # the Android app (Gradle project)
│  └─ app/src/main/
│     ├─ java/com/mushaf/reader/    # Compose UI + ViewModel + data layer
│     └─ assets/                    # page images + ayah_regions.json
├─ build_ayah_regions.py            # offline: builds assets/data/ayah_regions.json
├─ quran_pages_complete_text_coordinates.{json,csv}   # source ayah data
└─ quran_pages_complete_table_schema.md               # data schema docs
```

## 🚀 Build & run

```bash
cd mushaf_app

# Debug APK (no signing key required)
./gradlew :app:assembleDebug

# Install on a connected device/emulator
./gradlew :app:installDebug
```

### Release signing

Release builds are signed using credentials in `mushaf_app/keystore.properties`,
which is **gitignored** and not part of this repository. To build a signed release:

1. Copy the template and fill in your own key details:
   ```bash
   cp mushaf_app/keystore.properties.sample mushaf_app/keystore.properties
   ```
2. Place your keystore (e.g. `release.jks`) next to it and set `storeFile` accordingly.
3. Build:
   ```bash
   ./gradlew :app:assembleRelease
   ```

Without `keystore.properties` the release task still runs but produces an **unsigned** APK.

## 🔧 Regenerating ayah data

`ayah_regions.json` (the per-ayah highlight regions) is generated offline:

```bash
python build_ayah_regions.py
```

## 📜 License

Source code is licensed under the [MIT License](LICENSE).

> ⚠️ The bundled Mushaf ("ممتاز") page images and the Qur'anic text/coordinate
> data are **not** covered by the MIT license and remain the property of their
> respective owners. Do not redistribute them unless you hold the necessary rights.
