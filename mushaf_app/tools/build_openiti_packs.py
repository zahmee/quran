"""Build the downloadable packs taken from OpenITI, and the catalog that lists them with the others.

Run from this folder: python build_openiti_packs.py [pack_id ...]
Reads the pinned OpenITI files in openiti_2025-1-9/ at the repo root (gitignored; openiti_tafsir.py
names each file, the commit it was taken at and its SHA-256). Writes build/packs/<id>-v<version>.db and
.db.gz, OPENITI.md, and build/packs/catalog.json: the shipped catalog's packs — the Tafsir Center's,
built by build_packs.py on the machine that holds their sources — with these added or replaced.

These packs carry their own licence, CC BY-NC-SA 4.0, and credit OpenITI and altafsir.com.
Publishing is the same manual step as for the others: the new .db.gz files go up as assets of a new
GitHub release of the data repository, with the new catalog.json and OPENITI.md.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

import openiti_tafsir as ot
import pack_common as pc

OUT_DIR = ot.ROOT / "mushaf_app" / "build" / "packs"
DATA_REPO = "zahmee/natlu-data"
RELEASE_TAG = "packs-2026.11.13"
CATALOG_SCHEMA = 1
# Tafsir packs of schema 1 open in every version that has the library.
MIN_APP_VERSION_CODE = 58
SHIPPED_CATALOG = ot.ROOT / "mushaf_app" / "app" / "src" / "main" / "assets" / "packs" / "catalog.json"
# The order the catalog lists packs in; the app sorts by its own PACK_ORDER anyway.
ORDER = ["ibn_kathir", "tabari", "baghawi", "qurtubi", "shawkani", "ibn_ashur", "irab"]


def build_book(book: ot.OpenItiBook, names: ot.Surahs) -> tuple[dict, dict]:
    rows, report = ot.passages(book, names)
    meta = {
        "pack_id": book.pack_id,
        "kind": book.kind,
        "version": str(book.version),
        "schema": str(pc.PACK_SCHEMA),
        "title": book.title,
        "work": book.work,
        "author": book.author,
        "credit": book.credit,
        "changes": book.changes,
        "license": ot.LICENSE,
        "license_url": ot.LICENSE_URL,
        "source_url": book.source_url,
        "attribution": ot.ATTRIBUTION,
        "corpus_url": ot.CORPUS_URL,
        "source_rows_sha256": book.file_sha256,
        "snapshot": f"OpenITI/{book.repository}@{book.commit}",
    }
    output = OUT_DIR / f"{book.pack_id}-v{book.version}.db"
    group_of = pc.assign_groups(rows, {})
    pc.write_tafsir_pack(output, meta, rows, group_of)
    pc.verify_pack(output)
    packed = output.with_name(output.name + ".gz")
    pc.gzip_deterministic(output, packed)
    entry = {
        "id": book.pack_id,
        "kind": book.kind,
        "version": book.version,
        "schema": pc.PACK_SCHEMA,
        "title": book.title,
        "work": book.work,
        "author": book.author,
        "url": f"https://github.com/{DATA_REPO}/releases/download/{RELEASE_TAG}/{packed.name}",
        "sha256": pc.sha256_file(packed),
        "compressedBytes": packed.stat().st_size,
        "installedBytes": output.stat().st_size,
        "minAppVersionCode": MIN_APP_VERSION_CODE,
    }
    report.update({"installed_bytes": entry["installedBytes"], "compressed_bytes": entry["compressedBytes"]})
    return entry, report


def write_doc(books: list[ot.OpenItiBook]) -> None:
    lines = [
        "# Packs from OpenITI",
        "",
        "These packs are built from the OpenITI corpus (release 2025.1.9,",
        f"[{ot.CORPUS_URL}]({ot.CORPUS_URL})), licensed [{ot.LICENSE}]({ot.LICENSE_URL}). The texts are",
        "OpenITI's versions taken from altafsir.com, already divided by the ayahs they explain, with",
        "introductions and footnotes removed. The packs are adapted from them by",
        "`mushaf_app/tools/build_openiti_packs.py` and `openiti_tafsir.py` in the app repository, and carry",
        "the same licence. They are used in a free app with no advertising and nothing for sale.",
        "",
        "| Pack | Book | Author | Source file (pinned commit) |",
        "|---|---|---|---|",
    ]
    for b in books:
        lines.append(f"| `{b.pack_id}` | {b.work} | {b.author} | [{b.file}]({b.source_url}) |")
    lines += ["", "## ما غُيّر", ""]
    for b in books:
        lines += [f"- **{b.title}:** {b.changes}"]
    (OUT_DIR / "OPENITI.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    wanted = sys.argv[1:] or list(ot.BOOKS)
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    catalog_path = OUT_DIR / "catalog.json"
    base = catalog_path if catalog_path.exists() else SHIPPED_CATALOG
    entries = {e["id"]: e for e in json.loads(base.read_text(encoding="utf-8"))["packs"]}
    names = ot.surah_names()
    reports = {}
    for pack_id in wanted:
        entry, report = build_book(ot.BOOKS[pack_id], names)
        entries[pack_id] = entry
        reports[pack_id] = report
        print(pack_id, json.dumps({k: v for k, v in report.items() if k != "not_explained"}, ensure_ascii=False),
              "not explained:", report["not_explained"])
    order = ORDER + sorted(set(entries) - set(ORDER))
    catalog = {"schema": CATALOG_SCHEMA, "packs": [entries[k] for k in order if k in entries]}
    catalog_path.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (OUT_DIR / "openiti_report.json").write_text(json.dumps(reports, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    write_doc(list(ot.BOOKS.values()))
    print("catalog", catalog_path)


if __name__ == "__main__":
    main()
