"""Base provider interface for the proxy miner.

Every provider inherits from BaseProvider and implements:
  - page_url(page)   -> the URL for a given (0-indexed) page
  - parse_page(html) -> (proxies, meta) where proxies is a list of dicts
                        and meta holds pagination info (total, total_pages)
"""
from __future__ import annotations

import logging
import time
from abc import ABC, abstractmethod

import requests

log = logging.getLogger("miner")

DEFAULT_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    ),
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "en-US,en;q=0.9",
}


class BaseProvider(ABC):
    """Common fetching / pagination logic shared by all providers."""

    name: str = "base"
    label: str = "Base Provider"

    def __init__(self, timeout: int = 30, delay: float = 1.5,
                 session: requests.Session | None = None):
        self.timeout = timeout
        self.delay = delay
        self.session = session or requests.Session()
        self.session.headers.update(DEFAULT_HEADERS)

    # ------------------------------------------------------------------ #
    #  To be implemented by each provider
    # ------------------------------------------------------------------ #
    @abstractmethod
    def page_url(self, page: int) -> str:
        """Return the URL of a 0-indexed page."""

    @abstractmethod
    def parse_page(self, html: str) -> tuple[list[dict], dict]:
        """Parse a raw HTML page.

        Returns (proxies, meta). ``meta`` should contain at least
        ``total`` and ``total_pages``.
        """

    # ------------------------------------------------------------------ #
    #  Shared fetching helpers
    # ------------------------------------------------------------------ #
    def fetch_page(self, page: int, retries: int = 2) -> str | None:
        """Download one page, retrying on transient errors."""
        url = self.page_url(page)
        for attempt in range(retries + 1):
            try:
                resp = self.session.get(url, timeout=self.timeout)
                resp.raise_for_status()
                return resp.text
            except requests.RequestException as exc:
                log.warning("[%s] fetch failed (attempt %d/%d): %s",
                            self.name, attempt + 1, retries + 1, exc)
                if attempt < retries:
                    time.sleep(self.delay * (attempt + 1))
        return None

    def fetch(self, max_pages: int | None = None):
        """Generator that yields one dict per page:

            {"page": n, "proxies": [...], "total": T, "total_pages": P}

        Stops early when a page fails or when max_pages is reached.
        """
        html = self.fetch_page(0)
        if html is None:
            log.error("[%s] could not fetch first page", self.name)
            return

        proxies, meta = self.parse_page(html)
        total = int(meta.get("total", 0) or 0)
        total_pages = int(meta.get("total_pages", 0) or 0)

        if max_pages:
            total_pages = min(total_pages, max_pages)
        if total_pages < 1:
            total_pages = 1

        yield {"page": 1, "proxies": proxies, "total": total,
               "total_pages": total_pages}

        for page in range(1, total_pages):
            time.sleep(self.delay)
            html = self.fetch_page(page)
            if html is None:
                log.warning("[%s] stopping early at page %d/%d",
                            self.name, page + 1, total_pages)
                break
            proxies, _ = self.parse_page(html)
            yield {"page": page + 1, "proxies": proxies, "total": total,
                   "total_pages": total_pages}
