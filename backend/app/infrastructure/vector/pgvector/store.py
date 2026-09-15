"""PGVector + BM25 表读写。"""
from __future__ import annotations

import json
import re
from dataclasses import dataclass
from typing import Any

import threading

import jieba

from app.infrastructure.vector.pgvector.pool import pg_conn


@dataclass
class SegmentRecord:
    segment_id: str
    document_id: str
    knowledge_base_id: str
    content: str
    parent_segment_id: str | None = None
    score: float = 0.0
    meta: dict[str, Any] | None = None


_CJK_OR_ALNUM = re.compile(r"[\u4e00-\u9fffA-Za-z0-9]")
_JIEBA_LOCK = threading.Lock()


def _escape_ilike(term: str) -> str:
    """转义 ILIKE 通配符，避免用户输入 %/_ 扩大匹配面。"""
    return (term or "").replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")


def _bm25_terms(text: str, *, limit: int = 24) -> list[str]:
    """中文分词后的检索词（去重，过滤单字/纯标点）。"""
    raw = (text or "").strip()
    if not raw:
        return []
    terms: list[str] = []
    seen: set[str] = set()
    with _JIEBA_LOCK:
        tokens = list(jieba.cut_for_search(raw))
    for t in tokens:
        w = (t or "").strip()
        if len(w) < 2:
            continue
        if not _CJK_OR_ALNUM.search(w):
            continue
        if w in seen:
            continue
        seen.add(w)
        terms.append(w)
        if len(terms) >= limit:
            break
    if not terms:
        # 兜底：取前若干汉字/字母，避免整句无法命中
        compact = "".join(ch for ch in raw if _CJK_OR_ALNUM.match(ch))
        if len(compact) >= 2:
            terms.append(compact[:12])
    return terms


def _bm25_index_text(content: str) -> str:
    """写入 to_tsvector 前先分词空格拼接，使 simple 配置能按词命中中文。"""
    terms = _bm25_terms(content, limit=200)
    return " ".join(terms) if terms else (content or "")


def _tsquery_from_terms(terms: list[str]) -> str | None:
    """构造 simple to_tsquery：词1 | 词2 | …（OR，提高中文召回）。"""
    if not terms:
        return None
    parts: list[str] = []
    for t in terms:
        safe = re.sub(r"[^\u4e00-\u9fffA-Za-z0-9]", "", t)
        if len(safe) < 2:
            continue
        parts.append(safe)
    if not parts:
        return None
    return " | ".join(parts)


def delete_document_vectors(document_id: str) -> None:
    with pg_conn() as conn:
        conn.execute(
            "DELETE FROM knowledge_segment_vector WHERE document_id = %s",
            (document_id,),
        )
        conn.execute(
            "DELETE FROM knowledge_segment_bm25 WHERE document_id = %s",
            (document_id,),
        )


def delete_segment_vectors(segment_ids: list[str]) -> int:
    """按片段 id 清理 PGVector / BM25。"""
    ids = [str(s).strip() for s in (segment_ids or []) if s and str(s).strip()]
    if not ids:
        return 0
    with pg_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                "DELETE FROM knowledge_segment_vector WHERE segment_id = ANY(%s::text[])",
                (ids,),
            )
            cur.execute(
                "DELETE FROM knowledge_segment_bm25 WHERE segment_id = ANY(%s::text[])",
                (ids,),
            )
    return len(ids)


def delete_knowledge_base_vectors(knowledge_base_id: str) -> dict[str, int]:
    """按知识库清理 PGVector / BM25。"""
    with pg_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(
                "DELETE FROM knowledge_segment_vector WHERE knowledge_base_id = %s",
                (knowledge_base_id,),
            )
            vec_n = cur.rowcount
            cur.execute(
                "DELETE FROM knowledge_segment_bm25 WHERE knowledge_base_id = %s",
                (knowledge_base_id,),
            )
            bm25_n = cur.rowcount
    return {"vector_deleted": int(vec_n or 0), "bm25_deleted": int(bm25_n or 0)}


