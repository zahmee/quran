"""Build production ayah hit/highlight regions for all 604 Madinah pages.

The canonical Quran JSON supplies religious text and division metadata. Marker
centres come from the independently image-verified endpoint file because the
older canonical marker assignment contains missing and cross-ayah markers.
"""

from __future__ import annotations

import json
import csv
import statistics
from collections import defaultdict
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parent
CANONICAL_IN = ROOT / "quran_pages_complete_text_coordinates.json"
VERIFIED_ENDPOINTS_IN = (
    ROOT / "docs" / "audits" / "ayah-boundaries-2026-08-15" /
    "verified_ayah_endpoints_6236.json"
)
AUDIT_IN = (
    ROOT / "docs" / "audits" / "ayah-boundaries-2026-08-15" /
    "ayah_boundary_audit_all_6236.csv"
)
# One set of page images ships since 0.6.0; the dark ones were dropped and the light/ subfolder
# with them, so the pages now sit directly under assets/pages.
PAGE_IMAGES = ROOT / "mushaf_app" / "app" / "src" / "main" / "assets" / "pages"
OUT = ROOT / "mushaf_app" / "app" / "src" / "main" / "assets" / "data" / "ayah_regions.json"

EXPECTED_AYAHS = 6236
EXPECTED_PAGES = 604
LINE_HEIGHT = 118.0
BODY_LEFT = 39.5
BODY_RIGHT = 1066.5
BAND_HEIGHT = 101.5


def load_json(path: Path):
    with path.open(encoding="utf-8") as stream:
        return json.load(stream)


def division(record: dict, key: str) -> dict:
    return ((record.get("division") or {}).get(key) or {})


def clamp(value: float, lower: float, upper: float) -> float:
    return max(lower, min(upper, float(value)))


def page_top_line(endpoints: list[dict]) -> float:
    """Recover this page's first 15-line grid centre (normally around y=78)."""
    phases = []
    for endpoint in endpoints:
        y = float(endpoint["y"])
        phases.append(y - round((y - 80.0) / LINE_HEIGHT) * LINE_HEIGHT)
    return clamp(statistics.median(phases), 65.0, 100.0)


def has_ink_left_of_marker(image: Image.Image, x: float, y: float, left: float) -> bool:
    """Return whether the next ayah continues after a marker on the same line."""
    x0 = max(int(left + 12), 0)
    x1 = min(int(x - 17), image.width)
    y0 = max(int(y - 35), 0)
    y1 = min(int(y + 35), image.height)
    if x1 <= x0 or y1 <= y0:
        return False
    crop = image.crop((x0, y0, x1, y1)).convert("L")
    dark_pixels = sum(1 for pixel in crop.get_flattened_data() if pixel < 105)
    return dark_pixels >= 12


def region_segments(
    start_x: float,
    start_y: float,
    end_x: float,
    end_y: float,
    left: float,
    right: float,
    keep_first_tail: bool,
) -> list[tuple[float, float, float]]:
    if abs(end_y - start_y) < LINE_HEIGHT * 0.5:
        x0, x1 = sorted((start_x, end_x))
        return [(x0, end_y, x1 - x0)] if x1 - x0 > 6 else []

    line_steps = max(1, round((end_y - start_y) / LINE_HEIGHT))
    segments: list[tuple[float, float, float]] = []
    for step in range(line_steps + 1):
        y = start_y + step * (end_y - start_y) / line_steps
        if step == 0:
            if not keep_first_tail:
                continue
            x0, x1 = left, start_x
        elif step == line_steps:
            x0, x1 = end_x, right
        else:
            x0, x1 = left, right
        if x1 - x0 > 6:
            segments.append((x0, y, x1 - x0))
    return segments


