"""Run every check on ayah_regions.json using the image features from analyze.py."""
from __future__ import annotations

import csv
import json
import math
from collections import defaultdict, Counter
from pathlib import Path

import numpy as np
from PIL import Image

import analyze as A
import os
TAG = os.environ.get("CHECK_TAG", "")
if os.environ.get("CHECK_REGIONS"):
    A.REGIONS = Path(os.environ["CHECK_REGIONS"])

OUT = A.OUT
W, H = 1106, 1789


def load_ink(p):
    return np.unpackbits(np.load(OUT / f"ink_{p}.npy"), axis=1)[:, :W].astype(bool)


def red_blobs(p):
    """Pages 1-2 use white-filled markers: find their red digits instead."""
    a = np.array(Image.open(A.PAGES / f"{p}.webp").convert("RGB")).astype(int)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    red = (r > 150) & (g < 110) & (b < 110)
    pts = np.argwhere(red)
    clusters = []
    for y, x in pts:
        for c in clusters:
            if abs(c["y"] - y) < 25 and abs(c["x"] - x) < 25:
                c["ys"].append(y); c["xs"].append(x)
                c["y"] = sum(c["ys"]) / len(c["ys"]); c["x"] = sum(c["xs"]) / len(c["xs"])
                break
        else:
            clusters.append({"y": y, "x": x, "ys": [y], "xs": [x]})
    out = []
    for c in clusters:
        if len(c["ys"]) >= 8:
            out.append({"x": (min(c["xs"]) + max(c["xs"])) / 2, "y": (min(c["ys"]) + max(c["ys"])) / 2,
                        "w": 0, "h": 0, "mint": len(c["ys"])})
    return out


def runs(mask, merge=3):
    idx = np.where(mask)[0]
    res = []
    if len(idx) == 0:
        return res
    s = p = idx[0]
    for v in list(idx[1:]) + [None]:
        if v is None or v > p + merge:
            res.append((int(s), int(p)))
            if v is not None:
                s = v
        if v is not None:
            p = v
    return res


