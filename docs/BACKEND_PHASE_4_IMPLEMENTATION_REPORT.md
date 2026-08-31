# Backend Phase 4 Implementation Report

Date: 2026-08-31

Scope: Phase 4 only — Interactive Formulation Classifier + Regulatory Router.

## 1. Existing Architecture

The existing backend already provided:

- `POST /api/v1/ask` compatibility endpoint.
- `POST /api/v1/questions` product question endpoint.
- Typed `RagClient` for Python RAG `POST {RAG_BASE_URL}/api/v1/ask`.
- Security configuration with dev/prod API-key mode.
- Environment-based RAG/Supabase/JWT/Bhashini placeholders.
- Controlled global error handling.

The Python RAG service remains the legal evidence layer. Phase 4 did not duplicate retrieval, embeddings, reranking, generation, citation validation, confidence scoring, or abstention.

## 2. Phase 4 Architecture

Implemented a dedicated formulation classification flow:

```text
POST /api/v1/formulations/classify
  -> FormulationController
  -> FormulationClassificationService
  -> FormulationRuleEngine
  -> FormulationClarificationService
  -> RagClient
  -> RegulatoryRouteService
  -> FormulationResponse
```

The classifier is deterministic/rule-assisted and RAG-grounded. It does not behave as “LLM sees formulation -> guesses category.”

## 3. Classification Categories

The classifier supports exactly these five primary categories:

- `CLASSICAL_DRUG`
- `PATENT_PROPRIETARY`
- `PHYTOPHARMACEUTICAL_NEW_DRUG`
- `AYURVEDA_AAHAR_NUTRACEUTICAL`
- `COSMETIC`

If the system cannot safely classify, it returns:

- `classification: null`
- `needsClarification: true`

## 4. Input Schema

Endpoint:

```http
POST /api/v1/formulations/classify
Content-Type: application/json
```

Request:

```json
{
  "productName": "Herbal Digestive Product",
  "ingredients": ["A", "B"],
  "dosageForm": "powder",
  "intendedUse": "digestive support",
  "claims": ["supports digestion"],
  "manufacturingMethod": "traditional processing",
  "classicalReference": "optional",
  "traditionalUse": true,
  "commercialIntent": true,
  "targetMarket": "India",
  "country": "India",
  "existingLicense": "optional",
  "knownClassification": "optional"
}
```

Validation:

- `productName` is required, non-blank, max 160 characters.
- `ingredients` max 25 entries.
- each ingredient max 120 characters and non-blank.
- `claims` max 20 entries.
- each claim max 180 characters and non-blank.
- text fields have bounded lengths.

All fields except `productName` are optional to support progressive questioning.

## 5. Deterministic Rules

Implemented transparent signal scoring in `FormulationRuleEngine`.

Signals include:

- Classical references and traditional formulation identity.
- Proprietary, modified, novel-combination, and IP-protection signals.
- Standardized extract, active-constituent, clinical-trial, and phytopharmaceutical/new-drug signals.
- Food, nutrition, Ayurveda Aahar, wellness-support, and daily-consumption signals.
- Cosmetic, topical, skin/hair, beauty, personal-care, and appearance signals.

The engine explicitly avoids simplistic rules such as “tablet = drug” or “herbal = nutraceutical.”

One rule-quality issue was found during testing: `non-classical formulation` initially triggered a false `classical formulation` substring match. This was fixed with phrase-aware classical detection.

## 6. Clarification Logic

Implemented `FormulationClarificationService`.

When information is insufficient or conflicting, it returns 1-4 targeted clarification questions, for example:

- “What is the primary intended purpose of the product?”
- “Is this formulation based on a recognized classical Ayurvedic text or traditional formulation?”
- “Is the product intended primarily as a medicine for treatment/prevention, or as a food/nutritional product?”
- “Is the formulation identical to a classical formulation, or has it been modified/proprietary?”

Clarification is a successful product state, not an error.

## 7. RAG Integration

The formulation classifier reuses the existing `RagClient`.

It sends a privacy-light RAG query containing product characteristics and rule context, not raw full proprietary formulation detail beyond broad user-provided claims/context.

Example RAG request shape:

