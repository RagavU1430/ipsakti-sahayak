"""Phase 13 — candidate-K benchmark (K=8/12/16/24).

Measures retrieval/rerank/total latency + candidate/evidence counts on the frozen
Phase-16 question set using the in-process pipeline (no network LLM required for
retrieval timing; generation uses the configured generator which may be extractive).

Usage:
    python scripts/benchmark_candidate_k.py [--k 8 12 16 24] [--limit 25]
"""
from __future__ import annotations

import argparse
import json
import statistics
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def _load_questions(limit: int) -> list[dict]:
    cases = json.loads((ROOT / "dataset" / "evaluation" / "phase16_rag_questions.json").read_text(encoding="utf-8"))
    return cases[:limit]


def run_k(candidate_k: int, limit: int) -> dict:
    from app.core.config import get_settings
    from app.service import RAGService
    from app.models import QueryRequest

    settings = get_settings()
    # Override candidate_k without mutating global settings object semantics.
    object.__setattr__(settings, "candidate_k", candidate_k) if hasattr(settings, "__setattr__") else None
    try:
        settings = settings.__class__(**{**settings.__dict__, "candidate_k": candidate_k})
    except Exception:
        pass
    service = RAGService(settings=settings)
    retrieval, rerank, total, cands, evs = [], [], [], [], []
    for case in _load_questions(limit):
        req = QueryRequest(query=case["question"], jurisdiction=case.get("jurisdiction", "INDIA"), top_k=8)
        resp = service.query(req)
        m = resp.metrics
        retrieval.append(float(m.get("retrieval_ms", 0.0)))
        rerank.append(float(m.get("reranking_ms", 0.0)))
        total.append(float(m.get("total_ms", 0.0)))
        cands.append(int(m.get("candidate_count", 0)))
        evs.append(int(m.get("evidence_count", 0)))
    def _avg(values: list[float]) -> float:
        return round(statistics.mean(values), 2) if values else 0.0
    return {
        "candidate_k": candidate_k,
        "n": len(retrieval),
        "avg_retrieval_ms": _avg(retrieval),
        "avg_rerank_ms": _avg(rerank),
        "avg_total_ms": _avg(total),
        "avg_candidates": _avg(cands),
        "avg_evidence": _avg(evs),
        "corpus": getattr(service.store, "corpus_source", "unknown"),
        "embedding_provider": settings.embedding_provider,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--k", nargs="+", type=int, default=[8, 12, 16, 24])
    parser.add_argument("--limit", type=int, default=25)
    args = parser.parse_args()
    rows = [run_k(k, args.limit) for k in args.k]
    print(json.dumps(rows, indent=2))
    out = ROOT / "dataset" / "evaluation" / "results" / "candidate_k_benchmark.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({"results": rows}, indent=2), encoding="utf-8")
    print(f"wrote {out}")


if __name__ == "__main__":
    main()
