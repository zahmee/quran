"""Draw the app's ayah regions + stored end centres + detected markers over page images.

usage: overlay.py OUTDIR SCALE PAGE[:y0-y1] ...
"""
from __future__ import annotations

import json
import sys
from collections import defaultdict
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

import analyze as A

OUT = A.OUT
PALETTE = [(230, 25, 75), (60, 140, 230), (245, 130, 48), (30, 170, 90), (145, 30, 180),
           (0, 160, 160), (200, 160, 0), (240, 50, 230)]


def font(size):
    for f in ("C:/Windows/Fonts/arialbd.ttf", "C:/Windows/Fonts/arial.ttf"):
        try:
            return ImageFont.truetype(f, size)
        except OSError:
            pass
    return ImageFont.load_default()


def render(page, scale, outdir, crop=None, issues=None):
    regions = json.load(A.REGIONS.open(encoding="utf-8"))
    recs = [r for r in regions["records"] if r["page"] == page]
    info = json.load((OUT / "page_info.json").open()).get(str(page), {})
    base = Image.open(A.PAGES / f"{page}.webp").convert("RGBA")
    layer = Image.new("RGBA", base.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    fnt = font(22)
    # slots
    for s in info.get("slots", []):
        yc = s["yc"]
        if s["kind"] in ("header", "basmala"):
            col = (220, 0, 0, 255) if s["kind"] == "header" else (255, 140, 0, 255)
            d.rectangle([2, yc - 55, base.width - 3, yc + 55], outline=col, width=3)
            d.text((base.width - 150, yc - 54), s["kind"].upper(), fill=col, font=fnt)
    for i, r in enumerate(recs):
        c = PALETTE[i % len(PALETTE)]
        for q in r["rects"]:
            d.rectangle([q["x"], q["y"], q["x"] + q["w"], q["y"] + q["h"]], fill=c + (55,), outline=c + (255,), width=2)
        # stored end centre
        x, y = r["center_x"], r["center_y"]
        d.line([x - 12, y, x + 12, y], fill=(0, 0, 255, 255), width=3)
        d.line([x, y - 12, x, y + 12], fill=(0, 0, 255, 255), width=3)
        q0 = r["rects"][0]
        lx = q0["x"] + q0["w"] - 6
        d.text((lx, q0["y"] + 2), r["verse_key"], fill=c + (255,), font=fnt, anchor="ra",
               stroke_width=3, stroke_fill=(255, 255, 255, 255))
    for m in info.get("markers", []):
        x, y = m["x"], m["y"]
        d.ellipse([x - 29, y - 36, x + 29, y + 36], outline=(0, 170, 0, 255), width=3)
    for it in issues or []:
        if it.get("x0") is not None:
            d.rectangle([it["x0"] - 3, it["yc"] - 42, it["x1"] + 3, it["yc"] + 42], outline=(255, 0, 255, 255), width=4)
        if it.get("mx") is not None and it["code"] == "UNASSIGNED_MARKER":
            d.ellipse([it["mx"] - 40, it["my"] - 46, it["mx"] + 40, it["my"] + 46], outline=(255, 0, 255, 255), width=5)
        if it.get("cx") is not None and it["code"] == "NO_MARKER":
            d.rectangle([it["cx"] - 34, it["cy"] - 40, it["cx"] + 34, it["cy"] + 40], outline=(255, 0, 255, 255), width=5)
        for key in ("rect", "rect2"):
            q = it.get(key)
            if q:
                d.rectangle([q["x"], q["y"], q["x"] + q["w"], q["y"] + q["h"]], outline=(255, 0, 255, 255), width=5)
    img = Image.alpha_composite(base, layer).convert("RGB")
    if crop:
        img = img.crop((0, crop[0], img.width, crop[1]))
    if scale != 1:
        img = img.resize((int(img.width * scale), int(img.height * scale)), Image.LANCZOS)
    hdr = Image.new("RGB", (img.width, 34), (255, 255, 255))
    ImageDraw.Draw(hdr).text((8, 4), f"page {page}" + (f"  y {crop[0]}-{crop[1]}" if crop else ""),
                             fill=(0, 0, 0), font=font(22))
    full = Image.new("RGB", (img.width, img.height + 34), (255, 255, 255))
    full.paste(hdr, (0, 0))
    full.paste(img, (0, 34))
    name = f"p{page:03d}" + (f"_{crop[0]}-{crop[1]}" if crop else "") + ".png"
    outdir.mkdir(parents=True, exist_ok=True)
    full.save(outdir / name)
    return outdir / name


def main():
    outdir = Path(sys.argv[1])
    scale = float(sys.argv[2])
    issues = []
    ip = OUT / "issues.json"
    if ip.exists():
        issues = json.load(ip.open(encoding="utf-8"))
    by_page = defaultdict(list)
    for it in issues:
        by_page[it["page"]].append(it)
    for arg in sys.argv[3:]:
        crop = None
        if ":" in arg:
            pg, rng = arg.split(":")
            y0, y1 = rng.split("-")
            crop = (int(y0), int(y1))
        else:
            pg = arg
        print(render(int(pg), scale, outdir, crop, by_page.get(int(pg))))


if __name__ == "__main__":
    main()
