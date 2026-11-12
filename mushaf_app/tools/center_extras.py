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
from collections import Counter as Counter_
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


# ---------------------------------------------------------------------------------------------
# Tajweed: a note for each ayah on how to recite it.
#
# The source keeps the printed book's line breaks inside sentences (a real paragraph is <br>), opens
# 338 notes with a stray comma or colon, and files a note on several ayahs under each of them. It also
# lost what the print set as images or numbers: list numbers and footnote marks come out as «()», and
# three citations lost their ayah numbers. All of that is undone mechanically below; the few places
# where something had to be put back are listed here, each matching exactly once.

TAJWEED_CORRECTIONS: list[pc.Correction] = [
    ("20:135", "فالضمتان هكذا (ممم) والفتحتان والكسرتان هكذا (ممم)",
     "فالضمتان هكذا (ـٌ) والفتحتان والكسرتان هكذا (ـً ـٍ)", "علامتا التنوين صورتان في الطبعة لم تُنقلا"),
    ("64:18", "المتبقية ممم من", "المتبقية من", "علامة عمل"),
    ("66:1", "للتنوين ممم في", "للتنوين في", "علامة عمل"),
    ("72:3", "للساكن بعدها\nممم {صَاحِبَةً", "للساكن بعدها\n{صَاحِبَةً", "علامة عمل"),
    ("2:40", "الثانية [آية: ] وهذا", "الثانية وهذا", "رقم آية سقط من المصدر"),
    ("19:1", "{عَيْنَ الْيَقِينِ} [التكاثر: ].", "{عَيْنَ الْيَقِينِ} [التكاثر].", "رقم آية سقط من المصدر"),
    ("26:176", "موضع سورة [ص ] والبدء", "موضع سورة ص والبدء", "رقم آية سقط من المصدر"),
]

# A note that is nothing but a working mark or a dash: the ayah has no note.
_NO_NOTE = {"", "-", "ممم"}
# The print's own footnotes, under a rule of underscores: references to the author's other books.
_PRINT_FOOTNOTES = re.compile(r"(?:<br>)?\s*_{5,}.*\Z", re.DOTALL)
# What became of a list number or a footnote mark: «()».
_EMPTY_PARENS = re.compile(r"\(\s*\)")
_MIXED_OPEN = re.compile(r"\(([^(){}\n]+)\}")
_MIXED_CLOSE = re.compile(r"\{([^(){}\n]+)\)")


@dataclass
class TajweedNote:
    surah: int
    from_ayah: int
    to_ayah: int
    text: str


def read_tajweed(path: Path, expected_sha256: str) -> list[tuple[int, int, str | None]]:
    with closing(_connect(path)) as db:
        rows = db.execute(
            "SELECT surahNo, ayahNo, tajweed FROM ayah_content_tajweed ORDER BY surahNo, ayahNo"
        ).fetchall()
    found = digest(rows)
    if found != expected_sha256:
        raise ValueError(f"ayah_content_tajweed rows changed: {found}")
    return rows


def clean_tajweed_text(raw: str) -> str:
    text = raw.replace("&quot;", '"').replace("\r", "")
    text = _PRINT_FOOTNOTES.sub("", text)
    # A line break of the print is a space; <br> is where a paragraph really ends.
    text = re.sub(r"\s*\n\s*", " ", text)
    text = re.sub(r"<br\s*/?>", "\n", text, flags=re.IGNORECASE)
    # «()» opening a line was a list number; inside a Quran quote, the ayah-end sign; else a lost mark.
    text = re.sub(r"(^|\n)\s*\(\s*\)\s*", r"\1• ", text)
    text = re.sub(r"(^|\n)\s*\)\s*", r"\1• ", text)  # «1) …» with its number lost
    text = re.sub(r"\s*\(\s*\)\s*(?=[^{}]*\})", " ", text)
    text = _EMPTY_PARENS.sub("", text)
    # A Quran quote opened with one kind of bracket and closed with the other: «(وَمَوْعِظَةُ}».
    text = _MIXED_OPEN.sub(r"{\1}", text)
    text = _MIXED_CLOSE.sub(r"{\1}", text)
    text = pc.tidy(text)
    return text.lstrip("،:؛ ").strip()


