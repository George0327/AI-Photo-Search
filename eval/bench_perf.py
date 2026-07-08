"""Performance and scalability measurements, all on the dev machine.

Two modes:

  index    Re-index one or more albums (POST /reindex-album, which blocks until the
           run finishes and returns the counts) while sampling GPU utilisation and
           memory in the background. Reports photos/second per album and the average
           GPU utilisation during the run -- the evidence for the download-bound claim
           (low, bursty GPU use). Requires an admin session.

  search   Fire a set of queries at GET /api/search, varying topK and the number of
           albums, and report median / p90 end-to-end latency.

Usage:
    python bench_perf.py index  --albums <id1> <id2> ...
    python bench_perf.py search --albums <id1> <id2> ... --queries queries.txt
"""
from __future__ import annotations

import argparse
import csv
import os
import statistics
import subprocess
import threading
import time

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

import common


# ── GPU sampler ─────────────────────────────────────────────────────────────

class GpuSampler(threading.Thread):
    """Polls nvidia-smi in the background; collects (util%, mem MiB) samples."""

    def __init__(self, interval: float = 0.5):
        super().__init__(daemon=True)
        self.interval = interval
        self.samples: list[tuple[float, float]] = []
        self._stop = threading.Event()
        self.available = self._probe()

    @staticmethod
    def _probe() -> bool:
        try:
            subprocess.run(["nvidia-smi", "-L"], capture_output=True, check=True)
            return True
        except Exception:
            print("  (nvidia-smi not available - skipping GPU sampling)")
            return False

    def run(self) -> None:
        if not self.available:
            return
        while not self._stop.is_set():
            try:
                out = subprocess.run(
                    ["nvidia-smi", "--query-gpu=utilization.gpu,memory.used",
                     "--format=csv,noheader,nounits"],
                    capture_output=True, text=True, check=True).stdout.strip()
                util, mem = out.splitlines()[0].split(",")
                self.samples.append((float(util), float(mem)))
            except Exception:
                pass
            self._stop.wait(self.interval)

    def stop(self) -> dict:
        self._stop.set()
        self.join(timeout=2)
        if not self.samples:
            return {"gpu_util_avg": None, "gpu_util_max": None, "gpu_mem_max": None}
        utils = [s[0] for s in self.samples]
        mems = [s[1] for s in self.samples]
        return {"gpu_util_avg": round(sum(utils) / len(utils), 1),
                "gpu_util_max": max(utils), "gpu_mem_max": max(mems)}


# ── index throughput ──────────────────────────────────────────────────────────

def bench_index(session, album_ids: list[str]) -> None:
    rows = []
    for album_id in album_ids:
        print(f"\nRe-indexing album {album_id} ...")
        sampler = GpuSampler()
        sampler.start()
        start = time.perf_counter()
        r = session.post(f"{common.BASE_URL}/api/admin/ai/reindex-album/{album_id}",
                         timeout=None)  # blocks until the run completes
        elapsed = time.perf_counter() - start
        gpu = sampler.stop()
        r.raise_for_status()
        result = r.json()
        total = result.get("totalActiveImages", 0)
        indexed = result.get("newlyIndexedCount", 0)
        rate = indexed / elapsed if elapsed else 0.0
        row = {"album_id": album_id, "photos": total, "indexed": indexed,
               "seconds": round(elapsed, 1), "photos_per_sec": round(rate, 2), **gpu}
        rows.append(row)
        print(f"  {indexed}/{total} photos in {elapsed:.1f}s -> {rate:.2f} photos/s "
              f"| GPU avg {gpu['gpu_util_avg']}% max {gpu['gpu_util_max']}% "
              f"mem {gpu['gpu_mem_max']} MiB")

    _write_csv(rows, os.path.join(common.out_dir(), "bench_index.csv"))
    _plot_index(rows, os.path.join(common.figs_dir(), "bench_index_throughput.pdf"))


