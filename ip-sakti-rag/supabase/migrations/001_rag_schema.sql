CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS documents (
  id text PRIMARY KEY,
  title text NOT NULL,
  authority text NOT NULL,
  domain text NOT NULL,
  jurisdiction text NOT NULL,
  document_type text NOT NULL,
  source_url text NOT NULL,
  language text NOT NULL DEFAULT 'en',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS document_versions (
  id text PRIMARY KEY,
  document_id text NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  version_label text,
  publication_date date,
  effective_date date,
  retrieved_at timestamptz NOT NULL,
  sha256 char(64) NOT NULL UNIQUE,
  storage_path text NOT NULL,
  file_size_bytes bigint NOT NULL CHECK (file_size_bytes > 0),
  status text NOT NULL,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE IF NOT EXISTS chunks (
  id text PRIMARY KEY,
  document_id text NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  document_version_id text NOT NULL REFERENCES document_versions(id) ON DELETE CASCADE,
  ordinal integer NOT NULL,
  text text NOT NULL,
  citation_label text NOT NULL,
  page_start integer,
  page_end integer,
  chapter text,
  section text,
  subsection text,
  rule text,
  article text,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  UNIQUE(document_version_id, ordinal)
);

-- Default dimension is documented/configurable. Change this migration before first
-- production deployment if the selected model uses another dimension.
CREATE TABLE IF NOT EXISTS document_embeddings (
  chunk_id text PRIMARY KEY REFERENCES chunks(id) ON DELETE CASCADE,
  provider text NOT NULL,
  model text NOT NULL,
  dimension integer NOT NULL DEFAULT 1536,
  embedding vector(1536) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS document_embeddings_hnsw ON document_embeddings USING hnsw (embedding vector_cosine_ops);

CREATE TABLE IF NOT EXISTS retrieval_logs (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  query_hash char(64) NOT NULL,
  query_text text,
  filters jsonb NOT NULL DEFAULT '{}'::jsonb,
  result_chunk_ids text[] NOT NULL DEFAULT '{}',
  scores double precision[] NOT NULL DEFAULT '{}',
  latency_ms integer,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS evaluation_results (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  question_id text NOT NULL,
  run_label text NOT NULL,
  retrieved_chunk_ids text[] NOT NULL DEFAULT '{}',
  metrics jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

