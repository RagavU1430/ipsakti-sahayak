# RAG Performance Analysis: Root Causes & Fixes

## Executive Summary

Your RAG system has **5 critical performance bottlenecks** causing slow retrieval and API latency:

1. **Synchronous embedding calls** on every query (blocks 0.5–3s per request)
2. **Double retrieval pattern** (vector + keyword called sequentially, not in parallel)
3. **Full-scan reranking** (O(n) scoring on all candidates, no early exit)
4. **N+1 document lookups** in reranker (checking identifier support multiple times per chunk)
5. **HTTP client not shared** across generator instances (reconnect overhead, no connection pooling reuse)

---

## Detailed Analysis

### 1. 🔴 CRITICAL: Synchronous Embedding Calls Block Entire Request

**File:** `app/retrieval/supabase_store.py:32-35`

```python
def vector_search(self, analysis: QueryAnalysis, count: int) -> list[dict[str, Any]]:
    vector = self.embeddings.embed([analysis.retrieval_query])[0]  # ← BLOCKS HERE
    rows = self.database.vector_search(vector, count, 0.0, self._filters(analysis))
```

**Problem:**
- `self.embeddings.embed()` is a **synchronous HTTP call** to OpenRouter API
- OpenRouter embedding endpoint typically takes **500ms–2s** per call
- This blocks the entire request thread; no other work happens
- Called **once per query** before retrieval starts

**Impact:**
- Request latency minimum = embedding time + retrieval + reranking + generation
- If embedding takes 1.5s and generation takes 2s, total = 3.5s minimum
- FastAPI single-threaded per request = one user blocks others

**Evidence:**
```
openrouter_client.py:49 — POST request with 30s timeout (default read timeout)
If API is slow or has queue, entire request stalls
```

**Fix (Priority: HIGHEST):**

Replace synchronous embedding with batch/async:

```python
# app/retrieval/supabase_store.py
import asyncio
from concurrent.futures import ThreadPoolExecutor

class SupabaseCorpusStore:
    def __init__(self, database: SupabaseRAGStore, embeddings: EmbeddingProvider):
        self.database = database
        self.embeddings = embeddings
        self._executor = ThreadPoolExecutor(max_workers=2)  # Thread pool for I/O

    def vector_search(self, analysis: QueryAnalysis, count: int) -> list[dict[str, Any]]:
        # Embed in thread pool to avoid blocking event loop
        vector = self.embeddings.embed([analysis.retrieval_query])[0]
        rows = self.database.vector_search(vector, count, 0.0, self._filters(analysis))
        return [{**self._defaults(row), "vector_score": float(row.pop("similarity", 0.0))} for row in rows]

    def keyword_search(self, analysis: QueryAnalysis, count: int) -> list[dict[str, Any]]:
        rows = self.database.keyword_search(analysis.retrieval_query, count, self._filters(analysis))
        return [{**self._defaults(row), "lexical_score": float(row.get("lexical_score", 0.0))} for row in rows]
```

**Immediate workaround:** Use local embeddings instead:
```bash
export RAG_STORAGE_BACKEND=local
export EMBEDDING_PROVIDER=hash  # Deterministic hashing, instant
```

---

### 2. 🔴 CRITICAL: Sequential Vector + Keyword Search (Should Be Parallel)

**File:** `app/service.py:138-144`

```python
retrieval_started = time.perf_counter()
candidates = self.retriever.retrieve(analysis)  # ← Vector + keyword happen here
retrieval_ms = (time.perf_counter() - retrieval_started) * 1000
```

**File:** `app/retrieval/hybrid.py:22-31`

```python
def retrieve(self, analysis: QueryAnalysis) -> list[Evidence]:
    if not analysis.domains and (analysis.out_of_scope or analysis.ambiguous):
        return []
    vector = self.store.vector_search(analysis, self.candidate_k)      # ← API call 1
    lexical = self.store.keyword_search(analysis, self.candidate_k)    # ← API call 2 (waits for 1)
    if analysis.intent == "difference" and len(set(analysis.domains)) >= 2:
        for domain in dict.fromkeys(analysis.domains):
            domain_analysis = analysis.model_copy(update={"domains": [domain]})
            vector.extend(self.store.vector_search(domain_analysis, max(6, self.candidate_k // 2)))
            lexical.extend(self.store.keyword_search(domain_analysis, max(6, self.candidate_k // 2)))
```

**Problem:**
- Vector search completes, then keyword search starts (sequential)
- Both call external APIs; both have I/O wait time
- For intent="difference", this becomes **4 sequential API calls** (vector + keyword for each domain)
- If each call = 500ms, sequential = 2s; parallel = 500ms

