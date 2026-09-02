# RAG Dataset A/B Comparison

Status: **BLOCKED — INSUFFICIENT_EVIDENCE**

The controlled 162-question A/B evaluation could not proceed because the exact historical `chunks.jsonl` bytes were not recoverable from existing local repository artifacts, Git history, backup-like local copies, or generated artifacts under the workspace.

Per the master prompt, this triggers the Step 1 STOP condition:

> If the exact bytes cannot be recovered, stop and report that A/B evaluation cannot proceed.

No canonical dataset files, RAG code, chunker code, retrieval logic, evaluation logic, or tests were modified.

## 1. Executive summary

The current canonical dataset was verified locally:

- Hash: `827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d`
- Chunk count: `7,019`

The historical known-good dataset bytes were not found locally.

There is an important hash discrepancy in the prompt history:

- Latest prompt old hash: `4ce211289e88958c89d4bafc4ede7271cc1f18b73acbe9ea30131bda`
- Previously documented old hash: `4ce211289e88958c89d4bafc4ede7271cc387c55cc1f18b73acbe9ea30131bda`

The latest prompt hash is shorter than a normal SHA-256 hex digest. The previously documented hash is a valid 64-character SHA-256 string. Both were searched. Neither corresponding file was found locally.

Because the exact OLD file is missing, the requested A/B runs were not executed. Any attempt to substitute the local Git `HEAD` copy or `.audit_tmp/ab_eval/old/chunks.jsonl` would violate the exact-bytes rule because that file hashes to `61c6c52c7242ec2375bd4e9701b56933cf7ed1a7daba439db994823ce2bf94dd`, not either historical hash.

Final recommendation: **INSUFFICIENT_EVIDENCE**

## 2. Exact old hash

Latest prompt value:

```text
4ce211289e88958c89d4bafc4ede7271cc1f18b73acbe9ea30131bda
```

Status: **NOT RECOVERED**

Previously documented value:

```text
4ce211289e88958c89d4bafc4ede7271cc387c55cc1f18b73acbe9ea30131bda
```

Status: **DOCUMENTED BUT NOT RECOVERED AS BYTES**

Documentation/artifacts containing the previously documented hash include:

- `ip-sakti-rag/docs/RAG_DEEP_TEST_REPORT.md`
- `ip-sakti-rag/docs/RAG_QUALITY_REPAIR_REPORT.md`
- `ip-sakti-rag/dataset/evaluation/results/runtime_question_test.json`
- `ip-sakti-rag/dataset/evaluation/deep_rag/deep_rag_summary.json`
- `ip-sakti-rag/dataset/evaluation/deep_rag/deep_rag_results.json`
- `docs/DATASET_DRIFT_INVESTIGATION_REPORT.md`
- `docs/SUPABASE_PRODUCTION_VERIFICATION_REPORT.md`

## 3. Exact new hash

```text
827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d
```

Status: **VERIFIED**

Verified local files:

- `ip-sakti-rag/dataset/canonical/chunks.jsonl`
- `ip-sakti-rag/.audit_tmp/ab_eval/new/chunks.jsonl`

## 4. Old chunk count

Historical reported value: `6,514`

Verification status: **UNVERIFIED AGAINST EXACT OLD BYTES**

A local 6,514-chunk file exists at:

```text
ip-sakti-rag/.audit_tmp/ab_eval/old/chunks.jsonl
```

But its SHA-256 is:

```text
61c6c52c7242ec2375bd4e9701b56933cf7ed1a7daba439db994823ce2bf94dd
```

It is therefore not the exact historical known-good dataset and cannot be used for the requested A/B evaluation.

## 5. New chunk count

Current verified chunk count: `7,019`

Verified from:

```text
ip-sakti-rag/dataset/canonical/chunks.jsonl
```

## 6. Evaluation methodology

Intended methodology:

1. Recover exact OLD `chunks.jsonl`.
2. Create isolated OLD and NEW evaluation copies outside `dataset/canonical`.
3. Verify OLD and NEW hashes exactly.
4. Run the same 162-question evaluation against:
   - OLD dataset + current RAG code
   - NEW dataset + current RAG code
5. Compare metrics and failures question-by-question.

Actual methodology completed:

1. Searched existing local repository artifacts and backup-like local copies for `chunks.jsonl` candidates.
2. Computed SHA-256 hashes and line counts for all discovered chunk candidates.
3. Checked Git history for tracked versions of `ip-sakti-rag/dataset/canonical/chunks.jsonl`.
4. Searched documentation/evaluation artifacts for the historical hash.
5. Stopped before evaluation because the exact OLD dataset bytes were not found.

## 7. Complete metric comparison

Not available. The A/B evaluation was not run because the exact OLD dataset was not recovered.

