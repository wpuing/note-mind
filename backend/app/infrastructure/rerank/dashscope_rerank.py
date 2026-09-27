"""DashScope 原生 Rerank；失败则跳过。"""
from __future__ import annotations

import logging
from typing import Any

import httpx

from app.core.config import settings

logger = logging.getLogger(__name__)


def rerank_documents(
    query: str,
    documents: list[str],
    *,
    top_n: int | None = None,
    model: str | None = None,
) -> list[dict[str, Any]] | None:
    """
    成功返回 [{"index": int, "relevance_score": float}, ...]
    失败返回 None（调用方应优雅降级）。
    """
    if not documents:
        return []
    key = settings.rerank_api_key
    if not key:
        return None
    payload = {
        "model": model or settings.default_rerank_model,
        "input": {"query": query, "documents": documents},
        "parameters": {
            "return_documents": False,
            "top_n": top_n or len(documents),
        },
    }
    headers = {
        "Authorization": f"Bearer {key}",
        "Content-Type": "application/json",
    }
    try:
        with httpx.Client(timeout=30.0) as client:
            resp = client.post(settings.dashscope_rerank_url, headers=headers, json=payload)
            resp.raise_for_status()
            data = resp.json()
        results = (
            data.get("output", {}).get("results")
            or data.get("results")
            or []
        )
        out: list[dict[str, Any]] = []
        for item in results:
            idx = item.get("index")
            score = item.get("relevance_score", item.get("score"))
            if idx is None or score is None:
                continue
            out.append({"index": int(idx), "relevance_score": float(score)})
        return out
    except Exception as exc:  # noqa: BLE001 — MVP 优雅降级
        logger.warning("DashScope rerank skipped: %s", type(exc).__name__)
        return None
