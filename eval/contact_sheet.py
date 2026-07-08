"""Labelling aid: render an album's photos as a self-contained HTML contact sheet.

Each photo is shown with its driveFileId caption, so you can build the ground-truth
benchmark by eye (note down the IDs that are relevant to a query, or that contain a
given person). Thumbnails are fetched through the API with your session cookie and
embedded as data URIs, so the resulting HTML works offline.

Usage:
    python contact_sheet.py --list
    python contact_sheet.py --album <driveFolderId> [--out sheet.html]
"""
from __future__ import annotations

import argparse
import base64
import html
import sys
import threading
from concurrent.futures import ThreadPoolExecutor

import common


def list_albums(session) -> None:
    r = session.get(f"{common.BASE_URL}/api/drive/albums", timeout=common.TIMEOUT)
    r.raise_for_status()
    for a in r.json():
        print(f"{a['id']}\t{a.get('name', '')}")


def fetch_images(session, album_id: str) -> list[dict]:
    r = session.get(f"{common.BASE_URL}/api/drive/albums/{album_id}/images",
                    timeout=common.TIMEOUT)
    r.raise_for_status()
    return r.json()


def fetch_thumbnail(session, file_id: str) -> str | None:
    """Return a base64 data URI for the photo's thumbnail, or None on failure."""
    try:
        r = session.get(f"{common.BASE_URL}/api/drive/images/{file_id}/thumbnail",
                        timeout=common.TIMEOUT)
        r.raise_for_status()
        b64 = base64.b64encode(r.content).decode("ascii")
        ctype = r.headers.get("content-type", "image/jpeg")
        return f"data:{ctype};base64,{b64}"
    except Exception as exc:  # noqa: BLE001 - best effort per thumbnail
        print(f"  ! thumbnail failed for {file_id}: {exc}", file=sys.stderr)
        return None


def fetch_all_thumbnails(cookie: str, ids: list[str], workers: int) -> dict[str, str | None]:
    """Fetch every thumbnail concurrently. Each worker uses its own session
    (thread-local) so requests never share a connection across threads."""
    local = threading.local()

    def worker(fid: str) -> tuple[str, str | None]:
        if not hasattr(local, "session"):
            local.session = common.make_session(cookie)
        return fid, fetch_thumbnail(local.session, fid)

    result: dict[str, str | None] = {}
    done = 0
    with ThreadPoolExecutor(max_workers=workers) as pool:
        for fid, uri in pool.map(worker, ids):
            result[fid] = uri
            done += 1
            if done % 25 == 0 or done == len(ids):
                print(f"  ... {done}/{len(ids)} thumbnails")
    return result


def build_html(album_id: str, images: list[dict], thumbs: dict[str, str | None]) -> str:
    cells = []
    for i, img in enumerate(images, start=1):
        fid = html.escape(img["id"])
        name = html.escape(img.get("name", ""))
        data_uri = thumbs.get(img["id"])
        img_tag = (f'<img loading="lazy" src="{data_uri}">'
                   if data_uri else '<div class="missing">no thumb</div>')
        cells.append(
            f'<figure class="cell" data-id="{fid}"><div class="idx">{i}</div>'
            f'<input type="checkbox" class="sel">{img_tag}'
            f'<figcaption><code>{fid}</code><br>{name}</figcaption></figure>'
        )

    return (_SHEET_TEMPLATE
            .replace("__ALBUM__", html.escape(album_id))
            .replace("__COUNT__", str(len(images)))
            .replace("__CELLS__", "".join(cells)))


