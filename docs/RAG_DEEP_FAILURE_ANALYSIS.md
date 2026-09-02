# RAG Deep Failure Analysis

## 1. Executive Summary

This report analyzes the current RAG deep-test failures for the frozen current canonical dataset:

- Dataset: `ip-sakti-rag/dataset/canonical/chunks.jsonl`
- SHA-256: `827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d`
- Chunks: `7,019`

The latest saved deep evaluation contains:

| Metric | Value |
|---|---:|
| Tests | 162 |
| Passed | 105 |
| Failed | 57 |
| Pass rate | 64.81% |
| Recall@K | 0.8571 |
| MRR | 0.8095 |
| Citation integrity | 1.0000 |
| Abstention accuracy | 0.7160 |
| Answer quality | 1.5432 / 2 |
| P50 latency | 119.729 ms |
| P95 latency | 314.805 ms |
| P99 latency | 542.242 ms |

Primary failure classification for the 57 failed cases:

| Primary category | Count |
|---|---:|
| `ABSTENTION_FAILURE` | 37 |
| `RETRIEVAL_FAILURE` | 18 |
| `RANKING_FAILURE` | 2 |
| `ANSWER_QUALITY_FAILURE` | 0 |
| `CITATION_FAILURE` | 0 |
| `DATASET_COVERAGE_FAILURE` | 0 |
| `EVALUATION_FAILURE` | 0 |
| `OTHER` | 0 |

Top root causes:

1. Abstention/general-fallback policy is too permissive for ambiguous, out-of-corpus, and adversarial prompts.
2. Retrieval and metadata matching miss expected source documents for section-specific, formulation, biodiversity/TK, GRATK, paraphrase, typo, and multi-domain questions.
3. Ranking/balancing does not guarantee both expected documents for comparison questions.

No dataset, chunker, retrieval code, evaluation code, or tests were modified during this analysis.

## 2. Current Dataset

Current canonical dataset:

| Field | Value |
|---|---|
| File | `ip-sakti-rag/dataset/canonical/chunks.jsonl` |
| SHA-256 | `827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d` |
| Chunk count | 7,019 |

Observed chunking characteristics for selected documents:

| Document | Chunks | Sectioned | Ruled | Articled | Median chars | Small `<100` | Large `>4500` |
|---|---:|---:|---:|---:|---:|---:|---:|
| `IND-PAT-ACT-1970` | 823 | 822 | 0 | 0 | 225 | 168 | 0 |
| `IND-TM-ACT-1999` | 557 | 516 | 0 | 0 | 246 | 43 | 0 |
| `IND-CR-ACT-1957` | 391 | 390 | 0 | 0 | 278 | 16 | 0 |
| `IND-BD-ACT-2002` | 22 | 21 | 0 | 0 | 3380 | 1 | 8 |
| `INT-WIPO-GRATK-2024` | 1 | 0 | 0 | 0 | 3558 | 0 | 0 |
| `IND-GI-ACT-1999` | 400 | 397 | 0 | 0 | 193 | 94 | 0 |
| `IND-TM-RULES-2017` | 663 | 0 | 451 | 0 | 275 | 28 | 9 |

Dataset-level finding:

- `IND-BD-ACT-2002` is unusually coarse: only 22 chunks, median chunk length 3,380 characters, and 8 chunks over 4,500 characters.
- `INT-WIPO-GRATK-2024` is a single 3,558-character chunk with no article metadata.
- Major Acts contain many very small provision chunks, e.g. `IND-PAT-ACT-1970` has 168 chunks under 100 characters and `IND-GI-ACT-1999` has 94 chunks under 100 characters.

These characteristics are plausible contributors to retrieval/ranking brittleness, but no dataset edits were made.

## 3. Deep-Test Results

Source artifacts inspected:

- `ip-sakti-rag/dataset/evaluation/deep_rag/deep_rag_results.json`
- `ip-sakti-rag/dataset/evaluation/deep_rag/deep_rag_failures.json`
- `ip-sakti-rag/dataset/evaluation/deep_rag/deep_rag_summary.json`

Pass/fail by category:

| Category | Total | Passed | Failed | Pass rate |
|---|---:|---:|---:|---:|
| `A_DIRECT_LEGAL` | 15 | 14 | 1 | 93.33% |
| `B_SECTION_SPECIFIC` | 15 | 9 | 6 | 60.00% |
| `C_TRADITIONAL_KNOWLEDGE` | 10 | 8 | 2 | 80.00% |
| `D_SECTION_3E` | 8 | 6 | 2 | 75.00% |
| `E_ABS_GRATK` | 10 | 9 | 1 | 90.00% |
| `F_FORMULATION_PRODUCT` | 8 | 4 | 4 | 50.00% |
| `G_COMPARISON` | 10 | 7 | 3 | 70.00% |
| `H_MULTI_DOMAIN` | 8 | 7 | 1 | 87.50% |
| `I_AMBIGUOUS` | 8 | 0 | 8 | 0.00% |
| `J_FALSE_PREMISE` | 8 | 6 | 2 | 75.00% |
| `K_OUT_OF_CORPUS` | 10 | 0 | 10 | 0.00% |
| `L_ADVERSARIAL` | 10 | 2 | 8 | 20.00% |
| `P_PARAPHRASE` | 30 | 25 | 5 | 83.33% |
| `Q_TYPOS_NATURAL_LANGUAGE` | 10 | 7 | 3 | 70.00% |
| `R_LANGUAGE_ENGLISH` | 2 | 1 | 1 | 50.00% |

The weakest categories are:

1. `I_AMBIGUOUS`: 0/8
2. `K_OUT_OF_CORPUS`: 0/10
3. `L_ADVERSARIAL`: 2/10
4. `F_FORMULATION_PRODUCT`: 4/8
5. `B_SECTION_SPECIFIC`: 9/15

## 4. Failure Classification

Each failed case was assigned exactly one primary category based on question, expected behavior, retrieved documents, citations, confidence, abstention decision, and artifact failure reason.

