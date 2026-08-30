# RAG runtime question test report

Date: 2026-08-30

## 1. Test environment

- Runtime tested: FastAPI app `app.api.main:app`
- Server command: `python -m uvicorn app.api.main:app --host 127.0.0.1 --port 8765 --log-level warning`
- Public endpoint tested: `POST /api/v1/ask`
- Test runner: `scripts/test_rag_questions.py`
- Machine-readable results: `dataset/evaluation/results/runtime_question_test.json`
- Mode: local runtime over the existing canonical corpus. No Supabase credentials or external LLM credentials were used.

## 2. Dataset baseline

The dataset was checked before and after the runtime question test.

| Check | Expected/current |
|---|---:|
| Registered documents | 25 |
| Retrievable documents | 24 |
| Canonical chunks | 6,514 |
| Validation warnings | 2 |

Dataset hashes for canonical documents, canonical chunks, source registry, download manifest, and checksum manifest were identical before and after testing. The test did not modify the dataset corpus.

## 3. Number of questions

- Runtime questions: 55
- Malformed request checks: 4
- Total HTTP requests to `/api/v1/ask`: 59

## 4. Questions by category

| Category | Questions | Passed | Failed |
|---|---:|---:|---:|
| TRADEMARK | 5 | 5 | 0 |
| PATENTS | 5 | 5 | 0 |
| COPYRIGHT | 4 | 4 | 0 |
| DESIGNS | 3 | 0 | 3 |
| GEOGRAPHICAL INDICATIONS | 3 | 2 | 1 |
| PLANT VARIETIES | 3 | 2 | 1 |
| BIODIVERSITY | 4 | 3 | 1 |
| AYURVEDA | 3 | 3 | 0 |
| INTERNATIONAL IP | 6 | 5 | 1 |
| CROSS-DOMAIN | 4 | 4 | 0 |
| NATURAL LANGUAGE | 5 | 1 | 4 |
| ADVERSARIAL | 5 | 4 | 1 |
| OUT-OF-CORPUS | 5 | 5 | 0 |

## 5. API success rate

- HTTP 200 success rate across the 55 scored runtime questions: 100%
- Response schema validity: 100%
- Malformed request checks: 4/4 returned HTTP 422

## 6. Retrieval metrics

- Expected-document hit rate for grounded responses with expected documents: 0.9474
- MRR over returned public source order: 0.8377

Retrieval is functional, but not fully reliable. It missed or under-prioritized expected sources in several cases, and domain detection caused unexpected abstentions for all design questions and four natural-language questions.

## 7. Citation integrity

- Citation integrity rate for grounded responses: 1.0
- No fabricated document IDs were detected.
- No out-of-range citation pages were detected.
- No citations to `IND-FSS-AA-2022` were detected.
- No grounded response was returned without citations.

Citation integrity passes structurally.

## 8. Groundedness

Automated citation integrity passed, but manual review found that several answers were only partially supported or failed to directly answer the question. The extractive generator often returned legally relevant fragments rather than a synthesized answer.

Grounding is therefore not production-ready even though citations are structurally valid.

## 9. Abstention accuracy

- Expected abstention accuracy: 0.875
- Main abstention failure: Q49, teleportation patent question, returned a grounded patent-law answer instead of abstaining.
- Out-of-corpus questions Q51-Q55 all abstained correctly.
- Fabricated section Q47 abstained correctly.

## 10. Confidence behavior

- Confidence validity: 100% of responses had numeric confidence in `[0.0, 1.0]`.
- Abstentions returned low confidence `0.18`.
- However, some poor grounded answers still received high confidence. Example: Q49 returned confidence `0.8333` despite being an unsupported teleportation-specific query.

Confidence range validation passes, but calibration fails.

## 11. Latency statistics

Measured over 55 scored runtime questions:

| Metric | latency_ms |
|---|---:|
| Minimum | 3.489 |
| Maximum | 961.159 |
| Average | 72.768 |
| Median | 64.448 |
| p95 | 121.905 |

No performance target was assumed.

## 12. Representative manual answer review

