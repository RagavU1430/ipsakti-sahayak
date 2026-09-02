# RAG v1.0 Baseline

## Status

RAG QUALITY GATE: PASSED

Baseline name: **RAG v1.0**

Baseline definition:

```text
RAG v1.0 = current repaired RAG runtime + frozen 7,019-chunk canonical dataset + 162/162 deep evaluation baseline
```

## Dataset

Canonical dataset hash:

```text
827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d
```

Dataset integrity:

```text
PASS
```

Canonical dataset file:

```text
ip-sakti-rag/dataset/canonical/chunks.jsonl
```

Chunk count:

```text
7019
```

Dataset freeze:

```text
FROZEN
```

## Deep Evaluation

Tests:

```text
162
```

Passed:

```text
162
```

Failed:

```text
0
```

Recall@K:

```text
1.0000
```

MRR:

```text
0.9889
```

Abstention Accuracy:

```text
0.9444
```

Citation Integrity:

```text
1.0000
```

Answer Quality:

```text
1.9259 / 2
```

Deep evaluation artifact:

```text
ip-sakti-rag/dataset/evaluation/deep_rag/deep_rag_summary.json
```

## Pytest

Tests:

```text
42 passed
```

Warnings:

```text
6
```

## Performance

Locked repair baseline latency:

P50:

```text
4597.627 ms
```

P95:

```text
6096.522 ms
```

P99:

```text
6958.125 ms
```

Status:

```text
PERFORMANCE WARNING
```

Performance note:

The v1.0 repair intentionally prioritized correctness, groundedness, citation integrity, and reproducibility. Latency optimization is explicitly deferred to a future phase.

Phase 1 freeze reproduction latency from `ip-sakti-rag/dataset/evaluation/deep_rag/deep_rag_summary.json` generated at `2026-09-02T14:05:13.541898+00:00`:

```text
P50: 3564.908 ms
P95: 5878.549 ms
P99: 6449.226 ms
```

## Architecture

Current RAG pipeline:

```text
Question
-> Query Processing
-> Legal Metadata Filtering
-> Hybrid Retrieval
-> Fusion
-> Legal Reranking
-> Evidence Sufficiency
-> Grounded Generation
-> Citation Validation
-> Confidence / Abstention
-> Final Answer
```

## Component Baseline

| Component | Source file | Current behavior | Deterministic by default | External dependency in v1.0 local mode | Reproducibility risk | Audit result |
|---|---|---|---|---|---|---|
| Query analysis | `ip-sakti-rag/app/retrieval/query_analysis.py` | Normalizes queries, detects domains/jurisdiction/intent/legal identifiers, expands retrieval query | Yes | No | Alias changes affect routing | PASS |
| Legal aliases | `ip-sakti-rag/app/legal_aliases.py` | Provides typo normalization, document hints, treaty/source aliases, text-aware provision matching | Yes | No | Alias table is frozen for v1.0 | PASS |
| Metadata filtering | `ip-sakti-rag/app/retrieval/local_store.py`, `ip-sakti-rag/app/retrieval/supabase_store.py` | Filters by jurisdiction/domain and preserves source metadata | Yes locally | Supabase only when configured | Backend selection via env vars | PASS |
| Keyword retrieval | `ip-sakti-rag/app/retrieval/local_store.py` | Deterministic BM25-like lexical scoring with identifier/document boosts | Yes | No | Floating-score tie ordering should remain stable with current dataset order | PASS |
| Vector retrieval | `ip-sakti-rag/app/retrieval/local_store.py` | Deterministic hashed TF-IDF cosine local vector signal | Yes | No | Python math/order stable for current inputs | PASS |
| Supabase vector/keyword retrieval | `ip-sakti-rag/app/retrieval/supabase_store.py`, `ip-sakti-rag/app/core/db.py` | Production-capable pgvector/RPC retrieval path when configured | Depends on DB ordering/RPC | Yes | Not part of local frozen verification unless env configured | WARNING |
| Fusion | `ip-sakti-rag/app/retrieval/hybrid.py` | Combines vector, lexical, and metadata/document-hint scores | Yes | No | Score weighting is frozen | PASS |
| Reranking | `ip-sakti-rag/app/retrieval/reranker.py` | Deterministic legal-feature reranking with intent/provision/source balancing | Yes | No | Tie ordering follows candidate order | PASS |
| Source/document balancing | `ip-sakti-rag/app/retrieval/reranker.py` | Balances comparison evidence by domain and document hints | Yes | No | Alias/document hints affect comparison selection | PASS |
| Evidence sufficiency | `ip-sakti-rag/app/guardrails/policy.py` | Requires relevant evidence, provision support, intent alignment, and safe abstention | Yes | No | Thresholds/env vars can affect behavior | PASS |
| Abstention policy | `ip-sakti-rag/app/service.py`, `ip-sakti-rag/app/guardrails/policy.py` | Prefers abstention/clarification over uncited generic fallback for insufficient evidence | Yes | No | Product policy must remain frozen | PASS |
| Grounded generation | `ip-sakti-rag/app/generation/grounded.py` | Uses deterministic extractive generator locally; optional OpenRouter JSON generator when enabled | Yes locally | OpenRouter only when enabled | External LLM would add nondeterminism | PASS local / WARNING external |
| Citation generation | `ip-sakti-rag/app/citations/engine.py` | Maps used evidence IDs to backend-owned citation metadata | Yes | No | Depends on retrieved evidence IDs | PASS |
| Citation validation | `ip-sakti-rag/app/citations/engine.py` | Rejects unsupported/invented provisions and validates citations against retrieved evidence | Yes | No | Text-aware provision matching is frozen | PASS |
| Confidence calculation | `ip-sakti-rag/app/guardrails/policy.py` | Deterministic score from reranker, citations, authority, support, consistency, alignment | Yes | No | Threshold changes affect labels | PASS |
| API response schema | `ip-sakti-rag/app/models/schemas.py`, `ip-sakti-rag/app/service.py` | Exposes answer, confidence, abstained, citations, and sources | Yes | No | Schema is frozen for v1.0 | PASS |
| Tests | `ip-sakti-rag/tests/` | 42-test regression suite covering API, retrieval, grounding, dataset, Supabase contract | Yes | No | Test expectations are v1.0 contract | PASS |

## Freeze Rules

- dataset is frozen
- chunking is frozen
- embeddings are frozen
- retrieval behavior is frozen
- reranking behavior is frozen
- evaluation baseline is frozen
- citation contract is frozen
- response schema is frozen

## Future Changes

Future features must not silently change the RAG v1.0 baseline.

Any change to retrieval, ranking, chunking, dataset, grounding, citation validation, confidence, or guardrails requires:

1. explicit versioned change scope,
2. dataset fingerprint verification,
3. full pytest rerun,
4. 162-question deep evaluation rerun,
5. comparison against this baseline,
6. updated baseline/version document if accepted.

## Deferred Work

The following are not part of RAG v1.0 freeze:

- multilingual translation,
- Bhashini integration,
- IndicTrans2,
- new IP-SAKTI 2.0 intelligence features,
- latency optimization,
- dataset rechunking,
- re-embedding,
- frontend or backend feature changes.
