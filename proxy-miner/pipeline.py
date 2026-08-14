"""Parallel mining pipeline.

Runs every provider at the same time and, inside each provider, fetches
many pages concurrently, streaming parsed proxies into a single writer
that batch-upserts into SQLite as results arrive ("yield as done").

Architecture
------------
  discovery (1 request/provider, all in parallel)
      └─► harvest  (ALL pages of ALL providers in one shared thread pool,
                    bounded per-provider so we don't hammer a site)
              └─► bounded queue ──► writer thread ──► SQLite (batch upsert)

Everything is stdlib: ``concurrent.futures`` + ``queue`` + ``threading``.
"""
from __future__ import annotations

import argparse
import logging
import queue
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed

import requests

from providers import PROVIDERS, get_provider, list_providers
from providers.base import DEFAULT_HEADERS
from storage import DEFAULT_DB, Database, _now

log = logging.getLogger("pipeline")

# One requests.Session per thread => keep-alive connection pooling without
# any cross-thread sharing.
_tls = threading.local()


def _provider(name: str):
    if not hasattr(_tls, "session"):
        sess = requests.Session()
        sess.headers.update(DEFAULT_HEADERS)
        _tls.session = sess
    return get_provider(name, session=_tls.session)


class RateLimiter:
    """Keeps a minimum interval between requests of the same provider."""

    def __init__(self, min_interval: float):
        self.min_interval = min_interval
        self._lock = threading.Lock()
        self._next_ok = 0.0

    def wait(self):
        if self.min_interval <= 0:
            return
        with self._lock:
            now = time.monotonic()
            pause = self._next_ok - now
            if pause > 0:
                time.sleep(pause)
            self._next_ok = time.monotonic() + self.min_interval


class ParallelMiner:
    """Fetches every page of every provider in parallel and streams the
    results into the database."""

    def __init__(self, db: Database, providers: list[str] | None = None,
                 max_pages: int | None = None, harvest_workers: int = 16,
                 per_provider: int = 6, delay: float = 0.05,
                 batch: int = 400, retries: int = 3):
        self.db = db
        self.names = providers or list(PROVIDERS)
        self.max_pages = max_pages
        self.harvest_workers = max(1, harvest_workers)
        self.per_provider = max(1, per_provider)
        self.delay = max(0.0, delay)
        self.batch = max(50, batch)
        self.retries = max(0, retries)

    # ------------------------------------------------------------------ #
    def run(self, progress_cb=None) -> dict:
        started = _now()
        t0 = time.monotonic()
        self.db.upsert_providers(list_providers())
        log.info("⚙  %d provider(s), workers=%d global / %d per provider, "
                 "delay=%.2fs", len(self.names), self.harvest_workers,
                 self.per_provider, self.delay)

        # ---- Phase 1: discovery (one request per provider, parallel) -- #
        pages_map: dict[str, int] = {}
        first_batch: dict[str, list[dict]] = {}
        with ThreadPoolExecutor(max_workers=len(self.names)) as ex:
            futs = {ex.submit(self._discover, n): n for n in self.names}
            for fut in as_completed(futs):
                name = futs[fut]
                try:
                    proxies, tp = fut.result()
                except Exception as exc:  # noqa: BLE001
                    log.error("[%s] discovery failed: %s", name, exc)
                    continue
                if tp:
                    if self.max_pages:
                        tp = min(tp, self.max_pages)
                    pages_map[name] = max(1, tp)
                    first_batch[name] = proxies or []
                    log.info("[%s] discovered: %d page(s)", name, tp)
                else:
                    log.error("[%s] no pages discovered — skipped", name)

        total_pages = sum(pages_map.values())
        log.info("📖 total pages to harvest: %d", total_pages)

        # ---- Phase 2: writer thread ------------------------------------ #
        q: queue.Queue = queue.Queue(maxsize=self.batch * 4)
        result = {"inserted": 0, "updated": 0}
        writer = threading.Thread(target=self._writer, args=(q, result),
                                  daemon=True)
        writer.start()
        for plist in first_batch.values():
            if plist:
                q.put(plist)

        # ---- Phase 3: harvest every page in parallel -------------------- #
        sem = {n: threading.BoundedSemaphore(self.per_provider) for n in pages_map}
        limiter = {n: RateLimiter(self.delay) for n in pages_map}
        tasks = [(n, page) for n, tp in pages_map.items() for page in range(1, tp)]
        done = 0
        with ThreadPoolExecutor(max_workers=self.harvest_workers) as ex:
            futs = {ex.submit(self._harvest, n, page, sem[n], limiter[n]): (n, page)
                    for n, page in tasks}
            for fut in as_completed(futs):
                try:
                    proxies = fut.result()
                except Exception as exc:  # noqa: BLE001
                    proxies = None
                    log.debug("harvest error: %s", exc)
                done += 1
                if proxies:
                    q.put(proxies)
                if done % 100 == 0 or done == len(tasks):
                    log.info("   ...%d/%d pages | inserted=%d updated=%d",
                             done, len(tasks), result["inserted"],
                             result["updated"])
                if progress_cb and (done % 5 == 0 or done == len(tasks)):
                    try:
                        progress_cb(done, len(tasks), result["inserted"],
                                    result["updated"])
                    except Exception:  # noqa: BLE001
                        pass

        q.put(None)          # sentinel -> writer flushes and exits
        writer.join()

        duration_ms = int((time.monotonic() - t0) * 1000)
        summary = {
            "started": started,
            "finished": _now(),
            "providers": list(pages_map.keys()),
            "pages": total_pages,
            "inserted": result["inserted"],
            "updated": result["updated"],
            "duration_ms": duration_ms,
            "total_in_db": self.db.count(),
        }
        self.db.record_run(started, summary["finished"],
                           summary["providers"], summary["pages"],
                           summary["inserted"], summary["updated"],
                           duration_ms)
        return summary

    # ------------------------------------------------------------------ #
    def _discover(self, name: str):
        provider = _provider(name)
        html = provider.fetch_page(0, retries=self.retries)
        if html is None:
            return None, 0
        proxies, meta = provider.parse_page(html)
        return proxies, int(meta.get("total_pages", 0) or 0)

    def _harvest(self, name: str, page: int, sem, limiter: RateLimiter):
        with sem:
            limiter.wait()
            provider = _provider(name)
            html = provider.fetch_page(page, retries=self.retries)
            if html is None:
                return []
            proxies, _ = provider.parse_page(html)
            return proxies

    def _writer(self, q: queue.Queue, result: dict):
        batch: list[dict] = []
        while True:
            item = q.get()
            if item is None:
                self._flush(batch, result)
                break
            batch.extend(item)
            if len(batch) >= self.batch:
                self._flush(batch, result)
                batch = []

    def _flush(self, batch: list[dict], result: dict):
        if not batch:
            return
        ins, upd = self.db.upsert_proxies(batch)
        result["inserted"] += ins
        result["updated"] += upd


