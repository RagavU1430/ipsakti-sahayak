# Intelligent Routing Test Report

Date: 2026-09-03

## Automated routing evaluation

- Dataset: `ip-sakti-backend/src/test/resources/intelligent-routing-evaluation.csv`
- Labeled queries: **125**
- Categories: GENERAL 50, DOMAIN_RAG 60, AMBIGUOUS 10, UNSUPPORTED 5
- Exact classifications: **125/125**
- RAG precision: **1.0000**
- RAG recall: **1.0000**
- High-risk legal false negatives: **0**
- General-to-RAG false positives: **0**
- Additional tests: RAG conversation-context inheritance and all-six-language canonical-route invariance.
- Gemini provider test: primary-model 404 correctly fell through to the configured fallback; the API key was asserted in the request header and absent from the URL.

These are routing-classifier metrics only. They are not new RAG retrieval or answer-quality measurements.

## Regression suites

- Backend: **176/176 passed**, 0 skipped.
- RAG: **75 passed, 30 skipped, 6 warnings** in 118.27 seconds. Skips are environment/provider dependent.
- Frontend: **27/27 passed** across 8 files.
- Frontend build: **PASS**, 71 modules transformed.

## Live API

Local topology: frontend/static Spring service `localhost:8080` -> RAG `127.0.0.1:8765`.

- `Hi`: HTTP 200, GENERAL, null confidence, zero citations/sources, 5507 ms.
- `What is Section 3(p) of the Patents Act?`: HTTP 200, RAG/PATENT, grounded, confidence 0.6933, one citation/source, 14410 ms.
- `It?`: HTTP 200, AMBIGUOUS, clarification, null confidence, zero citations/sources, 814 ms.
- Missing question: HTTP 400.
- Unicode-safe Hindi greeting: HTTP 200, GENERAL, requested/detected Hindi, canonical English processing, null confidence, zero citations; 10616 ms.

These are individual observed requests, not percentile claims.

## Browser verification

Brave loaded `http://localhost:8080/ask`. A general query visibly showed “General information”, no confidence badge, and no evidence panels. A Section 3(p) query visibly showed “Evidence-backed”, confidence, Patents Act citations, section/page metadata, and source score. A first attempt from `127.0.0.1` exposed the expected origin mismatch with the frontend's configured `localhost` API URL; retesting on the configured origin succeeded.

## Performance sample

Thirty live AMBIGUOUS requests (no external provider call) measured end-to-end through Spring:

- p50: **515.22 ms**
- p95: **703.96 ms**
- p99: **808.64 ms**
- min/max: **406.75 / 1161.59 ms**

The general and RAG paths include external Gemini/RAG generation latency and were materially slower in the individual samples above.
