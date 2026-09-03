# IP-SAKTI RAG Deep Test Report

## 1. Executive Summary
- Runtime pipeline verified: NO
- Deep test cases: 162
- Passed: 120
- Failed: 42
- Overall RAG Status: D

## 2. Runtime Pipeline Verified
{
  "runtime_pipeline_verified": false,
  "base_url": "http://127.0.0.1:8765",
  "api_endpoint": "/api/v1/ask",
  "startup_command": "python -m uvicorn app.api.main:app --host 127.0.0.1 --port 8765 --log-level warning",
  "runtime_error_count_before_stop": 1,
  "first_runtime_error": "possible hallucination or false-premise acceptance"
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
- Pass rate: 0.7407
- Passed: 120
- Failed: 42

## 7. Retrieval Performance
- Recall@K: 1.0000
- MRR: 0.9894

## 8. Answer Quality
- Average score, 0-2: 1.4815

## 9. Grounding
- Groundedness proxy: 1.0000

## 10. Citation Integrity
- Citation integrity: 1.0000

## 11. Abstention
{
  "tp": 31,
  "tn": 89,
  "fp": 36,
  "fn": 5,
  "accuracy": 0.7407407407407407,
  "false_abstention_rate": 0.2222222222222222,
  "unsafe_answer_rate": 0.030864197530864196
}

## 12. Confidence Calibration
{
  "incorrect": 0.1975,
  "abstained": 0.18,
  "correct": 0.7357,
  "unsupported": 0.18
}

## 13. Comparison Questions
- Cases: 10
- Passed: 7
- Failed: 3

## 14. False-Premise Tests
- Cases: 8
- Passed: 4
- Failed: 4

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
- Passed: 25
- Failed: 5

## 18. Typo/Natural-Language Robustness
- Cases: 10
- Passed: 5
- Failed: 5

## 19. Latency
{
  "min": 3.39,
  "max": 28490921.378,
  "mean": 182965.08,
  "median": 6736.292,
  "p95": 15430.493,
  "p99": 30944.103
}

## 20. Failed Cases
- A001: What are the requirements for registering a trademark in India? | quality score failed
- A002: What is the purpose of the Trade Marks Act, 1999? | quality score failed
- A005: What is the purpose of copyright registration? | quality score failed
- A008: What rights does a registered patent provide? | quality score failed
- B004: What does Section 28 of the Trade Marks Act provide? | quality score failed
- B014: What is Section 6 of the Biological Diversity Act about? | quality score failed
- B015: What does Article 3 of the WIPO GRATK Treaty address? | quality score failed
- C004: How are associated traditional knowledge and biological resources treated? | quality score failed
- C005: What is the role of benefit sharing for traditional knowledge linked to biological resources? | quality score failed
- E001: What is access and benefit sharing for biological resources? | quality score failed
- E002: When is NBA approval relevant for biological resources? | quality score failed
- E003: What obligations apply when using Indian biological resources for research or IP? | quality score failed
- E004: How does benefit sharing relate to associated traditional knowledge? | quality score failed
- E008: Can benefit-sharing obligations arise from commercial use of biological resources? | quality score failed
- E009: Which regulatory review applies to biological resources and associated traditional knowledge? | quality score failed
- E010: Does an IP application involving biological resources require separate biodiversity review? | quality score failed
- F001: A herbal product containing plant extracts is intended for therapeutic use. What IP or regulatory evidence is relevant? | quality score failed
- F003: A cosmetic formulation is intended only for external use. What IP protection evidence is relevant? | quality score failed
- F004: A proprietary formulation has a novel composition. What patent evidence matters? | quality score failed
- F006: A plant-extract supplement uses community traditional knowledge. What issues arise? | quality score failed
- F007: A regional traditional food name is used as a brand. What IP evidence should be checked? | quality score failed
- G005: What is the difference between GI protection and trademark protection? | quality score failed
- G008: Compare the Madrid Protocol and Indian trademark registration. | quality score failed
- G010: What is the difference between trademark opposition and trademark infringement? | quality score failed
- H001: What IP and regulatory issues should be considered when developing a product based on traditional medicinal knowledge and biological resources? | quality score failed
- H002: For a herbal formulation using a regional name, what patent, GI, trademark, and ABS issues may arise? | quality score failed
- H005: What Indian and international sources address traditional knowledge in patent applications? | quality score failed
- I003: Can traditional knowledge be protected? | quality score failed
- J001: Can I patent an idea without an invention? | quality score failed
- J003: Does registration guarantee worldwide patent protection? | quality score failed

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
D - NOT RELIABLE under the current configured runtime because the API cannot initialize the RAG service.

========================================
IP-SAKTI RAG DEEP TEST COMPLETE
========================================

Tests: 162
Passed: 120
Failed: 42

Retrieval:
Recall@K: 1.0000
MRR: 0.9894

Grounding:
Citation integrity: 1.0000

Abstention:
Accuracy: 0.7407

Confidence:
Calibration: {"incorrect": 0.1975, "abstained": 0.18, "correct": 0.7357, "unsupported": 0.18}

Answer quality: 1.4815

Latency:
P50: 6736.292
P95: 15430.493
P99: 30944.103

Dataset changed: NO

Critical failures:
1. A001 - quality score failed
2. A002 - quality score failed
3. A005 - quality score failed
4. A008 - quality score failed
5. B004 - quality score failed

Overall RAG Status: D
