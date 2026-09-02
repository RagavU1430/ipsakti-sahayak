# IP-SAKTI Dataset Drift Investigation Report

## Executive summary

The canonical RAG chunks file drift is real and content-affecting. The current `ip-sakti-rag/dataset/canonical/chunks.jsonl` is not merely reordered or reformatted: it has a different record count, thousands of changed chunk IDs, and changed chunk boundaries/content relative to the local Git baseline.

The most likely cause is that the canonical dataset was regenerated through the existing ingestion pipeline after `ip-sakti-rag/app/ingestion/chunker.py` was modified. The timestamps are tightly aligned:

- `ip-sakti-rag/scripts/deep_test_rag.py` last modified: `2026-09-02 06:06:01 UTC`
- `ip-sakti-rag/app/ingestion/chunker.py` last modified: `2026-09-02 06:06:10 UTC`
- `ip-sakti-rag/dataset/canonical/chunks.jsonl` last modified: `2026-09-02 06:07:32 UTC`
- `ip-sakti-rag/dataset/canonical/metadata.json` last modified: `2026-09-02 06:07:32 UTC`
- deep-test artifacts last modified: `2026-09-02 06:21:12 UTC` to `2026-09-02 06:24:19 UTC`

`deep_test_rag.py` does not write canonical dataset files. It reads protected dataset fingerprints before and after the test run, reads canonical chunks for validation, and writes only evaluation artifacts plus `ip-sakti-rag/docs/RAG_DEEP_TEST_REPORT.md`.

The deep-test result is conditionally valid as a measurement of the dirty working-tree state at the time it ran, but it is invalid as a direct comparison against the previous 55/55 locked baseline because both the dataset hash and runtime/retrieval code changed, and the deep suite expanded from 55 to 162 cases.

## Previous hash

Known-good canonical chunks hash supplied in the prompt:

```text
4ce211289e88958c89d4bafc4ede7271cc387c55cc1f18b73acbe9ea30131bda
```

This hash is documented in the repository, including:

- `docs/SUPABASE_PRODUCTION_VERIFICATION_REPORT.md`
- `ip-sakti-rag/docs/RAG_QUALITY_REPAIR_REPORT.md`
- `ip-sakti-rag/dataset/evaluation/results/runtime_question_test.json`

The deep-test summary also records this as the initial session dataset hash.

## Current hash

Current working-tree hash:

```text
827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d
```

Current metadata:

```json
{
  "generated_at": "2026-09-02T06:07:32.211780+00:00",
  "document_count": 25,
  "retrievable_document_count": 24,
  "chunk_count": 7019,
  "validation_passed": true
}
```

Important baseline caveat: the version of `chunks.jsonl` in local Git `HEAD` hashes to:

```text
61c6c52c7242ec2375bd4e9701b56933cf7ed1a7daba439db994823ce2bf94dd
```

That Git `HEAD` file contains 6,514 records. The prompt’s known-good `4ce211...` content is documented locally, but the actual bytes for that prior file state were not found as a checked-out file or Git-tracked version during this investigation. Therefore, deep byte-level comparison was possible against local Git `HEAD`, not against the unavailable `4ce211...` content.

## Exact suspected cause

The suspected cause is a canonical dataset rebuild performed after modifying the chunking logic in `ip-sakti-rag/app/ingestion/chunker.py`.

Evidence:

1. `chunker.py` was modified immediately before canonical regeneration.
2. `metadata.json` records a new generation timestamp and changed `chunk_count`.
3. `pipeline.py` writes `dataset/canonical/chunks.jsonl` and `dataset/canonical/metadata.json`.
4. `scripts/build_dataset.py` calls `build_canonical_dataset()`.
5. `deep_test_rag.py` does not write canonical dataset files and its own summary shows `dataset_before == dataset_after == 827f...`.

No OS-level process log or shell history was available in the repository, so the exact command invocation cannot be proven from local project files alone. The repository evidence strongly supports:

```text
modified chunker.py -> build_canonical_dataset() executed -> chunks.jsonl regenerated
```

## Evidence

### Git status

Relevant dirty files:

```text
M ip-sakti-rag/app/ingestion/chunker.py
M ip-sakti-rag/app/retrieval/local_store.py
M ip-sakti-rag/app/retrieval/query_analysis.py
M ip-sakti-rag/app/retrieval/reranker.py
M ip-sakti-rag/app/service.py
M ip-sakti-rag/dataset/canonical/chunks.jsonl
M ip-sakti-rag/dataset/canonical/metadata.json
?? ip-sakti-rag/dataset/evaluation/deep_rag/
?? ip-sakti-rag/scripts/deep_test_rag.py
```

No changes were reported for:

```text
ip-sakti-rag/dataset/canonical/documents.jsonl
ip-sakti-rag/dataset/raw
ip-sakti-rag/dataset/manifests
```

### Canonical metadata drift

`metadata.json` changed from:

```text
generated_at: 2026-08-29T04:08:04.471580+00:00
chunk_count: 6514
```

to:

```text
generated_at: 2026-09-02T06:07:32.211780+00:00
chunk_count: 7019
```

The document count, retrievable document count, validation status, and warnings remained unchanged.

### Chunker changes

`ip-sakti-rag/app/ingestion/chunker.py` gained:

- `FOOTNOTE_START_RE`
- `HEADER_IGNORE_RE`
- `GAZETTE_ACT_SECTION_RE`

`_provision()` was changed to:

- ignore header-like lines,
- ignore footnote-like lines,
- detect Gazette-style act section lines for `ACT` and `AMENDMENT_ACT` documents.

These changes are directly capable of changing legal unit detection, chunk boundaries, chunk IDs, and per-document chunk counts.

### Canonical writer path

`ip-sakti-rag/scripts/build_dataset.py` calls:

```text
build_canonical_dataset()
```

`ip-sakti-rag/app/ingestion/pipeline.py` writes:

```text
dataset/canonical/documents.jsonl
dataset/canonical/chunks.jsonl
dataset/canonical/metadata.json
```

The pipeline also regenerates `generated_at`, so any run of this function changes canonical metadata even if corpus records were otherwise stable.

### Deep-test write behavior

`ip-sakti-rag/scripts/deep_test_rag.py` defines protected files:

```text
dataset/canonical/documents.jsonl
dataset/canonical/chunks.jsonl
dataset/manifests/source_registry.csv
dataset/manifests/download_manifest.json
dataset/manifests/checksums.sha256
```

It reads these fingerprints before and after evaluation. It writes only:

```text
ip-sakti-rag/dataset/evaluation/deep_rag/deep_rag_results.json
ip-sakti-rag/dataset/evaluation/deep_rag/deep_rag_failures.json
ip-sakti-rag/dataset/evaluation/deep_rag/deep_rag_summary.json
ip-sakti-rag/docs/RAG_DEEP_TEST_REPORT.md
```

The saved summary reports:

```text
dataset_before chunks hash: 827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d
dataset_after chunks hash:  827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d
dataset_changed: false
dataset_changed_from_initial_session: true
```

Therefore, `deep_test_rag.py` detected the earlier drift but did not cause it during the recorded run.

## Whether content changed or only serialization/order changed

Content changed: yes.

Comparison against local Git `HEAD`:

| Check | Result |
|---|---:|
| Git `HEAD` chunks hash | `61c6c52c7242ec2375bd4e9701b56933cf7ed1a7daba439db994823ce2bf94dd` |
| Current chunks hash | `827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d` |
| Git `HEAD` records | 6,514 |
| Current records | 7,019 |
| Added chunk IDs vs Git `HEAD` | 3,083 |
| Removed chunk IDs vs Git `HEAD` | 2,578 |
| Common chunk IDs | 3,936 |
| Common IDs with changed text | 5 |
| Common IDs with changed metadata | 0 |
| Common-record ordering | unchanged for surviving common IDs |
| Git `HEAD` line endings | LF |
| Current line endings | CRLF |

Conclusion by requested categories:

| Category | Finding |
|---|---|
| A. Same records in different ordering | No. Record count and IDs changed. |
| B. Formatting/serialization changes | Yes, line endings changed from LF to CRLF; not the primary cause. |
| C. Changed chunk text | Yes, at least 5 common IDs changed text, and thousands of replaced IDs imply changed chunk boundaries/text hashes. |
| D. Changed metadata | No metadata changes were detected among common chunk IDs. Dataset-level metadata changed. |
| E. Added/removed chunks | Yes: +3,083 added IDs, -2,578 removed IDs vs Git `HEAD`. |
| F. Changed chunk IDs | Yes, extensively. |
| G. Deterministic pipeline with different parameters/logic | Likely. The pipeline appears unchanged, but chunking logic changed. |
| H. Unintentional modification | Plausible, because the task constraints say the dataset was locked, and canonical files were regenerated despite that lock. Intent cannot be proven from repository evidence alone. |

