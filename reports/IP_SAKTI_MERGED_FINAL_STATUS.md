# IP-SAKTI Sahayak — V1 Shell + V2 Retrieval Final Status

Date: 2026-09-20 | Validation run: post-merge (this task). Architecture unchanged from merge (no Agentic RAG, no KG, Gemini kept, one API).

## Architecture

V1 shell: `ip-sakti-rag/app/{api/main.py,service.py,retrieval/hybrid.py,retrieval/local_store.py,retrieval/reranker.py,citations/,core/config.py,generation/grounded.py}` + Java `RagClient` + React frontend. Retained, not rewritten.
V2 components (adapters/boosters, no second API): `app/retrieval/v2_adapters.py`, `app/retrieval/v2_legal_boost.py` (query-understanding lite, legal-term extraction, domain soft-priority, RRF assist; boost bounded 0.10, V1 fusion authoritative).

## Corpus

- Default runtime: V1 legacy 24 docs / 7019 chunks (sha256 827f8a209ff7cbc8; reload deterministic, verified).
- V2 canonical: 22 docs / 1961 chunks (sha256 77b79004c3c1fdfa), opt-in via `RAG_CORPUS_SOURCE=v2`.
- V2 raw builder output (13093 UNKNOWN) is NOT canonical.
- Migration BLOCKED: wholesale V2 regresses Section 3(p) (0 V2 chunks with 3(p) text; regression test `test_section_3p_regression_v1_corpus_grounds` guards default).

## Source coverage

FSSAI 2022: NOT COVERED (quarantined HTML-not-PDF; Q08/Q09/Q23 abstain by guard). TKDL: NOT COVERED. IP India: NOT COVERED. See `docs/RAG_V2_SOURCE_COVERAGE.md`. No invented content.

## Retrieval

Vector-only / lexical-only / hybrid / hybrid+reranker measured on latest.jsonl end_to_end (n=63, doc-level ground truth, auto jurisdiction, K=24). Full table: `ip-sakti-rag/reports/RAG_RETRIEVAL_COMPARISON.md`.

| Retrieval | Recall@5 | Recall@8 | Recall@10 | MRR |
|---|---:|---:|---:|---:|
| Vector only | 0.8413 | 0.8413 | 0.8413 | 0.8810 |
| Lexical only | 0.9206 | 0.9206 | 0.9206 | 0.8810 |
| Hybrid | 0.9206 | 0.9206 | 0.9206 | 0.9127 |
| Hybrid + reranker | 0.8333 | 0.8333 | 0.8333 | 0.8862 |

Flat across K: expected sets are 1-2 docs. Chunk-level Recall/MRR: NOT MEASURED (no chunk judgments). Domain split measured per category in `retrieval_comparison.json` (e.g. trademark 0.9, international 0.0 under INDIA-hardcode corrected to auto; TKDL/IP-India/FSSAI-2022 have no questions by coverage — not fabricated).

## Reranking

V1 `LegalFeatureReranker` retained. Measured effect: hybrid+rerank doc-recall 0.8333 vs hybrid 0.9206 (reranker optimizes chunk precision for generation, not doc recall). K-sweep rerank cost: 3.2ms (K=8) → 8.2ms (K=24); recall@10 identical 0.8333 across K — K=24 retained (keeps hard-query pool; cost negligible vs Gemini).

## Grounding

- 25Q merged (phase16 questions, unchanged, case jurisdiction): 13/25 RAG_USED, 13/25 grounded, 12/12 abstained, avg retrieval 113.9ms, avg total 2064.8ms. Report: `ip-sakti-rag/reports/MERGED_25Q_BASELINE.md`.
- 55Q/65Q merged (latest.jsonl end_to_end, unchanged, auto jurisdiction): 45/65 RAG_USED, 45/65 grounded, 20 abstained, avg retrieval 145.5ms, avg total 1595.2ms. Report: `ip-sakti-rag/reports/MERGED_55Q_RESULTS.md`. Pre-merge artifact comparison NOT manufactured (configs differ).

## Citations

V1 `citations/` + `validate_citations` active; fallback to extractive re-run before abstain preserved. Citation success: 25Q 13/25 (equals grounded), 65Q 45/65. No fabricated citations observed; injection chunk test passes (cites only supplied IDs).

## Abstention

