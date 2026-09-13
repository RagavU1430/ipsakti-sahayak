# Phase 16 final validation report

## Status: FAIL (criteria deliberately unchanged)

The immutable baseline was 25/25 HTTP success, 15/25 grounded, average backend total 1537 ms, median 1485 ms, p95 3131 ms. The baseline failure matrix is in `docs/PHASE_16_FAILURE_MATRIX.md`.

Post-fix live traces showed:

- Q03, Q04, Q06, Q13 and Q18 can now produce evidence-attached responses on the same retrieved chunks (provider-empty/invalid output is recovered deterministically).
- Q07 remains an insufficient-content case because its indexed source is a title/navigation chunk; no treaty disclosure text is present.
- Q08, Q09 and Q23 remain intentional quarantined-source abstentions.
- Q22 still requires corpus/evidence review; its Rules chunks are retrieved but the current provider response remains insufficient.

The full uncached post-fix 25-question backend rerun was not persisted within the execution window, so no “after” score is claimed.

The conversation API benchmark has a complete prior 25-row run showing 25/25 HTTP success, 25/25 request-ID correlation, and 25/25 answers available to the client. Its original grounded flag was invalid because the evaluator compared `answerType` to `RAG_GROUNDED` while the API emits the lowercase enum; this is corrected in the benchmark runner, but a fresh 25-row post-fix rerun was not completed. The visible React benchmark also remains incomplete.

General-question evidence: 10/10 conversation requests returned `GENERAL` and `retrieval_called=false`.

Implemented fixes:

1. Evidence-preserving fallback when the LLM returns empty/invalid chunk IDs or fails citation validation.
2. Preserve selected evidence counts in abstention metrics.
3. Specific document hints for Patents Rules and the 2023 biodiversity amendment; less aggressive high-score Rules intent rejection.
4. React → Spring → RAG `X-Request-ID` propagation with response echoing.
5. Reproducible conversation benchmark and baseline failure-audit scripts.

This report intentionally does not claim Phase 16 PASS. A valid pass still requires the fresh uncached 25-question after run and the missing visible-render/timestamp rows.