Per-document added/removed chunk IDs vs Git `HEAD`:

| Document | Added IDs | Removed IDs |
|---|---:|---:|
| `IND-PAT-ACT-1970` | 822 | 665 |
| `IND-TM-ACT-1999` | 516 | 398 |
| `IND-PPV-ACT-2001` | 435 | 354 |
| `IND-GI-ACT-1999` | 397 | 279 |
| `IND-CR-ACT-1957` | 351 | 348 |
| `IND-PAT-RULES-2003` | 261 | 262 |
| `IND-BD-AMEND-2023` | 158 | 130 |
| `IND-TM-RULES-2017` | 120 | 121 |
| `IND-BD-ACT-2002` | 22 | 20 |
| `IND-CR-RULES-2013` | 1 | 1 |

This pattern is consistent with chunk boundary regeneration across major legal documents, not a small manual edit.

## Files/scripts involved

Likely involved:

- `ip-sakti-rag/app/ingestion/chunker.py`
- `ip-sakti-rag/app/ingestion/pipeline.py`
- `ip-sakti-rag/scripts/build_dataset.py`
- `ip-sakti-rag/dataset/canonical/chunks.jsonl`
- `ip-sakti-rag/dataset/canonical/metadata.json`

Relevant but not canonical writers:

- `ip-sakti-rag/scripts/deep_test_rag.py`
- `ip-sakti-rag/scripts/evaluate_rag.py`
- `ip-sakti-rag/scripts/test_rag_questions.py`

Runtime/retrieval files also changed in the same dirty working tree:

- `ip-sakti-rag/app/retrieval/local_store.py`
- `ip-sakti-rag/app/retrieval/query_analysis.py`
- `ip-sakti-rag/app/retrieval/reranker.py`
- `ip-sakti-rag/app/service.py`

Those runtime changes mean the deep-test failure set cannot be attributed to dataset drift alone.

## Deep-test failure classification

Existing saved deep-test artifact:

```text
ip-sakti-rag/dataset/evaluation/deep_rag/deep_rag_failures.json
```

Summary:

```text
Tests: 162
Passed: 105
Failed: 57
Recall@K: 0.8571
MRR: 0.8095
Citation integrity: 1.0000
Abstention accuracy: 0.7160
Answer quality: 1.5432 / 2
P50: 119.729 ms
P95: 314.805 ms
P99: 542.242 ms
```

Failure symptoms are overlapping. Classification from the saved JSON:

| Failure symptom | Count | Notes |
|---|---:|---|
| False abstention | 12 | Answer expected but system abstained. |
| Unsafe/unsupported non-abstention | 24 | Test expected abstention, clarification, or unsupported handling, but system answered. |
| Expected-document mismatch / retrieval failure | 18 | Expected source document was absent from returned sources. |
| Grounded answer has no citations | 18 | Often overlaps with retrieval or unsafe-answer cases. |
| Unbalanced comparison evidence | 2 | Comparison answer missed one expected side/source. |

Failures by category:

| Category | Failed |
|---|---:|
| `K_OUT_OF_CORPUS` | 10 |
| `I_AMBIGUOUS` | 8 |
| `L_ADVERSARIAL` | 8 |
| `B_SECTION_SPECIFIC` | 6 |
| `P_PARAPHRASE` | 5 |
| `F_FORMULATION_PRODUCT` | 4 |
| `G_COMPARISON` | 3 |
| `Q_TYPOS_NATURAL_LANGUAGE` | 3 |
| `C_TRADITIONAL_KNOWLEDGE` | 2 |
| `D_SECTION_3E` | 2 |
| `J_FALSE_PREMISE` | 2 |
| `A_DIRECT_LEGAL` | 1 |
| `E_ABS_GRATK` | 1 |
| `H_MULTI_DOMAIN` | 1 |
| `R_LANGUAGE_ENGLISH` | 1 |