def _vector_status(segs: int, done: int, pending: int, failed: int) -> str:
    if segs <= 0:
        return "EMPTY"
    if failed > 0 and done == 0:
        return "FAILED"
    if pending > 0 or failed > 0:
        return "PARTIAL"
    if done > 0:
        return "READY"
    return "EMPTY"


def knowledge_base_vector_status(knowledge_base_id: str) -> dict[str, Any]:
    """从 PGVector/BM25 汇总知识库及下属文档的向量状态（真实入库片段在 PG，不在 MySQL）。"""
    kb = (knowledge_base_id or "").strip()
    if not kb:
        return {
            "knowledge_base_id": "",
            "segment_count": 0,
            "vector_done_count": 0,
            "vector_pending_count": 0,
            "vector_failed_count": 0,
            "vector_status": "EMPTY",
            "documents": [],
        }

    with pg_conn() as conn:
        bm25_rows = conn.execute(
            """
            SELECT document_id, COUNT(1) AS cnt
            FROM knowledge_segment_bm25
            WHERE knowledge_base_id = %s
            GROUP BY document_id
            """,
            (kb,),
        ).fetchall()
        vec_rows = conn.execute(
            """
            SELECT document_id, COUNT(1) AS cnt
            FROM knowledge_segment_vector
            WHERE knowledge_base_id = %s
            GROUP BY document_id
            """,
            (kb,),
        ).fetchall()

    bm25_map = {str(r["document_id"]): int(r["cnt"] or 0) for r in bm25_rows}
    vec_map = {str(r["document_id"]): int(r["cnt"] or 0) for r in vec_rows}
    doc_ids = sorted(set(bm25_map) | set(vec_map))

    documents: list[dict[str, Any]] = []
    total_segs = 0
    total_done = 0
    total_pending = 0
    for doc_id in doc_ids:
        segs = max(bm25_map.get(doc_id, 0), vec_map.get(doc_id, 0))
        done = vec_map.get(doc_id, 0)
        # 同一次入库会同时写 BM25+向量；若仅有全文无向量，记为 PENDING
        pending = max(0, bm25_map.get(doc_id, 0) - done)
        failed = 0
        total_segs += segs
        total_done += done
        total_pending += pending
        documents.append(
            {
                "document_id": doc_id,
                "segment_count": segs,
                "vector_done_count": done,
                "vector_pending_count": pending,
                "vector_failed_count": failed,
                "vector_status": _vector_status(segs, done, pending, failed),
            }
        )

    return {
        "knowledge_base_id": kb,
        "segment_count": total_segs,
        "vector_done_count": total_done,
        "vector_pending_count": total_pending,
        "vector_failed_count": 0,
        "vector_status": _vector_status(total_segs, total_done, total_pending, 0),
        "documents": documents,
    }


def upsert_parent_contents(
    *,
    knowledge_base_id: str,
    document_id: str,
    parents: list[dict[str, Any]],
) -> int:
    """父块仅写入 BM25 内容表（不入向量表），供 parent_fill 按 id 回填。"""
    if not parents:
        return 0
    with pg_conn() as conn:
        with conn.cursor() as cur:
            for seg in parents:
                sid = seg["id"]
                content = (seg.get("content") or "").replace("\x00", "")
                if not sid or not content.strip():
                    continue
                cur.execute(
                    """
                    INSERT INTO knowledge_segment_bm25 (
                        segment_id, knowledge_base_id, document_id,
                        parent_segment_id, content, content_tsv, updated_at
                    ) VALUES (
                        %s, %s, %s, NULL, %s, to_tsvector('simple', %s), NOW()
                    )
                    ON CONFLICT (segment_id) DO UPDATE SET
                        content = EXCLUDED.content,
                        content_tsv = EXCLUDED.content_tsv,
                        knowledge_base_id = EXCLUDED.knowledge_base_id,
                        document_id = EXCLUDED.document_id,
                        updated_at = NOW()
                    """,
                    (
                        sid,
                        knowledge_base_id,
                        document_id,
                        content,
                        _bm25_index_text(content),
                    ),
                )
    return len(parents)


