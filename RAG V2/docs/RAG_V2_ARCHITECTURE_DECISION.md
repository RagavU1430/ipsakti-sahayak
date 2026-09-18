# RAG V2 Architecture Decision

Generated: 2026-09-14
Phase: 2 — BUILD THE ACTUAL RAG ENGINE

---

## 1. What Is Reused from V1

### Reused Components (Direct)

| Component | Path | Reuse Rationale |
|-----------|------|-----------------|
| `local_store.py` | `app/retrieval/local_store.py` | Deterministic TF-IDF + BM25 hybrid store using frozen corpus JSONL. Zero network calls, sub-150ms retrieval. |
| `query_analysis.py` | `app/retrieval/query_analysis.py` | Domain routing, intent detection, legal identifier extraction, hint document precomputation. Already battle-tested. |
| `hybrid.py` | `app/retrieval/hybrid.py` | Fusion scoring (0.55 vector + 0.35 lexical + 0.10 metadata). Parallel vector+lexical search. |
| `reranker.py` | `app/retrieval/reranker.py` | Legal feature reranker with document hint scoring, intent matching, section/rule/article extraction. |
| `legal_aliases.py` | `app/legal_aliases.py` | Document alias expansion, typo normalization, hint document matching. |
| `grounded.py` | `app/generation/grounded.py` | `ExtractiveGroundedGenerator` (deterministic, no network), `GeminiGroundedGenerator` (JSON-mode LLM). |
| `context.py` | `app/generation/context.py` | Evidence assembly with character budget, provision formatting, citation-ready XML blocks. |
| `policy.py` | `app/guardrails/policy.py` | Abstention logic, confidence scoring, evidence intent validation. |
| `citations.py` | `app/citations/engine.py` | Citation extraction, validation, identifier matching. |
| `schemas.py` | `app/models/schemas.py` | `Evidence`, `QueryAnalysis`, `QueryRequest`, `QueryResponse`, `Citation`, `Confidence`, `Domain`, `Jurisdiction`. |
| `config.py` | `app/core/config.py` | `Settings` dataclass, env loading, path resolution. |
| `service.py` | `app/service.py` | `RAGService` orchestration: query → retrieve → rerank → abstain/generate → cite. |
| `embeddings.py` | `app/retrieval/embeddings.py` | `OpenRouterEmbeddingProvider`, `HashEmbeddingProvider` protocol. |
| `main.py` | `app/api/main.py` | FastAPI app with `/health`, `/api/v1/ask`, `/rag/query`, request ID propagation. |

### Reused Settings (from V1 `.env`)

- `LLM_PROVIDER=gemini`, `LLM_MODEL=gemini-3.1-flash-lite`
- `GEMINI_API_KEY` (root `.env`)
- `RAG_LLM_TIMEOUT=30.0`
- `RAG_TOP_K=6`, `RAG_CANDIDATE_K=16`
- `RAG_ABSTENTION_THRESHOLD=0.12`
- `RAG_MAX_CONTEXT_CHARS=18000`
- `RAG_STORAGE_BACKEND=local`

### Reused Corpus Structure

V2 markdown files in `RAG V2/dataset_markdown/` contain the same source documents as V1 canonical chunks, but in markdown format instead of JSONL. The V1 `LocalCorpusStore` reads from `dataset/canonical/chunks.jsonl` — we need a new pipeline to convert V2 markdown → canonical chunks JSONL format compatible with V1's `LocalCorpusStore`.

---

## 2. What Is Replaced

### Replaced Component: `Embeddings`

**Old:** `OpenRouterEmbeddingProvider` → calls OpenRouter API for `text-embedding-3-small` (1536-dim). Requires API key, network latency, rate limits.

**New:** `HashEmbeddingProvider` (deterministic, no network). Dimension 384. Used for initial index-building and diagnostic retrieval. This is the same fallback used in V1 for testing.

