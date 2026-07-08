"""Shared helpers for the Chapter 6 evaluation scripts.

Configuration comes from environment variables (or per-script flags):

  EVAL_BASE_URL   Traefik entry point of the running stack. Default http://localhost
  EVAL_COOKIE     The logged-in session cookie. Either the raw SESSION value
                  ("A1B2C3...") or the full "SESSION=A1B2C3..." form.
  EVAL_TIMEOUT    Per-request timeout in seconds. Default 120.

Every request goes through the public API, exactly as the browser would, so the
numbers reflect the real end-to-end path (auth, hydration and all).
"""
from __future__ import annotations

import math
import os
from typing import Iterable, Sequence

import requests

BASE_URL = os.environ.get("EVAL_BASE_URL", "http://localhost").rstrip("/")
COOKIE = os.environ.get("EVAL_COOKIE", "")
TIMEOUT = float(os.environ.get("EVAL_TIMEOUT", "120"))


# ── Session / auth ────────────────────────────────────────────────────────────

def cookie_jar(cookie: str | None = None) -> dict[str, str]:
    raw = (cookie if cookie is not None else COOKIE).strip()
    if not raw:
        return {}
    if "=" in raw:
        name, _, value = raw.partition("=")
        return {name.strip(): value.strip()}
    # Spring Session's cookie is named SESSION (not the servlet default JSESSIONID).
    return {"SESSION": raw}


def make_session(cookie: str | None = None) -> requests.Session:
    s = requests.Session()
    s.cookies.update(cookie_jar(cookie))
    return s


def whoami(session: requests.Session) -> dict:
    """Return the /api/auth/me payload, raising if the session is not valid."""
    r = session.get(f"{BASE_URL}/api/auth/me", timeout=TIMEOUT)
    r.raise_for_status()
    body = r.json()
    if not body.get("authenticated"):
        raise SystemExit(
            "Not authenticated. Set EVAL_COOKIE to a valid SESSION cookie "
            "(log in via the browser, copy the cookie from devtools)."
        )
    return body


def require_auth(session: requests.Session) -> dict:
    me = whoami(session)
    print(f"Authenticated as {me.get('email')} "
          f"(roles: {', '.join(me.get('authorities', [])) or 'none'})")
    return me


# ── Retrieval metrics (binary relevance) ──────────────────────────────────────

def _hits(retrieved: Sequence[str], relevant: set[str], k: int) -> int:
    return sum(1 for item in retrieved[:k] if item in relevant)


def precision_at_k(retrieved: Sequence[str], relevant: set[str], k: int) -> float:
    if k == 0:
        return 0.0
    return _hits(retrieved, relevant, k) / k


def recall_at_k(retrieved: Sequence[str], relevant: set[str], k: int) -> float:
    if not relevant:
        return 0.0
    return _hits(retrieved, relevant, k) / len(relevant)


def average_precision(retrieved: Sequence[str], relevant: set[str]) -> float:
    """AP over the returned ranking, normalised by the number of relevant items."""
    if not relevant:
        return 0.0
    hit = 0
    score = 0.0
    for i, item in enumerate(retrieved, start=1):
        if item in relevant:
            hit += 1
            score += hit / i
    return score / len(relevant)


def dcg_at_k(retrieved: Sequence[str], relevant: set[str], k: int) -> float:
    return sum(
        (1.0 / math.log2(i + 1)) for i, item in enumerate(retrieved[:k], start=1)
        if item in relevant
    )


def ndcg_at_k(retrieved: Sequence[str], relevant: set[str], k: int) -> float:
    ideal_hits = min(len(relevant), k)
    if ideal_hits == 0:
        return 0.0
    idcg = sum(1.0 / math.log2(i + 1) for i in range(1, ideal_hits + 1))
    return dcg_at_k(retrieved, relevant, k) / idcg


def reciprocal_rank(retrieved: Sequence[str], relevant: set[str]) -> float:
    for i, item in enumerate(retrieved, start=1):
        if item in relevant:
            return 1.0 / i
    return 0.0


def mean(values: Iterable[float]) -> float:
    values = list(values)
    return sum(values) / len(values) if values else 0.0


# ── Output helpers ─────────────────────────────────────────────────────────────

def figs_dir() -> str:
    """Where plots are written: docs/Thesis/figs (falls back to ./out)."""
    here = os.path.dirname(os.path.abspath(__file__))
    figs = os.path.join(here, "..", "docs", "Thesis", "figs")
    figs = os.path.normpath(figs)
    if os.path.isdir(figs):
        return figs
    out = os.path.join(here, "out")
    os.makedirs(out, exist_ok=True)
    return out


def out_dir() -> str:
    here = os.path.dirname(os.path.abspath(__file__))
    out = os.path.join(here, "out")
    os.makedirs(out, exist_ok=True)
    return out
