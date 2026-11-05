"""Self-consistent 'OCR' of the red ayah numbers inside every printed marker.

Each marker's red-digit mask is compared with the mean mask of every OTHER marker that the data
labels n-1, n, n+1. If the data's label n is right, the n template must match best.
"""
from __future__ import annotations

import json
from collections import defaultdict

import numpy as np
from PIL import Image

import analyze as A

R = 22  # half crop


def main():
    d = json.load(A.REGIONS.open(encoding="utf-8"))
    feats = json.load((A.OUT / "features.json").open())
    crops = []  # (verse_key, page, n, mask)
    by_page = defaultdict(list)
    for r in d["records"]:
        by_page[r["page"]].append(r)
    for p in range(3, 605):
        a = np.array(Image.open(A.PAGES / f"{p}.webp").convert("RGB")).astype(int)
        red = (a[..., 0] > 140) & (a[..., 1] < 120) & (a[..., 2] < 110) & (a[..., 0] - a[..., 1] > 60)
        ms = feats[str(p)]["markers"]
        for r in by_page[p]:
            m = min(ms, key=lambda m: (m["x"] - r["center_x"]) ** 2 + (m["y"] - r["center_y"]) ** 2)
            cx, cy = int(round(m["x"])), int(round(m["y"]))
            sub = red[cy - R - 3:cy + R + 4, cx - R - 3:cx + R + 4].astype(np.float32)
            if sub.shape != (2 * R + 7, 2 * R + 7):
                continue
            # re-centre on the red ink's bounding box so jitter does not matter
            ys, xs = np.where(sub > 0)
            if len(ys) == 0:
                crops.append((r["verse_key"], p, r["ayah_number"], None))
                continue
            oy = int(round((ys.min() + ys.max()) / 2)) - (R + 3)
            ox = int(round((xs.min() + xs.max()) / 2)) - (R + 3)
            sub = red[cy + oy - R:cy + oy + R + 1, cx + ox - R:cx + ox + R + 1].astype(np.float32)
            crops.append((r["verse_key"], p, r["ayah_number"], sub))
    groups = defaultdict(list)
    for i, (_, _, n, m) in enumerate(crops):
        if m is not None:
            groups[n].append(i)
    sums = {n: sum(crops[i][3] for i in idx) for n, idx in groups.items()}
    res = []
    flagged = []
    for i, (key, p, n, m) in enumerate(crops):
        if m is None:
            flagged.append((key, p, "no red digit found"))
            continue

        def tmpl(k):
            idx = groups.get(k, [])
            cnt = len(idx) - (1 if k == n else 0)
            if cnt <= 0:
                return None
            s = sums[k] - (m if k == n else 0)
            return s / cnt

        def score(t):
            a_ = m - m.mean()
            b_ = t - t.mean()
            den = np.sqrt((a_ * a_).sum() * (b_ * b_).sum())
            return float((a_ * b_).sum() / den) if den else 0.0

        sc = {k: score(t) for k in (n - 1, n, n + 1) if (t := tmpl(k)) is not None}
        if n not in sc:
            res.append((key, p, n, None))
            continue
        best = max(sc, key=sc.get)
        res.append((key, p, n, sc[n]))
        if best != n:
            flagged.append((key, p, f"digits look like {best} (score {sc[best]:.2f}) not {n} ({sc[n]:.2f})"))
        elif sc[n] < 0.55:
            flagged.append((key, p, f"weak match to its own number ({sc[n]:.2f})"))
    checked = [x for x in res if x[3] is not None]
    print("markers", len(crops), "checked", len(checked), "untestable(single sample)", len(res) - len(checked))
    print("min own score", min(x[3] for x in checked), "median", float(np.median([x[3] for x in checked])))
    print("flagged", len(flagged))
    for f in flagged[:60]:
        print(" ", f)
    json.dump({"flagged": flagged, "untestable": [x[0] for x in res if x[3] is None]},
              (A.OUT / "digits.json").open("w", encoding="utf-8"), ensure_ascii=False)


if __name__ == "__main__":
    main()