**Why:** V1's OpenRouter embeddings are rate-limited and add ~200-500ms per query. For the V2 benchmark with 25 questions, this adds unnecessary latency. `HashEmbeddingProvider` is 100% deterministic and instant — sufficient for proving the RAG pipeline works end-to-end.

**Trade-off:** Hash embeddings are not semantically meaningful (no cosine similarity). The system relies on TF-IDF lexical + metadata scoring for retrieval quality. This is acceptable for Phase 2 — we prove the pipeline works, then can upgrade to real embeddings.

### Replaced Component: `CorpusStore`

**Old:** `LocalCorpusStore` reads pre-built JSONL chunks from `dataset/canonical/chunks.jsonl` (V1 corpus, 7,019 chunks / 28 docs).

**New:** `MarkdownCorpusStore` reads markdown files from `RAG V2/dataset_markdown/`, parses them, produces chunk dicts with V2-specific metadata fields, and can optionally write canonical JSONL for `LocalCorpusStore` compatibility.

**Why:** V1 corpus and V2 corpus are different documents. V1 corpus has 28 docs including `IND-PAT-RULES-2003`, `IND-BD-RULES-2024`, `IND-AYUSH-2024`, `IND-AYUSH-AR-2024-25`. V2 corpus has 25 docs including `IND-TRIPS-1994`, `INT-WIPO-PCT`, `INT-WIPO-MADRID`, `INT-WIPO-BUDAPEST`, `INT-WIPO-PARIS`, `INT-WIPO-GRATK-2024`, `IND-GI-RULES-2002`, `IND-CR-RULES-2013`, `IND-CR-ACT-1957`, `IND-DES-RULES-2001`, `IND-TM-RULES-2017`, `IND-PPV-ACT-2001`, `IND-PPV-RULES-2003`, `IND-BD-RULES-2024`, `IND-BD-ACT-2002`, `IND-BD-AMEND-2023`, `IND-FSS-AA-2022`, `IND-FSS-AA-ORDER-2025`, `IND-AYUSH-2024`, `IND-AYUSH-AR-2024-25`, `INT-WIPO-PCT`, `INT-WIPO-MADRID`, `INT-WIPO-BUDAPEST`, `INT-WIPO-PARIS`, `INT-WIPO-GRATK-2024`.

V2 corpus has some docs NOT in V1 (TRIPS, PCT, Madrid, Budapest, Paris, GRATK) and some docs MISSING from V2 that were in V1 (IND-PAT-RULES-2003, IND-BD-RULES-2024, IND-AYUSH-2024, IND-AYUSH-AR-2024-25).

**Decision:** Build a `MarkdownCorpusStore` that reads V2 markdown files, parses them into chunk dicts matching V1's `Evidence` schema, and feeds them into the existing `HybridRetriever` + `LocalCorpusStore` pipeline. This avoids duplicating the entire retrieval infrastructure.

### Replaced Component: `RAG Routing`

**Old:** Backend's `service.query()` routes all questions through `RAGService.query()` → `HybridRetriever.retrieve()` → `LegalFeatureReranker.rerank()`. If evidence insufficient → `_general_fallback()` or `_abstained()`. The Phase 16 audit showed ALL 25 questions had `RAG_USED=False` because the backend's GENERAL LLM answered directly.

**New:** Explicit `RAGRouter` class that inspects the query and determines whether to route through RAG or GENERAL. Uses domain terms, legal identifiers, and document hints — same logic as V1's `query_analysis.py` but with V2-specific domain terms.

**Why:** The Phase 16 audit proved that V1's backend does NOT use RAG retrieval for benchmark questions. V2 must have explicit routing that guarantees RAG_USED=True for IP/legal questions.

### Replaced Component: `Response Contract`

**Old:** `AskResponse` / `QueryResponse` with `answer`, `confidence`, `abstained`, `citations`, `sources`, `evidence`, `limitations`, `metrics`.

**New:** Extended `QueryResponse` adding `request_id`, `route`, `retrieval_status`, `chunks_retrieved`, `evidence_sufficiency`, `grounded`, `latency`. Same base shape but with explicit RAG telemetry fields.