def upsert_segments(
    *,
    knowledge_base_id: str,
    document_id: str,
    embedding_model: str,
    segments: list[dict[str, Any]],
) -> int:
    """segments: [{id, content, embedding, parent_segment_id?, meta?}]"""
    if not segments:
        return 0
    with pg_conn() as conn:
        with conn.cursor() as cur:
            for seg in segments:
                sid = seg["id"]
                content = (seg["content"] or "").replace("\x00", "")
                emb = seg["embedding"]
                parent = seg.get("parent_segment_id")
                meta = seg.get("meta") or {}
                preview = content[:512]
                vector_row_id = seg.get("vector_id") or sid
                cur.execute(
                    """
                    INSERT INTO knowledge_segment_vector (
                        id, segment_id, parent_segment_id, knowledge_base_id,
                        document_id, embedding_model, content_preview, meta_json, embedding
                    ) VALUES (
                        %s, %s, %s, %s, %s, %s, %s, %s::jsonb, %s
                    )
                    ON CONFLICT (id) DO UPDATE SET
                        embedding = EXCLUDED.embedding,
                        content_preview = EXCLUDED.content_preview,
                        meta_json = EXCLUDED.meta_json
                    """,
                    (
                        vector_row_id,
                        sid,
                        parent,
                        knowledge_base_id,
                        document_id,
                        embedding_model,
                        preview,
                        json.dumps(meta, ensure_ascii=False),
                        emb,
                    ),
                )
                cur.execute(
                    """
                    INSERT INTO knowledge_segment_bm25 (
                        segment_id, knowledge_base_id, document_id,
                        parent_segment_id, content, content_tsv, updated_at
                    ) VALUES (
                        %s, %s, %s, %s, %s, to_tsvector('simple', %s), NOW()
                    )
                    ON CONFLICT (segment_id) DO UPDATE SET
                        content = EXCLUDED.content,
                        content_tsv = EXCLUDED.content_tsv,
                        knowledge_base_id = EXCLUDED.knowledge_base_id,
                        document_id = EXCLUDED.document_id,
                        parent_segment_id = EXCLUDED.parent_segment_id,
                        updated_at = NOW()
                    """,
                    (
                        sid,
                        knowledge_base_id,
                        document_id,
                        parent,
                        content,
                        _bm25_index_text(content),
                    ),
                )
    return len(segments)


def vector_search(
    *,
    query_embedding: list[float],
    knowledge_base_id: str | None,
    top_k: int = 10,
    document_ids: list[str] | None = None,
) -> list[SegmentRecord]:
    doc_ids = [str(d).strip() for d in (document_ids or []) if str(d).strip()]
    sql = """
        SELECT
            v.segment_id,
            v.document_id,
            v.knowledge_base_id,
            v.parent_segment_id,
            v.meta_json,
            COALESCE(b.content, v.content_preview) AS content,
            1 - (v.embedding <=> %s::vector) AS score
        FROM knowledge_segment_vector v
        LEFT JOIN knowledge_segment_bm25 b ON b.segment_id = v.segment_id
        WHERE (%s::text IS NULL OR v.knowledge_base_id = %s)
    """
    params: list[Any] = [query_embedding, knowledge_base_id, knowledge_base_id]
    if doc_ids:
        sql += " AND v.document_id = ANY(%s)"
        params.append(doc_ids)
    sql += """
        ORDER BY v.embedding <=> %s::vector
        LIMIT %s
    """
    params.extend([query_embedding, top_k])
    with pg_conn() as conn:
        rows = conn.execute(sql, tuple(params)).fetchall()
    return [
        SegmentRecord(
            segment_id=r["segment_id"],
            document_id=r["document_id"],
            knowledge_base_id=r["knowledge_base_id"],
            content=r["content"] or "",
            parent_segment_id=r.get("parent_segment_id"),
            score=float(r["score"] or 0.0),
            meta=r.get("meta_json") if isinstance(r.get("meta_json"), dict) else None,
        )
        for r in rows
    ]


