# RAG V2 Implementation Guide

Generated: 2026-09-14
Status: IN_PROGRESS — V2 has no service code yet
Based on: V1 infrastructure at `ip-sakti-rag/app/`

---

## Architecture Overview

V2 reuses V1's proven components where possible and adds a markdown-first ingestion layer.

```
┌─────────────────────────────────────────────────────────────┐
│                     API LAYER (FastAPI)                      │
│  POST /api/v1/ask  →  RAGService.query()  →  AskResponse   │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                  SERVICE LAYER (RAGService)                  │
│  query() → analyze_query → retrieve → rerank → generate      │
│  → validate -> cite/abstain                                 │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                RETRIEVAL LAYER                               │
│  QueryAnalysis -> HybridRetriever (parallel) -> LegalFeatRe │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│              INGESTION LAYER (NEW for V2)                    │
│  Markdown -> StructureChunker -> Embedder -> VectorStore     │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│              CORPUS (RAG V2/dataset_markdown/)               │
│  25 markdown documents, domain-organized                     │
└─────────────────────────────────────────────────────────────┘
```

---

## Step 1: Fix Corpus Quality

### 1.1 Replace corrupt PDF

**File:** `RAG V2/dataset/fssai/ayurveda_aahara/ayurveda_aahara_regulations_2022.pdf`

**Action:** Download from FSSAI official source and replace the truncated file.

**Source:** `https://www.fssai.gov.in/` → Food Safety and Standards (Food Products and Food Advertising) Regulations, 2022.

**Verification:** After replacement, run `RAG V2/scripts/convert_dataset_to_markdown.py` and verify `conversion_status` is no longer `extraction_failed`.

### 1.2 Clean TRIPS navigation leak

**File:** `RAG V2/dataset_markdown/wipo/trips_agreement.html.md`

**Action:** Strip WIPO website navigation lines (lines 1-273 approximately) from the markdown, keeping only the actual TRIPS Agreement text starting from "Agreement on Trade-Related Aspects of Intellectual Property Rights".

**Option A:** Modify `convert_dataset_to_markdown.py` to add a WIPO-specific filter.
**Option B:** Post-process the existing markdown to remove navigation lines.

---

## Step 2: Add Metadata Schema

### 2.1 Document-level metadata

Add a `RAG V2/dataset_markdown/source_registry.csv` with the same schema as V1's `ip-sakti-rag/dataset/manifests/source_registry.csv`:

```csv
source_id,title,short_title,authority,domain,subdomain,jurisdiction,country,document_type,source_url,download_url,language,publication_date,enactment_date,effective_date,version,status,access_type,license_notes,local_path,retrieved_at,sha256,file_size_bytes,content_available,notes
```

