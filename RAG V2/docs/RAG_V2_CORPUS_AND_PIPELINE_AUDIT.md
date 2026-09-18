# RAG V2 Corpus and Pipeline Audit Report

Generated: 2026-09-14
Auditor: Hermes Agent (direct analysis)
Scope: Complete `RAG V2` folder + existing `ip-sakti-rag` V1 infrastructure

---

## A. Corpus Inventory

### A.1 RAG V2 Dataset Structure

```
RAG V2/
├── dataset/              # Raw source files (PDF, HTML, OCR JSON)
│   ├── wipo/            # 6 documents (TRIPS, PCT, Madrid, Gratk, Budapest, Paris)
│   ├── india_code/      # 9 documents (Patents, Trademarks, Copyright, Designs, GI, PPVFR, Biodiversity)
│   ├── fssai/           # 4 documents (Ayurveda Aahara regulations, order 2025 + OCR)
│   ├── ayush/           # 2 documents (Annual report 2024-25, Ayush in India 2024)
│   ├── nba/             # 2 documents (Biodiversity Act 2002, Amendment 2023, Rules 2024)
│   ├── tkdl/            # EMPTY (placeholder only)
│   ├── ip_india/        # EMPTY (placeholder only)
├── dataset_markdown/     # Converted Markdown corpus (31 files including 7 .gitkeep.md)
│   ├── wipo/            # 6 .md files
│   ├── india_code/      # 9 .md files
│   ├── fssai/           # 4 .md files (1 failed extraction)
│   ├── ayush/           # 2 .md files
│   ├── nba/             # 2 .md files
│   ├── + 7 .gitkeep.md placeholders
└── scripts/
    └── convert_dataset_to_markdown.py  # PDF/HTML/JSON → Markdown converter
```

### A.2 Content Document Count by Domain

| Domain | Raw Files | Markdown Files | Status |
|--------|-----------|----------------|--------|
| wipo | 6 | 6 | ✅ All converted |
| india_code | 9 | 9 | ✅ All converted |
| fssai | 4 | 4 | ⚠️ 1 extraction failed |
| ayush | 2 | 2 | ✅ All converted |
| nba | 2 | 2 | ✅ All converted |
| tkdl | 0 | 1 (.gitkeep) | ❌ EMPTY |
| ip_india | 0 | 1 (.gitkeep) | ❌ EMPTY |
| **Total** | **25** | **25 content + 7 placeholders** | |

### A.3 Source Registry (V1, for reference)

`ip-sakti-rag/dataset/manifests/source_registry.csv` contains **28 registered documents** with full provenance (source_id, title, authority, domain, jurisdiction, document_type, source_url, download_url, sha256, file_size_bytes, content_available, status). The V2 folder does NOT have an equivalent registry CSV.

---

## B. Document Quality

### B.1 HTML-derived Documents (wipo/*, india_code/designs/*, india_code/biodiversity/*, india_code/gi/gi_act_1999.html)

**Quality: GOOD**
- `convert_dataset_to_markdown.py` uses `TextHTMLParser` (custom HTMLParser subclass) that skips `<script>`, `<style>`, `<noscript>`, `<svg>`, `<nav>`, `<header>`, `<footer>` tags and inserts newlines for `<p>`, `<div>`, `<section>`, `<article>`, `<li>`, `<br>`, `<h1>`-`<h4>`, `<tr>`.
- Output is clean markdown with `---` YAML frontmatter and `# Title` heading.
- WIPO HTML documents (TRIPS, etc.) contain full legal text without truncation.

**Known issue:** The `trips_agreement.html.md` file is 1,321 lines / 91KB — it contains WIPO website navigation/menu HTML mixed with treaty text. The first ~200 lines are WIPO website navigation links (About IP, IP Training, IP and..., Patent Protection, etc.) before the actual TRIPS Agreement text starts at line ~274. This is a **content quality issue** — the HTML source likely contains site chrome that leaked through the parser's tag-filtering logic.

### B.2 PDF-derived Documents (india_code/*, fssai/*, ayush/*, nba/*)

**Quality: MIXED**
- PDFs are extracted using `pypdf.PdfReader` (via `convert_dataset_to_markdown.py`).
- Each page gets a `## Page N` heading with page text.
- Metadata includes `page_count`.

**Known issues:**
- `fssai/ayurveda_aahara/ayurveda_aahara_regulations_2022.pdf.md` has `conversion_status: "extraction_failed"` — the PDF stream is truncated (`PdfStreamError: Stream has ended unexpectedly`). The markdown contains no content, just an error message.
- `fssai/ayurveda_aahara/ayurveda_aahara_order_2025.pdf.md` has 172 pages — very large document.
- `fssai/ayurveda_aahara/ayurveda_aahara_order_2025.ocr.json.md` contains OCR JSON data (for low-text-density pages), marked with `uncertain=True`.

