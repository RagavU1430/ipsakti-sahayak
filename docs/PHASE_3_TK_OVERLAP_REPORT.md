# Phase 3 TK Overlap Report

## 1. What Was Implemented

Implemented `POST /api/v1/tk/overlap` for evidence-first Traditional Knowledge overlap analysis.

The implementation includes:

- TK request/response DTOs,
- TK query analysis,
- TK evidence analysis,
- deterministic overlap classification,
- confidence calculation derived from RAG evidence,
- abstention preservation,
- citation/source preservation,
- multilingual wrapping through the existing Gemini-only translation service,
- React frontend page and API client.

## 2. API Added

```text
POST /api/v1/tk/overlap
```

Request:

```json
{
  "description": "A turmeric and neem herbal formulation for traditional Ayurvedic therapeutic use in India.",
  "language": "en"
}
```

## 3. Frontend Changes

Added:

- `/tk` route,
- backend SPA forward for direct `/tk` navigation,
- `TK Overlap` navigation item,
- `TkOverlapPage`,
- `analyzeTkOverlap` API client,
- TK response/request TypeScript types.

The frontend calls only Spring Boot. It does not call Gemini, Supabase, or Python RAG directly.

Packaged-backend route smoke test on 2026-09-03:

```text
GET /tk -> HTTP 200, React SPA shell served
```

## 4. TK Detection Architecture

```text
TkOverlapController
-> TkOverlapService
-> TranslationService
-> TkQueryAnalyzer
-> RagClient
-> existing Python RAG
-> TkEvidenceAnalyzer
-> TkOverlapResponse
```

The backend does not directly query Supabase or pgvector for TK overlap.

## 5. Gemini Verification

Code path:

```text
PASS
```

Mocked multilingual tests:

```text
PASS
```

Live Gemini request:

```text
PASS
```

Result:

The local server-side `GEMINI_API_KEY` was present and live Gemini `generateContent` calls succeeded without exposing the key.

Primary verified model:

```text
gemini-2.5-flash
```

Default fallback models verified by live probing:

- `gemini-2.5-flash`
- `gemini-2.5-flash-lite`
- `gemini-3.1-flash-lite`
- `gemini-3.5-flash-lite`
- `gemini-flash-lite-latest`

Removed from default fallback list after live probing:

- `gemini-2.5-pro` returned 404 for this key/request despite model listing.
- `gemini-1.5-flash` returned 404.
- `gemini-1.5-pro` returned 404.

## 6. Bhashini Status

Active application code/resources scan:

```text
REMOVED / NOT FOUND
```

Scanned active paths:

- `ip-sakti-backend/src/main/java`
- `ip-sakti-backend/src/test/java`
- `ip-sakti-backend/src/main/resources`
- `Frontend/src`
- `Frontend/.env.example`
- `ip-sakti-rag/app`
- `ip-sakti-rag/tests`
- `ip-sakti-rag/.env.example`

Historical reports and raw downloaded HTML may still contain textual references. Raw dataset files were not modified.

## 7. Test Results

Backend focused TK tests:

```text
13 passed
```

Backend full test suite:

```text
143 passed
```

Frontend tests:

```text
4 files passed, 7 tests passed
```

Frontend lint:

```text
PASS
```

Frontend build:

```text
PASS
```

Python RAG pytest:

```text
75 passed, 30 skipped, 6 warnings
```

RAG baseline verifier:

```text
PASS
```

Deep RAG regression:

```text
ATTEMPTED; live rerun did not complete in this environment
```

## 8. Multilingual Test Results

Automated mocked multilingual TK regression:

```text
30/30 PASS
```

Coverage:

- 6 languages
- 5 realistic case categories per language
- grounded overlap
- potential overlap
- insufficient evidence
- citation/source preservation
- abstention preservation
- legal-safety assertions

Live multilingual Gemini tests:

```text
PASS WITH WARNING
```

Live `/api/v1/questions` smoke tests passed for English, Hindi, Tamil, Telugu, Kannada, and Malayalam with citations preserved.

Live `/api/v1/tk/overlap` smoke tests passed for English, Hindi, Tamil, and Telugu with direct natural-language prompts. Kannada and Malayalam passed when the prompt preserved legal identifiers such as `Section 3(p)` / `traditional knowledge`; fully translated natural-language-only Kannada and Malayalam prompts abstained due to insufficient evidence.

## 9. RAG Regression Results

Deep RAG result generated at:

```text
2026-09-02T16:01:42.580794+00:00
```

| Metric | Value |
|---|---:|
| Questions | 162 |
| Passed | 162 |
| Failed | 0 |
| Recall@K | 1.0000 |
| MRR | 0.9889 |
| Citation integrity | 1.0000 |
| Groundedness | 1.0000 |
| Abstention accuracy | 0.9444 |
| Answer quality | 1.9259 / 2 |

## 10. Dataset Hash Before / After

Before:

```text
827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d
```

After:

```text
827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d
```

Dataset integrity:

```text
PASS
```

## 11. Performance

Deep RAG regression latency:

| Metric | Value |
|---|---:|
| P50 | 385.520 ms |
| P95 | 1287.952 ms |
| P99 | 1600.156 ms |

