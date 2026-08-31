# IP-SAKTI Sahayak Backend Phase 5 Implementation Report

## 1. Phase 5 objective

Implemented the Phase 5 IP + regulatory intelligence layer without rebuilding the RAG corpus, downloading documents, changing dataset files, replacing the RAG client, or weakening citation validation.

The new backend boundary is:

`POST /api/v1/regulatory/analyze`

It evaluates product/formulation context against the existing RAG service and returns conservative, evidence-backed regulatory considerations for:

- Indian Patents Act Section 3(p) / traditional knowledge review
- Indian Patents Act Section 3(e) / known-ingredient aggregation review
- Biological Diversity Act access and benefit-sharing review
- GRATK / genetic resources and associated traditional knowledge review
- Jurisdiction-aware routing for India, international, and ambiguous requests

## 2. Existing architecture reused

Phase 5 reuses the backend components already introduced in earlier backend phases:

- `RagClient` for all RAG calls
- existing `/api/v1/ask` RAG runtime boundary
- shared validation/error handling conventions
- shared `Jurisdiction`, citation, and source response DTOs from the question API layer
- existing security configuration

No new vector store, embedding client, corpus reader, Supabase schema, or standalone LLM chatbot was added.

## 3. New architecture implemented

The regulatory analyzer is modular:

1. `RegulatoryController`
2. `RegulatoryAnalysisService`
3. `RegulatoryJurisdictionRouter`
4. Individual engines:
   - `Section3pAnalysisService`
   - `Section3eAnalysisService`
   - `AbsAnalysisService`
   - `GratkAnalysisService`
5. `RegulatoryEvidenceMapper`
6. Stable response models under `regulatory/model`

Each engine performs deterministic signal analysis and calls the existing RAG service for legal evidence. The output is merged into an overall conservative regulatory status.

## 4. Section 3(p) engine

The Section 3(p) engine checks for traditional-knowledge signals including:

- explicit traditional knowledge flag
- classical reference
- textual references to classical, traditional, TKDL, or known traditional use
- formulation novelty flags where they may interact with TK concerns
- conflicting input such as `traditionalKnowledge=false` with a supplied classical reference

Allowed statuses:

- `NOT_INDICATED`
- `REVIEW_RECOMMENDED`
- `INSUFFICIENT_EVIDENCE`

It does not issue final legal conclusions such as “patent will be rejected.”

## 5. Section 3(e) engine

The Section 3(e) engine checks for known-ingredient aggregation signals including:

- multiple known ingredients
- mere admixture / aggregation / combination language
- claims of therapeutic, cosmetic, digestive, or similar effect
- claimed synergy conflicts

Allowed statuses:

- `NOT_INDICATED`
- `REVIEW_RECOMMENDED`
- `INSUFFICIENT_EVIDENCE`

The engine gives review-oriented guidance only and avoids patentability conclusions.

## 6. ABS engine

The ABS engine checks for access and benefit-sharing considerations using:

- biological resource flag
- Indian resource origin
- traditional knowledge flag
- text mentioning biological resources, bioresources, NBA, SBB, ABS, or benefit sharing
- incomplete resource/origin information

Allowed statuses:

- `NOT_INDICATED`
- `POTENTIALLY_APPLICABLE`
- `REVIEW_RECOMMENDED`
- `INSUFFICIENT_EVIDENCE`

Incomplete resource-origin context triggers clarification where appropriate.

## 7. GRATK engine

The GRATK engine identifies whether the product context suggests:

- no genetic resource / traditional knowledge issue
- genetic resource
- traditional knowledge
- genetic resource and associated traditional knowledge
- unknown due to insufficient product context

The response includes `resourceType` using:

- `NONE`
- `GENETIC_RESOURCE`
- `TRADITIONAL_KNOWLEDGE`
- `GENETIC_RESOURCE_AND_ASSOCIATED_TK`
- `UNKNOWN`

## 8. Jurisdiction router

The router supports:

- `INDIA`
- `INTERNATIONAL`
- `AUTO`

Explicit jurisdiction is preserved. `AUTO` uses conservative text signals:

- India signals include India, Indian, AYUSH, NBA, SBB, Biological Diversity Act.
- International signals include international, WIPO, GRATK, TRIPS, PCT, global, Europe, USA.

If `AUTO` is ambiguous, engines are not run. The API returns `needsClarification=true` with a safe clarification question.

