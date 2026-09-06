# LIVE RAG PARITY DEBUG REPORT

## 1. Executive Summary

Live browser/API parity was failing for basic IP questions because the live RAG path was not reliably surfacing definition chunks for broad definition prompts. The canonical dataset was intact and contained the required Patent and GI definition evidence, but retrieval/reranking favored adjacent rights, term, and operational provisions. After a narrow retrieval/reranking repair, live `POST /api/v1/questions` and the browser UI return cited, non-abstained answers for Patent, GI, Trademark, ABS, IP-rules, IP-law, and Section 3(p).

Final verdict: PASS WITH WARNING - core live parity is restored for Patent/GI and major IP queries, but broad `traditional knowledge` and `TKDL` prompts still abstain and need a separate evidence/query-policy review.

## 2. Original Problem

- `What is a patent?` returned an abstention in the live application.
- `What is a GI?` returned an abstention in the live application.
- Previous automated validation expected non-abstained RAG answers with authoritative citations.

## 3. Live Environment

- Backend live port: `8080`, PID observed by `netstat`: `5004`.
- RAG live ports before cleanup: `8000` PID `26700`, stale duplicate `8765` PID `18956`.
- RAG live port after cleanup: `8000` only.
- Frontend browser URL verified: `http://localhost:8080/ask`.
- Backend health: `/health` returned `{"status":"ok"}`.
- Backend readiness: `/health/ready` returned `rag=UP`.
- RAG health: `/health` returned `{"status":"ok"}`.

## 4. Process / Service Audit

Before fix, direct RAG comparison showed duplicate runtime behavior:

| Query | RAG 8765 | RAG 8000 |
| --- | --- | --- |
| What is a patent? | abstained | sometimes grounded without explicit domain, failed with explicit domain |
| What is a GI? | abstained | inconsistent; failed with explicit domain |
| What is Section 3(p)? | grounded | grounded |

The stale `8765` process was stopped. The canonical live RAG process was restarted on `8000`.

## 5. Dataset Integrity

- Required hash: `827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d`
- Before audit hash: `827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d`
- After fix/build hash: `827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d`
- Result: PASS

The dataset was not modified.

## 6. Live Query Trace

After fix via backend `POST /api/v1/questions`:

| Query | Route | Domain | Citations | Confidence | Abstained | Result |
| --- | --- | --- | ---: | ---: | --- | --- |
| What is a patent? | DOMAIN_RAG | PATENT | 4 | 0.9969 | false | PASS |
| What is a GI? | DOMAIN_RAG | GEOGRAPHICAL_INDICATION | 1 | 0.8722 | false | PASS |
| What is a trademark? | DOMAIN_RAG | TRADEMARK | 2 | 0.8803 | false | PASS |
| What is traditional knowledge? | DOMAIN_RAG | TRADITIONAL_KNOWLEDGE | 0 | 0.18 | true | FAIL |
| What is TKDL? | DOMAIN_RAG | TRADITIONAL_KNOWLEDGE | 0 | 0.18 | true | FAIL |
| What is ABS? | DOMAIN_RAG | ABS | 3 | 0.9221 | false | PASS |
| What are IP rules in India? | DOMAIN_RAG | INDIA_IP_LAW | 6 | 0.8829 | false | PASS |
| Tell me about intellectual property law in India. | DOMAIN_RAG | IP | 8 | 0.8354 | false | PASS |
| What is Section 3(p)? | DOMAIN_RAG | INDIA_IP_LAW | 1 | 0.6489 | false | PASS |
| What is Section 377? | DOMAIN_RAG | INDIA_IP_LAW | 0 | 0.18 | true | PASS |
| Hi | GENERAL | none | 0 | n/a | false | PASS |
| What is Python? | GENERAL | none | 0 | n/a | false | PASS |
| What is 2 + 2? | GENERAL | none | 0 | n/a | false | PASS |

## 7. Patent Trace

- Corpus evidence exists: `IND-PAT-ACT-1970-0173-7d40f53993ce` contains `"patent" means a patent for any invention granted under this Act`.
- Failure layer before fix: Retrieval/reranking.
- Cause: definition query expansion included rights/duration terms such as `term twenty years`, pushing term/patent-of-addition chunks above the statutory definition.
- After fix: live backend returns `IND-PAT-ACT-1970` citations including Section `6(la)`.