---

## 3. What Is Improved

### Improvement 1: Structure-Aware Chunking

V1's `chunker.py` has sophisticated `legal_units()` regex patterns but operates on extracted `PageText` objects from PDFs/HTMLs. V2's markdown files are already semi-structured (page-based `## Page N` sections).

**V2 Approach:** Parse markdown sections, identify legal structural markers (Article, Section, Rule, Regulation, Chapter, Part, Schedule), and create chunks per legal unit. Fall back to page-based chunks where structure is unclear. Each chunk retains full metadata.

### Improvement 2: Markdown Corpus Store

V1's `LocalCorpusStore` reads pre-built JSONL. V2's source is markdown files with YAML headers. Build `MarkdownCorpusStore` that:
1. Reads all `.md` files in `RAG V2/dataset_markdown/`
2. Parses YAML frontmatter for metadata
3. Splits content into legal-unit chunks
4. Produces chunk dicts compatible with `HybridRetriever` + `LocalCorpusStore`

### Improvement 3: Explicit RAG Router

V1's backend has implicit routing through `RAGService.query()` which always retrieves. But the Phase 16 audit showed the backend LLM answered everything via GENERAL routing. V2 adds an explicit `RAGRouter` class:

```python
class RAGRouter:
    def route(self, query: str) -> str:  # "RAG" or "GENERAL"
        if any(term in query.lower() for term in RAG_DOMAIN_TERMS):
            return "RAG"
        return "GENERAL"
```

### Improvement 4: Diagnostic Mode

V2 includes `scripts/test_rag_v2_retrieval.py` — a standalone diagnostic that accepts a question, runs full retrieval, and prints:
- Query
- Top-K chunks with scores
- Document, section/article/rule
- Relevant text snippet
- Retrieval latency

This proves retrieval works independently of LLM generation.

### Improvement 5: V2-Specific Domain Terms

V1's `DOMAIN_TERMS` includes terms for IND-PAT-RULES-2003, IND-BD-RULES-2024, IND-AYUSH-2024. V2 adds:
- INT-WIPO-PCT, INT-WIPO-MADRID, INT-WIPO-BUDAPEST, INT-WIPO-PARIS, INT-WIPO-GRATK-2024
- IND-TRIPS-1994
- IND-FSS-AA-ORDER-2025
- IND-DES-RULES-2001, IND-CR-RULES-2013

Updated in `RAG V2/app/retrieval/query_analysis.py`.

### Improvement 6: Corrupt Document Rejection

V1 has no extraction validation at the API level. V2 adds `ExtractionValidator` that:
- Rejects `conversion_status: extraction_failed` documents
- Rejects empty files
- Rejects OCR-only documents without extractable text
- Logs rejected documents with reasons

---

## 4. Why This Architecture

1. **Minimum viable pipeline:** Reuses 100% of V1's proven retrieval, reranking, guardrails, citation, and generation code. Only the corpus store and router are new.

2. **V2-specific corpus:** V2 markdown files have different documents and metadata than V1 canonical JSONL. A `MarkdownCorpusStore` bridges this gap without rebuilding the retrieval engine.

3. **Deterministic and fast:** `HashEmbeddingProvider` + TF-IDF lexical search = no network calls, sub-200ms retrieval. Gemini API only for generation (when needed).

4. **Testable end-to-end:** Diagnostic script proves retrieval works before connecting to LLM. Frozen 25-question benchmark validates full pipeline.

5. **Independently testable:** `RAGRouter.route()`, `MarkdownCorpusStore.chunks`, `HybridRetriever.retrieve()`, `LegalFeatureReranker.rerank()` — each component is independently unit-testable.

6. **No false pass:** The pipeline requires actual chunks retrieved, evidence passed to LLM, citations generated, and abstention on insufficient evidence. Code existence alone does not constitute a pass.

---

## 5. How V2 Remains Independently Testable