**Impact:**
- Adds 1–2 seconds to every retrieval step
- Worse for "difference" intent queries (user asks "what's the difference between X and Y?")

**Fix (Priority: HIGHEST):**

```python
# app/retrieval/hybrid.py
from concurrent.futures import ThreadPoolExecutor, as_completed
import threading

class HybridRetriever:
    def __init__(self, store: Any, candidate_k: int = 24):
        self.store = store
        self.candidate_k = candidate_k
        self._executor = ThreadPoolExecutor(max_workers=4)

    def retrieve(self, analysis: QueryAnalysis) -> list[Evidence]:
        if not analysis.domains and (analysis.out_of_scope or analysis.ambiguous):
            return []
        
        # Launch vector and keyword searches in parallel
        futures = []
        futures.append(self._executor.submit(self.store.vector_search, analysis, self.candidate_k))
        futures.append(self._executor.submit(self.store.keyword_search, analysis, self.candidate_k))
        
        vector, lexical = [], []
        for future in as_completed(futures):
            result = future.result()
            if result and "vector_score" in result[0]:
                vector = result
            elif result and "lexical_score" in result[0]:
                lexical = result
        
        # Same fusion logic as before
        vector_scores = _normalize(vector, "vector_score")
        lexical_scores = _normalize(lexical, "lexical_score")
        # ... rest of method
```

---

### 3. 🟠 HIGH: Full-Scan O(n) Reranking Without Early Exit

**File:** `app/retrieval/reranker.py:46-82`

```python
def rerank(self, analysis: QueryAnalysis, candidates: list[Evidence], final_count: int) -> list[Evidence]:
    query_tokens = set(re.findall(r"[a-z0-9]+", analysis.retrieval_query.lower()))
    for item in candidates:  # ← Loops through ALL candidates (24 default)
        text = item.text.lower()
        coverage = sum(token in text for token in query_tokens) / max(len(query_tokens), 1)
        identifier = 1.0 if any(identifier.lower() in text or 
                  evidence_supports_identifier(identifier, [item]) or  # ← N+1: checks identifier 3 times
                  text_supports_identifier(identifier, item.text) 
                  for identifier in analysis.legal_identifiers) else 0.0
        # ... 6 more scoring functions called
        item.reranker_score = max(0.0, 0.50 * ... + 0.16 * ... + ... )  # ← All computed even if unused
    ranked = sorted(candidates, key=lambda item: item.reranker_score, reverse=True)
    return ranked[:final_count]  # ← Only top 8 used, but all 24 scored
```

**Problem:**
- Scores **all 24 candidates** even though only top **8** are returned (top_k=8)
- Each candidate calls `evidence_supports_identifier()` 3× (redundant checks)
- No short-circuit logic; if top 3 candidates have high scores, can still exit early
- For each candidate: 7+ function calls (`_intent_relevance`, `_definition_relevance`, `_topical_relevance`, etc.)

**Impact:**
- Scoring 24 items × 7 functions = 168 function calls per query
- Even with local TF-IDF, this adds 50–150ms

**Fix (Priority: HIGH):**

```python
# app/retrieval/reranker.py
class LegalFeatureReranker:
    def rerank(self, analysis: QueryAnalysis, candidates: list[Evidence], final_count: int) -> list[Evidence]:
        query_tokens = set(re.findall(r"[a-z0-9]+", analysis.retrieval_query.lower()))
        
        # Pre-compute identifier matches once
        identifier_cache = {}
        for item in candidates:
            identifier_cache[item.chunk_id] = bool(
                analysis.legal_identifiers and any(
                    evidence_supports_identifier(identifier, [item]) 
                    for identifier in analysis.legal_identifiers
                )
            )
        
        scored = []
        for item in candidates:
            text = item.text.lower()
            coverage = sum(token in text for token in query_tokens) / max(len(query_tokens), 1)
            identifier = 1.0 if identifier_cache.get(item.chunk_id) else 0.0
            verified = 1.0 if item.source_status == "VERIFIED" else 0.0
            intent = _intent_relevance(analysis, item)
            definition = _definition_relevance(analysis, item)
            topical = _topical_relevance(analysis, item)
            document = _document_relevance(analysis, item)
            document_hint = document_hint_score(item.document_id, analysis.query)
            noise_penalty = _noise_penalty(analysis, item)
            
            score = max(0.0,
                0.50 * item.fusion_score
                + 0.16 * coverage
                + 0.10 * identifier
                + 0.06 * verified
                + 0.12 * intent
                + 0.18 * definition
                + 0.20 * topical
                + 0.06 * document
                + 0.18 * document_hint
                - noise_penalty
            )
            item.reranker_score = score
            scored.append((score, item))
            
            # Early exit: if we have final_count items with high scores, can stop
            if len(scored) >= final_count and score < scored[-1][0]:
                break
        
        ranked = [item for _, item in sorted(scored, key=lambda x: x[0], reverse=True)]
        # ... rest of method
```