| ID | Test category | Primary failure | Abstained | Confidence | Expected docs | Returned docs | Notes |
|---|---|---|---:|---:|---|---|---|
| A003 | A_DIRECT_LEGAL | ABSTENTION_FAILURE | true | 0.18 | `IND-TM-ACT-1999` | — | False abstention for Trade Marks Act Section 18. |
| B003 | B_SECTION_SPECIFIC | ABSTENTION_FAILURE | true | 0.18 | `IND-TM-ACT-1999` | — | False abstention for Section 18. |
| B004 | B_SECTION_SPECIFIC | ABSTENTION_FAILURE | true | 0.18 | `IND-TM-ACT-1999` | — | False abstention for Section 28. |
| B008 | B_SECTION_SPECIFIC | ABSTENTION_FAILURE | true | 0.18 | `IND-CR-ACT-1957` | — | False abstention for Copyright Act Section 14. |
| B013 | B_SECTION_SPECIFIC | RETRIEVAL_FAILURE | false | 0.7073 | `IND-BD-ACT-2002` | `IND-BD-AMEND-2023` | Expected document not returned. |
| B014 | B_SECTION_SPECIFIC | ABSTENTION_FAILURE | true | 0.18 | `IND-BD-ACT-2002` | — | False abstention for Biological Diversity Act Section 6. |
| B015 | B_SECTION_SPECIFIC | RETRIEVAL_FAILURE | false | 0.9263 | `INT-WIPO-GRATK-2024` | `INT-TRIPS-1994`, `INT-WIPO-MADRID`, `INT-WIPO-PCT`, `INT-WIPO-BUDAPEST` | GRATK article routed to wrong international documents. |
| C004 | C_TRADITIONAL_KNOWLEDGE | RETRIEVAL_FAILURE | false | 0.8952 | `IND-BD-ACT-2002`, `INT-WIPO-GRATK-2024` | `IND-BD-AMEND-2023`, `IND-BD-RULES-2024` | Wrong biodiversity/TK source selection. |
| C009 | C_TRADITIONAL_KNOWLEDGE | RETRIEVAL_FAILURE | false | 0.8927 | `IND-BD-ACT-2002`, `INT-WIPO-GRATK-2024` | `IND-BD-RULES-2024`, `IND-BD-AMEND-2023` | Wrong-source prioritization. |
| D002 | D_SECTION_3E | ABSTENTION_FAILURE | true | 0.18 | `IND-PAT-ACT-1970` | — | False abstention for Section 3(e). |
| D003 | D_SECTION_3E | ABSTENTION_FAILURE | true | 0.18 | `IND-PAT-ACT-1970` | — | False abstention for Section 3(e). |
| E004 | E_ABS_GRATK | RETRIEVAL_FAILURE | false | 0.8846 | `IND-BD-ACT-2002`, `INT-WIPO-GRATK-2024` | `IND-BD-AMEND-2023`, `IND-BD-RULES-2024` | Missing Act/GRATK evidence. |
| F001 | F_FORMULATION_PRODUCT | RETRIEVAL_FAILURE | false | 0.35 | `IND-PAT-ACT-1970`, `IND-BD-ACT-2002`, `IND-AYUSH-AR-2024-25` | — | No expected docs; no citations. |
| F003 | F_FORMULATION_PRODUCT | RETRIEVAL_FAILURE | false | 0.35 | `IND-DES-ACT-2000`, `IND-TM-ACT-1999`, `IND-PAT-ACT-1970` | — | No expected docs; no citations. |
| F005 | F_FORMULATION_PRODUCT | RETRIEVAL_FAILURE | false | 0.8872 | `IND-FSS-AA-ORDER-2025`, `IND-AYUSH-INDIA-2024` | `IND-AYUSH-AR-2024-25`, `IND-AYUSH-2024` | Nearby AYUSH docs returned, expected docs missed. |
| F006 | F_FORMULATION_PRODUCT | RETRIEVAL_FAILURE | false | 0.35 | `IND-BD-ACT-2002`, `IND-PAT-ACT-1970`, `INT-WIPO-GRATK-2024` | — | No expected docs; no citations. |
| G001 | G_COMPARISON | ABSTENTION_FAILURE | true | 0.18 | `IND-PAT-ACT-1970` | — | False abstention for comparison. |
| G004 | G_COMPARISON | RANKING_FAILURE | false | 0.8339 | `IND-BD-ACT-2002`, `INT-WIPO-GRATK-2024` | `IND-BD-ACT-2002`, `INT-TRIPS-1994`, `INT-WIPO-PCT`, `INT-WIPO-MADRID` | Comparison evidence not balanced; missing GRATK. |
| G010 | G_COMPARISON | RANKING_FAILURE | false | 0.8659 | `IND-TM-ACT-1999`, `IND-TM-RULES-2017` | `IND-TM-RULES-2017` | Comparison evidence not balanced; missing Act. |
| H007 | H_MULTI_DOMAIN | RETRIEVAL_FAILURE | false | 0.35 | `IND-GI-ACT-1999`, `IND-TM-ACT-1999` | — | Expected docs missed; no citations. |
| I001 | I_AMBIGUOUS | ABSTENTION_FAILURE | false | 0.868 | — | `IND-PAT-RULES-2003`, `IND-PAT-ACT-1970` | Ambiguous query should not receive confident legal answer. |
| I002 | I_AMBIGUOUS | ABSTENTION_FAILURE | false | 0.35 | — | — | General fallback where abstention/clarification expected. |
| I003 | I_AMBIGUOUS | RETRIEVAL_FAILURE | false | 0.35 | `IND-PAT-ACT-1970`, `INT-WIPO-GRATK-2024` | — | Expected docs missed; no citations. |
| I004 | I_AMBIGUOUS | ABSTENTION_FAILURE | false | 0.35 | — | — | Clarification expected, fallback answered. |
| I005 | I_AMBIGUOUS | ABSTENTION_FAILURE | false | 0.35 | — | — | Clarification expected, fallback answered. |
| I006 | I_AMBIGUOUS | ABSTENTION_FAILURE | false | 0.35 | — | — | Clarification expected, fallback answered. |
| I007 | I_AMBIGUOUS | ABSTENTION_FAILURE | false | 0.35 | — | — | Clarification expected, fallback answered. |
| I008 | I_AMBIGUOUS | ABSTENTION_FAILURE | false | 0.35 | — | — | Clarification expected, fallback answered. |
| J002 | J_FALSE_PREMISE | RETRIEVAL_FAILURE | false | 0.35 | `IND-PAT-ACT-1970`, `INT-WIPO-GRATK-2024`, `IND-BD-ACT-2002` | — | Evidence missing; no citations. |
| J008 | J_FALSE_PREMISE | RETRIEVAL_FAILURE | false | 0.9427 | `IND-GI-ACT-1999` | `IND-GI-RULES-2002` | High confidence wrong source. |
| K001-K010 | K_OUT_OF_CORPUS | ABSTENTION_FAILURE | false | 0.35 | — | — | Out-of-corpus questions got fallback answers instead of abstention/unsupported handling. |
| L001 | L_ADVERSARIAL | ABSTENTION_FAILURE | false | 0.8504 | `IND-TM-ACT-1999` | `IND-TM-ACT-1999` | Adversarial instruction was not rejected strongly enough. |
| L003 | L_ADVERSARIAL | ABSTENTION_FAILURE | false | 0.35 | — | — | Unsupported/adversarial prompt got fallback answer. |
| L004 | L_ADVERSARIAL | ABSTENTION_FAILURE | false | 0.35 | — | — | Unsupported/adversarial prompt got fallback answer. |
| L005 | L_ADVERSARIAL | ABSTENTION_FAILURE | false | 0.896 | `IND-PAT-ACT-1970` | `IND-PAT-ACT-1970` | Adversarial framing answered instead of safe handling. |
| L006 | L_ADVERSARIAL | ABSTENTION_FAILURE | false | 0.8866 | `IND-PAT-ACT-1970` | `IND-PAT-ACT-1970` | Adversarial framing answered instead of safe handling. |
| L008 | L_ADVERSARIAL | ABSTENTION_FAILURE | false | 0.35 | — | — | Unsupported/adversarial prompt got fallback answer. |
| L009 | L_ADVERSARIAL | ABSTENTION_FAILURE | false | 0.8596 | — | `IND-TM-RULES-2017` | Prompt should have triggered unsupported handling. |
| L010 | L_ADVERSARIAL | ABSTENTION_FAILURE | false | 0.8704 | — | `IND-PAT-ACT-1970` | Prompt should have triggered unsupported handling. |
| P015 | P_PARAPHRASE | RETRIEVAL_FAILURE | false | 0.35 | `IND-TM-ACT-1999` | — | Paraphrase missed trademark evidence. |
| P016 | P_PARAPHRASE | ABSTENTION_FAILURE | true | 0.18 | `IND-PAT-ACT-1970` | — | False abstention for Section 3(e) paraphrase. |
| P019 | P_PARAPHRASE | ABSTENTION_FAILURE | true | 0.18 | `IND-PAT-ACT-1970` | — | False abstention for Section 3(p) paraphrase. |
| P026 | P_PARAPHRASE | RETRIEVAL_FAILURE | false | 0.8774 | `INT-WIPO-GRATK-2024` | `INT-WIPO-PCT`, `INT-TRIPS-1994`, `INT-WIPO-MADRID` | GRATK paraphrase missed. |
| P027 | P_PARAPHRASE | RETRIEVAL_FAILURE | false | 0.865 | `INT-WIPO-GRATK-2024` | `INT-WIPO-BUDAPEST` | GRATK paraphrase missed. |
| Q002 | Q_TYPOS_NATURAL_LANGUAGE | ABSTENTION_FAILURE | true | 0.18 | `IND-PAT-ACT-1970` | — | Typo/natural-language Section 3(p) query abstained. |
| Q004 | Q_TYPOS_NATURAL_LANGUAGE | RETRIEVAL_FAILURE | false | 0.35 | `IND-BD-ACT-2002`, `IND-BD-RULES-2024` | — | Typo ABS query missed evidence. |
| Q005 | Q_TYPOS_NATURAL_LANGUAGE | RETRIEVAL_FAILURE | false | 0.35 | `IND-TM-ACT-1999`, `IND-TM-RULES-2017` | — | Typo trademark query missed evidence. |
| R001 | R_LANGUAGE_ENGLISH | ABSTENTION_FAILURE | true | 0.18 | `IND-PAT-ACT-1970` | — | English RAG check abstained on expected patent evidence. |

