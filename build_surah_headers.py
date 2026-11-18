"""Find every surah's printed header on the page images, for share images.

Writes:
  mushaf_app/app/src/main/java/com/mushaf/reader/share/SurahHeaders.kt  (the table)
  mushaf_app/app/src/main/assets/surah_headers/{1,2}.webp               (two made headers)

A header is the coloured band nearest above a surah's first ayah, widened to its outer hairline.
Al-Fatiha and al-Baqarah open on the two decorated pages, which print a bare title instead; their
headers are al-Imran's frame with its name lifted out and the page's own title set in, in the red
the other headers use.

Run from the repository root:  python build_surah_headers.py [--review DIR]
--review writes each crop to DIR, to check by eye. Last run 2026-11-18: all 114 checked.
"""

from __future__ import annotations

import json
import statistics
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent
APP = ROOT / "mushaf_app" / "app" / "src" / "main"
PAGES = APP / "assets" / "pages"
REGIONS = APP / "assets" / "data" / "ayah_regions.json"
TABLE_OUT = APP / "java" / "com" / "mushaf" / "reader" / "share" / "SurahHeaders.kt"
ASSETS_OUT = APP / "assets" / "surah_headers"

# A pixel this far from grey is the header's colour; text ink is near-black or grey.
COLOURED = 60
# Rows where this share of pixels is coloured are the band itself.
BAND_ROW = 0.25
# Anything darker than this in every channel is ink: it finds the frame's grey hairline.
INK = 200
# White rows bridged between the band and its outer hairline.
GAP = 4
# The bare titles on the decorated pages (page, box), measured once.
OPENING_TITLES = {1: (1, (432, 349, 674, 420)), 2: (2, (440, 347, 666, 421))}
# They are set smaller than the names in the headers: brought up to their height.
TITLE_SCALE = 1.2


def coloured(p) -> bool:
    return max(p) - min(p) > COLOURED


def find(page_img: Image.Image, first_line: int) -> tuple[int, int, int, int]:
    px = page_img.load()
    w, h = page_img.size

    def band_share(y: int) -> float:
        return sum(1 for x in range(0, w, 2) if coloured(px[x, y])) / (w / 2)

    rows = [y for y in range(first_line - 1, -1, -1) if band_share(y) > BAND_ROW]
    if not rows:
        raise ValueError("no coloured band above the first ayah")
    top = bottom = rows[0]
    while top > 0 and band_share(top - 1) > 0.02:
        top -= 1
    while bottom + 1 < first_line and band_share(bottom + 1) > 0.02:
        bottom += 1

    def has_ink(y: int) -> bool:
        return any(min(px[x, y]) < INK for x in range(w))

    while True:
        up = [y for y in range(top - 1, max(-1, top - 1 - GAP), -1) if has_ink(y)]
        if not up:
            break
        top = min(up)
    while True:
        down = [y for y in range(bottom + 1, min(h, bottom + 1 + GAP)) if has_ink(y)]
        if not down:
            break
        bottom = max(down)
    xs = [x for x in range(w) if any(min(px[x, y]) < INK for y in range(top, bottom + 1))]
    return top, bottom + 1, min(xs), max(xs) + 1


