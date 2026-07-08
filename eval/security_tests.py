"""Access-control checks for Section 5.6.

Each check prints PASS / FAIL / SKIP. Checks that need extra inputs (a non-admin
session, Docker, the AI url) are skipped unless those are provided.

  1. no session            GET /api/search           -> 401
  2. non-admin -> admin    GET /api/admin/ai/albums/status  -> 403   (--user-cookie)
  3. admin (non-super)     POST /api/admin/ai/index-all     -> 403   (--admin-cookie)
  4. fail-closed           stop auth-service, GET /api/search -> 503  (--allow-docker)
  5. internal key          POST <ai>/search with bad key    -> 401   (--ai-url)

The main EVAL_COOKIE should be an OWNER/SUPER_ADMIN session.

Usage:
    python security_tests.py
    python security_tests.py --user-cookie <SESSION-of-a-plain-user>
    python security_tests.py --allow-docker --compose-dir /path/to/repo
    python security_tests.py --ai-url http://localhost:8000
"""
from __future__ import annotations

import argparse
import subprocess
import time

import requests

import common

PASS, FAIL, SKIP = "PASS", "FAIL", "SKIP"


def _expect(code: int, want: int) -> str:
    return f"{PASS} (got {code})" if code == want else f"{FAIL} (got {code}, wanted {want})"


def check_no_session() -> str:
    s = requests.Session()  # no cookie
    r = s.get(f"{common.BASE_URL}/api/search", params={"query": "x"}, timeout=common.TIMEOUT)
    return _expect(r.status_code, 401)


def check_non_admin(user_cookie: str | None) -> str:
    if not user_cookie:
        return f"{SKIP} (pass --user-cookie of a plain USER)"
    s = common.make_session(user_cookie)
    r = s.get(f"{common.BASE_URL}/api/admin/ai/albums/status", timeout=common.TIMEOUT)
    return _expect(r.status_code, 403)


def check_index_all(admin_cookie: str | None) -> str:
    if not admin_cookie:
        return f"{SKIP} (pass --admin-cookie of a non-super ADMIN)"
    s = common.make_session(admin_cookie)
    r = s.post(f"{common.BASE_URL}/api/admin/ai/index-all", timeout=common.TIMEOUT)
    return _expect(r.status_code, 403)


def check_fail_closed(allow_docker: bool, compose_dir: str | None) -> str:
    if not (allow_docker and compose_dir):
        return f"{SKIP} (needs --allow-docker --compose-dir)"
    s = common.make_session()
    try:
        subprocess.run(["docker", "compose", "stop", "auth-service"],
                       cwd=compose_dir, capture_output=True, text=True, check=True)
        # RemoteAuthFilter caches a valid auth for 30s; wait past that so the request
        # actually reaches the (now unreachable) auth-service and fails closed.
        print("    auth-service stopped; waiting 35s for the auth cache to expire ...")
        time.sleep(35)
        r = s.get(f"{common.BASE_URL}/api/search", params={"query": "x"}, timeout=common.TIMEOUT)
        # Fail-closed = the request is REFUSED when auth is unavailable (401/403/503),
        # never allowed through (200). Any denial code satisfies the property.
        if r.status_code in (401, 403, 503):
            result = f"{PASS} (auth down -> request refused with {r.status_code})"
        else:
            result = f"{FAIL} (auth down but request went through with {r.status_code})"
    finally:
        subprocess.run(["docker", "compose", "start", "auth-service"],
                       cwd=compose_dir, capture_output=True, text=True)
        time.sleep(5)
    return result


def check_internal_key(ai_url: str | None) -> str:
    if not ai_url:
        return f"{SKIP} (pass --ai-url of the AI service)"
    try:
        r = requests.post(f"{ai_url.rstrip('/')}/search",
                          json={"query": "x", "top_k": 1, "album_ids": []},
                          headers={"X-Internal-API-Key": "definitely-wrong-key"},
                          timeout=common.TIMEOUT)
    except Exception as exc:  # noqa: BLE001
        return f"{SKIP} (AI service unreachable from host: {exc})"
    return _expect(r.status_code, 401)


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--user-cookie", help="SESSION of a plain USER account")
    ap.add_argument("--admin-cookie", help="SESSION of a non-super ADMIN account")
    ap.add_argument("--allow-docker", action="store_true")
    ap.add_argument("--compose-dir")
    ap.add_argument("--ai-url", help="AI service base url (e.g. http://localhost:8000)")
    args = ap.parse_args()

    # sanity: the main cookie should be a valid elevated session
    common.require_auth(common.make_session())

    results = {
        "1. no session -> 401": check_no_session(),
        "2. non-admin -> 403": check_non_admin(args.user_cookie),
        "3. index-all non-super -> 403": check_index_all(args.admin_cookie),
        "4. auth down -> 503 (fail-closed)": check_fail_closed(args.allow_docker, args.compose_dir),
        "5. bad internal key -> 401": check_internal_key(args.ai_url),
    }
    print("\n=== Security checks ===")
    for name, res in results.items():
        print(f"  {name:<38}: {res}")


if __name__ == "__main__":
    main()
