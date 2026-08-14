"""🐍 Proxy Miner API server (pure stdlib, self-maintaining).

Reads from the SQLite dataset (storage.py) and runs the parallel pipeline
(pipeline.py) in background "runs" so the UI never blocks.

Self-maintenance
----------------
* exception-proof handlers — a bad request returns JSON, never kills the box;
* watchdog thread — health-checks the DB, reconnects after a fault and
  keeps the WAL file checkpointed;
* scheduler thread — optional periodic auto-mining so data stays fresh;
* auto-bootstrap — first boot mines a few pages if the dataset is empty;
* (see run.py for a supervisor that restarts the whole server on crash).

Endpoints
---------
GET  /                     dashboard (static/index.html)
GET  /api/providers        registered providers
GET  /api/proxies          proxies (filters + search + sort + pagination)
GET  /api/proxies/raw      text/plain ip:port list
GET  /api/stats            dataset + runtime stats
GET  /api/health           health / uptime / db / scheduler / active run
GET  /api/logs             recent server logs (tail=N)
GET  /api/export           JSON snapshot download
GET  /api/run[/<id>]       mining-run status (live progress)
POST /api/mine             start a run: ?provider=a&provider=b&pages=1..20
POST /api/scheduler        ?enabled=1&interval_min=30&pages=5
"""
from __future__ import annotations

import argparse
import json
import logging
import os
import threading
import time
import traceback
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

from pipeline import ParallelMiner
from providers import PROVIDERS, list_providers
from storage import DEFAULT_DB, Database

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
STATIC_INDEX = os.path.join(BASE_DIR, "static", "index.html")
VERSION = "2.1.0"
MAX_PAGES = 20          # /mine?pages is capped at this
DEFAULT_PAGES = 5

# ---------------------------------------------------------------------- #
#  Logging: console + in-memory ring buffer (served at /api/logs)
# ---------------------------------------------------------------------- #
class RingBuffer(logging.Handler):
    def __init__(self, capacity: int = 500):
        super().__init__()
        self.capacity = capacity
        self.buf: list[str] = []
        # RLock: logging.Handler.handle() already holds self.lock while
        # calling emit(); a plain Lock would self-deadlock here.
        self.lock = threading.RLock()

    def emit(self, record):
        try:
            msg = self.format(record)
        except Exception:
            msg = record.getMessage()
        with self.lock:
            self.buf.append(msg)
            if len(self.buf) > self.capacity:
                del self.buf[: len(self.buf) - self.capacity]

    def tail(self, n: int = 200) -> list[str]:
        with self.lock:
            return list(self.buf[-n:])


ring = RingBuffer()
log = logging.getLogger("miner")
log.setLevel(logging.INFO)
_fmt = logging.Formatter("%(asctime)s | %(levelname)-7s | %(message)s", "%H:%M:%S")
_console = logging.StreamHandler()
_console.setFormatter(_fmt)
_console.setLevel(logging.INFO)
ring.setFormatter(_fmt)
ring.setLevel(logging.INFO)
log.addHandler(_console)
log.addHandler(ring)

# ---------------------------------------------------------------------- #
#  Runtime state
# ---------------------------------------------------------------------- #
db = Database(DEFAULT_DB)
STARTED = time.monotonic()
STARTED_AT = datetime.now().astimezone().isoformat(timespec="seconds")

busy = threading.Lock()                       # held while a run is active
runs: dict[str, dict] = {}                    # run_id -> run snapshot
runs_lock = threading.Lock()
_run_counter = 0

scheduler_cfg = {
    "enabled": False,
    "interval_min": 30,
    "pages": 5,
    "last_run": None,
    "last_run_ts": None,
}
scheduler_lock = threading.Lock()

last_error: dict | None = None
last_error_lock = threading.Lock()


def _now() -> str:
    return datetime.now().astimezone().isoformat(timespec="seconds")


