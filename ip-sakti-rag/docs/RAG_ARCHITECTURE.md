# RAG architecture

## Runtime flow

```text
QueryRequest
  -> jurisdiction/domain/identifier analysis
  -> hard metadata filters
  -> vector candidates + lexical candidates
  -> normalized weighted fusion (0.55 / 0.35 / 0.10)
  -> deterministic legal-feature reranker
  -> identifier and relevance abstention gates
  -> bounded, deduplicated untrusted-data context
  -> grounded generator
  -> application-built citations
  -> citation validator
  -> rule-based confidence and QueryResponse
```

`RAGService` is the orchestration boundary. In production, `SupabaseCorpusStore` calls separate pgvector and PostgreSQL full-text RPCs. In local mode, `LocalCorpusStore` supplies deterministic BM25-like lexical and TF-IDF cosine signals. That local vector signal is useful for executable tests but is not a production embedding substitute.

The fusion score is `0.55 * normalized_vector + 0.35 * normalized_lexical + 0.10 * metadata_match`. The fallback reranker then uses fusion, query-token coverage, exact legal identifier match, and verified-source status. It is deliberately reported as `learned=false`; no cross-encoder claim is made.

## Grounding boundary

Context retains document ID/version, title, authority, URL, jurisdiction, legal hierarchy, page range, source status, and chunk ID. Retrieved text is surrounded by explicit untrusted-data markers. The optional LLM is instructed to return only answer text, used chunk IDs, and an insufficiency flag. It cannot author citations. Citations are generated from retrieved metadata, limited to chunk IDs actually selected, and validated before an answer is returned.

Exact `Section`, `Rule`, `Regulation`, or `Article` questions must match canonical legal metadata. Missing identifiers abstain. Questions that require the quarantined FSSAI 2022 regulation also abstain. Provider or citation-validation failures fail closed.

## Confidence

Confidence is deterministic and combines top reranker score (40%), citation coverage (20%), source authority status (15%), support count (15%), and jurisdiction consistency (10%). `HIGH` requires a score of at least 0.80 and verified sources; `MEDIUM` requires 0.55 and at least one verified source; otherwise supported answers are `LOW`. Abstentions are `INSUFFICIENT_EVIDENCE`.

## Data and database

Migration `003_rag_repair.sql` adds canonical version/status/legal fields, a dimension-checked `chunk_embeddings` table, citation records, GIN and HNSW indexes, RLS, current-version filters, and separate vector/keyword RPCs. Public roles receive filtered RPC execution and read access only to included current documents/chunks. Administrative writes require a service-role client.