For each of the 25 documents, determine:
- `source_id` (e.g., `IND-PAT-ACT-1970`, `INT-WIPO-TRIPS-1994`)
- `document_id` mapping (must match V1's naming convention for consistency)
- `domain` (PATENT, TRADEMARK, COPYRIGHT, GI, PLANT_VARIETY, ABS, FOOD, AYURVEDA, INTERNATIONAL)
- `document_type` (ACT, RULES, REGULATION, TREATY, HTML, PDF)
- `jurisdiction` (INDIA or INTERNATIONAL)
- `authority` (Government of India / IP India, WIPO, etc.)
- `source_url`, `download_url`
- `status` (VERIFIED, QUARANTINED, etc.)

### 2.2 Chunk-level metadata (after chunking)

Each chunk will have:
```json
{
  "chunk_id": "IND-PAT-ACT-1970-0001-a814667ecdf7",
  "document_id": "IND-PAT-ACT-1970",
  "document_title": "The Patents Act, 1970",
  "domain": "PATENT",
  "jurisdiction": "INDIA",
  "document_type": "ACT",
  "page_start": 1,
  "page_end": 1,
  "section": "1",
  "structure_type": "SECTION",
  "structure_anchor": true,
  "chunk_index": 1,
  "extraction_status": "VERIFIED",
  "source_reference": "IND-PAT-ACT-1970.pdf#page=1"
}
```

---

## Step 3: Build Markdown Chunking Pipeline

### 3.1 Approach

Reuse V1's `app/ingestion/chunker.py` logic but adapt it for markdown input. The markdown files have:
- YAML frontmatter (metadata)
- `# Title` headings (document title)
- `## Page N` headings (PDF page markers)
- `###` or other `##` subheadings (section/article markers)
- Numbered paragraphs (`1.`, `2.`, etc.)
- Legal markers: `Article X`, `Section Y`, `Rule Z`, `Regulation W`

### 3.2 Chunking Strategy

```python
# Pseudocode for markdown chunking
def chunk_markdown(md_path: Path) -> list[Chunk]:
    frontmatter = parse_yaml_frontmatter(md_path)
    text = strip_frontmatter(md_path)
    document_type = classify_document(frontmatter)  # ACT, RULES, TREATY, etc.
    
    # Parse legal units using V1's chunker regexes
    lines = text.splitlines()
    units = []
    current_unit = None
    
    for line in lines:
        if is_heading(line):
            if current_unit: units.append(current_unit)
            current_unit = create_unit(line, document_type)
        elif is_legal_marker(line, document_type):
            if current_unit: units.append(current_unit)
            current_unit = create_unit_from_marker(line, document_type)
        else:
            current_unit.add_text(line)
    
    if current_unit: units.append(current_unit)
    
    # Split oversized units
    chunks = []
    for unit in units:
        chunks.extend(split_by_chars(unit, max_chars=5000))
    
    return chunks
```

### 3.3 Document Classification

Map each document to a `document_type` for chunking:

| Document | document_type | Chunking Strategy |
|----------|---------------|-------------------|
| patents_act_1970.pdf | ACT | Section-level |
| trade_marks_act_1999.pdf | ACT | Section-level |
| copyright_act_1957.pdf | ACT | Section-level |
| trade_marks_rules_2017.pdf | RULES | Rule/sub-rule |
| gi_act_1999.html | ACT | Section-level |
| gi_rules_2002.pdf | RULES | Rule/sub-rule |
| trips_agreement.html | TREATY | Article/paragraph |
| pct.pdf | TREATY | Article/paragraph |
| parisi_convention.pdf | TREATY | Article/paragraph |
| madrid_protocol.html | TREATY | Article/paragraph |
| gratk_treaty.html | TREATY | Article/paragraph |
| budapest_treaty.html | TREATY | Article/paragraph |
| ayurveda_aahara_order_2025.pdf | REGULATION | Regulation/section |
| ayurveda_aahara_regulations_2022.pdf | REGULATION | Regulation/section |
| ayush_in_india_2024.pdf | REPORT | Chapter/section |
| annual_report_2024_25.pdf | REPORT | Chapter/section |
| biological_diversity_act_2002.html | ACT | Section-level |
| biological_diversity_amendment_2023.pdf | AMENDMENT | Section-level |
| biological_diversity_rules_2024.pdf | RULES | Rule/sub-rule |
| designs_act_2000.html | ACT | Section-level |
| designs_rules_2001.html | RULES | Rule/sub-rule |
| patents_act_1970.pdf | ACT | Section-level |

### 3.4 Reuse V1 Chunker

The existing `app/ingestion/chunker.py` provides:
- `HIERARCHY_RE`, `EXPLICIT_SECTION_RE`, `ARTICLE_RE`, `NUMBERED_PROVISION_RE`, etc. regexes.
- `legal_units()` function that parses pages into `Unit` objects with metadata.
- `chunk_units()` function that splits units by `max_chars`.
- `_blank_metadata()` for default chunk metadata.

For V2, create a markdown-specific variant that:
1. Parses YAML frontmatter for document metadata.
2. Uses markdown headings (`#`, `##`, `###`) instead of raw text patterns.
3. Feeds the text through the existing `legal_units()` logic.
4. Outputs chunks with V1-compatible metadata schema.

---

## Step 4: Build V2 Index

### 4.1 Ingestion Script

Create `RAG V2/scripts/build_rag_v2_index.py`:

```python
"""Build V2 canonical dataset from markdown corpus."""
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.ingestion.pipeline import build_canonical_dataset  # reuse V1 pipeline
from app.ingestion.chunker import legal_units, chunk_units  # reuse V1 chunker
from app.ingestion.extractor import extract  # reuse V1 extractor
from app.ingestion.validator import validate_dataset  # reuse V1 validator

def build_v2_index(markdown_dir: Path, output_dir: Path):
    # 1. Parse all markdown files
    # 2. Extract metadata from frontmatter
    # 3. Chunk each document
    # 4. Validate chunks
    # 5. Build documents.jsonl and chunks.jsonl
    # 6. Generate source_registry.csv
    pass

if __name__ == "__main__":
    documents, chunks, validation = build_v2_index(
        markdown_dir=Path(__file__).resolve().parents[1] / "dataset_markdown",
        output_dir=Path(__file__).resolve().parents[1] / "dataset" / "canonical"
    )
    print(f"Documents: {len(documents)}, Chunks: {len(chunks)}, Validation: {'PASS' if validation.passed else 'FAIL'}")
```

### 4.2 Build Command

```bash
cd "RAG V2"
python scripts/build_rag_v2_index.py
```

### 4.3 Validation

After building, validate with:
```bash
python -c "from app.ingestion.validator import validate_dataset; ..."
```

---

## Step 5: Build Embedding Pipeline

### 5.1 Configuration

Reuse V1's embedding configuration from `.env`:
```
EMBEDDING_PROVIDER=openrouter
EMBEDDING_MODEL=openai/text-embedding-3-small
EMBEDDING_DIMENSION=1536
```

### 5.2 Implementation

Reuse `app/retrieval/embeddings.py`:
- `OpenRouterEmbeddingProvider` for production embeddings.
- `HashEmbeddingProvider` for local testing/fallback.

For V2, create a dedicated embedding build script that:
1. Reads chunks.jsonl.
2. Batches text for embedding.
3. Stores vectors alongside chunks.
4. Persists the vector index.

### 5.3 Build Command

```bash
cd "RAG V2"
python scripts/build_rag_v2_embeddings.py
```

---

## Step 6: Build Retrieval Pipeline

### 6.1 Reuse V1 Components

All retrieval components can be reused from V1:

| Component | File | Usage |
|-----------|------|-------|
| `LocalCorpusStore` | `app/retrieval/local_store.py` | TF-IDF local store |
| `HybridRetriever` | `app/retrieval/hybrid.py` | Parallel vector+keyword fusion |
| `LegalFeatureReranker` | `app/retrieval/reranker.py` | Legal-feature scoring |
| `analyze_query` | `app/retrieval/query_analysis.py` | Domain/routing/intent |
| `RAGService` | `app/service.py` | Orchestration |
| `RAGConfig` | `app/core/config.py` | Configuration |

### 6.2 Load V2 Corpus

The V2 corpus must be loaded into V1's `LocalCorpusStore` format:
- `chunks.jsonl` with chunk documents.
- `documents.jsonl` with document metadata.
- Vector embeddings for each chunk.

### 6.3 Query Analysis

Reuse `app/retrieval/query_analysis.py` with V2's domain taxonomy. The `DOMAIN_TERMS` mapping in `query_analysis.py` must include all V2 domains.

---

## Step 7: Build Generation Pipeline

### 7.1 Reuse V1 Components

| Component | File | Usage |
|-----------|------|-------|
| `GeminiGroundedGenerator` | `app/generation/grounded.py` | LLM generation |
| `ExtractiveGroundedGenerator` | `app/generation/grounded.py` | Extractive fallback |
| `assemble_context` | `app/generation/context.py` | Context construction |
| `validate_citations` | `app/citations/engine.py` | Citation validation |
| `abstention_reason` | `app/guardrails/policy.py` | Abstention logic |
| `calculate_confidence` | `app/guardrails/policy.py` | Confidence scoring |

### 7.2 LLM Configuration

Keep V1's Gemini configuration:
```
LLM_PROVIDER=gemini
LLM_MODEL=gemini-3.1-flash-lite
RAG_LLM_TIMEOUT=30.0
```

---

## Step 8: Build API

### 8.1 Reuse V1 API

Reuse `app/api/main.py` with V1's endpoints:
- `GET /health`
- `POST /api/v1/ask`
- `POST /rag/query`

### 8.2 Run Command

```bash
cd "RAG V2"  # or wherever the service code lives
python -m uvicorn app.api.main:app --host 0.0.0.0 --port 8000
```

---

## Step 9: Benchmark V2

### 9.1 Benchmark Script

Reuse V1's `scripts/test_rag_questions.py` pattern:

```python
"""Run Phase 16 questions against V2 and measure results."""
import json, time, httpx
from pathlib import Path

QUESTIONS = json.loads(Path("dataset/evaluation/phase16_rag_questions.json").read_text())
BASE = "http://127.0.0.1:8000"

results = []
for q in QUESTIONS:
    t0 = time.perf_counter()
    r = httpx.post(f"{BASE}/api/v1/ask", json={"question": q["question"], "jurisdiction": q["jurisdiction"]}, timeout=60)
    ms = (time.perf_counter() - t0) * 1000
    d = r.json()
    results.append({
        "id": q["id"], "http_status": r.status_code, "total_ms": round(ms, 1),
        "confidence": d.get("confidence"), "abstained": d.get("abstained"),
        "grounded": d.get("answer") is not None and len(d.get("answer","")) > 10,
        "citations": len(d.get("citations", [])),
        "sources": len(d.get("sources", [])),
        "answer": d.get("answer", "")[:100],
        "failure_reason": d.get("abstained") and d.get("answer","")
    })

Path("dataset/evaluation/results/rag_v2_baseline_results.json").write_text(json.dumps(results, indent=2))
```

### 9.2 Metrics to Track

For each question:
- HTTP status
- Total latency
- Route (RAG / GENERAL)
- Retrieval latency
- Generation latency
- Confidence score
- Abstained (yes/no)
- Grounded (yes/no)
- Citation count
- Source count
- Retrieved chunk IDs
- Failure reason

---

## Step 10: Test Cases

### 10.1 Test Structure

Create `RAG V2/tests/test_rag_v2.py`:

```python
"""RAG V2 test cases per Master Prompt RULE 19."""
import pytest
from app.service import RAGService
from app.models import QueryRequest

class TestRAGFactual:
    """Test A: RAG factual question."""
    def test_patents_act_section_3(self):
        svc = RAGService()
        resp = svc.query(QueryRequest(query="What provisions address traditional knowledge?", jurisdiction="INDIA"))
        assert resp.confidence >= 0.5
        assert len(resp.evidence) > 0

class TestGeneralQuestion:
    """Test B: General question (no retrieval)."""
    def test_general_question(self):
        svc = RAGService()
        resp = svc.query(QueryRequest(query="What is IP law?", jurisdiction="INDIA"))
        assert resp.abstained == False  # GENERAL route answers directly

class TestMissingEvidence:
    """Test C: Missing evidence causes abstention."""
    def test_missing_evidence(self):
        svc = RAGService()
        resp = svc.query(QueryRequest(query="What is an obscure provision not in any document?", jurisdiction="INDIA"))
        assert resp.abstained == True
        assert "insufficient" in resp.answer.lower()

# ... similar tests for D-J
```

---

## Step 11: Run and Validate

### 11.1 Benchmark Command

```bash
cd "RAG V2"
python scripts/benchmark_phase16.py
```

### 11.2 Validation Checklist

- [ ] All 25 Phase 16 questions return HTTP 200.
- [ ] Grounded answers ≥ 20/25.
- [ ] Citation accuracy ≥ 90%.
- [ ] Abstention accuracy ≥ 90%.
- [ ] Average retrieval latency ≤ 500ms.
- [ ] Average total latency ≤ 2000ms.
- [ ] No hallucinated legal citations.
- [ ] Correct abstention for insufficient evidence.

---

## Documentation to Update

After implementation:
1. `README.md` — Add V2 setup instructions.
2. `docs/RAG_V2_IMPLEMENTATION_GUIDE.md` — This file (final version).
3. `RAG V2/scripts/build_rag_v2_index.py` — Document build steps.
4. `RAG V2/scripts/benchmark_phase16.py` — Document benchmark steps.

---

## Reproducibility Checklist

- [ ] `python scripts/convert_dataset_to_markdown.py` produces markdown from raw sources.
- [ ] `python scripts/build_rag_v2_index.py` produces canonical chunks.jsonl + documents.jsonl.
- [ ] `python scripts/build_rag_v2_embeddings.py` produces vector index.
- [ ] `python -m uvicorn app.api.main:app --port 8000` starts the server.
- [ ] `python scripts/benchmark_phase16.py` produces results JSON.
- [ ] `python -m pytest tests/test_rag_v2.py` passes all tests.

---

## Current Status

- [x] Audit complete (this document + 3 companion docs)
- [ ] Fix corrupt PDF (G002)
- [ ] Clean TRIPS navigation leak
- [ ] Build metadata schema + source_registry.csv
- [ ] Build markdown chunking pipeline
- [ ] Build V2 index (chunks.jsonl + documents.jsonl)
- [ ] Build embedding pipeline
- [ ] Build retrieval pipeline
- [ ] Build generation pipeline
- [ ] Build API
- [ ] Run Phase 16 benchmark
- [ ] Produce V1 vs V2 comparison
- [ ] Produce dataset expansion decision
- [ ] Produce final report

**Next step: Step 1 — Fix the corrupt PDF.**
