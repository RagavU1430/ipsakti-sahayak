# RAG Pre-Agentic Baseline — 2026-09-05

## Release gate

Status: **BASELINE FROZEN WITH KNOWN LIMITATIONS**

The complete evaluation was run against the frozen canonical dataset with no dataset regeneration or rewriting.

Command:

```text
python scripts/deep_test_rag.py --base-url http://127.0.0.1:8765 --timeout 30 --max-runtime-errors 3
```

Configuration: local corpus, hash embeddings (64 dimensions), `top_k=8`, `candidate_k=24`, similarity threshold `0.10`, minimum score `0.10`, abstention threshold `0.12`, maximum context `18000` characters, single Uvicorn worker.

## Reproducible result

| Metric | Result |
|---|---:|
| Cases | 162 |
| Passed | 159 |
| Failed | 3 |
| Pass rate | 98.1481% |
| Recall@K | 1.0000 |
| MRR | 0.9836 |
| Groundedness | 1.0000 |
| Citation integrity | 1.0000 |
| Abstention accuracy | 0.9506 |
| Unsafe-answer rate | 0.0432 |
| Median latency | 2092 ms |
| P95 latency | 4032 ms |
| P99 latency | 4324 ms |
| Dataset changed | No |

The evaluation process completed with `runtime_pipeline_verified=true`.

## Remaining cases

1. `B004`: Trade Marks Act Section 28 is not present as a section-28 chunk in the frozen `IND-TM-ACT-1999` corpus. The RAG correctly abstains.
2. `I003`: “Can traditional knowledge be protected?” is broad and underspecified. Safe abstention is retained.
3. `J003`: “Does registration guarantee worldwide patent protection?” requires a cross-border explanation not sufficiently supported by the selected evidence. Safe abstention is retained.

The 7 unsafe-answer count is evaluator-defined and belongs entirely to `J_FALSE_PREMISE`. It is not caused by `K_OUT_OF_CORPUS` or `L_ADVERSARIAL` cases; those classes abstained safely.

## Corpus evidence audit

Frozen dataset SHA-256:

```text
dataset/canonical/chunks.jsonl
827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d
```

Additional frozen corpus hashes:

```text
dataset/canonical/documents.jsonl
6d9b657a2fb84f6414dd7f28c7cc7550c4fe25681e6200242d38889da6ddb7f1
```

Confirmed evidence:

- Patents Act Section 3, including clauses 3(e) and 3(p): present.
- Trade Marks Act Section 18: present.
- Trade Marks Act Section 28 as an actual section-28 provision: absent; only incidental cross-references were found.
- Biological Diversity Act Sections 3 and 6: present in the frozen corpus.
- WIPO GRATK treaty landing-page/title evidence: present, but Article-level text is absent.
- PCT and Paris Convention material: present.

No missing authority is synthesized. Missing or insufficient evidence remains a safe abstention.

## API stability audit

The earlier connection-refused run was a process-lifecycle failure: the detached process launched through the shell-session wrapper was terminated after the wrapper exited. It did not indicate a RAG retrieval or memory failure.

The successful gate used one persistent foreground-equivalent Uvicorn process with one worker. A concurrency smoke test issued 24 requests through 8 workers: **24/24 succeeded**. The process remained responsive at approximately 114 MB working set. The successful 162-case run had no transport errors, with P95 latency of approximately 4.0 seconds.

For repeatability, start the API in a persistent terminal/session and keep that session alive for the entire evaluation. Avoid a shell wrapper that owns and then cleans up the child process.

## Code fingerprint

Key RAG files at the gate:

```text
app/service.py D7A1D7ED2622E25298E6AF540029FD8E0E13AC14273C19395EF769053BA2A027
app/retrieval/local_store.py DCA8D851098F1679C8E824911F9EFF08BA353E92ABE9CD8757C39B3FE4A4F371
app/retrieval/reranker.py 8395F4DC7D64BCB92772BD8B93BF191B9FB183963A13458D345F7DB62360A900
app/legal_aliases.py 31BCB68F1EDB0BEEB76FEDB421EDE296122BDD2BDEFD3871584F34F2C31DA445
app/guardrails/policy.py 10A2B994127ADBB6D743B551DD70AD4071DEEF52F87627C5B62FE94B39AC25E1
```

The working tree contains unrelated application changes, so no Git tag was created over the current `HEAD`; this document and the hashes above are the reproducible baseline marker. Create a Git tag only after committing the intended release snapshot.

## Known limitations before Agentic RAG

- Section 28 of the Trade Marks Act and WIPO GRATK article text need authoritative corpus additions in a future dataset version.
- Broad/underspecified legal questions may abstain conservatively.
- False-premise correction quality can be improved separately from retrieval.
- Local hash retrieval is deterministic but slower than a production vector index.

This baseline is suitable as the comparison point for Agentic RAG experiments.
