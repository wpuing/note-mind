"""LangChain DocumentCompressor 封装 DashScope 原生重排（约定：原生 → Compressor）。"""
from __future__ import annotations

import logging
from typing import Any, Sequence

from app.infrastructure.rerank.dashscope_rerank import rerank_documents

logger = logging.getLogger(__name__)

try:
    from langchain_core.callbacks import Callbacks
    from langchain_core.documents import Document
    from langchain_core.documents.compressor import BaseDocumentCompressor
    from pydantic import ConfigDict

    class DashScopeRerankCompressor(BaseDocumentCompressor):
        """将 DashScope text-rerank 结果映射为 LangChain Compressor。"""

        model_config = ConfigDict(arbitrary_types_allowed=True)
        top_n: int = 4
        model: str | None = None

        def compress_documents(
            self,
            documents: Sequence[Document],
            query: str,
            callbacks: Callbacks | None = None,
        ) -> Sequence[Document]:
            if not documents:
                return []
            texts = [d.page_content for d in documents]
            ranked = rerank_documents(
                query,
                texts,
                top_n=min(self.top_n, len(texts)),
                model=self.model,
            )
            if ranked is None:
                return list(documents)[: self.top_n]
            out: list[Document] = []
            for item in ranked:
                idx = int(item.get("index", -1))
                if idx < 0 or idx >= len(documents):
                    continue
                doc = documents[idx]
                meta = dict(doc.metadata or {})
                meta["relevance_score"] = float(item.get("relevance_score") or 0.0)
                out.append(Document(page_content=doc.page_content, metadata=meta))
            return out

    _HAS_COMPRESSOR = True
except Exception:  # noqa: BLE001
    DashScopeRerankCompressor = None  # type: ignore[misc, assignment]
    _HAS_COMPRESSOR = False
    Document = None  # type: ignore[assignment]


def compress_rerank(
    query: str,
    documents: list[str],
    *,
    top_n: int | None = None,
    model: str | None = None,
) -> list[dict[str, Any]] | None:
    """
    Compressor 路径入口；返回与 rerank_documents 相同结构。
    失败返回 None，调用方回退原生。
    """
    if not documents:
        return []
    if not _HAS_COMPRESSOR or DashScopeRerankCompressor is None or Document is None:
        return None
    try:
        compressor = DashScopeRerankCompressor(
            top_n=top_n or len(documents),
            model=model,
        )
        docs = [Document(page_content=t, metadata={"index": i}) for i, t in enumerate(documents)]
        compressed = compressor.compress_documents(docs, query)
        out: list[dict[str, Any]] = []
        for d in compressed:
            idx = int((d.metadata or {}).get("index", -1))
            score = float((d.metadata or {}).get("relevance_score") or 0.0)
            if idx < 0:
                try:
                    idx = documents.index(d.page_content)
                except ValueError:
                    continue
            out.append({"index": idx, "relevance_score": score})
        return out
    except Exception as exc:  # noqa: BLE001
        logger.warning("LangChain Compressor rerank skipped: %s", type(exc).__name__)
        return None
