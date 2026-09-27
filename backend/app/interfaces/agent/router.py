"""Agentic RAG HTTP：LangGraph 图，由 Java AiEngineClient 调用。"""
from __future__ import annotations

import asyncio
from typing import Any

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field

from app.core.config import settings
from app.core.defaults import resolve_knowledge_base_id
from app.core.security import verify_ai_engine_token
from app.infrastructure.graph.agentic_rag import run_agentic_rag

router = APIRouter(prefix="/agent", tags=["agent"])


class ChatCreds(BaseModel):
    model: str | None = None
    api_key: str | None = None
    base_url: str | None = None
    temperature: float | None = None
    judge_temperature: float | None = None


class AgentRunRequest(BaseModel):
    question: str = Field(..., min_length=1)
    knowledge_base_id: str | None = settings.default_knowledge_base_id
    strategy: dict[str, Any] | None = None
    chat: ChatCreds | None = None
    max_rounds: int | None = 3


@router.post("/run")
async def agent_run(
    body: AgentRunRequest,
    _: None = Depends(verify_ai_engine_token),
) -> dict[str, Any]:
    try:
        result = await asyncio.to_thread(
            run_agentic_rag,
            question=body.question.strip(),
            knowledge_base_id=resolve_knowledge_base_id(body.knowledge_base_id),
            strategy=body.strategy or {},
            chat=(body.chat.model_dump() if body.chat else {}),
            max_rounds=body.max_rounds or 3,
        )
    except Exception as exc:  # noqa: BLE001
        # 勿把内部异常细节回给调用方
        raise HTTPException(status_code=502, detail="agent run failed") from exc
    return result
