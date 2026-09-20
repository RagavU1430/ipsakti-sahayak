# Merged 55Q Results (Phase 4)

Date: 2026-09-20 | corpus=v1-legacy | jurisdiction=auto | set: latest.jsonl end_to_end (65), unmodified

Summary: {"n": 65, "rag_used": 45, "grounded": 45, "abstained": 20, "avg_retrieval_ms": 145.45, "avg_total_ms": 1595.19, "corpus": "v1-legacy"}

Pre-merge comparison: stored latest.jsonl artifact used different config/jurisdiction handling; direct grounded-rate comparison is NOT manufactured here.

| ID | RAG_USED | ev | status | retr | gen | total | grounded | abst | generator |
|---|---|---|---|---|---|---|---|---|---|
| EVAL-001 | True | 8 | SUFFICIENT | 253.063 | 1787.943 | 2054.638 | True | False | gemini-grounded-json-v1:gemini-3.5-flash-lite |
| EVAL-002 | False | 8 | None | 161.918 | 1228.322 | 1397.901 | False | True | None |
| EVAL-003 | True | 8 | SUFFICIENT | 169.661 | 1935.23 | 2127.992 | True | False | deterministic-extractive-v1 |
| EVAL-004 | True | 8 | SUFFICIENT | 177.291 | 1682.692 | 1869.868 | True | False | gemini-grounded-json-v1:gemini-3.5-flash-lite |
| EVAL-005 | True | 8 | SUFFICIENT | 162.652 | 1456.505 | 1632.518 | True | False | deterministic-extractive-v1 |
| EVAL-006 | True | 8 | SUFFICIENT | 132.455 | 0.826 | 146.374 | True | False | deterministic-extractive-v1 |
| EVAL-007 | False | 8 | None | 107.331 | 1698.116 | 1817.828 | False | True | None |
| EVAL-008 | True | 8 | SUFFICIENT | 118.945 | 1485.08 | 1618.188 | True | False | gemini-grounded-json-v1:gemini-3.5-flash-lite |
| EVAL-009 | True | 8 | SUFFICIENT | 108.85 | 1668.603 | 1788.734 | True | False | gemini-grounded-json-v1:gemini-3.5-flash-lite |
| EVAL-010 | True | 8 | SUFFICIENT | 50.168 | 0.3 | 56.204 | True | False | deterministic-extractive-v1 |
| EVAL-011 | False | 8 | None | 56.995 | 1470.26 | 1534.991 | False | True | None |
| EVAL-012 | True | 8 | SUFFICIENT | 133.106 | 1597.102 | 1737.941 | True | False | gemini-grounded-json-v1:gemini-3.5-flash-lite |
| EVAL-013 | False | 8 | None | 137.028 | 1694.368 | 1842.423 | False | True | None |
| EVAL-014 | True | 3 | SUFFICIENT | 84.676 | 2260.591 | 2358.005 | True | False | gemini-grounded-json-v1:gemini-3.5-flash-lite |
| EVAL-015 | True | 8 | SUFFICIENT | 120.864 | 2034.991 | 2163.978 | True | False | gemini-grounded-json-v1:gemini-3.5-flash-lite |
| EVAL-016 | False | 8 | None | 98.979 | 1611.69 | 1724.817 | False | True | None |
| EVAL-017 | False | 0 | None | 0.0 | 0.0 | 0.505 | False | True | None |
| EVAL-018 | False | 0 | None | 0.0 | 0.0 | 0.36 | False | True | None |
| EVAL-019 | True | 4 | SUFFICIENT | 77.829 | 1547.748 | 1643.358 | True | False | deterministic-extractive-v1 |
| EVAL-020 | True | 8 | SUFFICIENT | 752.506 | 1584.628 | 2347.301 | True | False | gemini-grounded-json-v1:gemini-3.5-flash-lite |
| EVAL-021 | False | 8 | None | 138.361 | 1903.636 | 2048.191 | False | True | None |
| EVAL-022 | True | 8 | SUFFICIENT | 322.127 | 1508.99 | 1842.618 | True | False | gemini-grounded-json-v1:gemini-3.5-flash-lite |
| EVAL-023 | True | 8 | SUFFICIENT | 132.969 | 1801.61 | 1946.104 | True | False | gemini-grounded-json-v1:gemini-3.5-flash-lite |
| EVAL-024 | True | 8 | SUFFICIENT | 249.927 | 1376.684 | 1637.3 | True | False | gemini-grounded-json-v1:gemini-3.5-flash-lite |
| EVAL-025 | False | 8 | None | 377.441 | 1763.931 | 2156.382 | False | True | None |
| EVAL-026 | True | 1 | PARTIAL | 396.224 | 0.292 | 413.05 | True | False | deterministic-extractive-v1 |
| EVAL-027 | True | 8 | SUFFICIENT | 132.735 | 1943.76 | 2085.271 | True | False | gemini-grounded-json-v1:gemini-3.5-flash-lite |
| EVAL-028 | True | 8 | SUFFICIENT | 164.419 | 1486.263 | 1661.876 | True | False | deterministic-extractive-v1 |
| EVAL-029 | True | 8 | SUFFICIENT | 152.369 | 0.591 | 164.068 | True | False | deterministic-extractive-v1 |
| EVAL-030 | False | 8 | None | 162.372 | 1437.859 | 1609.393 | False | True | None |
| EVAL-031 | True | 8 | SUFFICIENT | 152.323 | 0.283 | 159.318 | True | False | deterministic-extractive-v1 |
| EVAL-032 | True | 8 | SUFFICIENT | 146.759 | 1320.836 | 1476.544 | True | False | gemini-grounded-json-v1:gemini-3.5-flash-lite |
| EVAL-033 | False | 8 | None | 180.07 | 1859.821 | 2047.133 | False | True | None |
| EVAL-034 | False | 8 | None | 148.902 | 1789.712 | 1945.303 | False | True | None |
| EVAL-035 | True | 8 | SUFFICIENT | 133.314 | 1.283 | 141.106 | True | False | deterministic-extractive-v1 |
| EVAL-036 | True | 8 | SUFFICIENT | 97.133 | 0.569 | 103.969 | True | False | deterministic-extractive-v1 |
| EVAL-037 | True | 8 | SUFFICIENT | 96.336 | 1802.754 | 1907.159 | True | False | deterministic-extractive-v1 |
| EVAL-038 | True | 8 | SUFFICIENT | 102.146 | 1630.18 | 1738.631 | True | False | gemini-grounded-json-v1:gemini-3.5-flash-lite |
| EVAL-039 | True | 8 | SUFFICIENT | 86.23 | 1236.215 | 1333.729 | True | False | deterministic-extractive-v1 |
| EVAL-040 | False | 8 | None | 86.529 | 1440.139 | 1534.9 | False | True | None |
| EVAL-041 | True | 8 | SUFFICIENT | 120.154 | 0.422 | 127.931 | True | False | deterministic-extractive-v1 |
| EVAL-042 | True | 8 | SUFFICIENT | 97.536 | 2.153 | 110.817 | True | False | deterministic-extractive-v1 |
| EVAL-043 | True | 8 | SUFFICIENT | 110.538 | 1531.424 | 1649.51 | True | False | gemini-grounded-json-v1:gemini-3.5-flash-lite |
| EVAL-044 | False | 8 | None | 113.377 | 2584.996 | 2706.44 | False | True | None |
| EVAL-045 | False | 8 | None | 107.707 | 2341.018 | 2457.684 | False | True | None |
| EVAL-046 | True | 8 | SUFFICIENT | 59.717 | 0.363 | 67.886 | True | False | deterministic-extractive-v1 |
| EVAL-047 | True | 8 | SUFFICIENT | 43.823 | 2097.088 | 2147.812 | True | False | gemini-grounded-json-v1:gemini-3.1-flash-lite |
| EVAL-048 | False | 8 | None | 43.881 | 2969.853 | 3018.672 | False | True | None |
| EVAL-049 | False | 8 | None | 60.397 | 2901.204 | 2967.11 | False | True | None |
| EVAL-050 | True | 8 | SUFFICIENT | 127.216 | 2.894 | 139.156 | True | False | deterministic-extractive-v1 |
| EVAL-051 | True | 8 | SUFFICIENT | 114.279 | 0.536 | 123.361 | True | False | deterministic-extractive-v1 |
| EVAL-052 | True | 8 | SUFFICIENT | 109.351 | 2629.327 | 2745.903 | True | False | gemini-grounded-json-v1:gemini-3.1-flash-lite |
| EVAL-053 | False | 8 | None | 153.125 | 2550.002 | 2715.814 | False | True | None |
| EVAL-054 | False | 8 | None | 115.575 | 2622.102 | 2746.464 | False | True | None |
| EVAL-055 | True | 8 | SUFFICIENT | 102.514 | 2758.454 | 2870.128 | True | False | gemini-grounded-json-v1:gemini-3.1-flash-lite |
| EVAL-056 | True | 5 | SUFFICIENT | 115.117 | 2893.932 | 3020.134 | True | False | gemini-grounded-json-v1:gemini-3.1-flash-lite |
| EVAL-057 | True | 5 | SUFFICIENT | 99.678 | 2801.172 | 2912.401 | True | False | gemini-grounded-json-v1:gemini-3.1-flash-lite |
| EVAL-058 | True | 8 | SUFFICIENT | 184.821 | 1531.651 | 1726.096 | True | False | gemini-grounded-json-v1:gemini-3.5-flash-lite |
| EVAL-059 | False | 8 | SUFFICIENT | 204.65 | 0.0 | 219.277 | False | True | None |
| EVAL-060 | True | 8 | SUFFICIENT | 68.877 | 2107.267 | 2180.975 | True | False | gemini-grounded-json-v1:gemini-3.5-flash-lite |
| EVAL-061 | True | 4 | SUFFICIENT | 74.967 | 2129.885 | 2212.475 | True | False | gemini-grounded-json-v1:gemini-3.5-flash-lite |
| EVAL-062 | True | 6 | SUFFICIENT | 79.825 | 1177.2 | 1275.514 | True | False | deterministic-extractive-v1 |
| EVAL-063 | True | 8 | SUFFICIENT | 247.442 | 1376.152 | 1633.403 | True | False | deterministic-extractive-v1 |
| EVAL-064 | True | 8 | SUFFICIENT | 264.039 | 2080.535 | 2352.456 | True | False | gemini-grounded-json-v1:gemini-3.5-flash-lite |
| EVAL-065 | True | 8 | SUFFICIENT | 272.428 | 1459.16 | 1743.861 | True | False | deterministic-extractive-v1 |