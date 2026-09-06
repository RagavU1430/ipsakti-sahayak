# Intelligent Routing Pre-Implementation Audit

Date: 2026-09-03

## Baseline

- Spring Boot backend tests: **172 passed, 0 failed** (`.\\mvnw.cmd test`).
- Python RAG tests: **75 passed, 0 failed, 30 skipped**, with 6 warnings. Skips are credential/infrastructure dependent.
- Frontend tests: **27 passed, 0 failed** across 8 test files.
- Frontend production build: **PASS** (71 modules transformed).
- Canonical `chunks.jsonl` SHA-256: `827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d` (**MATCH**).
- Live services were not listening on ports 5173, 8080, 8000, or 8765 during baseline inspection.

## Existing request path

`POST /api/v1/questions` -> `QuestionController` -> `QuestionService` -> canonical English translation -> intent/jurisdiction classification -> `RagClient` -> FastAPI `POST /api/v1/ask`.

`VoiceService` transcribes audio and then calls either `QuestionService` or `ConversationService.askInConversation`, so text and voice already converge on the same question service.

## Root cause

`QuestionService.answer` unconditionally creates `RagAskRequest` and calls `RagClient.ask`, including when `QuestionIntentClassifier` returns `GENERAL`. Consequently greetings and ordinary general questions still invoke retrieval.

## Existing components to preserve

- Translation and six-language canonical-English processing.
- Existing RAG client and complete FastAPI grounding/citation/confidence/abstention pipeline.
- Gemini connection properties, model candidates, timeout configuration, and shared `RestClient`.
- Conversation persistence and voice STT/TTS entry points.
- Specialized formulation, regulatory, and TK endpoints.
- Current frontend/backend authentication boundary.

## Implementation boundary

The router will be placed in the Spring question service after translation and before RAG. It will never answer a question. A provider-neutral general-generation interface will use the configured Gemini client. Legal, regulatory, official-source, uploaded-document, and inherited legal-context requests will have a deterministic RAG override. General responses will have no citations and a null confidence. Existing RAG response evidence will pass through unchanged.

## Verification caveats

The baseline did not prove live Gemini, RAG-service, or Supabase-cloud connectivity. Those remain subject to configured credentials and network availability and must not be reported as passing unless exercised.
