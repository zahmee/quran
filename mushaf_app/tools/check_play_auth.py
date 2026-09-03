"""Verify that this machine can reach Google Play as the publishing service account.

Proves the whole chain — key file, androidpublisher API, Play Console permissions —
without uploading anything. It mints an OAuth token, opens a throwaway "edit"
transaction (which publishes nothing on its own) and closes it again.

Run it after moving the project to a new machine, or after rotating the key:

    python mushaf_app/tools/check_play_auth.py

Needs only a stock Python and the openssl binary that ships with Git for Windows.
Never prints the private key or the access token.
"""
import base64
import json
import os
import re
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

MODULE = Path(__file__).resolve().parent.parent
KEY = MODULE / "play-service-account.json"
SCOPE = "https://www.googleapis.com/auth/androidpublisher"


def application_id() -> str:
    """Read applicationId out of app/build.gradle.kts so this never goes stale."""
    gradle = (MODULE / "app" / "build.gradle.kts").read_text(encoding="utf-8")
    found = re.search(r'applicationId\s*=\s*"([^"]+)"', gradle)
    if not found:
        sys.exit("could not find applicationId in app/build.gradle.kts")
    return found.group(1)


def access_token(creds: dict) -> str:
    b64 = lambda raw: base64.urlsafe_b64encode(raw).decode().rstrip("=")
    now = int(time.time())
    header = b64(json.dumps({"alg": "RS256", "typ": "JWT"}).encode())
    claim = b64(json.dumps({
        "iss": creds["client_email"],
        "scope": SCOPE,
        "aud": "https://oauth2.googleapis.com/token",
        "exp": now + 3600,
        "iat": now,
    }).encode())
    unsigned = header + "." + claim

    handle, pem = tempfile.mkstemp(suffix=".pem")
    with os.fdopen(handle, "w") as out:
        out.write(creds["private_key"])
    try:
        signature = subprocess.run(
            ["openssl", "dgst", "-sha256", "-sign", pem],
            input=unsigned.encode(), capture_output=True, check=True).stdout
    finally:
        os.remove(pem)

    body = urllib.parse.urlencode({
        "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
        "assertion": unsigned + "." + b64(signature),
    }).encode()
    reply = urllib.request.urlopen("https://oauth2.googleapis.com/token", body, timeout=30)
    return json.load(reply)["access_token"]


def main() -> int:
    if not KEY.exists():
        print("MISSING  " + str(KEY))
        print("         Drop the service-account key there, then run this again.")
        print("         See publishing/PLAY-PUBLISHING-AR.md for how to obtain one.")
        return 1

    creds = json.loads(KEY.read_text(encoding="utf-8"))
    package = application_id()
    print("service account : " + creds["client_email"])
    print("cloud project   : " + creds.get("project_id", "?"))
    print("application     : " + package)
    print()

    try:
        token = access_token(creds)
        print("1) OAuth token           OK    key valid, androidpublisher API enabled")
    except urllib.error.HTTPError as failure:
        print("1) OAuth token           FAIL")
        print(failure.read().decode()[:600])
        return 1

    edits = ("https://androidpublisher.googleapis.com/androidpublisher/v3/applications/"
             + package + "/edits")
    headers = {"Authorization": "Bearer " + token, "Content-Type": "application/json"}
    try:
        request = urllib.request.Request(edits, data=b"{}", method="POST", headers=headers)
        edit_id = json.load(urllib.request.urlopen(request, timeout=30))["id"]
        print("2) Play permissions      OK    edit opened on " + package)
    except urllib.error.HTTPError as failure:
        print("2) Play permissions      FAIL  HTTP " + str(failure.code))
        print(failure.read().decode()[:700])
        print()
        print("   Most likely the service account was never invited under")
        print("   Play Console > Users and permissions, or its invite is still settling.")
        return 1

    try:
        request = urllib.request.Request(edits + "/" + edit_id, method="DELETE", headers=headers)
        urllib.request.urlopen(request, timeout=30)
        print("3) Cleanup               OK    nothing uploaded, nothing published")
    except urllib.error.HTTPError as failure:
        print("3) Cleanup               HTTP " + str(failure.code)
              + " (unused edits expire on their own)")

    print()
    print("Ready to publish. Bump versionCode first, then:")
    print("    ./gradlew :app:publishReleaseBundle")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
