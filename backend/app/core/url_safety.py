"""Outbound URL safety checks to reduce SSRF risk for model base_url."""
from __future__ import annotations

import ipaddress
import os
import socket
from urllib.parse import urlparse

_DEFAULT_HOST_SUFFIXES = (".aliyuncs.com",)
_DEFAULT_HOSTS = frozenset({"dashscope.aliyuncs.com", "dashscope-intl.aliyuncs.com"})
_DEFAULT_AI_TOKEN = "local-dev-ai-engine-token"


def _allow_weak() -> bool:
    return os.getenv("NOTEMIND_ALLOW_WEAK_DEFAULTS", "").strip().lower() in {
        "1",
        "true",
        "yes",
        "on",
    }


def require_safe_http_url(raw: str | None, *, allow_local_http: bool | None = None) -> str:
    if raw is None or not str(raw).strip():
        raise ValueError("URL 不能为空")
    url = str(raw).strip().rstrip("/")
    parsed = urlparse(url)
    scheme = (parsed.scheme or "").lower()
    host = (parsed.hostname or "").lower()
    if not host:
        raise ValueError("URL 缺少主机名")

    if allow_local_http is None:
        allow_local_http = _allow_weak()

    local = host in {"127.0.0.1", "localhost", "::1"}
    if scheme == "https":
        pass
    elif scheme == "http" and allow_local_http and local:
        pass
    else:
        raise ValueError("仅允许 https 出站（本地调试可 http://127.0.0.1）")

    if local:
        if not allow_local_http:
            raise ValueError("禁止访问本机地址")
        return url

    allowlist_only = True  # 始终白名单；弱默认仅放宽本机 http
    allowlisted = host in _DEFAULT_HOSTS or any(host.endswith(sfx) for sfx in _DEFAULT_HOST_SUFFIXES)
    if allowlist_only and not allowlisted:
        raise ValueError("出站主机不在白名单（默认仅 *.aliyuncs.com）")

    _assert_public_host(host)
    return url


def _assert_public_host(host: str) -> None:
    try:
        infos = socket.getaddrinfo(host, None)
    except socket.gaierror as exc:
        raise ValueError(f"无法解析主机: {host}") from exc
    for info in infos:
        ip_str = info[4][0]
        try:
            ip = ipaddress.ip_address(ip_str)
        except ValueError:
            continue
        if _is_blocked_ip(ip):
            raise ValueError(f"禁止访问内网/保留地址: {host}")


def _is_blocked_ip(ip: ipaddress.IPv4Address | ipaddress.IPv6Address) -> bool:
    if (
        ip.is_private
        or ip.is_loopback
        or ip.is_link_local
        or ip.is_multicast
        or ip.is_reserved
        or ip.is_unspecified
    ):
        return True
    # CGNAT 100.64.0.0/10
    if isinstance(ip, ipaddress.IPv4Address) and ip in ipaddress.ip_network("100.64.0.0/10"):
        return True
    # Unique local fc00::/7
    if isinstance(ip, ipaddress.IPv6Address) and ip in ipaddress.ip_network("fc00::/7"):
        return True
    return False


def assert_ai_engine_token_configured(token: str | None) -> None:
    """启动期：禁止空/默认 AI_ENGINE_TOKEN（除非显式弱默认）。"""
    expected = (token or "").strip()
    if not expected:
        raise RuntimeError("AI_ENGINE_TOKEN is not configured")
    if expected == _DEFAULT_AI_TOKEN and not _allow_weak():
        raise RuntimeError(
            "AI_ENGINE_TOKEN 为内置默认值；请修改或设置 NOTEMIND_ALLOW_WEAK_DEFAULTS=true（仅本地）"
        )
