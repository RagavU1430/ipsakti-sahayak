# IP-SAKTI Sahayak RAG

Source-grounded retrieval and answer generation for the IP-SAKTI legal corpus. The repaired implementation has one canonical ingestion path, legal-structure-aware chunks, hybrid retrieval, deterministic reranking, application-generated citations, explicit abstention, confidence scoring, an optional OpenRouter generation path, and a FastAPI boundary.

The local pipeline is operational, but the production release gate is **not passed**. Only the FSSAI 2025 order has a locally verified authoritative PDF; 23 retrievable documents use legacy text with page citations disabled, and the FSSAI 2022 regulation is quarantined because the downloaded bytes are HTML rather than a PDF. Supabase and production OpenRouter execution have not been verified because credentials are not configured.

## Reproduce

```powershell
python -m pip install -r requirements.txt
python scripts/build_dataset.py
python scripts/validate_dataset.py
python scripts/ingest_embeddings.py --provider hash --dry-run --limit 2
python -m pytest -q
python scripts/evaluate_rag.py
uvicorn app.api.main:app --host 127.0.0.1 --port 8000
```

The default `auto` storage mode selects Supabase only when its URL, anon key, and embedding API key are present; otherwise it uses the clearly labelled local TF-IDF development store. Administrative ingestion requires a service-role key. Do not expose that key to the API process.

Start with [the final repair report](docs/RAG_REPAIR_FINAL_REPORT.md), [architecture](docs/RAG_ARCHITECTURE.md), [dataset validation](docs/DATASET_FINAL_VALIDATION.md), [evaluation](docs/RAG_EVALUATION.md), [API contract](docs/RAG_API_CONTRACT.md), and [deployment guide](docs/RAG_DEPLOYMENT.md).
