"""
RAG V2 PostgreSQL + pgvector Schema

V2 database schema for vector storage and retrieval.
Uses PostgreSQL with pgvector extension for vector similarity search.

Schema design:
- documents_v2: Source document metadata
- chunks_v2: Content chunks with metadata
- embeddings_v2: Vector embeddings (pgvector type)
- retrieval_logs: Query and retrieval tracking
"""

# PostgreSQL Migration SQL for V2 schema
# Execute with: psql -U postgres -d ipsaki < v2_schema.sql

V2_SCHEMA_SQL = """
-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- V2 Documents table
CREATE TABLE IF NOT EXISTS documents_v2 (
    document_id VARCHAR(255) PRIMARY KEY,
    document_version VARCHAR(255) NOT NULL,
    source VARCHAR(512) NOT NULL,
    title VARCHAR(512) NOT NULL,
    domain VARCHAR(255) DEFAULT 'UNKNOWN',
    jurisdiction VARCHAR(255) DEFAULT 'UNKNOWN',
    document_type VARCHAR(255) DEFAULT 'UNKNOWN',
    authority VARCHAR(255) DEFAULT 'UNKNOWN',
    publication_date VARCHAR(50),
    effective_date VARCHAR(50),
    source_url TEXT,
    language VARCHAR(10) DEFAULT 'en',
    page_count INTEGER,
    extraction_status VARCHAR(50) DEFAULT 'UNKNOWN',
    source_hash VARCHAR(64) NOT NULL,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(source_hash)
);

CREATE INDEX IF NOT EXISTS idx_documents_v2_domain ON documents_v2(domain);
CREATE INDEX IF NOT EXISTS idx_documents_v2_jurisdiction ON documents_v2(jurisdiction);
CREATE INDEX IF NOT EXISTS idx_documents_v2_source_hash ON documents_v2(source_hash);

-- V2 Chunks table
CREATE TABLE IF NOT EXISTS chunks_v2 (
    chunk_id VARCHAR(255) PRIMARY KEY,
    document_id VARCHAR(255) NOT NULL,
    document_version VARCHAR(255) NOT NULL,
    ordinal INTEGER,
    text TEXT NOT NULL,
    domain VARCHAR(255) DEFAULT 'UNKNOWN',
    source VARCHAR(512) NOT NULL,
    title VARCHAR(512) NOT NULL,
    section VARCHAR(255),
    subsection VARCHAR(255),
    provision_type VARCHAR(50),
    provision_number VARCHAR(50),
    page_start VARCHAR(50),
    page_end VARCHAR(50),
    jurisdiction VARCHAR(255) DEFAULT 'UNKNOWN',
    document_type VARCHAR(255) DEFAULT 'UNKNOWN',
    authority VARCHAR(255) DEFAULT 'UNKNOWN',
    language VARCHAR(10) DEFAULT 'en',
    content_hash VARCHAR(64) NOT NULL,
    token_count INTEGER,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (document_id) REFERENCES documents_v2(document_id) ON DELETE CASCADE,
    UNIQUE(content_hash)
);

CREATE INDEX IF NOT EXISTS idx_chunks_v2_document_id ON chunks_v2(document_id);
CREATE INDEX IF NOT EXISTS idx_chunks_v2_domain ON chunks_v2(domain);
CREATE INDEX IF NOT EXISTS idx_chunks_v2_jurisdiction ON chunks_v2(jurisdiction);
CREATE INDEX IF NOT EXISTS idx_chunks_v2_section ON chunks_v2(section);
CREATE INDEX IF NOT EXISTS idx_chunks_v2_content_hash ON chunks_v2(content_hash);

-- V2 Embeddings table (with pgvector)
CREATE TABLE IF NOT EXISTS embeddings_v2 (
    embedding_id SERIAL PRIMARY KEY,
    chunk_id VARCHAR(255) NOT NULL UNIQUE,
    document_id VARCHAR(255) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    embedding_model VARCHAR(255) NOT NULL,
    embedding_version VARCHAR(50) NOT NULL,
    embedding vector(1536),  -- 1536 dimensions for openai/text-embedding-3-small
    corpus_version VARCHAR(50) DEFAULT 'RAG_V2_DATASET_001',
    similarity_metric VARCHAR(50) DEFAULT 'cosine',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (chunk_id) REFERENCES chunks_v2(chunk_id) ON DELETE CASCADE,
    FOREIGN KEY (document_id) REFERENCES documents_v2(document_id) ON DELETE CASCADE,
    UNIQUE(chunk_id, embedding_model, embedding_version)
);

-- Vector index for fast similarity search
CREATE INDEX IF NOT EXISTS idx_embeddings_v2_vector
    ON embeddings_v2
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

CREATE INDEX IF NOT EXISTS idx_embeddings_v2_chunk_id ON embeddings_v2(chunk_id);
CREATE INDEX IF NOT EXISTS idx_embeddings_v2_document_id ON embeddings_v2(document_id);
CREATE INDEX IF NOT EXISTS idx_embeddings_v2_model_version ON embeddings_v2(embedding_model, embedding_version);

-- Retrieval logs for tracking queries
CREATE TABLE IF NOT EXISTS retrieval_logs_v2 (
    log_id SERIAL PRIMARY KEY,
    query_text TEXT NOT NULL,
    retrieval_type VARCHAR(50) DEFAULT 'vector',
    corpus_version VARCHAR(50) DEFAULT 'RAG_V2_DATASET_001',
    retrieved_chunk_ids TEXT[] DEFAULT '{}',
    retrieved_count INTEGER DEFAULT 0,
    query_embedding_model VARCHAR(255),
    top_k INTEGER DEFAULT 5,
    similarity_threshold FLOAT DEFAULT 0.0,
    latency_ms FLOAT,
    user_id VARCHAR(255),
    session_id VARCHAR(255),
    feedback_score FLOAT,
    feedback_text TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_retrieval_logs_v2_corpus_version ON retrieval_logs_v2(corpus_version);
CREATE INDEX IF NOT EXISTS idx_retrieval_logs_v2_created_at ON retrieval_logs_v2(created_at);
CREATE INDEX IF NOT EXISTS idx_retrieval_logs_v2_user_id ON retrieval_logs_v2(user_id);

-- Embedding manifest (tracks ingestion status)
CREATE TABLE IF NOT EXISTS embedding_manifests_v2 (
    manifest_id SERIAL PRIMARY KEY,
    corpus_version VARCHAR(50) NOT NULL,
    embedding_model VARCHAR(255) NOT NULL,
    embedding_version VARCHAR(50) NOT NULL,
    embedding_dimension INTEGER NOT NULL,
    similarity_metric VARCHAR(50) NOT NULL,
    total_chunks INTEGER,
    successfully_embedded INTEGER,
    failed_chunks INTEGER,
    skipped_chunks INTEGER,
    throughput_chunks_per_second FLOAT,
    elapsed_seconds FLOAT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(corpus_version, embedding_model, embedding_version)
);

-- Grants for service role and anon role
-- (Adjust role names based on your actual PostgreSQL setup)
GRANT SELECT ON ALL TABLES IN SCHEMA public TO anon;
GRANT SELECT, INSERT, UPDATE ON embedding_logs_v2 TO anon;
GRANT SELECT, INSERT ON retrieval_logs_v2 TO service_role;
"""

