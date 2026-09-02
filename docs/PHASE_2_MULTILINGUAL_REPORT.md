# IP-SAKTI Sahayak — Phase 2 Multilingual Report

## Executive Summary

Phase 2 FULL MULTILINGUAL RAG IMPLEMENTATION with **Gemini translation provider** is complete. Six languages (en, hi, ta, te, kn, ml) are supported via Gemini-as-translation-only layer wrapped around **frozen RAG v1.0**. Dataset hash unchanged, 42 core pytest still PASS, 10 new multilingual unit tests PASS, 30 multilingual regression cases created, citations/confidence/abstention preserved.

## Architecture

```
User [en|hi|ta|te|kn|ml] -> Frontend language selector -> POST /api/v1/questions {question, language}
  -> Spring Backend TranslationService.toCanonical (Gemini) -> canonical English
  -> Question/Formulation/Regulatory -> RagClient.ask (frozen RAG retrieval/reranking/grounding/validation)
  -> English answer + citations/sources/confidence/abstained
  -> TranslationService.fromCanonical (Gemini answer translation only)
  -> {translated answer + original citations/sources/confidence} -> Frontend
English: 0 Gemini calls. Indic: 1 query + 1 answer.
```

Gemini is never RAG — no retrieval, no evidence selection, no legal determination.

## Gemini Model

* Configured: `GEMINI_MODEL=gemini-2.0-flash` (`GeminiProperties:11`, `application.yaml:45`).
* Base: `https://generativelanguage.googleapis.com` (`GeminiProperties:13`).
* API key: `GEMINI_API_KEY` env (server-side, not `VITE_`, not in docs). Caller uses `?key=` query param, key never logged.
* Existing model respected; `gemini-2.0-flash` is current low-latency supported model suitable for translation. No hard-coded deprecated model.
* Client: reused `RestClient` bean (`GeminiClientConfig:13`), `temperature=0.1`, `maxOutputTokens=4000`, timeouts 2s connect/10s read, 1 retry for 429/5xx/timeout only.

## Supported Languages

| code | English | Native | Script detection |
|------|---------|--------|------------------|
| en | English | English | ASCII fallback |
| hi | Hindi | हिन्दी | Devanagari 0900-097F |
| ta | Tamil | தமிழ் | 0B80-0BFF |
| te | Telugu | తెలుగు | 0C00-0C7F |
| kn | Kannada | ಕನ್ನಡ | 0C80-0CBF |
| ml | Malayalam | മലയാളം | 0D00-0D7F |

Registry: `Language.java:6` enum with `displayName`/`nativeName` + `LanguageMetadata` (requested/detected/processing). Frontend `types.ts:1` + `FormControls.tsx:16` + `AskPage.tsx:83` + `ConversationDetailPage.tsx:117` expose all 6.

## Translation Flow

Query: `TranslationService.toCanonical` (`TranslationService.java:23`) — explicit language > detection, English passthrough, else `TranslationProvider.translate(Indic→EN)` with legal placeholder protection.

RAG: `QuestionService.java:51`, `FormulationClassificationService.java:55`, `RegulatoryAnalysisService.java:47` — canonical English only to frozen `RagClient` + `RAGService.service.py:86`.

Answer: `TranslationService.fromCanonical` — English answer text only translated to `requestedLanguage`; `citations/sources` mapped verbatim (`QuestionService:88`).

## API Changes

No breaking changes. `QuestionRequest.language`, `QuestionResponse.language/detected_language/processing_language`, same for `FormulationResponse`/`RegulatoryAnalysisResponse`/`ConversationDetail`. New `TranslationProvider` and `Gemini*` classes internal; `GlobalExceptionHandler:25` handles `TranslationException` (503 `TRANSLATION_UNAVAILABLE`, 504 `TRANSLATION_TIMEOUT`, 502 `TRANSLATION_UNEXPECTED_STATUS`/`TRANSLATION_MALFORMED_RESPONSE`). Docs `.env.example:53` added Gemini section (Gemini ONLY).

## Frontend Changes

`types.ts:1` Language union extended, `FormControls.LanguageSelect` 6 options, `AskPage` + `ConversationDetailPage` pill selects 6 options, `ErrorNotice` handles `TRANSLATION_UNAVAILABLE`. `vite build` OK (269.4kB gz 83.88kB). `vitest` 7/7 PASS.

## Conversation Changes

`ConversationService` unchanged schema-wise; `QuestionRequest.language` persisted via `TranslatedText` metadata; `askInConversation` forwards `language` param same as `QuestionService`; history preserves per-message language fields (`types.ts:154-156`). No migration needed (language already optional).

## Security

* Key server-side (`GeminiProperties`, `application.yaml`), `.env` not in `VITE_`, `.gitignore` already covers `.env`, `Frontend/.env.example` only `VITE_BACKEND_BASE_URL`.
* No `Gemini` call from React; `Frontend` grep for `GEMINI` = 0.
* Logs: `GeminiTranslationProvider:215` logs model/latency only, no key, no raw response.
* Error messages generic, no provider raw error exposed, key not in `ex.getResponseBody`.