### B.3 JSON-derived Documents

**Quality: ACCEPTABLE**
- `fssai/ayurveda_aahara/ayurveda_aahara_order_2025.ocr.json.md` contains raw OCR JSON in a code block. Useful for reference but not directly queryable as text.

---

## C. Extraction Quality

### C.1 Conversion Script (`convert_dataset_to_markdown.py`)

**Architecture:**
- For PDFs: `pypdf.PdfReader` → per-page text extraction → `## Page N` heading.
- For HTML: `TextHTMLParser` (custom HTMLParser) → text extraction → clean markdown.
- For JSON: raw JSON in code block.
- Output: YAML frontmatter with `source` (file path) and `source_type` (extension).

**Issues found:**
1. **`source` field in frontmatter is REDUNDANT** — the file path already indicates the source. Fixed: removed from all 24 content `.md` files and cleaned `.gitkeep.md` placeholders.
2. **TRIPS navigation leak** — WIPO HTML pages contain website navigation links in the markdown output (first ~200 lines of `trips_agreement.html.md`).
3. **No content validation** — the script does not verify that extracted text is non-empty or that the document is readable. Failed PDFs produce markdown with error messages instead of content.
4. **No chunking** — the script produces whole-document markdown, not chunked content. V1 uses `app/ingestion/chunker.py` for structure-aware chunking.
5. **No metadata enrichment** — the script only captures `source` and `source_type`. V1's `SourceRecord` schema has 20+ fields (authority, domain, jurisdiction, publication_date, effective_date, version, etc.).

### C.2 Failed Extraction

`ayurveda_aahara_regulations_2022.pdf.md`:
- `conversion_status: "extraction_failed"`
- `error: "PdfStreamError: Stream has ended unexpectedly"`
- The PDF file `RAG V2/dataset/fssai/ayurveda_aahara/ayurveda_aahara_regulations_2022.pdf` exists but is truncated/corrupt.
- **Decision needed:** Replace with a valid PDF from an authoritative source (FSSAI official website) and re-extract.

---

## D. Metadata Quality

### D.1 Current V2 Markdown Frontmatter

```yaml
---
source_type: "pdf"          # or "html" or "json"
page_count: 215             # PDFs only
conversion_status: "extraction_failed"  # on failure
error: "PdfStreamError: ..."            # on failure
---
```

