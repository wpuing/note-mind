"""NoteMind AI Engine — 供 Java platform 内网调用。"""
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.core.config import settings
from app.core.defaults import cors_origins
from app.infrastructure.vector.pgvector.pool import close_pool, get_pool
from app.interfaces.agent.router import router as agent_router
from app.interfaces.chat.router import router as chat_router
from app.interfaces.chunk.router import router as chunk_router
from app.interfaces.ingest.router import router as ingest_router
from app.interfaces.knowledge.router import router as knowledge_router
from app.interfaces.llm.router import router as llm_router
from app.interfaces.parse.router import router as parse_router
from app.interfaces.retrieval.router import router as retrieval_router


@asynccontextmanager
async def lifespan(_app: FastAPI):
    get_pool()
    yield
    close_pool()


app = FastAPI(
    title="NoteMind AI Engine",
    version="1.0.0",
    description="LangChain + LangGraph；由 platform(Java) 通过 AiEngineClient 调用",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=cors_origins(),
    allow_credentials=True,
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
