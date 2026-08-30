# Dataset final validation

Validated on 2026-08-29 with `scripts/build_dataset.py` and `scripts/validate_dataset.py`.

## Measured state

| Check | Result |
|---|---:|
| Registered documents | 25 |
| Included in retrieval | 24 |
| Canonical chunks | 6,514 |
| Validation errors | 0 |
| Validation warnings | 2 |
| Verified raw ingestions | 22 |
| Quarantined documents | 1 |

The canonical outputs are `dataset/canonical/documents.jsonl`, `chunks.jsonl`, and `metadata.json`. Required IDs and legal metadata mappings pass, chunk IDs and within-document chunk text are unique, page ranges are checked when pages exist, and structure anchors have legal metadata.

## Source findings

- 21 of the 23 formerly missing-source warning documents were repaired with authoritative raw files. The verified set includes IP India/PPVFR Authority domestic IP statutes and rules, Ministry of Ayush reports, WIPO/WIPO Lex treaty texts, MoEFCC biodiversity amendment, and India Code biodiversity rules.
- `IND-FSS-AA-ORDER-2025` remains independently validated. It is a 172-page PDF with SHA-256 `498d6f579357e7bb8e8d7b9f7741db5863f6eea8d29ffd559ff9add7d32d7edc`. OCR was needed; OCR-derived chunks are marked uncertain.
- `IND-FSS-AA-2022` is excluded from retrieval. Its official URL currently returned an HTML application shell rather than PDF bytes, so the local file is quarantined and the pipeline refuses to answer questions requiring that regulation.
- `IND-PAT-RULES-2003` remains `LEGACY_UNVERIFIED_RAW_MISSING`: the India Code upload endpoint did not yield a verified source and an IP India candidate was rejected as the wrong document.
- `IND-CR-RULES-2013` remains `LEGACY_UNVERIFIED_RAW_MISSING`: the official Copyright Office site exposes a chapterized HTML index, not a single verified raw source artifact compatible with the current ingestion path.
- Restricted TKDL material is registry-only and is not ingested.

## Reproducibility and fail-closed behavior

The builder validates PDF magic bytes, computes checksums, extracts page-aware text, uses an OCR cache for sparse scans, assigns stable document-version and chunk IDs, deduplicates exact text within each document, and runs the validator before publishing canonical files. Invalid FSSAI 2022 bytes cannot fall back to the former unrelated USDA report.

The dataset is structurally valid for local testing. It is substantially source-repaired but not source-complete; it must not be called production-ready until the two remaining authoritative rule sources are acquired or ingested through a documented chapter-consolidation path.