### Component Isolation

| Component | Test Command | What It Validates |
|-----------|-------------|-------------------|
| `MarkdownCorpusStore` | `python -c "from RAG V2.app.corpus import MarkdownCorpusStore; ..."` | All 25 docs parsed, chunk count > 0, metadata preserved |
| `RAGRouter` | `python -c "from RAG V2.app.routing import RAGRouter; ..."` | RAG questions route to RAG, general questions to GENERAL |
| `ExtractionValidator` | `python -c "from RAG V2.app.validation import ExtractionValidator; ..."` | Corrupt docs rejected, valid docs accepted |
| `HybridRetriever` | `python scripts/test_rag_v2_retrieval.py "question"` | Chunks returned with scores > 0 |
| `LegalFeatureReranker` | `python -c "from RAG V2.app.reranker import ..."` | Top-K reranked, scores > 0 |
| `GeminiGroundedGenerator` | `python -c "from RAG V2.app.generation import ..."` | Answer generated with chunk_ids matching evidence |
| `AbstentionPolicy` | `python -c "from RAG V2.app.guardrails import ..."` | Insufficient evidence → abstain |
| Full benchmark | `python scripts/run_rag_v2_benchmark.py` | 25 questions, RAG_USED=True, persisted JSON |

### Frozen Benchmark

The 25 questions from `dataset/evaluation/phase16_rag_questions.json` are used unchanged. Results persisted to `dataset/evaluation/results/rag_v2_phase2_baseline.json`. Each question records route, rag_used, chunks_retrieved, evidence_sufficiency, grounded, citation_correct, abstained, and latency breakdown.

### No Dependency on V1 Backend

V2 has its own FastAPI app at `RAG V2/app/api/main.py`. It does not import from `ip-sakti-rag/app/`. It reads from `RAG V2/dataset_markdown/` and builds its own corpus index.

---

## 6. File Structure

```
RAG V2/
├── app/
│   ├── __init__.py
│   ├── api/
│   │   ├── __init__.py
│   │   └── main.py                          # FastAPI app with /health, /ask, /rag/query
│   ├── corpus/
│   │   ├── __init__.py
│   │   ├── markdown_parser.py               # Parse V2 .md files → chunks
│   │   └── markdown_store.py                # MarkdownCorpusStore
│   ├── routing/
│   │   ├── __init__.py
│   │   └── router.py                        # RAGRouter
│   ├── retrieval/
│   │   ├── __init__.py
│   │   ├── query_analysis.py               # V2-specific DOMAIN_TERMS, analyze_query()
│   │   ├── local_store.py                   # Copy from V1 (unchanged)
│   │   ├── hybrid.py                        # Copy from V1 (unchanged)
│   │   └── reranker.py                      # Copy from V1 (unchanged)
│   ├── generation/
│   │   ├── __init__.py
│   │   ├── grounded.py                      # Copy from V1 (unchanged)
│   │   └── context.py                       # Copy from V1 (unchanged)
│   ├── guardrails/
│   │   ├── __init__.py
│   │   └── policy.py                        # Copy from V1 (unchanged)
│   ├── legal_aliases.py                     # Copy from V1 (updated V2 docs)
│   ├── models/
│   │   ├── __init__.py
│   │   └── schemas.py                       # Extended with route, retrieval_status, request_id
│   ├── service.py                           # RAGService with explicit RAG routing
│   └── validation/
│       ├── __init__.py
│       └── extractor.py                     # ExtractionValidator
├── scripts/
│   ├── build_rag_v2_index.py                # Build corpus from markdown → JSONL
│   ├── test_rag_v2_retrieval.py             # Diagnostic retrieval test
│   └── run_rag_v2_benchmark.py              # Run frozen 25-question benchmark
├── dataset/
│   └── evaluation/
│       └── results/
│           └── rag_v2_phase2_baseline.json  # Persisted benchmark results
└── docs/
    └── RAG_V2_ARCHITECTURE_DECISION.md      # This document
```
