"""Turn the Tafsir Center's page fawaed and surah introductions (CC BY 4.0, https://tafsir.net) into
rows for the app.

The fawaed are the lessons the Mukhtasar draws from each page of the Madina mushaf, two to six a page,
filed by page number (1-604, the numbering the app shows). Each is one line opening with a bullet,
which is dropped; nothing else is changed.

The surah introductions are long, footnoted texts on each surah's names, its revelation, its merits
and the count of its ayahs. They are cleaned like the downloadable packs (pack_common.py): markup out,
only the notes that correct the text or name a hadith's source kept as anchored notes. The heading the
source opens a section with («فضائل سورة البقرة») is dropped, since the app titles each section itself;
a line wrapped in asterisks («*أسماؤها التوقيفية*») is a sub-heading and is kept as it is.
"""

from __future__ import annotations

import hashlib
import re
import sqlite3
import unicodedata
from contextlib import closing
from dataclasses import dataclass
from pathlib import Path

import pack_common as pc

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "tafsircenter_full_2026-11-12" / "quran.db"

PAGES = 604
SURAHS = 114

# What each section is called in the app, and the source column it comes from, in reading order.
# surahAyahCount1 is left out: a second, more technical count of the same ayahs.
SECTIONS: list[tuple[str, str]] = [
    ("surahNameInfo", "أسماء السورة"),
    ("surahNujoolInfo", "نزول السورة"),
    ("surahFadael", "فضائل السورة"),
    ("surahAyahCount0", "عدد آياتها"),
]

# The source's own heading for a section, on its first line: the app titles the section instead.
_OWN_HEADING = re.compile(
    r"^(?:سورة\s+\S+(?:\s+\S+)?|(?:في\s+)?نزول\s+السورة|فضائل?\s+سورة\s+.{1,30})$"
)
SUBHEADING = re.compile(r"^\*([^*\n]+)\*$")

# Keyed (surah, section position), as clean_rows reads them: "16:2" is the revelation of al-Nahl.
# Each must match exactly once. Notes the source opened and never closed would otherwise run to the end
# of their line and take the main text after them along, or leave the rest of the note in the text.
CORRECTIONS: list[pc.Correction] = [
    ("6:2", "المحرر الوجيز (56/ 6 - 57).\n", "المحرر الوجيز (56/ 6 - 57).¥\n", "حاشية لم تُغلق"),
    ("16:2", "برقم (12305).\nوالأثر أخرجه", "برقم (12305). والأثر أخرجه", "حاشية تمتد على ثلاثة أسطر"),
    ("16:2", "(1/ 139).\nوصححه الشيخ ناصر", "(1/ 139). وصححه الشيخ ناصر", "حاشية تمتد على ثلاثة أسطر"),
    ("16:2", "برقم (807).\nوالمشاشة هى \"رؤوس العظام اللينة التي يمكن مض", "برقم (807).¥",
     "حاشية لم تُغلق، وآخرها مقطوع في المصدر فحُذف"),
    ("22:2", "المحرر الوجيز (5/ 5)، والاق", "المحرر الوجيز (5/ 5).¥",
     "حاشية لم تُغلق، وآخرها مقطوع في المصدر فحُذف"),
    ("112:1", "(الكافرون) ¬الزمخشري في تفسيره ¬انظر:", "(الكافرون) ¬الزمخشري في تفسيره، انظر:",
     "علامة حاشية زائدة قسمت الحاشية الواحدة"),
    ("2:2", "\n\nالآيات المدعى مكيتها في سورة البقرة", "", "عنوان مقطوع في المصدر لم يأتِ بعده شيء"),
    # A note the print carried over to the next page («=» at its end), whose rest the source left in
    # the main text under a rule of underscores: the rest goes back into the note.
    ("8:2", "وتفسير ابن أبي حاتم، سورة=¥",
     "وتفسير ابن أبي حاتم، سورة الأنفال، (برقم 314) والأثر صحيح وجزء منه له شاهد عند مسلم في صحيحه، "
     "كتاب الحج، باب التلبية وصفتها ووقتها، (2/ 843) برقم (1185).¥",
     "بقية حاشية نقلتها الطبعة إلى الصفحة التالية"),
    ("8:2", "\n__________\n=الأنفال، (برقم 314) والأثر صحيح وجزء منه له شاهد عند مسلم في صحيحه، كتاب الحج، "
     "باب التلبية وصفتها ووقتها، (2/ 843) برقم (1185).", "",
     "بقية حاشية نقلتها الطبعة إلى الصفحة التالية"),
    ("13:2", "مفقود لايعرف له وجود=¥", "مفقود لايعرف له وجود إلى الآن.¥",
     "بقية حاشية نقلتها الطبعة إلى الصفحة التالية"),
    ("13:2", "\n__________\n=إلى الآن.\nوقوله: \"الرعد كالقمر\" أي أن سورة الرعد كسورة القمر من حيث مكية كل منهما. "
     "هذا وقد أورد المنظومة المذكورة أيضا المراكشي في كتابه الذيل والتكملة لكتابي الموصول والصلة في أثناء "
     "ترجمته لابن الحصار.\nانظر: السفر الثامن، القسم الأول (ص: 211).", "",
     "بقية حاشية نقلتها الطبعة إلى الصفحة التالية"),
]

