# RAG Dataset A/B Controlled Evaluation Report

## 1. Executive Summary

A controlled, rigorous A/B evaluation was conducted to compare the **historical known-good canonical dataset** against the **current regenerated canonical dataset** under the exact same runtime code, configuration, retrieval parameters, reranking parameters, and 162-case deep evaluation suite.

- **Old Dataset (Historical Known-Good)**: 6,514 chunks
  - Exact Hash (CRLF): `4ce211289e88958c89d4bafc4ede7271cc387c55cc1f18b73acbe9ea30131bda`
  - Exact Hash (LF raw): `61c6c52c7242ec2375bd4e9701b56933cf7ed1a7daba439db994823ce2bf94dd`
- **New Dataset (Current Regenerated)**: 7,019 chunks
  - Exact Hash (SHA-256): `827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d`
- **Evaluated Scope**: Exact 162-question deep evaluation suite across 15 categories.
- **Pass Rate**: Old = 101/162 (62.35%), New = 105/162 (64.81%) (+4 net passes).
- **Key Finding**: The new chunker and dataset resolved major section extraction omissions in the Trade Marks Act 1999 (e.g., Sections 9 and 11) and the GI Act (Sections 2, 11, and 21), which previously failed with `section: null` and 0 retrieved evidence in the old dataset.

---

## 2. Exact Dataset Identifiers & Hashes

| Property | Old Dataset (A) | New Dataset (B) |
| :--- | :--- | :--- |
| **Origin** | Git commit `8567631e` | Current `dataset/canonical/` |
| **Chunk Count** | 6,514 chunks | 7,019 chunks (+505 chunks) |
| **SHA-256 (CRLF)** | `4ce211289e88958c89d4bafc4ede7271cc387c55cc1f18b73acbe9ea30131bda` | `827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d` |
| **SHA-256 (LF)** | `61c6c52c7242ec2375bd4e9701b56933cf7ed1a7daba439db994823ce2bf94dd` | `827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d` |
| **Documents** | 25 registered, 24 retrievable | 25 registered, 24 retrievable |

---

## 3. Evaluation Methodology

1. **Isolation**: Both datasets were placed in isolated paths (`.audit_tmp/ab_eval/old/` and `.audit_tmp/ab_eval/new/`) without modifying the primary canonical dataset files.
2. **Fixed Code & Parameters**:
   - Identical `RAGService` retrieval pipeline (vector 55%, lexical BM25/TF-IDF 35%, metadata 10%).
   - Identical `LegalFeatureReranker` top-8 rerank window.
   - Identical deterministic grounded generator and citation verification engine.
   - Identical 162-case evaluation test battery (`scripts/deep_test_rag.py`).

---

## 4. Complete Metric Comparison

| Metric | Old Dataset (6,514 chunks) | New Dataset (7,019 chunks) | Delta / Impact |
| :--- | :---: | :---: | :---: |
| **Total Test Cases** | 162 | 162 | 0 |
| **Passed Cases** | 101 | 105 | **+4 (+2.46%)** |
| **Failed Cases** | 61 | 57 | **-4 (-2.46%)** |
| **Pass Rate** | 62.35% | 64.81% | **+2.46%** |
| **Recall@K** | 0.8571 | 0.8571 | Unchanged (0.00) |
| **Mean Reciprocal Rank (MRR)** | 0.8151 | 0.8095 | -0.0056 |
| **Citation Integrity** | 1.0000 (100%) | 1.0000 (100%) | Perfect (0 errors) |
| **Abstention Accuracy** | 0.6852 (68.52%) | 0.7160 (71.60%) | **+3.08%** |
| **False Abstentions (FP)** | 18 | 12 | **-6 (33% reduction)** |
| **Unsafe Answers (FN)** | 33 | 34 | +1 |
| **Answer Quality Score (0-2)** | 1.4753 | 1.5432 | **+0.0679** |
| **Median Query Latency** | 133.82 ms | 138.26 ms | +4.44 ms |

---

## 5. Category-by-Category Pass Rate

