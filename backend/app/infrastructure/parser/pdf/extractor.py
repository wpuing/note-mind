"""PDF 文本提取：PyMuPDF → pdfplumber → RapidOCR（扫描件）。"""
from __future__ import annotations

from pathlib import Path

import pymupdf

from app.infrastructure.parser.ocr import ocr_available, ocr_image_bytes

# 文本过短时视为「几乎无文本」，触发 OCR
_MIN_TEXT_CHARS = 20
# 预览：少页、低 DPI、凑够字数即停（避免大扫描件超时）
_PREVIEW_OCR_PAGES = 5
_PREVIEW_OCR_DPI = 110
_PREVIEW_OCR_STOP_CHARS = 800
# 全量：仍限制上限，防止极端文件拖垮服务
_FULL_OCR_PAGES = 20
_FULL_OCR_DPI = 140
_FULL_OCR_STOP_CHARS = 50_000


def extract_pdf_text(path: str | Path, *, preview: bool = True) -> str:
    p = Path(path)
    # 预览只扫前若干页，避免超大 PDF 抽全文拖垮请求
    text = _extract_pymupdf(p, max_pages=40 if preview else None)
    if len(text) >= _MIN_TEXT_CHARS:
        return text

    # pdfplumber 对大文件较慢；仅在文本几乎为空时尝试
    plumber = _extract_pdfplumber(p, max_pages=8 if preview else None)
    if len(plumber) > len(text):
        text = plumber
    if len(text) >= _MIN_TEXT_CHARS:
        return text

    if ocr_available():
        ocr_text = _extract_ocr(p, preview=preview)
        if len(ocr_text) > len(text):
            return ocr_text
    return text


def _extract_pymupdf(path: Path, max_pages: int | None = None) -> str:
    doc = pymupdf.open(str(path))
    try:
        parts: list[str] = []
        limit = len(doc) if max_pages is None else min(len(doc), max(1, max_pages))
        for i in range(limit):
            page = doc[i]
            chunk = (page.get_text("text") or "").strip()
            if not chunk:
                blocks = page.get_text("blocks") or []
                if isinstance(blocks, list):
                    lines = []
                    for b in blocks:
                        if isinstance(b, (list, tuple)) and len(b) >= 5 and isinstance(b[4], str):
                            t = b[4].strip()
                            if t:
                                lines.append(t)
                    chunk = "\n".join(lines)
                else:
                    chunk = str(blocks or "").strip()
            if chunk:
                parts.append(chunk)
        return "\n".join(parts).strip()
    finally:
        doc.close()


def _extract_pdfplumber(path: Path, max_pages: int | None = None) -> str:
    try:
        import pdfplumber
    except Exception:
        return ""
    parts: list[str] = []
    try:
        with pdfplumber.open(str(path)) as pdf:
            pages = pdf.pages
            if max_pages is not None:
                pages = pages[: max(1, max_pages)]
            for page in pages:
                t = (page.extract_text() or "").strip()
                if t:
                    parts.append(t)
    except Exception:
        return ""
    return "\n".join(parts).strip()


def _extract_ocr(path: Path, *, preview: bool) -> str:
    doc = pymupdf.open(str(path))
    try:
        max_pages = _PREVIEW_OCR_PAGES if preview else _FULL_OCR_PAGES
        dpi = _PREVIEW_OCR_DPI if preview else _FULL_OCR_DPI
        stop_chars = _PREVIEW_OCR_STOP_CHARS if preview else _FULL_OCR_STOP_CHARS
        page_count = min(len(doc), max_pages)
        zoom = dpi / 72.0
        mat = pymupdf.Matrix(zoom, zoom)
        parts: list[str] = []
        total = 0
        for i in range(page_count):
            page = doc[i]
            pix = page.get_pixmap(matrix=mat, alpha=False)
            png = pix.tobytes("png")
            text = ocr_image_bytes(png)
            if text:
                parts.append(text)
                total += len(text)
                if total >= stop_chars:
                    break
        return "\n".join(parts).strip()
    finally:
        doc.close()
