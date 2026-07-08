"""Scenario tests for the indexing lifecycle guarantees of Section 4.6 / 5.4.

Tests (choose with the positional argument, or 'all'):

  stop-resume   Start a re-index, stop it mid-run, check nothing is stuck and work
                remains, then resume and check it completes. Fully automated.
  conflict      Fire two index runs at the same album concurrently; exactly one must
                be rejected with 409. Fully automated.
  crash         Start a re-index, kill a service mid-run, restart it, and check crash
                recovery cleared the stuck state and a re-run completes. Needs Docker
                (--allow-docker --compose-dir <path>).

Use a TEST album with enough photos that a run lasts a few seconds.

Usage:
    python robustness_tests.py stop-resume --album <driveFolderId>
    python robustness_tests.py conflict    --album <driveFolderId>
    python robustness_tests.py crash       --album <driveFolderId> \
            --allow-docker --compose-dir /path/to/repo --service indexing-service
"""
from __future__ import annotations

import argparse
import subprocess
import threading
import time

import common

PASS, FAIL, SKIP = "PASS", "FAIL", "SKIP"


def status(session, album_id: str) -> dict | None:
    r = session.get(f"{common.BASE_URL}/api/admin/ai/albums/status", timeout=common.TIMEOUT)
    r.raise_for_status()
    for a in r.json():
        if a["driveAlbumId"] == album_id:
            return a
    return None


def _post_bg(cookie, path, holder):
    s = common.make_session(cookie)
    try:
        r = s.post(f"{common.BASE_URL}{path}", timeout=None)
        holder["code"] = r.status_code
    except Exception as exc:  # noqa: BLE001
        holder["err"] = str(exc)


def wait_until(pred, timeout=60, interval=1.0) -> bool:
    end = time.time() + timeout
    while time.time() < end:
        if pred():
            return True
        time.sleep(interval)
    return False


def test_stop_resume(session, album: str) -> str:
    holder: dict = {}
    t = threading.Thread(target=_post_bg,
                         args=(common.COOKIE, f"/api/admin/ai/reindex-album/{album}", holder))
    t.start()
    print("  started re-index; waiting for it to become active ...")
    active = wait_until(lambda: (st := status(session, album)) and st["indexingImages"], timeout=60)
    if not active:
        t.join()
        return f"{SKIP} (run finished before we could stop it - use a bigger album)"

    print("  requesting stop ...")
    common.make_session().post(f"{common.BASE_URL}/api/admin/ai/stop-index-album/{album}",
                               timeout=common.TIMEOUT)
    t.join(timeout=180)

    st = status(session, album)
    if st["indexingImages"]:
        return f"{FAIL} (album still marked indexing after stop)"
    if st["needsIndexImages"] <= 0:
        return f"{SKIP} (whole album finished before the stop took effect)"
    print(f"  stopped cleanly: {st['indexedImages']} indexed, "
          f"{st['needsIndexImages']} still need indexing -> resuming ...")

    r = session.post(f"{common.BASE_URL}/api/admin/ai/index-album/{album}", timeout=None)
    r.raise_for_status()
    st2 = status(session, album)
    if st2["needsIndexImages"] == 0 and not st2["indexingImages"]:
        return f"{PASS} (resumed and completed; {st2['indexedImages']} indexed)"
    return f"{FAIL} (after resume, {st2['needsIndexImages']} still need indexing)"


def test_conflict(session, album: str) -> str:
    # Start one run, wait until it is ACTUALLY indexing (photos in INDEXING state),
    # then fire a second request. The guard (count of INDEXING > 0) should reject it
    # with 409. Firing both simultaneously instead would race in the earlier Drive-sync
    # phase, before any item is marked INDEXING, so it would not test the guard.
    h1: dict = {}
    t1 = threading.Thread(target=_post_bg,
                          args=(common.COOKIE, f"/api/admin/ai/reindex-album/{album}", h1))
    t1.start()
    active = wait_until(lambda: (st := status(session, album)) and st["indexingImages"], timeout=90)
    if not active:
        t1.join()
        return f"{SKIP} (first run never became active - use a bigger album)"

    # second request while the first is indexing; index-album is guarded and non-destructive
    r = common.make_session().post(f"{common.BASE_URL}/api/admin/ai/index-album/{album}",
                                   timeout=common.TIMEOUT)
    second = r.status_code
    t1.join(timeout=600)
    if second == 409:
        return f"{PASS} (second request rejected with 409 while the first was indexing)"
    return f"{FAIL} (expected 409 for the second request; got {second})"


def _dc(compose_dir, *args):
    return subprocess.run(["docker", "compose", *args], cwd=compose_dir,
                          capture_output=True, text=True)


def test_crash(session, album: str, compose_dir: str, service: str) -> str:
    holder: dict = {}
    t = threading.Thread(target=_post_bg,
                         args=(common.COOKIE, f"/api/admin/ai/reindex-album/{album}", holder))
    t.start()
    active = wait_until(lambda: (st := status(session, album)) and st["indexingImages"], timeout=60)
    if not active:
        t.join()
        return f"{SKIP} (run finished before we could kill the service)"

    print(f"  killing {service} ...")
    _dc(compose_dir, "kill", service)
    t.join(timeout=30)
    print(f"  restarting {service} and waiting for health ...")
    _dc(compose_dir, "up", "-d", service)

    def healthy():
        try:
            return status(session, album) is not None
        except Exception:
            return False
    if not wait_until(healthy, timeout=180, interval=3):
        return f"{FAIL} (service did not come back healthy)"

    st = status(session, album)
    if st["indexingImages"]:
        return f"{FAIL} (album still stuck 'indexing' after restart - recovery failed)"
    print("  recovery cleared the stuck state; re-running to completion ...")
    r = session.post(f"{common.BASE_URL}/api/admin/ai/index-album/{album}", timeout=None)
    r.raise_for_status()
    st2 = status(session, album)
    return (f"{PASS} (recovered; completed with {st2['indexedImages']} indexed)"
            if st2["needsIndexImages"] == 0 else
            f"{FAIL} ({st2['needsIndexImages']} still need indexing after recovery)")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("test", choices=["stop-resume", "conflict", "crash", "all"])
    ap.add_argument("--album", required=True)
    ap.add_argument("--allow-docker", action="store_true")
    ap.add_argument("--compose-dir", help="repo dir with docker-compose.yml (crash test)")
    ap.add_argument("--service", default="indexing-service", help="service to kill (crash test)")
    args = ap.parse_args()

    session = common.make_session()
    common.require_auth(session)

    results = {}
    if args.test in ("stop-resume", "all"):
        print("\n[stop-resume]")
        results["stop-resume"] = test_stop_resume(session, args.album)
    if args.test in ("conflict", "all"):
        print("\n[conflict]")
        results["conflict"] = test_conflict(session, args.album)
    if args.test in ("crash", "all"):
        print("\n[crash]")
        if not (args.allow_docker and args.compose_dir):
            results["crash"] = f"{SKIP} (needs --allow-docker --compose-dir)"
        else:
            results["crash"] = test_crash(session, args.album, args.compose_dir, args.service)

    print("\n=== Results ===")
    for name, res in results.items():
        print(f"  {name:>12}: {res}")


if __name__ == "__main__":
    main()