def keyword_search(
    *,
    question: str,
    knowledge_base_id: str | None,
    top_k: int = 10,
    document_ids: list[str] | None = None,
) -> list[SegmentRecord]:
    """BM25/全文检索：jieba 分词 + tsvector OR；失败再按词 ILIKE 打分。"""
    q = (question or "").strip()
    if not q:
        return []
    terms = _bm25_terms(q, limit=16)
    doc_ids = [str(d).strip() for d in (document_ids or []) if str(d).strip()]
    doc_clause = " AND document_id = ANY(%s)" if doc_ids else ""
    tsquery = _tsquery_from_terms(terms)

    with pg_conn() as conn:
        rows: list[Any] = []
        if tsquery:
            tsv_params: list[Any] = [tsquery, knowledge_base_id, knowledge_base_id, tsquery]
            if doc_ids:
                tsv_params.append(doc_ids)
            tsv_params.append(top_k)
            rows = conn.execute(
                f"""
                SELECT
                    segment_id, document_id, knowledge_base_id, parent_segment_id, content,
                    ts_rank_cd(content_tsv, to_tsquery('simple', %s)) AS score
                FROM knowledge_segment_bm25
                WHERE (%s::text IS NULL OR knowledge_base_id = %s)
                  AND content_tsv @@ to_tsquery('simple', %s)
                  {doc_clause}
                ORDER BY score DESC
                LIMIT %s
                """,
                tuple(tsv_params),
            ).fetchall()

        if not rows and terms:
            # 按词 ILIKE：命中词越多分越高（避免整句当子串几乎永远不中）
            score_expr = " + ".join(
                ["(CASE WHEN content ILIKE %s ESCAPE '\\' THEN 1 ELSE 0 END)"] * len(terms)
            )
            like_params: list[Any] = [f"%{_escape_ilike(t)}%" for t in terms]
            like_params.extend([knowledge_base_id, knowledge_base_id])
            like_params.extend([f"%{_escape_ilike(t)}%" for t in terms])
            if doc_ids:
                like_params.append(doc_ids)
            like_params.append(top_k)
            where_likes = " OR ".join(["content ILIKE %s ESCAPE '\\'"] * len(terms))
            rows = conn.execute(
                f"""
                SELECT
                    segment_id, document_id, knowledge_base_id, parent_segment_id, content,
                    ({score_expr})::float AS score
                FROM knowledge_segment_bm25
                WHERE (%s::text IS NULL OR knowledge_base_id = %s)
                  AND ({where_likes})
                  {doc_clause}
                ORDER BY score DESC, updated_at DESC
                LIMIT %s
                """,
                tuple(like_params),
            ).fetchall()

    return [
        SegmentRecord(
            segment_id=r["segment_id"],
            document_id=r["document_id"],
            knowledge_base_id=r["knowledge_base_id"],
            content=r["content"] or "",
            parent_segment_id=r.get("parent_segment_id"),
            score=float(r["score"] or 0.0),
        )
        for r in rows
    ]


def refresh_bm25_tsv(*, knowledge_base_id: str | None = None) -> int:
    """用 jieba 分词重建 content_tsv（不改 content）。返回更新行数。"""
    with pg_conn() as conn:
        if knowledge_base_id:
            rows = conn.execute(
                """
                SELECT segment_id, content FROM knowledge_segment_bm25
                WHERE knowledge_base_id = %s
                """,
                (knowledge_base_id,),
            ).fetchall()
        else:
            rows = conn.execute(
                "SELECT segment_id, content FROM knowledge_segment_bm25"
            ).fetchall()
        n = 0
        for r in rows:
            conn.execute(
                """
                UPDATE knowledge_segment_bm25
                SET content_tsv = to_tsvector('simple', %s), updated_at = NOW()
                WHERE segment_id = %s
                """,
                (_bm25_index_text(r["content"] or ""), r["segment_id"]),
            )
            n += 1
        return n