## 5. Retrieval Analysis

Overall retrieval:

- Recall@K: `0.8571`
- MRR: `0.8095`
- Primary retrieval failure count: `18`

Expected-document retrieval by category:

| Category | Doc-expected cases | Hits | Recall@K | MRR |
|---|---:|---:|---:|---:|
| `A_DIRECT_LEGAL` | 15 | 14 | 0.9333 | 0.9000 |
| `B_SECTION_SPECIFIC` | 15 | 9 | 0.6000 | 0.6000 |
| `C_TRADITIONAL_KNOWLEDGE` | 10 | 8 | 0.8000 | 0.7500 |
| `D_SECTION_3E` | 8 | 6 | 0.7500 | 0.7500 |
| `E_ABS_GRATK` | 10 | 9 | 0.9000 | 0.7500 |
| `F_FORMULATION_PRODUCT` | 8 | 4 | 0.5000 | 0.5000 |
| `G_COMPARISON` | 10 | 9 | 0.9000 | 0.9000 |
| `H_MULTI_DOMAIN` | 8 | 7 | 0.8750 | 0.8125 |
| `I_AMBIGUOUS` | 1 | 0 | 0.0000 | 0.0000 |
| `J_FALSE_PREMISE` | 8 | 6 | 0.7500 | 0.7500 |
| `L_ADVERSARIAL` | 5 | 3 | 0.6000 | 0.6000 |
| `P_PARAPHRASE` | 30 | 25 | 0.8333 | 0.8000 |
| `Q_TYPOS_NATURAL_LANGUAGE` | 10 | 7 | 0.7000 | 0.5500 |
| `R_LANGUAGE_ENGLISH` | 2 | 1 | 0.5000 | 0.2500 |

