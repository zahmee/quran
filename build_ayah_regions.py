# Builds enriched per-ayah data + approximate highlight regions from the marker-only
# coordinates JSON (quran_pages_complete_text_coordinates.json), for pages 1..20.
#
# A verse's region spans from the PREVIOUS ayah's marker (start) to its OWN marker (end),
# laid out RTL across the lines between them. Region geometry is approximate because the
# source only has the ayah marker / ayah_end (== marker center), no real glyph bounds.

import json, statistics
from collections import defaultdict

JSON_IN = r"D:\new project\quran_01\quran_pages_complete_text_coordinates.json"
OUT = r"D:\new project\quran_01\mushaf_app\app\src\main\assets\data\ayah_regions.json"
EMIT_PAGES = set(range(1, 605))  # all pages

data = json.load(open(JSON_IN, encoding="utf-8"))
cs = data.get("coordinate_space", {})
IMG_W = cs.get("image_width", 1106)
IMG_H = cs.get("image_height", 1789)
recs = data["records"]


def marker(r):
    m = r.get("ayah_marker")
    if not m:
        return None
    cx, cy = m.get("center_x"), m.get("center_y")
    if cx is None or cy is None:
        return None
    return float(cx), float(cy)


def div(r, key):
    return ((r.get("division") or {}).get(key) or {})


all_m = [m for m in (marker(r) for r in recs) if m]
mxs = sorted(m[0] for m in all_m)
left_margin = max(14.0, min(mxs[int(len(mxs) * 0.01)], 45.0))
right_margin = IMG_W - left_margin

by_page = defaultdict(list)
for r in recs:
    by_page[r["page"]].append(r)

gaps = []
for lst in by_page.values():
    ys = sorted({round(marker(r)[1]) for r in lst if marker(r)})
    for a, b in zip(ys, ys[1:]):
        if 50 <= b - a <= 200:
            gaps.append(b - a)
line_h = statistics.median(gaps) if gaps else 118.0
top_line = min(m[1] for m in all_m)
band_h = line_h * 0.86


def clampx(v): return max(0.0, min(float(IMG_W), v))
def clampy(v): return max(0.0, min(float(IMG_H), v))


def rects_for(sx, sy, ex, ey, lm, rm):
    if abs(ey - sy) < line_h * 0.5:
        x0, x1 = min(sx, ex), max(sx, ex)
        return [(x0, ey, x1 - x0)]
    n = max(1, round((ey - sy) / line_h))
    out = []
    for k in range(n + 1):
        y = sy + k * (ey - sy) / n
        if k == 0:
            x0, x1 = lm, sx
        elif k == n:
            x0, x1 = ex, rm
        else:
            x0, x1 = lm, rm
        if x1 - x0 > 6:
            out.append((x0, y, x1 - x0))
    return out


records = []
for p in sorted(by_page):
    if p not in EMIT_PAGES:
        continue
    lm, rm = (185.0, 921.0) if p == 1 else (left_margin, right_margin)
    lst = [r for r in by_page[p] if marker(r)]
    for i, r in enumerate(lst):
        ex, ey = marker(r)
        if i == 0:
            sx, sy = (rm, ey) if r["ayah_number"] == 1 else (rm, top_line)
        else:
            sx, sy = marker(lst[i - 1])
        rects = [
            {"x": round(clampx(x)), "y": round(clampy(yc - band_h / 2)),
             "w": round(w), "h": round(band_h)}
            for (x, yc, w) in rects_for(sx, sy, ex, ey, lm, rm)
        ]
        saj = (r.get("sajdah") or div(r, "sajdah") or {})
        records.append({
            "page": p,
            "verse_key": r["verse_key"],
            "surah_number": r["surah_number"],
            "surah_name_ar": r["surah_name_ar"],
            "surah_name_en": r["surah_name_en"],
            "ayah_number": r["ayah_number"],
            "text_uthmani": r.get("ayah_text_uthmani", ""),
            "juz": div(r, "juz").get("number"),
            "hizb": div(r, "hizb").get("number"),
            "rub": div(r, "rub_el_hizb").get("number"),
            "is_sajdah": bool(saj.get("is_sajdah", False)),
            "sajdah_number": saj.get("number"),
            "center_x": ex,
            "center_y": ey,
            "rects": rects,
        })

out = {
    "coordinate_space": {"image_width": IMG_W, "image_height": IMG_H},
    "params": {"left_margin": left_margin, "right_margin": right_margin,
               "line_h": line_h, "top_line": top_line, "band_h": round(band_h, 1)},
    "records": records,
}
with open(OUT, "w", encoding="utf-8") as f:
    json.dump(out, f, ensure_ascii=False)

sajda = [r["verse_key"] for r in records if r["is_sajdah"]]
print("records:", len(records))
print("sajdah ayahs in 1-20:", sajda)