def list_document_segments(
    *,
    document_id: str,
    keyword: str | None = None,
    page: int = 1,
    page_size: int = 10,
) -> dict[str, Any]:
    """按文档分页列出片段（全文在 BM25 表）；可选内容关键词。"""
    doc_id = (document_id or "").strip()
    if not doc_id:
        return {"total": 0, "page": 1, "page_size": page_size, "records": []}
    safe_page = max(int(page or 1), 1)
    safe_size = min(max(int(page_size or 10), 1), 100)
    kw = (keyword or "").strip()
    where = "WHERE b.document_id = %s"
    args: list[Any] = [doc_id]
    if kw:
        where += " AND b.content ILIKE %s ESCAPE '\\'"
        args.append(f"%{_escape_ilike(kw[:120])}%")

    count_sql = f"""
        SELECT COUNT(1) AS cnt
        FROM knowledge_segment_bm25 b
        {where}
    """
    list_sql = f"""
        SELECT
            b.segment_id,
            b.document_id,
            b.knowledge_base_id,
            b.parent_segment_id,
            b.content,
            v.meta_json,
            v.content_preview
        FROM knowledge_segment_bm25 b
        LEFT JOIN knowledge_segment_vector v ON v.segment_id = b.segment_id
        {where}
        ORDER BY COALESCE((v.meta_json->>'chunk_index')::int, 2147483647), b.segment_id
        LIMIT %s OFFSET %s
    """
    with pg_conn() as conn:
        total_row = conn.execute(count_sql, args).fetchone()
        total = int((total_row or {}).get("cnt") or 0)
        list_args = list(args) + [safe_size, (safe_page - 1) * safe_size]
        rows = conn.execute(list_sql, list_args).fetchall()

    records = []
    for r in rows:
        meta = r.get("meta_json") if isinstance(r.get("meta_json"), dict) else {}
        chunk_index = meta.get("chunk_index") if isinstance(meta, dict) else None
        records.append(
            {
                "segment_id": r["segment_id"],
                "document_id": r["document_id"],
                "knowledge_base_id": r["knowledge_base_id"],
                "parent_segment_id": r.get("parent_segment_id"),
                "content": r.get("content") or "",
                "chunk_index": chunk_index,
                "content_preview": r.get("content_preview"),
            }
        )
    return {
        "total": total,
        "page": safe_page,
        "page_size": safe_size,
        "records": records,
    }


def get_segments_by_ids(segment_ids: list[str]) -> dict[str, SegmentRecord]:
    """按 segment_id 批量取正文（优先 BM25 全文）。"""
    ids = [str(x).strip() for x in (segment_ids or []) if x and str(x).strip()]
    if not ids:
        return {}
    # 去重保序
    seen: set[str] = set()
    ordered: list[str] = []
    for sid in ids:
        if sid not in seen:
            seen.add(sid)
            ordered.append(sid)
    sql = """
        SELECT
            COALESCE(b.segment_id, v.segment_id) AS segment_id,
            COALESCE(b.document_id, v.document_id) AS document_id,
            COALESCE(b.knowledge_base_id, v.knowledge_base_id) AS knowledge_base_id,
            COALESCE(b.parent_segment_id, v.parent_segment_id) AS parent_segment_id,
            COALESCE(b.content, v.content_preview, '') AS content,
            v.meta_json
        FROM knowledge_segment_bm25 b
        FULL OUTER JOIN knowledge_segment_vector v ON v.segment_id = b.segment_id
        WHERE COALESCE(b.segment_id, v.segment_id) = ANY(%s::text[])
    """
    with pg_conn() as conn:
        rows = conn.execute(sql, (ordered,)).fetchall()
    out: dict[str, SegmentRecord] = {}
    for r in rows:
        sid = r["segment_id"]
        out[sid] = SegmentRecord(
            segment_id=sid,
            document_id=r.get("document_id") or "",
            knowledge_base_id=r.get("knowledge_base_id") or "",
            content=r.get("content") or "",
            parent_segment_id=r.get("parent_segment_id"),
            score=0.0,
            meta=r.get("meta_json") if isinstance(r.get("meta_json"), dict) else None,
        )
    return out