`evidence_status` SUFFICIENT/PARTIAL/INSUFFICIENT; empty evidence abstains BEFORE generation (generator never called — tested). CONFLICTING as separate status: NOT IMPLEMENTED (limitation). Q08/Q09/Q23: controlled abstention via quarantined-source guard (ev=0, explicit message) — PASS. Adversarial 29/30 expectation-match; ADV-014 (demand guaranteed outcome) abstains where gold expects answer with disclaimer — safe-direction WARNING. Abstention cache 60s active (abstentions only).

## Gemini

Live, merged runtime (n=32 Gemini generations across 25Q+55Q+ADV): min 1320.8ms, avg 1906.6ms, median 1764.4ms, p95 2758.5ms, max 2893.9ms. Model `gemini-grounded-json-v1:gemini-3.5-flash-lite` (fallback chain). Extractive path median ~1ms on fast intents (some rows include failed-Gemini-then-fallback time — honest per-row values kept).

## Backend latency

Merged (n=120 scored rows): retrieval avg 142.3 / median 128.9 / p95 342.1ms; rerank avg 7.4 / p95 14.0ms; total avg 1442.1 / median 1634.4 / p95 2967.1ms. Dominant component: Gemini generation (measured, not assumed). Query-analysis/embedding/evidence-assembly/citation-validation not separately instrumented (limitation — included in total).

## Frontend latency

Instrumentation VERIFIED present (no new code needed): `Frontend/src/api/client.ts:62-122` (`requestWithMeta`: tRequestStart/tResponseStart/tResponseEnd, requestId, Server-Timing, provider, chunks, route, backendTotalMs) + `AskPage.tsx:224-270` (marks user-submit/request-start/response-received/react-render-end, TTFA = submit→answer visible). Live user-perceived numbers: NOT MEASURED (servers not run in this environment).

## Recall@5 / @8 / @10 / MRR

See Retrieval table above (document-level). Chunk-level: NOT MEASURED.

## 25Q results

`reports/IP_SAKTI_MERGED_FINAL_BENCHMARK.json` (q25) + `ip-sakti-rag/reports/merged_phase25_results.json` + `MERGED_25Q_BASELINE.md`. First trustworthy post-merge baseline (old Phase-16 RAG_USED/chunks discarded — harness bug).

## 55Q results

65 end_to_end run (the "55Q" set is the 65-row latest.jsonl end_to_end suite): 45 grounded / 20 abstained. See `MERGED_55Q_RESULTS.md`.

## Adversarial results

29/30 match expected abstain/answer behavior (graded against expected_abstain). 1 WARNING (ADV-014 safe abstention). Injection-doc test: chunk treated as DATA, citations constrained — PASS (`test_prompt_injection_chunk_treated_as_data`).

## V1 tests

86 passed, 30 skipped, 116 total (by module: api+chunking+dataset 18; grounding 13; multilingual 33+30 skipped; retrieval+supabase 13; merge 9). Historical `77/30` claim superseded (9 merge tests added). Zero failures.

## V2 tests

`test_rag_v2.py` 26/26 PASS (historical claim confirmed). corpus+part3: 18 passed, 4 failed — pre-existing `test_part3_retrieval.py` failures (patent/trademark/copyright intent, international refs); `RAG V2/` untouched by merge (git status clean for tracked files), unrelated to production path.

## Known limitations

1. V2 wholesale migration blocked (3(p) coverage constraint).
2. Chunk-level Recall/MRR absent (no judgments).
3. CONFLICTING evidence status not separate.
4. Stage timings for query-analysis/embedding/assembly/citation-validation not isolated.
5. Live frontend numbers not measured; Supabase pgvector path not verified (no credentials exercised).
6. ADV-014 over-abstention (safe direction).

## Remaining blockers

- V2 rebuild restoring 3(p)-class provisions + provenance validation.
- Official FSSAI-2022 acquisition; TKDL/IP-India scoping (or keep NOT COVERED).
- V2 part3 4 failures (non-production, low priority).
- Rebuild-from-source pipeline automation (verified deterministic reload only).

## Release status

FAIL — gate not passed. Truthful metrics, safety guards, and measurements are in, but: V2 migration blocked, 3 required sources NOT COVERED, chunk-level evaluation absent, and 25Q grounded rate (13/25 in-process; production backend GENERAL-routing inflates user-facing answers without evidence) does not meet an evidence-grounded release bar. No "production-ready / fully-grounded" claim made. Warnings alone cannot cover the missing coverage + Blocked migration.
