-- Function to perform cosine similarity search on document embeddings
CREATE OR REPLACE FUNCTION match_chunks (
  query_embedding vector(1536),
  match_threshold float,
  match_count int,
  filter_metadata jsonb default '{}'::jsonb
)
RETURNS TABLE (
  chunk_id text,
  document_id text,
  text text,
  citation_label text,
  page_start int,
  page_end int,
  chapter text,
  section text,
  subsection text,
  rule text,
  article text,
  similarity float
)
LANGUAGE plpgsql
AS $$
BEGIN
  RETURN QUERY
  SELECT
    c.id AS chunk_id,
    c.document_id,
    c.text,
    c.citation_label,
    c.page_start,
    c.page_end,
    c.chapter,
    c.section,
    c.subsection,
    c.rule,
    c.article,
    1 - (de.embedding <=> query_embedding) AS similarity
  FROM document_embeddings de
  JOIN chunks c ON de.chunk_id = c.id
  JOIN documents d ON c.document_id = d.id
  WHERE 1 - (de.embedding <=> query_embedding) > match_threshold
    AND (
      (filter_metadata->>'domain' IS NULL OR d.domain = filter_metadata->>'domain')
      AND (filter_metadata->>'authority' IS NULL OR d.authority = filter_metadata->>'authority')
      AND (filter_metadata->>'document_type' IS NULL OR d.document_type = filter_metadata->>'document_type')
    )
  ORDER BY de.embedding <=> query_embedding
  LIMIT match_count;
END;
$$;