_SHEET_TEMPLATE = """<!doctype html>
<html><head><meta charset="utf-8"><title>Label sheet __ALBUM__</title>
<style>
  body { font-family: sans-serif; margin: 0; }
  .bar { position: sticky; top: 0; background: #fff; border-bottom: 2px solid #ccc;
         padding: 8px 12px; z-index: 10; }
  .bar h2 { margin: 0 0 6px; font-size: 15px; }
  .row { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin: 4px 0; }
  .row input[type=text] { padding: 4px; } button { padding: 4px 10px; cursor: pointer; }
  .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
          gap: 10px; padding: 12px; }
  .cell { margin: 0; border: 1px solid #ccc; padding: 4px; position: relative; cursor: pointer; }
  .cell.on { outline: 3px solid #2a7; }
  .cell img { width: 100%; height: 135px; object-fit: cover; display: block; }
  figcaption { font-size: 11px; word-break: break-all; }
  .idx { position: absolute; top: 6px; left: 6px; background: #000a; color: #fff;
         padding: 0 5px; border-radius: 3px; font-size: 11px; }
  .sel { position: absolute; top: 6px; right: 6px; width: 20px; height: 20px; }
  .missing { height: 135px; display: grid; place-items: center; background: #eee; color: #999; }
  .out { display: flex; gap: 12px; padding: 12px; }
  .out > div { flex: 1; } textarea { width: 100%; height: 240px; font-family: monospace; font-size: 11px; }
  #count { font-weight: bold; }
</style></head>
<body>
<div class="bar">
  <h2>Album <code>__ALBUM__</code> — __COUNT__ photos &nbsp; | &nbsp; selected: <span id="count">0</span></h2>
  <div class="row">
    <b>Semantic:</b>
    <input type="text" id="q" placeholder="query text" size="40">
    <select id="cat"><option>activity</option><option>people</option><option>scene</option><option>object</option></select>
    <button onclick="addSemantic()">Add query from selected</button>
  </div>
  <div class="row">
    <b>Face:</b>
    <input type="text" id="person" placeholder="person name">
    <button onclick="addFace()">Add person from selected (first 5 = references)</button>
  </div>
  <div class="row">
    <button onclick="clearTicks()">Clear selection</button>
    <button onclick="undoSemantic()">Undo last query</button>
    <button onclick="undoFace()">Undo last person</button>
  </div>
</div>

<div class="grid">__CELLS__</div>

<div class="out">
  <div><b>semantic_benchmark.json</b>
    <button onclick="copyOut('sem')">Copy</button>
    <textarea id="sem" readonly>[]</textarea></div>
  <div><b>face_benchmark.json</b>
    <button onclick="copyOut('fac')">Copy</button>
    <textarea id="fac" readonly>[]</textarea></div>
</div>

<script>
const ALBUM_ID = "__ALBUM__";
let semantic = [], faces = [];

document.querySelectorAll('.cell').forEach(cell => {
  const box = cell.querySelector('.sel');
  const sync = () => cell.classList.toggle('on', box.checked);
  cell.addEventListener('click', e => {
    if (e.target !== box) box.checked = !box.checked;
    sync(); updateCount();
  });
  box.addEventListener('change', () => { sync(); updateCount(); });
});

function selectedIds() {
  return [...document.querySelectorAll('.cell')]
    .filter(c => c.querySelector('.sel').checked).map(c => c.dataset.id);
}
function updateCount() { document.getElementById('count').textContent = selectedIds().length; }
function clearTicks() {
  document.querySelectorAll('.sel').forEach(b => { b.checked = false; b.closest('.cell').classList.remove('on'); });
  updateCount();
}
function render() {
  document.getElementById('sem').value = JSON.stringify(semantic, null, 2);
  document.getElementById('fac').value = JSON.stringify(faces, null, 2);
}
function addSemantic() {
  const q = document.getElementById('q').value.trim();
  const ids = selectedIds();
  if (!q) { alert('Enter a query first.'); return; }
  if (!ids.length) { alert('Select the relevant photos first.'); return; }
  semantic.push({ query: q, category: document.getElementById('cat').value,
                  album_ids: [ALBUM_ID], relevant: ids });
  document.getElementById('q').value = '';
  render(); clearTicks();
}
function addFace() {
  const p = document.getElementById('person').value.trim();
  const ids = selectedIds();
  if (!p) { alert('Enter a person name first.'); return; }
  if (!ids.length) { alert('Select the photos this person is in.'); return; }
  faces.push({ person: p, album_ids: [ALBUM_ID], appears_in: ids, references: ids.slice(0, 5) });
  document.getElementById('person').value = '';
  render(); clearTicks();
}
function undoSemantic() { semantic.pop(); render(); }
function undoFace() { faces.pop(); render(); }
function copyOut(id) {
  const el = document.getElementById(id); el.select();
  navigator.clipboard.writeText(el.value);
}
</script>
</body></html>"""


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--list", action="store_true", help="list albums (id + title) and exit")
    ap.add_argument("--album", help="drive folder id of the album to render")
    ap.add_argument("--out", help="output HTML path (default sheet_<album>.html)")
    ap.add_argument("--workers", type=int, default=16,
                    help="parallel thumbnail downloads (default 16)")
    ap.add_argument("--limit", type=int, help="only render the first N photos")
    args = ap.parse_args()

    session = common.make_session()
    common.require_auth(session)

    if args.list:
        list_albums(session)
        return
    if not args.album:
        ap.error("provide --album <driveFolderId> (or --list)")

    print(f"Fetching image list for album {args.album} ...")
    images = fetch_images(session, args.album)
    if args.limit:
        images = images[:args.limit]
    print(f"Fetching {len(images)} thumbnails with {args.workers} workers ...")
    thumbs = fetch_all_thumbnails(common.COOKIE, [img["id"] for img in images], args.workers)
    doc = build_html(args.album, images, thumbs)

    out = args.out or f"sheet_{args.album}.html"
    with open(out, "w", encoding="utf-8") as f:
        f.write(doc)
    print(f"Wrote {out}  (open it in a browser)")


if __name__ == "__main__":
    main()
