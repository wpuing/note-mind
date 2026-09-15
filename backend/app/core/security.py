"""内网 AI Engine Token 校验。"""
import hmac

from fastapi import Header, HTTPException, status

from app.core.config import settings


async def verify_ai_engine_token(
    x_ai_engine_token: str | None = Header(default=None, alias="X-AI-Engine-Token"),
) -> None:
    expected = (settings.ai_engine_token or "").strip()
    if not expected:
        # 空 token 视为未配置：拒绝业务调用，避免误放开
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="AI_ENGINE_TOKEN is not configured",
        )
    provided = (x_ai_engine_token or "").strip()
    if not provided or not hmac.compare_digest(provided, expected):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or missing X-AI-Engine-Token",
        )
