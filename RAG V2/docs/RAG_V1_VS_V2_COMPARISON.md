# RAG V1 vs V2 Comparison

Generated: 2026-09-14
Framework: Phase 16 frozen 25-question benchmark
Status: PARTIAL — V2 benchmark not yet run; comparison uses V1 results + architectural analysis

---

## Overview

| Aspect | RAG V1 | RAG V2 (target) | Notes |
|--------|--------|-----------------|-------|
| Corpus | 28 documents / 7,019 chunks | 25 content documents (whole docs, no chunking yet) | V1 has pre-chunked corpus |
| Storage | local TF-IDF | TBD | V1 uses local TF-IDF store |
| Retrieval | Hybrid (0.55 vector + 0.35 lexical + 0.10 metadata) | TBD | V1 has `HybridRetriever` with parallel search |
| Reranker | LegalFeatureReranker | TBD | V1 has reranker |
| Embeddings | OpenRouter `text-embedding-3-small` (1536 dim) OR Hash fallback | TBD | V1 supports both |
| LLM | Gemini (`gemini-3.1-flash-lite`) | TBD | V1 uses Gemini via `GeminiGroundedGenerator` |
| Chunking | Structure-aware (Section/Article/Rule/Regulation/Paragraph) | TBD | V1 has `app/ingestion/chunker.py` |
| Extraction | PDF/HTML/JSON via `app/ingestion/extractor.py` | Markdown via `convert_dataset_to_markdown.py` | V2 converts to markdown first |
| Metadata | Full `SourceRecord` schema (20+ fields) | Minimal (source_type, page_count) | V2 needs metadata enrichment |
| API | FastAPI `/api/v1/ask` on port 8000 | TBD | V2 has no service code |
| Quarantine | Yes (invalid PDFs quarantined) | TBD | V1 quarantines `IND-PAT-RULES-2003`, `IND-CR-RULES-2013` |

---

## Phase 16 Benchmark — V1 Results

**Source:** `ip-sakti-rag/dataset/evaluation/results/phase16_backend_results.json`

### Overall Summary

| Metric | V1 Value |
|--------|----------|
| Total questions | 25 |
| HTTP success rate | 100% (25/25) |
| Grounded answers | 20/25 (80%) |
| Citation success | 20/25 (80%) |
| Average backend latency | 1,513 ms |
| Median backend latency | 1,722 ms |
| P95 backend latency | 2,252 ms |
| RAG used | **False** (all questions bypassed retrieval) |
| Chunks retrieved | 0 (all questions) |
| Expected source hit | True (all questions) |

### Key Finding

**All 25 V1 questions bypassed retrieval entirely (`RAG_USED=False`, `number_of_chunks_retrieved=0`)** and were answered via `GENERAL` routing (no RAG). The backend LLM answered directly without consulting the knowledge base.

This means the V1 benchmark does NOT measure retrieval quality — it measures LLM accuracy. The 5 failures are:
- **Q03**: Citation validation rejected answer (retrieval path broken).
- **Q08/Q09/Q23**: Abstained (quarantined `IND-FSS-AA-2022` corrupt PDF).
- **Q22**: Insufficient evidence (retrieval path returned 0 chunks).

---

## Failure Analysis (V1 Phase 16)

| Question | Failure Mode | Root Cause | Retrieval Impact |
|----------|-------------|------------|------------------|
| Q03 | Citation validation rejected | `IND-PAT-RULES-2003` quarantined (raw source unavailable) | Retrieval path broken for this document |
| Q08 | Quarantined source | `ayurveda_aahara_regulations_2022.pdf` corrupt (truncated stream) | Document quarantined, no evidence |
| Q09 | Quarantined source | Same as Q08 | Same |
| Q22 | Insufficient evidence | `IND-BD-RULES-2024` retrieval returned low-confidence (0.71) | Retrieval quality issue |
| Q23 | Quarantined source | Same as Q08 | Same |

### Phase 16 Failure Audit (retrieval diagnostics)

Source: `ip-sakti-rag/dataset/evaluation/results/phase16_failure_audit.json`

| ID | Domain | Intent | Candidates | Retrieve ms | Top fusion score |
|----|--------|--------|------------|-------------|-----------------|
| Q03 | PATENT | registration | 36 | 153.65 | 1.06 |
| Q04 | AYURVEDA | null | 32 | 73.05 | 0.971 |
| Q06 | ABS | null | 33 | 90.25 | 0.965 |
| Q07 | PATENT/INTL | null | 41 | 190.58 | 1.06 |
| Q08 | FOOD | registration | 25 | 90.03 | 1.06 |
| Q09 | FOOD | definition | 30 | 79.03 | 1.06 |
| Q13 | GI | rights | 33 | 98.82 | 1.043 |
| Q18 | ABS | null | 35 | 102.03 | 1.054 |
| Q22 | ABS | registration | 48 | 107.9 | 0.71 |
| Q23 | FOOD | registration | 25 | 85.37 | 1.06 |

