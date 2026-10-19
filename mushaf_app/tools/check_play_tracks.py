"""Show which release sits on each Google Play track, without changing anything.

Answers "is the version I submitted actually live, and what are the testers on?" from the
terminal, instead of opening the Console. Reads only: it mints a token, opens a throwaway
"edit" transaction (an edit publishes nothing on its own), reads the tracks, then deletes it.

    python mushaf_app/tools/check_play_tracks.py            # one line per release
    python mushaf_app/tools/check_play_tracks.py --notes     # also print the release notes
    python mushaf_app/tools/check_play_tracks.py --json      # raw API response

The OAuth plumbing is imported from check_play_auth.py rather than copied, so the key format
and scope live in exactly one place. That file guards its own main() behind __name__, so
importing it runs nothing; `tools/` is on sys.path because this script lives there.

Needs only a stock Python and the openssl binary that ships with Git for Windows.
Never prints the private key or the access token.
"""
import json
import sys
import urllib.error
import urllib.request

from check_play_auth import KEY, access_token, application_id

BASE = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/"

# Track names and release notes are Arabic, and a Windows console defaults to cp1252 — without
# this, printing a track called "الاختبار المغلق" dies with UnicodeEncodeError after the request
# has already succeeded, which reads like an API failure when it is only a console encoding.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")


def call(url: str, headers: dict, method: str, body: bytes | None = None):
    request = urllib.request.Request(url, data=body, method=method, headers=headers)
    raw = urllib.request.urlopen(request, timeout=60).read().decode("utf-8")
    return json.loads(raw) if raw.strip() else {}


def describe(track: dict, show_notes: bool) -> None:
    """One block per track: its id, then every release Play is serving on it."""
    print("── " + track.get("track", "?"))
    releases = track.get("releases") or []
    if not releases:
        print("   (no release)")
        return
    for release in releases:
        codes = ", ".join(release.get("versionCodes") or []) or "—"
        line = ("   " + str(release.get("name", "?"))
                + "  ·  code " + codes
                + "  ·  " + str(release.get("status", "?")))
        # Present only during a staged rollout; its absence means the release is at 100%.
        if "userFraction" in release:
            line += "  ·  " + str(round(float(release["userFraction"]) * 100, 1)) + "% rollout"
        if release.get("countryTargeting"):
            line += "  ·  limited countries"
        print(line)
        notes = release.get("releaseNotes") or []
        if notes and not show_notes:
            print("     notes: " + ", ".join(note.get("language", "?") for note in notes))
        for note in notes if show_notes else []:
            print("     notes [" + note.get("language", "?") + "]:")
            for row in note.get("text", "").splitlines():
                print("       " + row)


def main() -> int:
    show_notes = "--notes" in sys.argv
    as_json = "--json" in sys.argv

    if not KEY.exists():
        print("MISSING  " + str(KEY))
        print("         See publishing/PLAY-PUBLISHING-AR.md for how to obtain one.")
        return 1

    creds = json.loads(KEY.read_text(encoding="utf-8"))
    package = application_id()
    if not as_json:
        print("application : " + package)
        print("read as     : " + creds["client_email"])
        print()

    try:
        headers = {"Authorization": "Bearer " + access_token(creds),
                   "Content-Type": "application/json"}
    except urllib.error.HTTPError as failure:
        print("OAuth failed: " + failure.read().decode("utf-8")[:600])
        return 1

    edits = BASE + package + "/edits"
    try:
        edit_id = call(edits, headers, "POST", b"{}")["id"]
    except urllib.error.HTTPError as failure:
        print("could not open an edit: HTTP " + str(failure.code))
        print(failure.read().decode("utf-8")[:700])
        print()
        print("   Most likely the service account was never invited under")
        print("   Play Console > Users and permissions, or its invite is still settling.")
        return 1

    try:
        try:
            tracks = call(edits + "/" + edit_id + "/tracks", headers, "GET")
        except urllib.error.HTTPError as failure:
            print("tracks read failed: HTTP " + str(failure.code))
            print(failure.read().decode("utf-8")[:700])
            return 1
        if as_json:
            print(json.dumps(tracks, ensure_ascii=False, indent=2))
        else:
            for track in tracks.get("tracks", []):
                describe(track, show_notes)
            print()
    finally:
        # The edit is deleted either way; an abandoned one would also expire on its own, but
        # leaving it behind makes the next run's Console state look like an unfinished change.
        try:
            call(edits + "/" + edit_id, headers, "DELETE")
            if not as_json:
                print("(throwaway edit deleted; nothing was uploaded or published)")
        except urllib.error.HTTPError:
            if not as_json:
                print("(edit left to expire; nothing was uploaded or published)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
