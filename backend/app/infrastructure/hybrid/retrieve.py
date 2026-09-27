"""混合检索：七开关 + 双阈值 + 改写 + 父块回填 + 阶段耗时。"""
from __future__ import annotations

import time
from typing import Any

from app.core.defaults import resolve_knowledge_base_id
from app.infrastructure.ai.embedding.dashscope_embed import embed_query
from app.infrastructure.rerank.dashscope_rerank import rerank_documents
from app.infrastructure.rewrite import prepare_queries
from app.infrastructure.vector.pgvector.store import (
    SegmentRecord,
    get_segments_by_ids,
    keyword_search,
    vector_search,
)

SCORE_SCALE_LABELS = {
    "cosine": "余弦相似度",
    "rrf": "RRF名次分",
    "rerank": "重排相关分",
    "bm25": "BM25分",
    "mixed": "双路合并分",
    "none": "无",
}

REWRITE_MODE_LABELS = {
    "multi_query": "多查询扩展",
    "coref": "指代消解",
    "hyde": "HyDE",
    "all": "改写全开",
}


def _ms(t0: float) -> int:
    return max(0, int(round((time.perf_counter() - t0) * 1000)))


def _merge_unique(
    primary: list[SegmentRecord], secondary: list[SegmentRecord]
) -> list[SegmentRecord]:
    """向量优先，再补入第二路独有命中（用于未开 RRF 的双路合并）。"""
    seen: set[str] = set()
    out: list[SegmentRecord] = []
    for item in list(primary) + list(secondary):
        sid = item.segment_id
        if not sid or sid in seen:
            continue
        seen.add(sid)
        out.append(item)
    return out


def _score_range(items: list[SegmentRecord]) -> str | None:
    if not items:
        return None
    scores = [float(s.score or 0.0) for s in items]
    return f"{min(scores):.4f} ~ {max(scores):.4f}"


def _channel_label(channels: set[str], *, reranked: bool = False) -> str:
    has_v = "vector" in channels
    has_b = "bm25" in channels
    if has_v and has_b:
        base = "BM25检索+向量检索"
    elif has_v:
        base = "向量检索"
    elif has_b:
        base = "BM25检索"
    else:
        base = "—"
    return f"{base} -> 重排" if reranked and base != "—" else base


def rrf_fuse(
    ranked_lists: list[list[SegmentRecord]],
    *,
    k: int = 60,
) -> list[SegmentRecord]:
    scores: dict[str, float] = {}
    best: dict[str, SegmentRecord] = {}
    for ranked in ranked_lists:
        for rank, item in enumerate(ranked, start=1):
            sid = item.segment_id
            scores[sid] = scores.get(sid, 0.0) + 1.0 / (k + rank)
            prev = best.get(sid)
            if prev is None or len(item.content) > len(prev.content):
                best[sid] = item
    fused = []
    for sid, score in scores.items():
        rec = best[sid]
        fused.append(
            SegmentRecord(
                segment_id=rec.segment_id,
                document_id=rec.document_id,
                knowledge_base_id=rec.knowledge_base_id,
                content=rec.content,
                parent_segment_id=rec.parent_segment_id,
                score=score,
                meta=rec.meta,
            )
        )
    fused.sort(key=lambda x: x.score, reverse=True)
    return fused


