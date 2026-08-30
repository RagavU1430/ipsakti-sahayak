# RAG repair final report

Date: 2026-08-29  
Decision: **NOT READY FOR PRODUCTION**

## Outcome

The repository now contains a single executable RAG path from canonical ingestion through API response. It replaces the audited placeholders with legal-aware parsing, validation, hybrid retrieval, hard filters, reranking, bounded context, grounded generation, programmatic citations, citation validation, abstention, confidence scoring, Supabase schema/RPC support, tests, and executable evaluation.

The production decision remains negative. The implementation is locally functional and testable, and the missing-source set has been largely repaired, but two source warnings and production infrastructure gaps remain.

## Repairs delivered

- Canonical 25-document registry build with 24 retrievable documents and 6,514 validated chunks.
- PDF magic-byte/checksum validation, page-aware extraction, OCR caching/uncertainty, exact deduplication, stable IDs, and legal hierarchy for sections, clauses, rules, regulations, articles, and paragraphs.
- Hard quarantine of the invalid FSSAI 2022 download and removal of the former unrelated USDA fallback. The official 2025 FSSAI order is independently verified and OCR-indexed.
- OpenRouter embedding client with count/dimension/finite-value checks plus a deterministic hash provider restricted to tests/dry runs.
- Supabase admin/query separation, document/chunk/embedding upserts, pgvector and full-text RPCs, HNSW/GIN indexes, current-version filters, and RLS.
- Actual parallel lexical/vector candidate generation, documented weighted fusion, exact provision boosts, deterministic reranking, and jurisdiction/domain/language filtering.
- Prompt-injection-resistant context boundary; optional grounded JSON generation; extractive local fallback; citations generated only from used evidence; validation before return.
- Explicit missing-provision, ambiguous-query, source-quarantine, provider-failure, and citation-failure abstention paths. Deterministic confidence replaces unsupported labels.
- FastAPI `/rag/query` and `/health` contract with evidence/metrics kept internal.
- 25 regression tests, 65 end-to-end questions, 30 adversarial questions, structured golden expectations, detailed per-query results, and aggregate metrics.

## Verification evidence

- Source verification pass: 21 of 23 formerly missing raw sources repaired; 2 intentionally remain unresolved.
- Dataset build: 25 documents, 24 retrievable, 6,514 chunks, 0 errors, 2 source warnings.
- Dataset validator: pass.
- Embedding dry run: two 1,536-dimensional deterministic hash vectors validated; no database write.
- Pytest: 25 passed, 0 failed. Five PyMuPDF SWIG deprecation warnings remain.
- End-to-end evaluation: Recall@8 0.9692, Precision@8 0.7618, MRR 0.8724, citation integrity 1.0, groundedness 1.0, abstention accuracy 0.9692.
- Adversarial evaluation: Recall@8 0.9667, Precision@8 0.6542, MRR 0.9083, citation integrity 1.0, groundedness 1.0, abstention accuracy 0.9667.

These are local deterministic-path measurements. They do not validate production embeddings, Supabase behavior, or LLM answer quality.

## Remaining blockers

1. Acquire or manually consolidate the 2 remaining unresolved authoritative raw documents: `IND-PAT-RULES-2003` and `IND-CR-RULES-2013`; until then their page citations are disabled and confidence is capped.
2. Obtain a valid official FSSAI 2022 regulation PDF and rebuild it. Do not bypass quarantine with mirrors or unrelated reports.
3. Apply and test the Supabase migrations in staging, verify RLS as anon/authenticated/service-role, and ingest complete embeddings.
4. Select and evaluate a production embedding model and a learned reranker. The current local TF-IDF and legal-feature reranker are honest fallbacks, not claimed substitutes.
5. Exercise the OpenRouter grounded JSON path with configured credentials and rerun the full evaluation. Add provider timeout/retry/usage monitoring.
6. Preserve the tested fail-closed policy for hidden-instruction, credential, invalid-source, and nonexistent-provision requests while expanding red-team coverage.

## Next action

The next safe action is targeted source completion: obtain a verified full Patents Rules 2003 source and implement a documented Copyright Rules 2013 chapter-consolidation ingestion path, then rebuild. After the source warning count reaches zero, apply `003_rag_repair.sql` in a disposable Supabase staging project and run full non-dry-run embedding ingestion before reconsidering readiness.
