"""Bring every testing track up to the release that is on production.

Play never moves a testing track on its own: after a production release the internal and closed
tracks keep showing whatever was last put on them (they sat on 0.6.9 while production reached
0.18.1). Testers still get the higher production build, but the Console reads as if they were
left behind, and anyone who joins a testing track later sees the stale notes. This copies the
production release — same bundle, name and notes, nothing uploaded — onto each testing track
that is behind it, in one edit.

    python mushaf_app/tools/sync_test_tracks.py              # update tracks that are behind
    python mushaf_app/tools/sync_test_tracks.py --dry-run    # show what would change, change nothing

Runs on its own after `publishReleaseBundle` and `promoteReleaseArtifact` (see the
syncTestTracks task in app/build.gradle.kts), so a release made the usual way reaches the
testing tracks without a second command.

Rules, so it is safe to run at any time:
  * a track with no release at all (alpha, beta here) is an unused track and is left alone;
  * a track that already carries production's version code or a higher one — a newer draft on
    internal, say — is left alone, so a build being prepared for testers is never overwritten;
  * nothing is committed when no track is behind.

This goes through the API directly rather than through Gradle Play Publisher because the
closed track is named "الاختبار المغلق": Gradle on Windows hands the plugin that argument as
"???????? ??????", and Play answers 404.
"""
import json
import sys
import urllib.error
import urllib.parse

from check_play_auth import KEY, access_token, application_id
from check_play_tracks import BASE, call

LIVE = {"completed", "inProgress"}


def highest_code(release: dict) -> int:
    return max((int(code) for code in release.get("versionCodes") or []), default=0)


def main() -> int:
    dry_run = "--dry-run" in sys.argv
    if not KEY.exists():
        print("MISSING  " + str(KEY) + " — nothing synced")
        return 1

    creds = json.loads(KEY.read_text(encoding="utf-8"))
    headers = {"Authorization": "Bearer " + access_token(creds),
               "Content-Type": "application/json"}
    edits = BASE + application_id() + "/edits"
    edit_id = call(edits, headers, "POST", b"{}")["id"]
    committed = False
    try:
        tracks = call(edits + "/" + edit_id + "/tracks", headers, "GET").get("tracks", [])
        production = next((t for t in tracks if t.get("track") == "production"), {})
        live = [r for r in production.get("releases") or [] if r.get("status") in LIVE]
        if not live:
            print("production has no live release — nothing to sync")
            return 0
        source = max(live, key=highest_code)
        source_code = highest_code(source)
        print("production : " + str(source.get("name")) + " (code " + str(source_code) + ")")

        release = {
            "name": source.get("name"),
            "versionCodes": source["versionCodes"],
            "status": "completed",
            "releaseNotes": source.get("releaseNotes", []),
        }
        changed = []
        for track in tracks:
            name = track.get("track", "")
            if name == "production":
                continue
            releases = track.get("releases") or []
            if not releases:
                continue
            current = max(highest_code(r) for r in releases)
            if current >= source_code:
                print("up to date : " + name + " (code " + str(current) + ")")
                continue
            print(("would move : " if dry_run else "moving     : ")
                  + name + " (code " + str(current) + " → " + str(source_code) + ")")
            changed.append(name)
            if not dry_run:
                body = json.dumps({"track": name, "releases": [release]},
                                  ensure_ascii=False).encode("utf-8")
                call(edits + "/" + edit_id + "/tracks/" + urllib.parse.quote(name, safe=""),
                     headers, "PUT", body)

        if changed and not dry_run:
            call(edits + "/" + edit_id + ":commit", headers, "POST", b"")
            committed = True
            print("committed  : " + ", ".join(changed))
        elif not changed:
            print("every testing track already matches production")
        return 0
    except urllib.error.HTTPError as failure:
        print("Play API failed: HTTP " + str(failure.code))
        print(failure.read().decode("utf-8")[:900])
        return 1
    finally:
        if not committed:
            try:
                call(edits + "/" + edit_id, headers, "DELETE")
            except urllib.error.HTTPError:
                pass


if __name__ == "__main__":
    raise SystemExit(main())