# A note the print carried over to the next page ends in «=»; the source dropped the rest of it.
_CARRIED_OVER = re.compile(r"\s*=\s*$")

# Sections whose text stops short in the source, mid-sentence or mid-word. Each is closed with a line
# saying so, as the packs do with their cut rows.
TRUNCATED: set[pc.Key] = {(2, 2), (6, 2), (8, 2), (9, 2), (16, 2), (17, 2), (22, 2)}


def mark_truncated(text: str) -> str:
    """A text that ends mid-word loses the half word; one that ends at a full stop keeps everything."""
    end = text.rstrip()
    if end and end[-1].isalpha():
        return pc.mark_cut(end)
    return end + "\n" + pc.CUT_NOTICE
_SAJDAH = re.compile(r"الآية\s+(\d+)")


@dataclass
class SurahFacts:
    surah: int
    revelation_place: str  # «مكية» or «مدنية»
    revelation_order: int
    surah_class: str  # الطوال، المئين، المثاني، طوال المفصل، أوساط المفصل، قصار المفصل
    sajdah_ayahs: list[int]
    goal: str


def _connect(path: Path) -> sqlite3.Connection:
    uri = "file:" + path.as_posix().replace(" ", "%20") + "?mode=ro&immutable=1"
    db = sqlite3.connect(uri, uri=True)
    db.execute("PRAGMA query_only=ON")
    db.execute("PRAGMA trusted_schema=OFF")
    return db


def digest(rows: list[tuple]) -> str:
    return hashlib.sha256("\n".join(repr(r) for r in rows).encode("utf-8")).hexdigest()


def read_fawaed(path: Path, expected_sha256: str) -> list[tuple[int, str]]:
    with closing(_connect(path)) as db:
        rows = db.execute("SELECT page, content FROM mokhtasar_fawaed ORDER BY page, rowid").fetchall()
    found = digest(rows)
    if found != expected_sha256:
        raise ValueError(f"mokhtasar_fawaed rows changed: {found}")
    return rows


def read_surahs(path: Path, expected_sha256: str) -> tuple[list[tuple], list[tuple]]:
    columns = ", ".join(column for column, _ in SECTIONS)
    with closing(_connect(path)) as db:
        stats = db.execute(
            "SELECT surahNo, makkiMadani, revelationSeq, surahClass, sujud FROM surah_stats ORDER BY surahNo"
        ).fetchall()
        content = db.execute(
            f"SELECT surahNo, surahGoals, {columns} FROM surah_content ORDER BY surahNo"
        ).fetchall()
    found = digest(stats + content)
    if found != expected_sha256:
        raise ValueError(f"surah_stats/surah_content rows changed: {found}")
    return stats, content


