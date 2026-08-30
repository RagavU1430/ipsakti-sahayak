from __future__ import annotations

import re
from collections import Counter
from dataclasses import dataclass, field
from typing import Any


@dataclass
class ValidationResult:
    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)

    @property
    def passed(self) -> bool:
        return not self.errors


def _identifier_present(chunk: dict[str, Any], label: str, value: str | None) -> bool:
    if not value:
        return True
    text = chunk["text"]
    patterns = {
        "section": rf"(?:Section\s+)?{re.escape(value)}(?:\.|:|,|\s|\()",
        "rule_number": rf"(?:Rule\s+)?{re.escape(value)}(?:\.|:|,|\s|\()",
        "regulation_number": rf"(?:Regulation\s+)?{re.escape(value)}(?:\.|:|,|\s|\()",
        "article_number": rf"Article\s+{re.escape(value)}\b",
    }
    return bool(re.search(patterns[label], text, re.IGNORECASE))


def validate_dataset(documents: list[dict[str, Any]], chunks: list[dict[str, Any]]) -> ValidationResult:
    result = ValidationResult()
    doc_ids = [document.get("document_id") for document in documents]
    for missing in (value for value in doc_ids if not value):
        result.errors.append(f"document ID is missing: {missing!r}")
    duplicates = [key for key, count in Counter(doc_ids).items() if count > 1]
    if duplicates:
        result.errors.append(f"duplicate document IDs: {duplicates}")

    by_id = {document["document_id"]: document for document in documents if document.get("document_id")}
    included = {key for key, document in by_id.items() if document.get("included_in_retrieval")}
    for document in documents:
        for key in ("title", "authority", "domain", "jurisdiction", "document_type", "source_url", "document_version", "ingestion_status"):
            if not document.get(key):
                result.errors.append(f"{document.get('document_id')}: missing {key}")
        if document.get("ingestion_status", "").startswith("LEGACY"):
            result.warnings.append(f"{document['document_id']}: raw source unavailable; page citations disabled")
        if document.get("ingestion_status", "").startswith("QUARANTINED") and document.get("included_in_retrieval"):
            result.errors.append(f"{document['document_id']}: quarantined document included in retrieval")

    chunk_ids = [chunk.get("chunk_id") for chunk in chunks]
    duplicate_chunks = [key for key, count in Counter(chunk_ids).items() if count > 1]
    if duplicate_chunks:
        result.errors.append(f"duplicate chunk IDs: {duplicate_chunks[:10]}")
    normalized_text: Counter[tuple[str, str]] = Counter()
    for chunk in chunks:
        chunk_id = chunk.get("chunk_id", "<missing>")
        document_id = chunk.get("document_id")
        if document_id not in included:
            result.errors.append(f"{chunk_id}: invalid or excluded document mapping {document_id}")
        if not str(chunk.get("text", "")).strip():
            result.errors.append(f"{chunk_id}: empty text")
        normalized_text[(str(document_id), " ".join(str(chunk.get("text", "")).lower().split()))] += 1
        start, end = chunk.get("page_start"), chunk.get("page_end")
        if (start is None) != (end is None):
            result.errors.append(f"{chunk_id}: incomplete page range")
        if start is not None:
            page_count = by_id.get(document_id, {}).get("page_count")
            if start < 1 or end < start or (page_count and end > page_count):
                result.errors.append(f"{chunk_id}: impossible page mapping {start}-{end}/{page_count}")
        if chunk.get("structure_anchor"):
            active_label = {
                "SECTION": "section",
                "RULE": "rule_number",
                "REGULATION": "regulation_number",
                "ARTICLE": "article_number",
            }.get(chunk.get("structure_type"))
            if active_label and not _identifier_present(chunk, active_label, chunk.get(active_label)):
                result.errors.append(f"{chunk_id}: {active_label}={chunk.get(active_label)} not found in chunk text")
        if re.search(r"[\x00-\x08\x0b\x0c\x0e-\x1f]", str(chunk.get("text", ""))):
            result.errors.append(f"{chunk_id}: corrupted control character")
    duplicate_text = [key for key, count in normalized_text.items() if count > 1 and key[1]]
    if duplicate_text:
        result.errors.append(f"duplicate chunk text within documents: {len(duplicate_text)} groups")
    return result