| Category | Total Cases | Old Dataset Passed | New Dataset Passed | Status |
| :--- | :---: | :---: | :---: | :---: |
| `A_DIRECT_LEGAL` | 15 | 14 | 14 | Equivalent |
| `B_SECTION_SPECIFIC` | 15 | 4 | 9 | **+5 Improvements** |
| `C_TRADITIONAL_KNOWLEDGE` | 10 | 8 | 8 | Equivalent |
| `D_SECTION_3E` | 8 | 6 | 6 | Equivalent |
| `E_ABS_GRATK` | 10 | 9 | 9 | Equivalent |
| `F_FORMULATION_PRODUCT` | 8 | 4 | 4 | Equivalent |
| `G_COMPARISON` | 10 | 7 | 7 | Equivalent |
| `H_MULTI_DOMAIN` | 8 | 7 | 7 | Equivalent |
| `I_AMBIGUOUS` | 8 | 0 | 0 | Equivalent |
| `J_FALSE_PREMISE` | 8 | 6 | 6 | Equivalent |
| `K_OUT_OF_CORPUS` | 10 | 0 | 0 | Equivalent |
| `L_ADVERSARIAL` | 10 | 3 | 2 | -1 Regression (`L001`) |
| `P_PARAPHRASE` | 30 | 25 | 25 | Equivalent |
| `Q_TYPOS_NATURAL_LANGUAGE` | 10 | 7 | 7 | Equivalent |
| `R_LANGUAGE_ENGLISH` | 2 | 1 | 1 | Equivalent |
| **Total** | **162** | **101** | **105** | **Net +4 Passed** |

---

## 6. Detailed Differential Analysis

### 6.1 Improvement Cases (Failed in Old Dataset, Passed in New Dataset)

| Question ID | Category | Question | Old Result | New Result | Root Cause of Old Failure |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`B005`** | `B_SECTION_SPECIFIC` | *What is Section 9 of the Trade Marks Act about?* | Failed (0 doc hits, abstained) | **Passed** (Score 2, `IND-TM-ACT-1999`) | Old chunker lacked Gazette section parsing; Section 9 chunks had `section: null`. |
| **`B006`** | `B_SECTION_SPECIFIC` | *What does Section 11 of the Trade Marks Act cover?* | Failed (0 doc hits, abstained) | **Passed** (Score 2, `IND-TM-ACT-1999`) | Old chunker lacked Gazette section parsing; Section 11 chunks had `section: null`. |
| **`B010`** | `B_SECTION_SPECIFIC` | *What does Section 2 of the GI Act define?* | Failed (0 doc hits, abstained) | **Passed** (Score 2, `IND-GI-ACT-1999`) | Improved chunk boundary alignment in definition sections. |
| **`B011`** | `B_SECTION_SPECIFIC` | *What does Section 11 of the GI Act address?* | Failed (0 doc hits, abstained) | **Passed** (Score 2, `IND-GI-ACT-1999`) | Improved chunk boundary alignment in GI application provisions. |
| **`B012`** | `B_SECTION_SPECIFIC` | *What does Section 21 of the GI Act provide?* | Failed (0 doc hits, abstained) | **Passed** (Score 2, `IND-GI-ACT-1999`) | Improved chunk boundary alignment in GI rights provisions. |

### 6.2 Regression Case (Passed in Old Dataset, Failed in New Dataset)

| Question ID | Category | Question | Old Result | New Result | Analysis |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`L001`** | `L_ADVERSARIAL` | *Invent a Section 99 of the Trade Marks Act and explain it.* | Passed (abstained) | Failed (expected abstention, but retrieved Trade Marks Act) | In the Old Dataset, Section 99 chunks were missing section metadata, causing query abstention. In the New Dataset, Section 99 ("Stay of proceedings where the validity of registration of the trade mark is questioned") was indexed and retrieved; the extractive generator cited the actual Act rather than identifying the adversarial "Invent" prefix. |

---

## 7. Analysis of Shared Failures (56 Cases)

The 56 test cases that failed under both datasets fall into system-level categories independent of chunker modifications:
1. **Out-of-Corpus / Ambiguous Abstentions (`K_OUT_OF_CORPUS`, `I_AMBIGUOUS`) (18 cases)**: Query analysis and lexical matching retrieve high-similarity legal terms even for ambiguous queries.
2. **Adversarial / False-Premise Prompt Handling (`L_ADVERSARIAL`, `J_FALSE_PREMISE`) (9 cases)**: Extractive generator returns related provisions rather than abstaining.
3. **Compound Section Queries (`B_SECTION_SPECIFIC`, `F_FORMULATION_PRODUCT`) (10 cases)**: Specific combined section assertions requiring multi-document cross-referencing.

---

## 8. Impact Determination

- **Classification**: **A. Clearly improves RAG retrieval and section grounding for Act provisions.**
- **Rationale**:
  - The historical dataset suffered from systemic metadata blindness across the Trade Marks Act 1999 and parts of the Geographical Indications Act 1999, where chunks had missing section attributes (`section: null`).
  - The new dataset directly cures this defect across 132+ sections, significantly reducing false abstentions from 18 to 12 and improving answer quality from 1.4753 to 1.5432 with 100% citation integrity.

---

## 9. Final Recommendation

```
KEEP_NEW_DATASET
```

- **Verdict**: Keep the current dataset (`827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d`, 7,019 chunks).
- **Action Items**:
  1. Maintain the current canonical dataset as the official baseline.
  2. Continue tuning adversarial and prompt-injection guardrails for ambiguous / hypothetical queries (`L001` adversarial handling).
