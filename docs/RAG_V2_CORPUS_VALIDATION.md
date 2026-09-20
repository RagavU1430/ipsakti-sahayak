# RAG V2 Corpus Validation

Validation date: 2026-09-19
Dataset version: `RAG_V2_DATASET_001`
Status: VALIDATION PIPELINE OPERATIONAL; CORPUS NOT RELEASE-READY

## Executed pipeline

The validator scans `RAG V2/dataset_markdown/`, reads UTF-8 Markdown, parses the available frontmatter, assigns stable document IDs from normalized relative paths, records source hashes, rejects extraction-failure content, and emits structure-aware chunks with title/section/subsection/provision context.

Command:

```text
Push-Location "RAG V2"
python scripts/build_v2_corpus.py
```

Configuration:

- `MAX_CHUNK_TOKENS`: 700
- `MIN_CHUNK_TOKENS`: 40
- `OVERLAP_TOKENS`: 40

## Results

| Measure | Result |
|---|---:|
| Markdown files scanned | 24 |
| Valid documents | 24 |
| Quarantined documents | 0 |
| Generated chunks | 13,093 |
| Duplicate documents detected | 0 |
| Missing domain metadata | 24 |
| Missing jurisdiction metadata | 24 |
| Missing document type metadata | 24 |
| Missing authority metadata | 24 |
| Missing source URL metadata | 24 |
| Missing publication date metadata | 24 |
| Missing effective date metadata | 24 |
| Missing language metadata | 24 |

The missing metadata counts are intentionally reported as `UNKNOWN`; the validator does not infer authority, jurisdiction, dates, or URLs from filenames.

## Provenance and chunk checks

- Every document receives a stable `V2-DOC-...` identifier.
- Every document stores a SHA-256 source hash and a version identifier derived from that hash.
- Every chunk stores a content hash, document ID, source path, title, structural labels, page range when present, and token count.
- Placeholder pages such as `[No extractable text found on this page.]` are excluded from chunk output.
- Provision labels are represented as bounded structural context such as `Section 3` rather than duplicating the full provision text.
- Rebuilding the artifact twice produced the same `validated_manifest.json` SHA-256 hash.

## Current limitations

1. The generated Markdown frontmatter does not yet contain authoritative domain, jurisdiction, document type, authority, date, source URL, or language metadata. These must be supplied from a reviewed registry before indexing.
2. The validator currently scans the generated Markdown set, not a final authoritative 25-document registry. Placeholder and auxiliary source selection still requires an explicit corpus allowlist.
3. OCR quality, repeated headers/footers, navigation leakage, tables, and legal provision continuity need deeper document-specific audits.
4. No embeddings, pgvector index, lexical database index, reranker benchmark, grounded generation, citation validation, evidence status, V2 API, or end-to-end benchmark has been executed from these artifacts.

## Decision

The corpus validation and structure-aware chunking foundation is operational and reproducible. RAG V2 is not complete and must not be described as production-ready until authoritative metadata, source allowlisting, embeddings, retrieval, grounding, citations, abstention, performance, security, and benchmark gates are measured.