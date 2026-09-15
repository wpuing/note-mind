"""查询改写：multi_query / coref / hyde / all。"""
from __future__ import annotations

import re
from typing import Any

from app.infrastructure.ai.chat.dashscope_chat import chat_complete


def prepare_queries(
    question: str,
    *,
    enable_rewrite: bool,
    rewrite_mode: str = "multi_query",
    rewrite_count: int = 3,
    chat_history: list[dict[str, str]] | None = None,
) -> dict[str, Any]:
    """
    返回:
      queries: 用于 BM25 / 多路召回的问句列表（含原问或消解后问句）
      vector_texts: 与 queries 对齐的向量检索文本（HyDE 时用假想回答）
      rewritten_queries: 展示用改写列表
      resolved_question: 消解后的主问题
    """
    q = (question or "").strip()
    mode = (rewrite_mode or "multi_query").strip().lower()
    count = max(1, min(int(rewrite_count or 3), 5))
    history = chat_history or []

    if not enable_rewrite:
        return {
            "queries": [q],
            "vector_texts": [q],
            "rewritten_queries": [],
            "resolved_question": q,
        }

    resolved = q
    rewritten: list[str] = []

    if mode in ("coref", "all"):
        resolved = resolve_coreference(q, history) or q
        if resolved != q:
            rewritten.append(resolved)

    queries = [resolved]
    vector_texts = [resolved]

    if mode in ("multi_query", "all"):
        variants = multi_query_expand(resolved, count=count)
        for v in variants:
            if v and v not in queries:
                queries.append(v)
                vector_texts.append(v)
                rewritten.append(v)

    if mode == "hyde":
        hypo = hyde_document(resolved)
        rewritten.append(hypo[:200] + ("…" if len(hypo) > 200 else ""))
        return {
            "queries": [resolved],
            "vector_texts": [hypo or resolved],
            "rewritten_queries": rewritten,
            "resolved_question": resolved,
        }

    if mode == "all":
        # 主问 + 扩展问：向量路用各自 HyDE（控制成本：仅对 resolved 做 HyDE）
        hypo = hyde_document(resolved)
        if hypo:
            vector_texts[0] = hypo
            rewritten.append(hypo[:200] + ("…" if len(hypo) > 200 else ""))

    return {
        "queries": queries,
        "vector_texts": vector_texts,
        "rewritten_queries": rewritten,
        "resolved_question": resolved,
    }


def multi_query_expand(question: str, *, count: int = 3) -> list[str]:
    prompt = (
        "你是检索查询改写助手。请把用户问题改写成若干语义等价、表述不同的检索问句。\n"
        f"要求：输出恰好 {count} 条；每行一条；不要编号；不要解释；保持中文。\n"
        f"原问题：{question}"
    )
    try:
        text = chat_complete(
            [
                {"role": "system", "content": "只输出改写后的问句，每行一条。"},
                {"role": "user", "content": prompt},
            ],
            temperature=0.4,
        )
    except Exception:
        return []
    lines = [ln.strip(" -\t") for ln in text.splitlines() if ln.strip()]
    out: list[str] = []
    for ln in lines:
        ln = re.sub(r"^\d+[\.\)、]\s*", "", ln).strip()
        if ln and ln not in out:
            out.append(ln)
        if len(out) >= count:
            break
    return out[:count]


def resolve_coreference(question: str, chat_history: list[dict[str, str]]) -> str:
    if not chat_history:
        return question
    hist_lines = []
    for turn in chat_history[-8:]:
        role = (turn.get("role") or "user").strip()
        content = (turn.get("content") or "").strip()
        if content:
            hist_lines.append(f"{role}: {content}")
    if not hist_lines:
        return question
    prompt = (
        "根据对话历史，把当前用户问题改写成不依赖指代的独立完整问句。\n"
        "若无需改写，原样输出当前问题。只输出问句本身。\n\n"
        f"对话历史：\n{chr(10).join(hist_lines)}\n\n"
        f"当前问题：{question}"
    )
    try:
        text = chat_complete(
            [
                {"role": "system", "content": "你做指代消解，只输出改写后的完整问句。"},
                {"role": "user", "content": prompt},
            ],
            temperature=0.0,
        )
        return (text or question).strip().splitlines()[0].strip() or question
    except Exception:
        return question


def hyde_document(question: str) -> str:
    prompt = (
        "请针对下面的问题写一段简短的「假想答案」段落（不必事实正确），"
        "语气正式、像知识库文档，便于向量检索。不要反问，不要列表标题。\n"
        f"问题：{question}"
    )
    try:
        return chat_complete(
            [
                {"role": "system", "content": "你生成假想文档段落，只输出正文。"},
                {"role": "user", "content": prompt},
            ],
            temperature=0.5,
        )
    except Exception:
        return question
