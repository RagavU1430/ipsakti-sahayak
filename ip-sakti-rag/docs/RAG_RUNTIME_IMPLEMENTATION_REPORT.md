# RAG runtime implementation report

Date: 2026-08-30

## 1. Architecture implemented

Implemented the production-oriented runtime boundary `POST /api/v1/ask` on top of the existing validated RAG stack. The runtime reuses the existing query analysis, local/Supabase corpus stores, hybrid retrieval, deterministic fusion, legal-aware reranker, evidence sufficiency checks, grounded generation, citation validation, and deterministic confidence scoring.

No dataset rebuild or document download was performed for this runtime phase.

## 2. Files created

- `docs/RAG_RUNTIME_IMPLEMENTATION_REPORT.md`

## 3. Files modified

- `app/api/main.py`
- `app/core/config.py`
- `app/models/__init__.py`
- `app/models/schemas.py`
- `app/retrieval/local_store.py`
- `app/retrieval/query_analysis.py`
- `app/retrieval/supabase_store.py`
- `app/service.py`
- `tests/conftest.py`
- `tests/test_api.py`
- `.env.example`
- `docs/RAG_API_CONTRACT.md`

## 4. API contract

The backend/frontend boundary is:

`POST /api/v1/ask`

Request fields:

- `question`: required, normalized, non-empty after trimming, max 4,000 characters.
- `domain`: optional, validated enum.
- `jurisdiction`: optional, validated enum.
- `top_k`: optional, bounded 1 to 20.

Response fields:

- `answer`
- `confidence`: deterministic numeric float from 0.0 to 1.0.
- `abstained`
- `citations`
- `sources`

The compatibility endpoint `POST /rag/query` remains available for existing internal tests.

## 5. Retrieval flow

The runtime flow is:

`question -> query processor -> metadata filtering -> hybrid retrieval -> vector search + keyword search -> fusion -> reranking -> top-k evidence`.

The query processor now preserves the original question while also producing an expanded retrieval query for common legal-intent patterns such as trademark registration. This improves retrieval without changing the user-visible question or fabricating metadata.

## 6. Reranking

The runtime continues to use `LegalFeatureReranker`, a deterministic legal-feature reranker. It considers fused retrieval score, token coverage, exact legal identifier support, and verified-source status. It is explicitly not claimed to be a learned reranker.

## 7. Grounding

Generation remains grounded through `ExtractiveGroundedGenerator` in local mode and `OpenRouterGroundedGenerator` when `RAG_ENABLE_LLM=true` and credentials are configured. Evidence is wrapped as untrusted document data before generation.

## 8. Citation validation

Citations are backend-controlled. The generator can only reference retrieved chunk IDs; the backend maps those IDs to citation metadata and validates that cited provisions are supported by retrieved evidence. Citation validation failure causes abstention.

## 9. Confidence

The `/api/v1/ask` response returns numeric confidence. Grounded confidence is deterministically derived from reranker score, citation coverage, source authority, evidence count, and jurisdiction consistency. Abstentions return `0.18`.

## 10. Abstention

The runtime abstains for ambiguous questions, weak or empty evidence, missing exact provisions, quarantined Ayurveda Aahara 2022 source requests, security exfiltration attempts, generation failures, and citation validation failures.

## 11. Supabase integration

The existing Supabase integration is reused. `SupabaseCorpusStore` now embeds/searches with the expanded retrieval query while preserving the original user question for generation. Supabase URL and keys remain environment-based. No production Supabase call was executed in this phase because credentials were not provided.

## 12. Configuration

`.env.example` now includes:

- `RAG_MIN_SCORE`
- `RAG_ABSTENTION_THRESHOLD`
- `LLM_PROVIDER`, `LLM_MODEL`, and `LLM_API_KEY` placeholders/aliases

Secrets remain environment-based placeholders only. No secret values were added.

## 13. Tests executed

Executed:

```powershell
python -m pytest
```

Result:

- 31 passed
- 5 PyMuPDF/SWIG deprecation warnings

## 14. Test results

New runtime/API coverage includes:

- `/api/v1/ask` grounded response contract
- abstention response contract
- malformed request handling
- invalid domain handling
- invalid `top_k` handling
- service-level public schema mapping
- citation validation failure fail-closed behavior
- quarantined-source abstention through the public endpoint

Existing 25 tests continued passing.

## 15. Local API verification

Started Uvicorn locally and sent real HTTP requests to `POST /api/v1/ask`.

Observed:

- Grounded trademark request: HTTP 200, `abstained=false`, confidence `0.9062`, 3 citations, source IDs included `IND-TM-ACT-1999` and `IND-TM-RULES-2017`.
- Unsupported Mars/IP request: HTTP 200, `abstained=true`, confidence `0.18`, no citations, no sources.
- Malformed blank question: HTTP 422.

## 16. Known limitations

- Local mode uses deterministic local retrieval/generation, not production embeddings or a production LLM.
- The reranker is deterministic and feature-based, not learned.
- `IND-PAT-RULES-2003` and `IND-CR-RULES-2013` remain legacy fallback sources with warnings from the locked dataset baseline.
- The quarantined `IND-FSS-AA-2022` source remains excluded and cannot be cited as authoritative evidence.

## 17. Production blockers

- Configure and test Supabase production retrieval with real credentials.
- Ingest and verify production embeddings.
- Configure and test the selected production LLM provider if extractive local generation is not sufficient.
- Resolve the two remaining authoritative raw source warnings.
- Run staging load/security checks against the actual backend deployment environment.

## 18. Exact next steps

1. Provide Supabase URL and keys in the deployment environment.
2. Run the existing Supabase migrations in staging.
3. Run non-dry-run embedding ingestion against the verified canonical corpus.
4. Set `RAG_STORAGE_BACKEND=supabase` and verify `/api/v1/ask` against staging.
5. If using LLM generation, set `RAG_ENABLE_LLM=true`, `OPENROUTER_API_KEY`, and `OPENROUTER_MODEL`, then rerun grounded/citation/adversarial tests.
