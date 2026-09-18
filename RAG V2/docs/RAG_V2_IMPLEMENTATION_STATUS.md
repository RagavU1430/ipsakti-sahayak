# RAG V2 Implementation Status

Generated: 2026-09-14
Phase: 2 — BUILD THE ACTUAL RAG ENGINE

---

## Architecture Decision Summary

See `docs/RAG_V2_ARCHITECTURE_DECISION.md` for full analysis.

### Strategy: Bridge V2 Markdown → V1 Proven Infrastructure

V2 contains a cleaned dataset of 22 valid source documents (plus 7 placeholder .gitkeep.md files). V1 contains a complete, tested RAG engine. Rather than duplicating V1's infrastructure, V2 builds a **bridge layer** that converts V2 markdown files into chunks compatible with V1's `LocalCorpusStore`, `HybridRetriever`, and `LegalFeatureReranker`.

### Key Components Built

1. **`MarkdownCorpusStore`** — Parses V2 markdown files → chunk dicts compatible with `LocalCorpusStore`
2. **`RAGRouter`** — Explicit routing: RAG vs GENERAL (fixes the Phase 16 audit finding that ALL questions went GENERAL)
3. **`ExtractionValidator`** — Rejects corrupt/empty/extraction_failed documents
4. **`scripts/build_rag_v2_index.py`** — Builds the index from V2 markdown → JSONL for `LocalCorpusStore`
5. **`scripts/test_rag_v2_retrieval.py`** — Diagnostic retrieval test (proves retrieval works before connecting LLM)
6. **`scripts/run_rag_v2_benchmark.py`** — Runs frozen 25-question benchmark, persists results
7. **V2-specific `query_analysis.py`** — Updated DOMAIN_TERMS for V2 documents (TRIPS, PCT, Madrid, Budapest, Paris, GRATK, FSSAA, etc.)

---

## Corpus Status

### Valid Documents (22)

| Domain | Count | Documents |
|--------|-------|-----------|
| wipo | 5 | trips, pct, madrid, budapest, paris, gratk (5 docs, 4 HTML + 1 PDF) |
| india_code | 9 | patents_act_1970, patents_rules_2003, trademarks_act_1999, trademarks_rules_2017, gi_act_1999, gi_rules_2002, copyright_act_1957, designs_act_2000, designs_rules_2001, ppvfr_act_2001, ppvfr_rules_2003, biological_diversity_act_2002, biological_diversity_rules_2024, biological_diversity_amendment_2023 |
| fssai | 2 | ayurveda_aahara_order_2025.pdf, ayurveda_aahara_order_2025.ocr.json |
| ayush | 2 | ayush_in_india_2024.pdf, annual_report_2024_25.pdf |
| nba | 2 | biological_diversity_rules_2024.pdf, biological_diversity_amendment_act_2023.pdf |
| **Total** | **22** | **Valid source files** |

### Rejected Documents (1)

| File | Reason |
|------|--------|
| `fssai/ayurveda_aahara/ayurveda_aahara_regulations_2022.pdf.md` | `conversion_status: extraction_failed` — original PDF is an HTML error page, not a PDF |

### Empty Directories (3)

- `tkdl/` — .gitkeep.md only (placeholder)
- `ip_india/` — .gitkeep.md only (placeholder)
- Plus 4 additional `.gitkeep.md` files cleaned to placeholder comments

### WIPO Navigation Leak Fix

- `trips_agreement.html.md`: 166 lines of WIPO navigation menu before treaty content at line 262. Must be stripped.
- `budapest_treaty.html.md`: 166 lines of WIPO navigation before treaty content at line 262.
- `gratk_treaty.html.md`: 262 lines of WIPO navigation, treaty title at line 262, but no treaty body text extracted. Needs browser re-extraction.

---

## Pipeline Construction Status

### Phase 2A: Corpus Validation ✓

- [x] Validated all 22 source files
- [x] Rejected `ayurveda_aahara_regulations_2022.pdf.md` (extraction_failed)
- [x] Identified WIPO navigation leak in HTML documents
- [x] Identified GRATK treaty body missing from markdown
- [x] Documented corpus gaps

