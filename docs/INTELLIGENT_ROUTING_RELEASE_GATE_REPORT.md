# Intelligent Routing Release Gate Report

Date: 2026-09-03

## Implemented

- Shared GENERAL / DOMAIN_RAG / AMBIGUOUS / UNSUPPORTED router before RAG.
- High-risk authoritative/legal and document overrides.
- Provider-neutral general LLM boundary with Gemini implementation and ordered model fallback.
- Null confidence and empty evidence for general answers.
- RAG evidence/confidence/abstention preservation.
- Conversation route inheritance and shared text/voice path.
- Additive route/domain contracts for question, conversation, and voice responses.
- Restrained frontend distinction between general and evidence-backed responses.
- Structured routing telemetry without query or secret logging.

## Verification status

- General routing: **PASS**
- RAG routing: **PASS**
- High-risk routing: **PASS**
- Ambiguous routing: **PASS**
- Multilingual routing architecture and six-language tests: **PASS**
- Live multilingual sample: **PASS (Hindi)**
- Document routing decision: **PASS**
- Uploaded-document retrieval: **NOT IMPLEMENTED / OUTSIDE CURRENT CORPUS**
- Conversation routing: **PASS (automated context test)**
- Voice routing: **PASS (shared service and existing equivalence tests)**
- Live microphone/STT/TTS: **UNVERIFIED**
- General LLM: **PASS (live Gemini response)**
- RAG regression: **PASS**
- Citation integrity preservation: **PASS**
- Confidence semantics: **PASS**
- Abstention regression: **PASS**
- Authentication/authorization regression: **PASS (automated)**
- Frontend: **PASS**
- Backend: **PASS**
- Supabase cloud: **UNVERIFIED** (the class named `SupabaseCloudVerificationTest` runs against test-profile H2, not cloud Supabase)
- Dataset integrity: **PASS**, SHA-256 `827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d`
- Performance: **PASS WITH WARNINGS** (routing itself avoids network calls; external-provider latency remains high)
- Live API: **PASS**
- Brave browser: **PASS on configured `localhost` origin**

## Release decision

**ROUTING RELEASE STATUS: RELEASE BLOCKED**

The intelligent routing change itself passes its code, API, RAG, and browser gates. The complete production gate cannot be declared ready because real Supabase-cloud lifecycle verification and live microphone/STT/TTS verification were not executed, and uploaded-document retrieval is not implemented even though document references are safely routed to RAG. These limitations are not hidden or reclassified as passes.

Final automated results: backend 176/176 passed; frontend 27/27 passed and production build passed; RAG 75 passed, 30 environment-dependent skips, 6 warnings.

## Next actions

1. Run the lifecycle/isolation test against a dedicated non-production Supabase project and rename the existing H2 test to avoid overstating its scope.
2. Exercise microphone permission, Gemini STT, response routing, and TTS for all six languages in an approved browser environment.
3. Add a tenant-isolated uploaded-document index before advertising uploaded-PDF grounding.
4. Align local hostnames (`localhost` versus `127.0.0.1`) or use same-origin relative API calls for packaged frontend testing.
5. Expand the routing corpus with anonymized production misroutes and report per-language confusion matrices.
