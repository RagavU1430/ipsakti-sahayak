from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from app.ingestion.chunker import chunk_units, legal_units
from app.ingestion.extractor import ExtractionResult, PageText, extract
from app.ingestion.registry import SourceRecord, read_registry
from app.ingestion.validator import ValidationResult, validate_dataset


ROOT = Path(__file__).resolve().parents[2]


def _jsonl(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def _write_jsonl(path: Path, records: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("".join(json.dumps(record, ensure_ascii=False) + "\n" for record in records), encoding="utf-8")


def _legacy_pages(source_id: str, legacy_chunks: list[dict[str, Any]]) -> list[PageText]:
    return [
        PageText(None, chunk["text"], uncertain=True)
        for chunk in legacy_chunks
        if chunk.get("document_id") == source_id and chunk.get("text", "").strip()
    ]


def _document(record: SourceRecord, legacy: dict[str, Any] | None, extraction: ExtractionResult | None, status: str, errors: list[str]) -> dict[str, Any]:
    digest = extraction.sha256 if extraction else record.sha256
    version = f"{record.source_id}:{digest[:12]}" if digest else f"{record.source_id}:unverified"
    return {
        "document_id": record.source_id,
        "document_version": version,
        "title": record.title,
        "authority": record.authority,
        "domain": record.domain,
        "subdomain": record.subdomain,
        "jurisdiction": record.jurisdiction,
        "document_type": record.document_type,
        "publication_date": record.publication_date,
        "effective_date": record.effective_date,
        "version": record.version,
        "source_url": record.source_url,
        "download_url": record.download_url or None,
        "checksum": digest or None,
        "page_count": extraction.page_count if extraction else (legacy or {}).get("page_count"),
        "language": record.language,
        "retrieved_at": record.retrieved_at,
        "ingestion_status": status,
        "included_in_retrieval": not status.startswith("QUARANTINED"),
        "validation_errors": errors,
        "ocr": {
            "used": extraction.used_ocr,
            "ocr_pages": extraction.ocr_pages,
            "empty_pages": extraction.empty_pages,
            "characters": extraction.character_count,
            "average_characters_per_page": round(extraction.average_characters_per_page, 2),
            "coverage": round(extraction.ocr_coverage, 4),
            "warnings": extraction.warnings,
        } if extraction else None,
    }


def build_canonical_dataset(root: Path = ROOT) -> tuple[list[dict[str, Any]], list[dict[str, Any]], ValidationResult]:
    registry = read_registry(root / "dataset" / "manifests" / "source_registry.csv")
    legacy_documents = {item["document_id"]: item for item in _jsonl(root / "dataset" / "processed" / "documents.jsonl")}
    legacy_chunks = _jsonl(root / "dataset" / "processed" / "chunks.jsonl")
    documents: list[dict[str, Any]] = []
    chunks: list[dict[str, Any]] = []

    for record in registry:
        if not record.content_available or record.status in {"RESTRICTED", "DISCOVERED"}:
            continue
        raw_path = root / record.local_path
        extraction: ExtractionResult | None = None
        errors: list[str] = []
        pages: list[PageText] = []
        status = "VERIFIED"
        try:
            extraction = extract(raw_path)
            pages = extraction.pages
            if record.sha256 and extraction.sha256 != record.sha256:
                errors.append(f"checksum mismatch: expected {record.sha256}, got {extraction.sha256}")
                status = "QUARANTINED_CHECKSUM_MISMATCH"
        except ValueError as exc:
            errors.append(str(exc))
            if record.source_id == "IND-FSS-AA-2022":
                status = "QUARANTINED_INVALID_SOURCE"
            else:
                status = "LEGACY_UNVERIFIED_RAW_MISSING"
                pages = _legacy_pages(record.source_id, legacy_chunks)
                if not pages:
                    status = "QUARANTINED_NO_CONTENT"

        # Never permit the known USDA substitution to re-enter the canonical corpus.
        opening = "\n".join(page.text for page in pages[:2]).lower()
        if record.source_id == "IND-FSS-AA-2022" and ("voluntary report" in opening or "agricultural attach" in opening):
            errors.append("non-authoritative USDA report detected")
            status = "QUARANTINED_INVALID_SOURCE"
            pages = []

        document = _document(record, legacy_documents.get(record.source_id), extraction, status, errors)
        documents.append(document)
        if document["included_in_retrieval"]:
            units = legal_units(pages, record.document_type)
            chunks.extend(chunk_units(units, document))

    # Exact within-document duplicates are never useful retrieval evidence. Keep
    # the first occurrence deterministically and re-number the surviving rows.
    seen: set[tuple[str, str]] = set()
    unique_chunks: list[dict[str, Any]] = []
    ordinals: dict[str, int] = {}
    for chunk in chunks:
        key = (chunk["document_id"], " ".join(chunk["text"].lower().split()))
        if key in seen:
            continue
        seen.add(key)
        ordinals[chunk["document_id"]] = ordinals.get(chunk["document_id"], 0) + 1
        chunk["ordinal"] = ordinals[chunk["document_id"]]
        unique_chunks.append(chunk)
    chunks = unique_chunks

    validation = validate_dataset(documents, chunks)
    output = root / "dataset" / "canonical"
    _write_jsonl(output / "documents.jsonl", documents)
    _write_jsonl(output / "chunks.jsonl", chunks)
    metadata = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "document_count": len(documents),
        "retrievable_document_count": sum(document["included_in_retrieval"] for document in documents),
        "chunk_count": len(chunks),
        "validation_passed": validation.passed,
        "errors": validation.errors,
        "warnings": validation.warnings,
    }
    (output / "metadata.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8")
    return documents, chunks, validation
