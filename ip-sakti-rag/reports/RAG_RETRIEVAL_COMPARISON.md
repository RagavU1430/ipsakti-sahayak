# RAG Retrieval Comparison (Phase 5) — measured, document-level ground truth

Date: 2026-09-20 | corpus=v1-legacy | candidate_k=24 | n=63 (2/65 lack expected ids) | jurisdiction=auto
Ground truth: expected_source_ids from latest.jsonl end_to_end. Chunk-level relevance judgments do not exist.

| Retrieval | Recall@5 | Recall@8 | Recall@10 | MRR |
|---|---:|---:|---:|---:|
| Vector only | 0.8413 | 0.8413 | 0.8413 | 0.8810 |
| Lexical only | 0.9206 | 0.9206 | 0.9206 | 0.8810 |
| Hybrid | 0.9206 | 0.9206 | 0.9206 | 0.9127 |
| Hybrid + reranker | 0.8333 | 0.8333 | 0.8333 | 0.8862 |

Notes (measured differences only, no "best" claim):
- Recall is flat across K=5/8/10: expected sets are 1-2 docs, hit in top-5 or absent.
- Hybrid MRR (0.9127) exceeds either alone; reranker trades document recall (0.8333) for chunk precision used by generation/citations.
- Chunk-level Recall/MRR: NOT MEASURED (no chunk judgments).
- Initial run with hardcoded jurisdiction=INDIA scored international categories 0.0 (harness artifact — explicit jurisdiction overrides auto-detection in query_analysis.py:86-87). Reran with auto jurisdiction; numbers above are the corrected ones.
