# RAG deployment

## Required configuration

Copy `.env.example` and set `SUPABASE_URL`, `SUPABASE_ANON_KEY`, and—in the isolated ingestion environment only—`SUPABASE_SERVICE_ROLE_KEY`. Set a real `OPENROUTER_API_KEY`, embedding model/dimension, and generation model. Keep `RAG_ENABLE_LLM=false` until the grounded JSON path has passed staging evaluation.

The API process must use the anon key. Never ship the service-role key to a client or general API process. Run ingestion as a separate administrative job.

## Staging sequence

1. Acquire every official raw document, validate PDF/HTML type and checksum, rebuild the canonical dataset, and require zero source-missing warnings.
2. Review and apply migrations `001_rag_schema.sql`, `002_match_chunks.sql`, and `003_rag_repair.sql` in a fresh staging database. The third migration has static test coverage but has not been executed against this project's Supabase instance.
3. Set the embedding dimension in configuration and migration consistently. Run `scripts/ingest_embeddings.py` with the service-role key; do not use `--dry-run`.
4. Confirm document, chunk, and embedding counts match, then test both RPCs using the anon role. Verify jurisdiction/domain/language filters and current-version isolation.
5. Run unit tests and `scripts/evaluate_rag.py` against a staging-capable evaluation runner. Add an OpenRouter grounded-generation suite and inspect failures.
6. Start `uvicorn app.api.main:app --host 0.0.0.0 --port 8000`; place authentication, rate limits, request-size limits, TLS, and monitoring at the platform boundary.

## Rollback and observability

Canonical document versions are immutable identifiers derived from content checksums. Ingestion upserts a document's current version and RPCs filter chunks/embeddings to it. Retain prior raw files and database backups before applying migrations. A rollback should point documents to the prior validated version rather than mutate chunk contents.

Monitor retrieval/generation latency, abstention rate, confidence distribution, source/version coverage, provider errors, and citation-validation failures. Do not log secrets or full user queries by default; `retrieval_logs.query_text` should remain null or be governed by an explicit privacy policy.

Current blockers: 23 missing authoritative raw sources, quarantined FSSAI 2022 regulation, unapplied Supabase migration, no production embeddings, no learned reranker, and unverified OpenRouter generation.
