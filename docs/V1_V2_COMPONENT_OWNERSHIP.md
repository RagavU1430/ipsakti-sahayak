# V1 / V2 Component Ownership (Phase 1)

Target: V1 production shell + V2 corpus/retrieval intelligence. No rebuild, no second production API.

## V1 OWNS (`ip-sakti-rag/` + Java backend + React frontend)

- FastAPI application: `ip-sakti-rag/app/api/main.py` (routes `/api/v1/ask`, `/rag/query`, `/health`)
- Service orchestration shell: `ip-sakti-rag/app/service.py` (`RAGService.query/ask`, cache, guardrails, metrics)
- Gemini provider/generation: `ip-sakti-rag/app/generation/grounded.py` (`GeminiGroundedGenerator`, `OpenRouterGroundedGenerator`, `ExtractiveGroundedGenerator`)
- Citations + validation: `ip-sakti-rag/app/citations/`
- Abstention runtime (policy fixed in merge): `app/guardrails/policy.py` + `service.py:_abstained`
- Cache (response + new 60s abstention cache): `service.py:_cache`, `service.py:_abstention_cache`
- API contracts: `app/models/schemas.py` (`AskRequest/Response`, `QueryRequest/Response`, `Evidence`, `Citation`)
- Java integration: `RagProperties.java`, `RagClient.java:49-57`, `RagClientConfig.java`, `QuestionService.java`, `ConversationService.java`
- X-RAG metrics + tracing: `main.py:53-63` headers, `RagClient.java:61-68`, `RequestTiming`, `X-Request-ID`
- Production error handling: 503 mapping in `main.py:65-71,83-88`, `RagClientException`
- Config: `ip-sakti-rag/app/core/config.py` (+ `RAG_CANONICAL_CHUNKS` extension in Phase 2)
- Reranker of record until benchmark proves otherwise: `app/retrieval/reranker.py`
- Retrieval store interface + hybrid fusion shell: `app/retrieval/local_store.py`, `app/retrieval/hybrid.py`

## V2 OWNS (`RAG V2/`)

- Canonical corpus: `RAG V2/dataset/canonical/chunks_v2.jsonl` (1961 chunks / 22 docs / 10 domain tags)
- Corpus metadata + SourceRecord enrichment: `RAG V2/app/corpus/pipeline.py`, `RAG V2/app/embedding/schema.py`
- Query understanding (deterministic, no LLM): `RAG V2/app/retrieval/query_understanding.py`
- Legal term extraction: `RAG V2/app/retrieval/legal_terms.py`
- Domain-aware prioritization (soft, never hard-filter): `RAG V2/app/retrieval/domain_aware.py`
- Hybrid retrieval / RRF reference: `RAG V2/app/retrieval/hybrid_retrieval.py`
- Retrieval configuration + evaluation inputs: `RAG V2/dataset/evaluation/`, `app/retrieval/baseline.py`, `app/retrieval/benchmark.py`, `app/retrieval/part3.py`
- V2 tests: `RAG V2/tests/`

## SHARED / ADAPTED (V1-compatible adapters, no duplication)

- Reranker: V1 `reranker.py` is authoritative; V2 `reranking.py` (lexical-boost/domain-rank) is
  evaluation-only until Phase 8/23 benchmark.
- Evidence object: V1 `Evidence` schema is canonical; V2 `CanonicalRetrievalResult`/`data_contract.py`
  maps through `ip-sakti-rag/app/retrieval/v2_adapters.py::normalize_v2_chunk` (Phase 3).
- V2 intelligence inside V1 pipeline via additive boosters only:
  `ip-sakti-rag/app/retrieval/v2_legal_boost.py` (legal-term boost + domain prioritization +
  RRF-style fusion assist). Never replaces semantic retrieval.
- Configuration: `EMBEDDING_PROVIDER`, `RAG_CANDIDATE_K`, `RAG_CANONICAL_CHUNKS` shared via env;
  hash = DEVELOPMENT fallback, must be labelled in logs.

## EXPLICITLY NOT OWNED / FORBIDDEN IN MERGE

- No second production API. V2 has no FastAPI service; do not create one.
- No Agentic RAG, no Knowledge Graph (deferred until release gate passes).
- No invented FSSAI-2022 text, no placeholder TKDL/IP-India evidence.
- No `RAG_USED=false` + authoritative answer + `grounded` status (forbidden).
- No Recall/MRR/groundedness claims without measurement (use NOT MEASURED).