## 8. GI Trace

- Corpus evidence exists: `IND-GI-ACT-1999-0099-422b6c638e92` contains the Section 2(1)(e) geographical indication definition.
- Failure layer before fix: Retrieval/reranking, with downstream generation/citation rejection when non-definition evidence was assembled.
- After fix: live backend and browser return Section `2(1)` citation from `IND-GI-ACT-1999`.

## 9. Trademark Trace

- Corpus evidence exists: `IND-TM-ACT-1999`.
- After fix: live backend returns non-abstained answer with citations from `IND-TM-ACT-1999` and `IND-TM-RULES-2017`.

## 10. TK Trace

- Live backend still abstains for `What is traditional knowledge?`.
- Primary layer: Retrieval/query-policy coverage for broad TK definition.
- Current behavior is safe because it abstains rather than fabricating an unsupported definition.

## 11. ABS Trace

- Live backend returns non-abstained answer with evidence from Biological Diversity materials.
- Sources observed: `IND-BD-AMEND-2023`, `IND-BD-RULES-2024`.

## 12. Automated vs Live Comparison

The automated deterministic RAG path passed after the fix:

- RAG targeted subset: `32 passed, 1 warning`
- Full RAG pytest: `76 passed, 30 skipped, 6 warnings`

The live path now agrees with the expected Patent and GI non-abstention behavior through backend and browser.

## 13. Exact Failure Layer

Primary failure layer: G. Retrieval / I. Reranking.

Secondary runtime risk: P. Stale process / O. Environment mismatch, because two RAG services were live (`8000` and `8765`) with different behavior.

## 14. Root Cause

Broad definition queries for Patent and GI used expansions and reranking signals that favored adjacent legal provisions over the authoritative definition chunks already present in the frozen dataset.

## 15. Fix Applied

Minimal RAG-only fix:

- `ip-sakti-rag/app/retrieval/query_analysis.py`: separated definition expansions from rights/duration expansions for Patent, GI, Trademark, ABS, and TKDL-related wording.
- `ip-sakti-rag/app/retrieval/reranker.py`: added a definition relevance signal for chunks containing the requested legal term plus definitional language.
- `ip-sakti-rag/tests/test_retrieval.py`: added regression assertions that Patent and GI definition queries surface authoritative definition chunks in top evidence.

No dataset, chunking, validator, backend routing, voice architecture, or frontend design changes were made for the fix.

## 16. Regression Results

- RAG targeted tests: PASS, `32 passed, 1 warning`.
- RAG full tests: PASS, `76 passed, 30 skipped, 6 warnings`.
- Frontend tests: PASS, `27 passed`.
- Frontend lint/typecheck: PASS.
- Frontend build: PASS.
- Backend tests: NOT COMPLETED. Initial wrapper run failed because Maven tried `C:\.m2\repository`; rerun with installed Maven exceeded the 20-minute timeout.

## 17. Browser Verification

Browser-visible application at `http://localhost:8080/ask` was tested after the fix.

- `What is a patent?`: PASS, visible answer cites The Patents Act, 1970.
- `What is a GI?`: PASS, visible answer cites The Geographical Indications of Goods (Registration and Protection) Act, 1999.
- `What is Section 3(p)?`: PASS, visible answer cites The Patents Act, 1970.
- `Hi`: PASS, routed as general chat with no fake RAG evidence.

## 18. Remaining Risks

- `What is traditional knowledge?` and `What is TKDL?` still abstain in the live backend path.
- Backend full regression did not finish within the allowed command timeout.
- The live RAG path uses external model behavior when enabled, so deterministic evaluation and live generation may still differ on some broad questions.

## 19. Recommendation

Do not start Agentic RAG yet. First add a focused TK/TKDL evidence-policy repair or document that these broad definitions are intentionally abstained unless the corpus contains direct authoritative definition evidence. Then rerun backend regression in a stable Maven environment.

## 20. Final Verdict

PASS WITH WARNING - core live RAG parity for Patent and GI is verified through direct RAG, Spring Boot, and browser UI, but TK/TKDL parity and backend full regression remain incomplete.