# ---------------------------------------------------------------------- #
def main() -> None:
    ap = argparse.ArgumentParser(description="Parallel proxy mining pipeline")
    ap.add_argument("--provider", "--providers", action="append", default=None,
                    help="provider name(s); repeat for many (default: all)")
    ap.add_argument("--pages", type=int, default=None,
                    help="max pages per provider (default: ALL pages)")
    ap.add_argument("--db", default=DEFAULT_DB, help="SQLite path")
    ap.add_argument("--workers", type=int, default=12,
                    help="global harvest threads")
    ap.add_argument("--per-provider", type=int, default=4,
                    help="max concurrent requests per provider")
    ap.add_argument("--delay", type=float, default=0.1,
                    help="min interval (s) between requests of one provider")
    ap.add_argument("--batch", type=int, default=400,
                    help="DB batch size")
    ap.add_argument("--out", default=None,
                    help="also export a JSON snapshot to this path")
    args = ap.parse_args()

    logging.basicConfig(level=logging.INFO,
                        format="%(asctime)s | %(levelname)-7s | %(message)s",
                        datefmt="%H:%M:%S")

    db = Database(args.db)
    miner = ParallelMiner(
        db, providers=args.provider, max_pages=args.pages,
        harvest_workers=args.workers, per_provider=args.per_provider,
        delay=args.delay, batch=args.batch)

    summary = miner.run()

    print("\n" + "=" * 60)
    print("✔ done in %.1fs — %d inserted, %d updated" %
          (summary["duration_ms"] / 1000, summary["inserted"],
           summary["updated"]))
    print("   total in DB: %d | pages harvested: %d" %
          (summary["total_in_db"], summary["pages"]))
    stats = db.stats()
    print("   sources: " + ", ".join(f"{k}={v}" for k, v in stats["sources"].items()))
    print("   protocols: " + ", ".join(stats["protocols"]))
    if args.out:
        path = db.export_json(args.out)
        print("   JSON snapshot ->", path)
    print("=" * 60)


if __name__ == "__main__":
    main()