def clean_tajweed(
    rows: list[tuple[int, int, str | None]],
    corrections: list[pc.Correction] | None = None,
) -> list[TajweedNote]:
    """The notes in Quran order, an ayah run sharing one note given as one range; ayahs with no
    note are left out."""
    raw = {(s, a): t for s, a, t in rows if t is not None}
    if len(rows) != len(pc.ALL_AYAHS) or [(s, a) for s, a, _ in rows] != pc.ALL_AYAHS:
        raise ValueError("ayah_content_tajweed does not hold the 6,236 ayahs once each")
    fixed = pc.apply_corrections(raw, TAJWEED_CORRECTIONS if corrections is None else corrections)
    notes: list[TajweedNote] = []
    for (surah, ayah) in pc.ALL_AYAHS:
        source = fixed.get((surah, ayah), "").strip()
        if source in _NO_NOTE:
            continue
        text = clean_tajweed_text(source)
        pc.check_text(f"tajweed {surah}:{ayah}", text, [])
        last = notes[-1] if notes else None
        if last and last.surah == surah and last.to_ayah == ayah - 1 and last.text == text:
            last.to_ayah = ayah
        else:
            notes.append(TajweedNote(surah, ayah, ayah, text))
    return notes


def unbalanced(text: str) -> bool:
    return text.count("{") != text.count("}") or text.count("(") != text.count(")")


def table_digest(db: sqlite3.Connection, table: str) -> str:
    """A table's rows in a stable order, hashed: to show a rebuild left it as it was."""
    columns = [row[1] for row in db.execute(f"PRAGMA table_info(`{table}`)")]
    order = ", ".join(f"`{c}`" for c in columns)
    return digest(db.execute(f"SELECT {order} FROM `{table}` ORDER BY {order}").fetchall())


# ---------------------------------------------------------------------------------------------
# Surah statistics: the counts and curiosities of surah_stats that read well on a surah's card.
# Left out: the most frequent word (a particle for most surahs, a blank for al-Kawthar), the most
# frequent letter, and the counts of vowel marks.

STATS_COLUMNS = (
    "surahNo, beginType, surahNameType, wordCount, charCount, ayahCount, uniqueAyahCount, "
    "repeatedAyahs, longestWord, charNotOccured"
)
# The 28 letters of the alphabet: the source also lists hamza and its seats, which are not.
ALPHABET = "ابتثجحخدذرزسشصضطظعغفقكلمنهوي"
# More longest words than this is a list of ties, not a curiosity.
MAX_LONGEST_WORDS = 2


@dataclass
class SurahStats:
    surah: int
    begin_type: str
    name_topic: str  # empty when the source gives none
    word_count: int
    char_count: int
    repeated_ayahs: list[int]
    longest_words: list[str]  # empty when there are too many ties to name
    missing_letters: str  # in alphabet order, no separator


def read_stats(path: Path, expected_sha256: str) -> list[tuple]:
    with closing(_connect(path)) as db:
        rows = db.execute(f"SELECT {STATS_COLUMNS} FROM surah_stats ORDER BY surahNo").fetchall()
    found = digest(rows)
    if found != expected_sha256:
        raise ValueError(f"surah_stats rows changed: {found}")
    return rows


def _listed(raw: str | None) -> list[str]:
    raw = (raw or "").strip()
    return [] if raw in ("", "لا يوجد", "0") else [part.strip() for part in raw.split("/") if part.strip()]


def surah_stats(rows: list[tuple]) -> list[SurahStats]:
    out = []
    for surah, begin, topic, words, chars, ayahs, unique, repeated, longest, missing in rows:
        repeated_ayahs = sorted(int(n) for n in _listed(repeated))
        # Every ayah whose text recurs is listed, the first occurrence too, so the list is one longer
        # than the repeats for each distinct recurring text.
        if repeated_ayahs and (ayahs == unique or len(repeated_ayahs) <= ayahs - unique):
            raise ValueError(f"surah {surah}: {len(repeated_ayahs)} repeated ayahs for {ayahs - unique} repeats")
        if not repeated_ayahs and ayahs != unique:
            raise ValueError(f"surah {surah}: repeats with no ayah listed")
        if any(not 1 <= n <= ayahs for n in repeated_ayahs):
            raise ValueError(f"surah {surah}: a repeated ayah outside 1..{ayahs}")
        words_longest = [unicodedata.normalize("NFC", w) for w in _listed(longest)]
        letters = set(_listed(missing))
        out.append(SurahStats(
            surah=surah,
            begin_type=(begin or "").strip(),
            name_topic=re.sub(r"\s+-\s+", "، ", (topic or "").strip()),
            word_count=int(words),
            char_count=int(chars),
            repeated_ayahs=repeated_ayahs,
            longest_words=words_longest if len(words_longest) <= MAX_LONGEST_WORDS else [],
            missing_letters="".join(c for c in ALPHABET if c in letters),
        ))
    if [s.surah for s in out] != list(range(1, SURAHS + 1)):
        raise ValueError("surah_stats does not hold surahs 1..114 once each")
    if any(not s.begin_type for s in out):
        raise ValueError("a surah with no opening type")
    return out


