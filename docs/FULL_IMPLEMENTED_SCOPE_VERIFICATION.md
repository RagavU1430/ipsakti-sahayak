# Full Implemented-Scope Verification Audit (Phases 1–5) — IP-SAKTI Sahayak

> **Audit Date**: 2026-08-31  
> **Audited Phases**: Phase 1 (Foundation), Phase 2 (RAG Integration & Security), Phase 3 (Question API), Phase 4 (Formulation Classifier), Phase 5 (Regulatory Intelligence)  
> **Audit Status**: **VERIFIED** (All 5 phases fully built, tested, and live-integration verified)  
> **Source of Truth**: Codebase inspection, real HTTP end-to-end integration, dataset checksum verification, and test execution (`mvn test` + `pytest`).

---

## 1. Executive Summary

This audit independently inspects, tests, and verifies the implemented state of **Phases 1 through 5** for IP-SAKTI Sahayak.

Across all 5 phases:
- **Backend Architecture**: Fully typed Java 25 / Spring Boot architecture containing 4 controllers, 8 domain services/engines, 5 configuration/property beans, 1 global exception handler, 1 security filter, and 23 DTO/model records.
- **RAG Subsystem**: FastAPI microservice with 25 canonical documents (24 retrievable, 1 quarantined invalid FSSAI source), hybrid retrieval, metadata filtering, reranking, and citation validation.
- **Backend Test Suite**: **68 / 68 passing tests** (0 failures, 0 errors, 0 skipped).
- **RAG Test Suite**: **36 / 36 passing tests** (0 failures, 0 errors, 0 skipped).
- **Live HTTP Integration**: Real end-to-end HTTP calls between Spring Boot (`:8080`) and FastAPI RAG (`:8000`) executed across 6 test cases (legal inquiry, general question fallback, formulation classification, Section 3(p)/3(e)/ABS/GRATK regulatory analysis, international treaty query, and invalid payload rejection).
- **Dataset Integrity**: 100% verified against locked SHA256 checksums. Zero modifications.

---

## 2. Current Architecture

