-- IP-SAKTI canonical RAG repair migration.
-- Safe to apply after 001/002; retains legacy columns while making them nullable.
CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE documents ADD COLUMN IF NOT EXISTS document_version text;
ALTER TABLE documents ADD COLUMN IF NOT EXISTS publication_date date;
ALTER TABLE documents ADD COLUMN IF NOT EXISTS effective_date date;
ALTER TABLE documents ADD COLUMN IF NOT EXISTS version_label text;
ALTER TABLE documents ADD COLUMN IF NOT EXISTS checksum char(64);
ALTER TABLE documents ADD COLUMN IF NOT EXISTS page_count integer;
ALTER TABLE documents ADD COLUMN IF NOT EXISTS ingestion_status text NOT NULL DEFAULT 'UNVERIFIED';
ALTER TABLE documents ADD COLUMN IF NOT EXISTS included_in_retrieval boolean NOT NULL DEFAULT false;
ALTER TABLE documents ADD COLUMN IF NOT EXISTS metadata jsonb NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE chunks ALTER COLUMN document_version_id DROP NOT NULL;
ALTER TABLE chunks ALTER COLUMN citation_label DROP NOT NULL;
ALTER TABLE chunks ADD COLUMN IF NOT EXISTS document_version text;
ALTER TABLE chunks ADD COLUMN IF NOT EXISTS rule_number text;
ALTER TABLE chunks ADD COLUMN IF NOT EXISTS sub_rule text;
ALTER TABLE chunks ADD COLUMN IF NOT EXISTS regulation_number text;
ALTER TABLE chunks ADD COLUMN IF NOT EXISTS article_number text;
ALTER TABLE chunks ADD COLUMN IF NOT EXISTS paragraph_number text;
ALTER TABLE chunks ADD COLUMN IF NOT EXISTS clause text;
ALTER TABLE chunks ADD COLUMN IF NOT EXISTS language text NOT NULL DEFAULT 'en';
ALTER TABLE chunks ADD COLUMN IF NOT EXISTS source_status text NOT NULL DEFAULT 'UNVERIFIED';
ALTER TABLE chunks ADD COLUMN IF NOT EXISTS text_uncertain boolean NOT NULL DEFAULT false;
ALTER TABLE chunks ADD COLUMN IF NOT EXISTS search_vector tsvector
  GENERATED ALWAYS AS (to_tsvector('english', coalesce(text, ''))) STORED;

