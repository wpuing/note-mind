from pathlib import Path

from pydantic import AliasChoices, Field
from pydantic_settings import BaseSettings, SettingsConfigDict

# 仓库根目录 .env（backend/app/core/config.py → 上溯 4 层到 NoteMind/）
_ROOT_ENV = Path(__file__).resolve().parents[3] / ".env"
_BACKEND_ROOT = Path(__file__).resolve().parents[2]


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=str(_ROOT_ENV) if _ROOT_ENV.exists() else ".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    app_name: str = "NoteMind-AI-Engine"
    api_prefix: str = "/api/v1"
    # 内网调用校验（与 Java AI_ENGINE_TOKEN 对齐）；空字符串则跳过校验
    ai_engine_token: str = "local-dev-ai-engine-token"

    mysql_host: str = "127.0.0.1"
    mysql_port: int = 3306
    mysql_user: str = Field(
        default="root",
        validation_alias=AliasChoices("MYSQL_USER", "DB_USERNAME"),
    )
    mysql_password: str = Field(
        default="changeme",
        validation_alias=AliasChoices("MYSQL_PASSWORD", "DB_PASSWORD"),
    )
    mysql_database: str = "notemind"

    pg_host: str = "127.0.0.1"
    pg_port: int = 5432
    pg_user: str = Field(
        default="postgres",
        validation_alias=AliasChoices("PG_USER", "PG_USERNAME"),
    )
    pg_password: str = "changeme"
    pg_database: str = "notemind_vector"

    default_chat_model: str = "qwen-plus"
    default_embedding_model: str = "text-embedding-v4"
    default_rerank_model: str = "gte-rerank-v2"
    embedding_dimension: int = 1024

    dashscope_api_key: str = ""
    dashscope_chat_api_key: str = ""
    dashscope_embedding_api_key: str = ""
    dashscope_rerank_api_key: str = ""

    dashscope_compatible_base_url: str = (
        "https://dashscope.aliyuncs.com/compatible-mode/v1"
    )
    dashscope_rerank_url: str = (
        "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank"
    )

    chunk_size: int = 500
    chunk_overlap: int = 50
    default_knowledge_base_id: str = "kb_default"
    default_top_k: int = 5
    max_top_k: int = 50
    default_chat_temperature: float = 0.3
    judge_temperature: float = 0.0
    # 逗号分隔；空则回落到 defaults.cors_origins() 内置本地列表
    cors_origins: str = (
        "http://127.0.0.1:8080,http://localhost:8080,"
        "http://127.0.0.1:8081,http://localhost:8081,"
        "http://127.0.0.1:5174,http://localhost:5174,"
        "http://127.0.0.1:5173,http://localhost:5173"
    )
    upload_dir: Path = _BACKEND_ROOT / "uploads" / "knowledge"
    meta_dir: Path = _BACKEND_ROOT / "uploads" / "meta"
    samples_dir: Path = _BACKEND_ROOT / "uploads" / "samples"

    @property
    def chat_api_key(self) -> str:
        return self.dashscope_chat_api_key or self.dashscope_api_key

    @property
    def embedding_api_key(self) -> str:
        return self.dashscope_embedding_api_key or self.dashscope_api_key

    @property
    def rerank_api_key(self) -> str:
        return self.dashscope_rerank_api_key or self.dashscope_api_key


settings = Settings()
settings.upload_dir.mkdir(parents=True, exist_ok=True)
settings.meta_dir.mkdir(parents=True, exist_ok=True)
settings.samples_dir.mkdir(parents=True, exist_ok=True)
