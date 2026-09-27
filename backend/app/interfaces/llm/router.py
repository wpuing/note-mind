"""OpenAI 兼容 Chat 代理：供 Java 经 AiEngineClient 调用（Judge / 生成 / 评测）。"""
from __future__ import annotations

from typing import Any

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field

from app.core.defaults import chat_temperature
from app.core.security import verify_ai_engine_token
from app.core.url_safety import require_safe_http_url
from app.infrastructure.ai.chat.dashscope_chat import chat_complete

router = APIRouter(prefix="/llm", tags=["llm"])


class ChatMessage(BaseModel):
    role: str = "user"
    content: str = ""


class LlmChatRequest(BaseModel):
    messages: list[ChatMessage] = Field(..., min_length=1)
    model: str | None = None
    temperature: float | None = None
    api_key: str | None = None
    base_url: str | None = None


@router.post("/chat")
async def llm_chat(
    body: LlmChatRequest,
    _: None = Depends(verify_ai_engine_token),
) -> dict[str, Any]:
    messages = [
        {"role": (m.role or "user").strip() or "user", "content": m.content or ""}
        for m in body.messages
        if (m.content or "").strip() or (m.role or "") == "system"
    ]
    if not messages:
        raise HTTPException(status_code=400, detail="messages required")
    safe_base = None
    if body.base_url:
        try:
            safe_base = require_safe_http_url(body.base_url)
        except ValueError as exc:
            raise HTTPException(status_code=400, detail=str(exc)) from exc
    try:
        content = chat_complete(
            messages,
            model=body.model,
            temperature=chat_temperature(body.temperature),
            api_key=body.api_key,
            base_url=safe_base,
        )
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(status_code=502, detail="llm chat failed") from exc
    return {"content": content or ""}
