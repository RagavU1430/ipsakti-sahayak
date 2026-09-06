# IP-SAKTI Sahayak — Backend & CORS Comprehensive Audit Report

**Date:** 2026-09-03  
**Auditor:** Senior Backend Architect, Security Engineer & API QA Lead  
**Scope:** Complete repository audit of Spring Boot Backend, Python FastAPI RAG Service, React Frontend, CORS, Security Filters, and External Integrations.

---

## 1. Discovered Architecture & System Boundaries

The system strictly adheres to the mandated 4-tier enterprise boundary:

```
[Browser Client: React + Vite @ port 5173]
                    │
                    ▼  (Cross-Origin HTTP /api/v1/*, OPTIONS Preflight)
[Public Backend: Spring Boot 4.1.1 @ port 8080]
                    │
                    ▼  (Internal HTTP REST Client /api/v1/ask)
[Internal Service: Python FastAPI RAG @ port 8000]
                    │
                    ▼  (Hybrid BM25 + Vector Retrieval / LLM)
[Storage: Local Vector Store & DB (Supabase / In-Memory H2 fallback)]
```

### Non-Negotiable Boundary Verification
- **Frontend Isolation:** The React frontend communicates **exclusively** with Spring Boot (`http://localhost:8080/api/v1/*`).
- **No Direct FastAPI Calls:** Frontend contains zero references or direct calls to FastAPI (`:8000` or `:8765`).
- **No Direct Gemini Calls:** All multilingual translation calls originate server-side from `GeminiTranslationProvider.java`. `GEMINI_API_KEY` is completely absent from frontend source and bundles.
- **No Direct Database Calls:** Frontend contains no direct Supabase REST or pgvector queries.

---

## 2. Active Ports & URL Inventory

| Component | Default Port | Internal / Public | Runtime Config Property |
|---|---:|---|---|
| **Frontend (React / Vite)** | `5173` | Public Browser | `VITE_BACKEND_BASE_URL` |
| **Backend (Spring Boot)** | `8080` | Public Application / API | `SERVER_PORT` |
| **RAG Service (FastAPI)** | `8000` | Internal Service Only | `RAG_BASE_URL` |
| **PostgreSQL / Supabase** | `5432` | Internal Persistence | `SPRING_DATASOURCE_URL` |

---

## 3. API Endpoint Inventory

| HTTP Method | Path | Controller | Auth Required | Status | Description |
|---|---|---|---|---|---|
| `GET` | `/health` | `HealthController` | Public | Active | Basic liveness probe |
| `GET` | `/health/ready` | `HealthController` | Public | Active | Readiness probe |
| `POST` | `/api/v1/ask` | `AskController` | Public in Dev; API Key in Prod | Active | Direct RAG ask bridge |
| `POST` | `/api/v1/questions` | `QuestionController` | Public in Dev; API Key in Prod | Active | Full RAG + Translation pipeline |
| `GET` | `/api/v1/questions/health` | `QuestionController` | Public in Dev; API Key in Prod | Active | Questions health check |
| `POST` | `/api/v1/formulations/classify` | `FormulationController` | Public in Dev; API Key in Prod | Active | ASU formulation classification |
| `POST` | `/api/v1/regulatory/analyze` | `RegulatoryController` | Public in Dev; API Key in Prod | Active | Regulatory requirement analysis |
| `POST` | `/api/v1/tk/overlap` | `TkOverlapController` | Public in Dev; API Key in Prod | Active | Traditional knowledge overlap check |
| `POST` | `/api/v1/conversations` | `ConversationController` | Authenticated Principal | Active | Create new conversation |
| `GET` | `/api/v1/conversations` | `ConversationController` | Authenticated Principal | Active | Paginated conversation list |
| `GET` | `/api/v1/conversations/{id}` | `ConversationController` | Authenticated Owner | Active | Get full conversation & messages |
| `PATCH` | `/api/v1/conversations/{id}` | `ConversationController` | Authenticated Owner | Active | Update conversation metadata |
| `DELETE` | `/api/v1/conversations/{id}` | `ConversationController` | Authenticated Owner | Active | Delete conversation & messages |
| `POST` | `/api/v1/conversations/{id}/messages` | `ConversationController` | Authenticated Owner | Active | Ask question in conversation |
| `GET` | `/` and SPA routes | `SpaForwardController` | Public | Active | Client-side routing forwarding |

---

## 4. CORS Architecture & Configuration Analysis

### Single Source of Truth
CORS is centralized exclusively in `SecurityConfig.corsConfigurationSource`:
- **No Controller Leaks:** Zero `@CrossOrigin` annotations exist on controllers.
- **No MVC Conflicts:** No `WebMvcConfigurer.addCorsMappings` overrides exist.
- **Source:** Registered on `UrlBasedCorsConfigurationSource("/**")`.