def main() -> None:
    canonical_root = load_json(CANONICAL_IN)
    verified_root = load_json(VERIFIED_ENDPOINTS_IN)
    canonical = canonical_root["records"]
    endpoints = verified_root["records"]
    with AUDIT_IN.open(encoding="utf-8-sig", newline="") as stream:
        audit_status = {
            row["verse_key"]: row["start_status"] for row in csv.DictReader(stream)
        }

    if len(canonical) != EXPECTED_AYAHS or len(endpoints) != EXPECTED_AYAHS:
        raise ValueError(
            f"Expected {EXPECTED_AYAHS} canonical records and verified endpoints; "
            f"got {len(canonical)} and {len(endpoints)}"
        )

    canonical_by_key = {record["verse_key"]: record for record in canonical}
    endpoint_keys = [endpoint["verse_key"] for endpoint in endpoints]
    if len(canonical_by_key) != EXPECTED_AYAHS or len(set(endpoint_keys)) != EXPECTED_AYAHS:
        raise ValueError("Verse keys are not unique")
    if set(canonical_by_key) != set(endpoint_keys):
        raise ValueError("Canonical and verified verse-key sets differ")
    if set(audit_status) != set(endpoint_keys):
        raise ValueError("Audit and verified verse-key sets differ")

    by_page: dict[int, list[dict]] = defaultdict(list)
    for endpoint in endpoints:
        by_page[int(endpoint["page"])].append(endpoint)
    if set(by_page) != set(range(1, EXPECTED_PAGES + 1)):
        raise ValueError("Verified endpoints do not cover pages 1..604 exactly")

    records: list[dict] = []
    omitted_blank_tails = 0
    omitted_verified_blank_tails = 0
    omitted_inferred_blank_tails = 0
    for page in range(1, EXPECTED_PAGES + 1):
        page_endpoints = by_page[page]
        top_line = page_top_line(page_endpoints)
        left, right = (185.0, 921.0) if page == 1 else (BODY_LEFT, BODY_RIGHT)
        page_image = Image.open(PAGE_IMAGES / f"{page}.webp")

        for index, endpoint in enumerate(page_endpoints):
            key = endpoint["verse_key"]
            source = canonical_by_key[key]
            end_x, end_y = float(endpoint["x"]), float(endpoint["y"])
            ayah_number = int(endpoint["ayah"])

            if ayah_number == 1:
                if page in (1, 2) or int(endpoint["surah"]) == 9:
                    start_x, start_y = right, end_y
                elif index == 0:
                    # Page-opening surah: title, basmala, then Quran body.
                    start_x, start_y = right, top_line + 2 * LINE_HEIGHT
                else:
                    # Mid-page surah: previous marker, title, basmala, then body.
                    previous = page_endpoints[index - 1]
                    start_x = right
                    start_y = float(previous["y"]) + 3 * LINE_HEIGHT
                keep_first_tail = True
            elif index == 0:
                # Page-boundary inspection found no ayah text continuing from the
                # preceding image; this ayah starts at the first line's right edge.
                start_x, start_y = right, top_line
                keep_first_tail = True
            else:
                previous = page_endpoints[index - 1]
                start_x, start_y = float(previous["x"]), float(previous["y"])
                keep_first_tail = True
                if end_y - start_y >= LINE_HEIGHT * 0.5:
                    verified_blank = (
                        audit_status[key] == "START_ANCHOR_OK_EXTRA_BLANK_TAIL_RECT"
                    )
                    keep_first_tail = not verified_blank and has_ink_left_of_marker(
                        page_image, start_x, start_y, left
                    )
                    if not keep_first_tail:
                        omitted_blank_tails += 1
                        if verified_blank:
                            omitted_verified_blank_tails += 1
                        else:
                            omitted_inferred_blank_tails += 1

            segments = region_segments(
                start_x, start_y, end_x, end_y, left, right, keep_first_tail
            )
            rects = [
                {
                    "x": round(clamp(x, 0, page_image.width)),
                    "y": round(clamp(y - BAND_HEIGHT / 2, 0, page_image.height)),
                    "w": round(clamp(width, 0, page_image.width - x)),
                    "h": round(min(
                        BAND_HEIGHT,
                        page_image.height - clamp(y - BAND_HEIGHT / 2, 0, page_image.height),
                    )),
                }
                for x, y, width in segments
            ]
            if not rects:
                # A very short single-line ayah still needs a reliable touch target.
                rects = [{
                    "x": round(clamp(end_x - 24, 0, page_image.width)),
                    "y": round(clamp(end_y - BAND_HEIGHT / 2, 0, page_image.height)),
                    "w": 48,
                    "h": round(BAND_HEIGHT),
                }]

            sajdah = source.get("sajdah") or division(source, "sajdah") or {}
            records.append({
                "page": page,
                "verse_key": key,
                "surah_number": source["surah_number"],
                "surah_name_ar": source["surah_name_ar"],
                "surah_name_en": source["surah_name_en"],
                "ayah_number": source["ayah_number"],
                "text_uthmani": source.get("ayah_text_uthmani", ""),
                # Standard (imlaa'i) spelling, carried for SEARCH only — never displayed. The
                # Uthmani script writes the long /aa/ as a superscript alef, so a reader typing
                # الملائكة can never match ٱلْمَلَـٰٓئِكَةُ however the marks are folded. See
                # AyahSearchIndex.kt for why the app indexes both.
                "text_imlaei": source.get("ayah_text_simple", ""),
                "juz": division(source, "juz").get("number"),
                "hizb": division(source, "hizb").get("number"),
                "rub": division(source, "rub_el_hizb").get("number"),
                "is_sajdah": bool(sajdah.get("is_sajdah", False)),
                "sajdah_number": sajdah.get("number"),
                "center_x": end_x,
                "center_y": end_y,
                "rects": rects,
            })
        page_image.close()

    if len(records) != EXPECTED_AYAHS:
        raise ValueError(f"Generated {len(records)} regions, expected {EXPECTED_AYAHS}")

    output = {
        "coordinate_space": {"image_width": 1106, "image_height": 1789},
        "params": {
            "left_margin": BODY_LEFT,
            "right_margin": BODY_RIGHT,
            "line_h": LINE_HEIGHT,
            "band_h": BAND_HEIGHT,
            "verified_endpoint_count": EXPECTED_AYAHS,
            "blank_first_line_tails_omitted": omitted_blank_tails,
            "verified_blank_tails_omitted": omitted_verified_blank_tails,
            "inferred_blank_tails_after_anchor_correction": omitted_inferred_blank_tails,
        },
        "records": records,
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with OUT.open("w", encoding="utf-8") as stream:
        json.dump(output, stream, ensure_ascii=False, separators=(",", ":"))

    print(f"records={len(records)} pages={len(by_page)}")
    print(f"blank_first_line_tails_omitted={omitted_blank_tails}")
    print(f"verified={omitted_verified_blank_tails} inferred={omitted_inferred_blank_tails}")
    print(f"output={OUT} bytes={OUT.stat().st_size}")


if __name__ == "__main__":
    main()
