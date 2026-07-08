"""Input-validation and injection checks for the search API (Section 6.6).

Uses EVAL_COOKIE (any valid session). Each check prints PASS / FAIL.

Rejection checks (invalid input must be refused with 400 Bad Request):
  1. topK below the minimum (0)
  2. topK above the maximum (99999)
  3. blank query (POST body)

Injection checks (a SQL-like payload must be treated as literal text, not run;
the request should succeed normally and the system stay healthy):
  4. SQL-ish payload in the query text
  5. SQL-ish payload in an album id
  6. sanity: a normal search still works afterwards (database intact)

Usage:
    EVAL_COOKIE=<session> python validation_tests.py
"""
from __future__ import annotations

import common

PASS, FAIL = "PASS", "FAIL"
INJECTION = "'; DROP TABLE media_items;--"


def _expect(code: int, want: int) -> str:
    return f"{PASS} (got {code})" if code == want else f"{FAIL} (got {code}, wanted {want})"


def _expect_ok(code: int) -> str:
    return f"{PASS} (got {code})" if code == 200 else f"{FAIL} (got {code}, wanted 200)"


def check_topk_too_low(s) -> str:
    r = s.get(f"{common.BASE_URL}/api/search", params={"query": "test", "topK": 0},
              timeout=common.TIMEOUT)
    return _expect(r.status_code, 400)


def check_topk_too_high(s) -> str:
    r = s.get(f"{common.BASE_URL}/api/search", params={"query": "test", "topK": 99999},
              timeout=common.TIMEOUT)
    return _expect(r.status_code, 400)


def check_blank_query(s) -> str:
    r = s.post(f"{common.BASE_URL}/api/search",
               json={"query": "", "topK": 10, "albumIds": [], "restrictToIds": None},
               timeout=common.TIMEOUT)
    return _expect(r.status_code, 400)


def check_injection_query(s) -> str:
    r = s.get(f"{common.BASE_URL}/api/search",
              params={"query": INJECTION, "topK": 5}, timeout=common.TIMEOUT)
    return _expect_ok(r.status_code)


def check_injection_album(s) -> str:
    r = s.get(f"{common.BASE_URL}/api/search",
              params={"query": "test", "topK": 5, "albumIds": ["' OR '1'='1"]},
              timeout=common.TIMEOUT)
    return _expect_ok(r.status_code)


def check_sanity(s) -> str:
    r = s.get(f"{common.BASE_URL}/api/search",
              params={"query": "people", "topK": 5}, timeout=common.TIMEOUT)
    return _expect_ok(r.status_code)


def main() -> None:
    s = common.make_session()
    common.require_auth(s)

    results = {
        "1. topK below minimum -> 400":      check_topk_too_low(s),
        "2. topK above maximum -> 400":      check_topk_too_high(s),
        "3. blank query (POST) -> 400":      check_blank_query(s),
        "4. injection in query -> literal":  check_injection_query(s),
        "5. injection in album id -> safe":  check_injection_album(s),
        "6. normal search still works":      check_sanity(s),
    }
    print("\n=== Input validation / injection checks ===")
    for name, res in results.items():
        print(f"  {name:<36}: {res}")


if __name__ == "__main__":
    main()
