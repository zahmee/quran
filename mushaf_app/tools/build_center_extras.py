"""Build the read-only Room asset with the Tafsir Center's page fawaed and surah introductions.

Run from this folder: python build_center_extras.py
Reads the whole of the Center's quran.db (https://huggingface.co/datasets/tafsircenter/tafsir-mcp-data,
CC BY 4.0), kept outside git in tafsircenter_full_<date>/ at the repo root, and pinned here by the
SHA-256 of the rows it uses. center_extras.py says what is done to them.

It is a database of its own, not more tables in quran_content_v4.db: that one is built from sources
kept on another machine, and a new content file makes every install copy it afresh.
"""

from __future__ import annotations

import sqlite3
import sys

import center_extras as ce

FAWAED_SHA256 = "7915fd3ad82dbb8b97e87409db95e8d8a122a96cb3c3691f1198b9f9e889e52c"
SURAHS_SHA256 = "82f70c19244db28ead48400e8488cf5226cda3600a74f8c1457a36db24db4069"
TAJWEED_SHA256 = "c4b98729eb065b14c6de74ea1375614fa734cd153cd054fa76573e3970048f9a"
STATS_SHA256 = "b600ebf17cb196fffba910cf960b1c039e0d43d233dcac283733551a1d2955db"
ROOTS_SHA256 = "12343054b867b50fd867009dee67219718bcc044c75a94accc2f3dfa03e53f18"
# Roots the morphology names as a stem, and the words under them: a change means the source changed.
EXPECTED_ROOTS = 1720
EXPECTED_ROOT_WORDS = 51249
# Editor's notes the source opens and never closes; each runs to the end of its line.
EXPECTED_UNCLOSED = 0
# Tajweed notes the source leaves with a bracket it never closes or never opened. Shown as they are:
# the app only styles a quote whose braces close. A change in this count means the source changed.
EXPECTED_STRAY_BRACKETS = 18
# Ayahs with no tajweed note in the source (17 empty, one a lone dash, one a working mark).
EXPECTED_AYAHS_WITHOUT_TAJWEED = 19

