"""Provider: proxybros.com/free-proxy-list/ (HTML, 40 rows/page).

Pagination:
    page 1 -> https://proxybros.com/free-proxy-list/
    page 2 -> https://proxybros.com/free-proxy-list/2/
    ...
"""
from __future__ import annotations

import re

from bs4 import BeautifulSoup

from .base import BaseProvider
from .common import country_code

BASE_URL = "https://proxybros.com/free-proxy-list/"
PAGE_SIZE = 40


class ProxyBrosProvider(BaseProvider):
    name = "proxybros"
    label = "ProxyBros.com — Free Proxy List"

    # ------------------------------------------------------------------ #
    def page_url(self, page: int) -> str:
        if page <= 0:
            return BASE_URL
        return f"{BASE_URL}{page + 1}/"

    # ------------------------------------------------------------------ #
    def parse_page(self, html: str) -> tuple[list[dict], dict]:
        soup = BeautifulSoup(html, "html.parser")
        total_pages = self._total_pages(soup)

        proxies: list[dict] = []
        table = soup.find("table", class_="proxylist-table")
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
        pages = {1}
        pag = soup.find("ul", class_="pagination")
        if pag is not None:
            for a in pag.find_all("a", href=True):
                m = re.search(r"free-proxy-list/(\d+)/?", a["href"])
                if m:
                    pages.add(int(m.group(1)))
        return max(pages)

    # ------------------------------------------------------------------ #
    def _parse_row(self, tr) -> dict | None:
        tds = tr.find_all("td", recursive=False)
        if len(tds) < 7:
            return None

        # 1) IP ---------------------------------------------------------- #
        ip_span = tds[0].find("span", class_="proxy-ip")
        ip = ip_span.get_text(strip=True) if ip_span else tds[0].get_text(strip=True)
        ip = re.sub(r"\s+", "", ip)
        if not re.fullmatch(r"\d{1,3}(\.\d{1,3}){3}", ip):
            return None

        # 2) Port ---------------------------------------------------------- #
        port_raw = tds[1].get_text(strip=True)
        try:
            port = int(port_raw)
        except (TypeError, ValueError):
            port = None

        # 3) Country code --------------------------------------------------- #
        cc = country_code(tds[2].get_text(strip=True))

        # 4) Delay ----------------------------------------------------------- #
        speed = tds[3].find("span", class_="proxylist-speed")
        delay = speed.get_text(strip=True) if speed else tds[3].get_text(strip=True)
        delay = delay or None

        # 5) Type / protocol ------------------------------------------------- #
        ptype = tds[4].get_text(strip=True).upper() or None
        protocols = [ptype] if ptype in {"HTTP", "HTTPS", "SOCKS4", "SOCKS5"} else []

        # 6) Anonymity -------------------------------------------------------- #
        anonymity = tds[5].get_text(strip=True) or None

        # 7) Last checked: "17 h" --------------------------------------------- #
        upd = tds[6].find("span", class_="proxylist-updated")
        last_seen = upd.get_text(strip=True) if upd else None

        return {
            "ip": ip,
            "port": port,
            "proxy": f"{ip}:{port}" if port is not None else ip,
            "country": None,
            "country_code": cc,
            "flag": None,
            "city": None,
            "ip_risk": {"level": None, "score": None},
            "protocols": protocols,
            "anonymity": anonymity,
            "uptime": None,
            "delay": delay,
            "checked": None,
            "last_seen": last_seen,
            "source": self.name,
        }

    # ------------------------------------------------------------------ #
    def page_size(self) -> int:
        return PAGE_SIZE
