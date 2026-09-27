"""
本地冒烟：上传 samples 下 PDF/TXT，再非流式问一题。

用法（在 backend 目录）:
  python scripts/smoke_ingest_chat.py
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import httpx

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.core.config import settings  # noqa: E402

BASE = "http://127.0.0.1:8000"
TOKEN = settings.ai_engine_token
HEADERS = {"X-AI-Engine-Token": TOKEN} if TOKEN else {}


def pick_sample() -> Path | None:
    samples = settings.samples_dir
    if not samples.exists():
        return None
    for pattern in ("*.pdf", "*.txt", "*.md", "*.docx"):
        found = sorted(samples.glob(pattern))
        if found:
            return found[0]
    return None


def main() -> int:
    sample = pick_sample()
    if sample is None:
        print(f"[skip] no sample under {settings.samples_dir}")
        print("Put a .pdf/.txt/.md/.docx there and re-run.")
        return 1

    print(f"[upload] {sample.name}")
    with httpx.Client(timeout=180.0) as client:
        with sample.open("rb") as f:
            files = {"file": (sample.name, f)}
            data = {"knowledge_base_id": "kb_default"}
            r = client.post(
                f"{BASE}/api/v1/ai/ingest/upload",
                headers=HEADERS,
                files=files,
                data=data,
            )
        print("[upload]", r.status_code, r.text[:500])
        r.raise_for_status()
        ingest = r.json()
        kb = ingest.get("knowledge_base_id", "default")

        question = "请根据文档内容，用两三句话概括要点。"
        print(f"[retrieval] {question}")
        rr = client.post(
            f"{BASE}/api/v1/ai/retrieval/test",
            headers={**HEADERS, "Content-Type": "application/json"},
            json={"question": question, "knowledge_base_id": kb, "top_k": 3},
        )
        print("[retrieval]", rr.status_code)
        rr.raise_for_status()
        retrieved = rr.json()
        print(json.dumps(
            {
                "score_scale": retrieved.get("score_scale"),
                "n": len(retrieved.get("segments") or []),
                "previews": [
                    (s.get("content") or "")[:80]
                    for s in (retrieved.get("segments") or [])[:3]
                ],
            },
            ensure_ascii=False,
            indent=2,
        ))

        # 非流式：走 retrieval + 直接 chat SDK（避免 SSE 解析）
        from app.infrastructure.ai.chat.dashscope_chat import (  # noqa: E402
            build_rag_messages,
            chat_complete,
        )

        contexts = [s["content"] for s in (retrieved.get("segments") or [])]
        answer = chat_complete(build_rag_messages(question, contexts))
        print("[answer]", answer[:800])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