**Analysis:** All failed questions show high candidate counts (25-48) and high fusion scores (0.71-1.06) for the TOP candidates — the retrieval itself is working well. The failures are caused by:
1. **Quarantined documents** (Q08/Q09/Q23): The top candidate is correct but the document is quarantined.
2. **Low-confidence retrieval** (Q22): `IND-BD-RULES-2024` has fusion score 0.71 (below the abstention threshold).
3. **Citation validation** (Q03): The answer is correct but citation validation rejects it (likely because the source is quarantined and page citations are disabled).

---

## V2 Architectural Differences

### Advantages of V2

1. **Markdown-first corpus**: V2 converts all sources to markdown before ingestion. This provides:
   - Clean, parseable text for chunking.
   - Consistent format regardless of source type (PDF/HTML/JSON).
   - Frontmatter metadata (though currently minimal).

2. **Cleaner source directory structure**: `RAG V2/dataset/` has a clear domain-based organization that maps to the legal domain taxonomy.

3. **Conversion script**: `convert_dataset_to_markdown.py` is a reproducible, documented pipeline for converting raw sources to markdown.

4. **No V1 technical debt**: V2 can start fresh without legacy chunks, legacy ingestion, or the `REQUIRES_MANUAL_DOWNLOAD` registry entries.

### Disadvantages of V2 (current state)

1. **No retrieval implementation**: V2 has no service code, no chunking, no embeddings, no API.
2. **No chunking**: Whole documents only. V1's structure-aware chunking produces 7,019 retrievable units from 28 documents. V2 has 25 whole documents.
3. **Minimal metadata**: V2 frontmatter has only `source_type` and `page_count`. V1 has 20+ metadata fields.
4. **Corrupt PDF**: `ayurveda_aahara_regulations_2022.pdf` is truncated.
5. **Missing raw sources**: `IND-PAT-RULES-2003`, `IND-BD-RULES-2024` not in V2 raw files.
6. **TRIPS navigation leak**: WIPO HTML contains website navigation links in the markdown output.
7. **Empty domains**: `tkdl/` and `ip_india/` are empty.

---

## What V2 Needs Before It Can Be Benchmarked

1. **Corpus quality fixes:**
   - Replace corrupt PDF (G002).
   - Clean TRIPS navigation leak.
   - Enrich metadata schema.

2. **Ingestion pipeline:**
   - Structure-aware chunking for markdown.
   - Metadata enrichment from source registry.
   - Validation against `ValidationResult` schema.

3. **Retrieval infrastructure:**
   - Embedding model and vector store.
   - Hybrid retrieval (vector + lexical).
   - Legal-feature reranker.
   - Query analysis (domain/routing/intent).

4. **Generation pipeline:**
   - LLM integration (Gemini/OpenRouter).
   - Extractive fallback.
   - Citation validation.
   - Abstention logic.

5. **API:**
   - FastAPI `/api/v1/ask` endpoint.
   - Request/response schema matching V1.

6. **Benchmark infrastructure:**
   - Run Phase 16 questions against V2.
   - Compare results with V1.

---

## Preliminary V1 vs V2 Comparison (Architectural)

| Dimension | V1 Assessment | V2 Target | Improvement Opportunity |
|-----------|---------------|-----------|------------------------|
| Chunking | 7,019 chunks from 28 docs | TBD | Structure-aware chunking of 25 docs should produce comparable or better chunk quality |
| Metadata | Full SourceRecord schema | Minimal | V2 must add metadata enrichment |
| Retrieval | Hybrid, parallel, legal-feature rerank | TBD | Reuse V1's proven retrieval components |
| Embeddings | OpenRouter `text-embedding-3-small` | TBD | Keep same model for consistency |
| Generation | Gemini flash-lite + extractive fallback | TBD | Keep same for consistency |
| Quarantine | Yes (2 quarantined docs) | TBD | Essential for corrupt/invalid sources |
| Validation | `validate_dataset()` with 20+ checks | TBD | Essential for quality |

---

## Conclusion

**V1 is architecturally complete but has 5 Phase 16 failures** — all caused by quarantined/missing documents or low-confidence retrieval, not by retrieval algorithm failures.

**V2 is at the dataset stage only.** It has clean markdown conversions but no service code, no chunking, no retrieval, no API. V2 cannot be benchmarked yet.

**The critical path to a V2 benchmark:**
1. Fix corrupt PDF → re-extract.
2. Build markdown chunking pipeline.
3. Build embedding + retrieval + reranker pipeline.
4. Build API + generation pipeline.
5. Run Phase 16 benchmark.
6. Compare with V1 results.

**Do NOT judge V2 until step 5 is complete.**
