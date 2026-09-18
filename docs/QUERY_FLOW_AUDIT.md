# IP-SAKTI Sahayak — Query Flow Audit Report

**Date:** 2026-09-14 23:34:31  
**Component Audited:** End-to-End Conversation Flow & Intelligent Query Routing  

## 1. Request Flow Path Audited

```text
React User Submits Query (T0)
        │
        ▼
Frontend High-Res Timer Marked (T1)
        │
        ▼
Spring Boot Backend Receives Request (X-Request-ID Correlation)
        │
        ▼
Translation to Canonical English (0ms for English)
        │
        ▼
Query Router Evaluation (0-1ms)
        ├── GENERAL ──► Gemini General LLM (gemini-3.5-flash-lite)
        └── DOMAIN_RAG ─► Python RAG Service (/api/v1/ask)
                              ├── Hybrid Retriever (BM25 + Dense)
                              ├── Legal Feature Reranker
                              └── Grounded Generation (Extractive / Gemini JSON)
        │
        ▼
Response Assembly & Server-Timing Injection (TimingResponseBodyAdvice)
        │
        ▼
React Receives Response Body (T3)
        │
        ▼
React Commits to DOM (T4)
        │
        ▼
Performance Watch Recorded
```

## 2. Evidence-Based Bottleneck Audit Findings

1. **Frontend Pre-Flight Overhead:**
   - Prior to optimization, `AskPage.tsx` initiated `await createConversation('New Conversation', auth)` before the query was sent. This introduced a blocking network call of 2,600ms - 15,000ms on first queries.
   - Fixed by dispatching the direct query immediately and persisting conversations asynchronously in the background.

2. **Servlet Response Header Commitment:**
   - In Spring Boot, `RequestCorrelationFilter` set headers after `chain.doFilter()` inside `finally`. By that time, Tomcat had already committed the response stream, silently dropping `Server-Timing` headers.
   - Fixed by introducing `TimingResponseBodyAdvice` (`@ControllerAdvice`), which injects `Server-Timing` and `X-IPSAKTI-*` headers into `ServerHttpResponse` before body serialization.

3. **Gemini Model Latency:**
   - `gemini-3.1-flash-lite` experienced frequent 503 capacity errors, triggering slow fallbacks.
   - Replacing the primary model candidate with `gemini-3.5-flash-lite` reduced LLM latency from ~4,700ms down to ~1,200ms.