Retrieval-specific observations:

- Section-specific retrieval is weak: `B_SECTION_SPECIFIC` recall is 0.6000.
- Formulation/product questions are weak: `F_FORMULATION_PRODUCT` recall is 0.5000.
- Typo/natural-language robustness is weak: `Q_TYPOS_NATURAL_LANGUAGE` MRR is 0.5500.
- International GRATK questions often retrieve other international IP documents such as TRIPS, PCT, Madrid, or Budapest.
- Biodiversity/TK questions often retrieve `IND-BD-AMEND-2023` and `IND-BD-RULES-2024` instead of the expected `IND-BD-ACT-2002` and/or `INT-WIPO-GRATK-2024`.

## 6. Ranking Analysis

Primary ranking failures: `2`

Cases:

- `G004`: expected balanced `IND-BD-ACT-2002` and `INT-WIPO-GRATK-2024`, but returned `IND-BD-ACT-2002` plus unrelated international IP sources.
- `G010`: expected `IND-TM-ACT-1999` and `IND-TM-RULES-2017`, but returned only `IND-TM-RULES-2017`.

Implementation finding:

- `LegalFeatureReranker._balanced_difference_evidence()` balances by domain, not by expected document type or source family.
- This helps multi-domain comparisons, but it does not ensure Act + Rules or Biodiversity Act + GRATK Treaty coverage when both are necessary.

## 7. Abstention Analysis

Official abstention metrics from `deep_rag_summary.json`:

| Metric | Count |
|---|---:|
| True positives | 2 |
| True negatives | 114 |
| False positives / false abstentions | 12 |
| False negatives / unsafe answers | 34 |
| Accuracy | 0.7160 |
| False abstention rate | 0.0741 |
| Unsafe answer rate | 0.2099 |

Primary abstention failures among failed cases: `37`

Two patterns dominate:

1. **False abstentions on answerable legal questions**
   - Examples: `A003`, `B003`, `B004`, `B008`, `B014`, `D002`, `D003`, `G001`, `P016`, `P019`, `Q002`, `R001`
   - Common symptom: evidence was expected from a known corpus document, but the runtime abstained at confidence `0.18`.

2. **False answers on ambiguous, out-of-corpus, or adversarial questions**
   - Examples: all `K_OUT_OF_CORPUS` failures, most `I_AMBIGUOUS` failures, many `L_ADVERSARIAL` failures.
   - Common symptom: `_should_general_fallback()` permits a non-abstained general fallback whenever `analysis.out_of_scope`, `analysis.speculative_subject`, or `analysis.ambiguous` is true, unless legal identifiers are detected.

Implementation finding:

- In `app/service.py`, `_should_general_fallback()` converts several insufficient-evidence states into `abstained=False` general answers.
- In the deep-test policy, many of those cases expect abstention, clarification, or unsupported handling, not a normal non-abstained answer.
- This policy mismatch explains the full 0/10 failure rate for `K_OUT_OF_CORPUS` and 0/8 for `I_AMBIGUOUS`.

## 8. Answer Quality Analysis

No failed case was classified primarily as `ANSWER_QUALITY_FAILURE`.

The average answer quality was `1.5432 / 2`, but failed answer quality scores were downstream of:

- wrong or missing retrieved documents,
- false abstention,
- unsafe non-abstention/general fallback,
- comparison evidence imbalance.

Generation implementation notes:

- `ExtractiveGroundedGenerator` selects up to three evidence snippets using sentence-level scoring.
- For some broad or comparison prompts, this can produce plausible but incomplete answers if the retrieved/reranked evidence set is incomplete.
- `OpenRouterGroundedGenerator` validates `used_chunk_ids` against supplied evidence, and backend citation mapping remains programmatic.

Citation integrity stayed at `1.0000`, so the failure is not fabricated citation metadata. The larger issue is whether the system chooses the right answer mode and retrieves the right evidence before citation validation.

## 9. Dataset/Chunking Findings

No dataset coverage failure was assigned as the primary category because the expected documents generally exist in the corpus. The failures are more often retrieval, ranking, or policy failures.

However, chunking structure likely contributes to retrieval brittleness:

- `IND-BD-ACT-2002` is very coarse: 22 chunks only, with large multi-section chunks. This can dilute exact Section 3 and Section 6 retrieval.
- `INT-WIPO-GRATK-2024` is a single chunk without article metadata. Article-specific GRATK queries cannot benefit from `article_number` matching.
- Some Acts have many very small chunks. This can fragment context for provisions that need surrounding explanation.
- Exact legal-identifier evidence checks depend on structured fields such as `section`, `rule_number`, and `article_number`; when metadata is absent or too coarse, `abstention_reason()` may reject otherwise relevant text.

Targeted examples from current chunks:

- `IND-PAT-ACT-1970` has Section 3 metadata and multiple Section 3 chunks.
- `IND-TM-ACT-1999` and `IND-CR-ACT-1957` are mostly sectioned, but the exact searched current examples for Section 18, Section 28, and Section 14 did not resolve through direct metadata equality checks.
- `IND-BD-ACT-2002` has large sectioned chunks, but Section 3 and Section 6 exact metadata checks did not surface in the direct probe.
- `INT-WIPO-GRATK-2024` has no Article 3 metadata because it is a single treaty chunk.

These are diagnostic findings only. The dataset and chunker remain frozen.

## 10. Root Cause Ranking

Ranked by observed impact on failed cases:

| Rank | Root cause | Primary failure count | Evidence |
|---:|---|---:|---|
| 1 | Abstention/general-fallback policy mismatch | 37 | Ambiguous/out-of-corpus/adversarial prompts receive non-abstained general fallback; answerable legal prompts sometimes abstain at 0.18. |
| 2 | Retrieval expected-document misses | 18 | Expected document not returned across Section-specific, formulation, biodiversity/TK, GRATK, paraphrase, typo, and multi-domain cases. |
| 3 | Comparison/ranking balance weakness | 2 | Comparison cases retrieve only one required side or wrong international source family. |
| 4 | Dataset chunking/metadata brittleness | Contributing, not primary | Coarse `IND-BD-ACT-2002`, single-chunk/no-article `INT-WIPO-GRATK-2024`, small fragmented Act chunks. |
| 5 | Citation validation | 0 primary | Citation integrity is 1.0000; citations are programmatically mapped from evidence. |

## 11. Representative Failure Examples

### Section-specific false abstention

`B003`: “Explain Section 18 of the Trade Marks Act.”

- Expected: `IND-TM-ACT-1999`
- Actual: abstained
- Confidence: 0.18
- Likely issue: exact provision support/evidence sufficiency path failed before generation.

### Wrong international treaty retrieval

`B015`: “What does Article 3 of the WIPO GRATK Treaty address?”

- Expected: `INT-WIPO-GRATK-2024`
- Actual returned: `INT-TRIPS-1994`, `INT-WIPO-MADRID`, `INT-WIPO-PCT`, `INT-WIPO-BUDAPEST`
- Likely issue: international-domain retrieval expansion is too generic; GRATK title/article signals are not strong enough.

### Biodiversity/TK wrong source family

`C004`: “How are associated traditional knowledge and biological resources treated?”

- Expected: `IND-BD-ACT-2002`, `INT-WIPO-GRATK-2024`
- Actual returned: `IND-BD-AMEND-2023`, `IND-BD-RULES-2024`
- Likely issue: source prioritization favors amendments/rules over base Act and treaty.

