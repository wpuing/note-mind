"""Shared OpenAI-compatible client with SSRF-safe URL + no redirects."""
from __future__ import annotations

import httpx
from openai import OpenAI

from app.core.config import settings
from app.core.url_safety import require_safe_http_url

# 禁止 302 跳到内网；与 Java HttpClient.Redirect.NEVER 对齐
_http = httpx.Client(follow_redirects=False, timeout=httpx.Timeout(60.0, connect=10.0))


def openai_compatible_client(
    *,
    api_key: str,
    base_url: str | None = None,
) -> OpenAI:
    raw = (base_url or "").strip() or settings.dashscope_compatible_base_url
    url = require_safe_http_url(raw)
    return OpenAI(api_key=api_key, base_url=url, http_client=_http)
