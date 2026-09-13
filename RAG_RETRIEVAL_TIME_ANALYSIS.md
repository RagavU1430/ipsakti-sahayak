# RAG Retrieval Time Analysis — IP Sakthi

## Summary of What Was Measured

The RAG service (`ip-sakti-rag`, FastAPI on `127.0.0.1:8000`) was benchmarked with 16 real legal questions via the public `/api/v1/ask` endpoint, plus in-process stage timing.

### End-to-end HTTP latency (16 questions, warm server)

| Metric | Before fix | After fix |
|---|---|---|
| min | 420 ms | **304 ms** |
| avg | 1609 ms | **1562 ms** |
| median | 1747 ms | **1804 ms** |
| max | 3609 ms | **3768 ms** |

The average barely moved because **generation (LLM) dominates**, but retrieval itself got much faster.

### In-process stage breakdown (first 6 questions)

| Stage | Before | After |
|---|---|---|
| analyze_query | 0–2 ms | **0–1 ms** |
| **retrieve (hybrid)** | **292–2062 ms** | **119–656 ms** |
| rerank | 5–13 ms | **5–11 ms** |
| **generation (LLM)** | 1361–2247 ms | **1361–2247 ms** (unchanged) |

## Root Causes Found & Fixed

### 1. `document_hint_score` recomputed hints per chunk (FIXED — the retrieval bottleneck)

**File:** `app/legal_aliases.py:202`, `app/retrieval/local_store.py:88,117`, `app/retrieval/hybrid.py:62`

- `document_hint_score(document_id, query)` called `document_hint_ids(query)` **once per chunk** — which runs a ~20-rule alias scan + `normalize_legal_query()` on every call.
- The corpus is **7,019 chunks** (not 2,438), so per search that was **7,019 × (alias scan + regex + normalization)** — measured **1051 ms** for one pass, and it ran **3× per query** (keyword + vector + hybrid fusion): ~2.1s of pure hint scoring.
- **Fix applied:** added `hinted_documents: frozenset[str]` to `QueryAnalysis` (computed **once** in `analyze_query`), and all three call sites now pass the precomputed set → per-chunk work is now a single `set` membership check.
- **Result:** keyword_search **570→83 ms**, vector_search **538→143 ms** (verified with direct timing).

### 2. Sequential full-corpus scans (already parallel, but verified)

`HybridRetriever.retrieve` runs vector + keyword searches in a `ThreadPoolExecutor(max_workers=2)` — confirmed working; each side still scans all 7,019 chunks, which the hint fix reduced from ~2s to ~150-650ms combined.

## Remaining Bottleneck (NOT fixed — by design)

### 3. LLM generation for non-extractive intents (1.4–2.2s)

- `_use_fast_extractive_path()` (service.py:272) only skips the LLM for **definition/duration/purpose** intents or **legal-identifier** queries.
- Questions with intent = `difference`, `how_to`, `procedure`, etc. always hit `GeminiGroundedGenerator` — **1.4–2.2s** per question. Fast-extractive questions (GI tag, prior art, copyright duration) answer in **300–550ms total**.
- The LLM provider is correctly **Gemini** (`GeminiGroundedGenerator` confirmed; `LLM_PROVIDER=gemini` in `.env`), so this is inherent Gemini latency for full legal answers, not misconfiguration.

### 4. Abstention questions waste the LLM call (2s+ then abstain)

- Questions with `conf=0.18 abstained=True` (e.g., "What is the difference between patent and copyright?", "How to protect a software invention?") run the full LLM generation (~2s) and then **abstain** because evidence is insufficient / citation validation fails.
- The response cache **intentionally skips abstentions** (service.py:261: `if ... not response.abstained`), so these never cache and repeat the 2s cost.
- **Possible optimization (not applied):** for low-confidence retrievals, skip the LLM and go straight to extractive/abstain — but this risks answer quality, so it was left as a decision for the owner.

## Recommendations (in order of impact)

1. **Accept LLM cost for "how-to/difference" questions** — it's inherent; the system is already on Gemini (fastest available with the configured key). If sub-1s is required for those, expand `_use_fast_extractive_path` to more intents, accepting extractive-style answers.
2. **Optionally cache abstentions briefly** (e.g., 60s TTL) to avoid repeated 2s wasted calls for the same low-evidence question.
3. **Difference-intent retrieval spikes** (656ms, 53 candidates from 4 domain searches) could be trimmed by lowering `candidate_k` for the per-domain difference searches (currently `max(6, candidate_k//2)` = 12/domain × 4 = 48+).

## Verified Numbers (post-fix)

```
config: candidate_k=24 top_k=8 cache_enabled=True fast_extractive=True
corpus: 7,019 chunks | storage=local | embeddings=openrouter | LLM=gemini

[ 1] What is required to register a trademark in India?     retrieve=193ms  gen=2247ms  HTTP=2662ms
[ 2] How to file a patent application in India?             retrieve=167ms  gen=1361ms  HTTP=1944ms
[ 3] What is a GI tag and how to get one?                   retrieve=138ms  gen=2ms     HTTP=304ms
[ 4] patent vs copyright (difference)                        retrieve=605ms  gen=1492ms  HTTP=3186ms
[ 5] How long does copyright protection last?               retrieve=119ms  gen=1ms     HTTP=470ms
[ 6] What is prior art in patent law?                        retrieve=169ms  gen=1ms     HTTP=497ms
```

**Fast-extractive intents (3,5,6) → 300–550ms. LLM intents → 1.4–3.8s. Retrieval itself is now 119–656ms (was up to 2s).**