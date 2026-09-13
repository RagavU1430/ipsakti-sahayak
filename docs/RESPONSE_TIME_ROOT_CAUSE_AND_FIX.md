# Response-time root-cause audit and fix

Date: 2026-09-12

## Scope

The measured user path was:

`AskPage -> conversation API -> QuestionService -> RAG API -> grounded answer -> Supabase history persistence -> AskPage render`

The direct question endpoint was measured separately so RAG/answer time could be distinguished from conversation persistence time.

## Root causes

1. **Cold RAG initialization.** The health endpoint could report healthy before the 7,019-chunk local corpus was parsed and indexed. The first user request therefore paid the initialization cost.
2. **Repeated immutable vector work.** Local vector search recalculated every document-vector norm on every request even though the frozen corpus does not change.
3. **Unnecessary remote generation for simple evidence lookups.** Definition, duration, purpose, and exact legal-reference questions were sent to Gemini after retrieval even though the existing extractive grounded generator could answer them from selected evidence. Complex synthesis remains on Gemini.
4. **Unbatched remote history writes.** `askInConversation` was not transactional. Its calls to other `@Transactional` methods were self-invocations, so Spring proxy transaction interception did not occur. Saves for the user message, assistant message, citation, source, and conversation metadata caused separate commits to the Supabase PostgreSQL instance in Australia.
5. **Duplicate conversation ownership lookup.** Assistant persistence queried the same conversation again even though the verified managed entity was already available.
6. **First-message creation overhead.** A new chat performs conversation creation before asking. This remains a deliberate consistency cost so the first response is guaranteed to appear in history.

The frontend does not retry successful questions and does not eagerly synthesize audio. Audio is generated only when playback is requested, so TTS was not the text-response bottleneck.

## Fixes

- Initialize the RAG service during FastAPI lifespan startup, before readiness.
- Cache immutable document-vector norms and iterate only over query terms for dot products. The original TF-IDF formula and ranking behavior are preserved.
- Use the existing citation-validated extractive generator for safe simple-answer intents; retain the configured remote generator for complex questions.
- Make the public conversation orchestration method transactional so Spring applies one transaction to the history write set.
- Reuse the already verified conversation entity during assistant persistence.
- Add an overall `conversation_response_ready ... latencyMs=` log event.
- Add `RAG_FAST_EXTRACTIVE_ENABLED` as an explicit rollback/configuration switch.

## Live measurements

Measurements are wall-clock times against live local services, Gemini, and the configured remote Supabase database. Provider and Internet variation means they are samples, not hard real-time guarantees.

| Path | Before | After | Improvement |
|---|---:|---:|---:|
| Fresh RAG HTTP request | 7.869 s | 3.680 s | 53.2% |
| English question API | 2.560 s | 1.419 s (best measured) | 44.6% |
| Tamil question API | 5.126 s | 3.568 s | 30.4% |
| Same English direct API during final run | 2.673 s | 2.328 s | provider-variable |
| Same English conversation API | 9.667 s | 4.729 s | 51.1% |
| Real browser, submit to visible answer | 9.859 s | 7.560 s | 23.3% |

The browser result includes creation of a new conversation (about 1.1-1.3 seconds in the captured run), remote user/history persistence, RAG processing, response persistence, JSON transfer, and React rendering.

## Verification

- Backend: 185 tests passed, 0 failed, 0 skipped on the final code; conversation-focused tests also passed after persistence refactoring.
- RAG: 77 passed, 30 skipped, 0 failed.
- Frontend: 32 passed, 0 failed.
- Frontend production build: passed.
- Browser: grounded answer rendered with citation, confidence, source, and audio control.
- Frozen corpus SHA-256 before and after: `827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d`.

## Remaining limits

- Indic requests require query and answer translation and therefore remain slower than English.
- Complex synthesis still depends on Gemini latency and may occasionally exceed the measured values.
- Supabase is geographically remote from the development machine. Moving the database to a region closer to users would lower history latency but is an infrastructure decision, not a retrieval change.
- The first message must create a conversation before asking. A future combined create-and-ask endpoint could remove one browser round trip while keeping history atomic.
