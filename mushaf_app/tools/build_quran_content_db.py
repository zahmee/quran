"""Build the read-only Room asset for Tafsir Muyassar and Quran vocabulary."""

from __future__ import annotations

import json
import sqlite3
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SOURCE_DIR = ROOT / "qurancomplex_tafsir_gharib_2026-08-14"
TAFSIR_IN = SOURCE_DIR / "tafsir_muyassar.json"
GHARIB_IN = SOURCE_DIR / "muyassar_gharib.json"
OUTPUT = (
    ROOT / "mushaf_app" / "app" / "src" / "main" / "assets" /
    "databases" / "quran_content_v1.db"
)

EXPECTED_TAFSIR = 6236
EXPECTED_GHARIB = 11372


def read_json(path: Path):
    with path.open(encoding="utf-8") as stream:
        return json.load(stream)


def main() -> None:
    tafsir = read_json(TAFSIR_IN)
    gharib = read_json(GHARIB_IN)
    if len(tafsir) != EXPECTED_TAFSIR or len(gharib) != EXPECTED_GHARIB:
        raise ValueError(
            f"Unexpected source counts: tafsir={len(tafsir)}, gharib={len(gharib)}"
        )

    tafsir_keys = {
        f"{row['surah_number']}:{row['ayah_number']}" for row in tafsir
    }
    if len(tafsir_keys) != EXPECTED_TAFSIR:
        raise ValueError("Tafsir verse keys are not unique")

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    temporary = OUTPUT.with_suffix(".tmp")
    temporary.unlink(missing_ok=True)
    database = sqlite3.connect(temporary)
    try:
        database.executescript(
            """
            PRAGMA journal_mode = DELETE;
            PRAGMA synchronous = OFF;
            PRAGMA user_version = 1;

            CREATE TABLE `content_meta` (
                `key` TEXT NOT NULL,
                `value` TEXT NOT NULL,
                PRIMARY KEY(`key`)
            );

            CREATE TABLE `tafsir_ayah` (
                `verse_key` TEXT NOT NULL,
                `surah_number` INTEGER NOT NULL,
                `ayah_number` INTEGER NOT NULL,
                `source_id` INTEGER NOT NULL,
                `text_html` TEXT NOT NULL,
                PRIMARY KEY(`verse_key`)
            );
            CREATE UNIQUE INDEX `index_tafsir_ayah_surah_number_ayah_number`
                ON `tafsir_ayah` (`surah_number`, `ayah_number`);

            CREATE TABLE `gharib_entry` (
                `id` INTEGER NOT NULL,
                `surah_number` INTEGER NOT NULL,
                `ayah_start` INTEGER NOT NULL,
                `ayah_end` INTEGER NOT NULL,
                `entry_index` INTEGER NOT NULL,
                `word` TEXT NOT NULL,
                `meaning` TEXT NOT NULL,
                `source_text` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            );

            CREATE TABLE `gharib_ayah_map` (
                `verse_key` TEXT NOT NULL,
                `entry_id` INTEGER NOT NULL,
                `sort_order` INTEGER NOT NULL,
                PRIMARY KEY(`verse_key`, `entry_id`)
            );
            CREATE INDEX `index_gharib_ayah_map_verse_key_sort_order`
                ON `gharib_ayah_map` (`verse_key`, `sort_order`);
            """
        )

        database.executemany(
            "INSERT INTO content_meta(`key`, `value`) VALUES (?, ?)",
            [
                ("schema_version", "1"),
                ("source_package", "qurancomplex_tafsir_gharib_2026-08-14"),
                ("tafsir_title", "التفسير الميسر للقرآن الكريم"),
                ("tafsir_version", "3.0"),
                ("gharib_title", "الميسر في غريب القرآن الكريم"),
                ("gharib_edition", "الطبعة الثانية"),
                ("publisher", "مجمع الملك فهد لطباعة المصحف الشريف"),
            ],
        )
        database.executemany(
            """
            INSERT INTO tafsir_ayah(
                verse_key, surah_number, ayah_number, source_id, text_html
            ) VALUES (?, ?, ?, ?, ?)
            """,
            [
                (
                    f"{row['surah_number']}:{row['ayah_number']}",
                    row["surah_number"],
                    row["ayah_number"],
                    row["id"],
                    row["text"],
                )
                for row in tafsir
            ],
        )
        database.executemany(
            """
            INSERT INTO gharib_entry(
                id, surah_number, ayah_start, ayah_end, entry_index,
                word, meaning, source_text
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            [
                (
                    row["id"],
                    row["surah_number"],
                    row["ayah_number"],
                    row["ayah_number_end"],
                    row["entry_index"],
                    row["word"],
                    row["meaning"],
                    row["source_text"],
                )
                for row in gharib
            ],
        )

        mappings = []
        for row in gharib:
            for ayah in range(row["ayah_number"], row["ayah_number_end"] + 1):
                verse_key = f"{row['surah_number']}:{ayah}"
                if verse_key not in tafsir_keys:
                    raise ValueError(f"Gharib entry references unknown ayah {verse_key}")
                mappings.append((verse_key, row["id"], row["entry_index"]))
        database.executemany(
            """
            INSERT INTO gharib_ayah_map(verse_key, entry_id, sort_order)
            VALUES (?, ?, ?)
            """,
            mappings,
        )
        database.commit()
        database.execute("ANALYZE")
        database.execute("VACUUM")

        integrity = database.execute("PRAGMA integrity_check").fetchone()[0]
        counts = {
            "tafsir": database.execute("SELECT COUNT(*) FROM tafsir_ayah").fetchone()[0],
            "gharib": database.execute("SELECT COUNT(*) FROM gharib_entry").fetchone()[0],
            "mappings": database.execute("SELECT COUNT(*) FROM gharib_ayah_map").fetchone()[0],
        }
        if integrity != "ok" or counts["tafsir"] != EXPECTED_TAFSIR:
            raise ValueError(f"Database validation failed: integrity={integrity}, {counts}")
    finally:
        database.close()

    temporary.replace(OUTPUT)
    print(f"output={OUTPUT}")
    print(f"bytes={OUTPUT.stat().st_size} counts={counts} integrity={integrity}")


if __name__ == "__main__":
    main()
