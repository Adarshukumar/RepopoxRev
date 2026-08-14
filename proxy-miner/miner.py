"""Proxy miner — CLI entry point.

Runs the parallel pipeline (see pipeline.py) and stores everything in
the SQLite dataset (data/proxies.db).

Usage:
    python3 miner.py                       # all providers, ALL pages
    python3 miner.py --provider proxymix   # one provider
    python3 miner.py --pages 3             # first 3 pages per provider
    python3 miner.py --out output/proxies.json
"""
from pipeline import main

if __name__ == "__main__":
    main()
