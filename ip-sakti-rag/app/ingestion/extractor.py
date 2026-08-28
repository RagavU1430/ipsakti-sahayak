from __future__ import annotations
import json, re
from pathlib import Path
import fitz
from bs4 import BeautifulSoup

def extract(path: Path) -> dict:
    pages = []
    if path.suffix.lower() == ".pdf":
        with fitz.open(path) as doc:
            for number, page in enumerate(doc, 1): pages.append({"page": number, "text": page.get_text("text", sort=True)})
    else:
        text = BeautifulSoup(path.read_text(encoding="utf-8", errors="replace"), "html.parser").get_text("\n", strip=True)
        pages.append({"page": 1, "text": text})
    chars = sum(len(p["text"].strip()) for p in pages)
    return {"pages": pages, "page_count": len(pages), "ocr_required": bool(pages and chars / len(pages) < 80)}

def clean_text(text: str) -> str:
    text = text.replace("\u00ad", "").replace("\r", "\n")
    text = re.sub(r"[ \t]+", " ", text)
    return re.sub(r"\n{3,}", "\n\n", text).strip()

