"""DashScope Chat（OpenAI 兼容 + SSE 流式）。"""
from __future__ import annotations

from collections.abc import Iterator
from typing import Any

from openai import OpenAI

from app.core.config import settings
from app.infrastructure.ai.openai_client import openai_compatible_client


def build_rag_messages(question: str, context_blocks: list[str]) -> list[dict[str, str]]:
    context = "\n\n".join(
        f"[{i + 1}] {block}" for i, block in enumerate(context_blocks)
    ) or "（无检索上下文）"
    system = (
        "你是 NoteMind 知识库助手。请严格依据给定上下文回答用户问题；"
        "若上下文不足以回答，请明确说明不知道，不要编造。"
        "回答使用简洁中文。"
    )
    user = f"上下文：\n{context}\n\n问题：{question}"
    return [
        {"role": "system", "content": system},
        {"role": "user", "content": user},
    ]


def _client(*, api_key: str | None = None, base_url: str | None = None) -> OpenAI:
    key = (api_key or "").strip() or settings.chat_api_key
    if not key:
        raise RuntimeError("Missing DASHSCOPE_CHAT_API_KEY / DASHSCOPE_API_KEY")
    return openai_compatible_client(api_key=key, base_url=base_url)


def chat_complete(
    messages: list[dict[str, str]],
    *,
    model: str | None = None,
    temperature: float = 0.3,
    api_key: str | None = None,
    base_url: str | None = None,
) -> str:
    client = _client(api_key=api_key, base_url=base_url)
    resp = client.chat.completions.create(
        model=model or settings.default_chat_model,
        messages=messages,  # type: ignore[arg-type]
        temperature=temperature,
        stream=False,
    )
    return (resp.choices[0].message.content or "").strip()


def chat_stream(
    messages: list[dict[str, str]],
    *,
    model: str | None = None,
    temperature: float = 0.3,
    api_key: str | None = None,
    base_url: str | None = None,
) -> Iterator[str]:
    client = _client(api_key=api_key, base_url=base_url)
    stream: Any = client.chat.completions.create(
        model=model or settings.default_chat_model,
        messages=messages,  # type: ignore[arg-type]
        temperature=temperature,
        stream=True,
    )
    for chunk in stream:
        if not chunk.choices:
            continue
        delta = chunk.choices[0].delta
        content = getattr(delta, "content", None)
        if content:
            yield content
