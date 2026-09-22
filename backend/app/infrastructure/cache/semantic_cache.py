"""语义缓存（规划）：查询向量相似度命中则跳过 LLM。

目标行为（Phase B）：
- 键：规范化 query 的 embedding
- 值：历史 answer + sources 摘要
- 阈值：默认 cosine > 0.95 直接命中
- 调用点：Java 编排前或 Python chat 入口最前

当前为占位模块，禁止在未实现时伪装命中。
"""
from __future__ import annotations

from typing import Any


def lookup_semantic_cache(query: str, *, threshold: float = 0.95) -> dict[str, Any] | None:
    """查询语义缓存。未实现时恒返回 None。"""
    _ = (query, threshold)
    return None


def store_semantic_cache(query: str, payload: dict[str, Any]) -> None:
    """写入语义缓存。未实现时为空操作。"""
    _ = (query, payload)
