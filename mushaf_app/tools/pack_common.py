"""What every downloadable content pack shares: reading the Center's rows, cleaning them, writing,
compressing and checking the pack file.

A pack is one SQLite file the app downloads on request and opens read-only (it is not a Room
database). Schema 1:

    pack_meta(key TEXT PRIMARY KEY, value TEXT)
    passage(row_id INTEGER PRIMARY KEY, group_id, surah, from_ayah, to_ayah, kind, text, notes)
    ayah_group(ayah_key INTEGER PRIMARY KEY, group_id)        -- tafsir packs
    ayah_text(ayah_key INTEGER PRIMARY KEY, text, notes)      -- per-ayah text packs (i'rab)

where ayah_key = surah * 1000 + ayah. `passage.kind` holds the same Arabic row types as the bundled
database, so the app reads both with one mapping. `text` may carry note anchors ⟦n⟧; `notes` holds
the shown notes, one per line, in anchor order.

Cleaning is mechanical: markup out, the editor's notes taken out (only corrections and hadith sources
stay, as anchored notes, like the Sa'di tab), and each book's listed CORRECTIONS applied, every one of
which must match exactly once. Nothing is reworded.
"""

from __future__ import annotations

import gzip
import hashlib
import re
import shutil
import sqlite3
import unicodedata
from collections import Counter
from contextlib import closing
from dataclasses import dataclass, field
from pathlib import Path

from saadi_center import NOTE, note_kind

PACK_SCHEMA = 1
KIND_EXPLANATION = "تفسير آية"
ANCHOR_OPEN, ANCHOR_CLOSE = "⟦", "⟧"
ANCHOR = re.compile(f"{ANCHOR_OPEN}(\\d+){ANCHOR_CLOSE}")
SHOWN_NOTE_KINDS = {"correction", "hadith_source"}

LICENSE = "CC BY 4.0"
LICENSE_URL = "https://creativecommons.org/licenses/by/4.0/"
SOURCE_URL = "https://huggingface.co/datasets/tafsircenter/tafsir-mcp-data"
ATTRIBUTION = "مركز تفسير للدراسات القرآنية (tafsir.net)"

# Ayahs per surah, 1..114; 6,236 in all.
AYAH_COUNTS = [
    7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111, 110, 98, 135,
    112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88, 75, 85, 54, 53, 89,
    59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13, 14, 11, 11, 18, 12, 12, 30,
    52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42, 29, 19, 36, 25, 22, 17, 19, 26, 30, 20, 15,
    21, 11, 8, 8, 19, 5, 8, 8, 11, 11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6,
]
ALL_AYAHS = [(s, a) for s, count in enumerate(AYAH_COUNTS, start=1) for a in range(1, count + 1)]
assert len(ALL_AYAHS) == 6236

# Lengths a database column is cut at. A row of exactly one of these lengths was cut in the source.
CUT_LENGTHS = {4096, 8192, 16384, 32767, 32768, 65535, 65536}
CUT_NOTICE = "[انقطع نص هذا الموضع في المصدر الذي نُقل عنه.]"

# Marks the source uses for its own bookkeeping; none may reach a reader.
FORBIDDEN = ("¬", "¥", "ككك", "ممم", "___", "ضضض", "<br", "<p", "</", "<div", "&nbsp;", "\r", "﻿", "[as")
MAX_ROW_BYTES = 1_500_000  # a cursor window is 2 MB on Android 7
PARAGRAPH_LIMIT = 6000

Key = tuple[int, int]


def key_of(text: str) -> Key:
    sura, aya = text.split(":")
    return int(sura), int(aya)


def ayah_key(sura: int, aya: int) -> int:
    return sura * 1000 + aya


def rows_sha256(rows: dict[Key, str]) -> str:
    joined = "\n".join(f"{s}:{a}\t{rows[(s, a)]}" for s, a in sorted(rows))
    return hashlib.sha256(joined.encode("utf-8")).hexdigest()