Reference-only metrics supplied by the prompt:

| Dataset state | Chunks | Tests | Passed | Failed | Recall@K | MRR | Citation integrity | Abstention accuracy | Answer quality |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Historical reported | 6,514 | 55 | 55 | 0 | not supplied | 0.9735 | 1.0 | 1.0 | not supplied |
| Current reported | 7,019 | 162 | 105 | 57 | 0.8571 | 0.8095 | 1.0 | 0.7160 | 1.5432 / 2 |

These are not controlled A/B results because they were produced with different evaluation sets and possibly different code/dataset states.

## 8. Pass-rate comparison

Not available from controlled A/B.

The historical `55/55` and current `105/162` figures are not directly comparable as A/B results.

## 9. Retrieval comparison

Not available from controlled A/B.

## 10. Abstention comparison

Not available from controlled A/B.

## 11. Answer-quality comparison

Not available from controlled A/B.

## 12. Failure-by-failure analysis

Not available from controlled A/B because neither A nor B was executed in this task.

The existing current deep-test failures remain useful as a current-state diagnostic, but they cannot isolate old-vs-new dataset impact without the exact OLD dataset.

## 13. Regression cases

Not available from controlled A/B.

## 14. Improvement cases

Not available from controlled A/B.

## 15. Local recovery evidence

Discovered local chunk candidates:

| File | SHA-256 | Bytes | Lines | Use for OLD? |
|---|---|---:|---:|---|
| `ip-sakti-rag/dataset/canonical/chunks.jsonl` | `827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d` | 9,375,864 | 7,019 | No, this is NEW |
| `ip-sakti-rag/.audit_tmp/ab_eval/new/chunks.jsonl` | `827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d` | 9,375,864 | 7,019 | No, this is NEW copy |
| `ip-sakti-rag/.audit_tmp/ab_eval/old/chunks.jsonl` | `61c6c52c7242ec2375bd4e9701b56933cf7ed1a7daba439db994823ce2bf94dd` | 8,987,362 | 6,514 | No, hash mismatch |
| `ip-sakti-rag/dataset/processed/chunks.jsonl` | `aad411456242d63879c2c337222350e212d6af0d76e5bc99ebc0be1710068bde` | 4,345,697 | 830 | No, processed non-canonical file |

Git history check for `ip-sakti-rag/dataset/canonical/chunks.jsonl` found one tracked version:

| Commit | SHA-256 | Bytes | Lines |
|---|---|---:|---:|
| `fdd66ab9c21e` | `61c6c52c7242ec2375bd4e9701b56933cf7ed1a7daba439db994823ce2bf94dd` | 8,987,362 | 6,514 |

This confirms local Git history does not contain the exact historical hash.

## 16. Final recommendation

**INSUFFICIENT_EVIDENCE**

Do not decide whether to keep or restore either dataset from the currently available evidence.

Required next action:

1. Recover the exact historical `chunks.jsonl` bytes from an external trusted source such as CI artifacts, object storage, backup, source-control mirror, or the original generation environment.
2. Verify the recovered file hashes exactly to the documented 64-character historical hash:

   ```text
   4ce211289e88958c89d4bafc4ede7271cc387c55cc1f18b73acbe9ea30131bda
   ```

3. Place it in an isolated non-canonical evaluation path.
4. Re-run the A/B evaluation only after the OLD and NEW hashes are verified.

## A/B EVALUATION COMPLETE

Old dataset:

- hash = `4ce211289e88958c89d4bafc4ede7271cc1f18b73acbe9ea30131bda` from latest prompt; **not recovered**
- documented historical hash = `4ce211289e88958c89d4bafc4ede7271cc387c55cc1f18b73acbe9ea30131bda`; **not recovered as bytes**
- chunks = historical reported `6,514`, not verified against exact bytes
- passed = not measured
- failed = not measured
- Recall@K = not measured
- MRR = not measured
- abstention = not measured
- answer quality = not measured

New dataset:

- hash = `827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d`
- chunks = `7,019`
- passed = not measured in controlled A/B
- failed = not measured in controlled A/B
- Recall@K = not measured in controlled A/B
- MRR = not measured in controlled A/B
- abstention = not measured in controlled A/B
- answer quality = not measured in controlled A/B

Conclusion:

Controlled A/B evaluation cannot proceed because the exact OLD dataset bytes are unavailable locally. The new dataset is verified, but there is no valid OLD comparator.

Recommendation:

**INSUFFICIENT_EVIDENCE**

Files created:

- None newly created by this report update; `docs/RAG_DATASET_AB_COMPARISON.md` already existed as an untracked file and was replaced with this corrected blocked-evaluation report.

Files modified:

- `docs/RAG_DATASET_AB_COMPARISON.md`