CREATE TABLE IF NOT EXISTS chunk_embeddings (
  chunk_id text PRIMARY KEY REFERENCES chunks(id) ON DELETE CASCADE,
  document_version text NOT NULL,
  provider text NOT NULL,
  model text NOT NULL,
  dimension integer NOT NULL CHECK (dimension = 1536),
  embedding vector(1536) NOT NULL,
  text_checksum char(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS citation_records (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  retrieval_log_id bigint REFERENCES retrieval_logs(id) ON DELETE CASCADE,
  chunk_id text NOT NULL REFERENCES chunks(id) ON DELETE RESTRICT,
  citation jsonb NOT NULL,
  validated boolean NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS documents_jurisdiction_domain_idx ON documents (jurisdiction, domain);
CREATE INDEX IF NOT EXISTS documents_status_idx ON documents (included_in_retrieval, ingestion_status);
CREATE INDEX IF NOT EXISTS chunks_document_version_idx ON chunks (document_id, document_version);
CREATE INDEX IF NOT EXISTS chunks_legal_metadata_idx ON chunks (section, rule_number, regulation_number, article_number);
CREATE INDEX IF NOT EXISTS chunks_language_idx ON chunks (language);
CREATE INDEX IF NOT EXISTS chunks_search_vector_gin ON chunks USING gin (search_vector);
CREATE INDEX IF NOT EXISTS chunk_embeddings_hnsw ON chunk_embeddings USING hnsw (embedding vector_cosine_ops);

ALTER TABLE documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE chunks ENABLE ROW LEVEL SECURITY;
ALTER TABLE chunk_embeddings ENABLE ROW LEVEL SECURITY;
ALTER TABLE retrieval_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE evaluation_results ENABLE ROW LEVEL SECURITY;
ALTER TABLE citation_records ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS documents_public_read ON documents;
CREATE POLICY documents_public_read ON documents FOR SELECT TO anon, authenticated
USING (included_in_retrieval = true);

DROP POLICY IF EXISTS chunks_public_read ON chunks;
CREATE POLICY chunks_public_read ON chunks FOR SELECT TO anon, authenticated
USING (EXISTS (
  SELECT 1 FROM documents d
  WHERE d.id = chunks.document_id
    AND d.included_in_retrieval = true
    AND d.document_version = chunks.document_version
));

-- Embeddings and operational logs intentionally have no anon/authenticated
-- direct-access policy. RPCs below expose only filtered evidence fields.

CREATE OR REPLACE FUNCTION match_chunks_vector(
  query_embedding vector(1536),
  match_threshold double precision DEFAULT 0.10,
  match_count integer DEFAULT 24,
  filter_jurisdiction text DEFAULT NULL,
  filter_domains text[] DEFAULT NULL,
  filter_language text DEFAULT 'en'
)
RETURNS TABLE (
  chunk_id text, document_id text, document_version text, text text,
  title text, authority text, domain text, jurisdiction text, document_type text,
  source_url text, language text, source_status text,
  chapter text, section text, subsection text, rule_number text, sub_rule text,
  regulation_number text, article_number text, paragraph_number text, clause text,
  page_start integer, page_end integer, similarity double precision
)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$
  SELECT c.id, c.document_id, c.document_version, c.text,
         d.title, d.authority, d.domain, d.jurisdiction, d.document_type,
         d.source_url, c.language, c.source_status,
         c.chapter, c.section, c.subsection, c.rule_number, c.sub_rule,
         c.regulation_number, c.article_number, c.paragraph_number, c.clause,
         c.page_start, c.page_end,
         1 - (e.embedding <=> query_embedding) AS similarity
  FROM chunk_embeddings e
  JOIN chunks c ON c.id = e.chunk_id
  JOIN documents d ON d.id = c.document_id
  WHERE d.included_in_retrieval = true
    AND c.document_version = d.document_version
    AND e.document_version = d.document_version
    AND (filter_jurisdiction IS NULL OR d.jurisdiction = filter_jurisdiction)
    AND (filter_domains IS NULL OR d.domain = ANY(filter_domains))
    AND (filter_language IS NULL OR c.language = filter_language)
    AND 1 - (e.embedding <=> query_embedding) >= match_threshold
  ORDER BY e.embedding <=> query_embedding
  LIMIT LEAST(GREATEST(match_count, 1), 100);
$$;

CREATE OR REPLACE FUNCTION match_chunks_keyword(
  search_query text,
  match_count integer DEFAULT 24,
  filter_jurisdiction text DEFAULT NULL,
  filter_domains text[] DEFAULT NULL,
  filter_language text DEFAULT 'en'
)
RETURNS TABLE (
  chunk_id text, document_id text, document_version text, text text,
  title text, authority text, domain text, jurisdiction text, document_type text,
  source_url text, language text, source_status text,
  chapter text, section text, subsection text, rule_number text, sub_rule text,
  regulation_number text, article_number text, paragraph_number text, clause text,
  page_start integer, page_end integer, lexical_score double precision
)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$
  WITH query AS (SELECT websearch_to_tsquery('english', search_query) value)
  SELECT c.id, c.document_id, c.document_version, c.text,
         d.title, d.authority, d.domain, d.jurisdiction, d.document_type,
         d.source_url, c.language, c.source_status,
         c.chapter, c.section, c.subsection, c.rule_number, c.sub_rule,
         c.regulation_number, c.article_number, c.paragraph_number, c.clause,
         c.page_start, c.page_end,
         ts_rank_cd(c.search_vector, query.value)::double precision AS lexical_score
  FROM chunks c
  JOIN documents d ON d.id = c.document_id
  CROSS JOIN query
  WHERE d.included_in_retrieval = true
    AND c.document_version = d.document_version
    AND (filter_jurisdiction IS NULL OR d.jurisdiction = filter_jurisdiction)
    AND (filter_domains IS NULL OR d.domain = ANY(filter_domains))
    AND (filter_language IS NULL OR c.language = filter_language)
    AND c.search_vector @@ query.value
  ORDER BY lexical_score DESC
  LIMIT LEAST(GREATEST(match_count, 1), 100);
$$;

REVOKE ALL ON FUNCTION match_chunks_vector(vector, double precision, integer, text, text[], text) FROM PUBLIC;
REVOKE ALL ON FUNCTION match_chunks_keyword(text, integer, text, text[], text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION match_chunks_vector(vector, double precision, integer, text, text[], text) TO anon, authenticated;
GRANT EXECUTE ON FUNCTION match_chunks_keyword(text, integer, text, text[], text) TO anon, authenticated;
