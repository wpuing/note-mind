"""DashScope Embedding（OpenAI 兼容接口）。"""
from __future__ import annotations

from openai import OpenAI

from app.core.config import settings
from app.infrastructure.ai.openai_client import openai_compatible_client


def _client() -> OpenAI:
    key = settings.embedding_api_key
    if not key:
        raise RuntimeError("Missing DASHSCOPE_EMBEDDING_API_KEY / DASHSCOPE_API_KEY")
    return openai_compatible_client(api_key=key, base_url=settings.dashscope_compatible_base_url)


def embed_texts(texts: list[str], model: str | None = None) -> list[list[float]]:
    if not texts:
        return []
    client = _client()
    model_name = model or settings.default_embedding_model
    # 百炼 text-embedding-v4：单次 contents 不得超过 10
    batch_size = 10
    vectors: list[list[float]] = []
    for i in range(0, len(texts), batch_size):
        batch = texts[i : i + batch_size]
        resp = client.embeddings.create(
            model=model_name,
            input=batch,
            dimensions=settings.embedding_dimension,
        )
        # 按 index 排序保证顺序
        ordered = sorted(resp.data, key=lambda x: x.index)
        vectors.extend([item.embedding for item in ordered])
    return vectors


def embed_query(text: str, model: str | None = None) -> list[float]:
    vecs = embed_texts([text], model=model)
    return vecs[0]