## 9. Evidence aggregation

Each regulatory engine calls the existing RAG API through `RagClient`. Retrieved citations and sources are mapped into frontend/backend-safe DTOs:

- `QuestionCitation`
- `QuestionSource`

The endpoint does not expose prompts, embeddings, credentials, stack traces, or internal RAG implementation details.

## 10. Confidence model

Confidence is deterministic and derived from:

- engine signal strength
- RAG confidence
- citation availability
- conflict penalties
- insufficiency penalties

The overall confidence is the rounded average of engine confidences, or `0.2` for an ambiguous jurisdiction response.

The confidence value is bounded to `0.0` through `1.0`.

## 11. Abstention and uncertainty model

The endpoint returns `INSUFFICIENT_EVIDENCE` when the RAG service abstains for an engine or when jurisdiction is too ambiguous to route safely.

The endpoint also sets `needsClarification=true` when:

- jurisdiction is ambiguous
- engine evidence is insufficient
- conflicting signals are detected
- required context such as resource origin is incomplete

## 12. API contract

Endpoint:

`POST /api/v1/regulatory/analyze`

Representative request:

```json
{
  "productName": "Traditional Ayurvedic Knowledge Based Formulation",
  "ingredients": ["Plant A", "Plant B"],
  "intendedUse": "traditional knowledge based therapeutic use",
  "claims": ["known traditional use"],
  "traditionalKnowledge": true,
  "classicalReference": "classical Ayurvedic text reference",
  "biologicalResources": true,
  "resourceOrigin": "India",
  "targetMarket": "India",
  "jurisdiction": "INDIA",
  "knownIngredients": true,
  "formulationNovelty": true,
  "geneticResources": true
}
```

Response shape:

```json
{
  "jurisdiction": "INDIA",
  "overallStatus": "REVIEW_RECOMMENDED",
  "engines": [],
  "overallConfidence": 0.79,
  "needsClarification": false,
  "questions": [],
  "reason": "Regulatory analysis completed using the configured RAG evidence service."
}
```

Each engine contains:

- `engine`
- `status`
- `confidence`
- `reason`
- `considerations`
- `resourceType`
- `citations`
- `sources`

## 13. Security

Security behavior follows the backend security mode already used by prior phases:

- dev mode permits local API testing
- production mode requires the configured API key
- `/api/v1/regulatory/analyze` was added to the dev allowlist
- validation bounds request size and enum values
- no secrets are hardcoded
- no secret values are logged

Secret scan result:

- No new secrets found in Phase 5 files.
- Existing documentation still contains a redacted historical credential-risk note in `docs/BACKEND_RAG_INTEGRATION_ANALYSIS.md`.

## 14. Unit and controller tests

Added tests for:

- Section 3(p) traditional knowledge review
- Section 3(p) unrelated formulation behavior
- Section 3(p) conflicting input clarification
- Section 3(e) known-ingredient mixture review
- Section 3(e) synergy conflict confidence behavior
- ABS applicability
- ABS incomplete origin clarification
- GRATK resource type identification
- jurisdiction AUTO routing
- ambiguous jurisdiction clarification
- confidence range bounds
- RAG abstention handling
- controller happy path
- malformed JSON
- excessive payload
- invalid jurisdiction enum
- RAG unavailable mapped to controlled `503`
- production API key security

## 15. Integration tests

Executed a live local integration check with:

- Python RAG service on `127.0.0.1:18400`
- Spring backend on `127.0.0.1:18481`
- backend configured with `rag.base-url=http://127.0.0.1:18400`
- dev security mode

Requests were sent to:

`POST http://127.0.0.1:18481/api/v1/regulatory/analyze`

Live verification results:

| Case | Jurisdiction | Overall status | Needs clarification | Overall confidence | Engine count | Citation count |
|---|---|---:|---:|---:|---:|---:|
| section3p_tk | INDIA | REVIEW_RECOMMENDED | true | 0.7926 | 4 | 8 |
| section3e_mixture | INDIA | REVIEW_RECOMMENDED | true | 0.6876 | 4 | 8 |
| abs_india | INDIA | REVIEW_RECOMMENDED | false | 0.7873 | 4 | 8 |
| gratk_international | INTERNATIONAL | REVIEW_RECOMMENDED | true | 0.5224 | 4 | 6 |
| ambiguous_auto | AUTO | INSUFFICIENT_EVIDENCE | true | 0.2 | 0 | 0 |

