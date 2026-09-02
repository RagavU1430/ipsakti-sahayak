# IP-SAKTI Sahayak — Phase 2 Multilingual Implementation

## 1. Architecture

```
Frontend (React) --language--> Spring Boot Backend --canonical English--> RAG (Python FastAPI, frozen v1.0)
      |                                   |                                    |
      | select en/hi/ta/te/kn/ml          | TranslationService                 | retrieval / reranking / citations
      |                                   v                                    v
      |                          GeminiTranslationProvider              Grounded English answer
      |                          (GEMINI_API_KEY server-side)                  |
      |                                   |<--- English answer + citations ----|
      |                          Gemini translate answer to user language       |
      |                                   | citations/ confidence unchanged     |
      |<--- translated answer + original citations -----------------------------|
```

* DO NOT build 6 RAGs, DO NOT translate corpus, DO NOT create language-specific embeddings.
* Gemini is ONLY translation layer. Legal knowledge remains frozen RAG v1.0.

## 2. Gemini Integration

* `GeminiProperties` (`ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/config/GeminiProperties.java:6`) — `prefix=gemini`, env `GEMINI_API_KEY`, `GEMINI_MODEL` (default `gemini-2.0-flash`), `GEMINI_BASE_URL` (`https://generativelanguage.googleapis.com`), timeouts 2s/10s.
* `GeminiTranslationProvider` (`multilingual/GeminiTranslationProvider.java:23`) — `RestClient` POST `/v1beta/models/{model}:generateContent?key={API_KEY}`, `temperature=0.1`, `maxOutputTokens=4000`, bearer via query param, never header/log.
* `GeminiClientConfig` (`multilingual/GeminiClientConfig.java:10`) — creates `geminiRestClient` bean and sole `TranslationProvider` bean (`GeminiTranslationProvider` — no fallback, Gemini ONLY).
* `TranslationProvider` (`multilingual/TranslationProvider.java:10`) abstraction — `translate(text, source, target)`, `isConfigured()`, `providerName()`.
* Security: key in `application.yaml:44` placeholder, `.env.example:53` placeholder, never frontend, never logs, not in `VITE_` prefix. Frontend `auth.ts` never sees key.

## 3. Supported Languages

Central registry: `question/model/Language.java:6` enum with centralized metadata:

| code | display | native |
|------|---------|--------|
| en | English | English |
| hi | Hindi | हिन्दी |
| ta | Tamil | தமிழ் |
| te | Telugu | తెలుగు |
| kn | Kannada | ಕನ್ನಡ |
| ml | Malayalam | മലയാളം |

`Language.fromJson` normalizes case, throws `IllegalArgumentException` for unsupported. `TranslationService.detect` uses Unicode blocks: Tamil `0B80-0BFF`, Telugu `0C00-0C7F`, Kannada `0C80-0CBF`, Malayalam `0D00-0D7F`, Devanagari `0900-097F`; explicit `language` param wins over detection.

Frontend: `Frontend/src/api/types.ts:1`, `components/FormControls.tsx:16`, `pages/AskPage.tsx:83`, `pages/ConversationDetailPage.tsx:117` — all expose 6 options with native names.

## 4. Environment Configuration

```
GEMINI_ENABLED=true
GEMINI_API_KEY=<your-key>   # server-side only
GEMINI_MODEL=gemini-2.0-flash
GEMINI_BASE_URL=https://generativelanguage.googleapis.com
GEMINI_CONNECT_TIMEOUT=2s
GEMINI_READ_TIMEOUT=10s
```
Set in root `.env:7`, `ip-sakti-backend/.env.example:5`. English works with zero config; non-English requires key or returns `TRANSLATION_UNAVAILABLE` (503).

## 5. API Flow

`QuestionService.answer` (`question/QuestionService.java:51`): `toCanonical(request.language) -> intent/jurisdiction resolve on canonical English -> RagClient.ask(English) -> fromCanonical(answer, metadata)` — confidence, abstention, citations, sources passed through unchanged. Same wrapping for `FormulationClassificationService:55` (ingredients/claims via `toCanonicalList`) and `RegulatoryAnalysisService:47` (engines `translateEngines:102`). `ConversationService` persists language metadata via `QuestionRequest.language`.

Contract: request `{question, jurisdiction, language}`, response `{answer, confidence, abstained, citations, sources, language, detected_language, processing_language}` — backward compatible, existing fields untouched.

