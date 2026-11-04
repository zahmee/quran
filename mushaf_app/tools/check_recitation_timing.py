"""Check every MP3Quran recitation with ayah timing before the app offers it.

Two things decide whether a recitation is usable for following along and for memorisation:

1. Seeking. The app starts at an ayah by seeking inside a whole-surah MP3. That is exact only for a
   constant-bitrate file (byte offset = time x bitrate / 8). The file's header and a few frames
   sampled across it tell whether the bitrate is constant.
2. Timing. At a sample of ayah boundaries in Al-Baqarah, ~3 s of audio around the listed
   start_time is fetched (a range request, never the whole file) and decoded with ffmpeg. The
   boundary is good when the recitation resumes no earlier than 150 ms before start_time (so the
   first syllable is not clipped) and the audio dips into a pause near it.

A recitation is WEAK when it is not constant-bitrate, or when two or more sampled boundaries miss.
The app hides weak ones (RecitationQuality.kt); rerun this when MP3Quran adds recitations.

Needs Python 3 with numpy, and ffmpeg. Downloads roughly 0.4 MB per recitation.

Usage:
    python check_recitation_timing.py --ffmpeg <path-to-ffmpeg> --out report.json
"""
from __future__ import annotations

import argparse
import json
import subprocess
import sys
import urllib.request
from concurrent.futures import ThreadPoolExecutor

import numpy as np

API = "https://www.mp3quran.net/api/v3"
UA = {"User-Agent": "Natlu recitation check (github.com/zahmee/quran)"}
SURAH = 2
BOUNDARIES = [3, 30, 100, 150, 255, 284]
HALF_MS = 1500
WIN_MS = 20
CLIP_TOLERANCE_MS = -150
MAX_DIP_DEPTH = 0.5
MISSES_ALLOWED = 1

BITRATES_V1 = {1: 32, 2: 40, 3: 48, 4: 56, 5: 64, 6: 80, 7: 96, 8: 112, 9: 128, 10: 160,
               11: 192, 12: 224, 13: 256, 14: 320}
BITRATES_V2 = {1: 8, 2: 16, 3: 24, 4: 32, 5: 40, 6: 48, 7: 56, 8: 64, 9: 80, 10: 96, 11: 112,
               12: 128, 13: 144, 14: 160}
RATES = {3: {0: 44100, 1: 48000, 2: 32000}, 2: {0: 22050, 1: 24000, 2: 16000},
         0: {0: 11025, 1: 12000, 2: 8000}}


def get(url: str, rng: tuple[int, int] | None = None, method: str = "GET") -> tuple[dict, bytes]:
    headers = dict(UA)
    if rng:
        headers["Range"] = f"bytes={rng[0]}-{rng[1]}"
    req = urllib.request.Request(url, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=40) as resp:
        return dict(resp.headers), (resp.read() if method == "GET" else b"")


def frame_at(buf: bytes, start: int = 0):
    """First MPEG Layer III frame at or after start that is followed by another frame."""
    for i in range(start, len(buf) - 4):
        if buf[i] != 0xFF or (buf[i + 1] & 0xE0) != 0xE0:
            continue
        version = (buf[i + 1] >> 3) & 3
        layer = (buf[i + 1] >> 1) & 3
        br_idx = (buf[i + 2] >> 4) & 15
        sr_idx = (buf[i + 2] >> 2) & 3
        if version == 1 or layer != 1 or br_idx in (0, 15) or sr_idx == 3:
            continue
        kbps = (BITRATES_V1 if version == 3 else BITRATES_V2)[br_idx]
        size = (144 if version == 3 else 72) * kbps * 1000 // RATES[version][sr_idx] + ((buf[i + 2] >> 1) & 1)
        j = i + size
        if j + 1 < len(buf) and not (buf[j] == 0xFF and (buf[j + 1] & 0xE0) == 0xE0):
            continue
        return i, kbps, size
    return None


def decode(chunk: bytes, ffmpeg: str) -> np.ndarray:
    out = subprocess.run(
        [ffmpeg, "-hide_banner", "-loglevel", "error", "-f", "mp3", "-i", "pipe:0",
         "-ac", "1", "-ar", "8000", "-f", "s16le", "pipe:1"],
        input=chunk, capture_output=True, check=True,
    ).stdout
    return np.frombuffer(out, dtype=np.int16).astype(np.float32)