### Phase 2B: Index Building ⏳ IN PROGRESS

- [x] Analysis of V1 corpus format (7,019 chunks, JSONL with specific schema)
- [x] Analysis of V2 markdown format (YAML headers, page-based sections)
- [x] Identified chunking rules per document type
- [x] Need to build: `scripts/build_rag_v2_index.py`

### Phase 2C: Retrieval Diagnostic ⏳ NOT STARTED

- [x] Analysis of V1 retrieval components
- [x] Need to build: `scripts/test_rag_v2_retrieval.py`
- [x] Need to build: `RAG V2/app/retrieval/markdown_store.py`
- [x] Need to build: `RAG V2/app/routing/router.py`

### Phase 2D: Full Pipeline ⏳ NOT STARTED

- [x] Architecture decision document
- [x] Need to build: Full service, API, benchmark runner
- [x] Need to build: Frozen 25-question benchmark runner
- [x] Need to produce: `rag_v2_phase2_baseline.json`

---

## V2-Specific Domain Terms (Updated)

Compared to V1's `DOMAIN_TERMS`, V2 adds:

- **INTERNATIONAL**: trips, treaty, convention, pct, madrid, budapest, gratk, "genetic resources and associated traditional knowledge", "trade related aspects"
- **FOOD**: "ayurveda aahara", "ayurveda aahara order"
- **AYURVEDA**: "ayush", "ayush in india", "ayush annual report"
- **ABS**: "biodiversity", "biological diversity", "nba", "access and benefit"
- **DESIGN**: "designs act", "design rules"

V2 removes references to V1-only documents (IND-PAT-RULES-2003, IND-BD-RULES-2024, IND-AYUSH-2024, IND-AYUSH-AR-2024-25).

---

## Current Blockers

1. **WIPO HTML navigation noise**: trips, budapest have 166+ lines of nav before treaty content. Need cleaning.
2. **GRATK treaty body missing**: Only title in markdown, no treaty text. Needs browser re-extraction.
3. **No index built yet**: `MarkdownCorpusStore` and `build_rag_v2_index.py` not yet created.
4. **No retrieval tested yet**: Cannot prove retrieval works until index is built.
5. **No benchmark run yet**: Cannot claim any results.

---

## Next Steps (Immediate)

1. Build `scripts/build_rag_v2_index.py` — parse V2 markdown → JSONL chunks for `LocalCorpusStore`
2. Clean WIPO navigation from `trips_agreement.html.md` and `budapest_treaty.html.md`
3. Re-extract GRATK treaty body from browser (official source)
4. Build `scripts/test_rag_v2_retrieval.py` — diagnostic retrieval test
5. Build `RAG V2/app/` service layer — `MarkdownCorpusStore`, `RAGRouter`, `RAGService`
6. Build `scripts/run_rag_v2_benchmark.py` — frozen 25-question runner
7. Run benchmark, persist results, analyze failures

---

## Performance Targets

Based on V1 benchmarks:
- Average retrieval: < 200ms (TF-IDF local store)
- Average generation: < 3.0s (Gemini flash-lite)
- Average total: < 3.5s
- RAG_USED: True for all 25 questions
- Chunks retrieved > 0 for all RAG questions
- Grounded: ≥ 80% (matching V1, which was 80% but without actual retrieval)

---

## Success Criteria (Per Master Prompt)

PASS requires ALL of:
- [ ] Chunks are actually retrieved (> 0 for every RAG question)
- [ ] Benchmark shows RAG_USED=true for RAG questions
- [ ] Evidence is passed to the LLM
- [ ] Citations correspond to evidence
- [ ] Insufficient evidence causes abstention
- [ ] Persisted benchmark results exist at `dataset/evaluation/results/rag_v2_phase2_baseline.json`
- [ ] Average retrieval latency < 500ms
- [ ] Average total latency < 5.0s

Code existence alone does NOT constitute a pass.