"""集中业务默认值（读自 Settings，避免各处硬编码字面量）。"""
from __future__ import annotations

from app.core.config import settings


def resolve_knowledge_base_id(knowledge_base_id: str | None) -> str:
    if knowledge_base_id is None or not str(knowledge_base_id).strip():
        return settings.default_knowledge_base_id
    kb = str(knowledge_base_id).strip()
    if kb == "default":
        return settings.default_knowledge_base_id
    return kb


def default_top_k() -> int:
    return max(1, int(settings.default_top_k))


def clamp_top_k(value: int | None, *, fallback: int | None = None, raise_on_invalid: bool = False) -> int:
    top = int(value if value is not None else (fallback if fallback is not None else default_top_k()))
    lo = 1
    hi = max(lo, int(settings.max_top_k))
    if top < lo or top > hi:
        if raise_on_invalid:
            raise ValueError(f"top_k must be {lo}..{hi}")
        return max(lo, min(top, hi))
    return top


def judge_temperature(override: float | None = None) -> float:
    raw = settings.judge_temperature if override is None else override
    try:
        v = float(raw)
    except (TypeError, ValueError):
        return 0.0
    if v != v or v in (float("inf"), float("-inf")):
        return 0.0
    return max(0.0, min(v, 2.0))


def chat_temperature(override: float | None = None) -> float:
    raw = settings.default_chat_temperature if override is None else override
    try:
        v = float(raw)
    except (TypeError, ValueError):
        return float(settings.default_chat_temperature)
    if v != v or v in (float("inf"), float("-inf")):
        return float(settings.default_chat_temperature)
    return max(0.0, min(v, 2.0))


def cors_origins() -> list[str]:
    raw = settings.cors_origins or ""
    parts = [p.strip() for p in raw.split(",") if p.strip()]
    return parts or [
        "http://127.0.0.1:8080",
        "http://localhost:8080",
    ]
