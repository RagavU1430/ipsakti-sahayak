# IP-SAKTI Sahayak — RAG/Data Engineering

Reproducible, source-traceable ingestion for the project's authoritative legal and Ayurveda corpus. This directory owns only dataset acquisition, validation, extraction, chunking, RAG metadata, evaluation fixtures, and the RAG-owned Supabase schema. It contains no UI, authentication, user management, chat history, or backend business logic.

## Reproduce

Python 3.12+ is recommended.

```powershell
python -m venv .venv
.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
python scripts/discover_sources.py
python scripts/download_sources.py
python scripts/validate_documents.py
python scripts/build_dataset.py
python -m pytest -q
```

`build_dataset.py` performs extraction, cleaning, legal-structure detection, chunk creation, and metadata generation. The `extract_text.py`, `clean_text.py`, `chunk_documents.py`, and `build_metadata.py` compatibility entry points invoke that idempotent combined build.

Raw PDFs are intentionally ignored by Git. Re-run the downloader to reproduce them from `dataset/manifests/source_registry.csv`, then compare `checksums.sha256`. Files whose direct official URL has not been verified are not downloaded. Never insert a mirror URL to make a run pass.

## Safety and versioning

- Only allowlisted official HTTPS hosts are accepted by registry validation.
- Restricted records are never sent to the downloader.
- HTML error pages masquerading as PDFs are rejected.
- If bytes at a stable path change, the old file is timestamp-versioned before the new file is written.
- Production embeddings are not generated. `EMBEDDING_DIMENSION` defaults to 1536 but the provider interface is configurable.

See [docs/DATASET.md](docs/DATASET.md), [docs/SOURCES.md](docs/SOURCES.md), [docs/DATA_SCHEMA.md](docs/DATA_SCHEMA.md), and [docs/INGESTION.md](docs/INGESTION.md).

