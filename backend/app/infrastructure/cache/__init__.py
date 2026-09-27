"""缓存基础设施：会话辅助缓存；语义缓存见 semantic_cache（Phase B）。"""

from app.infrastructure.cache.semantic_cache import lookup_semantic_cache, store_semantic_cache

__all__ = ["lookup_semantic_cache", "store_semantic_cache"]