def read_slim(path: Path, expected_sha256: str, expected_rows: int) -> dict[Key, str]:
    """The verbatim rows of one book, as extracted by build_center_slim.py, checked against a pin."""
    uri = "file:" + path.as_posix().replace(" ", "%20") + "?mode=ro&immutable=1"
    with closing(sqlite3.connect(uri, uri=True)) as db:
        db.execute("PRAGMA query_only=ON")
        db.execute("PRAGMA trusted_schema=OFF")
        rows = {(s, a): t for s, a, t in db.execute("SELECT sura, aya, text FROM rows")}
    if len(rows) != expected_rows:
        raise ValueError(f"{path.name}: {len(rows)} rows, expected {expected_rows}")
    digest = rows_sha256(rows)
    if digest != expected_sha256:
        raise ValueError(f"{path.name}: rows sha256 {digest} differs from the pinned {expected_sha256}")
    return rows


Correction = tuple[str, str, str, str]  # (ayah, wrong, right, reason)


def apply_corrections(rows: dict[Key, str], corrections: list[Correction]) -> dict[Key, str]:
    """NFC every row, then apply each correction, which must match exactly once."""
    fixed = {key: unicodedata.normalize("NFC", text) for key, text in rows.items()}
    for where, wrong, right, _ in corrections:
        key = key_of(where)
        wrong_nfc = unicodedata.normalize("NFC", wrong)
        count = fixed[key].count(wrong_nfc)
        if count != 1:
            raise ValueError(f"Correction for {where} matched {count} times, expected once: {wrong!r}")
        fixed[key] = fixed[key].replace(wrong_nfc, unicodedata.normalize("NFC", right))
    return fixed


def find_cut_rows(rows: dict[Key, str]) -> set[Key]:
    return {key for key, text in rows.items() if len(text) in CUT_LENGTHS}


def mark_cut(text: str) -> str:
    """Drop the half word a cut row ends on and say plainly that the text stops there."""
    cut = text.rstrip()
    space = max(cut.rfind(" "), cut.rfind("\n"))
    if space > 0:
        cut = cut[:space]
    return cut.rstrip(" ،,") + " …\n" + CUT_NOTICE


_TAGS = [
    (re.compile(r"\r\n?"), "\n"),
    (re.compile("﻿"), ""),
    (re.compile(r"&nbsp;"), " "),
    (re.compile(r"<br\s*/?>", re.IGNORECASE), "\n"),
    (re.compile(r"</?p>", re.IGNORECASE), "\n"),
    (re.compile(r"<div[^>]*>|</div>", re.IGNORECASE), "\n"),
]


def strip_markup(text: str) -> str:
    for pattern, replacement in _TAGS:
        text = pattern.sub(replacement, text)
    return text


# A print page's footnotes run over to the next page; the source keeps the page break inside the note.
_PAGE_CARRY = re.compile(r"ممم\s*بقية السابق\s*ممم\s*-?\[\d+\]-?_?|ممم")


def clean_note(note: str) -> str:
    note = note.strip().removeprefix("¬").removesuffix("¥")
    note = _PAGE_CARRY.sub(" ", note)
    note = re.sub(r"\[as\d\]", " ", note)
    note = re.sub(r"_{3,}", " ", note)
    note = re.sub(r"\s+", " ", note).strip()
    return note


@dataclass
class NoteStats:
    kinds: Counter = field(default_factory=Counter)
    unclosed: int = 0


def take_notes(text: str, stats: NoteStats) -> tuple[str, list[str]]:
    """Replace each note with an anchor ⟦n⟧ if readers are shown it, else remove it."""
    shown: list[str] = []

    def swap(match: re.Match[str]) -> str:
        raw = match.group()
        if not raw.endswith("¥"):
            stats.unclosed += 1
        note = clean_note(raw)
        if not note:
            stats.kinds["empty"] += 1
            return ""
        kind = note_kind(note)
        stats.kinds[kind] += 1
        if kind not in SHOWN_NOTE_KINDS:
            return ""
        shown.append(note)
        return f"{ANCHOR_OPEN}{len(shown)}{ANCHOR_CLOSE}"

    return NOTE.sub(swap, text), shown


