"""NoteMind AI Engine — 供 Java platform 内网调用。"""
from contextlib import asynccontextmanager
import os

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.core.config import settings
from app.core.url_safety import assert_ai_engine_token_configured
from app.infrastructure.vector.pgvector.pool import close_pool, get_pool
from app.interfaces.agent.router import router as agent_router
from app.interfaces.chat.router import router as chat_router
from app.interfaces.chunk.router import router as chunk_router
from app.interfaces.ingest.router import router as ingest_router
from app.interfaces.knowledge.router import router as knowledge_router
from app.interfaces.llm.router import router as llm_router
from app.interfaces.parse.router import router as parse_router
from app.interfaces.retrieval.router import router as retrieval_router


def _allow_weak() -> bool:
    return os.getenv("NOTEMIND_ALLOW_WEAK_DEFAULTS", "").strip().lower() in {
        "1",
        "true",
        "yes",
        "on",
    }


@asynccontextmanager
async def lifespan(_app: FastAPI):
    assert_ai_engine_token_configured(settings.ai_engine_token)
    get_pool()
    yield
    close_pool()


_docs = "/docs" if _allow_weak() else None
_redoc = "/redoc" if _allow_weak() else None
_openapi = "/openapi.json" if _allow_weak() else None

app = FastAPI(
    title="NoteMind AI Engine",
    version="1.0.0",
    description="LangChain + LangGraph；由 platform(Java) 通过 AiEngineClient 调用",
    lifespan=lifespan,
    docs_url=_docs,
    redoc_url=_redoc,
    openapi_url=_openapi,
)

app.add_middleware(
    CORSMiddleware,
    # AI 引擎仅供 Java 内网调用，不对浏览器开放 CORS
    allow_origins=[],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

ai_prefix = f"{settings.api_prefix}/ai"
app.include_router(ingest_router, prefix=ai_prefix)
app.include_router(chunk_router, prefix=ai_prefix)
app.include_router(parse_router, prefix=ai_prefix)
app.include_router(knowledge_router, prefix=ai_prefix)
app.include_router(retrieval_router, prefix=ai_prefix)
app.include_router(chat_router, prefix=ai_prefix)
app.include_router(llm_router, prefix=ai_prefix)
app.include_router(agent_router, prefix=ai_prefix)


@app.get("/health")
async def health():
    return {"status": "ok", "service": "notemind-ai-engine"}