Representative failures:

- `A003`, `B003`, `B004`: trademark Section 18/28 questions abstained despite expected `IND-TM-ACT-1999`.
- `B013`: Biological Diversity Act Section 3 returned `IND-BD-AMEND-2023`, missing `IND-BD-ACT-2002`.
- `B015`, `P026`, `P027`: WIPO GRATK questions retrieved other international IP documents instead of `INT-WIPO-GRATK-2024`.
- `D002`, `D003`: Section 3(e) formulation questions abstained despite expected `IND-PAT-ACT-1970`.
- `K001` through `K010`: out-of-corpus questions produced unsupported handling failures.
- Multiple `I_*` ambiguous cases generated non-abstained answers with no citations.

## Impact on RAG evaluation

The dataset drift plausibly affects retrieval and ranking because:

- chunk count changed from 6,514 to 7,019,
- chunk IDs changed extensively,
- chunk boundaries changed across major source documents,
- legal provision detection changed,
- source selection for section-specific and treaty questions depends heavily on chunk/provision metadata.

However, the 57 failures cannot be attributed solely to dataset drift because runtime code also changed in the same working tree:

- query identifier parsing changed,
- local source matching changed,
- reranker identifier boosting changed,
- service-level evidence selection and generation sufficiency behavior changed.

Also, many failures are abstention-policy/general-fallback failures, especially out-of-corpus, ambiguous, and adversarial cases. Those are more likely tied to runtime answer policy than chunk drift alone.

## Deep-test comparison validity

Deep-test validity: conditionally valid.

The deep-test result is valid as a snapshot of this dirty working-tree state:

```text
dataset_before == dataset_after == 827f...
```

It should be considered invalid for direct comparison with the previous 55/55 baseline because:

1. The canonical chunks hash differs from the locked documented hash.
2. The local Git baseline also differs from the documented locked hash.
3. The deep test contains 162 questions, while the previous baseline reported 55.
4. RAG runtime/retrieval code changed alongside the dataset.
5. `deep_test_rag.py` is untracked and therefore not part of a stable committed evaluation baseline.

## Recommended next action

Do not repair blindly.

Recommended next step:

1. Preserve the current dirty worktree as evidence, preferably by copying the investigation report and collecting `git diff` output.
2. Locate the actual known-good `4ce211...` `chunks.jsonl` bytes from backup, artifact storage, previous working tree, or CI artifact.
3. Compare `4ce211...` directly against current `827f...`.
4. Decide whether the `chunker.py` change is desired.
5. If the dataset lock is authoritative, restore the locked dataset from verified artifact only after explicit approval.
6. Re-run evaluation only after establishing a single clean baseline: dataset hash, runtime commit, test suite version, and environment.

## Read-only verification commands run

No dataset repair, regeneration, retrieval-code edit, or test edit was performed.

Read-only commands run during this investigation included:

- `git status --short -- ip-sakti-rag\dataset ip-sakti-rag\scripts ip-sakti-rag\app\ingestion ip-sakti-rag\app\retrieval ip-sakti-rag\app\service.py docs`
- `Get-Item ... | Select-Object FullName,Length,LastWriteTimeUtc`
- `rg -n "<hashes>" docs ip-sakti-rag\docs ip-sakti-rag\dataset ...`
- `Get-ChildItem ip-sakti-rag\dataset\evaluation\deep_rag`
- record-level JSONL comparison against `git show HEAD:ip-sakti-rag/dataset/canonical/chunks.jsonl`
- `rg -n "def build_canonical_dataset|_write_jsonl|chunks.jsonl|metadata.json|..."`
- `git log --oneline --decorate -- <dataset/chunker/pipeline/build files>`
- `Get-Content ip-sakti-rag\dataset\evaluation\deep_rag\deep_rag_summary.json`
- saved-failure parsing from `deep_rag_failures.json`
- `git diff -- ip-sakti-rag\app\ingestion\chunker.py ...`
- `git diff -- ip-sakti-rag\dataset\canonical\metadata.json`
- `git diff --numstat -- <relevant files>`

Tests were not rerun for this drift-only investigation because the user requested investigation-only behavior and final read-only verification. The existing saved deep-test artifacts were inspected instead.