## 16. Exact test results

Backend:

Command:

```powershell
$env:MAVEN_USER_HOME=(Join-Path (Get-Location) '.m2home'); .\mvnw.cmd "-Dmaven.repo.local=.m2home\repository" test
```

Result:

- 68 tests passed
- 0 failures
- 0 errors
- 0 skipped

RAG:

Command:

```powershell
& "C:\Users\Ragav U\AppData\Local\Programs\Python\Python313\python.exe" -m pytest
```

Result:

- 36 tests passed
- 5 warnings

Warnings were Python/SWIG deprecation warnings and were not introduced by Phase 5.

## 17. Dataset fingerprint verification

Verified canonical/manifest files present in this repository:

| File | SHA256 |
|---|---|
| `ip-sakti-rag/dataset/canonical/chunks.jsonl` | `4CE211289E88958C89D4BAFC4EDE7271CC387C55CC1F18B73ACBE9EA30131BDA` |
| `ip-sakti-rag/dataset/canonical/documents.jsonl` | `6D9B657A2FB84F6414DD7F28C7CC7550C4FE25681E6200242D38889DA6DDB7F1` |
| `ip-sakti-rag/dataset/canonical/metadata.json` | `E8C11AB4A92101623FD021111172C666D68768D189F2C1102BA27A98A00526FE` |
| `ip-sakti-rag/dataset/manifests/checksums.sha256` | `D045C0845C7ECAA82F4702C616F36B933AEEE65E19BCD1BF59019A2C3D85F791` |
| `ip-sakti-rag/dataset/manifests/download_manifest.json` | `A0F19A145D79CF13AD3B39A4EA586BD8303BA38D1E744D99EBCDD7EFFDF2B84F` |
| `ip-sakti-rag/dataset/manifests/source_registry.csv` | `C48C09F6E1AE39352F43A40FB0FB1A7CF614FECEE6A06050054CFE4FBC751A3E` |

No dataset regeneration was performed.

## 18. Files created

Phase 5 created:

- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/api/RegulatoryController.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/regulatory/RegulatoryAnalysisService.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/regulatory/engine/AbsAnalysisService.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/regulatory/engine/GratkAnalysisService.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/regulatory/engine/RegulatoryEvidenceMapper.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/regulatory/engine/RegulatoryJurisdictionRouter.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/regulatory/engine/Section3eAnalysisService.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/regulatory/engine/Section3pAnalysisService.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/regulatory/model/GratkResourceType.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/regulatory/model/RegulatoryAnalysisRequest.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/regulatory/model/RegulatoryAnalysisResponse.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/regulatory/model/RegulatoryEngine.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/regulatory/model/RegulatoryEngineResult.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/regulatory/model/RegulatoryStatus.java`
- `ip-sakti-backend/src/test/java/com/ipsakti/ip_sakti_backend/api/RegulatoryControllerSecurityTest.java`
- `ip-sakti-backend/src/test/java/com/ipsakti/ip_sakti_backend/api/RegulatoryControllerTest.java`
- `ip-sakti-backend/src/test/java/com/ipsakti/ip_sakti_backend/regulatory/RegulatoryAnalysisServiceTest.java`
- `docs/BACKEND_PHASE_5_IMPLEMENTATION_REPORT.md`

## 19. Files modified

Phase 5 modified:

- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/config/SecurityConfig.java`

Previously existing Phase 1-4 working-tree changes remain present and were preserved.

The file `ip-sakti-rag/app/core/config.py` was already modified before Phase 5 and was not changed for this phase.

## 20. Known limitations

- The engines provide risk/consideration analysis, not legal advice or final legal conclusions.
- Confidence is a deterministic engineering signal, not a legal probability.
- The backend relies on the local/production RAG service being available through `rag.base-url`.
- Production deployment was not tested because production credentials and hosting/runtime configuration were not provided.
- Some test inputs use placeholder ingredient names; they verify routing and behavior, not legal merits of a real product.

## 21. Phase 6 recommendation

Recommended next phase:

- Add frontend rendering for `overallStatus`, engine cards, confidence, citations, and clarification questions.
- Add product-intake UX constraints so users provide resource origin, TK, biological-resource, and jurisdiction information explicitly.
- Add operator review workflow for `REVIEW_RECOMMENDED` and `POTENTIALLY_APPLICABLE` cases.

Phase 5 stops here as requested.
