from __future__ import annotations

import json
from pathlib import Path

from app.ingestion.validator import validate_dataset


ROOT = Path(__file__).resolve().parents[1]


def _rows(path: Path) -> list[dict]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line]


def test_canonical_dataset_passes_validation() -> None:
    documents = _rows(ROOT / "dataset/canonical/documents.jsonl")
    chunks = _rows(ROOT / "dataset/canonical/chunks.jsonl")
    report = validate_dataset(documents, chunks)
    assert report.passed, report.errors
    assert len(documents) == 25
    assert sum(document["included_in_retrieval"] for document in documents) == 24


def test_invalid_fssai_source_is_quarantined() -> None:
    documents = {row["document_id"]: row for row in _rows(ROOT / "dataset/canonical/documents.jsonl")}
    bad = documents["IND-FSS-AA-2022"]
    assert bad["ingestion_status"] == "QUARANTINED_INVALID_SOURCE"
    assert bad["included_in_retrieval"] is False
    assert not any(row["document_id"] == bad["document_id"] for row in _rows(ROOT / "dataset/canonical/chunks.jsonl"))


def test_verified_fssai_order_has_real_pages() -> None:
    documents = {row["document_id"]: row for row in _rows(ROOT / "dataset/canonical/documents.jsonl")}
    order = documents["IND-FSS-AA-ORDER-2025"]
    assert order["ingestion_status"] == "VERIFIED"
    assert order["page_count"] == 172
    assert order["checksum"] == "498d6f579357e7bb8e8d7b9f7741db5863f6eea8d29ffd559ff9add7d32d7edc"


def test_chunk_ids_and_text_are_unique(chunks: list[dict]) -> None:
    assert len({row["chunk_id"] for row in chunks}) == len(chunks)
    assert len({(row["document_id"], row["text"]) for row in chunks}) == len(chunks)


def test_evaluation_corpus_and_gold_are_complete() -> None:
    base = _rows(ROOT / "dataset/evaluation/questions.jsonl")
    supplemental = _rows(ROOT / "dataset/evaluation/supplemental_queries.jsonl")
    adversarial = _rows(ROOT / "dataset/evaluation/adversarial_queries.jsonl")
    gold = _rows(ROOT / "dataset/evaluation/golden_answers.jsonl")
    assert len(base) + len(supplemental) >= 65
    assert len(adversarial) >= 30
    assert {row["question_id"] for row in base} == {row["question_id"] for row in gold}
