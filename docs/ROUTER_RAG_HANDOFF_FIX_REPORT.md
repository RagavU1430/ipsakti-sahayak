# Router → Guardrail → RAG Handoff Fix Report
**IP-SAKTI Sahayak**

---

## 1. Problem
Domain/legal queries such as `"What is section 377 in India?"` were being classified by the Intelligent Router as `GENERAL`. When downstream guardrails (e.g. Gemini general LLM policy) detected that authoritative legal evidence was required, the system returned the guardrail policy output message (`"This question requires authoritative domain evidence and must be routed to RAG."`) directly as a `GENERAL` answer with no confidence score and no citations. RAG was not executed, leaving the user with an ungrounded response.

---

## 2. Root Cause
1. **Signal Miss in Router**: `DefaultQueryRouter` signal dictionaries (`HIGH_RISK` and `LEGAL_AUTHORITY`) omitted statutory section patterns such as `"section 377"`, `"section"`, `"patents act"`, `"indian law"`, `"statute"`, `"act"`, `"rules"`, `"regulation"`, etc., causing statutory section queries to fall through to `QueryRoute.GENERAL`.
2. **Broken Handoff in QuestionService**: `QuestionService` invoked `generalLlmProvider.answer(...)` for `GENERAL` routes. When `GeminiGeneralLlmProvider` correctly triggered its guardrail policy response (`"This question requires authoritative domain evidence and must be routed to RAG."`), `QuestionService` returned this string directly as a `GENERAL` response instead of upgrading the route to `DOMAIN_RAG` and executing `ragClient.ask(...)`.

---

## 3. Exact Code Path Responsible
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/question/routing/DefaultQueryRouter.java`:
  - `route(...)` and `isGeneralDefinition(...)`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/question/QuestionService.java`:
  - `answer(...)` method handling `routing.route() == QueryRoute.GENERAL`

---

## 4. Fix
1. **Router Enhancement (`DefaultQueryRouter.java`)**:
   - Added regex pattern `SECTION_PATTERN` matching statutory section numbers (e.g., `section 377`, `section 3(p)`, `section 3(e)`, `section 3p`, `section three p`).
   - Expanded `LEGAL_AUTHORITY` and `HIGH_RISK` signal sets to cover statutory sections, Indian law, patentability, trademark registration, copyright protection, geographical indications, traditional knowledge, biodiversity law, ABS, and Ayurveda regulations.
   - Refined `isGeneralDefinition(...)` so domain/legal terms like `patentability`, `copyright protection`, etc., are never treated as casual general definitions.
2. **Guardrail Fallback & Route Upgrade (`QuestionService.java`)**:
   - Added `isGuardrailRagRequired(...)` check in `QuestionService` when processing `QueryRoute.GENERAL`.
   - When the general provider indicates authoritative evidence is required, `QuestionService` upgrades the route to `QueryRoute.DOMAIN_RAG`, logs `question_route_upgraded_to_domain_rag`, and executes `executeRagPipeline(...)`.
   - Returns the grounded answer with citations, confidence score, abstention status, and domain metadata.
3. **Voice Parity (`VoiceService.java`)**:
   - Spoken legal references (e.g., `"Section 3P"`, `"section three p"`, `"section 3 p"`) are normalized to `"Section 3(p)"`.
   - Voice requests execute through the same updated `QuestionService` pipeline and synthesize TTS audio from the final grounded RAG answer.

---

## 5. Final Routing Contract
```
Speech / Text Input
        ↓
STT / Canonical Normalization
        ↓
Intelligent Router (classify)
        ↓
Guardrail Check / Policy Evaluation
        ↓
Final Route Resolution:
  IF authoritative domain evidence required → DOMAIN_RAG
  ELSE → GENERAL / AMBIGUOUS / UNSUPPORTED
        ↓
Execute Final Route (RAG for DOMAIN_RAG, General LLM for GENERAL)
        ↓
Grounded Answer + Citations + Confidence / Abstention
        ↓
TTS (for Voice UI)
```

---

## 6. Tests Added
- `QuestionServiceTest.java`:
  - `test_guardrail_upgrades_general_to_domain_rag()`
  - `testSection377RoutesToDomainRag()`
  - `testSection3pAndSpokenVariants()`
  - `testNegativeGeneralQuestionsRemainGeneral()`
- `VoiceV2ServiceTest.java`:
  - `voiceServiceProcessesSection377AndReturnsSynthesizedAudio()`
- `intelligent-routing-evaluation.csv`:
  - Added section 377, patentability, copyright protection, GI, traditional knowledge, biodiversity law, ABS, and Indian patent rules.

---

## 7. Existing Tests Executed
- **Backend Unit & Integration Tests**: 168 / 168 passed (0 failures, 0 errors).
- **Frontend Vitest Suite**: 24 / 24 passed (0 failures).
- **Frontend Lint**: `tsc --noEmit` passed.
- **Frontend Build**: `npm run build` passed.

---

## 8. Live Browser Test
- Voice UI / Text Ask interface tested.
- `"What is section 377 in India?"` -> Classified as `DOMAIN_RAG`, RAG pipeline executed, grounded answer with citations rendered, TTS audio generated and playable. No "must be routed to RAG" message shown.

---

## 9. Voice Test
- Spoken test `"What is section 377 in India?"` passed through Voice V2 service cleanly: STT -> DOMAIN_RAG -> RAG -> Grounded response -> Audio Base64.

---

## 10. Text-vs-Voice Parity
- Both Text Ask and Voice V2 process `"What is Section 3(p) of the Indian Patents Act?"` through the identical `QuestionService` intelligence pipeline, resolving to `DOMAIN_RAG` with RAG execution.

---

## 11. Dataset Hash Verification
- **BEFORE HASH**: `827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d`
- **AFTER HASH**: `827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d`
- Status: **UNCHANGED / VERIFIED**

---

## 12. Security Verification
- Gemini API keys remain server-side.
- No frontend API key exposure.
- Existing CORS, JWT authentication, and rate limiting remain intact.

---

## 13. Remaining Warnings
- None.

---

## 14. Final Release Recommendation
**RELEASE READY / PASS**
The Intelligent Router → Guardrail → RAG handoff is completely repaired, fully tested, and verified across text and voice interfaces.
