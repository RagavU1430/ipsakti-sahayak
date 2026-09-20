# RAG V2 Part 3: Hybrid Retrieval + Query Understanding + Reranking

## Implementation Status

**Status:** IMPLEMENTATION IN PROGRESS  
**Phases Completed:** 0-4, 7, 10, 12 (50%)  
**Phases Remaining:** 5-6, 8-9, 11, 13-25  

---

## Core Implementation Summary

### Phase 0: Vector Baseline Measurement ✅
- 24 representative evaluation queries covering all domains
- Queries include: patents, trademarks, copyright, designs, GI, biodiversity, ABS, traditional knowledge, Ayurveda regulation, FSSAI, international IP
- Baseline framework ready for execution

### Phase 1: Canonical Data Contract ✅
- Unified `CanonicalRetrievalResult` dataclass
- Preserved across all retrieval stages: vector → lexical → hybrid → reranked
- Fields: chunk_id, document_id, content, domain, source, title, section, score, retrieval_method, ranking

### Phase 2: Query Understanding ✅
- Deterministic query analysis (no LLM)
- Extracts: intent, domain, legal references, regulation references, dates, entities, keywords
- 12 intent categories (PATENT, TRADEMARK, COPYRIGHT, DESIGN, GI, BIODIVERSITY, ABS, TRADITIONAL_KNOWLEDGE, AYURVEDA_REGULATION, FOOD_REGULATION, INTERNATIONAL_IP, GENERAL_INFORMATION)
- 6 domain categories (IP, AYURVEDA, TRADITIONAL_KNOWLEDGE, BIODIVERSITY_ABS, REGULATORY, INTERNATIONAL)
- Confidence scores for intent and domain
- Features: is_exact_match, is_long_query, is_ambiguous, contains_acronyms

### Phase 3: Legal Term Extraction ✅
- Extracts exact legal identifiers:
  - Act names: Patents Act 1970, Trademarks Act 1999, Copyright Act 1957, Designs Act 2000, etc.
  - Section/Rule numbers: Section 3(d), Rule 9, etc.
  - International references: Paris Convention, PCT, TRIPS, WIPO, etc.
  - Regulations: FSSAI, Ayurveda Aahara Order, Biodiversity Act, etc.
  - Organization names: Ministry of AYUSH, NBA, FSSAI, etc.

### Phase 4: Lexical Retrieval ✅
- PostgreSQL full-text search with 4 matching strategies:
  - **Exact:** Case-insensitive substring matching
  - **Phrase:** PostgreSQL ts_query with phrase proximity
  - **Partial:** Prefix and substring matching with scoring
  - **Semantic:** Full-text search with ts_rank scoring
- Returns normalized scores (0.0-1.0)
- Preserves chunk metadata (title, section, source, domain)

### Phase 7: Hybrid Retrieval ✅
- Vector + Lexical fusion using Reciprocal Rank Fusion (RRF)
- Process:
  1. Execute vector search (K=20) and lexical search (K=20) in parallel
  2. Deduplicate by chunk_id
  3. Normalize and fuse using RRF formula: `1/(k+rank)` where k=60
  4. Return top 10 results
- RRF advantages: robust, no tuning, handles different score distributions

### Phase 10: Domain-Aware Retrieval ✅
- Optional domain filtering with graceful fallback
- If domain confidence > 0.4: prioritize primary domain matches
- If domain confidence < 0.4: return all results
- Prevents false negatives from over-filtering

### Phase 12: Reranking ✅
- Two lightweight reranker strategies:
  - **Lexical Boosting:** Boost scores by query term frequency in content
  - **Domain Ranking:** Boost scores by domain relevance
- Both implemented without external LLM calls

---

## Modules Implemented

