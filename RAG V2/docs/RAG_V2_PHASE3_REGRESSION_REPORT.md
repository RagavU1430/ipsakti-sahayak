# RAG V2 Phase 3 Regression Report

Run date: 2026-09-14  
Frozen input: `ip-sakti-rag/dataset/evaluation/phase16_rag_questions.json` (unchanged)  
Persisted output: `dataset/evaluation/results/rag_v2_phase3_after_web_sources.json`

## Change made

`IND-BD-ACT-2002` was already an official India Code source but its converted HTML included portal navigation and produced only three giant chunks. The V2 builder now removes that portal prefix and splits the numbered Act provisions. The rebuilt index has 1,961 chunks, including 39 Biodiversity Act chunks. No legal text was inferred or added.

## Before and after

| Metric | Phase 2 | Phase 3 | Change |
|---|---:|---:|---:|
| Indexed document IDs | 23 | 22 | -1 invalid FSSAI placeholder excluded |
| Chunks | 1,926 | 1,961 | +35 |
| RAG usage | 25/25 | 25/25 | 0 |
| Grounded answers | 20/25 | 22/25 | +2 |
| Correct abstentions | 0/25* | 0/25 | 0 |
| Incorrect abstentions | 0/25 | 0/25 | 0 |
| Citation correctness | 22/25 | 22/25 | 0 |
| Average retrieval latency | 51.99 ms | 47.67 ms | -4.32 ms |
| Average total latency | 69.44 ms | 63.06 ms | -6.38 ms |

*The stated Phase 2 narrative says five correct abstentions, but the persisted Phase 2 JSON records `abstained: 0` and labels Q06/Q08/Q09/Q18/Q23 as partial/ungrounded. This report uses the persisted JSON as the comparable machine-readable baseline.

Generation is `deterministic-extractive-v1`, not a production LLM call. The reported 4.34 ms is local extractive generation only; it must not be interpreted as live-LLM latency. The benchmark measures retrieval, reranking, and local extraction. It does not separately instrument context preparation or citation validation, so those costs are included in total only indirectly.

## Question-level comparison

| ID | Phase 2 | Phase 3 | Result / diagnosis |
|---|---|---|---|
| Q01 | grounded | grounded | preserved |
| Q02 | grounded | grounded | preserved |
| Q03 | grounded | grounded | preserved |
| Q04 | grounded | grounded | preserved |
| Q05 | grounded | grounded | preserved |
| Q06 | partial | grounded | repaired Act sections 3 and 4 retrieve for approval/intimation |
| Q07 | grounded | grounded | preserved |
| Q08 | partial | partial | exact 2022 regulation remains unavailable; 2025 order is insufficient |
| Q09 | partial | partial | exact 2022 regulation remains unavailable |
| Q10 | grounded | grounded | preserved |
| Q11 | grounded | grounded | preserved |
| Q12 | grounded | grounded | preserved |
| Q13 | grounded | grounded | preserved |
| Q14 | grounded | grounded | preserved |
| Q15 | grounded | grounded | preserved |
| Q16 | grounded | grounded | preserved |
| Q17 | grounded | grounded | preserved |
| Q18 | partial | grounded | amendment Act retrieved with clean companion Act chunks |
| Q19 | grounded | grounded | preserved |
| Q20 | grounded | grounded | preserved |
| Q21 | grounded | grounded | preserved |
| Q22 | grounded | grounded | preserved |
| Q23 | partial | partial | same evidence gap as Q08 |
| Q24 | grounded | grounded | preserved |
| Q25 | grounded | grounded | preserved |

Regression failures: **0** previously grounded questions degraded. Outstanding safety defect: Q08/Q09/Q23 are partial rather than explicit abstentions when only the later 2025 order is retrieved. They must not be represented to users as grounded answers until IND-FSS-AA-2022 is validly acquired.
