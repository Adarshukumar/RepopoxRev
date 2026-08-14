"""Provider: NodeMaven free proxies (JSON API).

GET https://freeproxies.nodemaven.com/proxies?per_page=10000

Returns a JSON object:
    {"page", "per_page", "proxies": [...], "total"}

Each proxy record:
    {"country", "ip_address", "port", "proxy", "protocol", "type",
     "latency", "response", "google", "last_checked", "source_name", ...}

We request per_page=10000 so the whole list normally arrives in a single
request, but pagination is still handled just in case ``total`` exceeds it.
"""
from __future__ import annotations

import json
import math

from .base import BaseProvider
from .common import country_code

API_URL = "https://freeproxies.nodemaven.com/proxies"
PER_PAGE = 10000


class NodeMavenProvider(BaseProvider):
    name = "nodemaven"
    label = "NodeMaven — Free Proxies API"

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        # The endpoint expects to be called from nodemaven.com (CORS).
        self.session.headers.update({
            "Origin": "https://nodemaven.com",
            "Referer": "https://nodemaven.com/",
            "Accept": "application/json",
        })

    # ------------------------------------------------------------------ #
    def page_url(self, page: int) -> str:
        return f"{API_URL}?per_page={PER_PAGE}&page={page + 1}"

    # ------------------------------------------------------------------ #
    def parse_page(self, html: str) -> tuple[list[dict], dict]:
        try:
            data = json.loads(html)
        except json.JSONDecodeError:
            return [], {"total": 0, "total_pages": 0}

        per_page = int(data.get("per_page") or PER_PAGE)
        total = int(data.get("total") or 0)
        total_pages = max(1, math.ceil(total / per_page)) if total else 0

        proxies = []
        for rec in data.get("proxies", []):
            proxy = self._parse_record(rec)
            if proxy:
                proxies.append(proxy)

        return proxies, {"total": total, "total_pages": total_pages}

    # ------------------------------------------------------------------ #
    def _parse_record(self, rec: dict) -> dict | None:
        ip = (rec.get("ip_address") or "").strip()
        port_raw = str(rec.get("port") or "").strip()
        proxy = (rec.get("proxy") or "").strip()

        if not proxy:
            if not ip:
                return None
            proxy = f"{ip}:{port_raw}" if port_raw else ip
        try:
            port = int(port_raw)
        except (TypeError, ValueError):
            port = None

        protocol = (rec.get("protocol") or "").upper()
        protocols = [protocol] if protocol in {"HTTP", "HTTPS", "SOCKS4", "SOCKS5"} else []

        latency = str(rec.get("latency") or "").strip()
        delay = f"{latency} ms" if latency and latency.isdigit() else (latency or None)

        return {
            "ip": ip or proxy.split(":")[0],
            "port": port,
            "proxy": proxy,
            "country": rec.get("country") or None,
            "country_code": country_code(rec.get("country")),
            "flag": None,
            "city": None,
            "ip_risk": {"level": None, "score": None},
            "protocols": protocols,
            "anonymity": rec.get("type") or None,
            "uptime": None,
            "delay": delay,
            "checked": rec.get("last_checked") or None,
            "last_seen": None,
            "upstream_source": rec.get("source_name") or None,
            "response": rec.get("response") or None,
            "google": rec.get("google") or None,
            "source": self.name,
        }
