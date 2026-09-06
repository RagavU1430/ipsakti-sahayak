# Intelligent Query Routing

## Purpose

The conversational API now presents one assistant with two intelligence routes. Routing happens in Spring Boot after language normalization and before the Python RAG service.

```text
text or voice -> canonical English -> QueryRouter
    GENERAL    -> GeneralLlmProvider (Gemini)
    DOMAIN_RAG -> existing RagClient -> retrieval/grounding/citations
    AMBIGUOUS  -> deterministic clarification
    UNSUPPORTED-> safe refusal
-> translate response to requested language -> unified response
```

Specialized formulation, regulatory, and TK endpoints are unchanged.

## Routing policy

`DefaultQueryRouter` combines query shape, conversational intent, authority-seeking intent, detected subject domain, explicit document references, jurisdiction, and recent conversation route. It is not a single `contains("patent")` switch: a simple definition such as “What is a patent?” is general, while “What does the Patents Act require?” is forced to RAG.

Deterministic high-risk overrides cover statutory sections, Indian law, infringement, registration/compliance, ABS, biological resources, TK, GRATK, PPVFR, FSSAI, and official-source requests. Uploaded/provided-document references always select RAG. If a previous assistant response used RAG, dependent follow-ups such as “Explain it simply” inherit that domain. Low-information standalone questions clarify; legal uncertainty prefers RAG.

## Provider boundary

`GeneralLlmProvider` is provider-neutral. `GeminiGeneralLlmProvider` is the current implementation and uses the existing Gemini connection and ordered model candidates. It sends the API key in the `x-goog-api-key` header, does not log it, tries configured fallback models for transient/model failures, and stops on authentication rejection.

The general prompt forbids legal, regulatory, patentability, compliance, and document-grounded conclusions. General responses contain no citations, no sources, and no fabricated numeric confidence.

## Response contract

New fields are additive:

```json
{
  "route": "GENERAL",
  "domain": null,
  "routing_reason": "CASUAL_CONVERSATION",
  "answer": "Hello! How can I help you today?",
  "answerType": "general_fallback",
  "confidence": null,
  "abstained": false,
  "citations": [],
  "sources": []
}
```

For RAG, `route` is `RAG`, `domain` is populated where detected, and the existing confidence/citations/sources are preserved. `AMBIGUOUS` and `UNSUPPORTED` return a stable safe response with null confidence and empty evidence. Conversation and voice immediate responses also carry route/domain while retaining legacy constructors and fields.

## Configuration

General generation reuses:

- `GEMINI_ENABLED`
- `GEMINI_API_KEY`
- `GEMINI_MODEL`
- `GEMINI_FALLBACK_MODELS`
- `GEMINI_BASE_URL`
- `GEMINI_CONNECT_TIMEOUT`
- `GEMINI_READ_TIMEOUT`

No secret is sent to the frontend. If Gemini is not configured or all candidates fail, the API returns a controlled `503` error with a stable error code.

## Observability

Structured log events include `question_routed` with request ID, route, domain, reason, routing confidence, and routing latency. General-generation logs contain provider/model and status only. Existing RAG timing and evidence logs remain unchanged. User question text and secrets are not logged by the router.

## Known limitations

- Routing semantics are a deterministic lightweight query classifier rather than a separately hosted embedding/LLM classifier. This keeps greetings local and failure-free, but the labeled evaluation corpus must grow with production misroutes.
- Conversation context currently inherits the most recent assistant route/domain; it does not summarize arbitrarily long conversations.
- Document references route to the existing RAG corpus. A future uploaded-document index must provide the actual per-user document retrieval layer.
- Historical conversation rows do not persist the new route/domain as dedicated columns; immediate responses expose them and older rows remain backward compatible.