def clean_fawaed(rows: list[tuple[int, str]]) -> dict[int, list[str]]:
    """page -> its fawaed, in the source's order."""
    out: dict[int, list[str]] = {}
    for page, raw in rows:
        text = unicodedata.normalize("NFC", raw).strip()
        if not text.startswith("•"):
            raise ValueError(f"page {page}: a faidah that does not open with a bullet: {text[:60]}")
        text = pc.tidy(text.removeprefix("•"))
        if "\n" in text or not text:
            raise ValueError(f"page {page}: a faidah that is empty or spans lines: {text[:60]}")
        pc.check_text(f"fawaed page {page}", text, [])
        out.setdefault(page, []).append(text)
    if sorted(out) != list(range(1, PAGES + 1)):
        raise ValueError(f"fawaed cover {len(out)} pages, not 1..{PAGES}")
    return out


def parse_sajdah(raw: str) -> list[int]:
    """«لا» -> []; «نعم، الآية 18، والآية 77» -> [18, 77]."""
    raw = raw.strip()
    if raw == "لا":
        return []
    ayahs = [int(n) for n in _SAJDAH.findall(raw)]
    if not raw.startswith("نعم") or not ayahs:
        raise ValueError(f"unexpected sajdah value {raw!r}")
    return ayahs


def surah_facts(stats: list[tuple], content: list[tuple]) -> list[SurahFacts]:
    goals = {row[0]: row[1] for row in content}
    facts = []
    for surah, place, order, surah_class, sujud in stats:
        if place not in ("مكية", "مدنية"):
            raise ValueError(f"surah {surah}: revelation place {place!r}")
        goal = pc.tidy(unicodedata.normalize("NFC", goals[surah] or ""))
        if not goal or "\n" in goal:
            raise ValueError(f"surah {surah}: goal {goal!r}")
        facts.append(SurahFacts(surah, place, int(order), surah_class.strip(), parse_sajdah(sujud), goal))
    if [f.surah for f in facts] != list(range(1, SURAHS + 1)):
        raise ValueError("surah_stats does not hold surahs 1..114 once each")
    if sorted(f.revelation_order for f in facts) != list(range(1, SURAHS + 1)):
        raise ValueError("revelation order is not a permutation of 1..114")
    return facts


def drop_own_heading(text: str) -> str:
    first, _, rest = text.partition("\n")
    return rest if _OWN_HEADING.match(first.strip()) else text


def clean_sections(content: list[tuple], expected_unclosed: int) -> dict[pc.Key, tuple[str, list[str]]]:
    """(surah, position) -> (text with ⟦n⟧ anchors, notes); position counts from 1 in SECTIONS order."""
    raw: dict[pc.Key, str] = {}
    for row in content:
        surah = row[0]
        for position, text in enumerate(row[2:], start=1):
            raw[(surah, position)] = text or ""
    raw = pc.apply_corrections(raw, CORRECTIONS)
    for key in TRUNCATED:
        raw[key] = mark_truncated(raw[key])
    cleaned, _ = pc.clean_rows(raw, [], set(), expected_unclosed=expected_unclosed)
    out = {}
    for key, (text, notes) in sorted(cleaned.items()):
        notes = [_CARRIED_OVER.sub(" …", note) if _CARRIED_OVER.search(note) else note for note in notes]
        text = drop_own_heading(text)
        # A heading the source left with nothing under it: the next line is a heading too, or none.
        lines = text.split("\n")
        lines = [
            line for i, line in enumerate(lines)
            if not (SUBHEADING.match(line) and (i + 1 == len(lines) or SUBHEADING.match(lines[i + 1])))
        ]
        text = "\n".join(lines)
        pc.check_text(f"surah {key[0]} section {key[1]}", text, notes)
        out[key] = (text, notes)
    return out