def tidy(text: str) -> str:
    """One paragraph per line, no empty lines, and no gap where a note stood."""
    lines = []
    for line in text.split("\n"):
        line = re.sub(r"[ \t ]+", " ", line).strip()
        line = re.sub(r" +([.،؛:؟!⟦])", r"\1", line)
        if line:
            lines.append(line)
    return "\n".join(lines)


_SENTENCE_END = re.compile(r"[.؟!»\"]\s")


def split_long_paragraphs(text: str, limit: int = PARAGRAPH_LIMIT) -> str:
    """Break a paragraph longer than [limit] at the last sentence end before it, so the app can lay
    out a long row as several items instead of one huge text."""
    out = []
    for line in text.split("\n"):
        while len(line) > limit:
            window = line[:limit]
            ends = [m.end() for m in _SENTENCE_END.finditer(window)]
            cut = ends[-1] if ends and ends[-1] > limit // 3 else (window.rfind(" ") + 1 or limit)
            out.append(line[:cut].rstrip())
            line = line[cut:].lstrip()
        out.append(line)
    return "\n".join(p for p in out if p)


@dataclass
class Row:
    sura: int
    from_ayah: int
    to_ayah: int
    text: str
    notes: list[str]


def merge_identical(rows: list[Row]) -> tuple[list[Row], int]:
    """Join consecutive ayahs whose rows are identical: the Center split a passage explained as one
    and filed the whole of it under each of its ayahs."""
    merged: list[Row] = []
    joined = 0
    for row in rows:
        last = merged[-1] if merged else None
        if (
            last is not None
            and last.sura == row.sura
            and last.to_ayah + 1 == row.from_ayah
            and last.text == row.text
            and last.notes == row.notes
        ):
            last.to_ayah = row.to_ayah
            joined += 1
        else:
            merged.append(Row(row.sura, row.from_ayah, row.to_ayah, row.text, list(row.notes)))
    return merged, joined


def check_text(place: str, text: str, notes: list[str]) -> None:
    for mark in FORBIDDEN:
        if mark in text:
            raise ValueError(f"{place} still holds {mark!r}: …{text[max(0, text.find(mark) - 60):text.find(mark) + 60]}…")
        for note in notes:
            if mark in note:
                raise ValueError(f"{place} has a note holding {mark!r}: {note[:120]}")
    if any("\n" in note for note in notes):
        raise ValueError(f"{place} has a note spanning lines")
    anchors = [int(n) for n in ANCHOR.findall(text)]
    if anchors != list(range(1, len(notes) + 1)):
        raise ValueError(f"{place} has anchors {anchors[:10]} for {len(notes)} notes")
    size = len(text.encode("utf-8")) + sum(len(n.encode("utf-8")) + 1 for n in notes)
    if size > MAX_ROW_BYTES:
        raise ValueError(f"{place} is {size} bytes, over {MAX_ROW_BYTES}")
    if not text.strip():
        raise ValueError(f"{place} is empty")


def _create(path: Path, meta: dict[str, str]) -> sqlite3.Connection:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.unlink(missing_ok=True)
    db = sqlite3.connect(path)
    db.executescript(
        f"""
        PRAGMA journal_mode = DELETE;
        PRAGMA user_version = {PACK_SCHEMA};
        CREATE TABLE pack_meta (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL);
        """
    )
    db.executemany("INSERT INTO pack_meta VALUES (?, ?)", sorted(meta.items()))
    return db


def _finish(db: sqlite3.Connection, temporary: Path, output: Path) -> None:
    db.commit()
    db.execute("ANALYZE")
    db.commit()
    db.execute("VACUUM")
    integrity = db.execute("PRAGMA integrity_check").fetchone()[0]
    db.close()
    if integrity != "ok":
        raise ValueError(f"{output.name}: integrity {integrity}")
    temporary.replace(output)


