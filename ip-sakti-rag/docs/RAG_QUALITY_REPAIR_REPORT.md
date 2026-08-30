# IP-SAKTI Sahayak RAG Quality Repair Report

Generated: 2026-08-30

## 1. Scope

This repair phase targeted runtime RAG quality failures observed in the existing `/api/v1/ask` evaluation.

The dataset was treated as locked. No dataset rebuild, document download, canonical chunk regeneration, validator weakening, or API contract replacement was performed.

## 2. Baseline Before Repair

Source: previously generated runtime question evaluation report/artifact.

- Scored runtime questions: 55
- Passed: 43
- Failed: 12
- API success rate: 1.0
- Expected-document hit rate: 0.9473684210526315
- MRR: 0.8377192982456141
- Citation integrity rate: 1.0
- Response schema rate: 1.0
- Confidence validity rate: 1.0
- Abstention accuracy: 0.875

Failed question IDs:

- Q15, Q16, Q17
- Q20
- Q23, Q24
- Q36
- Q41, Q42, Q43, Q44
- Q49

Primary failure patterns:

- Natural-language routing failed for some user-style questions, including invented-product, logo, regional-product, and song examples.
- Design, GI, plant-variety, biodiversity, and international queries were under-prioritized or retrieved noisy fragments.
- Evidence sufficiency accepted some weakly aligned evidence and rejected some valid sparse evidence.
- The deterministic generator sometimes selected procedural/noisy fragments instead of answer-bearing legal text.
- A speculative false-premise patent question about teleportation did not abstain safely enough.

## 3. Repair Plan

The pre-implementation plan is recorded in:

- `docs/RAG_QUALITY_REPAIR_PLAN.md`

The plan mapped each failed question to the likely component-level cause and the intended targeted repair. The plan focused on query analysis, retrieval scoring, reranking, sufficiency checks, deterministic generation, confidence alignment, and regression tests.

## 4. Files Created

- `docs/RAG_QUALITY_REPAIR_PLAN.md`
- `docs/RAG_QUALITY_REPAIR_REPORT.md`
- `dataset/evaluation/results/runtime_question_test.json`

The evaluation JSON is an execution artifact from the 55-question runtime HTTP test. It is not part of the canonical dataset.

## 5. Files Modified

- `app/models/schemas.py`
- `app/retrieval/query_analysis.py`
- `app/retrieval/hybrid.py`
- `app/retrieval/local_store.py`
- `app/retrieval/reranker.py`
- `app/guardrails/policy.py`
- `app/generation/grounded.py`
- `app/service.py`
- `tests/test_retrieval.py`
- `tests/test_grounding.py`

No canonical dataset, source registry, checksum manifest, or download manifest content changed.

## 6. Query Analysis Improvements

Implemented richer runtime query analysis while preserving the existing architecture:

- Added intent detection for definition, registration, rights, duration, opposition, purpose, difference, and infringement.
- Added natural-language domain recognition for:
  - invented/new device/new process → patent
  - logo/business name/company name → trademark
  - original song/music/lyrics → copyright
  - regional/traditional/place-of-origin product → GI
  - product shape/configuration/pattern/ornament → design
  - crop/new variety → plant variety
  - access and benefit sharing/biological resources → ABS
  - TRIPS/WIPO/treaty language → international
- Added out-of-scope and speculative-subject detection.
- Added retrieval query expansion based on detected domain and intent.
- Preserved unknown metadata instead of fabricating low-confidence filters.

## 7. Retrieval and Metadata Filtering Improvements

The hybrid retriever still uses the existing vector + keyword + metadata fusion design.

Changes:

- Semantic and keyword retrieval now consume the expanded retrieval query.
- Local fallback retrieval adds title/intent boosts without replacing Supabase architecture.
- Difference/comparison questions retrieve additional per-domain candidates so one domain cannot monopolize the evidence set.
- Metadata filtering remains evidence-preserving: detected domains prioritize retrieval but do not fabricate document IDs or unsupported filters.

## 8. Reranking Improvements

The deterministic legal-feature reranker was repaired, not replaced.

Changes:

- Added intent relevance features.
- Added document relevance features using domain, jurisdiction, document type, and title overlap.
- Added noise penalties for common non-answer fragments such as fee tables, forms, schedules, revocation/cancellation fragments, and procedural boilerplate.
- Added balanced evidence selection for cross-domain difference questions.
- Preserved provenance fields: document ID, title, page, section/rule/article metadata, authority, source URL, source status, and retrieval scores.

## 9. Evidence Sufficiency and Abstention Improvements

Changes:

- Added early abstention for clear out-of-corpus requests and speculative false-premise subjects.
- Added answer-intent alignment checks so retrieval alone is not treated as sufficient.
- Kept legal identifier validation strict; unsupported section/rule/article requests still abstain.
- Relaxed sparse-source definition checks only where authoritative treaty/Act title metadata and retrieved text support the question.
- Required comparison questions to contain evidence from both compared domains.

Result:

- Teleportation patent question now abstains.
- Out-of-corpus questions continue to abstain.
- Valid sparse treaty questions, such as TRIPS, no longer fail merely because the source lacks classic “means/defined” wording.

## 10. Grounded Generation Improvements

The local deterministic generator remains extractive and citation-first.

Changes:

- Uses retrieval-expanded query terms rather than only raw user terms.
- Reviews a wider evidence window.
- Prefers answer-bearing sentences for each detected intent.
- Avoids known noisy fragments for registration/rights/difference-style answers.
- For comparison questions, selects support from multiple domains before filling remaining evidence.
- Keeps citations backend-controlled; the generator still returns chunk IDs and the backend maps them to validated citation metadata.