```json
{
  "question": "Determine authoritative regulatory context for an Ayurvedic formulation classification...",
  "domain": "AYURVEDA",
  "jurisdiction": "INDIA",
  "top_k": null
}
```

RAG citations and sources are passed through.

If RAG abstains, the backend does not fabricate a classification.

## 8. Confidence Calculation

Confidence is deterministic and bounded to `0.0` through `1.0`.

Signals considered:

- leading rule score
- separation from second-best category
- RAG confidence
- conflicts
- missing information
- RAG abstention

The confidence score is not statistically calibrated and is not represented as a legal certainty.

## 9. Regulatory Routing

Implemented `RegulatoryRouteService`.

Routes:

- `CLASSICAL_DRUG` -> `AYUSH_CLASSICAL_DRUG`, domains `["AYURVEDA"]`
- `PATENT_PROPRIETARY` -> `AYUSH_PATENT_IP`, domains `["AYURVEDA", "PATENT"]`
- `PHYTOPHARMACEUTICAL_NEW_DRUG` -> `PHYTOPHARMACEUTICAL_NEW_DRUG`, domains `["AYURVEDA"]`
- `AYURVEDA_AAHAR_NUTRACEUTICAL` -> `AYURVEDA_AAHAR`, domains `["AYURVEDA", "FOOD"]`
- `COSMETIC` -> `COSMETIC_REGULATORY`, domains `["AYURVEDA"]`

Routes are product navigation hints, not final regulatory determinations.

## 10. Security

Phase 4 respects existing `SecurityConfig`.

In dev mode:

- `POST /api/v1/formulations/classify` is permitted for local development.

In prod/non-dev mode:

- the endpoint requires `X-API-Key`.

Logging:

- logs generated request ID, status, classification, confidence, route, and latency.
- does not log API keys, authorization headers, raw ingredient lists, or full formulation payloads.

## 11. API Examples

### Classified response

```json
{
  "classification": "AYURVEDA_AAHAR_NUTRACEUTICAL",
  "confidence": 1.0,
  "needsClarification": false,
  "questions": [],
  "reason": "Based on the structured information provided and the available authoritative knowledge sources, the product appears most consistent with AYURVEDA_AAHAR_NUTRACEUTICAL. This is a routing suggestion, not a final legal determination.",
  "status": "classified",
  "regulatoryRoute": {
    "route": "AYURVEDA_AAHAR",
    "domains": ["AYURVEDA", "FOOD"],
    "jurisdiction": "INDIA"
  },
  "citations": [],
  "sources": []
}
```

### Clarification response

```json
{
  "classification": null,
  "confidence": 0.2345,
  "needsClarification": true,
  "questions": [
    "What is the primary intended purpose of the product?",
    "Is this formulation based on a recognized classical Ayurvedic text or traditional formulation?",
    "What is the target market or country for the product?"
  ],
  "reason": "The provided information does not contain enough classification signals to suggest a category safely.",
  "status": "needs_clarification",
  "regulatoryRoute": null,
  "citations": [],
  "sources": []
}
```

## 12. Tests

### Backend tests

Command:

```powershell
$env:MAVEN_USER_HOME=(Join-Path (Get-Location) '.m2home')
.\mvnw.cmd "-Dmaven.repo.local=.m2home\repository" test
```

Result:

```text
Tests run: 49, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Breakdown:

```text
AskControllerTest: 3 passed
FormulationControllerSecurityTest: 2 passed
FormulationControllerTest: 5 passed
QuestionControllerSecurityTest: 2 passed
QuestionControllerTest: 12 passed
PropertiesBindingTest: 3 passed
FormulationClassificationServiceTest: 10 passed
IpSaktiBackendApplicationTests: 1 passed
QuestionServiceTest: 4 passed
RagClientTest: 7 passed
```

Phase 4 test coverage:

- clearly classical formulation
- clearly proprietary formulation
- clearly Ayurveda Aahar/nutraceutical formulation
- clearly cosmetic formulation
- phytopharmaceutical/new-drug-like formulation
- insufficient information
- conflicting therapeutic + food signals
- classical reference + modified/proprietary signals
- RAG unavailable
- RAG abstention
- RAG citation passthrough
- confidence range
- malformed request
- excessive payload
- prod API-key security

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

## 13. Real RAG Integration Results

A real local Spring Boot -> Python RAG smoke test was executed for the new formulation endpoint.

Setup:

- Python RAG: `127.0.0.1:18300`
- Spring Boot backend: `127.0.0.1:18381`
- Backend configured with `rag.base-url=http://127.0.0.1:18300`
- Database autoconfiguration excluded for local smoke testing so no production Supabase credentials were required.

