"""HuggingFace Spaces entry point.

HF Spaces injects a PORT env var; we default to it and bind 0.0.0.0.
Everything else (pipeline, dataset, dashboard, self-maintenance) lives
in server.py.
"""
import os

from server import main

if __name__ == "__main__":
    os.environ.setdefault("PORT", "7860")
    main()
