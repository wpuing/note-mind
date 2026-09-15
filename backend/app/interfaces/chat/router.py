"""SSE 问答：按策略检索 → qwen-plus 流式生成，最终事件带 sources。"""
from __future__ import annotations

import asyncio
import json
from collections.abc import Iterator
from typing import Any

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field
from sse_starlette.sse import EventSourceResponse

from app.core.config import settings
from app.core.defaults import resolve_knowledge_base_id, clamp_top_k
from app.core.security import verify_ai_engine_token
from app.infrastructure.ai.chat.dashscope_chat import build_rag_messages, chat_stream
from app.infrastructure.hybrid.retrieve import hybrid_retrieve, segments_as_sources

router = APIRouter(prefix="/chat", tags=["chat"])


class ChatTurn(BaseModel):
    role: str | None = "user"
    content: str | None = ""


def _normalize_kb_id(knowledge_base_id: str | None) -> str:
    return resolve_knowledge_base_id(knowledge_base_id)



class ChatRequest(BaseModel):
    question: str = Field(..., min_length=1)
    knowledge_base_id: str | None = settings.default_knowledge_base_id
    top_k: int | None = None
    enable_vector: bool = True
    enable_bm25: bool = False
    enable_rrf: bool = False
    enable_rerank: bool = False
    enable_rewrite: bool = False
    enable_parent_fill: bool = False
    vector_top_k: int | None = None
    bm25_top_k: int | None = None
    rrf_k: int | None = 60
    rerank_top_n: int | None = None
    cosine_threshold: float | None = None
    rerank_threshold: float | None = None
    rewrite_mode: str | None = "multi_query"
    rewrite_count: int | None = 3
    strategy_id: str | None = None
    strategy_name: str | None = None
    chat_history: list[ChatTurn] | None = None


@router.post("/completions")
async def chat_completions(
    body: ChatRequest,
    _: None = Depends(verify_ai_engine_token),
):
    kb = _normalize_kb_id(body.knowledge_base_id)
    question = body.question.strip()
    top_k = clamp_top_k(body.top_k or body.rerank_top_n or body.vector_top_k)

    history: list[dict[str, str]] | None = None
    if body.chat_history:
        history = [
            {"role": (t.role or "user"), "content": (t.content or "")}
            for t in body.chat_history
            if (t.content or "").strip()
        ]

    try:
        retrieved = await asyncio.to_thread(
            hybrid_retrieve,
            question=question,
            knowledge_base_id=kb,
            top_k=top_k,
            enable_vector=body.enable_vector,
            enable_bm25=body.enable_bm25,
            enable_rrf=body.enable_rrf,
            enable_rerank=body.enable_rerank,
            enable_rewrite=body.enable_rewrite,
            enable_parent_fill=body.enable_parent_fill,
            vector_top_k=body.vector_top_k,
            bm25_top_k=body.bm25_top_k,
            rrf_k=body.rrf_k or 60,
            rerank_top_n=body.rerank_top_n,
            cosine_threshold=body.cosine_threshold,
            rerank_threshold=body.rerank_threshold,
            rewrite_mode=body.rewrite_mode or "multi_query",
            rewrite_count=body.rewrite_count or 3,
            chat_history=history,
            strategy_id=body.strategy_id,
            strategy_name=body.strategy_name,
        )
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(status_code=502, detail=f"retrieval failed: {exc}") from exc

    segments = retrieved.get("segments") or []
    contexts = [s["content"] for s in segments]
    messages = build_rag_messages(question, contexts)
    sources = segments_as_sources(segments)

    def event_gen() -> Iterator[dict]:
        yield {
            "event": "meta",
            "data": json.dumps(
                {
                    "knowledge_base_id": kb,
                    "score_scale": retrieved.get("score_scale"),
                    "strategy_id": retrieved.get("strategy_id"),
                    "strategy_name": retrieved.get("strategy_name"),
                    "rewritten_queries": retrieved.get("rewritten_queries") or [],
                    "source_count": len(sources),
                },
                ensure_ascii=False,
            ),
        }
        try:
            for token in chat_stream(messages):
                yield {
                    "event": "delta",
                    "data": json.dumps({"content": token}, ensure_ascii=False),
                }
        except Exception as exc:  # noqa: BLE001
            yield {
                "event": "error",
                "data": json.dumps({"message": str(exc)}, ensure_ascii=False),
            }
        yield {
            "event": "final",
            "data": json.dumps({"sources": sources}, ensure_ascii=False),
        }
        yield {"event": "done", "data": "[DONE]"}

    return EventSourceResponse(event_gen())
