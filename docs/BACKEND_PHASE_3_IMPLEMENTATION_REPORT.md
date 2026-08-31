# Backend Phase 3 Implementation Report

Date: 2026-08-31

Scope: Phase 3 only — Core Product Question API.

## 1. Existing Backend Architecture

The Spring Boot backend already had Phase 1/2 foundations:

- Environment-based configuration.
- Security configuration with dev/prod API-key mode.
- Supabase/JWT/Bhashini/RAG configuration properties.
- `POST /api/v1/ask` compatibility endpoint.
- Typed `RagClient` for Python RAG `POST {RAG_BASE_URL}/api/v1/ask`.
- RAG request/response/citation/source DTOs.
- Controlled global error handling.

The existing Python RAG service remains the knowledge intelligence layer. It still owns retrieval, legal metadata filtering, reranking, grounded generation, citation validation, confidence scoring, abstention, and general fallback behavior.

## 2. Phase 3 Architecture

Implemented a product-facing orchestration layer:

```text
Frontend
  -> POST /api/v1/questions
  -> QuestionController
  -> QuestionService
  -> QuestionIntentClassifier
  -> JurisdictionResolver
  -> RagClient
  -> Python RAG /api/v1/ask
  -> frontend-safe QuestionResponse
```

Spring Boot does not answer legal questions directly. It classifies lightweight product metadata, calls RAG, and maps the RAG result into a frontend-facing response.

## 3. New API Endpoint

```http
POST /api/v1/questions
Content-Type: application/json
```

This is now the main product-facing question endpoint.

Backward compatibility preserved:

```http
POST /api/v1/ask
```

still exists and remains wired to the Phase 2 RAG client.

## 4. Request Schema

```json
{
  "question": "Can a classical Ayurvedic formulation be patented?",
  "jurisdiction": "INDIA",
  "language": "en"
}
```

Fields:

- `question`: required, non-blank, normalized for whitespace, max 4000 characters.
- `jurisdiction`: optional; supported values are `INDIA`, `INTERNATIONAL`, `AUTO`; default is `AUTO`.
- `language`: optional; supported values are `en`, `hi`, `ta`; default is `en`.

No Bhashini translation is implemented in Phase 3. The language value is preserved for future multilingual work.

## 5. Response Schema

```json
{
  "answer": "...",
  "answerType": "rag_grounded",
  "confidence": 0.94,
  "abstained": false,
  "jurisdiction": "INDIA",
  "language": "en",
  "intent": "PATENT",
  "citations": [
    {
      "document": "Patents Act, 1970",
      "documentId": "IND-PAT-ACT-1970",
      "page": 12,
      "section": "Section 3",
      "authority": "Parliament of India",
      "sourceUrl": "...",
      "chunkId": "..."
    }
  ],
  "sources": [
    {
      "documentId": "IND-PAT-ACT-1970",
      "score": 0.95
    }
  ]
}
```

Supported `answerType` values:

- `rag_grounded`
- `general_fallback`
- `abstained`

## 6. Intent Handling

Added a lightweight deterministic `QuestionIntentClassifier`.

Supported internal intent values:

- `IP_GENERAL`
- `PATENT`
- `TRADEMARK`
- `COPYRIGHT`
- `DESIGN`
- `GI`
- `PLANT_VARIETY`
- `BIODIVERSITY_ABS`
- `AYURVEDA_REGULATION`
- `INTERNATIONAL_IP`
- `GENERAL`

The classifier is deliberately conservative. It is for routing/observability only and does not make legal decisions.

RAG domain hints are passed only where useful:

- `PATENT` -> `PATENT`
- `TRADEMARK` -> `TRADEMARK`
- `COPYRIGHT` -> `COPYRIGHT`
- `DESIGN` -> `DESIGN`
- `GI` -> `GI`
- `PLANT_VARIETY` -> `PLANT_VARIETY`
- `BIODIVERSITY_ABS` -> `ABS`
- `AYURVEDA_REGULATION` -> `AYURVEDA`
- `INTERNATIONAL_IP` -> `INTERNATIONAL`
- `IP_GENERAL` / `GENERAL` -> no domain hint

This avoids blocking valid RAG retrieval when classification is uncertain.

## 7. Jurisdiction Handling

Added `JurisdictionResolver`.

Behavior:

- Explicit `INDIA` is preserved and sent to RAG as `INDIA`.
- Explicit `INTERNATIONAL` is preserved and sent to RAG as `INTERNATIONAL`.
- `AUTO` uses only obvious question signals.
- If `AUTO` is uncertain, the backend response keeps `AUTO` and sends no jurisdiction filter to RAG.

The backend does not silently invent a jurisdiction.

## 8. RAG Integration

`QuestionService` reuses the existing Phase 2 `RagClient`.

RAG request built by Phase 3:

```json
{
  "question": "...",
  "domain": "PATENT",
  "jurisdiction": "INDIA",
  "top_k": null
}
```

`domain`, `jurisdiction`, and `top_k` remain optional in the RAG request.

No RAG internals were duplicated in Spring Boot.

## 9. Abstention Handling

If RAG returns `abstained=true`, Spring Boot preserves:

- original answer text
- `abstained=true`
- confidence
- citations
- sources

Spring Boot does not replace abstentions with generic answers.

## 10. Citation Handling

Citations are passed through from RAG and mapped into frontend camelCase:

- `document`
- `documentId`
- `page`
- `section`
- `authority`
- `sourceUrl`
- `chunkId`

Spring Boot does not regenerate, alter, or invent citations.

## 11. Confidence Handling

Confidence remains owned by the RAG layer.

Spring Boot preserves the numeric confidence value exactly as returned by RAG.

## 12. Error Handling

Existing global error handling applies to the new endpoint:

- Invalid request/malformed JSON: `400 INVALID_REQUEST`
- RAG timeout: `504 RAG_TIMEOUT`
- RAG unavailable: `503 RAG_UNAVAILABLE`
- Malformed RAG response: `502 RAG_MALFORMED_RESPONSE`
- Unexpected RAG HTTP status: `502 RAG_UNEXPECTED_STATUS`
- Unexpected backend failure: `500 INTERNAL_ERROR`

No stack traces, credentials, RAG URL, or internal infrastructure details are returned to API consumers.

## 13. Security Behavior

Phase 3 respects `SecurityConfig`.

In dev mode:

- `/api/v1/questions`
- `/api/v1/questions/health`
- `/api/v1/ask`
- `/health`

are allowed for local development.

In prod/non-dev mode:

- `/api/v1/questions` requires `X-API-Key`.
- Missing/invalid API key returns `401 UNAUTHORIZED`.

Logging added by `QuestionService` includes:

- generated `questionId`
- intent
- jurisdiction
- language
- answer type
- confidence
- latency
- question length

It does not log the full question or secrets.

## 14. Tests Executed

### Backend tests

Command:

```powershell
$env:MAVEN_USER_HOME=(Join-Path (Get-Location) '.m2home')
.\mvnw.cmd "-Dmaven.repo.local=.m2home\repository" test
```

Result:

```text
Tests run: 32, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Backend test breakdown:

```text
AskControllerTest: 3 passed
QuestionControllerSecurityTest: 2 passed
QuestionControllerTest: 12 passed
PropertiesBindingTest: 3 passed
IpSaktiBackendApplicationTests: 1 passed
QuestionServiceTest: 4 passed
RagClientTest: 7 passed
```

Phase 3 coverage includes:

- Valid grounded question.
- Valid abstained question.
- General fallback response.
- Blank question.
- Missing question.
- Very long question.
- Invalid jurisdiction.
- Invalid language.
- RAG timeout.
- RAG unavailable.
- RAG malformed response.
- Citation passthrough.
- Confidence passthrough.
- Intent classification.
- Jurisdiction preservation.
- No RAG URL leakage in safe error detail.
- Prod API-key security behavior.

### Python RAG tests

Command:

```powershell
python -m pytest
```

Run from:

```text
ip-sakti-rag
```

Result:

```text
36 passed, 5 warnings
```

## 15. Local RAG Integration Result

A real local Spring Boot -> Python RAG smoke test was executed.

Setup:

- Python RAG started locally on `127.0.0.1:18200`.
- Spring Boot started locally on `127.0.0.1:18281`.
- Spring Boot was configured with `rag.base-url=http://127.0.0.1:18200`.
- Database autoconfiguration was excluded for the smoke test to avoid requiring production Supabase credentials.

Test 1:

```json
{
  "question": "What is Section 3(p) of the Patents Act?",
  "jurisdiction": "INDIA",
  "language": "en"
}
```

Observed result summary:

```json
{
  "legalAnswerType": "rag_grounded",
  "legalConfidence": 0.6018,
  "legalCitationCount": 1,
  "legalJurisdiction": "INDIA"
}
```

Test 2:

```json
{
  "question": "What is the capital of Mars?",
  "jurisdiction": "AUTO",
  "language": "en"
}
```

Observed result summary:

```json
{
  "generalAnswerType": "general_fallback",
  "generalAbstained": false,
  "generalConfidence": 0.35,
  "generalJurisdiction": "AUTO"
}
```

This verifies that the backend calls the Python RAG service and preserves RAG response classification instead of implementing its own generation path.

## 16. Dataset Fingerprint Verification

No dataset rebuild, redownload, chunk rewrite, or manifest rewrite was performed.

Locked dataset artifact hashes checked:

```text
ip-sakti-rag/dataset/canonical/chunks.jsonl
SHA256 4CE211289E88958C89D4BAFC4EDE7271CC387C55CC1F18B73ACBE9EA30131BDA

ip-sakti-rag/dataset/canonical/documents.jsonl
SHA256 6D9B657A2FB84F6414DD7F28C7CC7550C4FE25681E6200242D38889DA6DDB7F1

ip-sakti-rag/dataset/manifests/download_manifest.json
SHA256 A0F19A145D79CF13AD3B39A4EA586BD8303BA38D1E744D99EBCDD7EFFDF2B84F

ip-sakti-rag/dataset/manifests/checksums.sha256
SHA256 D045C0845C7ECAA82F4702C616F36B933AEEE65E19BCD1BF59019A2C3D85F791

ip-sakti-rag/dataset/manifests/source_registry.csv
SHA256 C48C09F6E1AE39352F43A40FB0FB1A7CF614FECEE6A06050054CFE4FBC751A3E
```

## 17. Files Created

Backend source:

- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/api/QuestionController.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/question/QuestionService.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/question/classification/JurisdictionResolver.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/question/classification/QuestionIntentClassifier.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/question/model/AnswerType.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/question/model/Jurisdiction.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/question/model/Language.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/question/model/QuestionCitation.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/question/model/QuestionIntent.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/question/model/QuestionRequest.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/question/model/QuestionResponse.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/question/model/QuestionSource.java`

Backend tests:

- `ip-sakti-backend/src/test/java/com/ipsakti/ip_sakti_backend/api/QuestionControllerSecurityTest.java`
- `ip-sakti-backend/src/test/java/com/ipsakti/ip_sakti_backend/api/QuestionControllerTest.java`
- `ip-sakti-backend/src/test/java/com/ipsakti/ip_sakti_backend/question/QuestionServiceTest.java`

Report:

- `docs/BACKEND_PHASE_3_IMPLEMENTATION_REPORT.md`

## 18. Files Modified

- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/config/SecurityConfig.java`

This change allows the new Phase 3 endpoint in local dev mode while retaining API-key protection in prod/non-dev mode.

## 19. Secret Scan

Command searched backend source, backend `.env.example`, and docs for known key patterns.

Result:

- No newly added plaintext secrets were found.
- The only match was the pre-existing redacted credential-pattern note in `docs/BACKEND_RAG_INTEGRATION_ANALYSIS.md`.

## 20. Known Limitations

- Intent classification is lightweight and deterministic; it is not the full formulation classifier.
- Jurisdiction auto-detection is conservative and signal-based only.
- No Bhashini translation is implemented.
- No conversation persistence/user history is implemented.
- No ABS, GRATK, Section 3(p), or Section 3(e) rule engine is implemented.
- Final Maven `package -DskipTests` retry still fails because Windows cannot rename the target jar while a local process holds the artifact handle. `mvn test` compiles and tests the final source successfully.

## 21. Phase 4 Recommendation

Recommended next phase:

Phase 4 — Formulation Classifier foundation.

Suggested scope:

1. Add product/formulation request DTOs.
2. Add deterministic clarification-first classification rules.
3. Call RAG for source-grounded regulatory context.
4. Return structured classification with confidence, missing information, and citations.
5. Do not make final legal determinations without evidence.

Stop condition honored: no Phase 4 implementation was performed in this phase.

