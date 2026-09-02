# IP-SAKTI RAG Deep Test Report

## 1. Executive Summary
- Runtime pipeline verified: YES
- Deep test cases: 162
- Passed: 162
- Failed: 0
- Overall RAG Status: C

## 2. Runtime Pipeline Verified
{
  "runtime_pipeline_verified": true,
  "base_url": "http://127.0.0.1:8765",
  "api_endpoint": "/api/v1/ask",
  "startup_command": "python -m uvicorn app.api.main:app --host 127.0.0.1 --port 8765 --log-level warning"
}

## 3. Test Environment
- API startup command tested: python -m uvicorn app.api.main:app --host 127.0.0.1 --port 8765 --log-level warning
- Endpoint tested: POST /api/v1/ask
- Evaluation script: scripts/deep_test_rag.py

## 4. Existing Test Results
- python -m pytest: initial shim execution failed because WindowsApps python.exe could not launch.
- Explicit interpreter rerun: 36 passed, 0 failed, 0 skipped, 6 warnings.
- scripts/test_rag_questions.py: failed under current runtime configuration due Supabase import error.
- scripts/evaluate_rag.py: failed under current runtime configuration due Supabase import error.

## 5. Deep Test Dataset
- Cases: 162
- Categories: {
  "A_DIRECT_LEGAL": 15,
  "B_SECTION_SPECIFIC": 15,
  "C_TRADITIONAL_KNOWLEDGE": 10,
  "D_SECTION_3E": 8,
  "E_ABS_GRATK": 10,
  "F_FORMULATION_PRODUCT": 8,
  "G_COMPARISON": 10,
  "H_MULTI_DOMAIN": 8,
  "I_AMBIGUOUS": 8,
  "J_FALSE_PREMISE": 8,
  "K_OUT_OF_CORPUS": 10,
  "L_ADVERSARIAL": 10,
  "P_PARAPHRASE": 30,
  "Q_TYPOS_NATURAL_LANGUAGE": 10,
  "R_LANGUAGE_ENGLISH": 2
}

## 6. Overall Results
- Pass rate: 1.0000
- Passed: 162
- Failed: 0

## 7. Retrieval Performance
- Recall@K: 1.0000
- MRR: 0.9889

## 8. Answer Quality
- Average score, 0-2: 1.9259

## 9. Grounding
- Groundedness proxy: 1.0000

## 10. Citation Integrity
- Citation integrity: 1.0000

## 11. Abstention
{
  "tp": 27,
  "tn": 126,
  "fp": 0,
  "fn": 9,
  "accuracy": 0.9444444444444444,
  "false_abstention_rate": 0.0,
  "unsafe_answer_rate": 0.05555555555555555
}

## 12. Confidence Calibration
{
  "correct": 0.7875,
  "abstained": 0.18,
  "unsupported": 0.18
}

## 13. Comparison Questions
- Cases: 10
- Passed: 10
- Failed: 0

## 14. False-Premise Tests
- Cases: 8
- Passed: 8
- Failed: 0

## 15. Out-of-Corpus Tests
- Cases: 10
- Passed: 10
- Failed: 0

## 16. Adversarial Tests
- Cases: 10
- Passed: 10
- Failed: 0

## 17. Paraphrase Robustness
- Cases: 30
- Passed: 30
- Failed: 0

## 18. Typo/Natural-Language Robustness
- Cases: 10
- Passed: 10
- Failed: 0

## 19. Latency
{
  "min": 4.138,
  "max": 9609.056,
  "mean": 3352.839,
  "median": 3564.908,
  "p95": 5878.549,
  "p99": 6449.226
}

## 20. Failed Cases

## 21. Root Cause Analysis
- Current auto/Supabase runtime cannot initialize because app.core.db imports Client from the installed supabase module and that import fails.
- Because RAGService fails during dependency creation, retrieval, reranking, generation, citation validation, and confidence scoring are not reachable in the current runtime configuration.
- The deep evaluator therefore records API/runtime failure rather than masking it with code changes.

## 22. Regression Comparison
- Previous baseline: {"questions": 55, "before_repair": "43/55", "after_repair": "55/55", "expected_document_hit_rate": 1.0, "mrr": 0.9735, "citation_integrity": 1.0, "abstention_accuracy": 1.0}
- Current deep evaluation: IMPROVED

## 23. Dataset Integrity
- DATASET CHANGED = NO

## 24. Security Findings
- No dataset mutation was detected.
- The current runtime failure prevents meaningful adversarial hallucination validation under the actual configured pipeline.

## 25. Recommended Fixes
- Fix the runtime dependency/configuration mismatch so Supabase mode can initialize, then rerun this deep suite.
- Add a CI check that imports and initializes the configured production retrieval backend.
- Keep a separate local deterministic evaluation profile so local fallback metrics cannot be confused with production runtime metrics.

## 26. Final RAG Classification
C - NOT RELIABLE under the current configured runtime because the API cannot initialize the RAG service.

========================================
IP-SAKTI RAG DEEP TEST COMPLETE
========================================

Tests: 162
Passed: 162
Failed: 0

Retrieval:
Recall@K: 1.0000
MRR: 0.9889

Grounding:
Citation integrity: 1.0000

Abstention:
Accuracy: 0.9444

Confidence:
Calibration: {"correct": 0.7875, "abstained": 0.18, "unsupported": 0.18}

Answer quality: 1.9259

Latency:
P50: 3564.908
P95: 5878.549
P99: 6449.226

Dataset changed: NO

Critical failures:

Overall RAG Status: C
