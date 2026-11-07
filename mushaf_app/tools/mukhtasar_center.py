"""Turn the Tafsir Center's Arabic Mukhtasar (CC BY 4.0, https://tafsir.net) into rows for the app.

One row per ayah, and the words are the Center's. The only changes are the ones listed in CORRECTIONS,
which repair the _word_ marks the source puts around the words it explains: a few are unbalanced or
paired across the wrong words, and would otherwise show as stray underscores or highlight half a
sentence. `verify` stops the build on any mark it cannot pair.
"""

from __future__ import annotations

import re
import unicodedata

# A word or phrase being explained: _word_. The app draws these as highlights.
GLOSS = re.compile(r"_([^_\n]+)_")
SOURCE_ID = "mukhtasar"
KIND = "تفسير آية"
EXPECTED_ROWS = 6236
LONGEST_GLOSS_WORDS = 26  # 6:128 is the longest real one, at 25

# (ayah, wrong text, right text). Each must occur exactly once, or the build stops.
CORRECTIONS: list[tuple[str, str, str]] = [
    ("2:75", "_جماعة_ _من علمائهم يسمعون", "_جماعة من علمائهم_ يسمعون"),
    ("2:75", "بعد _فهمهم لها", "بعد فهمهم لها"),
    ("4:127", "اليتامى بالعدل_ بما", "اليتامى _بالعدل_ بما"),
    ("4:127", "وسيجازيكم به_.", "وسيجازيكم به."),
    ("6:52", "وما _عليهم من", "وما عليهم من"),
    ("6:52", "من _المتجاوزين لحدود الله.", "من _المتجاوزين لحدود الله_."),
    ("5:45", "بمثل جنايته_، ومن تطوع", "بمثل جنايته، ومن تطوع"),
    ("5:45", "يحكم بما_ _أنزل الله", "يحكم بما أنزل الله"),
    ("5:45", "فهو _متجاوز لحدود الله.", "فهو _متجاوز لحدود الله_."),
    ("26:64", "الطريق سالك_", "الطريق سالك."),
    ("57:20", "أعجب _الزُّرَّاع نباته", "أعجب الزُّرَّاع نباته"),
    ("9:46", "المرضى  والضعفاء", "المرضى والضعفاء"),
]


def apply_corrections(rows: dict[str, str]) -> dict[str, str]:
    fixed = {key: unicodedata.normalize("NFC", text) for key, text in rows.items()}
    for key, wrong, right in CORRECTIONS:
        wrong_nfc = unicodedata.normalize("NFC", wrong)
        count = fixed[key].count(wrong_nfc)
        if count != 1:
            raise ValueError(f"Correction for {key} matched {count} times, expected once: {wrong!r}")
        fixed[key] = fixed[key].replace(wrong_nfc, unicodedata.normalize("NFC", right))
    return fixed


def verify(rows: dict[str, str], approved_keys: set[str]) -> None:
    """Fail the build unless every ayah has text and every mark pairs up around a few words."""
    if set(rows) != approved_keys or len(rows) != EXPECTED_ROWS:
        raise ValueError("The Mukhtasar's rows do not cover exactly the Quran's ayahs")
    for key, text in rows.items():
        if not text.strip():
            raise ValueError(f"{key}: empty")
        if "_" in GLOSS.sub("", text):
            raise ValueError(f"{key}: a mark without a partner: {text[:120]}")
        for match in GLOSS.finditer(text):
            inner = match.group(1)
            if inner != inner.strip() or len(inner.split()) > LONGEST_GLOSS_WORDS:
                raise ValueError(f"{key}: a mark paired across the wrong words: {inner[:80]}")
