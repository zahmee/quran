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
# Editor's notes the source opens and never closes; each runs to the end of its line.
EXPECTED_UNCLOSED = 0

OUTPUT = ce.ROOT / "mushaf_app" / "app" / "src" / "main" / "assets" / "databases" / "center_extras_v1.db"
SCHEMA_VERSION = 1


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    fawaed = ce.clean_fawaed(ce.read_fawaed(ce.SOURCE, FAWAED_SHA256))
    stats, content = ce.read_surahs(ce.SOURCE, SURAHS_SHA256)
    facts = ce.surah_facts(stats, content)
    sections = ce.clean_sections(content, EXPECTED_UNCLOSED)

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
            "notes": db.execute("SELECT SUM(LENGTH(notes) - LENGTH(REPLACE(notes, char(10), '')) + (notes <> '')) FROM surah_section").fetchone()[0],
        }
        if (
            integrity != "ok"
            or counts["pages"] != ce.PAGES
            or counts["surahs"] != ce.SURAHS
            or counts["sections"] != ce.SURAHS * len(ce.SECTIONS)
        ):
            raise ValueError(f"validation failed: integrity={integrity} {counts}")
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
