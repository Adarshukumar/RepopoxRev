"""SQLite storage engine — the persistent "dataset" for mined proxies.

Why SQLite:
  * embedded, zero-configuration, no separate DB server => no extra load
    ("heat") on the box that runs the API;
  * WAL journal mode + indexes => fast concurrent reads while the miner
    keeps writing;
  * a UNIQUE constraint on ``proxy`` (ip:port) => dedup enforced by the
    engine itself, even across providers and repeated runs.

Schema
------
proxies           one row per unique ip:port
proxy_protocols   (proxy, protocol) pairs — a proxy can serve several
providers         provider metadata
runs              history of mining runs
"""
from __future__ import annotations

import json
import os
import sqlite3
import threading
from datetime import datetime

DB_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data")
DEFAULT_DB = os.path.join(DB_DIR, "proxies.db")

# Scalar fields (everything except protocols + timestamps).
SCALAR_FIELDS = [
    "ip", "port", "country", "country_code", "flag", "city",
    "risk_level", "risk_score", "anonymity", "uptime", "delay",
    "checked", "last_seen", "upstream_source", "response", "google",
]
# ``source`` is special-cased in merge_proxy: first-seen wins, so stats
# don't drift when the same proxy is re-mined by another provider.

_COLUMNS = [
    "proxy", "ip", "port", "country", "country_code", "flag", "city",
    "risk_level", "risk_score", "anonymity", "uptime", "delay",
    "checked", "last_seen", "source", "upstream_source", "response",
    "google", "first_seen", "last_updated",
]

_SCHEMA = """
CREATE TABLE IF NOT EXISTS proxies (
    proxy           TEXT PRIMARY KEY,
    ip              TEXT,
    port            INTEGER,
    country         TEXT,
    country_code    TEXT,
    flag            TEXT,
    city            TEXT,
    risk_level      TEXT,
    risk_score      INTEGER,
    anonymity       TEXT,
    uptime          TEXT,
    delay           TEXT,
    checked         TEXT,
    last_seen       TEXT,
    source          TEXT,
    upstream_source TEXT,
    response        TEXT,
    google          TEXT,
    first_seen      TEXT NOT NULL,
    last_updated    TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_proxies_cc   ON proxies(country_code);
CREATE INDEX IF NOT EXISTS idx_proxies_src  ON proxies(source);
CREATE INDEX IF NOT EXISTS idx_proxies_port ON proxies(port);
CREATE INDEX IF NOT EXISTS idx_proxies_anon ON proxies(anonymity);

CREATE TABLE IF NOT EXISTS proxy_protocols (
    proxy    TEXT NOT NULL REFERENCES proxies(proxy) ON DELETE CASCADE,
    protocol TEXT NOT NULL,
    PRIMARY KEY (proxy, protocol)
);
CREATE INDEX IF NOT EXISTS idx_pp_protocol ON proxy_protocols(protocol);

CREATE TABLE IF NOT EXISTS providers (
    name  TEXT PRIMARY KEY,
    label TEXT
);

CREATE TABLE IF NOT EXISTS runs (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    started     TEXT,
    finished    TEXT,
    providers   TEXT,
    pages       INTEGER,
    inserted    INTEGER,
    updated     INTEGER,
    duration_ms INTEGER
);
"""

_UPSERT_SQL = """
INSERT INTO proxies (
    proxy, ip, port, country, country_code, flag, city,
    risk_level, risk_score, anonymity, uptime, delay, checked, last_seen,
    source, upstream_source, response, google, first_seen, last_updated
) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
ON CONFLICT(proxy) DO UPDATE SET
    ip=excluded.ip, port=excluded.port, country=excluded.country,
    country_code=excluded.country_code, flag=excluded.flag, city=excluded.city,
    risk_level=excluded.risk_level, risk_score=excluded.risk_score,
    anonymity=excluded.anonymity, uptime=excluded.uptime, delay=excluded.delay,
    checked=excluded.checked, last_seen=excluded.last_seen,
    source=excluded.source, upstream_source=excluded.upstream_source,
    response=excluded.response, google=excluded.google,
    last_updated=excluded.last_updated
"""

