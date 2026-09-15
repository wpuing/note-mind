#!/usr/bin/env python3
from __future__ import annotations

import json
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "backend"))

from app.infrastructure.vector.pgvector.store import keyword_search  # noqa: E402


def load_env() -> dict[str, str]:
    env: dict[str, str] = {}
    for line in (ROOT / ".env").read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        env[k.strip()] = v.strip().strip('"').strip("'")
    return env


def main() -> None:
    kb = "kb_a67e54d3188247ea"
    q = "支出经济分类科目改革的指导思想是什么？"
    hits = keyword_search(question=q, knowledge_base_id=kb, top_k=8)
    print("direct keyword_search hits=", len(hits))
    for h in hits:
        print(" ", h.segment_id, h.score, (h.content or "")[:40].replace("\n", " "))

    env = load_env()
    token = env.get("AI_ENGINE_TOKEN", "local-dev-ai-engine-token")

    def call(name: str, **kwargs):
        body = {
            "question": q,
            "knowledge_base_id": kb,
            "enable_rerank": False,
            "enable_rewrite": False,
            "enable_parent_fill": False,
            "rrf_k": 60,
            "strategy_name": name,
            **kwargs,
        }
        req = urllib.request.Request(
            "http://127.0.0.1:8000/api/v1/ai/retrieval/test",
            data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
            headers={
                "Content-Type": "application/json",
                "X-AI-Engine-Token": token,
            },
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=120) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        srcs = data.get("sources") or []
        stages = [(st.get("key"), st.get("count_text")) for st in (data.get("stages") or [])]
        ids = [s.get("segment_id") for s in srcs]
        print(f"--- {name} hits={len(srcs)} stages={stages}")
        print("  ids=", ids)
        return ids

    print("question=", q)
    b = call(
        "baseline",
        enable_vector=True,
        enable_bm25=False,
        enable_rrf=False,
        top_k=5,
        vector_top_k=5,
        bm25_top_k=0,
        cosine_threshold=0.3,
    )
    h = call(
        "hybrid",
        enable_vector=True,
        enable_bm25=True,
        enable_rrf=True,
        top_k=8,
        vector_top_k=8,
        bm25_top_k=8,
        cosine_threshold=0.3,
    )
    print("same_top?", b == h)
    print("overlap", len(set(b) & set(h)), "/", len(set(b) | set(h)))


if __name__ == "__main__":
    main()
