---
name: run-app
description: Launch قرآن القارئ on the Android emulator and drive it — install, navigate, and above all type ARABIC into the app (the search box). Use when asked to run, start, screenshot, or manually verify the app, or to confirm a change works in the real app rather than in tests.
---

# Running قرآن القارئ

Android app in `mushaf_app/`. Host is Windows; the shell here is Git Bash.

Everything below was established by actually doing it. The Arabic-input section
is the part worth reading first — it is where the whole afternoon went.

## Setup every command assumes

```bash
export PATH="$PATH:/c/Users/az544/AppData/Local/Android/Sdk/platform-tools:/c/Users/az544/AppData/Local/Android/Sdk/emulator"
export MSYS_NO_PATHCONV=1   # or Git Bash rewrites /sdcard/... into C:/Program Files/...
```

**Windows Python cannot read Git Bash `/tmp`.** It resolves `/tmp/x` to `C:\tmp\x`,
while Git Bash means `C:\Users\<user>\AppData\Local\Temp`. Pass Windows-style paths
to Python, or keep scratch files in the session scratchpad and pass the path as `argv`.

## 1. Build and install

```bash
cd mushaf_app && ./gradlew :app:assembleDebug --offline
```

```bash
emulator -avd Quran_API_36 -no-snapshot-load -no-boot-anim
```

Run that in the background, then block until it is really up — `wait-for-device`
returns long before the launcher exists:

```bash
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do :; done
```

Install. **If a release-signed build is already on the device the install fails**
with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`; uninstall first (throwaway emulator, no
real user data):

```bash
adb uninstall com.mushaf.reader
adb install -r mushaf_app/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.mushaf.reader/.MainActivity
```

## 2. Coordinates

Valid for **`Quran_API_36`, 1080x2400, API 36**. On any other device re-derive them
rather than guessing:

```bash
adb shell uiautomator dump /sdcard/ui.xml && adb shell cat /sdcard/ui.xml > "$SCRATCH/ui.xml"
# then grep content-desc="..." / text="..." with their bounds="[x1,y1][x2,y2]"
```

| Target | Tap |
|---|---|
| More menu ⋮ (top-left, RTL) | `42 42` |
| → البحث inside that menu | `298 212` |
| Search text field | `540 250` |
| Gboard clipboard icon (only when the field is EMPTY) | `538 1574` |
| Settings gear | `1038 42` |

**Search is in the More menu, not on the bar.** `ReadingStore.DEFAULT_BAR_BUTTONS`
is `{"fill", "hide"}`, so a fresh install shows only those two plus settings.

## 3. Typing Arabic — the part that matters

Three obvious routes are all dead ends on this image:

- `adb shell input text "عربي"` → `NullPointerException` from `KeyCharacterMap`. ASCII only.
- `adb shell cmd clipboard set-text` → `No shell command implementation`.
- `KEYCODE_PASTE` (279) and `input keycombination 113 50` (Ctrl+V) → silently do nothing.

**What works: the host clipboard + Gboard's clipboard chip.** The emulator syncs the
Windows clipboard into Android, and Gboard offers it as a chip above the keyboard
whenever the focused field is empty.

1. Set the host clipboard (PowerShell tool, not bash):
   ```powershell
   Set-Clipboard -Value "الملائكة"
   ```
2. Focus the field: `adb shell input tap 540 250`
3. Empty it — the chip only appears for an empty field:
   ```bash
   adb shell input keyevent 123                                  # move to end
   for i in $(seq 1 20); do adb shell input keyevent 67; done    # backspace
   ```
4. `adb shell input tap 538 1574` — the clipboard chip. Text lands, search runs.
5. `adb shell input keyevent 4` — **exactly once**, to hide the keyboard.

### Always screenshot and read the field before believing a result

The clipboard bridge fails intermittently and silently: it once delivered
`????????` — eight question marks, the right LENGTH for المقربين but ASCII-mangled —
and the app honestly reported «لا توجد نتائج». Nothing was wrong with the app.
Confirm the host side is intact and retry:

```powershell
$c = Get-Clipboard; ($c.ToCharArray() | ForEach-Object { [int]$_ }) -join ","
# Arabic must be in the 1568-1610 range; ASCII means the bridge mangled it
```

The emulator's own log confirms it from the other side — it prints
`Warning: Retrying to obtain clipboard` when the transfer struggles.

Never report a `????????` screenshot as an app failure.

## 4. Two traps that cost real time

**Gboard's "Try out your stylus" sheet** hijacks the field and swallows every paste,
while showing your clipboard text in its own demo box — which looks like success.
Reset it once per fresh emulator:

```bash
adb shell settings put secure stylus_handwriting_enabled 0
adb shell pm clear com.google.android.inputmethod.latin
```

**BACK is not idempotent.** One press hides the keyboard; a **second press closes the
search screen** and drops you back on the mushaf page. If a screenshot suddenly shows
سورة الفاتحة, that is what happened.

## 5. Verifying search specifically

`الملائكة` and `أولئك` are the regression canaries — both returned zero before the
two-orthography index landed. `المقربين` is the one that exercises the stemmed tier:
exact «ين» hits on top, then the divider **نتائج قريبة**, then the «ون» forms
(النساء ١٧٢، الواقعة ١١، المطففين ٢١ و٢٨).

Look at the screenshot. A page of results is not proof the right ones came back.
