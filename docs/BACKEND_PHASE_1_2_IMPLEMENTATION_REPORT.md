# Backend Phase 1/2 Implementation Report

Date: 2026-08-31

Scope: Phase 1 and Phase 2 from `docs/BACKEND_RAG_INTEGRATION_ANALYSIS.md`.

## 1. Summary

Implemented the Spring Boot backend boundary for IP-SAKTI Sahayak to call the existing Python RAG service through a clean `POST /api/v1/ask` API. The backend does not duplicate retrieval, generation, citation validation, embeddings, reranking, or dataset logic. Those remain owned by the Python RAG service.

The backend now provides:

- Externalized environment-based configuration.
- Safer default JPA settings.
- Basic API-key security support for non-dev mode.
- CORS configuration from environment variables.
- JWT-ready configuration placeholders.
- Bhashini-ready configuration placeholders.
- A typed RAG HTTP client for `{RAG_BASE_URL}/api/v1/ask`.
- A Spring controller exposing backend `POST /api/v1/ask`.
- Stable request/response DTOs matching the RAG API contract.
- Controlled error responses for invalid requests and RAG infrastructure failures.
- Backend tests for controller validation, RAG client behavior, error mapping, response classification, and property binding.

## 2. Existing Architecture Discovered

Repository layout inspected:

- `ip-sakti-rag`: existing Python RAG microservice and locked dataset owner.
- `ip-sakti-backend`: Spring Boot backend, initially mostly an application entry point plus YAML configuration.
- `Frontend`: existing frontend layer, not modified in this phase.
- `docs`: project documentation and backend/RAG integration analysis.

Existing RAG architecture discovered:

- Python FastAPI service exposes `POST /api/v1/ask`.
- RAG tests exist under `ip-sakti-rag/tests`.
- RAG owns retrieval, metadata filtering, reranking, grounded generation, citation validation, confidence, and abstention.
- Supabase migrations exist under `ip-sakti-rag/supabase/migrations`.
- Canonical dataset and manifests exist under `ip-sakti-rag/dataset`.

Existing backend architecture discovered:

- Spring Boot 4.1.1 project with Java 25.
- Dependencies already included web, validation, security, JPA, PostgreSQL, and test support.
- No existing controllers, service layer, RAG HTTP client, auth filter, product orchestration, or persistence entities were present before this phase.
- `application.yaml` contained database/JPA defaults that needed environment externalization and safer production defaults.

Product requirement source note:

- The attached prompt references `ANALYSIS SIH(1).md` as a primary source, but that file was not present under the workspace during this pass.
- The attached prompt’s MVP/product requirements and `docs/BACKEND_RAG_INTEGRATION_ANALYSIS.md` were therefore used as the available requirements sources.

## 3. Architecture Implemented

Frontend/backend callers use:

```http
POST /api/v1/ask
```

Spring Boot backend flow:

```text
Client
  -> Spring AskController
  -> RagClient
  -> Python RAG service: {RAG_BASE_URL}/api/v1/ask
  -> typed response mapping
  -> controlled API response
```

The backend remains a boundary/proxy layer. The real RAG pipeline remains inside `ip-sakti-rag`:

```text
Question
  -> query processing
  -> retrieval
  -> evidence comparison
  -> RAG-grounded answer / abstention / general fallback
  -> citations and sources
```

## 4. Files Created

Backend source:

- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/api/AskController.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/api/HealthController.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/config/ApiKeyAuthenticationFilter.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/config/BhashiniProperties.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/config/JwtProperties.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/config/RagProperties.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/config/SecurityConfig.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/config/SecurityProperties.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/config/SupabaseProperties.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/exception/ApiErrorResponse.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/exception/GlobalExceptionHandler.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/exception/RagClientException.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/rag/RagClient.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/rag/RagClientConfig.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/rag/dto/RagAnswerSource.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/rag/dto/RagAskRequest.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/rag/dto/RagAskResponse.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/rag/dto/RagCitation.java`
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/rag/dto/RagSource.java`

Backend tests:

- `ip-sakti-backend/src/test/java/com/ipsakti/ip_sakti_backend/api/AskControllerTest.java`
- `ip-sakti-backend/src/test/java/com/ipsakti/ip_sakti_backend/config/PropertiesBindingTest.java`
- `ip-sakti-backend/src/test/java/com/ipsakti/ip_sakti_backend/rag/RagClientTest.java`

Configuration/report:

- `ip-sakti-backend/.env.example`
- `docs/BACKEND_PHASE_1_2_IMPLEMENTATION_REPORT.md`

## 5. Files Modified

- `ip-sakti-backend/.gitignore`
- `ip-sakti-backend/src/main/resources/application.yaml`
- `ip-sakti-backend/src/test/java/com/ipsakti/ip_sakti_backend/IpSaktiBackendApplicationTests.java`

Note: `ip-sakti-rag/app/core/config.py` appears in `git status` as modified, but it was not changed during this backend Phase 1/2 implementation pass.

## 6. API Contract

Backend endpoint:

```http
POST /api/v1/ask
Content-Type: application/json
```