# ---------------------------------------------------------------------- #
#  Run manager (background mining with live progress)
# ---------------------------------------------------------------------- #
def start_mine(providers: list[str] | None, pages: int, label: str = "manual") -> dict | None:
    """Launch a mining run in a background thread. Returns its snapshot,
    or None if a run is already active."""
    global _run_counter
    if not busy.acquire(blocking=False):
        return None

    _run_counter += 1
    run_id = f"run-{_run_counter}"
    run = {
        "id": run_id, "label": label, "state": "running",
        "providers": providers or list(PROVIDERS),
        "pages": pages, "started": _now(), "finished": None,
        "done": 0, "total": 0, "inserted": 0, "updated": 0,
        "duration_ms": None, "error": None,
    }
    with runs_lock:
        runs[run_id] = run
        # keep only the most recent 10
        for old in list(runs.keys())[:-10]:
            del runs[old]

    def on_progress(done, total, inserted, updated):
        run["done"], run["total"] = done, total
        run["inserted"], run["updated"] = inserted, updated

    def worker():
        t0 = time.monotonic()
        try:
            summary = ParallelMiner(db, providers=run["providers"],
                                    max_pages=run["pages"]).run(progress_cb=on_progress)
            run.update(state="done", done=run["total"], finished=_now(),
                       inserted=summary["inserted"], updated=summary["updated"],
                       duration_ms=summary["duration_ms"])
            log.info("[%s] ✔ done: %d inserted / %d updated / %d in DB (%.1fs)",
                     run_id, summary["inserted"], summary["updated"],
                     summary["total_in_db"], summary["duration_ms"] / 1000)
        except Exception as exc:  # noqa: BLE001
            run.update(state="error", finished=_now(), error=str(exc),
                       duration_ms=int((time.monotonic() - t0) * 1000))
            log.error("[%s] ✖ failed: %s\n%s", run_id, exc,
                      traceback.format_exc())
        finally:
            busy.release()

    threading.Thread(target=worker, name=run_id, daemon=True).start()
    log.info("[%s] started — providers=%s pages=%d",
             run_id, ",".join(run["providers"]), pages)
    return dict(run)


def run_snapshot(run_id: str | None) -> dict | list[dict]:
    with runs_lock:
        if run_id:
            return dict(runs.get(run_id, {}))
        return [dict(r) for r in runs.values()]


# ---------------------------------------------------------------------- #
#  Watchdog + scheduler threads
# ---------------------------------------------------------------------- #
def watchdog_loop():
    """Keeps the DB healthy; reconnects on fault, checkpoints the WAL."""
    while True:
        try:
            if not db.healthcheck():
                log.warning("watchdog: DB unhealthy — reconnecting")
                db.reconnect()
                log.info("watchdog: DB reconnected ✔")
            db.checkpoint()
        except Exception as exc:  # noqa: BLE001
            log.error("watchdog error: %s", exc)
        time.sleep(60)


def scheduler_loop():
    """Periodic auto-mining so the dataset refreshes by itself."""
    while True:
        try:
            with scheduler_lock:
                cfg = dict(scheduler_cfg)
            if cfg["enabled"] and cfg["interval_min"] > 0:
                due = (cfg["last_run_ts"] is None
                       or time.monotonic() - cfg["last_run_ts"] >= cfg["interval_min"] * 60)
                if due:
                    with scheduler_lock:
                        scheduler_cfg["last_run"] = _now()
                        scheduler_cfg["last_run_ts"] = time.monotonic()
                    log.info("scheduler: auto-mining %d page(s)/provider",
                             cfg["pages"])
                    start_mine(None, cfg["pages"], label="auto")
        except Exception as exc:  # noqa: BLE001
            log.error("scheduler error: %s", exc)
        time.sleep(20)


# ---------------------------------------------------------------------- #
#  Helpers
# ---------------------------------------------------------------------- #
def _filters(params: dict) -> dict:
    q = {k: v[0] for k, v in params.items()}
    out = {}
    if q.get("country_code"):
        out["country_code"] = q["country_code"]
    if q.get("protocol"):
        out["protocol"] = q["protocol"]
    if q.get("anonymity"):
        out["anonymity"] = q["anonymity"]
    if q.get("city"):
        out["city"] = q["city"]
    if q.get("source"):
        out["source"] = q["source"]
    if q.get("q"):
        out["search"] = q["q"]
    out["sort"] = q.get("sort", "proxy")
    out["order"] = q.get("order", "asc")
    try:
        out["limit"] = int(q["limit"]) if q.get("limit") else 100
    except ValueError:
        out["limit"] = 100
    try:
        out["offset"] = max(0, int(q.get("offset", 0)))
    except ValueError:
        out["offset"] = 0
    return out


def _runtime_stats() -> dict:
    s = db.stats()
    with scheduler_lock:
        sched = dict(scheduler_cfg)
    return {
        **s,
        "server": {
            "version": VERSION,
            "uptime_seconds": int(time.monotonic() - STARTED),
            "started_at": STARTED_AT,
            "db_path": DEFAULT_DB,
        },
        "scheduler": sched,
        "active_run": run_snapshot(sorted(runs.keys())[-1]) if runs else None,
    }