OUTPUT = ce.ROOT / "mushaf_app" / "app" / "src" / "main" / "assets" / "databases" / "center_extras_v4.db"
SCHEMA_VERSION = 4
# The asset this one replaces; its tables must come out unchanged.
PREVIOUS_OUTPUT = OUTPUT.with_name("center_extras_v3.db")
UNCHANGED_TABLES = ("page_fawaed", "surah_info", "surah_section", "ayah_tajweed", "surah_stats")


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    fawaed = ce.clean_fawaed(ce.read_fawaed(ce.SOURCE, FAWAED_SHA256))
    stats, content = ce.read_surahs(ce.SOURCE, SURAHS_SHA256)
    facts = ce.surah_facts(stats, content)
    sections = ce.clean_sections(content, EXPECTED_UNCLOSED)
    tajweed = ce.clean_tajweed(ce.read_tajweed(ce.SOURCE, TAJWEED_SHA256))
    stats_rows = ce.surah_stats(ce.read_stats(ce.SOURCE, STATS_SHA256))
    root_words = ce.root_words(ce.read_root_rows(ce.SOURCE, ROOTS_SHA256), ce.read_uthmani())
    roots = ce.root_rows(root_words)
    if len(roots) != EXPECTED_ROOTS or len(root_words) != EXPECTED_ROOT_WORDS:
        raise ValueError(f"{len(roots)} roots over {len(root_words)} words, "
                         f"expected {EXPECTED_ROOTS} over {EXPECTED_ROOT_WORDS}")
    covered = sum(n.to_ayah - n.from_ayah + 1 for n in tajweed)
    if len(ce.pc.ALL_AYAHS) - covered != EXPECTED_AYAHS_WITHOUT_TAJWEED:
        raise ValueError(f"{len(ce.pc.ALL_AYAHS) - covered} ayahs without a tajweed note")
    stray = sum(1 for n in tajweed if ce.unbalanced(n.text))
    if stray != EXPECTED_STRAY_BRACKETS:
        raise ValueError(f"{stray} tajweed notes with a stray bracket, expected {EXPECTED_STRAY_BRACKETS}")

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    temporary = OUTPUT.with_suffix(".tmp")
    temporary.unlink(missing_ok=True)
    db = sqlite3.connect(temporary)
    try:
        db.executescript(
            f"""
            PRAGMA journal_mode = DELETE;
            PRAGMA user_version = {SCHEMA_VERSION};

            CREATE TABLE `content_meta` (
                `key` TEXT NOT NULL,
                `value` TEXT NOT NULL,
                PRIMARY KEY(`key`)
            );

            CREATE TABLE `page_fawaed` (
                `page` INTEGER NOT NULL,
                `position` INTEGER NOT NULL,
                `text` TEXT NOT NULL,
                PRIMARY KEY(`page`, `position`)
            );

            CREATE TABLE `surah_info` (
                `surah_number` INTEGER NOT NULL,
                `revelation_place` TEXT NOT NULL,
                `revelation_order` INTEGER NOT NULL,
                `surah_class` TEXT NOT NULL,
                `sajdah_ayahs` TEXT NOT NULL,
                `goal` TEXT NOT NULL,
                PRIMARY KEY(`surah_number`)
            );

            CREATE TABLE `surah_section` (
                `surah_number` INTEGER NOT NULL,
                `position` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `text` TEXT NOT NULL,
                `notes` TEXT NOT NULL,
                PRIMARY KEY(`surah_number`, `position`)
            );

            CREATE TABLE `ayah_tajweed` (
                `surah_number` INTEGER NOT NULL,
                `from_ayah` INTEGER NOT NULL,
                `to_ayah` INTEGER NOT NULL,
                `text` TEXT NOT NULL,
                PRIMARY KEY(`surah_number`, `from_ayah`)
            );

            CREATE TABLE `surah_stats` (
                `surah_number` INTEGER NOT NULL,
                `begin_type` TEXT NOT NULL,
                `name_topic` TEXT NOT NULL,
                `word_count` INTEGER NOT NULL,
                `char_count` INTEGER NOT NULL,
                `repeated_ayahs` TEXT NOT NULL,
                `longest_words` TEXT NOT NULL,
                `missing_letters` TEXT NOT NULL,
                PRIMARY KEY(`surah_number`)
            );

            CREATE TABLE `root` (
                `id` INTEGER NOT NULL,
                `root` TEXT NOT NULL,
                `word_count` INTEGER NOT NULL,
                `ayah_count` INTEGER NOT NULL,
                `forms` TEXT NOT NULL,
                `occurrences` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            );
            """
        )
        db.executemany(
            "INSERT INTO content_meta VALUES (?, ?)",
            [
                ("schema_version", str(SCHEMA_VERSION)),
                ("source_package", ce.SOURCE.parent.name),
                ("source", "مركز تفسير للدراسات القرآنية"),
                ("source_url", "https://tafsir.net"),
                ("dataset_url", "https://huggingface.co/datasets/tafsircenter/tafsir-mcp-data"),
                ("license", "CC BY 4.0"),
                ("license_url", "https://creativecommons.org/licenses/by/4.0/"),
                ("fawaed_rows_sha256", FAWAED_SHA256),
                ("surah_rows_sha256", SURAHS_SHA256),
                ("tajweed_rows_sha256", TAJWEED_SHA256),
                ("stats_rows_sha256", STATS_SHA256),
                ("roots_rows_sha256", ROOTS_SHA256),
            ],
        )
        db.executemany(
            "INSERT INTO page_fawaed VALUES (?, ?, ?)",
            [(page, i, text) for page, texts in sorted(fawaed.items()) for i, text in enumerate(texts, start=1)],
        )
        db.executemany(
            "INSERT INTO surah_info VALUES (?, ?, ?, ?, ?, ?)",
            [
                (f.surah, f.revelation_place, f.revelation_order, f.surah_class,
                 ",".join(map(str, f.sajdah_ayahs)), f.goal)
                for f in facts
            ],
        )
        titles = [title for _, title in ce.SECTIONS]
        db.executemany(
            "INSERT INTO surah_section VALUES (?, ?, ?, ?, ?)",
            [
                (surah, position, titles[position - 1], text, "\n".join(notes))
                for (surah, position), (text, notes) in sorted(sections.items())
            ],
        )
        db.executemany(
            "INSERT INTO ayah_tajweed VALUES (?, ?, ?, ?)",
            [(n.surah, n.from_ayah, n.to_ayah, n.text) for n in tajweed],
        )
        db.executemany(
            "INSERT INTO surah_stats VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            [
                (st.surah, st.begin_type, st.name_topic, st.word_count, st.char_count,
                 ",".join(map(str, st.repeated_ayahs)), "\n".join(st.longest_words), st.missing_letters)
                for st in stats_rows
            ],
        )
        db.executemany(
            "INSERT INTO root VALUES (?, ?, ?, ?, ?, ?)",
            [(r.id, r.root, r.word_count, r.ayah_count, r.forms, r.occurrences) for r in roots],
        )
        db.commit()
        db.execute("ANALYZE")
        db.commit()
        db.execute("VACUUM")
        integrity = db.execute("PRAGMA integrity_check").fetchone()[0]
        counts = {
            "fawaed": db.execute("SELECT COUNT(*) FROM page_fawaed").fetchone()[0],
            "pages": db.execute("SELECT COUNT(DISTINCT page) FROM page_fawaed").fetchone()[0],
            "surahs": db.execute("SELECT COUNT(*) FROM surah_info").fetchone()[0],
            "sections": db.execute("SELECT COUNT(*) FROM surah_section").fetchone()[0],
            "tajweed": db.execute("SELECT COUNT(*) FROM ayah_tajweed").fetchone()[0],
            "stats": db.execute("SELECT COUNT(*) FROM surah_stats").fetchone()[0],
            "roots": db.execute("SELECT COUNT(*) FROM root").fetchone()[0],
            "notes": db.execute("SELECT SUM(LENGTH(notes) - LENGTH(REPLACE(notes, char(10), '')) + (notes <> '')) FROM surah_section").fetchone()[0],
        }
        if (
            integrity != "ok"
            or counts["pages"] != ce.PAGES
            or counts["surahs"] != ce.SURAHS
            or counts["sections"] != ce.SURAHS * len(ce.SECTIONS)
        ):
            raise ValueError(f"validation failed: integrity={integrity} {counts}")
        if PREVIOUS_OUTPUT.exists():
            previous = sqlite3.connect(f"file:{PREVIOUS_OUTPUT.as_posix()}?mode=ro", uri=True)
            try:
                changed = [t for t in UNCHANGED_TABLES if ce.table_digest(previous, t) != ce.table_digest(db, t)]
            finally:
                previous.close()
            if changed:
                raise ValueError(f"tables changed since {PREVIOUS_OUTPUT.name}: {changed}")
            print(f"unchanged since {PREVIOUS_OUTPUT.name}: {', '.join(UNCHANGED_TABLES)}")
        largest = db.execute(
            "SELECT surah_number, title, LENGTH(text) FROM surah_section ORDER BY 3 DESC LIMIT 1"
        ).fetchone()
    finally:
        db.close()
    temporary.replace(OUTPUT)
    print("counts", counts, "largest section", largest)
    print(f"output={OUTPUT} bytes={OUTPUT.stat().st_size}")


if __name__ == "__main__":
    main()
