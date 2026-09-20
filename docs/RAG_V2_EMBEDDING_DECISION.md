# RAG V2 Embedding Model Decision

**Date:** 2026-09-19  
**Status:** SELECTED  
**Decision:** Use OpenAI's `text-embedding-3-small` via OpenRouter

---

## SELECTED MODEL

**Model:** `openai/text-embedding-3-small`  
**Provider:** OpenRouter  
**Dimension:** 1536  
**Similarity Metric:** Cosine similarity  

---

## RATIONALE

### 1. Retrieval Quality
- **Strong performance on legal/regulatory retrieval** — OpenAI embedding-3-small demonstrates competitive performance on domain-specific retrieval benchmarks
- **Multilingual support** — Handles English and Indian languages (Hindi, Tamil, Marathi, etc.) reasonably well, important for AYUSH/TK domains
- **Legal terminology** — Trained on broad data including legal/regulatory text
- **Long context** — Supports up to 8,191 tokens per text (chunk max is 700 tokens, well within limit)

### 2. Production Practicality
- **Stable, versioned model** — OpenAI maintains consistent versions; model behavior is reproducible
- **High reliability** — OpenAI's infrastructure is production-grade
- **Batch support** — Efficient batch processing via OpenRouter API
- **Rate limiting** — Well-documented, easy to implement retry logic
- **Cost-effective** — text-embedding-3-small is the cost-optimized variant

### 3. Multilingual Capability
- **English:** Native support (primary language for IP/regulatory corpus)
- **Indian languages:** Reasonable support for Hindi, Tamil, Marathi, Gujarati, Kannada (important for AYUSH/traditional knowledge)
- **Limitation:** Not specifically trained for Sanskrit or highly specialized traditional medicine terminology

### 4. Domain Suitability
- **IP law:** Handles patents, trademarks, copyright, designs, GI terminology
- **Regulations:** Strong on regulatory language and structured legal provisions
- **Ayurveda:** Reasonable performance, though not specialized
- **Biodiversity/ABS:** Handles technical environmental terminology
- **International IP:** Supports WIPO treaty language

### 5. Embedding Dimension
- **1536 dimensions:** Reasonable balance between:
  - Expressiveness (can capture nuanced legal/regulatory distinctions)
  - Storage efficiency (manageable for 13K+ chunks in PostgreSQL)
  - Query speed (pgvector HNSW search remains fast)

### 6. Similarity Metric
- **Cosine similarity:** Standard for embedding-based retrieval
- **Numerically stable:** Works well with normalized vectors (OpenAI embeddings are already normalized)
- **Well-supported in pgvector:** Native support with `<=>` operator

### 7. API Requirements
- **OpenRouter API:** 
  - Endpoint: `https://openrouter.ai/api/v1/embeddings`
  - Authentication: Bearer token
  - Batching: Supported (up to 2048 texts per batch)
  - Rate limit: Per-minute limits enforced by OpenRouter
  - Timeout: 60 seconds reasonable for batches
  - Retries: Exponential backoff with jitter

### 8. Cost Considerations
- **Per-token pricing:** `$0.02 per 1M tokens` (as of 2026-09)
- **13,093 chunks × ~420 tokens average = ~5.5M tokens**
- **Estimated cost for corpus:** ~$0.11 (one-time embedding generation)
- **Cost-effective** for a foundation-phase corpus

### 9. Reproducibility
- **Model versioning:** OpenAI maintains specific versions (e.g., text-embedding-3-small-20240514)
- **Deterministic:** Same text always produces same embedding (within floating-point precision)
- **Recordable:** Model version + timestamp recorded in embedding manifest
- **Rebuild-safe:** Can regenerate embeddings deterministically

---

## ALTERNATIVES CONSIDERED

### 1. Anthropic's Claude Embeddings (claude-text-embedding)
**Why not selected:**
- Not yet available via standard API as of 2026-09
- Dependency on Claude provider may complicate infrastructure
- OpenAI embedding-3-small is well-proven and stable

### 2. Meta's LLaMA Embeddings (via Ollama/local)
**Why not selected:**
- Requires local GPU or significant infrastructure
- IP-SAKTI is a web service; cloud-native approach preferred
- Smaller local models (384-768 dims) may not capture legal nuance
- Operational complexity of running local embedding service

### 3. Sentence Transformers (local, open-source)
**Why not selected:**
- `all-MiniLM-L6-v2` (384 dims): Too small for legal/regulatory domain
- `all-mpnet-base-v2` (768 dims): Reasonable, but:
  - Requires local GPU/CPU
  - Operational complexity
  - Less proven on IP-SAKTI domain mix
  - Harder to scale to production volumes

### 4. Google's Text Embedding (embeddings-001, Vertex AI)
**Why not selected:**
- Requires Google Cloud Platform account
- Different authentication/rate-limiting model
- Less familiar to IP-SAKTI infrastructure team
- OpenRouter integration is more straightforward

---

## KNOWN LIMITATIONS

1. **Specialized Domain:** Not specifically trained on Indian legal/regulatory terminology. Performance on AYUSH/traditional knowledge terminology is reasonable but not optimized.

2. **Multilingual Performance:** While supported, non-English retrieval may be suboptimal. Primary corpus language is English; Hindi and other Indian languages are secondary.

3. **Model Updates:** OpenAI may update the model. Breaking changes are rare but possible. Mitigated by:
   - Recording exact model version in embedding manifest
   - Versioning embeddings separately from model
   - Rebuild capability if model changes

4. **Cost:** Cloud-dependent. Requires internet connection and OpenRouter API key. Not suitable for fully offline deployments.

5. **API Rate Limits:** OpenRouter enforces per-minute rate limits. Batch processing mitigates but not eliminates this constraint.

---

## IMPLEMENTATION REQUIREMENTS

### Configuration
```python
EMBEDDING_MODEL = "openai/text-embedding-3-small"
EMBEDDING_PROVIDER = "openrouter"
EMBEDDING_DIMENSION = 1536
SIMILARITY_METRIC = "cosine"
```

### Environment Variables
- `OPENROUTER_API_KEY` — OpenRouter API authentication token
- `EMBEDDING_BATCH_SIZE` — Configurable (default: 100 texts per batch)
- `EMBEDDING_TIMEOUT_SECONDS` — Configurable (default: 60)
- `EMBEDDING_RETRY_COUNT` — Configurable (default: 3)
- `EMBEDDING_RETRY_BACKOFF_BASE` — Configurable (default: 2)

### Reproducibility Record
Every embedding must record:
- `chunk_id` — Chunk identifier
- `content_hash` — SHA-256 of chunk content
- `embedding_model` — Model identifier (e.g., "openai/text-embedding-3-small")
- `embedding_version` — Embedding version (e.g., "v1-2026-09-19")
- `embedding` — The 1536-dimensional vector
- `created_at` — ISO 8601 timestamp

---

## RECOMMENDATION

**Proceed with openai/text-embedding-3-small via OpenRouter.**

This model balances:
- Proven retrieval quality for legal/regulatory domains
- Reasonable multilingual support
- Production-grade infrastructure
- Cost-effectiveness
- Reproducibility and stability

It is not claimed to be "best" — it is selected based on explicit engineering criteria suitable for IP-SAKTI's current needs and constraints.

---

## NEXT PHASE

Proceed to Phase 3 — Embedding Configuration (centralized config, environment variables).