| ID | Manual label | Notes |
|---|---|---|
| Q1 | PARTIALLY SUPPORTED | Retrieved Trade Marks Act/Rules and cited valid sources, but answer is noisy and not a clean requirements summary. |
| Q3 | UNSUPPORTED | Asked about rights from registration; answer focused on advertisement/application procedure. |
| Q6 | UNSUPPORTED | Asked what a patent is; answer returned novelty/revocation/marking snippets, not a definition. |
| Q8 | PARTIALLY SUPPORTED | Retrieved patent filing provisions, but answer is overly PCT/Form-specific. |
| Q11 | PARTIALLY SUPPORTED | Includes author special rights but does not clearly summarize copyright rights. |
| Q13 | UNSUPPORTED | Asked duration; answer returned complaint/application metadata and did not answer term. |
| Q18 | PARTIALLY SUPPORTED | Retrieved GI sources, but answer mixed registration conditions with definition. |
| Q20 | UNSUPPORTED | Asked GI protection; answer returned fee/renewal/application fragments from rules. |
| Q24 | PARTIALLY SUPPORTED | Retrieved biodiversity rules/amendment; did not use the expected Act source for Act purpose. |
| Q28 | SUPPORTED | Used verified 2025 FSSAI order and Ayush context; did not cite quarantined 2022 source. |
| Q31 | PARTIALLY SUPPORTED | Retrieved TRIPS but answer described Council/review provisions rather than a clear definition. |
| Q36 | UNSUPPORTED | Abstained because citation validation rejected the generated GRATK answer. |
| Q37 | UNSUPPORTED | Cross-domain answer returned fee/opposition snippets, not a patent/trademark comparison. |
| Q41 | UNSUPPORTED | Natural-language patent-intent query unexpectedly abstained as ambiguous. |
| Q46 | PARTIALLY SUPPORTED | Did not directly accept false premise and cited novelty/public-known material, but answer did not clearly correct the premise. |
| Q47 | SUPPORTED | Correctly abstained for fabricated Section 9999. |
| Q49 | UNSUPPORTED | Failed: teleportation-specific patent question received a grounded-looking answer. |
| Q51 | SUPPORTED | Correctly abstained for weather/out-of-corpus question. |

## 13. Failed questions

Failed IDs:

- Q15, Q16, Q17: design questions unexpectedly abstained as ambiguous.
- Q20: GI protection question returned only `IND-GI-RULES-2002`, missing expected Act support and producing fee/renewal fragments.
- Q23: plant-variety rights question unexpectedly abstained as ambiguous.
- Q24: Biological Diversity Act purpose question missed the expected Act source.
- Q36: WIPO GRATK question abstained because citation validation rejected the generated answer.
- Q41-Q44: natural-language patent/logo/GI/song questions unexpectedly abstained as ambiguous.
- Q49: teleportation patent question should have abstained but returned a grounded-looking answer.

## 14. Hallucination cases

No fabricated citations or fabricated document IDs were detected.

The serious hallucination-like behavior is Q49: the system answered a teleportation-specific legal query using generic patent snippets. The answer did not fabricate a citation, but it over-applied retrieved evidence to an unsupported factual premise.

## 15. Citation failures

No public citation metadata integrity failures were detected.

Q36 failed internally because citation validation rejected the generated GRATK answer. This is safe fail-closed behavior, but it caused an unexpected abstention for a supported treaty question.

## 16. Abstention failures

- Q49 failed to abstain where it should have.
- Q15-Q17, Q23, and Q41-Q44 over-abstained because domain detection/query analysis treated answerable questions as ambiguous.

## 17. Quarantined-source test

The Ayurveda Aahara questions did not cite or expose `IND-FSS-AA-2022`. Q28 used `IND-FSS-AA-ORDER-2025` and Ayush sources. The quarantined source safety check passes.

## 18. Overall assessment

The runtime API is functional and citation-safe, but answer quality and domain routing need repair before production use.

Final classification:

**C. RAG REQUIRES FIXES**

## 19. Dimension pass/fail

| Dimension | Result |
|---|---|
| API FUNCTIONALITY | PASS |
| RETRIEVAL | FAIL |
| GROUNDING | FAIL |
| CITATIONS | PASS |
| ABSTENTION | FAIL |
| CONFIDENCE | FAIL |
| SECURITY / SOURCE INTEGRITY | PASS |
| RESPONSE CONTRACT | PASS |
| PERFORMANCE | MEASURED |

## 20. Production blockers

1. Domain detection misses generic design, logo, song, and invented-product questions.
2. Extractive generation produces noisy fragments instead of direct answers.
3. Confidence is over-optimistic for some weak/poorly grounded answers.
4. Evidence sufficiency does not reject unsupported factual premises such as teleportation-specific patent law.
5. Some expected source prioritization is weak, especially GI protection and biodiversity-purpose questions.

## 21. Recommended fixes

Do not treat these as implemented in this test run.

1. Improve query analysis vocabulary for natural-language intent: design, logo, song, invention, regional product, plant variety.
2. Add claim-question alignment checks before generation so retrieved chunks must answer the actual user question, not merely share domain terms.
3. Improve sufficiency checks for false-premise and speculative technology questions.
4. Calibrate confidence downward when answers are extractive fragments, when expected sections are absent, or when source diversity is weak.
5. Improve extractive answer synthesis or enable the grounded JSON LLM path after production credentials are configured and evaluated.
