"""Locate the printed red hizb stars without changing the Quran page images.

Run from any directory with Pillow and numpy installed. Coordinates are in the
same image space as ayah_regions.json. Only actual division starts are searched;
ayah numerals must never become anchors. Ambiguous matches fail the build.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

import numpy as np
from PIL import Image

ASSETS = Path(__file__).resolve().parents[1] / "app/src/main/assets"
SOURCE = ASSETS / "data/ayah_regions.json"
OUTPUT = ASSETS / "data/hizb_markers.json"
MIN_SCORE = 0.70


def red_mask(page: int) -> np.ndarray:
    rgb = np.asarray(Image.open(ASSETS / f"pages/{page}.webp").convert("RGB"), dtype=np.int16)
    return (rgb[:, :, 0] > 160) & (rgb[:, :, 0] > rgb[:, :, 1] + 50) & (rgb[:, :, 0] > rgb[:, :, 2] + 50)


def generate() -> dict:
    source_bytes = SOURCE.read_bytes()
    data = json.loads(source_bytes)
    # Image-inspected star preceding 2:106, a 24 by 24 pixel red rosette.
    reference = red_mask(17)[76:100, 1058:1082]
    records = []
    without_star = []
    previous_rub = 0
    for ayah in data["records"]:
        if ayah["rub"] == previous_rub:
            continue
        assert ayah["rub"] == previous_rub + 1, "Division metadata is not contiguous"
        previous_rub = ayah["rub"]
        if previous_rub == 1:
            # Al-Fatihah begins the mushaf without a printed hizb star.
            without_star.append(ayah["verse_key"])
            continue
        mask = red_mask(ayah["page"])
        first = ayah["rects"][0]
        right = first["x"] + first["w"]
        center = first["y"] + first["h"] / 2
        x0, x1 = max(0, int(right - 160)), min(mask.shape[1], int(right + 65))
        y0, y1 = max(0, int(center - 80)), min(mask.shape[0], int(center + 90))
        windows = np.lib.stride_tricks.sliding_window_view(mask[y0:y1, x0:x1], reference.shape)
        intersection = (windows & reference).sum(axis=(-1, -2))
        union = (windows | reference).sum(axis=(-1, -2))
        scores = intersection / np.maximum(union, 1)
        y, x = np.unravel_index(np.argmax(scores), scores.shape)
        score = float(scores[y, x])
        if score < MIN_SCORE:
            # Many divisions coincide with a surah heading, with no separate star.
            # Keep the existing page-level information for these instead of guessing.
            if ayah["ayah_number"] == 1:
                without_star.append(ayah["verse_key"])
                continue
            raise ValueError(f"Unverified star at {ayah['verse_key']}: {score:.3f}")
        records.append({
            "page": ayah["page"],
            "verse_key": ayah["verse_key"],
            "juz": ayah["juz"],
            "hizb": ayah["hizb"],
            "rub": ayah["rub"],
            "x": int(x + x0),
            "y": int(y + y0),
            "w": reference.shape[1],
            "h": reference.shape[0],
            "match_score": round(score, 4),
            "image_sha256": hashlib.sha256((ASSETS / f"pages/{ayah['page']}.webp").read_bytes()).hexdigest(),
        })
    assert previous_rub == 240
    assert len(records) + len(without_star) == 240
    return {
        "coordinate_space": data["coordinate_space"],
        "source_sha256": hashlib.sha256(source_bytes).hexdigest(),
        "reference": {"page": 17, "x": 1058, "y": 76, "w": 24, "h": 24},
        "division_starts_without_star": without_star,
        "records": records,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Verify the committed coordinates without writing")
    args = parser.parse_args()
    data = generate()
    encoded = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
    if args.check:
        if OUTPUT.read_text(encoding="utf-8") != encoded:
            raise SystemExit("Hizb marker coordinates need regeneration")
    else:
        OUTPUT.write_text(encoded, encoding="utf-8")
    print(f"Validated {len(data['records'])} printed stars; {len(data['division_starts_without_star'])} starts without a star")
    print(f"Lowest accepted score: {min(r['match_score'] for r in data['records']):.3f}")


if __name__ == "__main__":
    main()
