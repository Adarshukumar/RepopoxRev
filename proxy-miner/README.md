# 🐍 Proxy Miner v2 — parallel pipeline + SQLite dataset + full-stack UI

A self-maintaining proxy-mining system: scrapes free proxy lists from
multiple providers **in parallel**, stores everything in a **SQLite
dataset**, and serves a **full-stack dashboard + JSON API** over pure
stdlib HTTP.

```
proxy-miner/
├── providers/            # one file per source
│   ├── __init__.py       #   registry: PROVIDERS = {...}
│   ├── base.py           #   BaseProvider: fetch + pagination + retries
│   ├── common.py         #   country name -> ISO code map
│   ├── socks5proxies.py  #   #1 socks5proxies.com (HTML)
│   ├── proxymix.py       #   #2 proxymix.net      (HTML, base64 ip/port)
│   ├── proxybros.py      #   #3 proxybros.com     (HTML)
│   └── nodemaven.py      #   #4 nodemaven.com     (JSON API, per_page=10000)
├── storage.py            # SQLite dataset: schema, indexes, upsert+merge
├── pipeline.py           # parallel engine (discover → harvest → writer)
├── server.py             # HTTP API + self-maintenance (watchdog/scheduler)
├── run.py                # supervisor: restarts the server if it crashes
├── app.py                # HuggingFace Spaces entry point
├── static/index.html     # the dashboard (no external assets)
├── Dockerfile            # container build for HF Spaces / anywhere
├── data/proxies.db       # ← the dataset (SQLite, WAL mode)
└── output/proxies.json   # optional JSON snapshot export
```

## 1. Storage — the dataset

SQLite (stdlib `sqlite3`) tuned for heavy duty:
* **WAL journal mode** — readers never block the writer.
* **`proxy` (ip:port) = PRIMARY KEY** — dedup enforced by the engine.
* Indexes on country_code / source / port / anonymity + a
  `proxy_protocols` table for multi-protocol rows.
* Re-runs **merge** (protocols unioned, freshest fields win, `source`
  first-seen) — never duplicate.

## 2. Parallel pipeline

```
discovery (1 req/provider, in parallel)
   └─► harvest: ALL pages of ALL providers in ONE shared thread pool
          (per-provider concurrency cap + rate limit + retries)
              └─► bounded queue ──► writer thread ──► SQLite batch upsert
```

* All providers + all pages run **at the same time**, streaming results
  into the DB as they finish (backpressure via bounded queue).
* Thread-local `requests.Session` keep-alive pooling → fast, connection
  reuse, no cross-thread sharing.

## 3. Self-maintenance (server fixes itself)

| mechanism      | what it does                                             |
|----------------|----------------------------------------------------------|
| safe handlers  | any request error → JSON 500 + logged, server never dies |
| watchdog thread| health-checks DB, reconnects on fault, checkpoints WAL   |
| scheduler      | optional periodic auto-mining keeps data fresh           |
| auto-bootstrap | first boot with empty DB mines a few pages automatically |
| supervisor     | `run.py` restarts the server if it ever crashes          |
| console logs   | everything is streamed to stdout **and** `/api/logs`     |

## Run it

```bash
pip install -r requirements.txt

# supervised (self-repairing) — recommended
python3 run.py --host 0.0.0.0 --port 8080

# or plain
python3 server.py --host 0.0.0.0 --port 8080 --auto-refresh 30

# CLI-only full mining (uncapped)
python3 miner.py            # all providers, all pages
python3 miner.py --pages 20 # first 20 pages per provider
```

## API

| method | endpoint | description |
|--------|----------|-------------|
| GET  | `/api/proxies` | list proxies — `protocol`, `country_code`, `anonymity`, `city`, `source`, `q` (search), `sort`, `order`, `limit`, `offset` |
| GET  | `/api/proxies/raw` | plain `ip:port` list |
| GET  | `/api/stats` | counts, protocol/country/source aggregates, last run, scheduler |
| GET  | `/api/health` | uptime, db health, scheduler, active run, last error |
| GET  | `/api/providers` | registered providers |
| GET  | `/api/logs?tail=200` | recent server logs (the console) |
| GET  | `/api/export` | full dataset as JSON download |
| GET  | `/api/run` · `/api/run/<id>` | mining-run status + live progress |
| POST | `/api/mine?provider=a&provider=b&pages=20` | start a run — **pages capped at 20** |
| POST | `/api/scheduler?enabled=1&interval_min=30&pages=5` | configure auto-refresh |

## HuggingFace Spaces

`app.py` + `Dockerfile` are ready. In a Docker Space the image builds
itself; `run.py` keeps the server alive, the app binds `0.0.0.0:${PORT}`,
and the dashboard + API are served on the Space URL.

## Adding a provider

1. Create `providers/myprovider.py`, subclass `BaseProvider`, implement
   `page_url(page)` + `parse_page(html)` (nodemaven is the JSON example).
2. Register it in `providers/__init__.py` → `PROVIDERS`.

Everything (pipeline, dataset, API, dashboard) picks it up automatically.
