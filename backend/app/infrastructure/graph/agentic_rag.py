"""LangGraph Agentic RAG：rewrite → retrieve → grade → (retry) → generate → check。"""
from __future__ import annotations

import json
import time
from typing import Any, Literal, TypedDict

from langgraph.graph import END, StateGraph

from app.core.defaults import chat_temperature, clamp_top_k, judge_temperature, resolve_knowledge_base_id
from app.infrastructure.ai.chat.dashscope_chat import chat_complete
from app.infrastructure.hybrid.retrieve import hybrid_retrieve, segments_as_sources


class AgenticState(TypedDict, total=False):
    question: str
    knowledge_base_id: str
    strategy: dict[str, Any]
    chat: dict[str, Any]
    query: str
    round: int
    max_rounds: int
    sources: list[dict[str, Any]]
    answer: str
    relevant: bool
    grounded: bool
    conclusion: str
    steps: list[dict[str, Any]]
    retrieval_rounds: int
    rewrite_rounds: int


def _append_step(
    state: AgenticState,
    *,
    node: str,
    title: str,
    input_obj: dict[str, Any],
    output_obj: dict[str, Any],
    status: str,
    latency_ms: int,
) -> list[dict[str, Any]]:
    steps = list(state.get("steps") or [])
    steps.append(
        {
            "node_name": node,
            "title": title,
            "input": input_obj,
            "output": output_obj,
            "status": status,
            "latency_ms": latency_ms,
        }
    )
    return steps


def _chat(state: AgenticState, messages: list[dict[str, str]], *, temperature: float) -> str:
    cfg = state.get("chat") or {}
    return chat_complete(
        messages,
        model=cfg.get("model"),
        temperature=temperature,
        api_key=cfg.get("api_key"),
        base_url=cfg.get("base_url"),
    )


def _judge_temp(state: AgenticState) -> float:
    chat = state.get("chat") or {}
    raw = chat.get("judge_temperature")
    if raw is None:
        return judge_temperature()
    try:
        return judge_temperature(float(raw))
    except (TypeError, ValueError):
        return judge_temperature()


def _rewrite_query(original: str, round_idx: int) -> str:
    if round_idx == 1:
        return original + "（请检索制度/流程/政策相关条款）"
    return original + "（请补充具体条件、时限与处理步骤）"


def rewrite_node(state: AgenticState) -> dict[str, Any]:
    t0 = time.perf_counter()
    round_idx = int(state.get("round") or 0)
    question = (state.get("question") or "").strip()
    if round_idx == 0:
        query = question
        title = "准备检索问题"
    else:
        query = _rewrite_query(question, round_idx)
        title = "改写检索问题"
    ms = int((time.perf_counter() - t0) * 1000)
    steps = _append_step(
        state,
        node="rewrite",
        title=title,
        input_obj={"retry": round_idx},
        output_obj={"query": query},
        status="OK",
        latency_ms=ms,
    )
    out: dict[str, Any] = {"query": query, "steps": steps}
    if round_idx > 0:
        out["rewrite_rounds"] = int(state.get("rewrite_rounds") or 0) + 1
    return out


def retrieve_node(state: AgenticState) -> dict[str, Any]:
    t0 = time.perf_counter()
    strategy = state.get("strategy") or {}
    query = (state.get("query") or state.get("question") or "").strip()
    kb = resolve_knowledge_base_id(state.get("knowledge_base_id"))
    status = "OK"
    error = None
    sources: list[dict[str, Any]] = []
    score_scale = None
    try:
        retrieved = hybrid_retrieve(
            question=query,
            knowledge_base_id=kb,
            top_k=clamp_top_k(None if strategy.get("top_k") is None else int(strategy.get("top_k"))),
            enable_vector=bool(strategy.get("enable_vector", True)),
            enable_bm25=bool(strategy.get("enable_bm25", False)),
            enable_rrf=bool(strategy.get("enable_rrf", False)),
            enable_rerank=bool(strategy.get("enable_rerank", False)),
            enable_rewrite=bool(strategy.get("enable_rewrite", False)),
            enable_parent_fill=bool(strategy.get("enable_parent_fill", False)),
            vector_top_k=strategy.get("vector_top_k"),
            bm25_top_k=strategy.get("bm25_top_k"),
            rrf_k=int(strategy.get("rrf_k") or 60),
            rerank_top_n=strategy.get("rerank_top_n"),
            cosine_threshold=strategy.get("cosine_threshold"),
            rerank_threshold=strategy.get("rerank_threshold"),
            rewrite_mode=strategy.get("rewrite_mode") or "multi_query",
            rewrite_count=int(strategy.get("rewrite_count") or 3),
            strategy_id=strategy.get("strategy_id"),
            strategy_name=strategy.get("strategy_name"),
        )
        segments = retrieved.get("segments") or []
        sources = segments_as_sources(segments)
        score_scale = retrieved.get("score_scale")
    except Exception as exc:  # noqa: BLE001
        status = "FAILED"
        error = str(exc)[:200]
    ms = int((time.perf_counter() - t0) * 1000)
    out_obj: dict[str, Any] = {"hits": len(sources)}
    if score_scale:
        out_obj["score_scale"] = score_scale
    if error:
        out_obj["error"] = error
    if sources:
        top = sources[0].get("score")
        if top is not None:
            out_obj["top_score"] = top
    steps = _append_step(
        state,
        node="retrieve",
        title="检索知识库",
        input_obj={"query": query, "kb_id": kb},
        output_obj=out_obj,
        status=status,
        latency_ms=ms,
    )
    if status == "FAILED":
        raise RuntimeError(error or "retrieve failed")
    return {
        "sources": sources,
        "steps": steps,
        "retrieval_rounds": int(state.get("retrieval_rounds") or 0) + 1,
    }


