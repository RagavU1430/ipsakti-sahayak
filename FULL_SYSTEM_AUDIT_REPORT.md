# IP-SAKTI Sahayak: Full System Audit Report

## 1. Executive Summary
The system is functional in terms of connectivity (Frontend $\rightarrow$ Backend $\rightarrow$ RAG), but suffers from a **critical retrieval failure** and **extreme latency**. The RAG engine currently returns a "success" status (200 OK) but provides **zero evidence**, meaning the LLM is answering without the provided legal documents.

**Overall Health Score: 🟡 Marginal** (Infrastructure is sound, but RAG logic is broken).

---

## 2. Performance & Latency Breakdown
Measured via `measure_rag.py` against the live server.

| Stage | Latency (Est.) | Status | Note |
| :--- | :--- | :--- | :--- |
| **Embedding** | 500ms - 2s | 🔴 Slow | Synchronous HTTP call to OpenRouter |
| **Retrieval** | 1s - 3s | 🔴 Broken | Returns 0 chunks (filter mismatch) |
| **Reranking** | 100ms - 500ms | 🟡 Subopt | $O(n)$ full scan of all candidates |
| **Generation** | 2s - 5s | 🟢 Normal | Standard LLM generation time |
| **TOTAL E2E** | **~8.9 seconds** | 🔴 Critical | Target is < 1.5 seconds |

---

## 3. Detailed Error Log

### 🔴 Critical Errors (Must Fix Now)
- **The "Silent Empty" Bug (`supabase_store.py`):** The `_filters` method is too strict. A mismatch in `language` or `domain` tags between the query and the DB results in 0 evidence being retrieved, even if relevant documents exist.
- **Blocking I/O (`supabase_store.py`):** The embedding call is synchronous. This blocks the entire FastAPI thread, preventing any other work from happening during the 1-2s API wait.
- **RAG Latency:** Current latency (~9s) is unacceptable for a real-time assistant.

### 🟡 Minor/Medium Errors (Technical Debt)
- **Hardcoded Infrastructure (`application.yaml`):** Supabase production host is hardcoded. This prevents seamless environment switching (Dev $\rightarrow$ Staging $\rightarrow$ Prod).
- **Port Confusion (`README.md`):** Documentation lists RAG on port `8001`, but the actual service runs on `8000`.
- **Reranker Efficiency (`reranker.py`):** Lacks early-exit logic; scores all 24 candidates even if the top 3 are perfect.

### 🟢 Small/UX Errors (Polish)
- **Print CSS:** Frontend "Print" buttons trigger browser default printing without custom CSS, resulting in a poor-quality legal report.
- **Console Leaks:** `console.log` and `window.print` statements remain in production JS assets and raw HTML dataset files.

---

## 4. Integration Flow Verification
- **Frontend $\rightarrow$ Backend:** ✅ Verified. CORS is correctly configured for `localhost:5173`.
- **Backend $\rightarrow$ RAG:** ✅ Verified. Connectivity is stable via `RAG_BASE_URL`.
- **Payload Contract:** ⚠️ Warning. Internal scripts used `query` instead of `question`, causing 422 errors. (Fixed in measurement scripts).

---

## 5. Remediation Roadmap
1. **Immediate:** Fix `_filters` in `supabase_store.py` to recover evidence.
2. **Latency:** Implement `ThreadPoolExecutor` for embeddings and early-exit for reranking.
3. **Clean:** Move hardcoded Supabase URLs to `.env` and remove console leaks.
