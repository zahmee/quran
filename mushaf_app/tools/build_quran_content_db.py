"""Build the read-only Room asset: Tafsir Muyassar, Quran vocabulary and Tafsir al-Sa'di."""

from __future__ import annotations

import json
import sqlite3
from collections import defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SOURCE_DIR = ROOT / "qurancomplex_tafsir_gharib_2026-08-14"
TAFSIR_IN = SOURCE_DIR / "tafsir_muyassar.json"
GHARIB_IN = SOURCE_DIR / "muyassar_gharib.json"
# Made by tools/fetch_quranenc_saadi.py; a new snapshot means a new folder name here.
SAADI_DIR = ROOT / "quranenc_saadi_2026-11-05"
SAADI_ROWS_IN = SAADI_DIR / "saadi_rows.json"
SAADI_META_IN = SAADI_DIR / "metadata.json"
# A new file name, not just a new user_version: Room copies a prepackaged database only when the
# file is missing, so an install that already holds v1 would otherwise never see the new tables.
OUTPUT = (
    ROOT / "mushaf_app" / "app" / "src" / "main" / "assets" /
    "databases" / "quran_content_v2.db"
)
SCHEMA_VERSION = 2

EXPECTED_TAFSIR = 6236
EXPECTED_GHARIB = 11372
EXPECTED_SAADI_ROWS = 7150

SAADI_SOURCE_ID = "saadi"
PASSAGE_KIND = "المقطع"
# Rows with no ayah range (from_aya == 0) only have a place in the reading order. These open what
# comes next — a surah, a volume, a passage — so they belong to the passage that follows them.
# Every other range-less row (a lead-in to the next ayah, a closing note, a chapter, "* * *")
# continues the passage it follows.
LEADING_KINDS = {
    "اسم السورة",
    "مكان نزول السورة",
    "البسملة",
    "تمهيد للمقطع",
    "مقدمة المجلد",
}


def read_json(path: Path):
    with path.open(encoding="utf-8") as stream:
        return json.load(stream)


def group_saadi(rows: list[dict], verse_keys: set[str]) -> tuple[list[tuple[dict, int]], dict[str, int]]:
    """Cut Sa'di's rows, in reading order, into the passages a reader is shown together.

    A passage starts at each "المقطع" row (the block of Quran text being explained) and carries
    the rows after it. The print sometimes lets an ayah's explanation spill across a passage
    boundary (4:78 is explained at the end of the 4:77 passage and again after 4:78-79), so
    passages that share an ayah are merged: everything said about one ayah stays on one screen.
    """
    raw_group: list[int] = []
    group_surah: list[int] = []
    pending: list[int] = []
    current = -1
    for index, row in enumerate(rows):
        surah = int(row["sura"])
        if current >= 0 and group_surah[current] != surah:
            current = -1  # never let one surah's rows join another surah's passage
        raw_group.append(-1)
        if row["type"] == PASSAGE_KIND:
            group_surah.append(surah)
            current = len(group_surah) - 1
            for waiting in pending:
                raw_group[waiting] = current
            pending.clear()
            raw_group[index] = current
        elif int(row["from_aya"]) == 0 and (row["type"] in LEADING_KINDS or current < 0):
            pending.append(index)
        elif current < 0:
            raise ValueError(f"Ayah row before any passage in surah {surah}: id {row['id']}")
        else:
            raw_group[index] = current
        # A surah's last rows may still be waiting for a passage that never comes.
        next_surah = int(rows[index + 1]["sura"]) if index + 1 < len(rows) else None
        if pending and next_surah != surah:
            if current < 0:
                raise ValueError(f"Surah {surah} has rows but no passage")
            for waiting in pending:
                raw_group[waiting] = current
            pending.clear()
    if -1 in raw_group:
        raise ValueError(f"Sa'di row left outside any passage: id {rows[raw_group.index(-1)]['id']}")

    # Merge passages that mention the same ayah (union-find over passage indices).
    parent = list(range(len(group_surah)))

    def find(node: int) -> int:
        while parent[node] != node:
            parent[node] = parent[parent[node]]
            node = parent[node]
        return node

    groups_of_ayah: dict[str, set[int]] = defaultdict(set)
    for row, group in zip(rows, raw_group):
        start, end = int(row["from_aya"]), int(row["to_aya"])
        for ayah in range(start, end + 1) if start > 0 else ():
            groups_of_ayah[f"{row['sura']}:{ayah}"].add(group)
    for groups in groups_of_ayah.values():
        first, *others = sorted(groups)
        for other in others:
            parent[find(other)] = find(first)

    # Renumber the merged passages 1..n in reading order.
    final_id: dict[int, int] = {}
    assigned: list[tuple[dict, int]] = []
    for row, group in zip(rows, raw_group):
        root = find(group)
        final_id.setdefault(root, len(final_id) + 1)
        assigned.append((row, final_id[root]))

    ayah_group = {
        verse_key: final_id[find(next(iter(groups)))]
        for verse_key, groups in groups_of_ayah.items()
    }
    unknown = set(ayah_group) - verse_keys
    missing = verse_keys - set(ayah_group)
    if unknown or missing:
        raise ValueError(f"Sa'di coverage mismatch: unknown={sorted(unknown)[:5]} missing={sorted(missing)[:5]}")
    return assigned, ayah_group


