# Source this from Git Bash:  . .claude/skills/run-app/emu.sh
# Then: shot NAME (screenshot to $SCR/NAME.png), tap TEXT (by text or content-desc),
# texts (every text on screen), type_ar WORD (Arabic by tapping Gboard keys).
export PATH="$PATH:/c/Users/az544/AppData/Local/Android/Sdk/platform-tools:/c/Users/az544/AppData/Local/Android/Sdk/emulator"
export MSYS_NO_PATHCONV=1
: "${SCR:?set SCR to a Windows-style scratch folder first, e.g. SCR=C:/Users/.../scratchpad}"

# FoldDevice has two displays (inner and cover); screencap needs one named or it
# writes a warning into the PNG. This id is the inner (unfolded) display.
shot() { adb exec-out screencap -p -d 4619827259835644672 > "$SCR/${1:-s}.png"; }

tap() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null && adb shell cat /sdcard/ui.xml > "$SCR/ui.xml"
  local b; b=$(grep -oE "(text|content-desc)=\"[^\"]*$1[^\"]*\"[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]\"" "$SCR/ui.xml" | head -1 | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]')
  [ -z "$b" ] && { echo "not found: $1"; return 1; }
  local n=($(echo "$b" | grep -oE '[0-9]+')); adb shell input tap $(( (n[0]+n[2])/2 )) $(( (n[1]+n[3])/2 )); echo "tapped $1"
}

texts() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null && adb shell cat /sdcard/ui.xml | grep -oE 'text="[^"]+"'; }

# Gboard's Arabic layout on FoldDevice UNFOLDED (2208x1840, split keyboard).
# No key for أ إ آ ئ — those are long-presses; use the clipboard route for them.
type_ar() {
  local LC_ALL=C.UTF-8 s="$1" i c x y
  for ((i = 0; i < ${#s}; i++)); do
    c="${s:i:1}"
    case "$c" in
      ض|ش|ذ) x=171;; ص|س|ء) x=290;; ث|ي|ؤ) x=411;; ق|ب|ر) x=531;; ف|ل|ى) x=651;; غ|ا|ة) x=771;;
      ع|ت|و) x=1557;; ه|ن|ز) x=1675;; خ|م|ظ) x=1796;; ح|ك|د) x=1915;; ج|ط) x=2036;;
      " ") adb shell input tap 1170 1634; continue;;
      *) echo "no key for '$c' (hamza forms: use the clipboard route)"; return 1;;
    esac
    case "$c" in
      ض|ص|ث|ق|ف|غ|ع|ه|خ|ح|ج) y=1192;; ش|س|ي|ب|ل|ا|ت|ن|م|ك|ط) y=1345;; *) y=1490;;
    esac
    adb shell input tap $x $y
  done
}