Known limitation:

- Some answers remain extractive and grammatically rough because the local path is deterministic and does not rewrite legal text into polished prose. This is a quality limitation, not a citation-integrity failure.

## 11. Citation Validation

Citation validation was preserved.

- Grounded answers still cite only retrieved evidence.
- The LLM/local generator cannot invent public citation metadata.
- Invalid, missing, or nonexistent chunk citations still cause abstention.
- Final HTTP evaluation reported citation integrity rate: 1.0.

## 12. Confidence Scoring

Confidence remains deterministic.

Changes:

- Added answer/evidence alignment as a scoring signal.
- Confidence now considers retrieval/reranker quality, citation coverage, source authority, evidence support, jurisdiction consistency, and query-intent alignment.
- Abstained responses continue to return low confidence.

Final HTTP evaluation reported confidence validity rate: 1.0.

## 13. Dataset Integrity Verification

The 55-question evaluator performed before/after dataset checks and reported:

- `dataset_unchanged`: true
- document_count: 25
- retrievable_document_count: 24
- chunk_count: 6514

Canonical/manifest hashes after repair:

- `dataset/canonical/documents.jsonl`: `6d9b657a2fb84f6414dd7f28c7cc7550c4fe25681e6200242d38889da6ddb7f1`
- `dataset/canonical/chunks.jsonl`: `4ce211289e88958c89d4bafc4ede7271cc387c55cc1f18b73acbe9ea30131bda`
- `dataset/manifests/source_registry.csv`: `c48c09f6e1ae39352f43a40fb0fb1a7cf614fecee6a06050054cfe4fbc751a3e`
- `dataset/manifests/download_manifest.json`: `a0f19a145d79cf13ad3b39a4ea586bd8303ba38d1e744d99ebcdd7effdf2b84f`
- `dataset/manifests/checksums.sha256`: `d045c0845c7ecaa82f4702c616f36b933aeee65e19bcd1bf59019a2c3d85f791`

Remaining known source warnings are unchanged:

- `IND-PAT-RULES-2003`: raw source unavailable; page citations disabled
- `IND-CR-RULES-2013`: raw source unavailable; page citations disabled

## 14. Tests Executed

### Unit/integration test suite

Command:

```powershell
python -m pytest
```

Result:

- 35 passed
- 5 warnings from PyMuPDF/SWIG import deprecations

### Runtime HTTP evaluation

Command pattern:

```powershell
python -m uvicorn app.api.main:app --host 127.0.0.1 --port 8765
python scripts/test_rag_questions.py --base-url http://127.0.0.1:8765
```

Result:

- HTTP endpoint: `/api/v1/ask`
- Question count: 55
- Passed: 55
- Failed: 0
- API success rate: 1.0
- Expected-document hit rate: 1.0
- MRR: 0.9734848484848485
- Citation integrity rate: 1.0
- Response schema rate: 1.0
- Confidence validity rate: 1.0
- Abstention accuracy: 1.0
- Latency min: 3.412 ms
- Latency max: 597.934 ms
- Latency average: 139.049 ms
- Latency median: 71.242 ms
- Latency p95: 550.439 ms

Malformed request checks passed:

- missing question → 422
- blank question → 422
- invalid domain → 422
- invalid top_k → 422

## 15. Before/After Comparison

| Metric | Before repair | After repair |
|---|---:|---:|
| Questions | 55 | 55 |
| Passed | 43 | 55 |
| Failed | 12 | 0 |
| API success rate | 1.0 | 1.0 |
| Expected-document hit rate | 0.9473684210526315 | 1.0 |
| MRR | 0.8377192982456141 | 0.9734848484848485 |
| Citation integrity rate | 1.0 | 1.0 |
| Response schema rate | 1.0 | 1.0 |
| Confidence validity rate | 1.0 | 1.0 |
| Abstention accuracy | 0.875 | 1.0 |

## 16. Known Limitations

- The local deterministic generator is still extractive. It can produce awkward prose and occasionally quote OCR-noisy fragments.
- Local fallback retrieval approximates vector search with deterministic lexical/TF-IDF style scoring. Production Supabase/embedding behavior still depends on configured credentials and deployed schema/functions.
- This repair improves runtime behavior against the existing 55-question suite; it is not a claim of broader legal-answer production quality beyond the executed tests.
- No new external credentials were available or fabricated.

## 17. Production Blockers

- Production Supabase, embedding, and LLM credentials must be configured in the deployment environment.
- A learned reranker or LLM-based answer synthesizer may be needed for polished production prose.
- The two existing source warnings remain unresolved and should continue to be disclosed in validation reports.

## 18. Exact Next Steps

1. Configure production environment variables for Supabase, embedding provider, and LLM provider.
2. Run the same 55-question HTTP suite against the production-like Supabase backend.
3. Add a larger regression suite for multi-hop comparisons and legal false-premise prompts.
4. Consider enabling the grounded LLM generator in production once credentials and latency/cost limits are approved.
5. Keep canonical dataset fingerprints under CI so runtime changes cannot silently alter the corpus.

## 19. Final Quality Status

RAG QUALITY: IMPROVED.

The executed 55-question local HTTP evaluation improved from 43/55 passing to 55/55 passing, with citation integrity, schema validity, confidence validity, expected-document hit rate, and abstention accuracy all at 1.0.

Another repair phase is not required for this specific 55-question evaluation gate, but a future prose-quality phase is recommended before presenting deterministic extractive answers as final user-facing legal copy.