### Active CORS Rules:
- **Allowed Origins:** `http://localhost:5173`, `http://localhost:3000`, `http://localhost:8080`.
- **Allowed Methods:** `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`.
- **Allowed Headers:** `Content-Type`, `Authorization`, `X-API-Key`, `X-Dev-User-Id`, `X-User-Id`, `Accept`, `Origin`, `X-Requested-With`.
- **Exposed Headers:** `Content-Type`, `Authorization`.
- **AllowCredentials:** `false` (Stateless token-based authentication).

### Identified CORS Defects:
1. **Preflight Interception in API-Key Mode:** `ApiKeyAuthenticationFilter` runs prior to Spring Security's authorization and does NOT exempt `HttpMethod.OPTIONS`. When the backend runs in API-key/production mode, browser preflight requests are rejected with `401 Unauthorized`.
2. **Missing `127.0.0.1:5173`:** When developers or users access Vite via `http://127.0.0.1:5173`, requests fail the origin allowlist.
3. **Missing Preflight Cache Duration:** `maxAge` is not set on `CorsConfiguration`, causing browsers to issue a preflight `OPTIONS` call before every single API interaction.

---

## 5. Spring Security & Authentication Flow

### Pipeline Order:
1. `JwtAuthenticationFilter`: Parses `Authorization: Bearer <token>`. Validates via `JwtService`. If `isDevMode()`, falls back to checking `X-Dev-User-Id`.
2. `ApiKeyAuthenticationFilter`: Active only when `apiKeyRequired()` is true (`mode != "dev"`). Checks `X-API-Key` header with constant-time equality (`MessageDigest.isEqual`).
3. `AnonymousAuthenticationFilter`.
4. `ExceptionTranslationFilter`: Returns standard JSON `401 UNAUTHORIZED` on failure.

### Security Isolation:
- `X-Dev-User-Id` is strictly guarded by `if (securityProperties.isDevMode())`. In staging/production (`mode: api-key` or `mode: prod`), `X-Dev-User-Id` is ignored.

---

## 6. Confirmed Issues, Root Causes & Severity Matrix

| ID | Severity | Category | Issue Description | Root Cause | Affected Files | Regression Risk |
|---|---|---|---|---|---|---|
| **ISS-01** | **CRITICAL** | CORS | CORS Preflight (`OPTIONS`) rejected with 401 in non-dev mode | `ApiKeyAuthenticationFilter` does not bypass `OPTIONS` requests before requiring `X-API-Key` | `ApiKeyAuthenticationFilter.java` | Low |
| **ISS-02** | **HIGH** | Persistence | Conversation deletion fails with 409 Conflict | `ConversationEntity.messages` lacks `cascade = CascadeType.ALL, orphanRemoval = true`. Foreign key violation on delete. | `ConversationEntity.java`, `ConversationService.java` | Medium |
| **ISS-03** | **HIGH** | Security / SPA | Static SPA routes blocked with 401 in non-dev mode | `ApiKeyAuthenticationFilter` only exempts `/health` and blocks `/`, `/index.html`, `/assets/**` | `ApiKeyAuthenticationFilter.java` | Low |
| **ISS-04** | **MEDIUM** | CORS | `127.0.0.1:5173` origin rejected | Default origin list only contains `localhost` variant | `SecurityProperties.java`, `application.yaml`, `.env` | Low |
| **ISS-05** | **MEDIUM** | Performance | Uncached preflight requests | `CorsConfiguration.maxAge` is not configured (default 1800s not applied) | `SecurityConfig.java` | Low |
| **ISS-06** | **MEDIUM** | Routing | Singular `/formulation` route returns 404 | `SpaForwardController` and `SecurityConfig` only map `/formulations` | `SpaForwardController.java`, `SecurityConfig.java` | Low |
| **ISS-07** | **LOW** | Resilience | RAG read timeout under peak LLM loads | Default 20s read timeout was tight for multi-step translation + RAG | `application.yaml`, `RagClient.java` | Low |

---

## 7. Recommended Fix Plan

1. **Fix `ApiKeyAuthenticationFilter.java`:**
   - Add early return for `request.getMethod().equalsIgnoreCase("OPTIONS")`.
   - Add exemption for public SPA static routes (`/`, `/index.html`, `/assets/**`, etc.).
2. **Fix `ConversationEntity.java`:**
   - Add `cascade = CascadeType.ALL, orphanRemoval = true` to `@OneToMany ... messages`.
3. **Fix `SecurityConfig.java` & `SecurityProperties.java`:**
   - Add `http://127.0.0.1:5173` to `allowedOrigins`.
   - Add `configuration.setMaxAge(3600L)` to cache preflight responses.
   - Add `/formulation` to permitAll and `SpaForwardController`.
4. **Execute Verification & Regression:**
   - Re-run `mvn test` in `ip-sakti-backend` (ensure 100% 138/138 pass).
   - Test preflight `OPTIONS` with curl.
   - Run live smoke tests across all 6 endpoints.
