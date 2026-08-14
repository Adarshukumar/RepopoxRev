"""Provider: proxymix.net/freeproxy (HTML, 100 rows/page).

Pagination:
    page 1 -> https://proxymix.net/freeproxy
    page 2 -> https://proxymix.net/freeproxy?page=2
    ...

The IP and port cells are rendered client-side from base64 in the
``data-ip`` / ``data-port`` attributes, so we decode those directly.
"""
from __future__ import annotations

import base64
import re

from bs4 import BeautifulSoup

from .base import BaseProvider
from .common import country_code

BASE_URL = "https://proxymix.net/freeproxy"
PAGE_SIZE = 100

_PROTOCOLS = {"HTTP", "HTTPS", "SOCKS4", "SOCKS5"}
_ANONYMITY = {"ANON": "Anonymous", "ELITE": "Elite", "TRANSPARENT": "Transparent"}


class ProxymixProvider(BaseProvider):
    name = "proxymix"
    label = "ProxyMix.net — Free Proxy List"

    # ------------------------------------------------------------------ #
    def page_url(self, page: int) -> str:
        if page <= 0:
            return BASE_URL
        return f"{BASE_URL}?page={page + 1}"

    # ------------------------------------------------------------------ #
    def parse_page(self, html: str) -> tuple[list[dict], dict]:
        soup = BeautifulSoup(html, "html.parser")
        total_pages = self._total_pages(soup)

        proxies: list[dict] = []
        table = soup.find("table", id="table_proxies")
        if table is not None:
            tbody = table.find("tbody")
            if tbody is not None:
                for tr in tbody.find_all("tr", recursive=False):
                    proxy = self._parse_row(tr)
                    if proxy:
                        proxies.append(proxy)

        return proxies, {
            "total": total_pages * PAGE_SIZE,
            "total_pages": total_pages,
        }

    # ------------------------------------------------------------------ #
    def _total_pages(self, soup: BeautifulSoup) -> int:
        """Last page number = max of the ?page=N links (Laravel always
        renders the final page in its pagination)."""
        pages = {1}
        pag = soup.find("ul", class_="pagination")
        if pag is not None:
            for a in pag.find_all("a", href=True):
                m = re.search(r"page=(\d+)", a["href"])
                if m:
                    pages.add(int(m.group(1)))
        return max(pages)

    # ------------------------------------------------------------------ #
    def _parse_row(self, tr) -> dict | None:
        tds = tr.find_all("td", recursive=False)
        if len(tds) < 7:
            return None

        # 1) IP (base64 data-ip, fallback to text) --------------------- #
        ip = self._b64_attr(tds[1], "data-ip") or tds[1].get_text(strip=True)
        # 2) Port (base64 data-port) ----------------------------------- #
        port_raw = self._b64_attr(tds[2], "data-port") or tds[2].get_text(strip=True)

        ip = re.sub(r"\s+", "", ip)
        if not re.fullmatch(r"\d{1,3}(\.\d{1,3}){3}", ip):
            return None
        try:
            port = int(port_raw)
        except (TypeError, ValueError):
            port = None
        proxy = f"{ip}:{port}" if port is not None else ip

        # 3) Type cell: protocol(s) + anonymity ------------------------- #
        labels = [a.get_text(strip=True) for a in tds[3].find_all("a")]
        protocols = [l for l in labels if l.upper() in _PROTOCOLS]
        anonymity = next((_ANONYMITY[l.upper()] for l in labels
                          if l.upper() in _ANONYMITY), None)

        # 4) Country code ------------------------------------------------ #
        cc = None
        a = tds[4].find("a")
        if a:
            cc = country_code(a.get_text(strip=True))

        # 5) Speed (ms) ---------------------------------------------------- #
        speed = tds[5].get_text(strip=True)
        delay = f"{speed} ms" if speed and speed.isdigit() else (speed or None)

        # 6) Last checkup: "14:01:13 14.08.2026 (9 minutes ago)" ---------- #
        raw = tds[6].get_text(" ", strip=True)
        m = re.search(r"\((.*?)\)\s*$", raw)
        last_seen = m.group(1) if m else None
        checked = re.sub(r"\s*\(.*?\)\s*$", "", raw).strip() or None

        return {
            "ip": ip,
            "port": port,
            "proxy": proxy,
            "country": None,
            "country_code": cc,
            "flag": None,
            "city": None,
            "ip_risk": {"level": None, "score": None},
            "protocols": protocols,
            "anonymity": anonymity,
            "uptime": None,
            "delay": delay,
            "checked": checked,
            "last_seen": last_seen,
            "source": self.name,
        }

    # ------------------------------------------------------------------ #
    @staticmethod
    def _b64_attr(td, attr: str) -> str | None:
        val = td.get(attr)
        if not val:
            return None
        try:
            return base64.b64decode(val).decode("utf-8", errors="ignore").strip()
        except Exception:
            return None