def hybrid_retrieve(
    *,
    question: str,
    knowledge_base_id: str | None = None,
    top_k: int = 5,
    candidate_k: int | None = None,
    enable_vector: bool = True,
    enable_bm25: bool = True,
    enable_rrf: bool = True,
    enable_rerank: bool = False,
    enable_rewrite: bool = False,
    enable_parent_fill: bool = False,
    vector_top_k: int | None = None,
    bm25_top_k: int | None = None,
    rrf_k: int = 60,
    rerank_top_n: int | None = None,
    cosine_threshold: float | None = None,
    rerank_threshold: float | None = None,
    rewrite_mode: str = "multi_query",
    rewrite_count: int = 3,
    chat_history: list[dict[str, str]] | None = None,
    strategy_id: str | None = None,
    strategy_name: str | None = None,
    document_ids: list[str] | None = None,
) -> dict[str, Any]:
    t_all = time.perf_counter()
    stages: list[dict[str, Any]] = []

    kb = resolve_knowledge_base_id(knowledge_base_id)
    doc_ids = [str(d).strip() for d in (document_ids or []) if str(d).strip()] or None

    final_top = max(1, int(top_k or 5))
    v_topk = int(vector_top_k if vector_top_k is not None else (candidate_k or max(final_top * 4, 20)))
    b_topk = int(bm25_top_k if bm25_top_k is not None else (candidate_k or max(final_top * 4, 20)))
    keep_n = int(rerank_top_n if rerank_top_n is not None else final_top)
    keep_n = max(1, keep_n)

    # ---- 改写 ----
    t0 = time.perf_counter()
    prepared = prepare_queries(
        question,
        enable_rewrite=enable_rewrite,
        rewrite_mode=rewrite_mode,
        rewrite_count=rewrite_count,
        chat_history=chat_history,
    )
    queries: list[str] = prepared["queries"] or [question]
    vector_texts: list[str] = prepared["vector_texts"] or queries
    rewritten_queries: list[str] = prepared.get("rewritten_queries") or []
    resolved_q: str = prepared.get("resolved_question") or question
    if enable_rewrite:
        mode_label = REWRITE_MODE_LABELS.get(rewrite_mode or "multi_query", rewrite_mode or "改写")
        stages.append(
            {
                "key": "rewrite",
                "name": f"查询改写（{mode_label}）",
                "count_text": str(len(queries)),
                "elapsed_ms": _ms(t0),
                "detail": "；".join(queries) if queries else None,
            }
        )

    channel_map: dict[str, set[str]] = {}
    vec_lists: list[list[SegmentRecord]] = []
    kw_lists: list[list[SegmentRecord]] = []

    # ---- 向量 ----
    vec_hits: list[SegmentRecord] = []
    vec_fused_by_rrf = False
    if enable_vector:
        t0 = time.perf_counter()
        for text in vector_texts:
            if not text or not str(text).strip():
                continue
            qv = embed_query(str(text).strip())
            vec_lists.append(
                vector_search(
                    query_embedding=qv,
                    knowledge_base_id=kb,
                    top_k=max(1, v_topk),
                    document_ids=doc_ids,
                )
            )
        if len(vec_lists) > 1:
            vec_hits = rrf_fuse(vec_lists, k=rrf_k)
            vec_fused_by_rrf = True
        else:
            vec_hits = vec_lists[0] if vec_lists else []
        for h in vec_hits:
            channel_map.setdefault(h.segment_id, set()).add("vector")
        stages.append(
            {
                "key": "vector",
                "name": "向量检索",
                "count_text": str(len(vec_hits)),
                "elapsed_ms": _ms(t0),
                "detail": "多查询RRF融合" if vec_fused_by_rrf else None,
            }
        )

    # ---- BM25 ----
    kw_hits: list[SegmentRecord] = []
    kw_fused_by_rrf = False
    if enable_bm25:
        t0 = time.perf_counter()
        for q in queries:
            if not q or not str(q).strip():
                continue
            kw_lists.append(
                keyword_search(
                    question=str(q).strip(),
                    knowledge_base_id=kb,
                    top_k=max(1, b_topk),
                    document_ids=doc_ids,
                )
            )
        if len(kw_lists) > 1:
            kw_hits = rrf_fuse(kw_lists, k=rrf_k)
            kw_fused_by_rrf = True
        else:
            kw_hits = kw_lists[0] if kw_lists else []
        for h in kw_hits:
            channel_map.setdefault(h.segment_id, set()).add("bm25")
        stages.append(
            {
                "key": "bm25",
                "name": "BM25检索",
                "count_text": str(len(kw_hits)),
                "elapsed_ms": _ms(t0),
                "detail": "多查询RRF融合" if kw_fused_by_rrf else None,
            }
        )

    use_both = bool(enable_vector and enable_bm25 and vec_hits and kw_hits)
    use_rrf = bool(enable_rrf and use_both)

    if use_rrf:
        t0 = time.perf_counter()
        fused = rrf_fuse([vec_hits, kw_hits], k=rrf_k)
        score_scale = "rrf"
        stages.append(
            {
                "key": "rrf",
                "name": "RRF融合",
                "count_text": str(len(fused)),
                "elapsed_ms": _ms(t0),
                "detail": f"k={rrf_k}",
            }
        )
    elif use_both:
        # 双路都开但未开 RRF：交错去重合并，避免静默丢弃 BM25；分数量纲不一致，不做绝对值阈值
        fused = _merge_unique(vec_hits, kw_hits)
        score_scale = "mixed"
        stages.append(
            {
                "key": "merge",
                "name": "双路合并（未开RRF）",
                "count_text": str(len(fused)),
                "elapsed_ms": 0,
                "detail": "向量优先，补入 BM25 独有命中",
            }
        )
    elif enable_vector and vec_hits:
        fused = list(vec_hits)
        # 多查询改写后的向量结果已是 RRF 名次分，不可再按余弦绝对值阈值过滤
        score_scale = "rrf" if vec_fused_by_rrf else "cosine"
    elif enable_bm25 and kw_hits:
        fused = list(kw_hits)
        score_scale = "rrf" if kw_fused_by_rrf else "bm25"
    else:
        fused = []
        score_scale = "none"

    # 纯向量：余弦阈值（仅单查询余弦分；RRF/混合分不做绝对值阈值）
    pending_rerank_threshold: float | None = None
    if score_scale == "cosine" and cosine_threshold is not None:
        t0 = time.perf_counter()
        before = len(fused)
        thr = float(cosine_threshold)
        fused = [s for s in fused if float(s.score or 0.0) >= thr]
        stages.append(
            {
                "key": "threshold",
                "name": f"阈值过滤（余弦相似度 >= {thr:g}）",
                "count_text": f"{before} → {len(fused)}",
                "elapsed_ms": _ms(t0),
                "detail": (
                    f"当前分数量纲: 余弦相似度，门槛 {thr:g}"
                    + (f"；本次分数区间 {_score_range(fused)}" if fused else "")
                ),
                "threshold_label": f"余弦相似度 >= {thr:g}",
                "threshold_hint": f"当前分数量纲: 余弦相似度，门槛 {thr:g}",
                "score_range": _score_range(fused),
            }
        )

    did_rerank = False
    if enable_rerank and fused:
        t0 = time.perf_counter()
        before = len(fused)
        pool_n = min(len(fused), max(keep_n * 4, 20))
        pool = fused[:pool_n]
        docs = [s.content for s in pool]
        from app.infrastructure.rerank.compressor import compress_rerank

        rr = compress_rerank(resolved_q, docs, top_n=min(keep_n, len(docs)))
        if rr is None:
            rr = rerank_documents(resolved_q, docs, top_n=min(keep_n, len(docs)))
        if rr is not None:
            reranked: list[SegmentRecord] = []
            for item in rr:
                idx = item["index"]
                if 0 <= idx < len(pool):
                    base = pool[idx]
                    reranked.append(
                        SegmentRecord(
                            segment_id=base.segment_id,
                            document_id=base.document_id,
                            knowledge_base_id=base.knowledge_base_id,
                            content=base.content,
                            parent_segment_id=base.parent_segment_id,
                            score=float(item["relevance_score"]),
                            meta=base.meta,
                        )
                    )
            if reranked:
                fused = reranked
                score_scale = "rerank"
                did_rerank = True
                stages.append(
                    {
                        "key": "rerank",
                        "name": "重排",
                        "count_text": f"{before} → {len(fused)}",
                        "elapsed_ms": _ms(t0),
                        "detail": None,
                    }
                )
                if rerank_threshold is not None:
                    pending_rerank_threshold = float(rerank_threshold)

    cut = keep_n if enable_rerank else final_top
    top = fused[:cut]

    if enable_parent_fill and top:
        t0 = time.perf_counter()
        before = len(top)
        top = parent_fill(top)
        stages.append(
            {
                "key": "parent_fill",
                "name": "父块回填",
                "count_text": f"{before} → {len(top)}",
                "elapsed_ms": _ms(t0),
                "detail": None,
            }
        )

    # 完整策略：父块后再做重排阈值（对齐外部系统顺序）
    if pending_rerank_threshold is not None:
        t0 = time.perf_counter()
        before = len(top)
        thr = pending_rerank_threshold
        top = [s for s in top if float(s.score or 0.0) >= thr]
        stages.append(
            {
                "key": "threshold",
                "name": f"阈值过滤（重排相关分 >= {thr:g}）",
                "count_text": f"{before} → {len(top)}",
                "elapsed_ms": _ms(t0),
                "detail": (
                    f"当前分数量纲: 重排相关分，门槛 {thr:g}"
                    + (f"；本次分数区间 {_score_range(top)}" if top else "")
                ),
                "threshold_label": f"重排相关分 >= {thr:g}",
                "threshold_hint": f"当前分数量纲: 重排相关分，门槛 {thr:g}",
                "score_range": _score_range(top),
            }
        )

    # 进阶混合：RRF 名次分不做阈值
    if score_scale == "rrf" and not any(s.get("key") == "threshold" for s in stages):
        stages.append(
            {
                "key": "threshold",
                "name": "阈值过滤",
                "count_text": f"{len(top)} → {len(top)}",
                "elapsed_ms": 0,
                "detail": "（本次不适用）",
                "not_applicable": True,
                "explain": (
                    "RRF 名次分只表示排序先后，没有绝对含义，不能和固定门槛比大小。"
                    "要让阈值生效，请在策略中打开重排。"
                ),
            }
        )

    segments: list[dict[str, Any]] = []
    for s in top:
        channels = channel_map.get(s.segment_id) or set()
        meta = dict(s.meta or {})
        child_id = meta.get("filled_from_child")
        if child_id and not channels:
            channels = channel_map.get(str(child_id)) or set()
        is_parent = bool(meta.get("filled_from_child")) or meta.get("segment_type") == "parent"
        segments.append(
            {
                "segment_id": s.segment_id,
                "document_id": s.document_id,
                "knowledge_base_id": s.knowledge_base_id,
                "parent_segment_id": s.parent_segment_id,
                "content": s.content,
                "score": s.score,
                "meta": meta,
                "recall_source": _channel_label(channels, reranked=did_rerank),
                "segment_type": "父块" if is_parent else "普通块",
                "hit_child_content": meta.get("hit_child_content"),
            }
        )

    return {
        "question": question,
        "knowledge_base_id": knowledge_base_id,
        "top_k": final_top,
        "score_scale": score_scale,
        "score_scale_label": SCORE_SCALE_LABELS.get(score_scale, score_scale),
        "strategy_id": strategy_id,
        "strategy_name": strategy_name,
        "rewritten_queries": rewritten_queries,
        "retrieval_queries": queries,
        "hit_count": len(segments),
        "elapsed_ms": _ms(t_all),
        "stages": stages,
        "segments": segments,
        "sources": segments_as_sources(segments),
        "debug": {
            "vector_hits": len(vec_hits),
            "keyword_hits": len(kw_hits),
            "fused_before_cut": len(fused),
            "enable_vector": enable_vector,
            "enable_bm25": enable_bm25,
            "enable_rrf": use_rrf,
            "enable_rerank": enable_rerank,
            "enable_rewrite": enable_rewrite,
            "enable_parent_fill": enable_parent_fill,
            "queries": queries,
            "document_ids": doc_ids or [],
        },
    }