def extend_to_neighbours(rows: list[Row], redirect: dict[Key, Key]) -> dict[Key, Key]:
    """A redirected ayah right next to the row that explains it joins that row's range: the author
    explains the two together (Tabari's heading for 4:169 quotes 4:168 too). Returns the redirects
    left, those whose row is further away; the app shows them that row as the passage around them."""
    left = dict(redirect)
    changed = True
    while changed:
        changed = False
        for key in sorted(left):
            sura, aya = key
            target = left[key]
            row = next(r for r in rows if r.sura == target[0] and r.from_ayah <= target[1] <= r.to_ayah)
            if row.from_ayah - 1 == aya:
                row.from_ayah = aya
            elif row.to_ayah + 1 == aya:
                row.to_ayah = aya
            else:
                continue
            del left[key]
            changed = True
    return left


def assign_groups(rows: list[Row], redirect: dict[Key, Key]) -> dict[Key, int]:
    """Each row is its own group (group id = row id). An ayah in [redirect] has no row of its own
    and is shown the group of the ayah it points at."""
    group_of: dict[Key, int] = {}
    for row_id, row in enumerate(rows, start=1):
        for aya in range(row.from_ayah, row.to_ayah + 1):
            if (row.sura, aya) in group_of:
                raise ValueError(f"{row.sura}:{aya} is in two rows")
            group_of[(row.sura, aya)] = row_id
    for key, target in redirect.items():
        if key in group_of:
            raise ValueError(f"{key} is redirected but still has a row")
        group_of[key] = group_of[target]
    missing = [k for k in ALL_AYAHS if k not in group_of]
    if missing:
        raise ValueError(f"ayahs with no row: {missing[:10]}")
    return group_of


def write_tafsir_pack(output: Path, meta: dict[str, str], rows: list[Row], group_of: dict[Key, int]) -> None:
    """[rows] are in reading order; row N is group N, and [group_of] maps every ayah to a group."""
    temporary = output.with_suffix(".tmp")
    db = _create(temporary, meta)
    db.executescript(
        """
        CREATE TABLE passage (
            row_id INTEGER PRIMARY KEY,
            group_id INTEGER NOT NULL,
            surah INTEGER NOT NULL,
            from_ayah INTEGER NOT NULL,
            to_ayah INTEGER NOT NULL,
            kind TEXT NOT NULL,
            text TEXT NOT NULL,
            notes TEXT NOT NULL
        );
        CREATE INDEX passage_group ON passage(group_id);
        CREATE TABLE ayah_group (ayah_key INTEGER PRIMARY KEY, group_id INTEGER NOT NULL);
        """
    )
    db.executemany(
        "INSERT INTO passage VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        [
            (row_id, row_id, r.sura, r.from_ayah, r.to_ayah, KIND_EXPLANATION, r.text, "\n".join(r.notes))
            for row_id, r in enumerate(rows, start=1)
        ],
    )
    db.executemany(
        "INSERT INTO ayah_group VALUES (?, ?)",
        sorted((ayah_key(*key), group) for key, group in group_of.items()),
    )
    _finish(db, temporary, output)


def write_text_pack(output: Path, meta: dict[str, str], texts: dict[Key, tuple[str, list[str]]]) -> None:
    temporary = output.with_suffix(".tmp")
    db = _create(temporary, meta)
    db.execute("CREATE TABLE ayah_text (ayah_key INTEGER PRIMARY KEY, text TEXT NOT NULL, notes TEXT NOT NULL)")
    db.executemany(
        "INSERT INTO ayah_text VALUES (?, ?, ?)",
        [(ayah_key(*key), text, "\n".join(notes)) for key, (text, notes) in sorted(texts.items())],
    )
    _finish(db, temporary, output)


def gzip_deterministic(source: Path, target: Path) -> None:
    """The same pack always compresses to the same bytes: no file name, no time stamp."""
    with source.open("rb") as raw, target.open("wb") as out:
        with gzip.GzipFile(filename="", mode="wb", fileobj=out, mtime=0, compresslevel=9) as packed:
            shutil.copyfileobj(raw, packed, 1 << 20)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(1 << 20):
            digest.update(chunk)
    return digest.hexdigest()


