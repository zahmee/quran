"""Snapshot Tafsir al-Sa'di from the QuranEnc API into a local source package.

The package is the only input build_quran_content_db.py reads for Sa'di, so a database build
never touches the network and two builds from the same package are identical.

Usage:  python mushaf_app/tools/fetch_quranenc_saadi.py [output_dir]
Default output: <repo>/quranenc_saadi_<YYYY-MM-DD>/ (gitignored, like the Complex package).
"""

from __future__ import annotations

import datetime as dt
import hashlib
import json
import sys
import time
import urllib.request
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
API = "https://quranenc.com/api/v1"
KEY = "arabic_saadi"
SURAH_COUNT = 114
REQUIRED_FIELDS = ("id", "sura", "from_aya", "to_aya", "type", "text")
USER_AGENT = "Natlu-content-build/1.0 (+https://github.com/zahmee/quran)"


def get_json(url: str):
    last_error: Exception | None = None
    for attempt in range(4):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(request, timeout=60) as response:
                return json.loads(response.read().decode("utf-8"))
        except Exception as error:  # network hiccup: back off and retry
            last_error = error
            time.sleep(2 * (attempt + 1))
    raise RuntimeError(f"GET {url} failed: {last_error}")


def main() -> None:
    today = dt.date.today().isoformat()
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / f"quranenc_saadi_{today}"
    out.mkdir(parents=True, exist_ok=True)

    listing = get_json(f"{API}/tafsirs/list/ar")
    entry = next((t for t in listing["tafsirs"] if t["key"] == KEY), None)
    if entry is None:
        raise SystemExit(f"{KEY} is not in the QuranEnc tafsir list")

    rows = []
    for surah in range(1, SURAH_COUNT + 1):
        result = get_json(f"{API}/tafsir/sura/{KEY}/{surah}")["result"]
        if not result:
            raise SystemExit(f"Surah {surah} came back empty")
        for row in result:
            missing = [field for field in REQUIRED_FIELDS if field not in row]
            if missing or int(row["sura"]) != surah:
                raise SystemExit(f"Malformed row in surah {surah}: {row.get('id')} {missing}")
            rows.append({field: row[field] for field in REQUIRED_FIELDS})
        time.sleep(0.3)  # be gentle with a free, key-less service

    ids = [int(row["id"]) for row in rows]
    if len(set(ids)) != len(ids) or ids != sorted(ids):
        raise SystemExit("Row ids are not unique and increasing; reading order would be lost")

    rows_path = out / "saadi_rows.json"
    rows_path.write_text(json.dumps(rows, ensure_ascii=False, indent=1), encoding="utf-8")
    digest = hashlib.sha256(rows_path.read_bytes()).hexdigest()

    last_update = dt.datetime.fromtimestamp(int(entry["last_update"]), dt.timezone.utc)
    metadata = {
        "fetched_at": dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds"),
        "source": {
            "name_ar": "موسوعة القرآن الكريم",
            "site": "https://quranenc.com",
            "browse_url": entry.get("browse_url"),
            "api_list": f"{API}/tafsirs/list/ar",
            "api_surah": f"{API}/tafsir/sura/{KEY}/{{sura}}",
            "api_updates": f"{API}/tafsirs/updates?key={KEY}",
        },
        "work": {
            "key": KEY,
            "title_ar": "تيسير الكريم الرحمن في تفسير كلام المنان",
            "short_title_ar": "تفسير السعدي",
            "author_ar": "عبد الرحمن بن ناصر السعدي",
            "version": entry["version"],
            "last_update_unix": int(entry["last_update"]),
            "last_update": last_update.date().isoformat(),
        },
        "terms_summary_ar": [
            "الإشارة إلى الناشر والمصدر (QuranEnc.com)",
            "عدم التعديل أو الإضافة أو الحذف في المحتوى",
            "ذكر رقم الإصدار عند إعادة النشر",
            "التحديث إلى آخر إصدار يصدر من المصدر",
            "إبلاغ المصدر بأي ملحوظة على المحتوى",
            "عدم تضمين إعلانات غير لائقة عند العرض",
        ],
        "row_count": len(rows),
        "files": {"saadi_rows.json": {"bytes": rows_path.stat().st_size, "sha256": digest}},
    }
    (out / "metadata.json").write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(f"output={out} rows={len(rows)} version={entry['version']} sha256={digest}")


if __name__ == "__main__":
    main()