# ---------------------------------------------------------------------------------------------
# Roots, for searching by root: every word of the Quran from a real root, placed by its position
# among the words of the app's own Uthmani text, so the search can mark it there.
#
# The source gives every word a «root», particles included (من، في، الذي), and gives a word the
# mushaf writes as one — يَـٰقَوْمِ — both of its parts: «يا/قوم». A part counts as a root when the
# word's morphology names it as its stem: «من مادة: (قوم)». That leaves out the particles and
# pronouns, and the opening letters of surahs, and keeps the derived words and the proper names.

AYAH_REGIONS = ROOT / "mushaf_app" / "app" / "src" / "main" / "assets" / "data" / "ayah_regions.json"

# What the app's normalizeArabic drops: tashkeel, Quranic annotation marks, superscript alef, tatweel.
_APP_DROPPED = re.compile(r"[ً-ٕۖ-ٰۭـ]")
_DIACRITICS = re.compile(r"[ً-ٰٕـ]")
_SKELETON_DROP = str.maketrans("", "", "اأإآٱوىيءئؤ/ ")
# Prefixes a word form may carry that do not make it another word: «ورحمة» is «رحمة».
FORM_PREFIXES = ("وال", "فال", "بال", "كال", "ولل", "فلل", "لل", "ال", "و", "ف", "ب", "ك", "ل")


def _bare(text: str) -> str:
    return _DIACRITICS.sub("", unicodedata.normalize("NFC", text or "")).strip()


def app_words(text_uthmani: str) -> list[str]:
    """The Uthmani words the app counts: space-separated tokens, a lone waqf sign not being one."""
    return [token for token in text_uthmani.split(" ") if _APP_DROPPED.sub("", token)]


def skeleton(word: str) -> str:
    return _APP_DROPPED.sub("", unicodedata.normalize("NFC", word)).translate(_SKELETON_DROP)


def align_to_app(center: list[str], app: list[str]) -> list[list[int]]:
    """For each of the source's words, the app's word indices it covers.

    The two agree on the word count in every ayah but one, 15:7, where the mushaf writes لَّوْ مَا as two
    words and the source as one; that is where a word maps to two."""
    if len(center) == len(app):
        return [[i] for i in range(len(app))]
    out: list[list[int]] = []
    j = 0
    for word in center:
        target = skeleton(word)
        if j < len(app) and skeleton(app[j]) == target:
            out.append([j])
            j += 1
        elif j + 1 < len(app) and skeleton(app[j]) + skeleton(app[j + 1]) == target:
            out.append([j, j + 1])
            j += 2
        else:
            raise ValueError(f"cannot place {word!r} among {app}")
    if j != len(app):
        raise ValueError(f"words left over: {app[j:]}")
    return out


@dataclass
class RootWord:
    surah: int
    ayah: int
    app_words: list[int]  # indices among the ayah's Uthmani words, from 0
    root: str
    form: str  # the word as the source writes it, without diacritics: «ورحمة»


def read_root_rows(path: Path, expected_sha256: str) -> list[tuple]:
    with closing(_connect(path)) as db:
        rows = db.execute(
            """SELECT s.surahNo, s.ayahNo, s.wordNo, s.root, r.word, f.sarf
               FROM word_statistics s
               JOIN word_content_rasm r USING(surahNo, ayahNo, wordNo)
               JOIN word_content_sarf f USING(surahNo, ayahNo, wordNo)
               ORDER BY s.surahNo, s.ayahNo, s.wordNo"""
        ).fetchall()
    found = digest(rows)
    if found != expected_sha256:
        raise ValueError(f"word_statistics/word_content_rasm/word_content_sarf rows changed: {found}")
    return rows