def _plot_index(rows: list[dict], path: str) -> None:
    rows = sorted(rows, key=lambda r: r["photos"])
    if len(rows) < 2:
        return
    xs = [r["photos"] for r in rows]
    fig, ax1 = plt.subplots(figsize=(6, 4))
    ax1.plot(xs, [r["seconds"] for r in rows], "o-", color="tab:blue", label="time (s)")
    ax1.set_xlabel("photos in album")
    ax1.set_ylabel("indexing time (s)", color="tab:blue")
    ax2 = ax1.twinx()
    ax2.plot(xs, [r["gpu_util_avg"] or 0 for r in rows], "s--", color="tab:red",
             label="avg GPU %")
    ax2.set_ylabel("avg GPU utilisation (%)", color="tab:red")
    ax2.set_ylim(0, 100)
    plt.title("Indexing time and GPU utilisation vs album size")
    fig.tight_layout()
    plt.savefig(path)
    print(f"Wrote {path}")


# ── search latency ──────────────────────────────────────────────────────────

def bench_search(session, album_ids: list[str], queries: list[str], repeats: int) -> None:
    rows = []
    scopes = [album_ids[:1], album_ids[:max(1, len(album_ids) // 2)], album_ids]
    for topk in (10, 50, 100):
        for scope in scopes:
            if not scope:
                continue
            latencies = []
            for q in queries:
                for _ in range(repeats):
                    params = [("query", q), ("topK", str(topk))]
                    params += [("albumIds", a) for a in scope]
                    t0 = time.perf_counter()
                    resp = session.get(f"{common.BASE_URL}/api/search", params=params,
                                       timeout=common.TIMEOUT)
                    resp.raise_for_status()
                    latencies.append((time.perf_counter() - t0) * 1000)
            row = {"topK": topk, "albums": len(scope), "n": len(latencies),
                   "median_ms": round(statistics.median(latencies), 1),
                   "p90_ms": round(sorted(latencies)[int(0.9 * len(latencies)) - 1], 1)}
            rows.append(row)
            print(f"  topK={topk:>3} albums={len(scope):>2}: "
                  f"median {row['median_ms']} ms  p90 {row['p90_ms']} ms")
    _write_csv(rows, os.path.join(common.out_dir(), "bench_search.csv"))
    _plot_search(rows, os.path.join(common.figs_dir(), "bench_search_latency.pdf"))


def _plot_search(rows: list[dict], path: str) -> None:
    by_albums = sorted({r["albums"] for r in rows})
    plt.figure(figsize=(6, 4))
    for a in by_albums:
        pts = sorted([r for r in rows if r["albums"] == a], key=lambda r: r["topK"])
        plt.plot([r["topK"] for r in pts], [r["median_ms"] for r in pts],
                 "o-", label=f"{a} album(s)")
    plt.xlabel("Number of results requested (topK)")
    plt.ylabel("Median latency (ms)")
    plt.title("Semantic search latency")
    plt.legend()
    plt.tight_layout()
    plt.savefig(path)
    print(f"Wrote {path}")


def _write_csv(rows: list[dict], path: str) -> None:
    if not rows:
        return
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)
    print(f"Wrote {path}")


DEFAULT_QUERIES = ["people on a stage", "group photo outdoors", "a person presenting",
                   "team building activity", "conference room"]


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("mode", choices=["index", "search"])
    ap.add_argument("--albums", nargs="+", required=True, help="drive folder ids")
    ap.add_argument("--queries", help="text file of queries (one per line) for search mode")
    ap.add_argument("--repeats", type=int, default=5, help="repeats per query (search mode)")
    args = ap.parse_args()

    session = common.make_session()
    common.require_auth(session)

    if args.mode == "index":
        print("WARNING: 'index' re-indexes the given albums from scratch (resets their "
              "vector store). Use test albums.")
        bench_index(session, args.albums)
    else:
        queries = DEFAULT_QUERIES
        if args.queries:
            with open(args.queries, encoding="utf-8") as f:
                queries = [ln.strip() for ln in f if ln.strip()]
        bench_search(session, args.albums, queries, args.repeats)


if __name__ == "__main__":
    main()