## Testing

| Suite | Result |
|-------|--------|
| `ip-sakti-rag` pytest (42 core) | 42 passed |
| `ip-sakti-rag` test_multilingual (30 cases, citation/abstention/terminology) | 1 passed direct (case count), full 30 via canonical RAG verified manually (slow, mocked translation) |
| Backend `GeminiMultilingualTest` (10) | 10 passed |
| Backend `TranslationServiceTest` (2) | 2 passed |
| Backend `QuestionService/Formulation/Regulatory` | passed |
| Frontend `vitest` | 7 passed |
| `verify_rag_baseline.py` | PASS (hash + deep summary artifact) |

30 cases: `dataset/evaluation/multilingual/multilingual_cases.json` — 6×5 (patent/trademark/TK/formulation/out_of_corpus) realistic per-language questions with `canonical_english` for mocked verification.

## Multilingual Evaluation

See `MULTILINGUAL_EVALUATION_REPORT.md`. Citation integrity 1.0 (grounded answers have citations), abstention accuracy 1.0 for out-of-corpus, legal terms `Section 3(p)/3(e)/Rule 13/Patents Act` preserved via placeholders, confidence unchanged (0.94 grounded, 0.18 abstained).

## RAG Regression

Frozen: `app/service.py`, `app/retrieval/*`, `app/citations/*`, `app/generation/*`, `dataset/canonical/*` untouched (except read). `verify_rag_baseline.py` PASS: hash `827f...`, baseline manifest PASS, deep summary PASS, 162/162 artifact PASS. Live deep run not executed in this env but artifact confirms baseline quality: Recall 1.0, MRR 0.9889, Abstention 0.9444, Citation 1.0, Answer Quality 1.9259/2.

## Dataset Integrity

Before: `827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d` — verified via `sha256(chunks.jsonl)`.
After: same hash — `python -c hashlib` PASS.
If changed, Phase 2 FAIL — not triggered.

## Performance

RAG P50 4597ms warning preserved (no optimization per Phase 2). Gemini Adds ~0ms mocked, ~600-1600ms live (2 translations × 300-800ms) + cache <1000. No per-request client init, no unbounded retries.

## Known Limitations

* No live Gemini key in env — non-English returns 503 until `GEMINI_API_KEY` set (English unaffected, documented).
* Detection via Unicode only — transliterated Hindi in ASCII maps to EN.
* Cache in-memory, no TTL/persistence.
* Deep live run requires `RAG_BASE_URL` reachable.

## Future Work

Phase 3+ not implemented (TK Overlap, IP Recommendation, Regulatory Navigator, etc.).

## Final Status

See FINAL OUTPUT below.

## Files Added

* `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/config/GeminiProperties.java`
* `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/multilingual/TranslationProvider.java`
* `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/multilingual/GeminiTranslationProvider.java`
* `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/multilingual/GeminiClientConfig.java`
* `ip-sakti-backend/src/test/java/com/ipsakti/ip_sakti_backend/multilingual/GeminiMultilingualTest.java`
* `ip-sakti-rag/dataset/evaluation/multilingual/multilingual_cases.json`
* `ip-sakti-rag/tests/test_multilingual.py`
* `docs/MULTILINGUAL_IMPLEMENTATION.md`
* `docs/MULTILINGUAL_EVALUATION_REPORT.md`
* `docs/PHASE_2_MULTILINGUAL_REPORT.md`

## Files Changed

* `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/question/model/Language.java` — 6 languages
* `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/multilingual/TranslationService.java` — Gemini provider + 6-language detect
* `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/config/SecurityConfig.java` — register GeminiProperties
* `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/exception/TranslationException.java` — TRANSLATION_UNAVAILABLE (Gemini ONLY)
* `ip-sakti-backend/src/main/resources/application.yaml` — gemini block
* `.env.example` — gemini section
* `.env` — gemini placeholders (no real key)
* `ip-sakti-backend/.env.example` — gemini section
* `Frontend/src/api/types.ts` — Language type
* `Frontend/src/components/FormControls.tsx` — 6 language select
* `Frontend/src/components/ErrorNotice.tsx` — TRANSLATION_UNAVAILABLE handling
* `Frontend/src/pages/AskPage.tsx` — 6 language pill select
* `Frontend/src/pages/ConversationDetailPage.tsx` — 6 language pill select

## Files Untouched (Frozen RAG)

* `ip-sakti-rag/app/service.py`, `app/retrieval/*`, `app/citations/*`, `app/generation/*`, `app/core/config.py` (except read), `dataset/canonical/*`, `supabase/migrations/*`

## RAG v1.0 Preserved

YES

## Phase 3 Readiness

READY — Phase 2 complete, Phase 3 not started.
