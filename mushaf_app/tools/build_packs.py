"""Build the downloadable content packs and their catalog.

Run from this folder: python build_packs.py [pack_id ...]
Writes build/packs/<id>-v<version>.db (for checking) and .db.gz (what is published), plus
catalog.json and report.json. Needs rapidfuzz (through saadi_center). Inputs: see pack_books.py.

Publishing is a separate, manual step: the .db.gz files and catalog.json go up as assets of one
GitHub release of the data repository, and the catalog names their download URLs.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

import pack_books as pb
import pack_common as pc

OUT_DIR = pb.ROOT / "build" / "packs"
DATA_REPO = "zahmee/natlu-data"
RELEASE_TAG = "packs-2026.11.08"
CATALOG_SCHEMA = 1
MIN_APP_VERSION_CODE = 58


def build_book(book: pb.Book) -> tuple[Path, dict]:
    raw = pc.read_slim(book.slim_path, book.rows_sha256, book.expected_rows)
    cleaned, stats = pc.clean_rows(
        raw,
        book.corrections,
        book.cuts,
        expected_unclosed=0,
        pre_clean=book.pre_clean,
        whole_despite_length=book.whole_despite_length,
    )
    report: dict = {"notes": dict(sorted(stats.kinds.items())), "corrections": len(book.corrections)}
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
        "license": pc.LICENSE,
        "license_url": pc.LICENSE_URL,
        "source_url": pc.SOURCE_URL,
        "attribution": pc.ATTRIBUTION,
        "source_rows_sha256": book.rows_sha256,
        "snapshot": f"{book.snapshot}@{pb.SNAPSHOT_DATE}",
    }
    output = OUT_DIR / f"{book.pack_id}-v{book.version}.db"

    if book.kind == "irab":
        for (s, a), (text, notes) in cleaned.items():
            pc.check_text(f"{book.pack_id} {s}:{a}", text, notes)
        pc.write_text_pack(output, meta, cleaned)
    else:
        empty = {k for k, (text, _) in cleaned.items() if not text}
        placeholders = set()
        if book.placeholder is not None:
            placeholders = {k for k, (text, _) in cleaned.items() if book.placeholder.match(text) and len(text) < 600}
        if len(placeholders) != book.expected_placeholders:
            raise ValueError(f"{len(placeholders)} placeholder rows, expected {book.expected_placeholders}")
        found = pb.find_redirects(cleaned, empty | placeholders)
        if found != book.redirects:
            raise ValueError(f"redirects differ from the pinned ones: {sorted(found.items())}")
        if not empty <= set(found):
            raise ValueError(f"empty rows nobody explains: {sorted(empty - set(found))}")
        redirect = dict(found)
        report["placeholders_kept"] = len(placeholders - set(found))
        report["redirected"] = len(redirect)
        rows = [
            pc.Row(s, a, a, text, notes)
            for (s, a), (text, notes) in sorted(cleaned.items())
            if (s, a) not in redirect
        ]
        rows, merges = pc.merge_identical(rows)
        if merges != book.expected_merges:
            raise ValueError(f"{merges} rows merged, expected {book.expected_merges}")
        report["merged"] = merges
        report["rows"] = len(rows)
        redirect = pc.extend_to_neighbours(rows, redirect)
        report["redirects_shown_as_passage"] = len(redirect)
        for row in rows:
            pc.check_text(f"{book.pack_id} {row.sura}:{row.from_ayah}", row.text, row.notes)
        group_of = pc.assign_groups(rows, redirect)
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
    return packed, {"entry": entry, "report": report}


def write_docs(entries: dict) -> None:
    """README.md and CORRECTIONS.md for the data repository, published next to the packs."""
    lines = [
        "# Natlu data — downloadable tafsir packs",
        "",
        "Packs the Natlu app (`com.mushaf.reader`) downloads on request from its tafsir library and reads",
        "offline. Each is a gzip-compressed SQLite file; `catalog.json` lists them with their SHA-256.",
        "",
        "## Source and licence",
        "",
        "All texts come from the Tafsir Center for Quranic Studies' dataset",
        f"([tafsircenter/tafsir-mcp-data]({pc.SOURCE_URL}), file `quran.db`), licensed",
        f"[CC BY 4.0]({pc.LICENSE_URL}). Attribution: Tafsir Center for Quranic Studies (https://tafsir.net).",
        "The packs are adapted from it; what was changed is listed in [CORRECTIONS.md](CORRECTIONS.md) and",
        "made by the build tools in the app repository (`mushaf_app/tools/build_packs.py`, `pack_books.py`,",
        "`pack_common.py`). These files carry the same licence.",
        "",
        "| Pack | Book | Version | Download | On device | SHA-256 |",
        "|---|---|---|---|---|---|",
    ]
    for key in pb.BOOKS:
        e = entries.get(key)
        if not e:
            continue
        lines.append(
            f"| `{e['id']}` | {e['work']} | {e['version']} | {e['compressedBytes'] / 1048576:.1f} MB | "
            f"{e['installedBytes'] / 1048576:.1f} MB | `{e['sha256'][:16]}…` |"
        )
    lines += ["", "## Pack format (schema 1)", "", "```", pc.__doc__.split("Schema 1:")[1].split("Cleaning")[0].strip(), "```", ""]
    (OUT_DIR / "README.md").write_text("\n".join(lines), encoding="utf-8")

    out = [
        "# التصحيحات والتعديلات",
        "",
        "كل ما غُيّر في نص مركز تفسير قبل نشر الكتب. التنظيف العام في كل الكتب: حذف الوسوم وعلامات العمل، "
        "وحذف حواشي المحقق إلا التصحيحات وتخريج الأحاديث، ودمج الآيات المتتالية التي تطابق نصها.",
        "",
    ]
    for key, book in pb.BOOKS.items():
        out += [f"## {book.title} (`{book.pack_id}`)", ""]
        if book.corrections:
            out += ["| الآية | النص في المصدر | بعد التصحيح | السبب |", "|---|---|---|---|"]
            for where, wrong, right, reason in book.corrections:
                cell = lambda t: (t.replace("|", "\\|").replace("¬", "«بداية حاشية»").replace("¥", "«نهاية حاشية»") or "—")
                out.append(f"| {where} | {cell(wrong)} | {cell(right)} | {reason} |")
            out.append("")
        if book.cuts:
            out.append("مواضع قُطع نصها في المصدر، ووُضعت في آخرها عبارة تبيّن ذلك: "
                       + "، ".join(f"{s}:{a}" for s, a in sorted(book.cuts)) + ".")
            out.append("")
        if book.redirects:
            out.append("آيات ليس لها في المصدر شرح مستقل، وتُعرض بالموضع الذي يقتبسها ويشرحها: "
                       + "، ".join(f"{s}:{a} ← {t[0]}:{t[1]}" for (s, a), t in sorted(book.redirects.items())) + ".")
            out.append("")
        if book.expected_merges:
            out.append(f"صفوف متتالية متطابقة دُمجت في موضع واحد: {book.expected_merges}.")
            out.append("")
    (OUT_DIR / "CORRECTIONS.md").write_text("\n".join(out), encoding="utf-8")


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    wanted = sys.argv[1:] or list(pb.BOOKS)
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    catalog_path = OUT_DIR / "catalog.json"
    report_path = OUT_DIR / "report.json"
    entries = {}
    reports = {}
    if catalog_path.exists() and wanted != list(pb.BOOKS):
        entries = {e["id"]: e for e in json.loads(catalog_path.read_text(encoding="utf-8"))["packs"]}
        reports = json.loads(report_path.read_text(encoding="utf-8")) if report_path.exists() else {}
    for pack_id in wanted:
        packed, result = build_book(pb.BOOKS[pack_id])
        entries[pack_id] = result["entry"]
        reports[pack_id] = result["report"]
        print(pack_id, json.dumps(result["report"], ensure_ascii=False))
    catalog = {
        "schema": CATALOG_SCHEMA,
        "packs": [entries[k] for k in pb.BOOKS if k in entries],
    }
    catalog_path.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    report_path.write_text(json.dumps(reports, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    write_docs(entries)
    print("catalog", catalog_path)


if __name__ == "__main__":
    main()
