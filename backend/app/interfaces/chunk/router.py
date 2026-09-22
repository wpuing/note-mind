"""切分 / 向量化分步接口（不绑定上传落盘）。"""
from __future__ import annotations

import asyncio
import json
import re
import tempfile
import uuid
from pathlib import Path
from typing import Any

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile
from pydantic import BaseModel, Field

from app.core.config import settings
from app.core.defaults import resolve_knowledge_base_id
from app.core.security import verify_ai_engine_token
from app.infrastructure.ai.embedding.dashscope_embed import embed_texts
from app.infrastructure.chunk.splitter import split_document
from app.infrastructure.parser.loader import extract_text
from app.infrastructure.vector.pgvector.store import upsert_parent_contents, upsert_segments

router = APIRouter(prefix="/chunk", tags=["chunk"])

_ALLOWED = {".pdf", ".txt", ".md", ".markdown", ".docx", ".xlsx", ".pptx"}
_DOC_ID_RE = re.compile(r"^[a-fA-F0-9]{32}$")


def _normalize_kb_id(knowledge_base_id: str | None) -> str:
    return resolve_knowledge_base_id(knowledge_base_id)



def _parse_separators(separators_json: str | None) -> list[str] | None:
    if not separators_json or not separators_json.strip():
        return None
    try:
        data = json.loads(separators_json)
        if isinstance(data, list):
            return [str(x) for x in data]
    except json.JSONDecodeError:
        return [s for s in separators_json.split(",") if s != ""]
    return None


class EmbedSegmentItem(BaseModel):
    id: str
    content: str
    parent_segment_id: str | None = None
    meta: dict[str, Any] | None = None


class EmbedRequest(BaseModel):
    document_id: str
    knowledge_base_id: str = settings.default_knowledge_base_id
    segments: list[EmbedSegmentItem] = Field(default_factory=list)


@router.post("/split")
async def split_file(
    file: UploadFile = File(...),
    knowledge_base_id: str = Form(default=settings.default_knowledge_base_id),
    document_id: str | None = Form(default=None),
    chunk_size: int | None = Form(default=None),
    chunk_overlap: int | None = Form(default=None),
    strategy_type: str | None = Form(default=None),
    parent_chunk_size: int | None = Form(default=None),
    child_chunk_size: int | None = Form(default=None),
    child_overlap: int | None = Form(default=None),
    separators_json: str | None = Form(default=None),
    chunk_strategy_id: str | None = Form(default=None),
    _: None = Depends(verify_ai_engine_token),
):
    """解析 + 切分，返回片段列表，不写 PG。"""
    if not file.filename:
        raise HTTPException(status_code=400, detail="filename required")
    suffix = Path(file.filename).suffix.lower()
    if suffix not in _ALLOWED:
        raise HTTPException(status_code=400, detail=f"unsupported type {suffix}; allow {_ALLOWED}")

    kb_id = _normalize_kb_id(knowledge_base_id)
    doc_id = (document_id or "").strip() or None
    if doc_id and not _DOC_ID_RE.match(doc_id):
        raise HTTPException(status_code=400, detail="document_id must be 32-char hex")

    size = chunk_size if chunk_size and chunk_size > 0 else settings.chunk_size
    overlap = (
        chunk_overlap if chunk_overlap is not None and chunk_overlap >= 0 else settings.chunk_overlap
    )
    st = (strategy_type or "RECURSIVE").strip().upper()
    if st != "PARENT_CHILD" and overlap >= size:
        raise HTTPException(status_code=400, detail="chunk_overlap must be < chunk_size")

    raw = await file.read()
    if not raw:
        raise HTTPException(status_code=400, detail="empty file")

    with tempfile.TemporaryDirectory(prefix="notemind-split-") as tmp:
        dest = Path(tmp) / Path(file.filename).name
        dest.write_bytes(raw)
        try:
            text = await asyncio.to_thread(extract_text, dest, preview=False)
        except Exception as exc:  # noqa: BLE001
            raise HTTPException(status_code=400, detail=f"parse failed: {exc}") from exc

    separators = _parse_separators(separators_json)
    pieces = split_document(
        text,
        strategy_type=st,
        chunk_size=size,
        chunk_overlap=overlap,
        separators=separators,
        parent_chunk_size=parent_chunk_size,
        child_chunk_size=child_chunk_size,
        child_overlap=child_overlap,
    )
    if not pieces:
        raise HTTPException(status_code=400, detail="empty document after parse")

    segments: list[dict[str, Any]] = []
    for i, piece in enumerate(pieces):
        sid = piece.segment_id or uuid.uuid4().hex
        seg_type = (piece.segment_type or "CHUNK").upper()
        meta: dict[str, Any] = {
            "chunk_index": i,
            "filename": file.filename,
            "chunk_size": size,
            "chunk_overlap": overlap,
            "strategy_type": st,
            "segment_type": seg_type,
        }
        if chunk_strategy_id:
            meta["chunk_strategy_id"] = chunk_strategy_id
        if piece.meta:
            meta.update(piece.meta)
            meta["segment_type"] = seg_type
        page = None
        if isinstance(piece.meta, dict):
            page = piece.meta.get("page") or piece.meta.get("page_number")
        segments.append(
            {
                "id": sid,
                "parent_id": piece.parent_segment_id,
                "segment_type": seg_type,
                "segment_index": int(meta.get("chunk_index") if meta.get("chunk_index") is not None else i),
                "content": piece.content,
                "content_tokens": len(piece.content),
                "page": page,
                "vector_status": "SKIPPED" if seg_type == "PARENT" else "PENDING",
                "meta": meta,
            }
        )

    vectorizable = sum(1 for s in segments if s["segment_type"] != "PARENT")
    return {
        "document_id": doc_id,
        "knowledge_base_id": kb_id,
        "strategy_type": st,
        "chunk_strategy_id": chunk_strategy_id,
        "char_count": len(text or ""),
        "segment_count": len(segments),
        "vectorizable_count": vectorizable,
        "segments": segments,
        "status": "chunked",
    }