## 6. Translation Prompts

**Query (Indic→EN)**: "You are a translation component... Translate ... into English. Translation only. Do not answer... Preserve legal terminology, Act names, Section numbers..." (`GeminiTranslationProvider:114`).

**Answer (EN→Indic)**: "Translate the supplied authoritative answer from English into {target}... Preserve citations, document names, section references... The answer was generated from authoritative evidence..." (`GeminiTranslationProvider:139`).

## 7. Legal Terminology Handling

Regex `LEGAL_PATTERN` (`GeminiTranslationProvider:28`) protects `Section 3(p)`, `Section 3(e)`, `Rule \d+`, `Article \d+`, `Patents Act 1970`, `Trade Marks Act 1999`, `GRATK`, `ABS`, etc. via `__LEGAL_REF_N__` placeholders before translation, restored after. Ensures `3(p)` not morphed.

## 8. Citation Preservation

Citations/sources are never sent to Gemini. `QuestionService:88-90` maps `RagCitation`/`RagSource` directly. Answer-only translation (`fromCanonical` text) + original `citations` objects (`service.py:178` path) guarantees `document_id`, `chunk_id`, `page`, `section` unchanged.

## 9. Confidence Preservation

`ragResponse.confidence()` passed directly to `QuestionResponse` (`QuestionService:82`). Gemini never recomputes. `RAGService.service.py:193` confidence from `calculate_confidence` unchanged.

## 10. Abstention Preservation

`ragResponse.abstained()` mapped directly (`QuestionService:83`). `Formulation/Regulatory` status `INSUFFICIENT_EVIDENCE` preserved. Translation only translates abstention message text.

## 11. Error Handling

`GeminiTranslationProvider:219` maps 400/401/403/404 → `TRANSLATION_UNAVAILABLE` (503), 429 with 1 retry, 5xx with 1 retry, timeout → `TRANSLATION_TIMEOUT` (504) with 1 retry, else `TRANSLATION_UNAVAILABLE`/`TRANSLATION_UNEXPECTED_STATUS`/`TRANSLATION_MALFORMED_RESPONSE`. No fallback — Gemini is ONLY provider. English never requires translation. Frontend `ErrorNotice.tsx:8` shows `Translation is not configured` for any `TRANSLATION_*` code.

## 12. Rate Limiting

Max 1 query translation + 1 answer translation per request (English 0). Simple in-memory `ConcurrentHashMap` cache (<1000 entries) in `GeminiTranslationProvider:35` dedups identical short texts. No concurrent unbounded fan-out.

## 13. Security

Key server-side only (`GeminiProperties:11`), `spring-dotenv` loads `.env` server-side, `SecurityConfig:20` lists `GeminiProperties`, no `VITE_GEMINI`. Logs omit key, only `sourceLanguage/targetLanguage/model/latency`. Existing auth preserved (`SecurityConfig:80`, `JwtAuthenticationFilter`).

## 14. Testing

`ip-sakti-backend/src/test/java/com/ipsakti/ip_sakti_backend/multilingual/GeminiMultilingualTest.java:7` — 10 tests: 6-language registry, unsupported throws, native names, script detection for all 6, English passthrough, Hindi→EN via Gemini mock, all Indic→EN, answer legal identifier preservation, same-language no-call, legal terms. `ip-sakti-rag/dataset/evaluation/multilingual/multilingual_cases.json` — 30 cases (6×5: patent/trademark/TK/formulation/out_of_corpus). `ip-sakti-rag/tests/test_multilingual.py` — citation integrity, abstention, case count, terminology, frozen RAG check.

## 15. Performance

Gemini client reused (`RestClient` bean), not per-request init. No major RAG optimization in Phase 2 (baseline P50 4597ms warning preserved). Translation adds ~ 300-800ms per direction when key present; cache reduces repeat cost.

## 16. Known Limitations

* Gemini requires `GEMINI_API_KEY` — without it non-English returns 503 (by design, English unaffected).
* Detection heuristic uses Unicode blocks only — mixed-script or pure ASCII Hindi/Telugu transliteration falls to EN.
* Placeholder protection covers common legal patterns; highly novel citation formats may not be protected (tested not needed).
* `__LEGAL_REF_N__` cache sized 1000, TTL-less in-memory (restart clears).
