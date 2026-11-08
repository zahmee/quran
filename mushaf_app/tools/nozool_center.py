"""Turn the Tafsir Center's asbab al-nuzul rows (CC BY 4.0, https://tafsir.net) into rows for the app.

The dataset files 201 ayahs with the occasions of their revelation, each with the hadith and its
grading in editor's notes. They are cleaned like the downloadable packs (pack_common.py): markup out,
the notes that name a hadith's source or correct the text kept as anchored notes, the rest dropped.

Two rows of the source are filed under the wrong ayah, and both are copies of a row that is also
filed correctly: 38:1 holds the whole of 36:78's text (the bones Ubayy ibn Khalaf crumbled), and 3:181
opens with the whole of 3:173's entry before its own. The copies are dropped, and `build` checks
each is still an exact copy before dropping it.
"""

from __future__ import annotations

import re
from pathlib import Path

import pack_common as pc

ROOT = Path(__file__).resolve().parents[2]
SLIM = ROOT / "tafsircenter_nozool_2026-11-08" / "nozool_slim.db"
ROWS_SHA256 = "9d23c9d8daf3500fef856d990749d5cd8dd91bcf0fe335a3f7d3ad09d3efd1c8"
SOURCE_ROWS = 201
EXPECTED_ROWS = 200

# A row that is a copy of another ayah's row: dropped.
COPIES: dict[pc.Key, pc.Key] = {(38, 1): (36, 78)}
# A row that starts with a copy of another ayah's entry: the copy, up to this marker, is dropped.
COPIED_PREFIXES: dict[pc.Key, tuple[pc.Key, str]] = {
    (3, 181): ((3, 173), "قال تعالى: {لَقَدْ سَمِعَ اللَّهُ"),
}


def build() -> dict[str, tuple[str, list[str]]]:
    """verse key -> (text with ⟦n⟧ anchors, notes)."""
    raw = pc.read_slim(SLIM, ROWS_SHA256, SOURCE_ROWS)
    cleaned, _ = pc.clean_rows(raw, [], set(), expected_unclosed=0)
    for key, original in COPIES.items():
        if cleaned[key] != cleaned[original]:
            raise ValueError(f"{key} is no longer a copy of {original}")
        del cleaned[key]
    for key, (original, marker) in COPIED_PREFIXES.items():
        text, notes = cleaned[key]
        at = text.find(marker)
        copy = text[:at].strip()
        if at <= 0 or copy not in cleaned[original][0]:
            raise ValueError(f"{key} no longer opens with a copy of {original}")
        kept = text[at:]
        # Notes keep their order; drop the ones whose anchors went with the copy and renumber.
        used = [int(n) for n in pc.ANCHOR.findall(kept)]
        renumber = {old: new for new, old in enumerate(used, start=1)}
        kept = pc.ANCHOR.sub(lambda m: f"{pc.ANCHOR_OPEN}{renumber[int(m.group(1))]}{pc.ANCHOR_CLOSE}", kept)
        cleaned[key] = (kept, [notes[old - 1] for old in used])
    if len(cleaned) != EXPECTED_ROWS:
        raise ValueError(f"{len(cleaned)} rows, expected {EXPECTED_ROWS}")
    out = {}
    for (sura, aya), (text, notes) in sorted(cleaned.items()):
        pc.check_text(f"nozool {sura}:{aya}", text, notes)
        if not re.search(r"\{[^{}]+\}", text):
            raise ValueError(f"nozool {sura}:{aya} quotes no ayah")
        out[f"{sura}:{aya}"] = (text, notes)
    return out