def check_boundary(url: str, id3: int, kbps: int, t: int, ffmpeg: str) -> dict:
    bpm = kbps / 8.0
    lo = max(t - HALF_MS, 0)
    start = int(id3 + lo * bpm)
    _, chunk = get(url, (start, start + int(2 * HALF_MS * bpm) + 4096))
    sync = frame_at(chunk)
    sync_off = sync[0] if sync else 0
    t0 = lo + sync_off / bpm
    pcm = decode(chunk[sync_off:], ffmpeg)
    win = 8000 * WIN_MS // 1000
    n = len(pcm) // win
    if n < 10:
        return {"ayah_start_ms": t, "ok": False, "reason": "too little audio"}
    rms = np.sqrt((pcm[: n * win].reshape(n, win) ** 2).mean(axis=1) + 1e-9)
    smooth = np.convolve(rms, np.ones(5) / 5, mode="same")
    loud = float(np.percentile(rms, 90)) or 1.0
    i_t = int((t - t0) / WIN_MS)
    if not 0 <= i_t < n:
        return {"ayah_start_ms": t, "ok": False, "reason": "boundary outside window"}
    lo_i, hi_i = max(i_t - 1000 // WIN_MS, 0), min(i_t + 500 // WIN_MS, n - 1)
    dip_i = lo_i + int(np.argmin(smooth[lo_i: hi_i + 1]))
    onset_i = dip_i
    while onset_i < n and smooth[onset_i] < 0.35 * loud:
        onset_i += 1
    onset_ms = (onset_i - i_t) * WIN_MS
    depth = round(float(smooth[dip_i] / loud), 2)
    return {
        "ayah_start_ms": t,
        "dip_offset_ms": (dip_i - i_t) * WIN_MS,
        "dip_depth": depth,
        "onset_after_start_ms": onset_ms,
        "ok": onset_ms >= CLIP_TOLERANCE_MS and depth <= MAX_DIP_DEPTH,
    }


def check_read(read: dict, ffmpeg: str) -> dict:
    res = {"id": read["id"], "name": read["name"], "rewaya": read.get("rewaya")}
    try:
        url = read["folder_url"].rstrip("/") + f"/{SURAH:03d}.mp3"
        _, tbody = get(f"{API}/ayat_timing?surah={SURAH}&read={read['id']}")
        timing = {t["ayah"]: t for t in json.loads(tbody)}
        headers, _ = get(url, method="HEAD")
        size = int(headers.get("Content-Length", 0))
        _, head = get(url, (0, 65535))
        id3 = 0
        if head[:3] == b"ID3":
            id3 = 10 + ((head[6] & 0x7F) << 21 | (head[7] & 0x7F) << 14 |
                        (head[8] & 0x7F) << 7 | (head[9] & 0x7F))
            if id3 > len(head) - 4096:
                _, head = get(url, (id3, id3 + 65535))
                base = 0
            else:
                base = id3
        else:
            base = 0
        first = frame_at(head, base)
        if not first:
            res.update(verdict="weak", reason="no MPEG audio frame found")
            return res
        off, kbps, fsize = first
        frame = head[off: off + fsize]
        vbr_header = b"Xing" in frame or b"VBRI" in frame
        seen = {kbps}
        for pct in (25, 50, 75):
            pos = size * pct // 100
            _, chunk = get(url, (pos, pos + 8191))
            k = 0
            for _ in range(6):
                f = frame_at(chunk, k)
                if not f:
                    break
                seen.add(f[1])
                k = f[0] + f[2]
        cbr = len(seen) == 1 and not vbr_header
        res.update(kbps=kbps, constant_bitrate=cbr, sampled_kbps=sorted(seen))
        if not cbr:
            res.update(verdict="weak", reason="variable bitrate: seeking to an ayah is not exact")
            return res
        rows = [check_boundary(url, id3, kbps, timing[a]["start_time"], ffmpeg)
                for a in BOUNDARIES if a in timing]
        misses = sum(1 for r in rows if not r["ok"])
        res.update(boundaries=rows, misses=misses)
        if misses > MISSES_ALLOWED:
            res.update(verdict="weak", reason=f"{misses} of {len(rows)} sampled ayah starts miss the pause")
        else:
            res.update(verdict="good")
    except Exception as exc:  # report, do not abort the whole run
        res.update(verdict="error", reason=f"{type(exc).__name__}: {exc}")
    return res


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--ffmpeg", default="ffmpeg")
    ap.add_argument("--out", required=True)
    ap.add_argument("--workers", type=int, default=6)
    args = ap.parse_args()
    _, body = get(f"{API}/ayat_timing/reads")
    reads = [r for r in json.loads(body) if r.get("soar_count", 0) >= 114]
    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        results = list(pool.map(lambda r: check_read(r, args.ffmpeg), reads))
    # One retry for network errors, sequentially.
    results = [check_read(r, args.ffmpeg) if res["verdict"] == "error" else res
               for r, res in zip(reads, results)]
    report = {
        "surah": SURAH,
        "boundaries": BOUNDARIES,
        "criteria": {"clip_tolerance_ms": CLIP_TOLERANCE_MS, "max_dip_depth": MAX_DIP_DEPTH,
                     "misses_allowed": MISSES_ALLOWED},
        "results": sorted(results, key=lambda r: r["id"]),
    }
    with open(args.out, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(report, fh, ensure_ascii=False, indent=1)
    counts = {}
    for r in results:
        counts[r["verdict"]] = counts.get(r["verdict"], 0) + 1
    weak = sorted(r["id"] for r in results if r["verdict"] != "good")
    sys.stdout.write(f"checked {len(results)}: {counts}\nnot good: {weak}\n")


if __name__ == "__main__":
    main()
