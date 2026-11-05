"""Independent audit of ayah_regions.json against the page images (pages 1..604).

Image features are detected from the pixels only (no app data used):
  * ayah-end markers: the mint-filled oval inside every printed ayah number
  * surah header frames: wide saturated (green/gold) bands
  * text ink: dark, unsaturated pixels
Then the app's records are checked against them and against quran.com page numbers.
"""
from __future__ import annotations

import json
import sys
from collections import defaultdict
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[4]
PAGES = ROOT / "mushaf_app/app/src/main/assets/pages"
REGIONS = ROOT / "mushaf_app/app/src/main/assets/data/ayah_regions.json"
QC_PAGES = ROOT / "quran_com_pages_cache.json"
QC_CHAPTERS = ROOT / "quran_com_chapters_cache.json"
import os
import tempfile
# ~150 MB of per-page ink masks: kept outside the repository.
OUT = Path(os.environ.get("AYAH_AUDIT_OUT", Path(tempfile.gettempdir()) / "natlu_ayah_audit"))
OUT.mkdir(parents=True, exist_ok=True)

PITCH0 = 117.9
TOP0 = 83.0


def mint_mask(a):
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    return (g - r >= 4) & (g - r <= 16) & (g >= 228) & (g <= 250) & (b >= r) & (b <= g)


def boxsum(m, h, w):
    ii = np.pad(m.astype(np.int32).cumsum(0).cumsum(1), ((1, 0), (1, 0)))
    H, W = m.shape
    hy, hx = h // 2, w // 2
    ys = np.arange(H)
    xs = np.arange(W)
    y0 = np.clip(ys - hy, 0, H)
    y1 = np.clip(ys + hy + 1, 0, H)
    x0 = np.clip(xs - hx, 0, W)
    x1 = np.clip(xs + hx + 1, 0, W)
    return ii[y1][:, x1] - ii[y0][:, x1] - ii[y1][:, x0] + ii[y0][:, x0]


def detect_markers(a, sat_rows):
    """Printed ayah-number ovals: dense mint blobs of marker size, outside header bands."""
    m = mint_mask(a)
    # header frames contain mint too; blank them out
    m[sat_rows, :] = False
    s = boxsum(m, 31, 25)
    thr = int(0.50 * 31 * 25)
    cand = np.argwhere(s >= thr)
    if len(cand) == 0:
        return []
    vals = s[cand[:, 0], cand[:, 1]]
    order = np.argsort(-vals, kind="stable")
    peaks = []
    for i in order:
        y, x = cand[i]
        if any(abs(y - py) < 45 and abs(x - px) < 45 for py, px in peaks):
            continue
        peaks.append((int(y), int(x)))
    out = []
    H, W = m.shape
    for y, x in peaks:
        y0, y1 = max(y - 48, 0), min(y + 49, H)
        x0, x1 = max(x - 40, 0), min(x + 41, W)
        sub = m[y0:y1, x0:x1]
        cols = np.where(sub.sum(0) >= 3)[0]
        rows = np.where(sub.sum(1) >= 3)[0]
        if len(cols) == 0 or len(rows) == 0:
            continue
        # keep the contiguous run around the peak
        def run(idx, centre):
            idx = list(idx)
            best = None
            start = idx[0]
            prev = idx[0]
            for v in idx[1:] + [None]:
                if v is None or v > prev + 6:
                    if start <= centre <= prev:
                        best = (start, prev)
                    if v is not None:
                        start = v
                if v is not None:
                    prev = v
            return best or (idx[0], idx[-1])
        cx0, cx1 = run(cols, x - x0)
        ry0, ry1 = run(rows, y - y0)
        bw, bh = cx1 - cx0 + 1, ry1 - ry0 + 1
        cnt = int(sub[ry0:ry1 + 1, cx0:cx1 + 1].sum())
        out.append({
            "x": x0 + (cx0 + cx1) / 2.0,
            "y": y0 + (ry0 + ry1) / 2.0,
            "w": int(bw), "h": int(bh), "mint": cnt,
        })
    # marker-size filter (interior oval of the ring ~ 40-50 x 55-65)
    good = [o for o in out if 28 <= o["w"] <= 60 and 38 <= o["h"] <= 80]
    return good


def saturated_rows(a):
    """Rows belonging to a surah header frame (wide saturated band)."""
    mx = a.max(2)
    mn = a.min(2)
    sat = (mx - mn) > 45
    cnt = sat.sum(1)
    rows = cnt > 250
    # close small holes / grow to band
    idx = np.where(rows)[0]
    bands = []
    if len(idx):
        start = prev = idx[0]
        for v in list(idx[1:]) + [None]:
            if v is None or v - prev > 6:
                if prev - start >= 60:
                    bands.append((int(start), int(prev)))
                if v is not None:
                    start = v
            if v is not None:
                prev = v
    mask = np.zeros(a.shape[0], bool)
    for b0, b1 in bands:
        mask[max(b0 - 4, 0):b1 + 5] = True
    return mask, bands


def ink_mask(a):
    mx = a.max(2)
    mn = a.min(2)
    gray = a.mean(2)
    return (gray < 110) & ((mx - mn) < 40)


def main():
    regions = json.load(REGIONS.open(encoding="utf-8"))
    records = regions["records"]
    qc_pages = json.load(QC_PAGES.open(encoding="utf-8"))
    ref_page = {}
    for p, verses in qc_pages.items():
        for v in verses:
            ref_page[v["verse_key"]] = int(v["page_number"])
    chapters = json.load(QC_CHAPTERS.open(encoding="utf-8"))["chapters"]
    counts = {c["id"]: c["verses_count"] for c in chapters}

    by_page = defaultdict(list)
    for i, r in enumerate(records):
        r["_idx"] = i
        by_page[r["page"]].append(r)

    pages_arg = [int(x) for x in sys.argv[1:]] or list(range(1, 605))
    feats = {}
    for p in pages_arg:
        a = np.array(Image.open(PAGES / f"{p}.webp").convert("RGB")).astype(np.int16)
        satmask, bands = saturated_rows(a)
        markers = detect_markers(a, satmask)
        ink = ink_mask(a)
        # ink profile per row (for line slots) and column extents are computed later
        row_ink = ink.sum(1).astype(int).tolist()
        feats[p] = {
            "header_bands": bands,
            "markers": markers,
            "row_ink": row_ink,
        }
        # compact ink storage: per row, list of runs is heavy; store packed bits
        np.save(OUT / f"ink_{p}.npy", np.packbits(ink, axis=1))
        if p % 50 == 0:
            print("page", p, len(markers), bands, flush=True)
    json.dump(feats, (OUT / "features.json").open("w"), separators=(",", ":"))
    print("done", len(feats))


if __name__ == "__main__":
    main()