def main() -> None:
    tafsir = read_json(TAFSIR_IN)
    gharib = read_json(GHARIB_IN)
    saadi_rows = read_json(SAADI_ROWS_IN)
    saadi_meta = read_json(SAADI_META_IN)
    if (
        len(tafsir) != EXPECTED_TAFSIR
        or len(gharib) != EXPECTED_GHARIB
        or len(saadi_rows) != EXPECTED_SAADI_ROWS
    ):
        raise ValueError(
            f"Unexpected source counts: tafsir={len(tafsir)}, gharib={len(gharib)}, "
            f"saadi={len(saadi_rows)}"
        )

    tafsir_keys = {
        f"{row['surah_number']}:{row['ayah_number']}" for row in tafsir
    }
    if len(tafsir_keys) != EXPECTED_TAFSIR:
        raise ValueError("Tafsir verse keys are not unique")

    saadi_ids = [int(row["id"]) for row in saadi_rows]
    if saadi_ids != sorted(set(saadi_ids)):
        raise ValueError("Sa'di row ids are not unique and increasing")
    saadi_assigned, saadi_ayah_group = group_saadi(saadi_rows, tafsir_keys)
    saadi_work = saadi_meta["work"]

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    temporary = OUTPUT.with_suffix(".tmp")
    temporary.unlink(missing_ok=True)
    database = sqlite3.connect(temporary)
    try:
        database.executescript(
            f"""
            PRAGMA journal_mode = DELETE;
            PRAGMA synchronous = OFF;
            PRAGMA user_version = {SCHEMA_VERSION};

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

            CREATE TABLE `tafsir_passage` (
                `source_id` TEXT NOT NULL,
                `row_id` INTEGER NOT NULL,
                `group_id` INTEGER NOT NULL,
                `surah_number` INTEGER NOT NULL,
                `from_ayah` INTEGER NOT NULL,
                `to_ayah` INTEGER NOT NULL,
                `kind` TEXT NOT NULL,
                `text` TEXT NOT NULL,
                PRIMARY KEY(`source_id`, `row_id`)
            );
            CREATE INDEX `index_tafsir_passage_source_id_group_id`
                ON `tafsir_passage` (`source_id`, `group_id`);

            CREATE TABLE `tafsir_ayah_group` (
                `source_id` TEXT NOT NULL,
                `verse_key` TEXT NOT NULL,
                `group_id` INTEGER NOT NULL,
                PRIMARY KEY(`source_id`, `verse_key`)
            );
            """
        )

        database.executemany(
            "INSERT INTO content_meta(`key`, `value`) VALUES (?, ?)",
            [
                ("schema_version", str(SCHEMA_VERSION)),
                ("source_package", "qurancomplex_tafsir_gharib_2026-08-14"),
                ("tafsir_title", "التفسير الميسر للقرآن الكريم"),
                ("tafsir_version", "3.0"),
                ("gharib_title", "الميسر في غريب القرآن الكريم"),
                ("gharib_edition", "الطبعة الثانية"),
                ("publisher", "مجمع الملك فهد لطباعة المصحف الشريف"),
                ("saadi_source_package", SAADI_DIR.name),
                ("saadi_title", saadi_work["title_ar"]),
                ("saadi_author", saadi_work["author_ar"]),
                ("saadi_version", saadi_work["version"]),
                ("saadi_last_update", saadi_work["last_update"]),
                ("saadi_source", "موسوعة القرآن الكريم — QuranEnc.com"),
                ("saadi_fetched_at", saadi_meta["fetched_at"]),
            ],
        )
        database.executemany(
            """
            INSERT INTO tafsir_passage(
                source_id, row_id, group_id, surah_number, from_ayah, to_ayah, kind, text
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            [
                (
                    SAADI_SOURCE_ID,
                    int(row["id"]),
                    group_id,
                    int(row["sura"]),
                    int(row["from_aya"]),
                    int(row["to_aya"]),
                    row["type"],
                    row["text"],
                )
                for row, group_id in saadi_assigned
            ],
        )
        database.executemany(
            "INSERT INTO tafsir_ayah_group(source_id, verse_key, group_id) VALUES (?, ?, ?)",
            [
                (SAADI_SOURCE_ID, verse_key, group_id)
                for verse_key, group_id in sorted(saadi_ayah_group.items())
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
            "saadi_rows": database.execute("SELECT COUNT(*) FROM tafsir_passage").fetchone()[0],
            "saadi_groups": database.execute(
                "SELECT COUNT(DISTINCT group_id) FROM tafsir_passage"
            ).fetchone()[0],
            "saadi_ayahs": database.execute("SELECT COUNT(*) FROM tafsir_ayah_group").fetchone()[0],
        }
        if (
            integrity != "ok"
            or counts["tafsir"] != EXPECTED_TAFSIR
            or counts["saadi_rows"] != EXPECTED_SAADI_ROWS
            or counts["saadi_ayahs"] != EXPECTED_TAFSIR
        ):
            raise ValueError(f"Database validation failed: integrity={integrity}, {counts}")
        largest = database.execute(
            """
            SELECT group_id, COUNT(*), SUM(LENGTH(text)) FROM tafsir_passage
            GROUP BY group_id ORDER BY 3 DESC LIMIT 1
            """
        ).fetchone()
        print(f"largest_saadi_group id={largest[0]} rows={largest[1]} chars={largest[2]}")
    finally:
        database.close()

    temporary.replace(OUTPUT)
    print(f"output={OUTPUT}")
    print(f"bytes={OUTPUT.stat().st_size} counts={counts} integrity={integrity}")


if __name__ == "__main__":
    main()