Observed results:

```json
[
  {
    "name": "classical",
    "status": "classified",
    "classification": "CLASSICAL_DRUG",
    "needsClarification": false,
    "confidence": 1.0,
    "citationCount": 3,
    "route": "AYUSH_CLASSICAL_DRUG"
  },
  {
    "name": "aahar",
    "status": "classified",
    "classification": "AYURVEDA_AAHAR_NUTRACEUTICAL",
    "needsClarification": false,
    "confidence": 1.0,
    "citationCount": 3,
    "route": "AYURVEDA_AAHAR"
  },
  {
    "name": "proprietary",
    "status": "classified",
    "classification": "PATENT_PROPRIETARY",
    "needsClarification": false,
    "confidence": 0.9542,
    "citationCount": 3,
    "route": "AYUSH_PATENT_IP"
  },
  {
    "name": "unrelated_sparse",
    "status": "needs_clarification",
    "classification": null,
    "needsClarification": true,
    "confidence": 0.2345,
    "citationCount": 3,
    "route": null
  }
]
```

No production RAG or production Supabase integration is claimed.

## 14. Dataset Fingerprint Verification

No dataset rebuild, redownload, chunk rewrite, or manifest rewrite was performed.

Locked dataset hashes checked:

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

## 15. Files Created

Backend source:

- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/api/FormulationController.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/formulation/FormulationClassificationService.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/formulation/classification/FormulationClarificationService.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/formulation/classification/FormulationRuleAssessment.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/formulation/classification/FormulationRuleEngine.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/formulation/classification/RegulatoryRouteService.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/formulation/model/FormulationClassification.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/formulation/model/FormulationRequest.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/formulation/model/FormulationResponse.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/formulation/model/FormulationStatus.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/formulation/model/RegulatoryRoute.java`

Backend tests:

- `ip-sakti-backend/src/test/java/com/ipsakti/ip_sakti_backend/api/FormulationControllerSecurityTest.java`
- `ip-sakti-backend/src/test/java/com/ipsakti/ip_sakti_backend/api/FormulationControllerTest.java`
- `ip-sakti-backend/src/test/java/com/ipsakti/ip_sakti_backend/formulation/FormulationClassificationServiceTest.java`

Report:

- `docs/BACKEND_PHASE_4_IMPLEMENTATION_REPORT.md`

## 16. Files Modified

- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/config/SecurityConfig.java`

The only Phase 4 modification to existing source was adding the new formulation endpoint to the dev-mode allowlist. Prod/non-dev mode still requires API-key authentication.

## 17. Secret Scan

Searched backend source, backend `.env.example`, and docs for known key patterns.

Result:

- no newly added plaintext secrets found.
- only the pre-existing redacted note in `docs/BACKEND_RAG_INTEGRATION_ANALYSIS.md` matched known historical key-pattern strings.

## 18. Known Limitations

- The classifier is a routing suggestion, not a legal determination.
- Deterministic rules are intentionally conservative and should be refined with domain-expert review.
- It does not implement Phase 5 full dual-jurisdiction routing.
- It does not implement Section 3(p), Section 3(e), ABS, GRATK, TKDL, Bhashini translation, conversation persistence, frontend, or deployment.
- The local integration test used the local file-backed RAG runtime, not production Supabase credentials.

## 19. Phase 5 Recommendation

Recommended next phase:

Phase 5 — hard-partitioned dual-jurisdiction router.

Suggested scope:

1. Promote jurisdiction into a stricter routing policy shared by question and formulation APIs.
2. Add explicit cross-jurisdiction comparison mode.
3. Ensure Indian and international regimes are never silently mixed.
4. Add tests for India-only, international-only, auto, and comparison scenarios.
5. Keep RAG evidence and citation validation in the Python RAG layer.

Stop condition honored: no Phase 5 work was implemented.

