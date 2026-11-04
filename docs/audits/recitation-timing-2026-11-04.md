# Recitation timing and seeking check

Recitation plays whole-surah MP3 files from MP3Quran and starts at an ayah by seeking to the
ayah's `start_time` from `/api/v3/ayat_timing`. Two things must hold for that to work, and both
were checked for every complete recitation with ayah timing on 2026-11-04 with
`mushaf_app/tools/check_recitation_timing.py`.

## Method

- **Seeking.** A seek lands exactly only in a constant-bitrate file. For each recitation, the
  Al-Baqarah file's first frame (and any Xing/VBRI header) and frames sampled at 25, 50 and 75% of
  the file were read with range requests.
- **Timing.** At six ayah starts in Al-Baqarah (ayahs 3, 30, 100, 150, 255, 284), 3 s of audio
  around `start_time` was fetched by byte range and decoded with ffmpeg (8 kHz mono, 20 ms RMS
  windows). A start is good when recitation resumes no earlier than 150 ms before `start_time`
  and the audio dips (≤ 50% of the loud level) into a pause near it.
- A recitation is **weak** when it is not constant-bitrate or when two or more of the six starts
  miss. About 0.4 MB was downloaded per recitation; no file was fetched whole.

## Result

113 recitations checked: **102 good** (12 of them with one miss out of six), **11 weak**.
The weak ones are hidden from the reciter list by `RecitationQuality.HIDDEN`.

| id | Recitation | Riwayah | Why hidden |
|---:|---|---|---|
| 62 | عبدالله عواد الجهني | حفص عن عاصم | variable bitrate |
| 259 | أحمد النفيس | حفص عن عاصم | variable bitrate |
| 67 | عبدالمحسن القاسم | حفص عن عاصم | 4/6 starts inside the recitation (up to 740 ms) |
| 74 | علي بن عبدالرحمن الحذيفي | حفص عن عاصم | 4/6 starts 160–360 ms late |
| 88 | نعمة الحسان | حفص عن عاصم | 3/6, no pause near the start |
| 119 | محمود خليل الحصري | المصحف المجود | 5/6 starts up to 800 ms late |
| 120 | محمود خليل الحصري | ورش عن نافع | 2/6 |
| 122 | محمود علي البنا | المصحف المجود | 3/6, up to 840 ms late |
| 134 | محمد سايد | ورش عن نافع | 3/6 |
| 288 | مصطفى إسماعيل | المصحف المجود | 4/6, up to 560 ms late |
| 300 | صالح الشمراني | حفص عن عاصم | 2/6, no pause near the start |

The default reciter, Mishary Alafasy (123), passed with every sampled start in the pause.

## Rerunning

```
python mushaf_app/tools/check_recitation_timing.py --ffmpeg <ffmpeg> --out report.json
```

Update `RecitationQuality.HIDDEN` from the report's non-`good` entries. A network `error` verdict
is retried once by the tool; check any that remain by hand before hiding them.