@router.post("/embed")
async def embed_segments(
    body: EmbedRequest,
    _: None = Depends(verify_ai_engine_token),
):
    """对给定片段做 embedding 并写入 PG；PARENT 只写 BM25 供父块回填。"""
    doc_id = (body.document_id or "").strip()
    if not doc_id or not _DOC_ID_RE.match(doc_id):
        raise HTTPException(status_code=400, detail="document_id must be 32-char hex")
    kb_id = _normalize_kb_id(body.knowledge_base_id)
    items = [s for s in body.segments if s.content and s.content.strip()]
    if not items:
        raise HTTPException(status_code=400, detail="segments required")

    parents = [
        s
        for s in items
        if str((s.meta or {}).get("segment_type") or "").upper() == "PARENT"
    ]
    vector_items = [
        s
        for s in items
        if str((s.meta or {}).get("segment_type") or "").upper() != "PARENT"
    ]
    if not vector_items and not parents:
        raise HTTPException(status_code=400, detail="segments required")

    count = 0
    if vector_items:
        contents = [s.content for s in vector_items]
        try:
            embeddings = await asyncio.to_thread(embed_texts, contents)
        except Exception as exc:  # noqa: BLE001
            raise HTTPException(status_code=502, detail=f"embedding failed: {exc}") from exc

        rows: list[dict[str, Any]] = []
        for seg, emb in zip(vector_items, embeddings):
            sid = (seg.id or "").strip() or uuid.uuid4().hex
            rows.append(
                {
                    "id": sid,
                    "content": seg.content,
                    "embedding": emb,
                    "parent_segment_id": seg.parent_segment_id,
                    "meta": seg.meta or {},
                }
            )

        count = upsert_segments(
            knowledge_base_id=kb_id,
            document_id=doc_id,
            embedding_model=settings.default_embedding_model,
            segments=rows,
        )

    if parents:
        upsert_parent_contents(
            knowledge_base_id=kb_id,
            document_id=doc_id,
            parents=[
                {
                    "id": (s.id or "").strip() or uuid.uuid4().hex,
                    "content": s.content,
                }
                for s in parents
            ],
        )

    return {
        "document_id": doc_id,
        "knowledge_base_id": kb_id,
        "segment_count": count,
        "parent_count": len(parents),
        "status": "ready",
    }
