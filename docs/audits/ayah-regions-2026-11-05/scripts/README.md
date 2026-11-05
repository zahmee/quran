Run from this folder, in order (Python 3 + numpy + Pillow):

    python analyze.py        # image features for pages 1..604 -> $AYAH_AUDIT_OUT (default: %TEMP%/natlu_ayah_audit)
    python check.py          # every check -> issues.csv / issues.json / page_info.json
    python digits.py         # marker digits vs ayah numbers
    python overlay.py OUTDIR 0.5 595 586:0-500   # overlays (whole page, or a y-range)

check.py honours CHECK_REGIONS=<path to another ayah_regions.json> and CHECK_TAG=<suffix>, which is how
the fault-injection run was done.