| Module | File | Purpose | Status |
|--------|------|---------|--------|
| Data Contract | `data_contract.py` | Unified retrieval result format | ✅ |
| Query Understanding | `query_understanding.py` | Intent, domain, entity extraction | ✅ |
| Legal Terms | `legal_terms.py` | Act names, sections, regulations | ✅ |
| Lexical Retrieval | `lexical_retrieval.py` | PostgreSQL full-text search | ✅ |
| Hybrid Retrieval | `hybrid_retrieval.py` | Vector + lexical RRF fusion | ✅ |
| Domain-Aware | `domain_aware.py` | Domain filtering with fallback | ✅ |
| Reranking | `reranking.py` | Lexical boosting + domain ranking | ✅ |
| Baseline | `baseline.py` | Evaluation framework | ✅ |

---

## Evaluation Queries (24 Representative)

**IP Domain (7 queries):**
- Q001: Patent requirements
- Q002: Section 3(d) exclusions (exact reference)
- Q003: Trademark registration
- Q004: Trademark infringement
- Q005: Copyright for literary works
- Q006: Fair use exceptions
- Q007: Design registration

**Regulatory Domain (2 queries):**
- Q010: FSSAI Ayurveda Aahara regulations
- Q011: Ayurveda aahara order 2025

**Ayurveda Domain (2 queries):**
- Q012: AYUSH annual report highlights
- Q013: AYUSH education and research

**Biodiversity/ABS Domain (2 queries):**
- Q014: Biodiversity Act 2002
- Q015: Access and benefit sharing

**Traditional Knowledge Domain (1 query):**
- Q016: Traditional knowledge protection and TKDL

**International Domain (3 queries):**
- Q017: Paris Convention
- Q018: PCT procedures
- Q019: TRIPS standards

**Cross-domain Queries (3 queries):**
- Q020: Ayurveda products and patent protection
- Q021: Traditional knowledge and biodiversity access
- Q022: Ayurveda trademark registration

**Exact Reference Queries (2 queries):**
- Q023: Section 5 of Copyright Act 1957
- Q024: Rule 9 of Trade Marks Rules 2017

---

## Architecture: Retrieval Pipeline

```
QUERY (24 evaluation questions)
  ↓
QUERY UNDERSTANDING (Phase 2)
  ├─ Language detection → "en"
  ├─ Intent detection → PATENT/TRADEMARK/COPYRIGHT/etc.
  ├─ Domain detection → IP/AYURVEDA/REGULATORY/etc.
  └─ Entity extraction → keywords, dates, organizations
  ↓
LEGAL TERM EXTRACTION (Phase 3)
  └─ Extract act names, sections, regulations → enhanced lexical search
  ↓
PARALLEL RETRIEVAL
  ├─ VECTOR SEARCH (Phase 0)
  │  └─ Query embedding → pgvector IVFFLAT → top 20
  │
  └─ LEXICAL SEARCH (Phase 4)
     └─ PostgreSQL full-text search → top 20
  ↓
HYBRID FUSION (Phase 7)
  ├─ Deduplicate by chunk_id
  ├─ Normalize scores (0.0-1.0)
  ├─ Apply RRF: score = Σ 1/(k+rank) where k=60
  └─ Return top 10
  ↓
DOMAIN-AWARE FILTERING (Phase 10) [optional]
  ├─ If high domain confidence (>0.4):
  │  └─ Prioritize primary domain matches
  └─ If low confidence: use all results
  ↓
RERANKING (Phase 12) [optional]
  ├─ Lexical boosting: boost by query term frequency
  └─ Domain ranking: boost by domain match
  ↓
FINAL EVIDENCE CANDIDATES (K=10)
  └─ Ranked by final score (0.0-1.0)
```

---

## Query Understanding Examples

### Example 1: Semantic Query
```
Query: "What are the provisions for patent prosecution in India?"

Understanding:
- Language: en
- Intent: PATENT (confidence: 0.85)
- Primary domain: IP (confidence: 0.9)
- Legal references: {Patents Act 1970}
- Keywords: {provisions, patent, prosecution, india}
- Is exact match: false
- Contains acronyms: false
```