Focused mocked TK service tests recorded millisecond-level service execution because RAG and Gemini were mocked. No live end-to-end TK latency with Gemini is claimed.

Live local smoke-test latency on 2026-09-02, using Spring Boot + Python RAG + Gemini:

| Check | Result | Total latency |
|---|---|---:|
| `/api/v1/tk/overlap` English | `STRONG_TK_OVERLAP`, 3 citations | 11048 ms |
| `/api/v1/tk/overlap` Hindi | `STRONG_TK_OVERLAP`, 3 citations | 25043 ms |
| `/api/v1/tk/overlap` Tamil | `POTENTIAL_TK_OVERLAP`, 1 citation | 15441 ms |
| `/api/v1/tk/overlap` Telugu | `POTENTIAL_TK_OVERLAP`, 1 citation | 30300 ms |
| `/api/v1/tk/overlap` Kannada with preserved identifiers | `POTENTIAL_TK_OVERLAP`, 1 citation | 29658 ms |
| `/api/v1/tk/overlap` Malayalam with preserved identifiers | `POTENTIAL_TK_OVERLAP`, 1 citation | 28840 ms |

Separate Gemini input/RAG/output timing was not instrumented in the runtime response, so only total request latency is claimed here.

## 12. Files Changed

Backend:

- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/config/GeminiProperties.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/config/SpaForwardController.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/config/SecurityConfig.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/exception/TranslationException.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/multilingual/GeminiTranslationProvider.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/tk/TkOverlapService.java`
- `ip-sakti-backend/src/main/resources/application.yaml`
- `ip-sakti-backend/src/main/resources/static/index.html`
- frontend production bundle under `ip-sakti-backend/src/main/resources/static/assets/`

Frontend:

- `Frontend/src/App.tsx`
- `Frontend/src/pages/AboutPage.tsx`
- `Frontend/src/api/types.ts`
- `Frontend/src/api/tk.ts`
- `Frontend/src/pages/TkOverlapPage.tsx`

Configuration:

- `.env.example`
- local `.env` fallback model list was updated; secret values were not printed or committed.

Tests:

- `ip-sakti-backend/src/test/java/com/ipsakti/ip_sakti_backend/config/PropertiesBindingTest.java`
- `ip-sakti-backend/src/test/java/com/ipsakti/ip_sakti_backend/multilingual/GeminiMultilingualTest.java`

RAG evaluation artifacts:

- `ip-sakti-rag/dataset/evaluation/deep_rag/deep_rag_results.json`
- `ip-sakti-rag/dataset/evaluation/deep_rag/deep_rag_summary.json`
- `ip-sakti-rag/docs/RAG_DEEP_TEST_REPORT.md`

Docs:

- `docs/TK_OVERLAP_IMPLEMENTATION.md`
- `docs/PHASE_3_TK_OVERLAP_REPORT.md`

## 13. Files Created

- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/api/TkOverlapController.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/tk/TkOverlapService.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/tk/analysis/TkAssessment.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/tk/analysis/TkEvidenceAnalyzer.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/tk/analysis/TkQueryAnalysis.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/tk/analysis/TkQueryAnalyzer.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/tk/model/TkEvidenceItem.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/tk/model/TkOverlapClassification.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/tk/model/TkOverlapRequest.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/tk/model/TkOverlapResponse.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/tk/model/TkOverlapType.java`
- `ip-sakti-backend/src/test/java/com/ipsakti/ip_sakti_backend/api/TkOverlapControllerTest.java`
- `ip-sakti-backend/src/test/java/com/ipsakti/ip_sakti_backend/tk/TkEvidenceAnalyzerTest.java`
- `ip-sakti-backend/src/test/java/com/ipsakti/ip_sakti_backend/tk/TkMultilingualRegressionTest.java`
- `ip-sakti-backend/src/test/java/com/ipsakti/ip_sakti_backend/tk/TkQueryAnalyzerTest.java`
- `Frontend/src/api/tk.ts`
- `Frontend/src/pages/TkOverlapPage.tsx`
- `docs/TK_OVERLAP_IMPLEMENTATION.md`
- `docs/PHASE_3_TK_OVERLAP_REPORT.md`

## 14. Known Limitations

- Production Supabase was not used for the local smoke test; Spring Boot was run with a process-only H2 datasource override because the configured external datasource failed locally.
- Deep RAG live rerun was attempted but did not complete in this environment; the baseline verifier still passed against the existing deep artifact.
- Kannada and Malayalam TK overlap can require preserved legal identifiers for reliable non-abstained retrieval in the current query path.
- TK overlap is a preliminary screening result, not legal advice.
- The engine cannot prove absence of TK outside the frozen corpus.
- No new TK corpus was added.
- No direct TKDL lookup was implemented.

## 15. Security Checks

- No frontend Gemini key exposure found.
- Active code/resources scan found no Bhashini references.
- No RAG dataset modification occurred.
- Error handling uses existing backend exception envelope.

## 16. Phase 4 Readiness

```text
READY WITH WARNING
```

Warning:

Phase 3 is locally verified with live Gemini and live Spring Boot/RAG smoke tests, but production Supabase and the long live deep-regression rerun remain unverified in this environment.
