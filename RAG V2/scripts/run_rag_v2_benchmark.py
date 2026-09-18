#!/usr/bin/env python3
"""Run RAG V2 frozen 25-question benchmark.

Loads questions from phase16_rag_questions.json, runs retrieval + generation,
and persists results to rag_v2_phase2_baseline.json.
"""
from __future__ import annotations

import argparse
import json
import sys
import time
from uuid import uuid4
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "ip-sakti-rag"))

from app.retrieval.local_store import LocalCorpusStore
from app.retrieval.hybrid import HybridRetriever
from app.retrieval.query_analysis import analyze_query
from app.retrieval.reranker import LegalFeatureReranker
from app.models import QueryRequest
from app.legal_aliases import normalize_legal_query
from app.generation.grounded import ExtractiveGroundedGenerator
from app.generation.context import assemble_context
from app.guardrails.policy import abstention_reason
from app.citations.engine import citations_for, validate_citations

QUESTIONS_PATH = Path(__file__).resolve().parents[2] / "ip-sakti-rag" / "dataset" / "evaluation" / "phase16_rag_questions.json"
DEFAULT_RESULTS_PATH = Path(__file__).resolve().parents[2] / "RAG V2" / "dataset" / "evaluation" / "results" / "rag_v2_phase2_baseline.json"
CHUNKS_PATH = Path(__file__).resolve().parents[2] / "RAG V2" / "dataset" / "canonical" / "chunks_v2.jsonl"

DOMAIN_TERMS = {
    "PATENT": ("patent", "patented", "patentability", "invention", "novelty", "specification", "inventor", "traditional knowledge", "tkdl"),
    "TRADEMARK": ("trademark", "trade mark", "brand", "mark registration", "logo"),
    "GI": ("geographical indication", "gi", "origin-linked", "regional product"),
    "COPYRIGHT": ("copyright", "literary work", "artistic work"),
    "DESIGN": ("design", "designed", "industrial design", "design registration"),
    "PLANT_VARIETY": ("plant variety", "farmer rights", "ppvfr", "seed variety"),
    "ABS": ("biodiversity", "biological diversity", "benefit sharing", "nba"),
    "FOOD": ("fssai", "food safety", "ayurveda aahara", "label"),
    "AYURVEDA": ("ayurveda", "ayush", "traditional medicine"),
    "INTERNATIONAL": ("wipo", "trips", "treaty", "convention", "pct", "madrid", "budapest", "gratk", "paris"),
}
INTERNATIONAL_TERMS = set(DOMAIN_TERMS["INTERNATIONAL"])