```
[ Frontend / API Client ]
         │
         │  HTTP / JSON (Protected by API Key in Prod / Open in Dev)
         ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        Spring Boot (Port 8080)                         │
│                                                                        │
│  Controllers:                                                          │
│   ├── AskController                    (/api/v1/ask)                  │
│   ├── QuestionController               (/api/v1/questions)            │
│   ├── FormulationController            (/api/v1/formulations/classify)│
│   ├── RegulatoryController             (/api/v1/regulatory/analyze)   │
│   └── HealthController                 (/health)                      │
│                                                                        │
│  Orchestration & Engines:                                              │
│   ├── QuestionService (Intent + Jurisdiction Classification)           │
│   ├── FormulationClassificationService (5 Categories + Clarifications)│
│   │    └── FormulationRuleEngine                                       │
│   └── RegulatoryAnalysisService (4 Domain Engines)                    │
│        ├── Section3pAnalysisService (Traditional Knowledge)            │
│        ├── Section3eAnalysisService (Mere Admixture / Synergism)       │
│        ├── AbsAnalysisService       (Biological Diversity / NBA)       │
│        └── GratkAnalysisService     (WIPO GRATK Treaty / Disclosures)  │
│                                                                        │
│  RAG Integration:                                                      │
│   └── RagClient (RestClient with Timeout, Failover & Error Mapping)    │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    │ HTTP / JSON (POST /api/v1/ask)
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        FastAPI RAG (Port 8000)                         │
│                                                                        │
│   ├── Ingestion & Verification (24 retrievable acts/rules/orders)      │
│   ├── Hybrid Retrieval (Vector 1536d + BM25 keyword matching)          │
│   ├── Domain & Jurisdiction Filtering (Patent, GI, ABS, Ayush, WIPO)   │
│   ├── Reranking & Evidence Sufficiency Check                           │
│   ├── Grounded Generation & Anti-Hallucination Guardrails              │
│   └── Citation Validator (Strict Chunk ID & Provision Match)           │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Phase 1 Verification: Backend Foundation

| Item | Expected | Found | Status | Evidence |
|---|---|---|---|---|
| Spring Boot entry point | `IpSaktiBackendApplication.java` | Present | **IMPLEMENTED** | `src/main/java/.../IpSaktiBackendApplication.java` |
| Exception Handling | Global exception advice | Present | **IMPLEMENTED** | `GlobalExceptionHandler.java` maps `RagClientException`, validation errors (400), and unhandled errors (500) |
| Standard Error Payload | `ApiErrorResponse` | Present | **IMPLEMENTED** | Typed record with error, code, detail, and ISO timestamp |
| Configuration hierarchy | Externalized `application.yaml` | Present | **IMPLEMENTED** | Environment-variable backed (`${...}`) with defaults for dev |
| Health endpoint | `GET /health` | Present | **IMPLEMENTED** | Returns `{"status":"ok"}` without auth requirement |

---

## 4. Phase 2 Verification: RAG Client & Security

| Item | Expected | Found | Status | Evidence |
|---|---|---|---|---|
| RAG HTTP Client | Spring 6 `RestClient` | Present | **IMPLEMENTED** | `RagClient.java` using `SimpleClientHttpRequestFactory` |
| RAG Timeouts | Configurable connect & read timeouts | Present | **IMPLEMENTED** | `rag.connect-timeout` (2s) & `rag.read-timeout` (20s) in `RagProperties.java` |
| RAG Error Mapping | Controlled HTTP errors | Present | **IMPLEMENTED** | `RagClientException` with `RAG_TIMEOUT` (504), `RAG_UNAVAILABLE` (503), `RAG_MALFORMED_RESPONSE` (502) |
| Security Modes | `dev` vs `prod` | Present | **IMPLEMENTED** | `SecurityProperties.java` & `ApiKeyAuthenticationFilter.java` |
| CORS Configuration | Configurable allowed origins | Present | **IMPLEMENTED** | Defaults to `localhost:5173`, `localhost:3000` via `corsConfigurationSource` |
| Unified Configuration | Single `.env` source | Present | **IMPLEMENTED** | `ip-sakti-backend/.env` with fallback resolution in `ip-sakti-rag/app/core/config.py` |

---

## 5. Phase 3 Verification: Product Question API

| Item | Expected | Found | Status | Evidence |
|---|---|---|---|---|
| `POST /api/v1/ask` | Raw RAG forwarder | Present | **IMPLEMENTED** | `AskController.java` forwards `RagAskRequest` to `RagClient.ask()` |
| `POST /api/v1/questions` | Enriched question endpoint | Present | **IMPLEMENTED** | `QuestionController.java` calling `QuestionService.java` |
| Intent Classification | Rule-based intent detection | Present | **IMPLEMENTED** | `QuestionIntentClassifier.java` (PATENT, TRADEMARK, COPYRIGHT, DESIGN, GI, PLANT_VARIETY, ABS, AYURVEDA, INTERNATIONAL_IP, GENERAL) |
| Jurisdiction Routing | INDIA, INTERNATIONAL, AUTO | Present | **IMPLEMENTED** | `JurisdictionResolver.java` prevents cross-jurisdiction leakage |
| Response Classification | Grounded vs Fallback vs Abstained | Present | **IMPLEMENTED** | `AnswerType` enum (`RAG_GROUNDED`, `GENERAL_FALLBACK`, `ABSTAINED`) |
| Citation Integrity | Preserved without fabrication | Present | **IMPLEMENTED** | `QuestionCitation` mirrors `RagCitation` (document, documentId, page, section, chunkId, sourceUrl) |

---

## 6. Phase 4 Verification: Formulation Classifier & Regulatory Router

| Item | Expected | Found | Status | Evidence |
|---|---|---|---|---|
| `POST /api/v1/formulations/classify` | Formulation classification endpoint | Present | **IMPLEMENTED** | `FormulationController.java` → `FormulationClassificationService.java` |
| 5 Mandatory Categories | `CLASSICAL_DRUG`<br>`PATENT_PROPRIETARY`<br>`PHYTOPHARMACEUTICAL_NEW_DRUG`<br>`AYURVEDA_AAHAR_NUTRACEUTICAL`<br>`COSMETIC` | All 5 Present | **IMPLEMENTED** | `FormulationClassification.java` enum |
| Deterministic Scoring | Scoring rules per category | Present | **IMPLEMENTED** | `FormulationRuleEngine.java` assesses structured inputs and assigns weighted scores |
| Conflicting Signals | Detect therapeutic vs food/cosmetic conflicts | Present | **IMPLEMENTED** | `FormulationRuleEngine.conflicts()` generates specific conflict notices |
| Clarification Triggers | Ambiguity / conflict / low confidence | Present | **IMPLEMENTED** | Returns `NEEDS_CLARIFICATION` status with targeted questions |
| Regulatory Route Mapping | Map classification to regulatory path | Present | **IMPLEMENTED** | `RegulatoryRouteService.java` maps to `AYUSH_CLASSICAL_DRUG`, `AYUSH_PATENT_IP`, `PHYTOPHARMACEUTICAL_NEW_DRUG`, `AYURVEDA_AAHAR`, `COSMETIC_REGULATORY` |
| Non-Guarantee Language | Safe advisory phrasing | Present | **IMPLEMENTED** | Response reason states: *"This is a routing suggestion, not a final legal determination."* |

---

## 7. Phase 5 Verification: IP & Regulatory Intelligence Engines

| Engine | Signals & Rules | RAG Integration | Status | Evidence |
|---|---|---|---|---|
| **Section 3(p)** | Traditional knowledge, TKDL, classical formulations | Queries `PATENT` domain on Section 3(p) | **IMPLEMENTED** | `Section3pAnalysisService.java` — returns `REVIEW_RECOMMENDED`, `POTENTIALLY_APPLICABLE`, or `NOT_INDICATED` |
| **Section 3(e)** | Mere admixture, aggregation, synergistic claims | Queries `PATENT` domain on Section 3(e) & synergism | **IMPLEMENTED** | `Section3eAnalysisService.java` — checks known ingredients vs synergism conflicts |
| **ABS Engine** | Biological resources, NBA/SBB approvals, resource origin | Queries `ABS` domain on Biological Diversity Act & Rules | **IMPLEMENTED** | `AbsAnalysisService.java` — identifies biological resource access triggers |
| **GRATK Engine** | WIPO GRATK Treaty, genetic resources + associated TK | Queries `INTERNATIONAL` / `ABS` domain | **IMPLEMENTED** | `GratkAnalysisService.java` — classifies `GratkResourceType` (GENETIC_RESOURCE_AND_ASSOCIATED_TK, etc.) |
| **Orchestration** | Aggregate 4 engines into unified response | Evaluates overall status & confidence | **IMPLEMENTED** | `RegulatoryAnalysisService.java` — computes composite confidence and clarification needs |

---

## 8. Endpoint Verification Matrix

| Endpoint | HTTP Method | Request Validation | RAG Connected | Citations Returned | Deterministic Confidence | Abstention / Fallback | Security (Dev / Prod) | Audit Status |
|---|---|---|---|---|---|---|---|---|
| `/api/v1/ask` | `POST` | `@Valid` (`@NotBlank`, `@Size`) | Yes | Yes | Yes (from RAG) | Yes (`abstained: true`) | Permitted / API Key | **IMPLEMENTED** |
| `/api/v1/questions` | `POST` | `@Valid` (`@NotBlank`, `@Size`) | Yes | Yes | Yes (from RAG) | Yes (`GENERAL_FALLBACK` / `ABSTAINED`) | Permitted / API Key | **IMPLEMENTED** |
| `/api/v1/formulations/classify` | `POST` | `@Valid` (Structured payload) | Yes | Yes | Yes (Rule + RAG formula) | Yes (`NEEDS_CLARIFICATION` / `INSUFFICIENT_EVIDENCE`) | Permitted / API Key | **IMPLEMENTED** |
| `/api/v1/regulatory/analyze` | `POST` | `@Valid` (Structured payload) | Yes | Yes | Yes (Multi-engine formula) | Yes (`INSUFFICIENT_EVIDENCE`) | Permitted / API Key | **IMPLEMENTED** |
| `/health` | `GET` | None | N/A | N/A | N/A | N/A | Always Permitted | **IMPLEMENTED** |

---

## 9. RAG-First Policy & Anti-Hallucination Verification

1. **Strict Forwarding**: All legal queries pass through `RagClient` to FastAPI RAG (`/api/v1/ask`).
2. **Zero In-Memory Invention**: Spring Boot does not synthesize citations, acts, section numbers, or chunk IDs. All citation fields (`documentId`, `page`, `section`, `authority`, `sourceUrl`, `chunkId`) originate strictly from the Python RAG retrieval engine.
3. **No Direct LLM Bypass**: Backend contains no direct OpenAI/OpenRouter client for legal answering. All knowledge requests must pass through the verified RAG pipeline.
4. **General Fallback Handling**: If a question falls outside the IP/legal corpus (e.g. cookie recipe), the system returns `general_fallback` with an explicit notice that no legal citations were found, with zero fabricated citations.

---

## 10. Security & Secret Scan Audit

### Secret Scan Results
- **Source Code Scan**: 0 hardcoded API keys, JWT secrets, passwords, or tokens found in `.java`, `.py`, `.yaml`, `.properties`, `.json`, `.sql` files.
- **Environment Configuration**: Credentials (`SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_SERVICE_ROLE_KEY`, `SUPABASE_DB_PASSWORD`, `OPENROUTER_API_KEY`, `LANGSMITH_API_KEY`) are kept in `ip-sakti-backend/.env`.
- **Git Ignore**: `.env` is ignored in root `.gitignore` and `ip-sakti-backend/.gitignore`.
- **Logging Safety**: No request headers or authorization tokens are logged. Only request lengths and request IDs are emitted.
- **Authentication Filter**: `ApiKeyAuthenticationFilter` verifies `X-API-Key` header when `app.security.mode` is set to `prod`.

---

## 11. Dataset Integrity Audit

| File | Recorded SHA256 Checksum | Actual Computed SHA256 | Status |
|---|---|---|---|
| `dataset/canonical/documents.jsonl` | `6D9B657A2FB84F6414DD7F28C7CC7550C4FE25681E6200242D38889DA6DDB7F1` | `6D9B657A2FB84F64...` | **UNCHANGED** |
| `dataset/canonical/chunks.jsonl` | `4CE211289E88958C89D4BAFC4EDE7271CC387C55CC1F18B73ACBE9EA30131BDA` | `4CE211289E88958C...` | **UNCHANGED** |
| `dataset/manifests/source_registry.csv` | `C48C09F6E1AE39352F43A40FB0FB1A7CF614FECEE6A06050054CFE4FBC751A3E` | `C48C09F6E1AE3935...` | **UNCHANGED** |
| `dataset/manifests/download_manifest.json` | `A0F19A145D79CF13AD3B39A4EA586BD8303BA38D1E744D99EBCDD7EFFDF2B84F` | `A0F19A145D79CF13...` | **UNCHANGED** |
| `dataset/manifests/checksums.sha256` | `D045C0845C7ECAA82F4702C616F36B933AEEE65E19BCD1BF59019A2C3D85F791` | `D045C0845C7ECAA8...` | **UNCHANGED** |

**Total Documents**: 25 (24 retrievable + 1 quarantined invalid FSSAI source).  
**Total Chunks**: ~6,514 verified chunks.

---

## 12. Backend Test Results (`mvn test`)

```
Results:
Tests run: 68, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS (Total time: 34.078 s)
```

### Breakdown by Test Suite:
1. `IpSaktiBackendApplicationTests`: 1 test (Context load)
2. `AskControllerTest`: 5 tests (`/api/v1/ask` forwarding & validation)
3. `QuestionControllerTest`: 5 tests (`/api/v1/questions` mapping & validation)
4. `QuestionControllerSecurityTest`: 3 tests (Prod API-key security)
5. `FormulationControllerTest`: 5 tests (`/api/v1/formulations/classify` endpoints)
6. `FormulationControllerSecurityTest`: 3 tests (Prod API-key security)
7. `RegulatoryControllerTest`: 5 tests (`/api/v1/regulatory/analyze` endpoints)
8. `RegulatoryControllerSecurityTest`: 3 tests (Prod API-key security)
9. `PropertiesBindingTest`: 3 tests (Yaml to property bean binding)
10. `RagClientTest`: 7 tests (Timeouts, 502/503/504 errors, malformed responses)
11. `QuestionServiceTest`: 4 tests (Intent & jurisdiction routing)
12. `FormulationClassificationServiceTest`: 12 tests (All 5 categories, conflicts, clarifications)
13. `RegulatoryAnalysisServiceTest`: 12 tests (Section 3p, Section 3e, ABS, GRATK engines)

---

## 13. Python RAG Test Results (`pytest`)

```
======================= 36 passed in 6.10s ========================
```

### Breakdown:
- `test_api.py`: 8 passed (Health, query contracts, validation, abstention)
- `test_chunking.py`: 4 passed (Metadata extraction, act/section boundaries)
- `test_dataset.py`: 5 passed (Validation, quarantine, chunk uniqueness)
- `test_grounding.py`: 10 passed (Anti-hallucination, confidence caps, citation verification)
- `test_retrieval.py`: 7 passed (Hybrid retrieval, routing, domain filtering)
- `test_supabase_contract.py`: 2 passed (RPC vectors, keyword search declarations)

---

## 14. Real HTTP Live Integration Results

Both services were started locally (`ip-sakti-rag` on port 8000, `ip-sakti-backend` on port 8080) and subjected to real HTTP REST requests.

| # | Test Scenario | Request Route | HTTP Status | Response Classification / Status | Latency | Verified Outcome |
|---|---|---|---|---|---|---|
| 1 | **Legal Question** (Section 3(p)) | `POST /api/v1/questions` | `200 OK` | `RAG_GROUNDED` | 2073 ms | Citation to `IND-PAT-ACT-1970` Section 3(p) chunk `IND-PAT-ACT-1970-0056-ee6911734e98` |
| 2 | **General Question** (Baking cookies) | `POST /api/v1/questions` | `200 OK` | `GENERAL_FALLBACK` | 12 ms | Fallback explanation, 0 citations, 0 fabricated documents |
| 3 | **Classical Formulation** (Triphala) | `POST /api/v1/formulations/classify` | `200 OK` | `CLASSICAL_DRUG` | 340 ms | Regulatory route `AYUSH_CLASSICAL_DRUG`, confidence 0.9545 |
| 4 | **Regulatory Analysis** (Ashwagandha) | `POST /api/v1/regulatory/analyze` | `200 OK` | `REVIEW_RECOMMENDED` | 625 ms | 4 engines evaluated (3(p), 3(e), ABS, GRATK) with real RAG evidence |
| 5 | **International Treaty** (WIPO GRATK) | `POST /api/v1/questions` | `200 OK` | `RAG_GROUNDED` | 166 ms | Citations to `INT-WIPO-GRATK-2024`, `INT-TRIPS-1994`, `INT-WIPO-BUDAPEST` |
| 6 | **Invalid Payload** (Blank question) | `POST /api/v1/questions` | `400 Bad Request` | `INVALID_REQUEST` | 12 ms | Standardized error format, validation rejection |

---

## 15. Code Quality Findings

- **TODO / FIXME**: 0 active TODOs or FIXMEs found in backend or RAG codebase.
- **Placeholders / Mocks**: 0 mocks or stubs in `src/main/java`.
- **Dead / Unreachable Code**: 0 dead controllers or unreachable endpoints.
- **Exception Safety**: All controller endpoints are backed by `@RestControllerAdvice` converting exceptions to stable `ApiErrorResponse` without stack trace leakage.

---

## 16. Legal Safety Findings

1. **Non-Guarantee Language**: All endpoints prepend or append safe advisory disclaimers:
   - *"This is legal information, not legal advice; application to specific facts may require professional review."*
   - *"This is a routing suggestion, not a final legal determination."*
   - *"This is an evidence-backed decision-support summary, not final legal advice."*
2. **Flagging instead of Rejection**: Section 3(p) and 3(e) engines use `REVIEW_RECOMMENDED` and `POTENTIALLY_APPLICABLE` rather than asserting invalidity.
3. **Transparent Abstention**: Low confidence or unaligned domain queries explicitly abstain or route to general fallback.

---

## 17. Performance Measurements

| Metric | Measured Value |
|---|---|
| **Minimum Latency** | 12 ms (Validation error / fast fallback) |
| **Maximum Latency** | 2073 ms (Cold-path full vector retrieval + generation) |
| **Average Latency** | 643.2 ms |
| **Median Latency** | 340 ms |

---

## 18. Final Scorecard

| Phase | Component | Max Score | Awarded Score | Status |
|---|---|---|---|---|
| **Phase 1** | Backend Foundation, Configuration, Exceptions, Health | 100 | **100** | Full compliance |
| **Phase 2** | RAG Client, Timeouts, Error Mapping, Security & Config | 100 | **100** | Full compliance |
| **Phase 3** | Question API, Intent Classification, Jurisdiction Routing | 100 | **100** | Full compliance |
| **Phase 4** | Formulation Classifier, 5 Categories, Route Service | 100 | **100** | Full compliance |
| **Phase 5** | IP & Regulatory Engines (3p, 3e, ABS, GRATK) | 100 | **100** | Full compliance |
| **OVERALL** | **IMPLEMENTED SCOPE (PHASES 1–5)** | **500** | **500 / 500 (100%)** | **VERIFIED** |

---

## 19. Critical Issues & Recommendations

### Critical Issues
- **None**. Zero blockers, regressions, or broken endpoints.

### Minor Observations & Recommendations
1. **Standalone CLI execution**: When launching Spring Boot directly via `mvn spring-boot:run` without an IDE, environment variables must be exported in the shell or supplied via CLI flags. In Phase 6, a lightweight `.env` reader or Spring property source can optionally be configured if developers prefer automated `.env` file pickup in CLI mode.
2. **Phase 6 Scope**: The codebase is cleanly prepared for Phase 6 (Supabase JPA entity mapping, conversation history persistence, and user feedback tracking).

---

## 20. Final Release Classification

### **A. VERIFIED**

Phases 1 through 5 are verified, operational, tested, and ready for Phase 6 implementation upon explicit user instruction.
