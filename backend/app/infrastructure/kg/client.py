"""企业知识图谱访问占位。"""

from __future__ import annotations

from typing import Any


def search_entities(query: str, *, limit: int = 10) -> list[dict[str, Any]]:
    """按自然语言或关键词检索实体。未接图库时返回空列表。"""
    _ = (query, limit)
    return []


def multi_hop(entity_id: str, *, depth: int = 2) -> list[dict[str, Any]]:
    """多跳关系查询占位。"""
    _ = (entity_id, depth)
    return []
