# RAG evaluation

The latest executable run is stored in `dataset/evaluation/results/latest.jsonl`; aggregate metrics are in `summary.json`. Every result includes the question, expected sources/behavior, ranked chunks and component scores, answer, citations, confidence, abstention decision, citation errors, and timings.

## 2026-08-29 measured results

| Metric | 65 end-to-end | 30 adversarial |
|---|---:|---:|
| Recall@8 | 0.9692 | 0.9667 |
| Precision@8 | 0.7618 | 0.6542 |
| MRR | 0.8724 | 0.9083 |
| Citation accuracy | 1.0000 | 1.0000 |
| Citation completeness | 1.0000 | 1.0000 |
| Groundedness validator | 1.0000 | 1.0000 |
| Abstention accuracy | 0.9692 | 0.9667 |
| Median total latency | 65.222 ms | 71.629 ms |

The run used local TF-IDF retrieval, the non-learned deterministic legal-feature reranker, and deterministic extractive generation. It did not exercise Supabase, production embeddings, or OpenRouter generation. Accordingly these numbers establish local executable behavior, not production quality.

The 25 original questions have structured gold expectations in `golden_answers.jsonl`; 40 supplemental questions bring the end-to-end set to 65. The adversarial suite covers nonexistent provisions, prompt/data injection, quarantined sources, fake pages, jurisdiction/domain confusion, secret requests, citation bypass, and scope overclaims.

Citation accuracy means every returned citation maps to retrieved evidence and every provision mentioned in a non-abstaining answer is either the cited chunk's legal anchor or literally present in cited evidence. Abstentions need no citation. Recall counts a hit when at least one expected source appears in the final evidence set; precision is the fraction of final evidence from an expected source; MRR uses the first expected source rank.

The release gate remains failed even though citation, groundedness, and query-count gates pass: the authoritative raw corpus still has two unresolved source warnings and the production backend is unverified.
