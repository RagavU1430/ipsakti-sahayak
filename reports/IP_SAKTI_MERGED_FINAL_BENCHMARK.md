# IP-SAKTI Merged Final Benchmark

Date: 2026-09-20 | corpus: v1-legacy

## 25Q
{"n": 25, "rag_used": 13, "grounded": 13, "abstained": 12, "avg_retrieval_ms": 113.91, "avg_total_ms": 2064.75, "corpus": "v1-legacy", "generator_note": "mixed extractive/live-Gemini per intent; see per-row generator"}

## 65Q (55Q suite)
{"n": 65, "rag_used": 45, "grounded": 45, "abstained": 20, "avg_retrieval_ms": 145.45, "avg_total_ms": 1595.19, "corpus": "v1-legacy"}

## Adversarial
{"n": 30, "abstained": 16, "grounded": 14}

## Retrieval
{"vector": {"Recall@5": 0.8413, "Recall@8": 0.8413, "Recall@10": 0.8413, "MRR": 0.881, "n": 63}, "lexical": {"Recall@5": 0.9206, "Recall@8": 0.9206, "Recall@10": 0.9206, "MRR": 0.881, "n": 63}, "hybrid": {"Recall@5": 0.9206, "Recall@8": 0.9206, "Recall@10": 0.9206, "MRR": 0.9127, "n": 63}, "hybrid_rerank": {"Recall@5": 0.8333, "Recall@8": 0.8333, "Recall@10": 0.8333, "MRR": 0.8862, "n": 63}}

## Candidate-K
[{"K": 8, "n": 63, "recall@10": 0.8333, "mrr": 0.8889, "avg_retrieval_ms": 140.23, "avg_rerank_ms": 3.24}, {"K": 12, "n": 63, "recall@10": 0.8333, "mrr": 0.8889, "avg_retrieval_ms": 143.04, "avg_rerank_ms": 4.76}, {"K": 16, "n": 63, "recall@10": 0.8333, "mrr": 0.8889, "avg_retrieval_ms": 142.16, "avg_rerank_ms": 5.97}, {"K": 24, "n": 63, "recall@10": 0.8333, "mrr": 0.8889, "avg_retrieval_ms": 141.66, "avg_rerank_ms": 8.23}]

Decision: K=24 retained (recall@10 identical 0.8333 at K=8..24).

## Gemini live (n=32)
min 1320.8 / avg 1906.6 / median 1764.4 / p95 2758.5 / max 2893.9 ms

## Backend (n=120)
retrieval avg 142.3 p95 342.1 | rerank avg 7.4 | total avg 1442.1 median 1634.4 p95 2967.1 | dominant: Gemini (measured)

## Frontend
instrumentation VERIFIED; live numbers NOT MEASURED

## Tests
V1: {"passed": 86, "skipped": 30, "total": 116, "by_module": {"api+chunking+dataset": 18, "grounding": 13, "multilingual": "33 passed + 30 skipped", "retrieval+supabase": 13, "merge": 9}}

V2: {"test_rag_v2": "26 passed", "corpus+part3": "18 passed, 4 failed (pre-existing TestQueryUnderstanding patent/trademark/copyright + international refs; RAG V2/ untouched by merge)"}

## Safety
{"Q08": "abstain (quarantined 2022 source guard)", "Q09": "abstain (same)", "Q23": "abstain (same)", "section_3p": "grounds on default corpus (regression test)"}

## Sources
{"FSSAI_2022": "NOT COVERED", "TKDL": "NOT COVERED", "IP_India": "NOT COVERED"}

## Release
FAIL