def route_query(query: str) -> str:
    q = query.lower()
    if any(term in q for term in INTERNATIONAL_TERMS):
        return "RAG"
    if any(term in q for terms in DOMAIN_TERMS.values() for term in terms):
        return "RAG"
    return "GENERAL"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=DEFAULT_RESULTS_PATH)
    args = parser.parse_args()
    print("=== RAG V2 FROZEN 25-QUESTION BENCHMARK ===")
    print()

    # Load questions
    with open(QUESTIONS_PATH) as f:
        questions = json.load(f)
    print(f"Loaded {len(questions)} questions")

    # Load corpus
    store = LocalCorpusStore(CHUNKS_PATH)
    print(f"Loaded {len(store.chunks)} chunks")

    retriever = HybridRetriever(store, candidate_k=24)
    reranker = LegalFeatureReranker()
    generator = ExtractiveGroundedGenerator()

    results = []
    rag_used_count = 0
    chunks_retrieved_total = 0
    grounded_count = 0
    abstained_count = 0
    retrieval_latencies = []
    generation_latencies = []

    for q in questions:
        qid = q["id"]
        question = q["question"]
        expected_docs = q.get("expected_documents", [])
        expected_abstention = q.get("expected_abstention", False)
        jurisdiction = q.get("jurisdiction", "INDIA")

        normalized = normalize_legal_query(question)
        request = QueryRequest(query=normalized)

        # Analyze query
        analysis = analyze_query(request)

        # Route
        route = route_query(question)

        if route == "GENERAL":
            # Skip retrieval, use general LLM
            results.append({
                "id": qid,
                "question": question,
                "route": "GENERAL",
                "rag_used": False,
                "chunks_retrieved": 0,
                "grounded": False,
                "abstained": False,
                "retrieval_latency_ms": 0,
                "generation_latency_ms": 0,
                "total_latency_ms": 0,
            })
            continue

        # RAG route
        rag_used_count += 1

        retrieval_start = time.perf_counter()
        candidates = retriever.retrieve(analysis)
        retrieval_ms = (time.perf_counter() - retrieval_start) * 1000
        retrieval_latencies.append(retrieval_ms)

        rerank_start = time.perf_counter()
        evidence = reranker.rerank(analysis, candidates, final_count=6)
        rerank_ms = (time.perf_counter() - rerank_start) * 1000

        # Determine evidence sufficiency
        abstain_reason_val = abstention_reason(analysis, evidence) if evidence else "No evidence retrieved"
        abstained = bool(abstain_reason_val)

        if not abstained and evidence:
            # Build context and generate
            context, selected = assemble_context(evidence, 18000)
            gen_start = time.perf_counter()
            try:
                generated = generator.generate(analysis, context, selected)
                gen_ms = (time.perf_counter() - gen_start) * 1000
                generation_latencies.append(gen_ms)

                # Validate citations
                citations = citations_for(selected, generated.used_chunk_ids)
                valid, citation_errors = validate_citations(generated.answer, citations, selected)

                grounded = valid and not generated.insufficient_evidence
                chunks_retrieved = len(selected)
                if grounded:
                    grounded_count += 1
            except Exception as e:
                gen_ms = (time.perf_counter() - gen_start) * 1000
                generation_latencies.append(gen_ms)
                grounded = False
                chunks_retrieved = len(evidence)
                abstained = True
        else:
            gen_ms = 0
            generation_latencies.append(0)
            chunks_retrieved = len(evidence)
            grounded = False
            if abstained:
                abstained_count += 1

        total_ms = retrieval_ms + rerank_ms + gen_ms
        chunks_retrieved_total += chunks_retrieved

        results.append({
            "id": qid,
            "question": question,
            "route": route,
            "rag_used": True,
            "chunks_retrieved": chunks_retrieved,
            "evidence_sufficiency": "INSUFFICIENT" if abstained else ("SUFFICIENT" if grounded else "PARTIAL"),
            "grounded": grounded,
            "abstained": abstained,
            "citation_correct": valid if not abstained else False,
            "retrieval_latency_ms": round(retrieval_ms, 2),
            "reranking_latency_ms": round(rerank_ms, 2),
            "generation_latency_ms": round(gen_ms, 2),
            "total_latency_ms": round(total_ms, 2),
            "request_id": str(uuid4()),
        })

    # Summary
    rag_questions = sum(1 for r in results if r["rag_used"])
    general_questions = sum(1 for r in results if not r["rag_used"])
    avg_retrieval = sum(r["retrieval_latency_ms"] for r in results if r["rag_used"]) / max(rag_questions, 1)
    avg_generation = sum(r["generation_latency_ms"] for r in results if r["rag_used"]) / max(rag_questions, 1)
    avg_total = sum(r["total_latency_ms"] for r in results if r["rag_used"]) / max(rag_questions, 1)
    avg_chunks = chunks_retrieved_total / max(rag_questions, 1)

    print()
    print("=== BENCHMARK RESULTS ===")
    print(f"Total questions: {len(questions)}")
    print(f"Questions using RAG: {rag_questions}")
    print(f"Questions using GENERAL: {general_questions}")
    print(f"Chunks retrieved: {chunks_retrieved_total} total, {avg_chunks:.1f} avg per RAG question")
    print(f"Grounded: {grounded_count}/{rag_questions}")
    print(f"Abstained: {abstained_count}/{rag_questions}")
    print(f"Average retrieval latency: {avg_retrieval:.1f}ms")
    print(f"Average generation latency: {avg_generation:.1f}ms")
    print(f"Average total latency: {avg_total:.1f}ms")
    print()

    # Per-question breakdown
    print("=== PER-QUESTION BREAKDOWN ===")
    for r in results:
        status = "GROUND" if r["grounded"] else ("ABSTAIN" if r["abstained"] else "PARTIAL")
        print(f"  {r['id']}: route={r['route']}, chunks={r['chunks_retrieved']}, {status}, retrieval={r['retrieval_latency_ms']:.0f}ms, total={r['total_latency_ms']:.0f}ms")

    # Persist results
    output = {
        "summary": {
            "total_questions": len(questions),
            "rag_questions": rag_questions,
            "general_questions": general_questions,
            "grounded": grounded_count,
            "abstained": abstained_count,
            "chunks_retrieved_total": chunks_retrieved_total,
            "avg_retrieval_ms": round(avg_retrieval, 2),
            "avg_generation_ms": round(avg_generation, 2),
            "avg_total_ms": round(avg_total, 2),
            "avg_chunks_per_rag": round(avg_chunks, 2),
        },
        "results": results,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with open(args.output, "w", encoding="utf-8") as f:
        json.dump(output, f, indent=2, ensure_ascii=False)
    print(f"\nResults persisted to: {args.output}")


if __name__ == "__main__":
    main()