REQUIRED_META = ("pack_id", "kind", "version", "schema", "title", "credit", "changes",
                 "license", "license_url", "source_url", "source_rows_sha256")


def verify_pack(path: Path) -> dict[str, str]:
    """Open a finished pack the way the app will, and fail on anything the app could not show."""
    uri = "file:" + path.as_posix().replace(" ", "%20") + "?mode=ro"
    with closing(sqlite3.connect(uri, uri=True)) as db:
        meta = dict(db.execute("SELECT key, value FROM pack_meta"))
        missing = [k for k in REQUIRED_META if not meta.get(k)]
        if missing:
            raise ValueError(f"{path.name}: pack_meta lacks {missing}")
        if int(meta["schema"]) != PACK_SCHEMA or db.execute("PRAGMA user_version").fetchone()[0] != PACK_SCHEMA:
            raise ValueError(f"{path.name}: schema is not {PACK_SCHEMA}")
        expected = {ayah_key(s, a) for s, a in ALL_AYAHS}
        if meta["kind"] == "tafsir":
            mapped = dict(db.execute("SELECT ayah_key, group_id FROM ayah_group"))
            if set(mapped) != expected:
                raise ValueError(f"{path.name}: ayah_group covers {len(mapped)} ayahs, not the 6,236")
            groups = {g for (g,) in db.execute("SELECT DISTINCT group_id FROM passage")}
            if not set(mapped.values()) <= groups:
                raise ValueError(f"{path.name}: an ayah points at a group with no rows")
            for row_id, s, f, t, text, notes in db.execute(
                "SELECT row_id, surah, from_ayah, to_ayah, text, notes FROM passage"
            ):
                check_text(f"{path.name} {s}:{f}-{t}", text, notes.split("\n") if notes else [])
        elif meta["kind"] == "irab":
            keys = set()
            for key, text, notes in db.execute("SELECT ayah_key, text, notes FROM ayah_text"):
                keys.add(key)
                check_text(f"{path.name} {key // 1000}:{key % 1000}", text, notes.split("\n") if notes else [])
            if keys != expected:
                raise ValueError(f"{path.name}: ayah_text covers {len(keys)} ayahs, not the 6,236")
        else:
            raise ValueError(f"{path.name}: unknown kind {meta['kind']}")
        if db.execute("PRAGMA integrity_check").fetchone()[0] != "ok":
            raise ValueError(f"{path.name}: integrity check failed")
    return meta


def clean_rows(
    rows: dict[Key, str],
    corrections: list[Correction],
    known_cuts: set[Key],
    expected_unclosed: int,
    pre_clean=None,
    whole_despite_length: set[Key] = frozenset(),
) -> tuple[dict[Key, tuple[str, list[str]]], NoteStats]:
    """The shared cleaning pass: corrections, cut rows, markup, notes, tidying, long paragraphs.

    [whole_despite_length] are rows whose length happens to be a cut length but which end properly.
    """
    cuts = find_cut_rows(rows)
    if cuts != set(known_cuts) | set(whole_despite_length):
        raise ValueError(f"cut rows {sorted(cuts)} differ from the known {sorted(known_cuts)}")
    fixed = apply_corrections(rows, corrections)
    stats = NoteStats()
    out: dict[Key, tuple[str, list[str]]] = {}
    for key in sorted(fixed):
        text = fixed[key]
        if key in known_cuts:
            text = mark_cut(text)
        text, notes = take_notes(strip_markup(text), stats)
        # After the notes are out, so a line dropped here can never be half of a note.
        if pre_clean is not None:
            text = pre_clean(key, text)
        text = split_long_paragraphs(tidy(text))
        out[key] = (text, notes)
    if stats.unclosed != expected_unclosed:
        raise ValueError(f"{stats.unclosed} notes are left unclosed, expected {expected_unclosed}")
    return out, stats
