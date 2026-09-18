#!/usr/bin/env python3
"""Diagnostic retrieval test for RAG V2.

Accepts a question and prints:
- Query
- Top K chunks with scores
- Document, section/article/rule
- Relevant text snippet
- Retrieval latency

Usage:
    python scripts/test_rag_v2_retrieval.py "What does Section 3(p) say?"
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "ip-sakti-rag"))

from app.retrieval.local_store import LocalCorpusStore
from app.retrieval.hybrid import HybridRetriever
from app.retrieval.query_analysis import analyze_query
from app.retrieval.reranker import LegalFeatureReranker
from app.models import QueryRequest
from app.legal_aliases import normalize_legal_query

# Paths
CHUNKS_PATH = Path(__file__).resolve().parents[2] / "RAG V2" / "dataset" / "canonical" / "chunks_v2.jsonl"

def main():
    if len(sys.argv) < 2:
        print("Usage: python test_rag_v2_retrieval.py '<question>'")
        sys.exit(1)

    question = " ".join(sys.argv[1:])

    # Load store
    print(f"Loading chunks from {CHUNKS_PATH}...")
    store = LocalCorpusStore(CHUNKS_PATH)
    retriever = HybridRetriever(store, candidate_k=24)
    reranker = LegalFeatureReranker()

    # Build query
    request = QueryRequest(query=normalize_legal_query(question))
    analysis = analyze_query(request)

    # Determine RAG route
    q_lower = question.lower()
    rag_terms = ["wipo", "trips", "treaty", "convention", "pct", "patent",
                 "trademark", "copyright", "design", "biodiversity", "ayurveda",
                 "ayush", "fssai", "gi", "ppvfr"]
    is_rag = any(t in q_lower for t in rag_terms)
    route = "RAG" if is_rag else "GENERAL"

    print(f"\n{'='*60}")
    print(f"Query:    {question}")
    print(f"Route:    {route}")
    print(f"{'='*60}")

    if route == "GENERAL":
        print("(General question — bypasses retrieval)")
        return

    # Retrieve
    import time
    t0 = time.perf_counter()
    candidates = retriever.retrieve(analysis)
    retrieval_ms = (time.perf_counter() - t0) * 1000

    print(f"\nRetrieved {len(candidates)} candidates in {retrieval_ms:.1f}ms")

    # Re-rank
    evidence = reranker.rerank(analysis, candidates, final_count=6)
    print(f"\nTop {len(evidence)} chunks after re-ranking:")

    for idx, chunk in enumerate(evidence, 1):
        print(f"\n{'-'*60}")
        score = chunk.reranker_score or chunk.fusion_score or chunk.vector_score or 0.0
        print(f"Rank #{idx} | Score: {score:.4f} | Latency: {retrieval_ms:.1f}ms")
        print(f"Document: {chunk.document_id} | Domain: {chunk.domain}")
        section = chunk.section or chunk.rule_number or chunk.article_number or "N/A"
        print(f"Section:  {section}")
        print(f"Text:     {chunk.text[:200]}...")
        print(f"{'-'*60}")

    # Evidence assessment
    abstain_reason = None
    if not evidence:
        abstain_reason = "NO_RETRIEVED_EVIDENCE"
    elif len(evidence) == 0:
        abstain_reason = "INSUFFICIENT_RETRIEVED_EVIDENCE"

    print(f"\nEvidence sufficiency: {'SUFFICIENT' if evidence and not abstain_reason else 'INSUFFICIENT'}")
    if abstain_reason:
        print(f"Abstention reason: {abstain_reason}")

if __name__ == "__main__":
    main()