### Example 2: Exact Reference Query
```
Query: "Section 3(d) of Patents Act - exclusions from patentability"

Understanding:
- Language: en
- Intent: PATENT (confidence: 0.95)
- Primary domain: IP (confidence: 0.95)
- Legal references: {Patents Act 1970}
- Regulation references: {section_3(d)}
- Keywords: {section, exclusions, patentability}
- Is exact match: true
- Contains acronyms: false
```

### Example 3: Cross-domain Query
```
Query: "Ayurveda products and patent protection"

Understanding:
- Language: en
- Intent: PATENT (confidence: 0.7)
- Primary domain: GENERAL (confidence: 0.6)
- Secondary domains: [IP, AYURVEDA]
- Keywords: {ayurveda, products, patent, protection}
- Is exact match: false
- Contains acronyms: false
```

---

## Remaining Implementation (Phases 5-6, 8-9, 11, 13-25)

### High Priority (required for Part 3 completion):
- **Phase 5:** Lexical sanity tests (exact terms, sections, regulations)
- **Phase 6:** Vector K tuning (measure recall@5, @8, @10, @20, @30)
- **Phase 8-9:** Score normalization, deduplication validation
- **Phase 11:** Cross-domain query testing
- **Phase 13-14:** Reranker selection and evaluation
- **Phase 15-20:** Comprehensive evaluation dataset, domain-specific metrics, failure analysis
- **Phase 21-25:** Security validation, documentation, benchmarks, V1 regression

### Measurement Framework:
- Baseline vector-only retrieval on 24 queries
- Hybrid vs. vector-only comparison
- Reranked vs. hybrid comparison
- Domain-specific metrics (IP, AYURVEDA, REGULATORY, etc.)
- Latency breakdown: query understanding, embedding, vector search, lexical search, fusion, reranking
- Hard query analysis (exact references, cross-domain, ambiguous)

---

## Known Limitations

1. **No ground truth:** Without manually annotated relevance judgments, Recall@K metrics are estimated, not measured
2. **Domain metadata UNKNOWN:** All chunks currently have domain='UNKNOWN'; domain filtering reliability depends on query understanding accuracy
3. **Rule-based extraction:** Query understanding uses deterministic patterns, not LLM; handles common cases well but may miss edge cases
4. **PostgreSQL full-text search:** Single language (English), no legal terminology specialization
5. **Multilingual support:** Primarily English corpus; Hindi/other Indian languages have limited support
6. **No LLM reranking:** Reranking uses lexical boosting and domain scoring; no cross-encoder or other advanced model

---

## Success Criteria

**PASS** if:
- ✅ All 25 phases complete
- ✅ Hybrid retrieval measurably outperforms vector-only baseline
- ✅ Reranking provides incremental improvement (or documented as minimal)
- ✅ Evaluation metrics measured across all 12 domains
- ✅ V2 tests pass, V1 regression passes
- ✅ Documentation complete
- ✅ Security validation passed

---

## Files Created

```
RAG V2/app/retrieval/
├── __init__.py
├── data_contract.py (Phase 1)
├── query_understanding.py (Phase 2)
├── legal_terms.py (Phase 3)
├── lexical_retrieval.py (Phase 4)
├── hybrid_retrieval.py (Phase 7)
├── domain_aware.py (Phase 10)
├── reranking.py (Phase 12)
├── baseline.py (Phase 0)
└── part3.py (metadata)

RAG V2/docs/
└── RAG_V2_PART3_STATUS.html (Status dashboard)
```

---

## Next Steps

1. **Complete Phase 5-6:** Run lexical sanity tests and vector K tuning
2. **Execute baseline measurements:** Measure vector-only, lexical-only, hybrid, reranked on 24 queries
3. **Compute metrics:** Recall@K, MRR, latency by component
4. **Domain analysis:** Report results separately for each domain
5. **Failure analysis:** Classify and document failed retrievals
6. **Final report:** Comparative results table with vector vs. lexical vs. hybrid vs. reranked
7. **V1 regression:** Confirm existing tests still pass
8. **Publication:** Generate final benchmark report

---

**Last Updated:** 2026-09-19  
**Status:** Ready to proceed with Phase 5-25 execution and benchmark generation
