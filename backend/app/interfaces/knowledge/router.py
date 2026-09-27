"""知识库片段查询 / 向量清理（供 Java 管理端）。"""
from __future__ import annotations

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel, Field

from app.core.security import verify_ai_engine_token
from app.infrastructure.vector.pgvector.store import (
    delete_document_vectors,
    delete_knowledge_base_vectors,
    delete_segment_vectors,
    knowledge_base_vector_status,
    list_document_segments,
)

router = APIRouter(prefix="/knowledge", tags=["knowledge"])


class SegmentIdsBody(BaseModel):
    segment_ids: list[str] = Field(default_factory=list)


@router.get("/documents/{document_id}/segments")
async def document_segments(
    document_id: str,
    keyword: str | None = Query(default=None),
    page: int = Query(default=1, ge=1),
    page_size: int = Query(default=8, ge=1, le=100),
    _: None = Depends(verify_ai_engine_token),
):
    return list_document_segments(
        document_id=document_id,
        keyword=keyword,
        page=page,
        page_size=page_size,
    )


@router.delete("/documents/{document_id}/vectors")
async def clear_document_vectors(
    document_id: str,
    _: None = Depends(verify_ai_engine_token),
):
    """按文档清理 PGVector / BM25（删文档或清理孤儿时由 Java 调用）。"""
    delete_document_vectors(document_id)
    return {"document_id": document_id, "deleted": True}


@router.post("/segments/delete-vectors")
async def clear_segment_vectors(
    body: SegmentIdsBody,
    _: None = Depends(verify_ai_engine_token),
):
    """按片段 id 清理 PG 向量。"""
    n = delete_segment_vectors(body.segment_ids)
    return {"deleted": n}


@router.get("/bases/{knowledge_base_id}/vector-status")
async def knowledge_base_vectors_status(
    knowledge_base_id: str,
    _: None = Depends(verify_ai_engine_token),
):
    """从 PG 汇总知识库向量状态（供 Java 管理端展示）。"""
    return knowledge_base_vector_status(knowledge_base_id)


@router.delete("/bases/{knowledge_base_id}/vectors")
async def clear_knowledge_base_vectors(
    knowledge_base_id: str,
    _: None = Depends(verify_ai_engine_token),
):
    return delete_knowledge_base_vectors(knowledge_base_id)