# ---------------------------------------------------------------------- #
#  HTTP handler
# ---------------------------------------------------------------------- #
class Handler(BaseHTTPRequestHandler):
    server_version = f"ProxyMiner/{VERSION}"

    # -- plumbing ------------------------------------------------------ #
    def _send(self, code: int, body: bytes, ctype: str,
              extra: dict | None = None) -> None:
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(body)

    def _json(self, obj, code: int = 200, extra: dict | None = None) -> None:
        body = json.dumps(obj, indent=2, ensure_ascii=False).encode()
        self._send(code, body, "application/json; charset=utf-8", extra)

    def _text(self, txt: str, code: int = 200) -> None:
        self._send(code, txt.encode(), "text/plain; charset=utf-8")

    def _html(self, html, code: int = 200) -> None:
        body = html if isinstance(html, (bytes, bytearray)) else html.encode()
        self._send(code, bytes(body), "text/html; charset=utf-8")

    # -- safe dispatch -------------------------------------------------- #
    def do_GET(self):
        self._safe(self._get)

    def do_POST(self):
        self._safe(self._post)

    def do_OPTIONS(self):
        self._send(204, b"", "text/plain")

    def _safe(self, fn):
        try:
            fn()
        except BrokenPipeError:
            pass
        except Exception as exc:  # noqa: BLE001
            global last_error
            with last_error_lock:
                last_error = {"at": _now(), "path": self.path,
                              "error": str(exc)}
            log.error("handler error on %s: %s\n%s", self.path, exc,
                      traceback.format_exc())
            try:
                self._json({"error": "internal error",
                            "detail": str(exc)}, 500)
            except Exception:  # noqa: BLE001
                pass

    # -- routes --------------------------------------------------------- #
    def _get(self):
        parsed = urlparse(self.path)
        path = parsed.path.rstrip("/") or "/"
        params = parse_qs(parsed.query)

        if path in ("/", "/index.html"):
            return self._html(INDEX_HTML())
        if path == "/favicon.ico":
            return self._send(204, b"", "image/x-icon")

        if path in ("/providers", "/api/providers"):
            return self._json({"providers": list_providers()})

        if path in ("/proxies", "/api/proxies"):
            return self._proxies(params)

        if path in ("/proxies/raw", "/api/proxies/raw"):
            return self._raw(params)

        if path in ("/stats", "/api/stats"):
            return self._json(_runtime_stats())

        if path in ("/health", "/api/health"):
            return self._json(self._health())

        if path in ("/logs", "/api/logs"):
            try:
                tail = max(1, min(2000, int(params.get("tail", [200])[0])))
            except ValueError:
                tail = 200
            return self._json({"count": len(ring.tail(tail)),
                               "logs": ring.tail(tail)})

        if path in ("/export", "/api/export"):
            total, rows = db.query(limit=None)
            payload = json.dumps(
                {"generated_at": _now(), "count": total, "proxies": rows},
                indent=2, ensure_ascii=False).encode()
            return self._send(200, payload, "application/json; charset=utf-8",
                              {"Content-Disposition":
                               'attachment; filename="proxies.json"'})

        if path.startswith("/api/run"):
            rid = path[len("/api/run"):].strip("/") or None
            return self._json(run_snapshot(rid))

        if path.startswith("/run"):
            rid = path[len("/run"):].strip("/") or None
            return self._json(run_snapshot(rid))

        return self._json({"error": "not found", "path": self.path,
                           "hint": "see /api/health for endpoints"}, 404)

    def _post(self):
        parsed = urlparse(self.path)
        path = parsed.path.rstrip("/") or "/"
        params = parse_qs(parsed.query)

        if path in ("/mine", "/api/mine"):
            providers = params.get("provider") or None
            unknown = [p for p in (providers or []) if p not in PROVIDERS]
            if unknown:
                return self._json({"error": "unknown provider(s)",
                                   "unknown": unknown,
                                   "available": list(PROVIDERS)}, 400)
            pages = DEFAULT_PAGES
            capped = False
            if params.get("pages"):
                try:
                    pages = int(params["pages"][0])
                except ValueError:
                    return self._json({"error": "pages must be an integer"}, 400)
            if pages < 1:
                pages = 1
            if pages > MAX_PAGES:
                pages = MAX_PAGES
                capped = True
            run = start_mine(providers, pages)
            if run is None:
                return self._json({"status": "busy", "message":
                                   "a mining run is already in progress",
                                   "active_run": run_snapshot(
                                       sorted(runs.keys())[-1])}, 409)
            return self._json({"status": "started", "pages_capped": capped,
                               "max_pages": MAX_PAGES, "run": run})

        if path in ("/scheduler", "/api/scheduler"):
            with scheduler_lock:
                if params.get("enabled"):
                    scheduler_cfg["enabled"] = params["enabled"][0].lower() in ("1", "true", "yes", "on")
                if params.get("interval_min"):
                    try:
                        scheduler_cfg["interval_min"] = max(1, int(params["interval_min"][0]))
                    except ValueError:
                        return self._json({"error": "interval_min must be an integer"}, 400)
                if params.get("pages"):
                    try:
                        scheduler_cfg["pages"] = max(1, min(MAX_PAGES, int(params["pages"][0])))
                    except ValueError:
                        return self._json({"error": "pages must be an integer"}, 400)
                sched = dict(scheduler_cfg)
            log.info("scheduler config updated: %s", sched)
            return self._json({"status": "ok", "scheduler": sched})

        return self._json({"error": "not found"}, 404)

    # -- endpoint helpers ------------------------------------------------ #
    def _proxies(self, params):
        f = _filters(params)
        total, rows = db.query(**f)
        per_page = f["limit"] or total
        total_pages = (total + per_page - 1) // per_page if per_page else 1
        return self._json({
            "count": len(rows),
            "total_available": total,
            "page": (f["offset"] // per_page) + 1 if per_page else 1,
            "per_page": per_page,
            "total_pages": total_pages,
            "proxies": rows,
        })

    def _raw(self, params):
        f = _filters(params)
        f["limit"] = None
        _, rows = db.query(**f)
        return self._text("\n".join(p["proxy"] for p in rows)
                          + ("\n" if rows else ""))

    def _health(self):
        active = None
        with runs_lock:
            if runs:
                active = dict(runs[sorted(runs.keys())[-1]])
        with last_error_lock:
            le = dict(last_error) if last_error else None
        with scheduler_lock:
            sched = dict(scheduler_cfg)
        return {
            "status": "ok",
            "version": VERSION,
            "uptime_seconds": int(time.monotonic() - STARTED),
            "started_at": STARTED_AT,
            "db": {"path": DEFAULT_DB, "count": db.count(),
                   "healthy": db.healthcheck()},
            "scheduler": sched,
            "active_run": active,
            "last_error": le,
        }

    def log_message(self, fmt, *args):
        log.debug("%s - %s", self.address_string(), fmt % args)


# ---------------------------------------------------------------------- #
#  Static dashboard (cached at boot)
# ---------------------------------------------------------------------- #
_index_cache: bytes | None = None


def INDEX_HTML() -> bytes:
    global _index_cache
    if _index_cache is None:
        try:
            with open(STATIC_INDEX, "r", encoding="utf-8") as fh:
                _index_cache = fh.read().encode()
        except FileNotFoundError:
            _index_cache = b"<h1>Proxy Miner</h1><p>static/index.html missing</p>"
    return _index_cache


# ---------------------------------------------------------------------- #
def main() -> None:
    ap = argparse.ArgumentParser(description="Proxy miner API server")
    ap.add_argument("--host", default="0.0.0.0")
    ap.add_argument("--port", type=int,
                    default=int(os.environ.get("PORT", 8080)))
    ap.add_argument("--bootstrap-pages", type=int, default=2,
                    help="pages to mine on first boot if the DB is empty")
    ap.add_argument("--auto-refresh", type=int, default=0,
                    help="auto-mine every N minutes (0 = off)")
    args = ap.parse_args()

    if args.auto_refresh > 0:
        scheduler_cfg.update(enabled=True, interval_min=args.auto_refresh)
        log.info("auto-refresh enabled: every %d min", args.auto_refresh)

    # background maintenance threads
    threading.Thread(target=watchdog_loop, daemon=True, name="watchdog").start()
    threading.Thread(target=scheduler_loop, daemon=True, name="scheduler").start()

    # bootstrap if empty
    if db.count() == 0:
        log.info("empty dataset — bootstrapping %d page(s)/provider",
                 args.bootstrap_pages)
        start_mine(None, min(args.bootstrap_pages, MAX_PAGES), label="bootstrap")

    server = ThreadingHTTPServer((args.host, args.port), Handler)
    log.info("🐍 Proxy Miner v%s listening on http://%s:%s  (DB: %s)",
             VERSION, args.host, args.port, DEFAULT_DB)
    print(f"🐍 Proxy Miner v{VERSION} → http://{args.host}:{args.port}  "
          f"(DB: {DEFAULT_DB})", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
