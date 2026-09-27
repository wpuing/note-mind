"""文本切分：滑窗 / 递归分隔符 / 父子分块（仅子块入向量）。"""
from __future__ import annotations

import uuid
from dataclasses import dataclass
from typing import Sequence


@dataclass
class ChunkPiece:
    content: str
    parent_segment_id: str | None = None
    meta: dict | None = None
    segment_type: str = "CHUNK"  # PARENT / CHILD / CHUNK
    segment_id: str | None = None


def split_text(text: str, chunk_size: int = 500, chunk_overlap: int = 50) -> list[str]:
    """兼容旧接口：固定滑窗。"""
    return [p.content for p in _window_split(text, chunk_size, chunk_overlap)]


def split_document(
    text: str,
    *,
    strategy_type: str = "RECURSIVE",
    chunk_size: int = 500,
    chunk_overlap: int = 50,
    separators: Sequence[str] | None = None,
    parent_chunk_size: int | None = None,
    child_chunk_size: int | None = None,
    child_overlap: int | None = None,
) -> list[ChunkPiece]:
    """按策略切分；PARENT_CHILD 仅返回子块，并带 parent_segment_id。"""
    st = (strategy_type or "RECURSIVE").strip().upper()
    if st == "PARENT_CHILD":
        return _parent_child_split(
            text,
            parent_size=parent_chunk_size or chunk_size or 1000,
            child_size=child_chunk_size or min(chunk_size or 500, 500),
            child_overlap=child_overlap if child_overlap is not None else chunk_overlap,
            separators=separators,
        )
    if st in {"RECURSIVE", "SLIDING", "FIXED"}:
        seps = list(separators) if separators else ["\n\n", "\n", "。", "！", "？", "；", " ", ""]
        return _recursive_split(text, chunk_size, chunk_overlap, seps)
    return _window_split(text, chunk_size, chunk_overlap)


def _window_split(text: str, chunk_size: int, chunk_overlap: int) -> list[ChunkPiece]:
    cleaned = (text or "").strip()
    if not cleaned:
        return []
    if chunk_size <= 0:
        return [ChunkPiece(content=cleaned)]
    overlap = max(0, min(chunk_overlap, chunk_size - 1))
    pieces: list[ChunkPiece] = []
    start = 0
    n = len(cleaned)
    idx = 0
    while start < n:
        end = min(start + chunk_size, n)
        piece = cleaned[start:end].strip()
        if piece:
            pieces.append(
                ChunkPiece(
                    content=piece,
                    segment_id=uuid.uuid4().hex,
                    segment_type="CHUNK",
                    meta={"chunk_index": idx},
                )
            )
            idx += 1
        if end >= n:
            break
        start = max(0, end - overlap)
        if start >= end:
            start = end
    return pieces


def _recursive_split(
    text: str,
    chunk_size: int,
    chunk_overlap: int,
    separators: Sequence[str],
) -> list[ChunkPiece]:
    cleaned = (text or "").strip()
    if not cleaned:
        return []
    if chunk_size <= 0:
        return [ChunkPiece(content=cleaned)]

    parts = _recursive_split_raw(cleaned, chunk_size, list(separators))
    # 过长块再用滑窗兜底；短块尽量合并到 size
    merged = _merge_small(parts, chunk_size)
    final: list[ChunkPiece] = []
    idx = 0
    for part in merged:
        if len(part) <= chunk_size:
            final.append(
                ChunkPiece(
                    content=part,
                    segment_id=uuid.uuid4().hex,
                    segment_type="CHUNK",
                    meta={"chunk_index": idx},
                )
            )
            idx += 1
        else:
            for w in _window_split(part, chunk_size, chunk_overlap):
                w.meta = {"chunk_index": idx, **(w.meta or {})}
                if not w.segment_id:
                    w.segment_id = uuid.uuid4().hex
                w.segment_type = "CHUNK"
                final.append(w)
                idx += 1
    return final


def _recursive_split_raw(text: str, chunk_size: int, separators: list[str]) -> list[str]:
    if len(text) <= chunk_size:
        return [text] if text.strip() else []
    if not separators:
        return [text]

    sep = separators[0]
    rest = separators[1:]
    if sep == "":
        # 按字符硬切
        out: list[str] = []
        for i in range(0, len(text), chunk_size):
            piece = text[i : i + chunk_size].strip()
            if piece:
                out.append(piece)
        return out

    splits = text.split(sep) if sep else [text]
    good: list[str] = []
    for i, s in enumerate(splits):
        piece = s if i == len(splits) - 1 else s + sep
        piece = piece.strip()
        if not piece:
            continue
        if len(piece) <= chunk_size:
            good.append(piece)
        else:
            good.extend(_recursive_split_raw(piece, chunk_size, rest))
    return good


def _merge_small(parts: list[str], chunk_size: int) -> list[str]:
    if not parts:
        return []
    merged: list[str] = []
    buf = ""
    for p in parts:
        if not buf:
            buf = p
            continue
        if len(buf) + 1 + len(p) <= chunk_size:
            buf = f"{buf}\n{p}" if not buf.endswith("\n") else f"{buf}{p}"
        else:
            merged.append(buf)
            buf = p
    if buf:
        merged.append(buf)
    return merged


def _parent_child_split(
    text: str,
    *,
    parent_size: int,
    child_size: int,
    child_overlap: int,
    separators: Sequence[str] | None,
) -> list[ChunkPiece]:
    """返回父块 + 子块；父块不入向量，子块带 parent_segment_id。"""
    seps = list(separators) if separators else ["\n\n", "\n", "。", "！", "？", "；", " ", ""]
    parents = _recursive_split(text, parent_size, max(0, parent_size // 10), seps)
    pieces: list[ChunkPiece] = []
    child_idx = 0
    for p_idx, parent in enumerate(parents):
        parent_id = uuid.uuid4().hex
        parent_content = parent.content
        pieces.append(
            ChunkPiece(
                content=parent_content,
                segment_id=parent_id,
                segment_type="PARENT",
                meta={"chunk_index": p_idx, "parent_index": p_idx},
            )
        )
        sub = _recursive_split(parent_content, child_size, child_overlap, seps)
        if not sub:
            continue
        for s in sub:
            sid = uuid.uuid4().hex
            pieces.append(
                ChunkPiece(
                    content=s.content,
                    segment_id=sid,
                    segment_type="CHILD",
                    parent_segment_id=parent_id,
                    meta={
                        "chunk_index": child_idx,
                        "parent_index": p_idx,
                        "parent_preview": parent_content[:120],
                    },
                )
            )
            child_idx += 1
    return pieces
