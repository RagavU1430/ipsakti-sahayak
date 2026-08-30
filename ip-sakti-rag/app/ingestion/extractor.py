from __future__ import annotations

import hashlib
import json
import re
from dataclasses import dataclass
from pathlib import Path

import fitz
from bs4 import BeautifulSoup


@dataclass(frozen=True)
class PageText:
    page: int | None
    text: str
    uncertain: bool = False


@dataclass(frozen=True)
class ExtractionResult:
    pages: list[PageText]
    page_count: int
    sha256: str
    size_bytes: int
    used_ocr: bool
    ocr_pages: int
    empty_pages: int
    character_count: int
    average_characters_per_page: float
    ocr_coverage: float
    warnings: list[str]


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def clean_text(text: str) -> str:
    text = text.replace("\u00ad", "").replace("\r\n", "\n").replace("\r", "\n")
    text = re.sub(r"[\x00-\x08\x0b\x0c\x0e-\x1f]", "�", text)
    text = re.sub(r"[ \t]+", " ", text)
    text = re.sub(r"\n[ \t]+", "\n", text)
    return re.sub(r"\n{3,}", "\n\n", text).strip()


def looks_like_pdf(path: Path) -> bool:
    return path.exists() and path.stat().st_size > 8 and path.read_bytes()[:5].startswith(b"%PDF")


def _metrics(pages: list[PageText], used_ocr: bool, warnings: list[str], digest: str, size: int) -> ExtractionResult:
    chars = sum(len(page.text.strip()) for page in pages)
    empty = sum(not page.text.strip() for page in pages)
    total = len(pages)
    return ExtractionResult(
        pages=pages,
        page_count=total,
        sha256=digest,
        size_bytes=size,
        used_ocr=used_ocr,
        ocr_pages=total if used_ocr else 0,
        empty_pages=empty,
        character_count=chars,
        average_characters_per_page=(chars / total if total else 0.0),
        ocr_coverage=((total - empty) / total if total else 0.0),
        warnings=warnings,
    )


def extract(path: Path) -> ExtractionResult:
    if not path.exists() or path.stat().st_size == 0:
        raise ValueError(f"source file missing or empty: {path}")
    digest, size = sha256_file(path), path.stat().st_size
    warnings: list[str] = []

    if path.suffix.lower() == ".pdf":
        if not looks_like_pdf(path):
            raise ValueError(f"expected PDF but received non-PDF content: {path}")
        with fitz.open(path) as document:
            pages = [PageText(number, clean_text(page.get_text("text", sort=True))) for number, page in enumerate(document, 1)]
        average = sum(len(page.text) for page in pages) / max(len(pages), 1)
        cache_path = path.with_suffix(".ocr.json")
        if average < 80 and cache_path.exists():
            cached = json.loads(cache_path.read_text(encoding="utf-8"))
            pages = [
                PageText(int(item["page"]), clean_text(item.get("text", "")), uncertain=True)
                for item in cached
            ]
            warnings.append("OCR cache used; OCR-derived text is marked uncertain")
            if len(pages) != len(cached):
                warnings.append("OCR cache page count changed during parsing")
            return _metrics(pages, True, warnings, digest, size)
        if average < 80:
            warnings.append("low text density and no OCR cache available")
        return _metrics(pages, False, warnings, digest, size)

    raw = path.read_text(encoding="utf-8", errors="replace")
    if path.suffix.lower() in {".html", ".htm"}:
        raw = BeautifulSoup(raw, "html.parser").get_text("\n", strip=True)
    pages = [PageText(1, clean_text(raw), uncertain="\ufffd" in raw)]
    return _metrics(pages, False, warnings, digest, size)