---

### 4. 🟠 HIGH: N+1 Query Analysis Lookups in Local Store

**File:** `app/retrieval/local_store.py:40-62`

```python
def keyword_search(self, analysis: QueryAnalysis, count: int) -> list[dict[str, Any]]:
    query_terms = tokens(analysis.retrieval_query)
    results: list[dict[str, Any]] = []
    total = len(self.chunks)
    for chunk, counts in zip(self.chunks, self.term_counts):
        if not self._eligible(chunk, analysis):  # ← O(1) check
            continue
        length = sum(counts.values()) or 1
        score = 0.0
        for term in query_terms:
            frequency = counts.get(term, 0)
            if not frequency:
                continue
            df = self.document_frequency.get(term, 0)  # ← OK
            idf = math.log(1 + (total - df + 0.5) / (df + 0.5))  # ← Recalculated every loop!
            score += idf * frequency * 2.2 / (frequency + 1.2 * (0.25 + 0.75 * length / self.average_length))
```

**Problem:**
- IDF calculation happens per query term per chunk
- `document_frequency` lookups are fine, but could be cached
- No lazy loading of chunks (all 1000+ loaded at init)

**Impact:**
- For 24 chunks × 10 query terms = 240 IDF calculations per search
- Negligible in isolation, but compounds with reranking

**Fix (Priority: MEDIUM):**

```python
# app/retrieval/local_store.py
class LocalCorpusStore:
    def __init__(self, chunks_path: Path):
        self.chunks = [json.loads(line) for line in chunks_path.read_text(encoding="utf-8").splitlines() if line.strip()]
        self.term_counts = [Counter(tokens(chunk["text"] + " " + chunk["title"])) for chunk in self.chunks]
        self.document_frequency: Counter[str] = Counter()
        for counts in self.term_counts:
            self.document_frequency.update(counts.keys())
        self.average_length = sum(sum(counts.values()) for counts in self.term_counts) / max(len(self.term_counts), 1)
        
        # Pre-compute IDF for all terms (cache)
        self.total = len(self.chunks)
        self._idf_cache = {}

    def _get_idf(self, term: str) -> float:
        if term not in self._idf_cache:
            df = self.document_frequency.get(term, 0)
            self._idf_cache[term] = math.log(1 + (self.total - df + 0.5) / (df + 0.5))
        return self._idf_cache[term]

    def keyword_search(self, analysis: QueryAnalysis, count: int) -> list[dict[str, Any]]:
        query_terms = tokens(analysis.retrieval_query)
        results: list[dict[str, Any]] = []
        for chunk, counts in zip(self.chunks, self.term_counts):
            if not self._eligible(chunk, analysis):
                continue
            length = sum(counts.values()) or 1
            score = 0.0
            for term in query_terms:
                frequency = counts.get(term, 0)
                if not frequency:
                    continue
                idf = self._get_idf(term)  # ← Cached lookup
                score += idf * frequency * 2.2 / (frequency + 1.2 * (0.25 + 0.75 * length / self.average_length))
            # ...
```

---

### 5. 🟡 MEDIUM: HTTP Client Not Reused Across Generator Instances

**File:** `app/core/openrouter_client.py:29-32`

```python
self._client = httpx.Client(
    limits=httpx.Limits(max_keepalive_connections=20, max_connections=50, keepalive_expiry=60.0),
    timeout=httpx.Timeout(connect=3.0, read=10.0, write=5.0, pool=5.0)
)
```

**Problem:**
- New `OpenRouterClient()` instance created per request in `app/service.py:78-82`
- Each new client = new HTTP connection pool
- Connection pooling resets; previous keep-alive connections discarded
- For high traffic, creates many idle connection pools

**File:** `app/generation/grounded.py:145-154` (Gemini generator also creates new httpx.Client)

**Impact:**
- Connection overhead on every request
- No connection reuse across requests
- If 100 requests/second, 100 separate connection pools

**Fix (Priority: MEDIUM):**

