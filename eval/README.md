# Evaluation toolkit (Chapter 6)

Scripts that measure the system's **retrieval quality**, **performance**, **robustness**
and **security**, driving it through the public API exactly as the browser does. Outputs
go to `eval/out/` (CSV + `.tex` tables) and `docs/Thesis/figs/` (PDF plots).

## 1. Setup

```bash
cd eval
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
```

The stack must be running (`docker compose up -d` in the repo root).

## 2. Get a session cookie

The API needs a logged-in session, and login is interactive Google OAuth, so a script
can't log in on its own. Log in once in the browser, then copy the cookie:

1. Sign in at `http://localhost`.
2. Open devtools → **Application → Cookies → http://localhost** → copy the value of
   **`SESSION`**.
3. Export it (use an **OWNER/SUPER_ADMIN** account for the admin/perf tests):

```bash
export EVAL_COOKIE="paste-the-SESSION-value-here"
export EVAL_BASE_URL="http://localhost"     # optional, this is the default
```

Sanity check: every script prints `Authenticated as <you>` on start.

## 3. Build the ground-truth benchmark

Generate a clickable contact sheet per test album, then note the relevant IDs:

```bash
python contact_sheet.py --list                       # see album ids + titles
python contact_sheet.py --album <driveFolderId>      # writes sheet_<id>.html
```

Open the HTML, and fill in the two benchmark files (copy the `.example.json` ones):

- `semantic_benchmark.json` — for each query: its `relevant` driveFileIds + `category` + `album_ids`.
- `face_benchmark.json` — for each person: `appears_in` (all their photos) + `references` (5 query photos).

The `<details>` block at the bottom of each contact sheet lists every driveFileId to copy from.

## 4. Retrieval quality

```bash
python eval_semantic.py --benchmark semantic_benchmark.json --topk 50
python eval_faces.py    --benchmark face_benchmark.json --topk 100
```

Semantic → `out/semantic_metrics.tex`, `out/semantic_per_query.csv`,
`figs/semantic_by_category.pdf`.
Faces → `out/faces_metrics.tex`, `out/faces_per_identity.csv`,
`figs/face_reference_count.pdf`.

**Threshold sweep** (face PR curve). The public API fixes the threshold, so this one
talks to the AI service directly. Expose its port first (add `ports: ["8000:8000"]` to
the `ai-service` in `docker-compose.yml`, `docker compose up -d ai-service`), then:

```bash
python eval_faces.py --threshold-sweep \
    --ai-url http://localhost:8000 --internal-key "$AI_SERVICE_INTERNAL_API_KEY"
```

→ `figs/face_threshold_pr.pdf`. Remove the port mapping afterwards.

## 5. Performance (dev box)

```bash
# indexing throughput + GPU utilisation (RE-INDEXES the albums; use TEST albums)
python bench_perf.py index --albums <id1> <id2> <id3>

# search latency vs topK and album count
python bench_perf.py search --albums <id1> <id2> <id3> [--queries queries.txt] [--repeats 5]
```

→ `out/bench_index.csv`, `figs/bench_index_throughput.pdf`,
`out/bench_search.csv`, `figs/bench_search_latency.pdf`.
GPU numbers need `nvidia-smi` on the host (skipped gracefully otherwise).

For the **fp16 vs fp32** comparison, run `index` once, then set `CLIP_PRECISION=fp32`
for the ai-service, `docker compose up -d ai-service`, and run it again.

## 6. Robustness

Use a TEST album big enough that a run lasts several seconds.

```bash
python robustness_tests.py stop-resume --album <driveFolderId>
python robustness_tests.py conflict    --album <driveFolderId>
python robustness_tests.py crash       --album <driveFolderId> \
        --allow-docker --compose-dir .. --service indexing-service
```

## 7. Security

```bash
python security_tests.py \
    --user-cookie  <SESSION-of-a-plain-USER> \
    --admin-cookie <SESSION-of-a-non-super-ADMIN> \
    --allow-docker --compose-dir .. \
    --ai-url http://localhost:8000
```

Any argument you omit turns its check into a SKIP, so you can run a subset.

## Notes / caveats

- Everything runs on the dev machine; scalability figures are measured-small and
  projected in the write-up.
- `bench_perf index` and the robustness tests **re-index albums** — point them at test
  albums, not the whole archive.
- The download-vs-GPU-time split in the thesis is inferred from the GPU-utilisation
  samples (low, bursty = download-bound). A finer per-stage split would need a few
  timing log lines added to the services.
