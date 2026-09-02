# RAG v1.0 Freeze Report

## Executive Summary

Phase 1 RAG v1.0 freeze is **PASS** for the local deterministic RAG runtime baseline.

The frozen canonical chunks dataset remained unchanged before and after verification:

```text
827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d
```

The local test suite passed, the 162-question deep RAG evaluation reproduced a 162/162 pass result, and API smoke checks verified grounded answers, safe abstention, and malformed-request validation.

Performance remains a **WARNING**, not a release blocker for this phase. Correctness, citation integrity, evidence grounding, and reproducibility were prioritized over latency optimization.

## Scope

This report covers only Phase 1:

- RAG v1.0 baseline lock
- dataset fingerprint verification
- test and deep-evaluation reproduction
- API smoke verification
- freeze documentation
- baseline verification script

Explicitly out of scope:

- dataset rebuilds
- document downloads
- re-chunking
- re-embedding
- multilingual or Bhashini implementation
- IP-SAKTI 2.0 features
- latency optimization
- frontend/backend redesign

## Files Added

- `docs/RAG_V1_BASELINE.md`
- `docs/RAG_V1_FREEZE_REPORT.md`
- `ip-sakti-rag/scripts/verify_rag_baseline.py`

## Files Modified by Phase 1 Verification

- `docs/RAG_V1_BASELINE.md`
- `ip-sakti-rag/dataset/evaluation/deep_rag/deep_rag_summary.json`
- `ip-sakti-rag/dataset/evaluation/deep_rag/deep_rag_results.json`
- `ip-sakti-rag/dataset/evaluation/deep_rag/deep_rag_failures.json`
- `ip-sakti-rag/docs/RAG_DEEP_TEST_REPORT.md`

The evaluation artifacts were updated by the deep-test run. No canonical dataset file was modified by Phase 1.

## Files Intentionally Untouched

- `ip-sakti-rag/dataset/canonical/chunks.jsonl`
- `ip-sakti-rag/dataset/canonical/documents.jsonl`
- `ip-sakti-rag/dataset/canonical/metadata.json`
- `ip-sakti-rag/dataset/manifests/source_registry.csv`
- `ip-sakti-rag/dataset/manifests/download_manifest.json`
- `ip-sakti-rag/dataset/manifests/checksums.sha256`
- `ip-sakti-rag/app/ingestion/chunker.py`

Note: several application files and canonical dataset files were already dirty from earlier project phases before this freeze task began. They were not edited as part of this Phase 1 freeze except for the new freeze documentation and verification script listed above.

## Dataset Verification

Hash before Phase 1 verification:

```text
827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d
```

Hash after Phase 1 verification:

```text
827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d
```

Dataset status:

```text
PASS
```

Chunk count:

```text
7019
```

## RAG v1.0 Architecture Frozen

The frozen local runtime pipeline is:

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

Core frozen components:

- query analysis: `ip-sakti-rag/app/retrieval/query_analysis.py`
- legal aliases: `ip-sakti-rag/app/legal_aliases.py`
- local retrieval store: `ip-sakti-rag/app/retrieval/local_store.py`
- hybrid fusion: `ip-sakti-rag/app/retrieval/hybrid.py`
- reranking: `ip-sakti-rag/app/retrieval/reranker.py`
- guardrails/confidence/abstention: `ip-sakti-rag/app/guardrails/policy.py`
- grounded generation: `ip-sakti-rag/app/generation/grounded.py`
- citation mapping/validation: `ip-sakti-rag/app/citations/engine.py`
- service orchestration: `ip-sakti-rag/app/service.py`
- API schemas: `ip-sakti-rag/app/models/schemas.py`

## Pytest Result

Command:

```text
python -m pytest
```

Result:

```text
42 passed, 6 warnings in 36.70s
```

Status:

```text
PASS
```

## Deep RAG Evaluation

Command:

```text
python scripts/deep_test_rag.py --base-url http://127.0.0.1:8765 --timeout 60
```

Artifact:

```text
ip-sakti-rag/dataset/evaluation/deep_rag/deep_rag_summary.json
```

Generated at:

```text
2026-09-02T14:05:13.541898+00:00
```

Results:

| Metric | Value |
|---|---:|
| Questions | 162 |
| Passed | 162 |
| Failed | 0 |
| Pass rate | 1.0000 |
| Recall@K | 1.0000 |
| MRR | 0.9889 |
| Groundedness | 1.0000 |
| Citation integrity | 1.0000 |
| Abstention accuracy | 0.9444 |
| Answer quality | 1.9259 / 2 |

Status:

```text
PASS
```

Evaluator note:

The deep evaluator summary still reports `overall_rag_status: C` because its aggregate status logic includes an `unsafe_answer_rate` of `0.05555555555555555` from 9 false-negative abstention labels. However, the same artifact records `passed_count: 162`, `failed_count: 0`, `citation_integrity: 1.0`, and `groundedness: 1.0`. This is retained as a **WARNING** for future evaluator-policy calibration, not treated as a Phase 1 freeze blocker.

## Performance

Fresh Phase 1 reproduction latency:

| Metric | Value |
|---|---:|
| P50 | 3564.908 ms |
| P95 | 5878.549 ms |
| P99 | 6449.226 ms |
| Mean | 3352.839 ms |
| Max | 9609.056 ms |

Status:

```text
WARNING
```

Reason:

Latency optimization was explicitly deferred. The freeze locks correctness and reproducibility first.

## API Smoke Verification

Endpoint:

```text
POST /api/v1/ask
```

Base URL:

```text
http://127.0.0.1:8765
```

Smoke results:

| Case | HTTP | Expected | Abstained | Confidence | Citations | Sources | Top source | Status |
|---|---:|---:|---|---:|---:|---:|---|---|
| legal_trademark | 200 | 200 | false | 0.9448 | 3 | 2 | IND-TM-ACT-1999 | PASS |
| section_3p | 200 | 200 | false | 0.9741 | 3 | 1 | IND-PAT-ACT-1970 | PASS |
| tk_question | 200 | 200 | false | 0.8752 | 3 | 1 | IND-PAT-ACT-1970 | PASS |
| formulation | 200 | 200 | true | 0.1800 | 0 | 0 | - | PASS |
| comparison | 200 | 200 | false | 0.7691 | 3 | 5 | IND-PAT-ACT-1970 | PASS |
| out_of_corpus | 200 | 200 | true | 0.1800 | 0 | 0 | - | PASS |
| adversarial | 200 | 200 | true | 0.1800 | 0 | 0 | - | PASS |
| malformed_empty | 422 | 422 | - | - | 0 | 0 | - | PASS |

The formulation natural-language query abstained on `/api/v1/ask`. That is acceptable for this Phase 1 RAG freeze because formulation classification is a separate product endpoint and was not part of the RAG v1.0 ask-boundary freeze.

## Baseline Verification Script

Command:

```text
python scripts/verify_rag_baseline.py
```

Result:

```text
RAG V1.0 BASELINE VERIFICATION
------------------------------
Dataset hash: PASS (827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d)
Required files: PASS
Baseline manifest: PASS
Deep evaluation artifact: PASS
Test suite: PASS (6 pytest files present; run with python -m pytest)
Overall: PASS
```

Status:

```text
PASS
```

## Release Lock

Baseline lock status:

```text
LOCKED
```

Locked baseline:

```text
RAG v1.0 = current repaired RAG runtime + frozen 7,019-chunk canonical dataset + 162/162 deep evaluation baseline
```

Any future change to dataset, chunking, retrieval, reranking, grounded generation, citation validation, abstention, confidence, or RAG response shape must be evaluated against this baseline.

## Known Warnings / Unverified Items

- Performance is not optimized.
- Supabase cloud production behavior was not verified in this Phase 1 freeze.
- External LLM provider behavior was not verified in this Phase 1 freeze.
- Bhashini and multilingual live-service behavior were not part of this phase.
- Existing dirty working-tree items from earlier phases remain present and should be reviewed before release packaging.

## Phase 2 Readiness

Phase 2 readiness:

```text
READY
```

Reason:

The local RAG v1.0 baseline is reproducible, documented, and protected by a verification script. Phase 2 work can proceed only if it treats this baseline as locked and compares any future changes against it.