def real_roots(rows: list[tuple]) -> set[str]:
    """The parts the morphology names as a stem in at least half of the words that carry them."""
    named, seen = Counter_(), Counter_()
    for _, _, _, root, _, sarf in rows:
        stem_note = _bare(sarf)
        for part in _bare(root).split("/"):
            seen[part] += 1
            if f"مادة: ({part})" in stem_note:
                named[part] += 1
    return {part for part, n in seen.items() if named[part] / n >= 0.5}


def root_words(rows: list[tuple], uthmani: dict[pc.Key, str]) -> list[RootWord]:
    real = real_roots(rows)
    by_ayah: dict[pc.Key, list[tuple]] = {}
    for row in rows:
        by_ayah.setdefault((row[0], row[1]), []).append(row)
    if sorted(by_ayah) != pc.ALL_AYAHS:
        raise ValueError("the word tables do not cover the 6,236 ayahs")
    out: list[RootWord] = []
    for key in pc.ALL_AYAHS:
        words = by_ayah[key]  # already in the source's word order
        placed = align_to_app([w[4] for w in words], app_words(uthmani[key]))
        for (s, a, _, root, form, _), indices in zip(words, placed):
            for part in dict.fromkeys(_bare(root).split("/")):
                if part in real:
                    out.append(RootWord(s, a, indices, part, _bare(form).replace("/", " ")))
    return out


def base_form(form: str, attested: set[str]) -> str:
    """[form] without a leading conjunction, preposition or article, when what is left is itself a form
    of the same root the Quran uses: «ورحمة» -> «رحمة», but «بلد» stays «بلد»."""
    for prefix in FORM_PREFIXES:
        rest = form[len(prefix):]
        if form.startswith(prefix) and len(rest) >= 2 and rest in attested:
            return base_form(rest, attested)
        # «للرحمن» is لِ + «الرحمن»: the article's alef is not written after the lam.
        if prefix in ("لل", "ولل", "فلل") and form.startswith(prefix) and "ال" + rest in attested:
            return base_form("ال" + rest, attested)
    return form


def read_uthmani(path: Path = AYAH_REGIONS) -> dict[pc.Key, str]:
    import json
    records = json.loads(path.read_text(encoding="utf-8"))["records"]
    return {(r["surah_number"], r["ayah_number"]): r["text_uthmani"] for r in records}


@dataclass
class RootRow:
    """One root as the app stores it: everything a search by that root shows, in one row."""
    id: int
    root: str
    word_count: int
    ayah_count: int
    # Its forms, most frequent first, one per line as «form<TAB>count»: a word's prefixes set aside
    # (see base_form). The app matches a typed word against these.
    forms: str
    # Every word under it in mushaf order, space-separated «ayahKey:word:form», where word is the
    # index among the ayah's Uthmani words and form the line of [forms] it belongs to, both from 0.
    occurrences: str


def root_rows(words: list[RootWord]) -> list[RootRow]:
    by_root: dict[str, list[RootWord]] = {}
    for w in words:
        by_root.setdefault(w.root, []).append(w)
    rows = []
    for rid, root in enumerate(sorted(by_root), start=1):
        group = by_root[root]
        attested = {w.form for w in group}
        base = {form: base_form(form, attested) for form in attested}
        counts = Counter_(base[w.form] for w in group)
        ordered = sorted(counts.items(), key=lambda kv: (-kv[1], kv[0]))
        line_of = {form: i for i, (form, _) in enumerate(ordered)}
        # A fused word placed on two indices (15:7) gives two; a word seen twice gives one.
        places = sorted({
            (pc.ayah_key(w.surah, w.ayah), index, line_of[base[w.form]]) for w in group for index in w.app_words
        })
        if any("\t" in form or "\n" in form for form, _ in ordered):
            raise ValueError(f"root {root}: a form holding a tab or line break")
        rows.append(RootRow(
            id=rid,
            root=root,
            word_count=len(group),
            ayah_count=len({w.surah * 1000 + w.ayah for w in group}),
            forms="\n".join(f"{form}\t{n}" for form, n in ordered),
            occurrences=" ".join(f"{key}:{index}:{line}" for key, index, line in places),
        ))
    return rows
