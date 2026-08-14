"""Provider registry.

To add a new provider:
    1. Create a module in this folder (e.g. myprovider.py).
    2. Subclass ``providers.base.BaseProvider``.
    3. Register it in ``PROVIDERS`` below.
"""
from .nodemaven import NodeMavenProvider
from .proxymix import ProxymixProvider
from .proxybros import ProxyBrosProvider
from .socks5proxies import Socks5ProxiesProvider

PROVIDERS = {
    "socks5proxies": Socks5ProxiesProvider,
    "proxymix": ProxymixProvider,
    "proxybros": ProxyBrosProvider,
    "nodemaven": NodeMavenProvider,
}


def get_provider(name: str, **kwargs):
    """Instantiate a provider by name. Extra kwargs go to the constructor
    (e.g. ``session=...`` for connection pooling)."""
    cls = PROVIDERS.get(name)
    if cls is None:
        raise KeyError(f"Unknown provider '{name}'. "
                       f"Available: {', '.join(PROVIDERS)}")
    return cls(**kwargs)


def list_providers() -> list[dict]:
    """Metadata about every registered provider."""
    return [{"name": n, "label": c.label} for n, c in PROVIDERS.items()]
