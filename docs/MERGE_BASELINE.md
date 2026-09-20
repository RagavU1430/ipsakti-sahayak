# MERGE BASELINE — Frozen Audit (Phase 0)

Date: 2026-09-20
Status: PRE-MERGE — no production code changed except benchmark-harness/metric fixes noted below.

## 1. Chunk / document counts (measured)

| Corpus | Chunks | Docs | Domains |
|---|---|---|---|
| `ip-sakti-rag/dataset/canonical/chunks.jsonl` (V1) | 7019 | 24 | PATENT 1181, TRADEMARK 1220, GI 596, COPYRIGHT 838, DESIGN 217, PLANT_VARIETY 691, ABS 692, AYURVEDA 389, INTERNATIONAL 1171, FOOD 24 |
| `RAG V2/dataset/canonical/chunks_v2.jsonl` (V2 canonical) | 1961 | 22 | AYURVEDA 545, FOOD 173, ABS 155, COPYRIGHT 62, DESIGN 50, GI 96, PATENT 90, PLANT_VARIETY 122, TRADEMARK 173, INTERNATIONAL 495 |
| `RAG V2/dataset/v2/chunks_v2.jsonl` (raw V2 builder output) | 13093 | 24 (V2-DOC-* placeholder IDs) | UNKNOWN 13093 — NOT canonical, do not use |

V1 extra docs vs V2 canonical: `IND-CR-RULES-2013` (447 chunks), `IND-PAT-RULES-2003` (358 chunks) —
both `LEGACY_UNVERIFIED_RAW_MISSING` in V1. V2 canonical correctly excludes them (all 22 docs VERIFIED).
V2 canonical BioDiv Act coverage includes 39 Biodiversity Act chunks after portal-strip repair.

## 2. V1 evidence (Phase 16 backend benchmark)

Source: `ip-sakti-rag/dataset/evaluation/results/phase16_backend_results.json`
- total_rag_questions: 25, successful_http: 25, grounded_answers: 20, citation_success: 20
- average_backend_total_ms: 1512.902, median: 1722.017, p95: 2251.781
- RAG_USED: False 25/25, number_of_chunks_retrieved: 0 25/25 (as recorded by harness)

### 2a. KNOWN HARNESS BUG (found during merge audit, blocks truthfulness)
`scripts/phase16_backend_benchmark.py:27-29` looks up:
- `X-Rag-evidence-count` — but `app/api/main.py:60-61` emits `X-RAG-evidence-passed-to-llm` and
  `X-RAG-context-chunks`, never `X-RAG-evidence-count`. Result: always 0.
- Header lookup is case-sensitive (`X-Rag-...` vs wire `x-rag-...`), so `evidence_passed_to_llm`
  also misses even when evidence exists.
- Direct in-process evidence (`dataset/evaluation/results/latest.jsonl`, 55-question runtime)
  DOES show 8 chunks/query with fusion/reranker scores — retrieval works in-process.

Conclusion: Phase 16 `RAG_USED=False / chunks=0` is at least partly a METRICS-PROPAGATION /
HARNESS bug, not proof of zero retrieval. Phase 9/20 must fix headers + harness before
any RAG_USED claim. Recorded here so the merge does not invent retrieval numbers.

## 3. V2 evidence

Source: `RAG V2/dataset/evaluation/results/rag_v2_phase3_after_web_sources.json`
- summary: total 25, rag_questions 25, grounded 22, abstained 0, chunks_total 135 (5.4/q),
  avg_retrieval_ms 47.67, avg_generation_ms 4.34 (deterministic-extractive-v1, NOT live LLM),
  avg_total_ms 63.06.
- Phase 2 baseline (`rag_v2_phase2_baseline.json`): grounded 22 (file) vs narrative 20/25 in
  `RAG_V2_FINAL_REPORT.md` — persisted JSON is the machine-readable baseline.
- Outstanding defect: Q08/Q09/Q23 partial, not explicit abstention.
- `tkdl=0, ip_india=0`, FSSAI-2022 excluded, Part 3 Recall@K NOT MEASURED.

## 4. Latency (from `RAG_RETRIEVAL_TIME_ANALYSIS.md`, verified config)

- V1 local: analyze 0-1ms, retrieve 119-656ms (post hint-precompute fix), rerank 5-11ms,
  generation (Gemini) 1361-2247ms. Fast-extractive intents total 304-497ms.
- Config: `EMBEDDING_PROVIDER=openrouter`, `RAG_STORAGE_BACKEND=local`,
  `RAG_CANDIDATE_K=24`, `RAG_LLM_TIMEOUT=30.0`, `RAG_READ_TIMEOUT=60s` (backend).
- V2 4.3ms generation is NOT comparable to production LLM latency.

## 5. Tests

- V1: `ip-sakti-rag/tests` collected (test_api, test_chunking, test_dataset, test_grounding,
  test_multilingual, ...). Full `pytest -q` exceeded 120s in this environment — NOT re-baselined
  here; Phase 28 must record `77 passed / 30 skipped` claim as UNVERIFIED until rerun.
- V2: `RAG V2/tests/test_rag_v2.py` claims 26/26 PASS; `test_part3_retrieval.py`,
  `test_corpus_pipeline.py` present. Rerun required in Phase 28.

## 6. Source coverage (explicit)

- FSSAI 2022 regulation: NOT COVERED (quarantined, HTML-not-PDF) in both systems.
- TKDL: NOT COVERED (0 chunks). IP India: NOT COVERED (0 chunks).
- V1 README release gate: NOT PASSED (23/24 docs legacy text, page cites disabled for legacy).
