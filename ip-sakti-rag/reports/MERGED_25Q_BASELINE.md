# Merged 25Q Baseline (Phase 2)

Date: 2026-09-20 | corpus=v1-legacy | questions unchanged (phase16_rag_questions.json)

`{"n": 25, "rag_used": 13, "grounded": 13, "abstained": 12, "avg_retrieval_ms": 113.91, "avg_total_ms": 2064.75, "corpus": "v1-legacy", "generator_note": "mixed extractive/live-Gemini per intent; see per-row generator"}`

| ID | RAG_USED | ev | status | retr_ms | gen_ms | total_ms | grounded | abst | generator |
|---|---|---|---|---|---|---|---|---|---|
| Q01 | True | 8 | SUFFICIENT | 209.533 | 1740.781 | 1964.109 | True | False | gemini-grounded-json-v1:gemini-3.5-flash-lite |
| Q02 | False | 8 | None | 139.397 | 1484.993 | 1632.734 | False | True | None |
| Q03 | True | 8 | SUFFICIENT | 153.079 | 1680.551 | 1852.093 | True | False | deterministic-extractive-v1 |
| Q04 | True | 4 | SUFFICIENT | 65.956 | 1444.329 | 1530.927 | True | False | deterministic-extractive-v1 |
| Q05 | True | 8 | SUFFICIENT | 170.594 | 1527.541 | 1708.354 | True | False | gemini-grounded-json-v1:gemini-3.5-flash-lite |
| Q06 | True | 3 | SUFFICIENT | 80.235 | 2396.065 | 2487.89 | True | False | gemini-grounded-json-v1:gemini-3.5-flash-lite |
| Q07 | True | 1 | PARTIAL | 192.846 | 1221.011 | 1431.304 | True | False | deterministic-extractive-v1 |
| Q08 | False | 0 | None | 0.0 | 0.0 | 0.453 | False | True | None |
| Q09 | False | 0 | None | 0.0 | 0.0 | 0.53 | False | True | None |
| Q10 | True | 8 | SUFFICIENT | 149.534 | 1583.578 | 1740.9 | True | False | gemini-grounded-json-v1:gemini-3.5-flash-lite |
| Q11 | False | 8 | None | 98.074 | 1525.719 | 1636.427 | False | True | None |
| Q12 | False | 8 | None | 217.764 | 1614.206 | 1841.63 | False | True | None |
| Q13 | True | 8 | SUFFICIENT | 110.448 | 0.662 | 120.246 | True | False | deterministic-extractive-v1 |
| Q14 | True | 8 | SUFFICIENT | 140.376 | 1881.611 | 2029.593 | True | False | gemini-grounded-json-v1:gemini-3.5-flash-lite |
| Q15 | False | 8 | None | 159.663 | 1777.251 | 1945.718 | False | True | None |
| Q16 | False | 8 | None | 135.288 | 1906.637 | 2047.699 | False | True | None |
| Q17 | True | 8 | SUFFICIENT | 157.324 | 2389.799 | 2559.73 | True | False | gemini-grounded-json-v1:gemini-3.5-flash-lite |
| Q18 | True | 8 | SUFFICIENT | 100.285 | 1736.97 | 1845.22 | True | False | gemini-grounded-json-v1:gemini-3.5-flash-lite |
| Q19 | True | 8 | SUFFICIENT | 115.78 | 1512.007 | 1635.439 | True | False | gemini-grounded-json-v1:gemini-3.5-flash-lite |
| Q20 | False | 8 | None | 48.643 | 3938.614 | 3994.239 | False | True | None |
| Q21 | True | 8 | SUFFICIENT | 50.406 | 0.234 | 56.374 | True | False | deterministic-extractive-v1 |
| Q22 | False | 8 | None | 108.474 | 4944.651 | 5064.713 | False | True | None |
| Q23 | False | 0 | None | 0.0 | 0.0 | 0.413 | False | True | None |
| Q24 | False | 8 | None | 97.082 | 4706.008 | 4810.099 | False | True | None |
| Q25 | False | 8 | None | 146.957 | 7526.989 | 7681.907 | False | True | None |