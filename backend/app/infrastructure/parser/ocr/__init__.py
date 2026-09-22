"""RapidOCR 封装：可选依赖，未安装时优雅降级。"""
from __future__ import annotations

import threading
from functools import lru_cache
from typing import Any

_OCR_LOCK = threading.Lock()


@lru_cache(maxsize=1)
def _engine() -> Any | None:
    try:
        from rapidocr_onnxruntime import RapidOCR

        return RapidOCR()
    except Exception:
        return None


def ocr_image_bytes(image_bytes: bytes) -> str:
    """对图片 bytes 做 OCR，返回纯文本；失败或未安装返回空串。"""
    if not image_bytes:
        return ""
    engine = _engine()
    if engine is None:
        return ""
    try:
        with _OCR_LOCK:
            result, _ = engine(image_bytes)
    except Exception:
        return ""
    if not result:
        return ""
    lines: list[str] = []
    for item in result:
        # RapidOCR: [box, text, score]
        if isinstance(item, (list, tuple)) and len(item) >= 2:
            text = item[1]
            if text and str(text).strip():
                lines.append(str(text).strip())
    return "\n".join(lines).strip()


def ocr_available() -> bool:
    return _engine() is not None