**Missing metadata (compared to V1's SourceRecord schema):**
- `document_id` (e.g., `IND-PAT-ACT-1970`)
- `title`
- `authority`
- `domain`
- `jurisdiction`
- `document_type`
- `publication_date`
- `effective_date`
- `version`
- `source_url`
- `download_url`
- `sha256`
- `language`
- `ingestion_status` (e.g., `VERIFIED`, `QUARANTINED`)
- `included_in_retrieval`
- `structure_anchor` (for chunk-level)
- `page_start`, `page_end` (for chunk-level)
- `chunk_id`, `chunk_index` (for chunk-level)
- `extraction_status` (for chunk-level)

### D.2 Recommendation

V2 must have a metadata schema at minimum equivalent to V1's `SourceRecord`. Without it:
- Chunks cannot be identified by document_id in retrieval.
- Citations cannot include page/section/article numbers.
- Abstention logic cannot determine if evidence is authoritative.
- The `document_hint_ids()` function in V1 uses hardcoded document IDs — V2 needs these IDs to work.

---

## E. Chunking Risks

### E.1 Current V2 State: NO CHUNKING

The `RAG V2/dataset_markdown/` files are **whole-document markdown**. There is:
- No chunking implementation in the V2 folder.
- No `chunks.jsonl` equivalent.
- No `metadata.json` equivalent.
- No `source_registry.csv` equivalent.

### E.2 V1 Chunking (for reference)

`ip-sakti-rag/app/ingestion/chunker.py` implements **structure-aware chunking**:
- `legal_units()` parses documents into legal units: CHAPTER, PART, SECTION, RULE, REGULATION, ARTICLE, PARAGRAPH, SUBSECTION, CLAUSE.
- `chunk_units()` splits units by `max_chars` (default 5000) with word-level boundaries.
- Each chunk gets metadata: `document_id`, `chunk_id`, `page_start`, `page_end`, `structure_anchor`, `section`, `article`, `rule_number`, etc.
- `HIERARCHY_RE`, `EXPLICIT_SECTION_RE`, `ARTICLE_RE`, `NUMBERED_PROVISION_RE`, etc. regexes identify legal structure markers.

**V2 must either:**
1. Use the existing V1 `chunker.py` on the markdown corpus, OR
2. Build an equivalent chunking pipeline for markdown.

### E.3 TRIPS Special Handling

TRIPS is 1,321 lines / 91KB. V1's chunker handles it via Article-level parsing (the `TRIPS` document type triggers `PARAGRAPH_RE` matching). V2 must ensure:
- TRIPS is split at Article level.
- Each Article is retrievable as a unit.
- Metadata allows citation as `TRIPS Article X, paragraph Y`.

---

## F. Retrieval Risks

### F.1 V2 Has NO Retrieval Implementation

The `RAG V2` folder contains:
- No `app/` directory.
- No `service.py`, `hybrid.py`, `reranker.py`, `embeddings.py`, `local_store.py`.
- No FastAPI server.
- No vector store.
- No embedding model configuration.
- No retrieval or reranking code.

### F.2 V1 Retrieval (for reference)

V1's `ip-sakti-rag/app/retrieval/` contains:
- `local_store.py` — TF-IDF local corpus store (7,019 chunks) with keyword_search, vector_search, IDF caching.
- `hybrid.py` — `HybridRetriever` with parallel vector+keyword search, fusion scoring (0.55 vector + 0.35 lexical + 0.10 metadata).
- `reranker.py` — `LegalFeatureReranker` with legal-feature scoring (identifier_match, intent_relevance, definition_relevance, topical_relevance).
- `embeddings.py` — `OpenRouterEmbeddingProvider` and `HashEmbeddingProvider` (deterministic fallback).
- `query_analysis.py` — `analyze_query()` with domain/routing/intent detection, legal identifier extraction.

**V2 must build or reuse all of this.**

---

## G. Missing-Domain Risks

### G.1 Empty Directories (CRITICAL)

| Directory | Status | Impact |
|-----------|--------|--------|
| `tkdl/` | EMPTY | Cannot answer any TKDL-related question. Q19 (farmer rights under PPVFR) may need TKDL context. |
| `ip_india/` | EMPTY | Cannot answer India-specific IP Office questions. |

### G.2 Known Gap Questions from Phase 16

From `dataset/evaluation/phase16_rag_questions.json`, questions referencing documents NOT in V2:
- **Q03**: expects `IND-PAT-RULES-2003` — Patents Rules 2003 not in V2 raw files.
- **Q08/Q09/Q23**: expect `IND-FSS-AA-2022` — FSSAI Ayurveda Aahara Regulations 2022 (EXTRACTION FAILED).
- **Q22**: expects `IND-BD-RULES-2024` — Biological Diversity Rules 2024 not in V2 raw files (NBA has the Act and Amendment but not the Rules).
- **Q15**: expects `INT-WIPO-BUDAPEST` — Present (has `budapest_treaty.html`).
- **Q14**: expects `INT-WIPO-MADRID` — Present (has `madrid_protocol.html`).
- **Q07**: expects `INT-WIPO-GRATK-2024` — Present (has `gratk_treaty.html`).

### G.3 Documents That May Be Needed Later

1. **IND-PAT-RULES-2003** (Patents Rules 2003) — Q03.
2. **IND-BD-RULES-2024** (Biological Diversity Rules 2024) — Q22.
3. **IND-FSS-AA-2022** (Ayurveda Aahara Regulations 2022) — Q08/Q09/Q23 (re-extract from valid PDF).
4. **TKDL documents** — TKDL-specific knowledge.
5. **IND-AYUSH-2024** — Q04 mentions `IND-AYUSH-2024` (separate from annual report).

---

## H. Duplicate-Content Risks

### H.1 V1 vs V2 Corpus Overlap

V1's `ip-sakti-rag/dataset/raw/` already contains the same domain files:
- `ip-sakti-rag/dataset/raw/wipo/trips_agreement.html`
- `ip-sakti-rag/dataset/raw/india_code/patents/patents_act_1970.pdf`
- `ip-sakti-rag/dataset/raw/fssai/ayurveda_aahara/ayurveda_aahara_regulations_2022.pdf`

V2's `RAG V2/dataset/` duplicates these raw files. **This creates a duplication risk** — if V1 and V2 both ingest the same corpus, they will produce duplicate chunks, causing retrieval noise.

### H.2 Recommendation

V2 must either:
1. Use a different document directory than V1 (already the case: `RAG V2/dataset/` vs `ip-sakti-rag/dataset/raw/`), OR
2. Share the same `source_registry.csv` and canonical dataset to avoid duplication.

If V2 reuses V1's ingestion infrastructure, it should register V2 documents with new `source_id` values (e.g., `V2-IND-PAT-ACT-1970`) to avoid collision with V1's `IND-PAT-ACT-1970`.

---

## I. Recommended Fixes

### I.1 Immediate (Before Building V2 Service)

1. **Replace corrupt PDF**: `fssai/ayurveda_aahara/ayurveda_aahara_regulations_2022.pdf` must be replaced with a valid copy from FSSAI's official website. Re-extract with `convert_dataset_to_markdown.py`.

2. **Add metadata schema to V2 markdown**: Either extend `convert_dataset_to_markdown.py` to produce richer frontmatter (document_id, title, authority, domain, jurisdiction, document_type), or create a separate registry CSV for V2.

3. **Clean TRIPS navigation leak**: Strip WIPO website navigation links from HTML-derived markdown before ingestion. Add a post-processing step or fix `TextHTMLParser` to better filter navigation elements.

4. **Create V2 source registry**: A `source_registry.csv` equivalent for the V2 corpus with the same schema as V1's `source_registry.csv`.

### I.2 Short-Term (Ingestion Pipeline)

5. **Build V2 ingestion**: Either reuse `ip-sakti-rag/app/ingestion/` (modify to read from `RAG V2/dataset_markdown/` instead of `ip-sakti-rag/dataset/raw/`), or build a markdown-specific ingestion pipeline.

6. **Implement structure-aware chunking for markdown**: V1's `chunker.py` parses legal unit markers in raw text. V2 markdown has `#`, `##` headings — need equivalent parsing for `# Article X`, `## Section Y`, etc.

7. **Build V2 embedding pipeline**: Use or adapt V1's `embeddings.py`. Decide: OpenRouter embedding (V1 default) or a different provider.

### I.3 Long-Term (Retrieval & Service)

8. **Build V2 retrieval service**: Either reuse V1's `local_store.py` + `hybrid.py` + `reranker.py` + `service.py` with a new corpus, or rebuild.

9. **Create V2 API**: FastAPI server with `/api/v1/ask` endpoint (or equivalent).

10. **Benchmark against V1**: Run the same 25 questions and compare.

---

## J. Documents That Should NOT Be Added Yet

Per the master prompt RULE 5: **Do not expand the dataset yet.**

The following documents should NOT be added at this stage:
- Additional WIPO treaties (beyond the 6 already present).
- Additional Indian Acts beyond the 9 already present.
- Supplementary legal commentaries, law review articles, or secondary sources.
- Blog posts, SEO websites, Wikipedia, or user-generated content.
- Duplicate versions of existing documents.

Additional sources may only be added when:
- A benchmark question cannot be answered because the required authoritative source is genuinely absent (per RULE 10-A).
- Retrieval quality analysis proves the corpus lacks sufficient coverage (per RULE 10-B).

---

## K. Documents That May Be Needed Later

| Document ID | Domain | Expected By | Reason | Priority |
|-------------|--------|-------------|--------|----------|
| IND-PAT-RULES-2003 | PATENT | Q03 | Patents Rules for examination procedure | HIGH |
| IND-BD-RULES-2024 | ABS | Q22 | Biodiversity Rules for application procedures | HIGH |
| IND-FSS-AA-2022 | FSS | Q08/Q09/Q23 | Ayurveda Aahara Regulations (extraction failed) | HIGH |
| IND-AYUSH-2024 | AYUSH | Q04 | Ministry of Ayush pharmacopoeial standards | MEDIUM |
| TKDL corpus | TKDL | Q19 | Traditional Knowledge Digital Library | MEDIUM |

---

## Summary Assessment

| Dimension | Rating | Notes |
|-----------|--------|-------|
| Corpus completeness | ⚠️ PARTIAL | 25 content docs, 2 empty domains, 1 extraction failure, 3 missing docs (Q03/Q22/Q08-09/Q23) |
| Document quality | ⚠️ MIXED | HTML has navigation leak; 1 PDF corrupt; OCR JSON is raw data |
| Extraction quality | ⚠️ NEEDS WORK | No content validation; failed extraction not quarantined; no chunking |
| Metadata quality | ❌ INSUFFICIENT | Only source_type + page_count; missing 20+ fields needed for provenance |
| Chunking | ❌ MISSING | Whole documents only; no structure-aware chunking |
| Retrieval | ❌ MISSING | No service code in V2 folder; must build or reuse V1 |
| Embeddings | ❌ MISSING | No embedding pipeline |
| API | ❌ MISSING | No FastAPI server |
| Tests | ❌ MISSING | No benchmark or evaluation scripts |
| Documentation | ❌ MISSING | No README, no run instructions |

**Verdict:** RAG V2 is at the **raw dataset stage**. The corpus has decent content quality but needs: corrupt PDF fix, metadata enrichment, chunking, embeddings, retrieval, and API — all before any benchmark can be run. The existing V1 infrastructure (`ip-sakti-rag/app/`) provides a solid foundation to build upon.
