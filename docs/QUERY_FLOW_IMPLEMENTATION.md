# IP-SAKTI Sahayak — Query Flow Implementation Document

**Document:** `docs/QUERY_FLOW_IMPLEMENTATION.md`  
**Status:** Completed & Validated  
**Version:** 2.1.0  

---

## 1. System Architecture & Query Routing Engine

The conversational architecture implements a high-precision, two-lane intelligence routing pipeline with zero unnecessary retrieval overhead for non-domain queries and strict statutory grounding for legal queries.

```text
                                 USER
                                   │
                                   ▼
                             REACT CHAT UI
                          (http://localhost:5173/ask)
                                   │
                           T0: User Submits Query
                           T1: Start Fetch Timer
                                   │
                                   ▼
                         SPRING BOOT BACKEND API
                     (POST /api/v1/conversations/{id}/messages
                      or POST /api/v1/questions)
                                   │
                          X-Request-ID Tracking
                                   │
                                   ▼
                             QUERY ROUTER
                         (QueryRouter / QuestionIntentClassifier)
                             /          \
                            /            \
                           ▼              ▼
                     IP-SAKTI            GENERAL
                         │                  │
                         ▼                  ▼
                       RAG V2           GENERAL LLM
                (/api/v1/ask on port 8000) (gemini-3.5-flash-lite)
                         │                  │
                         ▼                  │
                 EVIDENCE VALIDATION        │
               (LegalFeatureReranker)       │
                         │                  │
                         ▼                  │
                   GROUNDED LLM             │
              (Extractive / Gemini JSON)    │
                         │                  │
                         └─────────┬────────┘
                                   ▼
                             FINAL RESPONSE
                         + Server-Timing Headers
                      (TimingResponseBodyAdvice)
                                   │
                              SAVE HISTORY
                       (Asynchronous Persistence)
                                   │
                                   ▼
                              REACT RENDER
                        T3: Body Received Timer
                        T4: DOM Render & Commit
                                   │
                                   ▼
                       REACT PERFORMANCE WATCH
                       (window.__IPSAKTI_LAST_RUN__)
```

---

## 2. Routing Policy & Grounding Rules

### A. IP-SAKTI Domain Questions:
- Route: `DOMAIN_RAG` (or `RAG`)
- Evidence handling:
  - **Evidence Sufficient:** Fully grounded response citing specific sections, statutory provisions, and documents (`citations` array populated).
  - **Evidence Partial:** Clearly indicates limitations of available statutory records.
  - **Evidence Insufficient:** Explicit, safe abstention ("I do not have sufficient authoritative evidence to answer this legal question...").
- **STRICT PROHIBITION:** Never silently fallback to general ungrounded LLM hallucinations for:
  - Indian Patents Act (e.g., Section 3(p), Section 3(e))
  - Trademarks Act, 1999
  - Biological Diversity Act, 2002 & NBA Access and Benefit Sharing (ABS)
  - Traditional Knowledge Digital Library (TKDL)
  - FSSAI & Ayurveda Aahara regulations
  - AYUSH regulatory compliance
  - WIPO, TRIPS, and International IP agreements

### B. General Conversational Questions:
- Route: `GENERAL`
- Handled by: `GeminiGeneralLlmProvider` with `gemini-3.5-flash-lite`.
- Zero RAG retrieval overhead (0 chunks retrieved, 0 retrieval ms).
- Guardrails: If user asks a general question that secretly requires statutory legal advice, guardrail triggers an automatic upgrade to `DOMAIN_RAG`.

### C. Ambiguous Queries:
- Route: `AMBIGUOUS`
- Deterministic clarification prompt guiding user to specify whether they seek general discussion or authoritative statutory compliance.

---

## 3. Response Contract

The JSON response contract returned to the client contains:

```json
{
  "request_id": "b80fd91f-3fa9-42d9-8432-611410ad64fa",
  "route": "DOMAIN_RAG",
  "domain": "PATENT",
  "routing_reason": "STATUTORY_SECTION",
  "confidence": 0.95,
  "abstained": false,
  "answer": "Based on the cited PATENT evidence, Section 3(p)...",
  "citations": [
    {
      "document_id": "the_patents_act_1970",
      "section": "Section 3(p)",
      "page": 4
    }
  ],
  "sources": [
    {
      "document_id": "the_patents_act_1970",
      "score": 0.88
    }
  ]
}
```

Exposed via HTTP Headers:
- `X-Request-ID`: Correlation identifier
- `Server-Timing`: `route;dur=0.0, retrieval;dur=326.6, llm;dur=0.2, evidence_validation;dur=11.9, rag;dur=456.1, history_save;dur=4.8, total;dur=5353.0`
- `X-IPSAKTI-Provider`: Generator name (`gemini-3.5-flash-lite`, `deterministic-extractive-v1`)
- `X-IPSAKTI-Chunks`: Number of evidence chunks passed to generator
- `X-IPSAKTI-Backend-Total-Ms`: Measured backend duration
- `X-IPSAKTI-Route`: Query route name

---

## 4. Frontend Developer Performance Watch

Implemented in [AskPage.tsx](file:///c:/Users/Ragav%20U/OneDrive/Desktop/Ragav%20Folder/Projects/SIH/ipsakti-sahayak/Frontend/src/pages/AskPage.tsx):
- Active in development mode (`import.meta.env.DEV`) or via URL parameter `?perf=1` / `?debug=1`.
- Shows real-time request ID, route badge, provider, chunk count, and full latency breakdown:
  - Frontend Overhead ($T_1 - T_0$)
  - Routing time
  - RAG Retrieval time
  - LLM Generation time
  - History Persistence time
  - Backend Total time
  - Network / Transport time ($T_3 - T_1 - \text{Backend}$)
  - React Render time ($T_4 - T_3$)
  - Total User Wait Time ($T_4 - T_0$)
- Exposes window global variables `window.__IPSAKTI_LAST_RUN__` and `window.__IPSAKTI_BENCHMARK_HISTORY__` for automated browser benchmarking.
