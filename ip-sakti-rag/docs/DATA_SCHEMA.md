# Data schema

`source_registry.csv` is the acquisition control plane. Its canonical column list and status/host validation live in `app/ingestion/registry.py`.

`documents.jsonl` contains one immutable document-version view per validated source. `chunks.jsonl` carries document/version identifiers, authority, jurisdiction, legal structure, page range, source URL, text, and a citation label. Detected structure is limited to headings present in extracted text; the pipeline never synthesizes section numbers.

`metadata.json` records build time, counts, extracted pages, and OCR-required identifiers. `download_manifest.json` records transport and validation results. `checksums.sha256` uses the conventional `<digest>  <relative path>` format.

The Supabase migration owns only `documents`, `document_versions`, `chunks`, `document_embeddings`, `retrieval_logs`, and `evaluation_results`. It does not touch backend-owned user, conversation, message, or audit tables. The checked-in vector column is 1536 dimensions; choose the production embedding model and adjust the initial migration before deployment if another dimension is required.

