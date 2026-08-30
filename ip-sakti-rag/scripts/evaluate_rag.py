from __future__ import annotations

import argparse
import json
import statistics
import sys
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.citations import validate_citations
from app.models import Jurisdiction, QueryRequest
from app.service import RAGService


def read_jsonl(path: Path) -> list[dict]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def normalize(row: dict) -> dict:
    row = dict(row)
    row.setdefault("expected_abstain", row["question_id"] in {"EVAL-017", "EVAL-018"})
    if row["expected_abstain"]:
        row["expected_source_ids"] = []
    return row


def evaluate(rows: list[dict], service: RAGService, suite: str) -> tuple[list[dict], dict]:
    results: list[dict] = []
    for row in rows:
        expected = set(row.get("expected_source_ids", []))
        response = service.query(QueryRequest(query=row["question"], jurisdiction=Jurisdiction(row["jurisdiction"])))
        retrieved = [item.document_id for item in response.evidence]
        retrieved_set = set(retrieved)
        recall = 1.0 if (not expected or expected & retrieved_set) else 0.0
        precision = sum(document_id in expected for document_id in retrieved) / len(retrieved) if retrieved and expected else float(not retrieved and not expected)
        reciprocal_rank = next((1.0 / rank for rank, value in enumerate(retrieved, 1) if value in expected), 0.0) if expected else float(response.abstained)
        if response.abstained and not response.citations:
            citation_valid, citation_errors = True, []
        else:
            citation_valid, citation_errors = validate_citations(response.answer, response.citations, response.evidence)
        cited_ids = {citation.chunk_id for citation in response.citations}
        evidence_ids = {item.chunk_id for item in response.evidence}
        citation_accuracy = 1.0 if cited_ids <= evidence_ids and citation_valid else 0.0
        completeness = 1.0 if response.abstained or bool(response.citations) else 0.0
        abstention_correct = response.abstained == bool(row["expected_abstain"])
        result = {
            "suite": suite, "question_id": row["question_id"], "category": row["category"],
            "question": row["question"], "expected_source_ids": sorted(expected),
            "expected_abstain": row["expected_abstain"], "abstained": response.abstained,
            "answer": response.answer, "confidence": response.confidence.value,
            "retrieved_document_ids": retrieved, "retrieved_chunks": [
                {"chunk_id": item.chunk_id, "document_id": item.document_id,
                 "vector_score": item.vector_score, "lexical_score": item.lexical_score,
                 "fusion_score": item.fusion_score, "reranker_score": item.reranker_score}
                for item in response.evidence
            ],
            "citations": [citation.model_dump(mode="json") for citation in response.citations],
            "recall_at_k": recall, "precision_at_k": precision, "reciprocal_rank": reciprocal_rank,
            "citation_accuracy": citation_accuracy, "citation_completeness": completeness,
            "groundedness": float(citation_valid), "citation_errors": citation_errors,
            "abstention_correct": abstention_correct, "metrics": response.metrics,
        }
        results.append(result)
    summary = {
        "suite": suite, "query_count": len(results),
        "recall_at_k": statistics.fmean(row["recall_at_k"] for row in results),
        "precision_at_k": statistics.fmean(row["precision_at_k"] for row in results),
        "mrr": statistics.fmean(row["reciprocal_rank"] for row in results),
        "citation_accuracy": statistics.fmean(row["citation_accuracy"] for row in results),
        "citation_completeness": statistics.fmean(row["citation_completeness"] for row in results),
        "groundedness": statistics.fmean(row["groundedness"] for row in results),
        "abstention_accuracy": statistics.fmean(float(row["abstention_correct"]) for row in results),
        "abstained_count": sum(row["abstained"] for row in results),
        "confidence_distribution": dict(Counter(row["confidence"] for row in results)),
        "median_total_ms": statistics.median(row["metrics"].get("total_ms", 0) for row in results),
    }
    return results, summary


def main() -> int:
    parser = argparse.ArgumentParser(description="Execute the local, deterministic RAG evaluation suites.")
    parser.add_argument("--output-dir", type=Path, default=ROOT / "dataset/evaluation/results")
    args = parser.parse_args()
    base = [normalize(row) for row in read_jsonl(ROOT / "dataset/evaluation/questions.jsonl")]
    supplemental = [normalize(row) for row in read_jsonl(ROOT / "dataset/evaluation/supplemental_queries.jsonl")]
    adversarial = [normalize(row) for row in read_jsonl(ROOT / "dataset/evaluation/adversarial_queries.jsonl")]
    if len(base) + len(supplemental) < 65 or len(adversarial) < 30:
        raise RuntimeError("evaluation gate requires at least 65 end-to-end and 30 adversarial queries")
    service = RAGService()
    e2e_results, e2e_summary = evaluate(base + supplemental, service, "end_to_end")
    adversarial_results, adversarial_summary = evaluate(adversarial, service, "adversarial")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    all_results = e2e_results + adversarial_results
    (args.output_dir / "latest.jsonl").write_text("".join(json.dumps(row, ensure_ascii=False) + "\n" for row in all_results), encoding="utf-8")
    summary = {
        "generated_at": datetime.now(timezone.utc).isoformat(), "execution_mode": "local",
        "embedding_mode": "local TF-IDF retrieval (not production embeddings)",
        "reranker": service.reranker.name, "reranker_learned": service.reranker.learned,
        "generator": "deterministic extractive", "end_to_end": e2e_summary,
        "adversarial": adversarial_summary,
        "gates": {
            "minimum_query_counts": len(e2e_results) >= 65 and len(adversarial_results) >= 30,
            "citation_integrity": e2e_summary["citation_accuracy"] == 1.0 and adversarial_summary["citation_accuracy"] == 1.0,
            "groundedness": e2e_summary["groundedness"] == 1.0 and adversarial_summary["groundedness"] == 1.0,
            "production_backend_verified": False,
            "authoritative_raw_corpus_complete": False,
        },
    }
    summary["release_ready"] = all(summary["gates"].values())
    (args.output_dir / "summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(json.dumps(summary, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
