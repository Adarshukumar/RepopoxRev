"""Provider #1: socks5proxies.com free proxy list.

Pagination (25 per page):
    page 1 -> https://socks5proxies.com/free-proxy-list/
    page 2 -> https://socks5proxies.com/free-proxy-list/?limit=25&offset=25
    page 3 -> https://socks5proxies.com/free-proxy-list/?limit=25&offset=50
    ...
"""
from __future__ import annotations

import math
import re

from bs4 import BeautifulSoup

from .base import BaseProvider

BASE_URL = "https://socks5proxies.com/free-proxy-list/"
PAGE_SIZE = 25

_SHOWING_RE = re.compile(r"Showing\s*(\d+)\s*-\s*(\d+)\s*of\s*(\d+)")
_SCORE_RE = re.compile(r"score\s+(\d+)")
_TRAILING_DIGITS_RE = re.compile(r"\s*\d+\s*$")


class Socks5ProxiesProvider(BaseProvider):
    name = "socks5proxies"
    label = "Socks5Proxies.com — Free Proxy List"

    # ------------------------------------------------------------------ #
    def page_url(self, page: int) -> str:
        if page <= 0:
            return BASE_URL
        return f"{BASE_URL}?limit={PAGE_SIZE}&offset={page * PAGE_SIZE}"

    # ------------------------------------------------------------------ #
    def parse_page(self, html: str) -> tuple[list[dict], dict]:
        soup = BeautifulSoup(html, "html.parser")

        # Pagination info lives in a "Showing X-Y of Z" text node.
        text = soup.get_text(" ", strip=True)
        m = _SHOWING_RE.search(text)
        if m:
            total = int(m.group(3))
            total_pages = math.ceil(total / PAGE_SIZE)
        else:
            total = 0
            total_pages = 0

        proxies: list[dict] = []
        table = soup.find("table", role="grid")
        if table is not None:
            tbody = table.find("tbody")
            if tbody is not None:
                for tr in tbody.find_all("tr", recursive=False):
                    proxy = self._parse_row(tr)
                    if proxy:
                        proxies.append(proxy)

        return proxies, {"total": total, "total_pages": total_pages}

    # ------------------------------------------------------------------ #
    def _parse_row(self, tr) -> dict | None:
        tds = tr.find_all("td", recursive=False)
        if len(tds) < 10:
            return None

        # 1) Proxy ip:port --------------------------------------------- #
        proxy_span = tds[1].find("span")
        proxy = proxy_span.get_text(strip=True) if proxy_span else ""
        proxy = re.sub(r"\s+", "", proxy)
        if not re.fullmatch(r"\d{1,3}(\.\d{1,3}){3}:\d{1,5}", proxy):
            return None
        ip, _, port_str = proxy.rpartition(":")
        try:
            port = int(port_str)
        except ValueError:
            port = None

        # 2) Country: "🇺🇸 United States US" ---------------------------- #
        country_text = tds[2].get_text(" ", strip=True).split()
        flag = country_text[0] if country_text else None
        code = country_text[-1] if len(country_text) >= 2 else None
        name = " ".join(country_text[1:-1]) if len(country_text) >= 3 else None
        # Normalise placeholders ("--") and non-ISO codes to None.
        if code in ("", "--") or not re.fullmatch(r"[A-Za-z]{2}", code or ""):
            code = None
        if name in ("", "--"):
            name = None
        if flag in ("--", ""):
            flag = None

        # 3) City -------------------------------------------------------- #
        city = tds[3].get_text(strip=True)
        if city in ("", "--"):
            city = None

        # 4) IP risk: badge + optional score ---------------------------- #
        level, score = self._parse_risk(tds[4])

        # 5) Protocols ---------------------------------------------------- #
        protocols = [p.get_text(strip=True)
                     for p in tds[5].find_all("span")]

        # 6) Anonymity ----------------------------------------------------- #
        anonymity = tds[6].get_text(strip=True) or None

        # 7) Uptime --------------------------------------------------------- #
        uptime = None
        up_span = tds[7].find("span")
        if up_span:
            label = up_span.get("aria-label") or up_span.get_text(strip=True)
            um = re.search(r"(\d+)%", label)
            uptime = f"{um.group(1)}%" if um else (label or None)

        # 8) Delay ----------------------------------------------------------- #
        delay = tds[8].get_text(strip=True) or None

        # 9) Checked status + last seen ------------------------------------- #
        status = None
        last_seen = None
        badge = tds[9].find("span")
        if badge:
            status = badge.get_text(strip=True) or None
        sub = tds[9].find("div")
        if sub:
            raw = sub.get_text(strip=True)
            if raw and raw.lower().startswith("seen"):
                last_seen = raw[4:].strip() or None

        return {
            "ip": ip,
            "port": port,
            "proxy": proxy,
            "country": name,
            "country_code": code,
            "flag": flag,
            "city": city,
            "ip_risk": {"level": level, "score": score},
            "protocols": protocols,
            "anonymity": anonymity,
            "uptime": uptime,
            "delay": delay,
            "checked": status,
            "last_seen": last_seen,
            "source": self.name,
        }

    # ------------------------------------------------------------------ #
    @staticmethod
    def _parse_risk(td) -> tuple[str | None, int | None]:
        span = td.find("span")
        if span is None:
            return None, None

        title = span.get("title") or ""
        sm = _SCORE_RE.search(title)
        score = int(sm.group(1)) if sm else None

        level = _TRAILING_DIGITS_RE.sub("", span.get_text(" ", strip=True))
        level = level.strip() or None
        return level, score