def main():
    regions = json.load(A.REGIONS.open(encoding="utf-8"))
    records = regions["records"]
    feats = json.load((OUT / "features.json").open())
    qc = json.load(A.QC_PAGES.open(encoding="utf-8"))
    ref_page = {v["verse_key"]: int(v["page_number"]) for vs in qc.values() for v in vs}
    ref_order = [v["verse_key"] for p in sorted(qc, key=int) for v in qc[p]]
    chapters = json.load(A.QC_CHAPTERS.open(encoding="utf-8"))["chapters"]
    counts = {c["id"]: c["verses_count"] for c in chapters}

    issues = []

    def issue(page, key, sev, code, msg, **kw):
        issues.append({"page": page, "verse_key": key, "severity": sev, "code": code, "message": msg, **kw})

    # ---------- G: global sequence ----------
    keys = [r["verse_key"] for r in records]
    if len(records) != 6236:
        issue(0, "", "ERROR", "COUNT", f"records={len(records)}")
    dup = [k for k, c in Counter(keys).items() if c > 1]
    for k in dup:
        issue(0, k, "ERROR", "DUP", "duplicate verse key")
    canon = [f"{s}:{a}" for s in range(1, 115) for a in range(1, counts[s] + 1)]
    if keys != canon:
        for i, (k1, k2) in enumerate(zip(keys, canon)):
            if k1 != k2:
                issue(records[i]["page"], k1, "ERROR", "ORDER", f"expected {k2} at position {i}")
                break
    if canon != ref_order:
        issue(0, "", "ERROR", "REF", "quran.com order differs from canonical")
    for r in records:
        s, a = r["surah_number"], r["ayah_number"]
        if r["verse_key"] != f"{s}:{a}":
            issue(r["page"], r["verse_key"], "ERROR", "KEY", f"key vs surah/ayah fields {s}:{a}")
        if ref_page.get(r["verse_key"]) != r["page"]:
            issue(r["page"], r["verse_key"], "ERROR", "PAGE", f"quran.com page {ref_page.get(r['verse_key'])}")
    pages_seq = [r["page"] for r in records]
    if pages_seq != sorted(pages_seq):
        issue(0, "", "ERROR", "PAGE_ORDER", "pages not monotonic")
    by_page = defaultdict(list)
    for r in records:
        by_page[r["page"]].append(r)
    for p in range(1, 605):
        if not by_page[p]:
            issue(p, "", "ERROR", "EMPTY_PAGE", "no records")
            continue
        refs = qc[str(p)]
        if by_page[p][0]["verse_key"] != refs[0]["verse_key"]:
            issue(p, by_page[p][0]["verse_key"], "ERROR", "PAGE_FIRST", f"ref first {refs[0]['verse_key']}")
        if by_page[p][-1]["verse_key"] != refs[-1]["verse_key"]:
            issue(p, by_page[p][-1]["verse_key"], "ERROR", "PAGE_LAST", f"ref last {refs[-1]['verse_key']}")

    # ---------- P: per-page image checks ----------
    page_info = {}
    stats = Counter()
    for p in range(1, 605):
        recs = by_page[p]
        f = feats[str(p)]
        markers = f["markers"] if p > 2 else red_blobs(p)
        bands = f["header_bands"]
        ink = load_ink(p)

        # --- match record centres to printed markers (one-to-one, greedy by distance)
        pairs = []
        for i, r in enumerate(recs):
            for j, m in enumerate(markers):
                d = math.hypot(r["center_x"] - m["x"], r["center_y"] - m["y"])
                if d < 40:
                    pairs.append((d, i, j))
        pairs.sort()
        rec_to_m, m_to_rec = {}, {}
        for d, i, j in pairs:
            if i in rec_to_m or j in m_to_rec:
                continue
            rec_to_m[i] = (j, d)
            m_to_rec[j] = i
        for i, r in enumerate(recs):
            if i not in rec_to_m:
                issue(p, r["verse_key"], "ERROR", "NO_MARKER",
                      "stored end centre is not on any printed ayah marker",
                      cx=r["center_x"], cy=r["center_y"])
            else:
                j, d = rec_to_m[i]
                stats["matched"] += 1
                if d > 9:
                    issue(p, r["verse_key"], "WARN", "MARKER_OFFSET",
                          f"stored centre {d:.1f}px from printed marker centre",
                          cx=r["center_x"], cy=r["center_y"], mx=markers[j]["x"], my=markers[j]["y"])
        for j, m in enumerate(markers):
            if j not in m_to_rec:
                issue(p, "", "ERROR", "UNASSIGNED_MARKER", "printed ayah marker with no ayah ending at it",
                      mx=m["x"], my=m["y"])
        stats["markers_detected"] += len(markers)

        if p <= 2:
            page_info[p] = {"slots": [], "markers": markers}
            # overlap check still applies
        # --- line grid (pages 3..604)
        slots = []
        if p > 2:
            ys = sorted(m["y"] for m in markers)
            top, pitch = A.TOP0 + 2.5, A.PITCH0
            for _ in range(3):
                ks = [round((y - top) / pitch) for y in ys]
                if len(set(ks)) >= 2:
                    kk = np.array(ks, float); yy = np.array(ys, float)
                    pitch, top = np.polyfit(kk, yy, 1)
                else:
                    top = float(np.median([y - k * pitch for y, k in zip(ys, ks)]))
            if not (115 <= pitch <= 121):
                issue(p, "", "WARN", "GRID", f"odd line pitch {pitch:.1f}")
                pitch = A.PITCH0
            for k in range(15):
                yc = top + k * pitch
                r0, r1 = int(max(yc - 40, 0)), int(min(yc + 40, H))
                inkc = int(ink[r0:r1].sum())
                hdr = any(b0 <= yc + 30 and b1 >= yc - 30 for b0, b1 in bands)
                mk = [m for m in markers if abs(m["y"] - yc) < 35]
                slots.append({"k": k, "yc": float(yc), "ink": inkc, "header": hdr, "n_markers": len(mk)})
            for k, s in enumerate(slots):
                if s["header"]:
                    s["kind"] = "header"
                elif k > 0 and slots[k - 1]["header"] and s["ink"] > 300 and s["n_markers"] == 0:
                    s["kind"] = "basmala"
                elif s["ink"] > 300:
                    s["kind"] = "text"
                else:
                    s["kind"] = "empty"
            page_info[p] = {"slots": slots, "top": float(top), "pitch": float(pitch), "markers": markers}

            def slot_of(y):
                k = int(round((y - top) / pitch))
                return k if 0 <= k < 15 and abs(top + k * pitch - y) < 30 else None

            # --- reading order of printed markers vs record order
            seq = []
            for j, m in enumerate(markers):
                if j in m_to_rec:
                    seq.append((slot_of(m["y"]) if slot_of(m["y"]) is not None else 99, -m["x"], m_to_rec[j]))
            seq.sort()
            idxs = [t[2] for t in seq]
            if idxs != sorted(idxs):
                for a_, b_ in zip(idxs, idxs[1:]):
                    if b_ < a_:
                        issue(p, recs[b_]["verse_key"], "ERROR", "READING_ORDER",
                              f"marker order puts {recs[a_]['verse_key']} before {recs[b_]['verse_key']}")

            # --- surah headers in the image vs surah starts in data
            n_hdr = sum(1 for s in slots if s["kind"] == "header")
            starts = [r for r in recs if r["ayah_number"] == 1]
            if n_hdr != len(starts):
                issue(p, ",".join(r["verse_key"] for r in starts), "ERROR", "HEADER_COUNT",
                      f"image shows {n_hdr} surah header(s), data starts {len(starts)} surah(s) here")

            # --- rect geometry
            for r in recs:
                for q in r["rects"]:
                    yc = q["y"] + q["h"] / 2
                    k = slot_of(yc)
                    if k is None:
                        k2 = int(round((yc - top) / pitch))
                        off = yc - (top + k2 * pitch)
                        issue(p, r["verse_key"], "WARN", "RECT_OFF_LINE",
                              f"highlight band centre y={yc:.0f} is {off:+.0f}px off the nearest text line",
                              rect=q)
                        continue
                    s = slots[k]
                    if s["kind"] in ("header", "basmala"):
                        issue(p, r["verse_key"], "ERROR", "COVERS_" + s["kind"].upper(),
                              f"highlight covers the surah {s['kind']} line (slot {k + 1})", rect=q)
                    elif s["kind"] == "empty":
                        issue(p, r["verse_key"], "WARN", "COVERS_EMPTY_LINE",
                              f"highlight on a line with no text (slot {k + 1})", rect=q)
                    # ink inside the rect
                    r0, r1 = int(max(yc - 40, 0)), int(min(yc + 40, H))
                    x0, x1 = int(q["x"]), int(q["x"] + q["w"])
                    inside = ink[r0:r1, x0:x1]
                    if inside.sum() < 40:
                        issue(p, r["verse_key"], "WARN", "BLANK_RECT",
                              f"highlight band over blank paper on slot {k + 1} (x {x0}-{x1})", rect=q)
                    else:
                        cols = np.where(inside.sum(0) > 0)[0]
                        lead = x1 - (x0 + cols.max())
                        trail = cols.min()
                        if lead > 45 or trail > 45:
                            issue(p, r["verse_key"], "INFO", "RECT_WIDER_THAN_TEXT",
                                  f"band reaches {trail}px left / {lead}px right of the ink on slot {k + 1}",
                                  rect=q)

            # --- overlaps between different ayahs
            allrects = [(r["verse_key"], q) for r in recs for q in r["rects"]]
            for i in range(len(allrects)):
                k1, a1 = allrects[i]
                for j in range(i + 1, len(allrects)):
                    k2, a2 = allrects[j]
                    if k1 == k2:
                        continue
                    ow = min(a1["x"] + a1["w"], a2["x"] + a2["w"]) - max(a1["x"], a2["x"])
                    oh = min(a1["y"] + a1["h"], a2["y"] + a2["h"]) - max(a1["y"], a2["y"])
                    if ow > 2 and oh > 10:
                        issue(p, f"{k1}|{k2}", "ERROR", "OVERLAP",
                              f"{k1} and {k2} overlap by {ow}x{oh}px", rect=a1, rect2=a2)

            # --- uncovered ink on text lines (gaps)
            for s in slots:
                if s["kind"] != "text":
                    continue
                yc = s["yc"]
                r0, r1 = int(max(yc - 38, 0)), int(min(yc + 38, H))
                cov = np.zeros(W, bool)
                for r in recs:
                    for q in r["rects"]:
                        top_, bot_ = q["y"], q["y"] + q["h"]
                        if top_ <= yc - 25 and bot_ >= yc + 25:
                            cov[int(math.floor(q["x"])):int(math.ceil(q["x"] + q["w"])) + 1] = True
                colink = ink[r0:r1].sum(0)
                unc = (colink > 0) & ~cov
                for a0, a1 in runs(unc, merge=4):
                    tot = int(colink[a0:a1 + 1].sum())
                    if tot < 25:
                        continue
                    # which ayah would a tap there select? (pickAyah: nearest centre)
                    tx, ty = (a0 + a1) / 2, yc
                    near = min(recs, key=lambda r: (r["center_x"] - tx) ** 2 + (r["center_y"] - ty) ** 2)
                    edge = a1 < 12 or a0 > W - 12
                    issue(p, "", "WARN" if edge else "ERROR", "UNCOVERED_INK_EDGE" if edge else "UNCOVERED_INK",
                          f"text ink not inside any ayah on slot {s['k'] + 1}, x {a0}-{a1} ({tot}px); "
                          f"a tap there selects {near['verse_key']}",
                          x0=a0, x1=a1, yc=yc)

            # --- own marker inside own region, no other ayah over it
            for i, r in enumerate(recs):
                if i not in rec_to_m:
                    continue
                m = markers[rec_to_m[i][0]]
                mx, my = m["x"], r["center_y"]
                span = np.zeros(W, bool)
                span[int(mx - 26):int(mx + 27)] = True
                own = np.zeros(W, bool)
                other = Counter()
                for r2 in recs:
                    for q in r2["rects"]:
                        if q["y"] <= my - 20 and q["y"] + q["h"] >= my + 20:
                            seg = np.zeros(W, bool)
                            seg[int(q["x"]):int(q["x"] + q["w"]) + 1] = True
                            if r2 is r:
                                own |= seg
                            else:
                                ov = int((seg & span).sum())
                                if ov > 3:
                                    other[r2["verse_key"]] += ov
                miss = int((span & ~own).sum())
                if miss > 3:
                    issue(p, r["verse_key"], "ERROR", "MARKER_NOT_IN_OWN_REGION",
                          f"{miss}px of its own marker ring are outside its highlight", mx=mx, my=my)
                for k2, ov in other.items():
                    issue(p, r["verse_key"], "ERROR", "MARKER_IN_OTHER_REGION",
                          f"{k2}'s highlight covers {ov}px of this ayah's marker", mx=mx, my=my)

            # --- ayah 1 of each surah: starts on the line after header (+ basmala)
            for r in starts:
                if not r["rects"]:
                    continue
                first_y = min(q["y"] + q["h"] / 2 for q in r["rects"])
                k = slot_of(first_y)
                if k is None:
                    continue
                need = 1 if r["surah_number"] == 9 else 2
                ok_hdr = k - need >= 0 and slots[k - need]["kind"] == "header"
                ok_bsm = r["surah_number"] == 9 or (k - 1 >= 0 and slots[k - 1]["kind"] == "basmala")
                if not (ok_hdr and ok_bsm):
                    kinds = [s["kind"] for s in slots[max(k - 3, 0):k + 1]]
                    issue(p, r["verse_key"], "ERROR", "SURAH_START",
                          f"ayah 1 highlight starts on slot {k + 1}; slots before it are {kinds}")

            # --- page start/end: no text before first ayah or after the last marker
            text_slots = [s for s in slots if s["kind"] == "text"]
            last = recs[-1]
            if (len(recs) - 1) in rec_to_m:
                m = markers[rec_to_m[len(recs) - 1][0]]
                k = slot_of(m["y"])
                if k is not None:
                    yc = slots[k]["yc"]
                    tail = ink[int(yc - 38):int(yc + 38), 0:max(int(m["x"] - 32), 0)]
                    if tail.sum() > 40:
                        issue(p, last["verse_key"], "ERROR", "TEXT_AFTER_LAST_MARKER",
                              f"{int(tail.sum())} ink px left of the page's last marker")
                    below = [s for s in text_slots if s["k"] > k]
                    if below:
                        issue(p, last["verse_key"], "ERROR", "TEXT_BELOW_LAST_MARKER",
                              f"text lines below the last marker: {[s['k'] + 1 for s in below]}")

        stats["pages"] += 1

    # ---------- output ----------
    json.dump(page_info, (OUT / f"page_info{TAG}.json").open("w"), separators=(",", ":"))
    with (OUT / f"issues{TAG}.csv").open("w", encoding="utf-8-sig", newline="") as fh:
        wr = csv.writer(fh)
        wr.writerow(["page", "verse_key", "severity", "code", "message", "extra"])
        for it in issues:
            extra = {k: v for k, v in it.items() if k not in ("page", "verse_key", "severity", "code", "message")}
            wr.writerow([it["page"], it["verse_key"], it["severity"], it["code"], it["message"],
                         json.dumps(extra, ensure_ascii=False)])
    json.dump(issues, (OUT / f"issues{TAG}.json").open("w", encoding="utf-8"), ensure_ascii=False, indent=0)
    print(dict(stats))
    c = Counter((it["severity"], it["code"]) for it in issues)
    for (sev, code), n in sorted(c.items()):
        pages = sorted({it["page"] for it in issues if it["code"] == code})
        print(f"{sev:5} {code:28} {n:5}  pages={len(pages)} e.g. {pages[:12]}")


if __name__ == "__main__":
    main()
