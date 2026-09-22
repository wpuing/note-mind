"""文档上传入库：解析 → 切分 → 向量化 → PG。"""
from __future__ import annotations

import asyncio
import json
import re
import shutil
import uuid
from datetime import datetime, timezone
from pathlib import Path

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile

from app.core.config import settings
from app.core.defaults import resolve_knowledge_base_id
from app.core.security import verify_ai_engine_token
from app.infrastructure.ai.embedding.dashscope_embed import embed_texts
from app.infrastructure.chunk.splitter import split_document
from app.infrastructure.parser.loader import extract_text
from app.infrastructure.vector.pgvector.store import upsert_parent_contents, upsert_segments

router = APIRouter(prefix="/ingest", tags=["ingest"])

_ALLOWED = {".pdf", ".txt", ".md", ".markdown", ".docx", ".xlsx", ".pptx"}
_DOC_ID_RE = re.compile(r"^[a-fA-F0-9]{32}$")


def _normalize_kb_id(knowledge_base_id: str | None) -> str:
    return resolve_knowledge_base_id(knowledge_base_id)



def _normalize_document_id(document_id: str | None) -> str:
    if document_id and document_id.strip():
        doc_id = document_id.strip()
        if not _DOC_ID_RE.match(doc_id):
            raise HTTPException(status_code=400, detail="document_id must be 32-char hex")
        return doc_id.lower()
    return uuid.uuid4().hex


def _parse_separators(separators_json: str | None) -> list[str] | None:
    if not separators_json or not separators_json.strip():
        return None
    try:
        data = json.loads(separators_json)
        if isinstance(data, list):
            return [str(x) for x in data]
    except json.JSONDecodeError:
        # 逗号分隔兜底
        return [s for s in separators_json.split(",") if s != ""]
    return None


async def _ingest_upload(
    *,
    file: UploadFile,
    knowledge_base_id: str | None,
    document_id: str | None,
    chunk_size: int | None = None,
    chunk_overlap: int | None = None,
    strategy_type: str | None = None,
    parent_chunk_size: int | None = None,
    child_chunk_size: int | None = None,
    child_overlap: int | None = None,
    separators_json: str | None = None,
    chunk_strategy_id: str | None = None,
) -> dict:
    if not file.filename:
        raise HTTPException(status_code=400, detail="filename required")
    suffix = Path(file.filename).suffix.lower()
    if suffix not in _ALLOWED:
        raise HTTPException(
            status_code=400,
            detail=f"unsupported type {suffix}; allow {_ALLOWED}",
        )

    kb_id = _normalize_kb_id(knowledge_base_id)
    doc_id = _normalize_document_id(document_id)
    size = chunk_size if chunk_size and chunk_size > 0 else settings.chunk_size
    overlap = (
        chunk_overlap if chunk_overlap is not None and chunk_overlap >= 0 else settings.chunk_overlap
    )
    st = (strategy_type or "RECURSIVE").strip().upper()
    if st != "PARENT_CHILD" and overlap >= size:
        raise HTTPException(status_code=400, detail="chunk_overlap must be < chunk_size")

    dest_dir = settings.upload_dir / doc_id
    dest_dir.mkdir(parents=True, exist_ok=True)
    safe_name = Path(file.filename or "upload.bin").name
    dest_path = dest_dir / safe_name

    try:
        with dest_path.open("wb") as out:
            shutil.copyfileobj(file.file, out)
    finally:
        await file.close()

    try:
        text = await asyncio.to_thread(extract_text, dest_path, preview=False)
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

    # 仅向量子块 / 普通块；父块只写 BM25 供回填
    parent_pieces = [p for p in pieces if (p.segment_type or "CHUNK").upper() == "PARENT"]
    vector_pieces = [p for p in pieces if (p.segment_type or "CHUNK").upper() != "PARENT"]
    if not vector_pieces:
        raise HTTPException(status_code=400, detail="no vectorizable segments after split")
    contents = [p.content for p in vector_pieces]
    try:
        embeddings = await asyncio.to_thread(embed_texts, contents)
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(status_code=502, detail=f"embedding failed: {exc}") from exc

    segments = []
    for i, (piece, emb) in enumerate(zip(vector_pieces, embeddings)):
        sid = piece.segment_id or uuid.uuid4().hex
        meta = {
            "chunk_index": i,
            "filename": safe_name,
            "chunk_size": size,
            "chunk_overlap": overlap,
            "strategy_type": st,
            "segment_type": (piece.segment_type or "CHUNK").upper(),
        }
        if chunk_strategy_id:
            meta["chunk_strategy_id"] = chunk_strategy_id
        if piece.meta:
            meta.update(piece.meta)
        segments.append(
            {
                "id": sid,
                "content": piece.content,
                "embedding": emb,
                "parent_segment_id": piece.parent_segment_id,
                "meta": meta,
            }
        )

    count = upsert_segments(
        knowledge_base_id=kb_id,
        document_id=doc_id,
        embedding_model=settings.default_embedding_model,
        segments=segments,
    )
    if parent_pieces:
        upsert_parent_contents(
            knowledge_base_id=kb_id,
            document_id=doc_id,
            parents=[
                {
                    "id": p.segment_id or uuid.uuid4().hex,
                    "content": p.content,
                }
                for p in parent_pieces
            ],
        )

    meta = {
        "document_id": doc_id,
        "knowledge_base_id": kb_id,
        "filename": file.filename,
        "segment_count": count,
        "chunk_size": size,
        "chunk_overlap": overlap,
        "strategy_type": st,
        "chunk_strategy_id": chunk_strategy_id,
        "char_count": sum(len(p.content) for p in pieces),
        "status": "ready",
        "created_at": datetime.now(timezone.utc).isoformat(),
        "path": str(dest_path),
    }
    meta_path = settings.meta_dir / f"{doc_id}.json"
    meta_path.write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")

    return {
        "document_id": doc_id,
        "knowledge_base_id": kb_id,
        "segment_count": count,
        "chunk_size": size,
        "chunk_overlap": overlap,
        "strategy_type": st,
        "char_count": meta["char_count"],
        "status": "ready",
    }


@router.post("/upload")
async def upload_and_ingest(
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
    return await _ingest_upload(
        file=file,
        knowledge_base_id=knowledge_base_id,
        document_id=document_id,
        chunk_size=chunk_size,
        chunk_overlap=chunk_overlap,
        strategy_type=strategy_type,
        parent_chunk_size=parent_chunk_size,
        child_chunk_size=child_chunk_size,
        child_overlap=child_overlap,
        separators_json=separators_json,
        chunk_strategy_id=chunk_strategy_id,
    )


@router.post("/documents/{document_id}")
async def ingest_by_document_id(
    document_id: str,
    file: UploadFile = File(...),
    knowledge_base_id: str = Form(default=settings.default_knowledge_base_id),
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
    return await _ingest_upload(
        file=file,
        knowledge_base_id=knowledge_base_id,
        document_id=document_id,
        chunk_size=chunk_size,
        chunk_overlap=chunk_overlap,
        strategy_type=strategy_type,
        parent_chunk_size=parent_chunk_size,
        child_chunk_size=child_chunk_size,
        child_overlap=child_overlap,
        separators_json=separators_json,
        chunk_strategy_id=chunk_strategy_id,
    )
