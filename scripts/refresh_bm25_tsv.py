#!/usr/bin/env python3
"""Rebuild BM25 content_tsv with jieba tokenization."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "backend"))

from app.infrastructure.vector.pgvector.store import refresh_bm25_tsv  # noqa: E402


def main() -> None:
    kb = sys.argv[1] if len(sys.argv) > 1 else None
    n = refresh_bm25_tsv(knowledge_base_id=kb)
    print(f"refreshed {n} rows" + (f" for kb={kb}" if kb else " (all)"))


if __name__ == "__main__":
    main()