Request:

```json
{
  "question": "What are the requirements for registering a trademark in India?",
  "domain": "TRADEMARK",
  "jurisdiction": "INDIA",
  "top_k": 8
}
```

Only `question` is required.

Validation implemented:

- `question` must be present.
- `question` must be non-blank.
- `question` maximum length is 4000 characters.
- `top_k` must be between 1 and 20 when supplied.

Response:

```json
{
  "answer": "...",
  "confidence": 0.91,
  "abstained": false,
  "citations": [],
  "sources": [],
  "answer_source": "rag_grounded"
}
```

`answer_source` is backend-derived:

- `rag_grounded` when the response is non-abstained and includes citations or sources.
- `abstained` when the RAG service abstains.
- `general_fallback` when the RAG-first policy returns an uncited general answer.

## 6. Security Changes

Implemented:

- Environment-based secrets/configuration.
- `BACKEND_SECURITY_MODE=dev` default for local development.
- API-key enforcement when `BACKEND_SECURITY_MODE` is not `dev`.
- `X-API-Key` support through `BACKEND_API_KEY`.
- Stateless Spring Security configuration.
- Disabled form login, HTTP Basic, logout, and CSRF for API usage.
- Configurable CORS origins via `BACKEND_ALLOWED_ORIGINS`.
- Health endpoint allowed without API key.

No Supabase service-role key is exposed to frontend code.

## 7. Configuration

Added/standardized environment variables:

```env
RAG_BASE_URL=http://localhost:8000
RAG_CONNECT_TIMEOUT=2s
RAG_READ_TIMEOUT=20s

BACKEND_SECURITY_MODE=dev
BACKEND_API_KEY=
BACKEND_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000
BACKEND_JWT_ISSUER=ip-sakti-sahayak
BACKEND_JWT_SECRET=
BACKEND_JWT_ACCESS_TOKEN_TTL=30m

BHASHINI_ENABLED=false
BHASHINI_BASE_URL=
BHASHINI_API_KEY=

SUPABASE_URL=
SUPABASE_ANON_KEY=
SUPABASE_SERVICE_ROLE_KEY=
SUPABASE_DB_PASSWORD=
SPRING_DATASOURCE_URL=
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=
SPRING_JPA_HIBERNATE_DDL_AUTO=none
SPRING_JPA_SHOW_SQL=false
SPRING_JPA_FORMAT_SQL=false

OPENROUTER_API_KEY=
LANGSMITH_API_KEY=
```

`application.yaml` no longer hardcodes the Supabase database host and no longer defaults Hibernate to `ddl-auto: update`.

JWT and Bhashini configuration is intentionally inert in this phase. It creates an environment-bound foundation for later authentication and multilingual phases without pretending those integrations are live.

## 8. RAG Client

The Spring `RagClient`:

- Sends typed JSON requests to `{RAG_BASE_URL}/api/v1/ask`.
- Uses configured connect/read timeouts.
- Does not log the full user question.
- Does not log secrets.
- Preserves RAG response fields: `answer`, `confidence`, `abstained`, `citations`, `sources`.
- Adds backend-side classification through `answer_source`.
- Maps timeout/unavailable/malformed/unexpected status conditions into controlled backend exceptions.

## 9. Error Handling

Implemented controlled errors:

- Invalid request body: `400 INVALID_REQUEST`
- Validation failure: `400 INVALID_REQUEST`
- RAG timeout: `504 RAG_TIMEOUT`
- RAG unavailable: `503 RAG_UNAVAILABLE`
- Malformed RAG response: `502 RAG_MALFORMED_RESPONSE`
- Unexpected RAG HTTP status: `502 RAG_UNEXPECTED_STATUS`
- Unexpected backend exception: `500 INTERNAL_ERROR`

Stack traces are logged internally and not returned to API consumers.

## 10. Tests Executed

### Backend tests

Command:

```powershell
$env:MAVEN_USER_HOME=(Join-Path (Get-Location) '.m2home')
.\mvnw.cmd "-Dmaven.repo.local=.m2home\repository" test
```

Result:

```text
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Backend test coverage added:

- Valid `/api/v1/ask` request forwarding.
- Blank question validation.
- Controlled RAG timeout response.
- RAG client grounded response mapping.
- RAG client abstained response mapping.
- RAG client general fallback classification.
- RAG malformed response handling.
- RAG unexpected HTTP status handling.
- RAG timeout/unavailable exception factories.
- RAG/security property binding.
- JWT/Bhashini placeholder property binding.
- Application context load with database autoconfiguration excluded for isolated backend test.

### Backend package check

Command:

```powershell
$env:MAVEN_USER_HOME=(Join-Path (Get-Location) '.m2home')
.\mvnw.cmd "-Dmaven.repo.local=.m2home\repository" package -DskipTests
```

Result:

```text
Initial package check before the final JWT/Bhashini placeholder patch: BUILD SUCCESS
Final package retry after the placeholder patch: BUILD FAILURE due to Windows file lock on target/ip-sakti-backend-0.0.1-SNAPSHOT.jar during Spring Boot repackage rename.
```

Note: Maven package required downloading Maven plugin artifacts from Maven Central. The first sandboxed attempt failed with network permission denial; the command was rerun with approved network escalation. After the final patch, all source files compiled during `mvn test`, but the package retry could not rename the existing jar because another Windows process still held the file handle from the earlier local smoke-test attempt.

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

No RAG dataset rebuild was performed.

## 11. Local API Verification

Verified:

- Spring Boot tests confirm `/api/v1/ask` accepts valid requests and returns the RAG-shaped response.
- Spring Boot tests confirm invalid requests receive controlled 4xx responses.
- Spring Boot tests confirm RAG timeout/unavailable/malformed conditions map to safe backend errors.
- The backend package builds successfully.

Attempted:

- A local jar-based smoke test against a mock RAG HTTP endpoint.

Result:

- Backend jar started enough for `/health` probing during the harness, but the mocked RAG endpoint path returned a backend `503 RAG_UNAVAILABLE`.
- This mock smoke test is therefore not claimed as a successful end-to-end HTTP verification.

Not claimed:

- No production Supabase-backed backend/RAG integration was executed.
- No production LLM-backed answer generation was executed from the Spring Boot backend.
- No new RAG quality/evaluation metrics were generated.

## 12. Dataset Integrity Verification

No dataset rebuild/download command was run.

Dataset artifact hashes checked:

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

## 13. Secret Handling

Implemented:

- Backend `.env.example` contains placeholders only.
- Runtime secrets are expected through environment variables.
- `.env` remains ignored.
- `.m2home/` is ignored to avoid committing local Maven cache.

Secret scan:

- No newly added plaintext API keys were found in backend source/config examples.
- Existing `docs/BACKEND_RAG_INTEGRATION_ANALYSIS.md` still discusses previously observed redacted credential patterns and the Supabase host as part of the security analysis.

Security note:

- Any keys that were ever committed or shared in plaintext before this phase should be treated as compromised and rotated. This phase does not rotate external credentials.

## 14. Known Limitations

- The backend currently provides the integration boundary and delegates RAG internals to the Python service.
- JWT and Bhashini are configuration placeholders only; authentication tokens and translation are not implemented in Phase 1/2.
- `domain` and `jurisdiction` validation is intentionally light in the backend to avoid duplicating RAG-domain policy. Stricter enum validation can be added if the backend/API team wants to enforce a fixed public enum contract.
- The local mock HTTP smoke test did not complete successfully; only unit/controller/client tests are claimed.
- Final package retry is blocked by a local Windows jar file lock, although `mvn test` compiled the final source successfully.
- Production integration requires valid `RAG_BASE_URL`, Supabase settings, and RAG service credentials/configuration.
- Spring test output includes a Mockito dynamic-agent warning under the current JDK. Tests pass, but future JDKs may require configuring Mockito as a Java agent.

## 15. Production Blockers

Before production deployment:

1. Provide real environment values for:
   - `RAG_BASE_URL`
   - `BACKEND_SECURITY_MODE`
   - `BACKEND_API_KEY`
   - `BACKEND_ALLOWED_ORIGINS`
   - Supabase/database variables if backend persistence is enabled
2. Run the Python RAG service with its real Supabase/vector/LLM configuration.
3. Run a real HTTP request through Spring Boot to Python RAG:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/ask" `
  -ContentType "application/json" `
  -Body '{"question":"What are the requirements for registering a trademark in India?"}'
```

4. Validate the returned answer, confidence, citations, sources, and `answer_source`.
5. Rotate any credentials that may have been exposed before this implementation.

## 16. Recommended Phase 3

Recommended next work:

1. Add frontend wiring to call backend `POST /api/v1/ask`.
2. Add UI display for:
   - answer
   - confidence
   - answer source
   - citations
   - sources
3. Run a real local end-to-end test with:
   - Python RAG service running
   - Spring Boot backend running
   - frontend calling backend only
4. Add deployment-specific secrets/configuration in the hosting environment.
5. Add CI checks for backend tests, RAG tests, and secret scanning.

## 17. Completion Status

Implemented:

- Phase 1 security/config cleanup.
- Phase 2 backend-to-RAG API boundary.
- JWT-ready and Bhashini-ready environment configuration placeholders.
- Backend request/response DTOs.
- Backend RAG client.
- Backend ask endpoint.
- Controlled error handling.
- Backend `.env.example`.
- Tests for the implemented boundary.

Tested:

- Backend tests: 14 passed.
- Backend package build: initial package success; final retry blocked by Windows jar file lock after placeholder patch.
- Python RAG tests: 36 passed.
- Dataset artifacts were checked by hash.

Not tested:

- Real production Supabase-backed Spring-to-RAG call.
- Real LLM-backed Spring-to-RAG call.
- Browser/frontend integration.

Blocked:

- Production verification is blocked on real runtime service configuration and credentials.
- Final jar packaging retry is blocked until the local process holding `target/ip-sakti-backend-0.0.1-SNAPSHOT.jar` exits or releases the file.
