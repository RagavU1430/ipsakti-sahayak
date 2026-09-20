# RAG V2 Implementation Baseline

Baseline date: 2026-09-19
Status: NOT READY

## Scope and source of truth

The V2 work area is `RAG V2/`. Its source inputs are the Markdown files under `RAG V2/dataset_markdown/`, generated from the raw files under `RAG V2/dataset/`. The generated directory currently contains 31 `.md` files, including placeholder and auxiliary conversion artifacts. These are not all authoritative documents.

The intended content corpus is the 25-document set described by the existing V2 mapping in `RAG V2/scripts/build_rag_v2_index.py`. The current prototype index contains 1,961 JSONL records in `RAG V2/dataset/canonical/chunks_v2.jsonl`; these are page/heading fragments, not a validated V2 chunk build.

## Existing infrastructure

### V1 RAG runtime

The V1 implementation remains under `ip-sakti-rag/` and is not modified by this V2 effort. It includes:

- FastAPI `/api/v1/ask` and compatibility endpoints.
- Query analysis, local lexical/TF-IDF retrieval, hybrid fusion, and a deterministic legal-feature reranker.
- Grounded extractive generation plus optional Gemini/OpenRouter providers.
- Programmatic citation construction and validation.
- Evidence sufficiency, confidence, abstention, prompt-injection boundaries, and request logging.
- Supabase/pgvector migrations and RPC contracts, with production connectivity still environment-dependent.
- V1 tests and evaluation artifacts.

### Database and providers

PostgreSQL with pgvector is the existing target database. Migrations define document, chunk, embedding, retrieval-log, evaluation, citation, and conversation structures. The V1 embedding wrapper supports OpenRouter `openai/text-embedding-3-small` at 1,536 dimensions and a deterministic hash provider for tests. No V2-specific embedding manifest or production V2 index is present.

### Frontend and backend

The Spring Boot backend and V1 Python service exist. The repository contains frontend/backend integration work for V1, but no verified V2 endpoint or frontend V2 integration. V2 must preserve V1 APIs and use a separate implementation boundary.

## V2 corpus state

| Item | Current finding |
|---|---|
| Raw source families | India Code, WIPO, Ayush, NBA, and FSSAI material under `RAG V2/dataset/` |
| Generated Markdown files | 31, including placeholders and auxiliary artifacts |
| Intended content documents | 25 according to the existing V2 document map |
| Prototype JSONL records | 1,961 |
| Stable validated document IDs | Not yet produced by a V2 validator |
| Metadata manifest | Not present as `dataset/v2/validated_manifest.json` |
| V2 chunks artifact | Prototype only; not a validated structure-aware build |
| Embeddings | No V2 embedding manifest or generated V2 vectors |
| Vector index | No verified V2 pgvector table/index populated |
| Lexical index | No V2 database lexical index; local V1-style lexical fallback is reusable |
| Reranker | No V2-owned reranker; V1 deterministic reranker is reusable behind a boundary |
| Generation/citations/abstention | No V2-owned API pipeline; V1 components are reusable but not proof of V2 readiness |

## Known corpus risks

1. The Markdown converter can include extraction placeholders such as `[No extractable text found on this page.]`.
2. The prototype index truncates each fragment to 5,000 characters, which can silently cut legal provisions.
3. The prototype assigns source URLs to generated local paths and marks all indexed content `VERIFIED`; those values are not sufficient provenance.
4. Metadata is partly inferred from filename mappings rather than validated from source metadata.
5. HTML-derived material may contain navigation or boilerplate leakage.
6. The FSSAI 2022 file is an HTML application shell despite its `.pdf` name and must remain quarantined until an authoritative source is supplied.
7. OCR and extraction quality are not represented consistently in the current V2 records.
8. No reproducible corpus hash, data version, embedding version, or index version is recorded for V2.

## Missing V2 components

The V2 release path still needs authoritative corpus validation, normalization, structure-aware chunking, a complete provenance manifest, chunk-quality audit, documented embedding selection, resumable embedding generation, a dedicated pgvector schema/index, lexical retrieval, hybrid retrieval, reranking, evidence assembly, grounded generation, citation validation, evidence status, abstention, tracing, V2 API integration, benchmark execution, adversarial/security testing, and release-gate evidence.

Knowledge graph and agentic RAG are intentionally out of scope for this foundation phase.

## Reusable components

V1 retrieval models, local store interfaces, hybrid fusion, deterministic legal reranking, citation validation, grounding context boundaries, abstention policy, provider wrappers, Supabase migration patterns, and test fixtures can be adapted behind V2-owned modules. V1 files and APIs must remain operational and unchanged unless a separately justified compatibility fix is required.

## Baseline tests

Executed on 2026-09-19 before V2 implementation changes:

- V1 `ip-sakti-rag`: 77 passed, 30 skipped, 5 warnings.
- V2 `RAG V2`: 26 passed, 2 warnings.

These results validate the existing code paths only. They do not demonstrate V2 retrieval quality, embedding coverage, pgvector operation, groundedness, citation correctness, latency, or release readiness.

## Baseline decision

V2 is a corpus-ingestion project at this point, not a production RAG release. The next controlled implementation slice is a V2 validator and structure-aware chunker that emits explicit `UNKNOWN` metadata rather than inventing provenance, followed by focused corpus tests.