_PROTOCOL_INSERT_SQL = (
    "INSERT OR IGNORE INTO proxy_protocols (proxy, protocol) VALUES (?, ?)")


def _now() -> str:
    return datetime.now().astimezone().isoformat(timespec="seconds")


def merge_proxy(base: dict, incoming: dict) -> dict:
    """Merge an incoming (normalized) proxy into an existing one.

    * protocols are unioned,
    * non-empty incoming fields overwrite (freshest data wins),
    * first_seen is preserved, last_updated refreshed.
    """
    out = dict(base)
    protos = [p for p in out.get("protocols") or [] if p]
    for p in incoming.get("protocols") or []:
        if p and p not in protos:
            protos.append(p)
    out["protocols"] = protos

    for field in SCALAR_FIELDS:
        value = incoming.get(field)
        if value not in (None, ""):
            out[field] = value
    # first-seen provider wins; keeps stats stable across re-runs
    if not out.get("source"):
        out["source"] = incoming.get("source")
    out["proxy"] = incoming.get("proxy") or base.get("proxy")
    out["last_updated"] = _now()
    return out


class Database:
    """Thread-safe-ish SQLite access. One shared connection (WAL mode)
    guarded by a write lock."""

    def __init__(self, path: str | None = None):
        self.path = path or DEFAULT_DB
        os.makedirs(os.path.dirname(self.path), exist_ok=True)
        self._wlock = threading.RLock()
        self._init_conn()

    def _init_conn(self) -> None:
        self._conn = sqlite3.connect(self.path, check_same_thread=False)
        self._conn.row_factory = sqlite3.Row
        self._conn.execute("PRAGMA journal_mode=WAL")
        self._conn.execute("PRAGMA synchronous=NORMAL")
        self._conn.execute("PRAGMA busy_timeout=10000")
        self._conn.execute("PRAGMA cache_size=-64000")
        self._conn.execute("PRAGMA temp_store=MEMORY")
        self._conn.executescript(_SCHEMA)

    def healthcheck(self) -> bool:
        """True if the DB answers a trivial query."""
        try:
            self._conn.execute("SELECT 1").fetchone()
            return True
        except Exception:
            return False

    def reconnect(self) -> None:
        """Drop + rebuild the connection (self-healing after a fault)."""
        with self._wlock:
            try:
                self._conn.close()
            except Exception:
                pass
            self._init_conn()

    def checkpoint(self) -> None:
        """Keep the WAL file from growing unbounded."""
        try:
            with self._wlock, self._conn:
                self._conn.execute("PRAGMA wal_checkpoint(PASSIVE)")
        except Exception:
            pass

    # ------------------------------------------------------------------ #
    #  Writers
    # ------------------------------------------------------------------ #
    def upsert_providers(self, providers: list[dict]) -> None:
        with self._wlock, self._conn:
            self._conn.executemany(
                "INSERT INTO providers(name, label) VALUES(?, ?) "
                "ON CONFLICT(name) DO UPDATE SET label=excluded.label",
                [(p["name"], p["label"]) for p in providers])

    def upsert_proxies(self, records: list[dict]) -> tuple[int, int]:
        """Normalize + dedup + merge a batch, then write it in one
        transaction. Returns (inserted, updated)."""
        if not records:
            return 0, 0

        # 1) within-batch merge ---------------------------------------- #
        merged: dict[str, dict] = {}
        for rec in records:
            key = rec.get("proxy") or ""
            if not key:
                continue
            norm = self._norm(rec)
            merged[key] = merge_proxy(merged[key], norm) if key in merged else norm
        keys = list(merged)

        # 2) merge against what's already stored ----------------------- #
        existing = self._fetch_existing(keys)
        finals, inserted, updated = [], 0, 0
        for key in keys:
            if key in existing:
                updated += 1
                finals.append(merge_proxy(existing[key], merged[key]))
            else:
                inserted += 1
                finals.append(merged[key])

        # 3) single transaction ----------------------------------------- #
        with self._wlock, self._conn:
            self._conn.executemany(_UPSERT_SQL, [self._upsert_args(f) for f in finals])
            pairs = [(f["proxy"], p) for f in finals
                     for p in (f.get("protocols") or []) if p]
            if pairs:
                self._conn.executemany(_PROTOCOL_INSERT_SQL, pairs)
        return inserted, updated

    def record_run(self, started, finished, providers, pages,
                   inserted, updated, duration_ms) -> None:
        with self._wlock, self._conn:
            self._conn.execute(
                "INSERT INTO runs(started, finished, providers, pages, "
                "inserted, updated, duration_ms) VALUES (?,?,?,?,?,?,?)",
                (started, finished, json.dumps(providers), pages,
                 inserted, updated, duration_ms))

    # ------------------------------------------------------------------ #
    #  Readers
    # ------------------------------------------------------------------ #
    def count(self) -> int:
        return self._conn.execute("SELECT COUNT(*) FROM proxies").fetchone()[0]

    def query(self, *, country_code=None, protocol=None, anonymity=None,
              city=None, source=None, search=None, sort="proxy",
              order="asc", limit=None, offset=0):
        where, args = [], []
        if country_code:
            where.append("p.country_code = ?"); args.append(country_code.upper())
        if protocol:
            where.append("EXISTS (SELECT 1 FROM proxy_protocols pp "
                         "WHERE pp.proxy = p.proxy AND pp.protocol = ?)")
            args.append(protocol.upper())
        if anonymity:
            where.append("p.anonymity = ?"); args.append(anonymity)
        if city:
            where.append("p.city = ?"); args.append(city)
        if source:
            where.append("p.source = ?"); args.append(source)
        if search:
            like = f"%{search}%"
            where.append("(p.proxy LIKE ? OR p.ip LIKE ? OR p.country LIKE ? "
                         "OR p.city LIKE ? OR p.anonymity LIKE ? OR p.source LIKE ?)")
            args += [like] * 6
        clause = ("WHERE " + " AND ".join(where)) if where else ""

        total = self._conn.execute(
            f"SELECT COUNT(*) FROM proxies p {clause}", args).fetchone()[0]

        sort_map = {"proxy": "p.proxy", "port": "p.port",
                    "country": "p.country_code", "source": "p.source",
                    "anonymity": "p.anonymity", "updated": "p.last_updated",
                    "first_seen": "p.first_seen"}
        col = sort_map.get(sort, "p.proxy")
        direction = "DESC" if str(order).lower() == "desc" else "ASC"

        sql = (
            "SELECT p.*, (SELECT group_concat(protocol) FROM proxy_protocols pp "
            f"WHERE pp.proxy = p.proxy) AS protocols "
            f"FROM proxies p {clause} ORDER BY {col} {direction}, p.proxy ASC"
        )
        if limit is not None:
            sql += " LIMIT ? OFFSET ?"
            args += [int(limit), int(offset)]
        rows = self._conn.execute(sql, args).fetchall()
        return total, [self._to_proxy(dict(r)) for r in rows]

    def stats(self) -> dict:
        count = self.count()
        countries = [r[0] for r in self._conn.execute(
            "SELECT DISTINCT country_code FROM proxies "
            "WHERE country_code IS NOT NULL ORDER BY country_code")]
        protocols = [r[0] for r in self._conn.execute(
            "SELECT DISTINCT protocol FROM proxy_protocols ORDER BY protocol")]
        sources = {r[0]: r[1] for r in self._conn.execute(
            "SELECT source, COUNT(*) FROM proxies GROUP BY source ORDER BY 2 DESC")}
        providers = [{"name": r["name"], "label": r["label"]}
                     for r in self._conn.execute(
                         "SELECT name, label FROM providers ORDER BY name")]
        last = self._conn.execute(
            "SELECT started, finished, pages, inserted, updated, duration_ms "
            "FROM runs ORDER BY id DESC LIMIT 1").fetchone()
        return {
            "count": count,
            "providers": providers,
            "countries": countries,
            "protocols": protocols,
            "sources": sources,
            "protocol_counts": self.protocol_counts(),
            "country_counts": self.country_counts(15),
            "last_run": {
                "started": last[0], "finished": last[1], "pages": last[2],
                "inserted": last[3], "updated": last[4],
                "duration_ms": last[5],
            } if last else None,
        }

    def protocol_counts(self, limit: int = 20) -> dict:
        rows = self._conn.execute(
            "SELECT protocol, COUNT(*) AS n FROM proxy_protocols "
            "GROUP BY protocol ORDER BY n DESC LIMIT ?", (limit,)).fetchall()
        return {r[0]: r[1] for r in rows}

    def country_counts(self, limit: int = 15) -> dict:
        rows = self._conn.execute(
            "SELECT country_code, COUNT(*) AS n FROM proxies "
            "WHERE country_code IS NOT NULL GROUP BY country_code "
            "ORDER BY n DESC LIMIT ?", (limit,)).fetchall()
        return {r[0]: r[1] for r in rows}

    def export_json(self, path: str) -> str:
        total, rows = self.query(limit=None)
        payload = {
            "generated_at": _now(),
            "count": total,
            "proxies": rows,
        }
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as fh:
            json.dump(payload, fh, indent=2, ensure_ascii=False)
        return path

    # ------------------------------------------------------------------ #
    #  Internals
    # ------------------------------------------------------------------ #
    @staticmethod
    def _norm(rec: dict) -> dict:
        risk = rec.get("ip_risk") or {}
        return {
            "proxy": rec.get("proxy"),
            "ip": rec.get("ip"),
            "port": rec.get("port"),
            "country": rec.get("country"),
            "country_code": rec.get("country_code"),
            "flag": rec.get("flag"),
            "city": rec.get("city"),
            "risk_level": risk.get("level"),
            "risk_score": risk.get("score"),
            "anonymity": rec.get("anonymity"),
            "uptime": rec.get("uptime"),
            "delay": rec.get("delay"),
            "checked": rec.get("checked"),
            "last_seen": rec.get("last_seen"),
            "source": rec.get("source"),
            "upstream_source": rec.get("upstream_source"),
            "response": rec.get("response"),
            "google": rec.get("google"),
            "protocols": [p for p in (rec.get("protocols") or []) if p],
            "first_seen": _now(),
            "last_updated": _now(),
        }

    def _fetch_existing(self, keys: list[str]) -> dict[str, dict]:
        out: dict[str, dict] = {}
        for i in range(0, len(keys), 400):
            chunk = keys[i:i + 400]
            marks = ",".join("?" * len(chunk))
            sql = (
                "SELECT p.*, (SELECT group_concat(protocol) FROM "
                "proxy_protocols pp WHERE pp.proxy = p.proxy) AS protocols "
                f"FROM proxies p WHERE p.proxy IN ({marks})")
            for r in self._conn.execute(sql, chunk):
                d = dict(r)
                d["protocols"] = [x for x in (d["protocols"] or "").split(",") if x]
                out[d["proxy"]] = d
        return out

    @staticmethod
    def _upsert_args(f: dict) -> tuple:
        return tuple(f.get(c) for c in _COLUMNS)

    @staticmethod
    def _to_proxy(row: dict) -> dict:
        protos = sorted([x for x in (row.pop("protocols") or "").split(",") if x])
        return {
            "ip": row.get("ip"),
            "port": row.get("port"),
            "proxy": row.get("proxy"),
            "country": row.get("country"),
            "country_code": row.get("country_code"),
            "flag": row.get("flag"),
            "city": row.get("city"),
            "ip_risk": {"level": row.get("risk_level"),
                        "score": row.get("risk_score")},
            "protocols": protos,
            "anonymity": row.get("anonymity"),
            "uptime": row.get("uptime"),
            "delay": row.get("delay"),
            "checked": row.get("checked"),
            "last_seen": row.get("last_seen"),
            "source": row.get("source"),
            "upstream_source": row.get("upstream_source"),
            "response": row.get("response"),
            "google": row.get("google"),
            "first_seen": row.get("first_seen"),
            "last_updated": row.get("last_updated"),
        }