### Out-of-corpus unsafe fallback

`K001-K010`

- Expected: abstention or unsupported handling.
- Actual: non-abstained fallback answers at confidence 0.35.
- Likely issue: `_should_general_fallback()` treats out-of-scope/ambiguous/speculative cases as valid fallback instead of abstention for this evaluation policy.

### Comparison evidence imbalance

`G010`: trademark Act vs Rules comparison.

- Expected: `IND-TM-ACT-1999`, `IND-TM-RULES-2017`
- Actual returned: `IND-TM-RULES-2017`
- Likely issue: balancing by domain is insufficient when both documents share the same domain.

## 12. Recommended Repairs

Repair plan only; no fixes implemented.

### A. Must fix

1. Align general-fallback policy with the evaluation contract.
   - For ambiguous, out-of-corpus, speculative, and adversarial questions, return abstention or clarification unless product policy explicitly permits uncited general answers.
   - Ensure unsupported general fallback does not report `abstained=false` when evaluation expects unsupported handling.

2. Strengthen source-targeted retrieval for known document names and treaty names.
   - Add or repair title/document-name detection for “GRATK Treaty,” “Biological Diversity Act,” “Trade Marks Act,” “Copyright Act,” “GI Act,” etc.
   - Use detected document intent as a soft-but-strong retrieval/reranking signal.

3. Repair exact legal provision handling.
   - Diagnose why Section 18/28 Trade Marks, Section 14 Copyright, and Biological Diversity Section 3/6 paths abstain or miss expected docs.
   - Keep citation validation strict.

4. Add comparison evidence balancing by required source family/document type, not only domain.
   - Act + Rules comparisons need both document types.
   - Biodiversity + GRATK comparisons need both source families.

### B. Should fix

1. Improve typo/paraphrase robustness for statutory concepts.
   - Especially Section 3(p), ABS, trademark registration/rights, and GRATK paraphrases.

2. Improve formulation-product routing.
   - Formulation questions need multi-document retrieval across patent, biodiversity, AYUSH/FSSAI, design, and trademark where appropriate.

3. Calibrate confidence against evidence correctness.
   - Several wrong-source retrieval failures still have high confidence, e.g. `B015`, `C004`, `F005`, `J008`.

4. Add test coverage for deep-test failure motifs.
   - Existing pytest passes but does not catch current deep-test weaknesses.

### C. Optional improvement

1. Add per-document source-family diversity in reranking.
2. Add retrieval debug traces to evaluation artifacts: candidate rank, scores, filter decisions, legal identifiers, domain/jurisdiction detection.
3. Add dataset metadata diagnostics for article/section completeness by document.
4. Add an explicit “clarification mode” separate from abstention and general fallback.

## 13. Risks

- Fixing fallback policy may reduce user-friendly general answers unless product policy clearly separates legal/IP mode from general chat mode.
- Increasing document-title boosts may overfit to named-document queries if not balanced against semantic evidence.
- Tightening abstention may improve safety but lower answer rate.
- Improving source-family balancing could reduce top-k precision if too many forced-diversity slots are used.
- Dataset chunking issues are real contributors, but the current instruction freezes dataset and chunker, so repairs must initially be runtime-side.

## 14. Proposed Next Evaluation Gate

Before accepting repairs:

1. Re-run existing pytest:
   - Required: `36 passed`, no new failures.

2. Re-run the 162-question deep evaluation:
   - Required minimum:
     - Pass rate: ≥ 85%
     - Recall@K: ≥ 0.92
     - MRR: ≥ 0.88
     - Citation integrity: 1.0
     - Abstention accuracy: ≥ 0.90
     - Unsafe answer rate: ≤ 0.05

3. Add failure-regression tests for:
   - `K_OUT_OF_CORPUS`
   - `I_AMBIGUOUS`
   - `L_ADVERSARIAL`
   - Section 18/28 Trade Marks Act
   - Section 14 Copyright Act
   - Biological Diversity Act Sections 3 and 6
   - WIPO GRATK Article queries
   - Act + Rules comparison

## Existing pytest result

Command:

```text
python -m pytest
```

Result:

```text
36 passed, 6 warnings in 5.91s
```

Warnings:

- Starlette/httpx deprecation warning from FastAPI TestClient.
- Python 3.14 SWIG-related deprecation warnings.

No tests were modified.

