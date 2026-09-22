"""纯解析：按文件类型提取文本，不入库、不向量化。"""
from __future__ import annotations

import asyncio
import tempfile
from pathlib import Path

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile

from app.core.security import verify_ai_engine_token
from app.infrastructure.parser.loader import extract_text
from app.infrastructure.parser.ocr import ocr_available

router = APIRouter(prefix="/parse", tags=["parse"])

_ALLOWED = {".pdf", ".txt", ".md", ".markdown", ".docx", ".xlsx", ".pptx"}


@router.post("/extract")
async def extract_document_text(
    file: UploadFile = File(...),
    preview: bool = Form(default=True),
    _: None = Depends(verify_ai_engine_token),
):
    """preview=True：管理端预览，OCR 限页加速；preview=False：尽量抽全量。"""
    if not file.filename:
        raise HTTPException(status_code=400, detail="filename required")
    suffix = Path(file.filename).suffix.lower()
    if suffix not in _ALLOWED:
        raise HTTPException(status_code=400, detail=f"unsupported type {suffix}; allow {_ALLOWED}")

    raw = await file.read()
    if not raw:
        raise HTTPException(status_code=400, detail="empty file")

    safe_name = f"upload{suffix}"
    with tempfile.TemporaryDirectory(prefix="notemind-parse-") as tmp:
        dest = Path(tmp) / safe_name
        dest.write_bytes(raw)
        try:
            text = await asyncio.to_thread(extract_text, dest, preview=preview)
        except Exception as exc:  # noqa: BLE001
            raise HTTPException(status_code=400, detail=f"parse failed: {exc}") from exc

    content = text or ""
    hint = None
    if not content and suffix == ".pdf":
        if ocr_available():
            hint = "未提取到文字，可能是空白页或图片质量过低"
        else:
            hint = "未提取到文字（疑似扫描件）。请安装 rapidocr-onnxruntime 后重启 AI 引擎"
    elif preview and suffix == ".pdf" and content and len(content) >= 800:
        hint = "预览模式已截取部分内容（大文件/扫描件加速）；切分入库会再完整处理"
    return {
        "filename": file.filename,
        "file_type": suffix.lstrip(".").upper(),
        "char_count": len(content),
        "text": content,
        "ocr_available": ocr_available(),
        "preview": preview,
        "hint": hint,
        "status": "ok",
    }