# Python module for schema management
import logging
from typing import Any

logger = logging.getLogger(__name__)


class V2SchemaManager:
    """Manages V2 database schema."""

    EMBEDDING_DIMENSION = 1536  # Must match OpenAI text-embedding-3-small
    SIMILARITY_METRIC = "cosine"

    def __init__(self, db_connection):
        """Initialize with database connection."""
        self.db = db_connection

    def verify_pgvector_available(self) -> bool:
        """Check if pgvector extension is available."""
        try:
            result = self.db.execute("SELECT extversion FROM pg_extension WHERE extname = 'vector'")
            version = result.fetchone()
            if version:
                logger.info(f"pgvector is available (version {version[0]})")
                return True
            else:
                logger.warning("pgvector extension not found. It may need to be installed.")
                return False
        except Exception as e:
            logger.error(f"Error checking pgvector availability: {e}")
            return False

    def verify_schema_v2_exists(self) -> bool:
        """Verify V2 schema tables exist."""
        tables_to_check = [
            "documents_v2",
            "chunks_v2",
            "embeddings_v2",
            "retrieval_logs_v2",
            "embedding_manifests_v2",
        ]

        for table_name in tables_to_check:
            try:
                result = self.db.execute(
                    f"SELECT 1 FROM information_schema.tables WHERE table_name = '{table_name}'"
                )
                if not result.fetchone():
                    logger.warning(f"Table {table_name} does not exist")
                    return False
            except Exception as e:
                logger.error(f"Error checking table {table_name}: {e}")
                return False

        logger.info("All V2 schema tables exist")
        return True

    def verify_embedding_dimension(self) -> bool:
        """Verify embedding column has correct dimension."""
        try:
            result = self.db.execute(
                "SELECT data_type FROM information_schema.columns WHERE table_name = 'embeddings_v2' AND column_name = 'embedding'"
            )
            row = result.fetchone()
            if row and "1536" in str(row[0]):
                logger.info("Embedding dimension is correct (1536)")
                return True
            else:
                logger.error(f"Embedding dimension mismatch. Expected 1536, got {row}")
                return False
        except Exception as e:
            logger.error(f"Error verifying embedding dimension: {e}")
            return False

    def insert_document(self, doc: dict[str, Any]) -> None:
        """Insert document metadata."""
        self.db.execute(
            """
            INSERT INTO documents_v2 (
                document_id, document_version, source, title, domain, jurisdiction,
                document_type, authority, publication_date, effective_date, source_url,
                language, page_count, extraction_status, source_hash, metadata
            ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            ON CONFLICT (document_id) DO UPDATE SET
                updated_at = CURRENT_TIMESTAMP
            """,
            (
                doc.get("document_id"),
                doc.get("document_version"),
                doc.get("source"),
                doc.get("title"),
                doc.get("domain", "UNKNOWN"),
                doc.get("jurisdiction", "UNKNOWN"),
                doc.get("document_type", "UNKNOWN"),
                doc.get("authority", "UNKNOWN"),
                doc.get("publication_date"),
                doc.get("effective_date"),
                doc.get("source_url"),
                doc.get("language", "en"),
                doc.get("page_count"),
                doc.get("extraction_status", "UNKNOWN"),
                doc.get("source_hash"),
                doc.get("metadata", {}),
            ),
        )

    def insert_chunk(self, chunk: dict[str, Any]) -> None:
        """Insert chunk metadata."""
        self.db.execute(
            """
            INSERT INTO chunks_v2 (
                chunk_id, document_id, document_version, ordinal, text, domain, source,
                title, section, subsection, provision_type, provision_number,
                page_start, page_end, jurisdiction, document_type, authority, language,
                content_hash, token_count, metadata
            ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            ON CONFLICT (chunk_id) DO UPDATE SET
                updated_at = CURRENT_TIMESTAMP
            """,
            (
                chunk.get("chunk_id"),
                chunk.get("document_id"),
                chunk.get("document_version"),
                chunk.get("chunk_index", 0),
                chunk.get("text"),
                chunk.get("domain", "UNKNOWN"),
                chunk.get("source"),
                chunk.get("title"),
                chunk.get("section"),
                chunk.get("subsection"),
                chunk.get("provision_type"),
                chunk.get("provision_number"),
                chunk.get("page_start"),
                chunk.get("page_end"),
                chunk.get("jurisdiction", "UNKNOWN"),
                chunk.get("document_type", "UNKNOWN"),
                chunk.get("authority", "UNKNOWN"),
                chunk.get("language", "en"),
                chunk.get("content_hash"),
                chunk.get("token_count", 0),
                chunk.get("metadata", {}),
            ),
        )

    def insert_embedding(self, embedding_record: dict[str, Any]) -> None:
        """Insert embedding vector."""
        self.db.execute(
            """
            INSERT INTO embeddings_v2 (
                chunk_id, document_id, content_hash, embedding_model, embedding_version,
                embedding, corpus_version, similarity_metric
            ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
            ON CONFLICT (chunk_id, embedding_model, embedding_version) DO UPDATE SET
                updated_at = CURRENT_TIMESTAMP
            """,
            (
                embedding_record.get("chunk_id"),
                embedding_record.get("document_id"),
                embedding_record.get("content_hash"),
                embedding_record.get("embedding_model"),
                embedding_record.get("embedding_version"),
                embedding_record.get("embedding"),  # pgvector accepts list[float]
                embedding_record.get("corpus_version"),
                embedding_record.get("similarity_metric", "cosine"),
            ),
        )
