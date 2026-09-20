"""Phase 3 — V2 SourceRecord adapter (V1 shell + V2 corpus).

V2 SourceRecord
    -> normalize_v2_chunk()
    -> V1 retrieval/evidence interface (dicts validated by app.models.Evidence)

Does not duplicate the schema: V1 Evidence remains canonical.
Handles V2 builder quirks: ordinal/page_start/page_end as strings, 'None' literals,
missing legal-structure fields (section/rule/article/...), missing provenance fields.
"""
from __future__ import annotations

from typing import Any

_V1_OPTIONAL_TEXT_FIELDS = (
    "chapter", "section", "subsection", "rule_number", "sub_rule",
    "regulation_number", "article_number", "paragraph_number", "clause",
)

_V2_TO_V1_DOMAIN = {
    # V2 canonical already uses V1 domain tags; keep mapping for raw V2-DOC builder output.
    "UNKNOWN": "INTERNATIONAL",
}


def _to_int_or_none(value: Any) -> int | None:
    if value is None:
        return None
    if isinstance(value, int):
        return value
    text = str(value).strip()
    if text.lower() in {"", "none", "null", "nan"}:
        return None
    try:
        return int(float(text))
    except (TypeError, ValueError):
        return None


def _to_ordinal(value: Any, default: int = 0) -> int:
    parsed = _to_int_or_none(value)
    return parsed if parsed is not None else default


def normalize_v2_chunk(raw: dict[str, Any]) -> dict[str, Any]:
    """Normalize one V2 chunk dict into a V1-compatible chunk dict."""
    chunk: dict[str, Any] = dict(raw)
    chunk["chunk_id"] = str(chunk.get("chunk_id", ""))
    chunk["document_id"] = str(chunk.get("document_id", ""))
    chunk["text"] = str(chunk.get("text", ""))
    chunk["title"] = str(chunk.get("title") or chunk.get("document_id") or "")
    chunk["authority"] = str(chunk.get("authority") or "Unknown authority")
    domain = str(chunk.get("domain") or "INTERNATIONAL")
    chunk["domain"] = _V2_TO_V1_DOMAIN.get(domain, domain)
    chunk["jurisdiction"] = str(chunk.get("jurisdiction") or "INDIA")
    chunk["document_type"] = str(chunk.get("document_type") or "ACT")
    chunk["source_url"] = str(chunk.get("source_url") or "")
    chunk["document_version"] = str(chunk.get("document_version") or f"{chunk['document_id']}:v2")
    chunk["language"] = str(chunk.get("language") or "en")
    chunk["source_status"] = str(chunk.get("source_status") or "UNVERIFIED")
    chunk["ordinal"] = _to_ordinal(chunk.get("ordinal"), 0)
    chunk["page_start"] = _to_int_or_none(chunk.get("page_start"))
    chunk["page_end"] = _to_int_or_none(chunk.get("page_end"))
    text_uncertain = chunk.get("text_uncertain", False)
    if isinstance(text_uncertain, str):
        chunk["text_uncertain"] = text_uncertain.strip().lower() in {"1", "true", "yes"}
    else:
        chunk["text_uncertain"] = bool(text_uncertain)
    chunk.setdefault("structure_type", chunk.get("structure_type") or "SECTION")
    chunk.setdefault("structure_anchor", chunk.get("structure_anchor") or True)
    for field in _V1_OPTIONAL_TEXT_FIELDS:
        if chunk.get(field) is None:
            chunk[field] = None
        elif not isinstance(chunk[field], str):
            chunk[field] = str(chunk[field])
    # Provenance: V2 markdown conversions carry dataset_markdown path in source_url;
    # preserve it and record corpus origin for metrics/tracing.
    chunk.setdefault("provenance", chunk.get("source_url") or "RAG-V2-canonical")
    chunk.setdefault("corpus", "v2-canonical")
    return chunk


def normalize_v2_chunks(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return [normalize_v2_chunk(row) for row in rows]