def parent_fill(hits: list[SegmentRecord]) -> list[SegmentRecord]:
    parent_ids = [h.parent_segment_id for h in hits if h.parent_segment_id]
    parents = get_segments_by_ids([pid for pid in parent_ids if pid])
    out: list[SegmentRecord] = []
    seen_parent: set[str] = set()
    for h in hits:
        pid = h.parent_segment_id
        if pid and pid in parents:
            if pid in seen_parent:
                continue
            seen_parent.add(pid)
            p = parents[pid]
            out.append(
                SegmentRecord(
                    segment_id=p.segment_id,
                    document_id=p.document_id or h.document_id,
                    knowledge_base_id=p.knowledge_base_id or h.knowledge_base_id,
                    content=p.content or h.content,
                    parent_segment_id=None,
                    score=h.score,
                    meta={
                        **(p.meta or {}),
                        "filled_from_child": h.segment_id,
                        "hit_child_content": (h.content or "")[:240],
                        "segment_type": "parent",
                    },
                )
            )
        else:
            out.append(h)
    return out


def segments_as_sources(segments: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return [
        {
            "segment_id": s["segment_id"],
            "document_id": s["document_id"],
            "knowledge_base_id": s["knowledge_base_id"],
            "score": s.get("score"),
            "content": (s.get("content") or "")[:500],
            "content_preview": (s.get("content") or "")[:500],
            "recall_source": s.get("recall_source"),
            "segment_type": s.get("segment_type"),
            "hit_child_content": s.get("hit_child_content"),
        }
        for s in segments
    ]