def _extract_bool(raw: str, key: str, fallback: bool) -> bool:
    text = (raw or "").strip()
    if not text:
        return fallback
    try:
        i = text.find("{")
        j = text.rfind("}")
        if i >= 0 and j > i:
            text = text[i : j + 1]
        data = json.loads(text)
        if key in data:
            return bool(data[key])
    except Exception:  # noqa: BLE001
        pass
    lower = raw.lower()
    if f'"{key}":true' in lower or f'"{key}": true' in lower:
        return True
    if f'"{key}":false' in lower or f'"{key}": false' in lower:
        return False
    return fallback


def grade_node(state: AgenticState) -> dict[str, Any]:
    t0 = time.perf_counter()
    sources = state.get("sources") or []
    question = state.get("question") or ""
    relevant = False
    if sources:
        ctx_parts: list[str] = []
        for i, src in enumerate(sources[:5], start=1):
            c = str(src.get("content") or "")[:400]
            if c.strip():
                ctx_parts.append(f"[{i}]\n{c}")
        prompt = (
            "判断下列检索资料是否足以回答用户问题。只输出 JSON {\"relevant\":true/false}。\n"
            f"问题：{question}\n资料：\n" + "\n\n".join(ctx_parts)
        )
        try:
            raw = _chat(
                state,
                [
                    {"role": "system", "content": "你是严格的 RAG 裁判，只输出合法 JSON，不要 Markdown。"},
                    {"role": "user", "content": prompt},
                ],
                temperature=_judge_temp(state),
            )
            relevant = _extract_bool(raw, "relevant", True)
        except Exception:  # noqa: BLE001
            relevant = True
    ms = int((time.perf_counter() - t0) * 1000)
    steps = _append_step(
        state,
        node="grade",
        title="判断召回是否相关",
        input_obj={"count": len(sources)},
        output_obj={"verdict": "相关" if relevant else "不相关", "relevant": relevant},
        status="OK",
        latency_ms=ms,
    )
    return {
        "relevant": relevant,
        "round": int(state.get("round") or 0) + 1,
        "steps": steps,
    }


def route_after_grade(state: AgenticState) -> Literal["generate", "rewrite", "giveup"]:
    if state.get("relevant"):
        return "generate"
    if int(state.get("round") or 0) >= int(state.get("max_rounds") or 3):
        return "giveup"
    return "rewrite"


def generate_node(state: AgenticState) -> dict[str, Any]:
    t0 = time.perf_counter()
    question = state.get("question") or ""
    sources = state.get("sources") or []
    contexts: list[str] = []
    for src in sources:
        c = str(src.get("content") or "").strip()
        if c:
            contexts.append(c)
        if sum(len(x) for x in contexts) > 12000:
            break
    ctx = "\n\n".join(f"[{i}]\n{c}" for i, c in enumerate(contexts, start=1))
    system = (
        "你是企业知识库助手。请严格根据下面提供的资料回答用户问题。"
        "资料中没有的内容不要编造，直接回答\"知识库中未找到相关内容\"。"
    )
    user = f"资料：\n{ctx}\n\n用户问题：{question}"
    temp = (state.get("chat") or {}).get("temperature")
    answer = _chat(
        state,
        [{"role": "system", "content": system}, {"role": "user", "content": user}],
        temperature=chat_temperature(None if temp is None else float(temp)),
    )
    ms = int((time.perf_counter() - t0) * 1000)
    steps = _append_step(
        state,
        node="generate",
        title="生成答案",
        input_obj={"regenerate": 0},
        output_obj={"length": len(answer or "")},
        status="OK",
        latency_ms=ms,
    )
    return {"answer": answer or "", "steps": steps}


