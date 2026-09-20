"""Curated verified official-PDF overrides for citations.

Each entry was verified by downloading the official PDF and locating the provision text:
- IND-PAT-ACT-1970: IP India e-version PDF (109 pages, text-extractable), verified 2026-09-21.
  Section 3(p) ("traditional knowledge or which is an aggregation...") is on PDF page 9.

Applied to Evidence BEFORE citations_for/validate_citations so validation still passes
(Citation.source_url must equal Evidence.source_url). Falls back silently when unmatched:
stored source_url/page are kept, never invented.
"""
from __future__ import annotations

from app.models import Evidence

# document_id -> {"pdf_url": ..., "pages": {(section, clause): pdf_page}}
VERIFIED_PDFS: dict[str, dict] = {
    "IND-PAT-ACT-1970": {
        "pdf_url": "https://ipindia.gov.in/storage/uploads/docs-operator/df4efbcf-6fdf-4b2b-b6d6-56853aa39083.pdf",
        "pages": {
            ("3", "p"): 9,
        },
    },
}


def apply_verified_sources(evidence: list[Evidence]) -> list[Evidence]:
    """Override source_url/page with verified PDF values where a provision matches."""
    for item in evidence:
        entry = VERIFIED_PDFS.get(item.document_id)
        if not entry:
            continue
        key = ((item.section or "").strip(), (item.clause or "").strip().lower())
        page = entry["pages"].get(key)
        if page is None:
            continue
        item.source_url = entry["pdf_url"]
        item.page_start = page
        item.page_end = page
    return evidence