def opening_headers(frame: Image.Image) -> dict[int, Image.Image]:
    fp = frame.load()
    w, h = frame.size
    # The name is the red inside the middle panel; the orange of the flowers either side is not.
    red = [(x, y) for x in range(w // 2 - 190, w // 2 + 190) for y in range(h)
           if fp[x, y][0] > 150 and fp[x, y][1] < 120 and fp[x, y][2] < 100]
    x0, x1 = min(p[0] for p in red) - 3, max(p[0] for p in red) + 4
    y0, y1 = min(p[1] for p in red) - 3, max(p[1] for p in red) + 4
    panel = fp[w // 2, 12]
    ink = tuple(int(statistics.median(fp[x, y][i] for x, y in red if fp[x, y][0] - fp[x, y][1] > 100))
                for i in range(3))
    d = [ink[i] - panel[i] for i in range(3)]

    def on_name(p) -> bool:
        """Whether p is the name's red, or its anti-aliased blend with the panel."""
        t = sum((p[i] - panel[i]) * d[i] for i in range(3)) / sum(v * v for v in d)
        if t <= 0.02:
            return False
        t = min(t, 1.0)
        return sum((p[i] - (panel[i] + t * d[i])) ** 2 for i in range(3)) ** 0.5 < 28

    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    made = {}
    for surah, (page, box) in OPENING_TITLES.items():
        out = frame.copy()
        op = out.load()
        for x in range(x0, x1):
            for y in range(y0, y1):
                if on_name(op[x, y]):
                    op[x, y] = panel
        title = Image.open(PAGES / f"{page}.webp").convert("L").crop(
            (box[0] - 4, box[1] - 4, box[2] + 4, box[3] + 4))
        title = title.resize((round(title.width * TITLE_SCALE), round(title.height * TITLE_SCALE)), Image.LANCZOS)
        tp = title.load()
        tw, th = title.size
        darkest = min(tp[x, y] for x in range(tw) for y in range(th))
        ox, oy = round(cx - tw / 2), round(cy - th / 2)
        for x in range(tw):
            for y in range(th):
                # Its ink is dark grey, not black: the darkest of it counts as full ink.
                a = min(1.0, (255 - tp[x, y]) / (255 - darkest))
                if a <= 0:
                    continue
                p = op[ox + x, oy + y]
                op[ox + x, oy + y] = tuple(round(p[i] * (1 - a) + ink[i] * a) for i in range(3))
        made[surah] = out
    return made


def main() -> None:
    review = Path(sys.argv[sys.argv.index("--review") + 1]) if "--review" in sys.argv else None
    records = json.loads(REGIONS.read_text(encoding="utf-8"))["records"]
    firsts = {r["surah_number"]: r for r in records if r["ayah_number"] == 1}
    boxes = {}
    for surah in range(3, 115):
        r = firsts[surah]
        page = r["page"]
        image = Image.open(PAGES / f"{page}.webp").convert("RGB")
        top, bottom, left, right = find(image, min(x["y"] for x in r["rects"]))
        boxes[surah] = (page, top, bottom, left, right)
        if review:
            review.mkdir(parents=True, exist_ok=True)
            image.crop((left, top, right, bottom)).save(review / f"{surah:03d}.png")
        print(surah, boxes[surah])

    page, top, bottom, left, right = boxes[3]
    frame = Image.open(PAGES / f"{page}.webp").convert("RGB").crop((left, top, right, bottom))
    ASSETS_OUT.mkdir(parents=True, exist_ok=True)
    for surah, image in opening_headers(frame).items():
        image.save(ASSETS_OUT / f"{surah}.webp", lossless=True, quality=100, method=6)
        if review:
            image.save(review / f"{surah:03d}.png")

    rows = "\n".join(f"        {p}, {t}, {b}, {l}, {r}, // {s}" for s, (p, t, b, l, r) in boxes.items())
    TABLE_OUT.write_text(KOTLIN.replace("ROWS", rows), encoding="utf-8", newline="\n")


KOTLIN = '''package com.mushaf.reader.share

/**
 * Where each surah's printed header sits: on the page the surah opens, a band one line tall
 * across the text block. A shared run of ayahs carries its surah's header the way the mushaf
 * does, taken from the page itself rather than drawn anew.
 *
 * Al-Fatiha and al-Baqarah open on the two decorated pages, which print a bare title instead of
 * the framed band. Their headers ship ready-made under `assets/surah_headers/`: al-Imran's frame
 * with its name lifted out and the page's own title set in, in the red the other headers use.
 *
 * Generated by build_surah_headers.py at the repository root; do not edit by hand.
 */
internal object SurahHeaders {

    sealed interface Source

    /** The header's band on [page], in page pixels; [right] and [bottom] exclusive. */
    data class OnPage(val page: Int, val left: Int, val top: Int, val right: Int, val bottom: Int) : Source

    /** A header made in advance, see the object's notes. */
    data class Asset(val path: String) : Source

    fun source(surah: Int): Source {
        require(surah in 1..114) { "no surah $surah" }
        if (surah <= 2) return Asset("surah_headers/$surah.webp")
        val i = (surah - 3) * 5
        return OnPage(
            page = BOXES[i], top = BOXES[i + 1], bottom = BOXES[i + 2],
            left = BOXES[i + 3], right = BOXES[i + 4],
        )
    }

    /** Page, top, bottom, left, right — surah 3 first. */
    private val BOXES = intArrayOf(
ROWS
    )
}
'''

if __name__ == "__main__":
    main()