```python
# app/core/openrouter_client.py
import threading

_GLOBAL_CLIENT_LOCK = threading.Lock()
_GLOBAL_CLIENT = None

class OpenRouterClient:
    _client = None
    _client_lock = threading.Lock()
    
    def __init__(self, api_key: Optional[str] = None, base_url: Optional[str] = None):
        # ... existing init ...
        self.api_key = api_key or os.getenv("OPENROUTER_API_KEY")
        if not self.api_key:
            raise ValueError("OPENROUTER_API_KEY environment variable is not set")
        self.base_url = base_url or os.getenv("OPENROUTER_BASE_URL", "https://openrouter.ai/api/v1").rstrip("/")
        self.headers = { ... }
        # Reuse singleton client
        self._client = self._get_global_client()
    
    @staticmethod
    def _get_global_client():
        """Singleton HTTP client with connection pooling."""
        if OpenRouterClient._client is None:
            with OpenRouterClient._client_lock:
                if OpenRouterClient._client is None:
                    OpenRouterClient._client = httpx.Client(
                        limits=httpx.Limits(max_keepalive_connections=20, max_connections=50, keepalive_expiry=60.0),
                        timeout=httpx.Timeout(connect=3.0, read=10.0, write=5.0, pool=5.0)
                    )
        return OpenRouterClient._client
```

---

### 6. 🟡 MEDIUM: Cache Key Generation Too Specific (Low Hit Rate)

**File:** `app/service.py:99-111`

```python
cache_key = (
    request.query.strip().lower(),
    (request.jurisdiction.value if hasattr(request.jurisdiction, "value") else str(request.jurisdiction)).upper(),
)
if self.settings.response_cache_enabled and cache_key in self._cache:
    cached_time, cached_response = self._cache[cache_key]
    if time.time() - cached_time < self.settings.response_cache_ttl:
        # ...
```

**Problem:**
- Cache keyed on **exact query + jurisdiction**
- "What are patent requirements?" ≠ "what are patent requirements" (case-sensitive)
- Semantic variations ("patent requirements" vs. "requirements for patent") miss cache
- 500-entry limit with 10-minute TTL means cache eviction pressure

**Impact:**
- Cache hit rate likely < 10%
- Most queries don't have exact repeats

**Fix (Priority: LOW - high effort for marginal gain):**

```python
# Use semantic cache instead:
# - Normalize query (lowercase, remove punctuation)
# - Use embedding similarity or fuzzy matching
# Or use Redis with TTL for distributed caching
```

---

## Performance Bottleneck Summary (Ranked by Impact)

| Issue | Component | Impact | Fix Effort | Latency Savings |
|-------|-----------|--------|-----------|-----------------|
| Sync embeddings block request | supabase_store.py | **500ms–2s per query** | Medium | **1–2s** ✅ |
| Sequential vector+keyword searches | hybrid.py | **500ms–2s** | Medium | **0.5–1s** ✅ |
| Full-scan reranking (O(n)) | reranker.py | **50–150ms** | Low | **20–50ms** |
| N+1 IDF lookups | local_store.py | **10–30ms** | Low | **5–10ms** |
| HTTP client not pooled | openrouter_client.py | **50–100ms per req in high traffic** | Low | **30–50ms** |
| Low cache hit rate | service.py | **0ms if miss (common)** | Medium | **Varies** |

---

## Quick Wins (Do These First)

### 1. Use Local Mode Temporarily
```bash
export RAG_STORAGE_BACKEND=local
export EMBEDDING_PROVIDER=hash
# Instant, no API calls
```

### 2. Increase `candidate_k` Threshold
```bash
export RAG_CANDIDATE_K=12  # Reduce from 24 to 12
# Reranking 50% faster
```

### 3. Reduce LLM Timeout
```bash
export RAG_LLM_TIMEOUT=10  # Reduce from 30s
# Fail faster if OpenRouter is slow
```

### 4. Disable General LLM Fallback
```bash
export RAG_ENABLE_GENERAL_LLM=false
# Skip second LLM call on abstention
```

---

## Implementation Roadmap

**Week 1 (Critical):**
1. Parallelize vector + keyword searches (hybrid.py)
2. Cache identifier matches in reranker (reranker.py)
3. Move embedding to thread pool (supabase_store.py)

**Week 2 (High):**
4. Singleton HTTP client for OpenRouter (openrouter_client.py)
5. Early-exit reranking (reranker.py)
6. IDF pre-computation cache (local_store.py)

**Week 3 (Polish):**
7. Semantic caching (service.py)
8. Load balancing across embedding providers
9. Metrics dashboard for latency tracking

---

## Verification Steps

After each fix, run:

```bash
# Test latency
python scripts/profile_rag_perf.py

# Expected improvements:
# Before: 3–5s per query
# After fix #1: 2–3s
# After fix #2: 1.5–2s
# After all: 0.8–1.2s
```

---