def check_node(state: AgenticState) -> dict[str, Any]:
    t0 = time.perf_counter()
    question = state.get("question") or ""
    answer = state.get("answer") or ""
    sources = state.get("sources") or []
    contexts = [str(s.get("content") or "")[:400] for s in sources[:4] if s.get("content")]
    grounded = False
    if answer.strip() and contexts:
        prompt = (
            "判断答案是否可由资料支撑（无胡编）。只输出 JSON {\"grounded\":true/false}。\n"
            f"问题：{question}\n答案：{answer}\n资料：\n"
            + "\n\n".join(f"[{i}]\n{c}" for i, c in enumerate(contexts, start=1))
        )
        try:
            raw = _chat(
                state,
                [
                    {"role": "system", "content": "你是严格的 RAG 裁判，只输出合法 JSON，不要 Markdown。"},
                    {"role": "user", "content": prompt},
                ],
                temperature=_judge_temp(state),
            )
            grounded = _extract_bool(raw, "grounded", len(answer) >= 20)
        except Exception:  # noqa: BLE001
            grounded = len(answer) >= 20
    ms = int((time.perf_counter() - t0) * 1000)
    conclusion = "正常回答" if grounded else "支撑不足"
    steps = _append_step(
        state,
        node="check",
        title="校验答案是否有资料支撑",
        input_obj={"answer_length": len(answer)},
        output_obj={"verdict": "有支撑" if grounded else "支撑不足", "grounded": grounded},
        status="OK",
        latency_ms=ms,
    )
    return {"grounded": grounded, "conclusion": conclusion, "steps": steps}


def giveup_node(state: AgenticState) -> dict[str, Any]:
    return {
        "answer": "知识库中未找到相关内容",
        "conclusion": "未找到相关内容",
        "grounded": False,
    }


def build_agentic_graph():
    g: StateGraph = StateGraph(AgenticState)
    g.add_node("rewrite", rewrite_node)
    g.add_node("retrieve", retrieve_node)
    g.add_node("grade", grade_node)
    g.add_node("generate", generate_node)
    g.add_node("check", check_node)
    g.add_node("giveup", giveup_node)
    g.set_entry_point("rewrite")
    g.add_edge("rewrite", "retrieve")
    g.add_edge("retrieve", "grade")
    g.add_conditional_edges(
        "grade",
        route_after_grade,
        {"generate": "generate", "rewrite": "rewrite", "giveup": "giveup"},
    )
    g.add_edge("generate", "check")
    g.add_edge("check", END)
    g.add_edge("giveup", END)
    return g.compile()


_GRAPH = None


def run_agentic_rag(
    *,
    question: str,
    knowledge_base_id: str,
    strategy: dict[str, Any] | None = None,
    chat: dict[str, Any] | None = None,
    max_rounds: int = 3,
) -> dict[str, Any]:
    global _GRAPH
    if _GRAPH is None:
        _GRAPH = build_agentic_graph()
    init: AgenticState = {
        "question": (question or "").strip(),
        "knowledge_base_id": resolve_knowledge_base_id(knowledge_base_id),
        "strategy": strategy or {},
        "chat": chat or {},
        "query": (question or "").strip(),
        "round": 0,
        "max_rounds": max(1, min(int(max_rounds or 3), 5)),
        "sources": [],
        "answer": "",
        "relevant": False,
        "grounded": False,
        "conclusion": "",
        "steps": [],
        "retrieval_rounds": 0,
        "rewrite_rounds": 0,
    }
    final = _GRAPH.invoke(init)
    return {
        "answer": final.get("answer") or "",
        "conclusion": final.get("conclusion") or "",
        "sources": final.get("sources") or [],
        "relevant": bool(final.get("relevant")),
        "grounded": bool(final.get("grounded")),
        "retrieval_rounds": int(final.get("retrieval_rounds") or 0),
        "rewrite_rounds": int(final.get("rewrite_rounds") or 0),
        "steps": final.get("steps") or [],
    }
