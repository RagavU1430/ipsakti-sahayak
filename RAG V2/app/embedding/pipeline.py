"""
RAG V2 Embedding Pipeline

Implements embedding generation for V2 corpus chunks.
Features:
- Batch processing with configurable sizes
- Retry with exponential backoff
- Resume capability (idempotent, tracks progress)
- Failed item tracking
- Deterministic chunk-to-vector mapping
"""

from __future__ import annotations

import asyncio
import hashlib
import json
import logging
import time
from dataclasses import dataclass, field, asdict
from datetime import datetime
from pathlib import Path
from typing import Any, AsyncGenerator, Sequence

logger = logging.getLogger(__name__)


@dataclass
class EmbeddingRecord:
    """Single embedding record with provenance."""
    chunk_id: str
    content_hash: str
    embedding_model: str
    embedding_version: str
    embedding: list[float]
    corpus_version: str
    created_at: str = field(default_factory=lambda: datetime.utcnow().isoformat() + "Z")

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass
class EmbeddingPipelineStats:
    """Statistics for embedding pipeline execution."""
    total_chunks: int
    successfully_embedded: int
    failed_chunks: int
    skipped_chunks: int  # Already embedded
    elapsed_seconds: float
    throughput_chunks_per_second: float = field(init=False)

    def __post_init__(self):
        self.throughput_chunks_per_second = (
            self.successfully_embedded / self.elapsed_seconds
            if self.elapsed_seconds > 0
            else 0.0
        )

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


class EmbeddingPipeline:
    """V2 Embedding pipeline with resume capability."""

    def __init__(
        self,
        chunk_source_path: Path | str,
        output_dir: Path | str,
        embedding_provider,  # EmbeddingProvider protocol
        config,  # EmbeddingConfig
    ):
        self.chunk_source_path = Path(chunk_source_path)
        self.output_dir = Path(output_dir)
        self.embedding_provider = embedding_provider
        self.config = config

        # Output files
        self.output_dir.mkdir(parents=True, exist_ok=True)
        self.embeddings_file = self.output_dir / "embeddings_v2.jsonl"
        self.failed_items_file = self.output_dir / "failed_embeddings.jsonl"
        self.manifest_file = self.output_dir / "embedding_manifest.json"
        self.progress_file = self.output_dir / "embedding_progress.json"

        # State tracking
        self.processed_chunk_ids: set[str] = set()
        self.failed_chunk_ids: set[str] = set()
        self.start_time: float | None = None

    def _load_progress(self) -> dict[str, Any]:
        """Load resumable progress from disk."""
        if not self.progress_file.exists():
            return {"processed": [], "failed": []}

        try:
            with open(self.progress_file, "r", encoding="utf-8") as f:
                progress = json.load(f)
                logger.info(f"Resuming from {len(progress.get('processed', []))} processed chunks")
                return progress
        except Exception as e:
            logger.warning(f"Could not load progress: {e}. Starting fresh.")
            return {"processed": [], "failed": []}

    def _save_progress(self) -> None:
        """Save progress to disk for resumability."""
        progress = {
            "processed": list(self.processed_chunk_ids),
            "failed": list(self.failed_chunk_ids),
            "timestamp": datetime.utcnow().isoformat() + "Z",
        }
        with open(self.progress_file, "w", encoding="utf-8") as f:
            json.dump(progress, f, indent=2)

    def _load_chunks(self) -> list[dict[str, Any]]:
        """Load chunks from JSONL source."""
        chunks = []
        with open(self.chunk_source_path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line:
                    chunks.append(json.loads(line))
        logger.info(f"Loaded {len(chunks)} chunks from {self.chunk_source_path}")
        return chunks

    def _chunks_to_embed(self, chunks: list[dict[str, Any]]) -> list[dict[str, Any]]:
        """Filter to chunks not yet embedded."""
        filtered = [c for c in chunks if c.get("chunk_id") not in self.processed_chunk_ids]
        logger.info(f"Found {len(filtered)} chunks to embed (skipping {len(chunks) - len(filtered)} already done)")
        return filtered

    def _embed_with_retry(self, texts: list[str], batch_index: int) -> list[list[float]] | None:
        """Embed texts with exponential backoff retry."""
        delay = 1.0
        for attempt in range(self.config.max_retries + 1):
            try:
                logger.debug(f"Batch {batch_index}: Embedding {len(texts)} texts (attempt {attempt + 1})")
                vectors = self.embedding_provider.embed(texts)

                # Validate
                if len(vectors) != len(texts):
                    raise ValueError(
                        f"Embedding count mismatch: expected {len(texts)}, got {len(vectors)}"
                    )
                for i, vec in enumerate(vectors):
                    if len(vec) != self.config.dimension:
                        raise ValueError(
                            f"Vector {i} dimension mismatch: expected {self.config.dimension}, got {len(vec)}"
                        )

                logger.debug(f"Batch {batch_index}: Successfully embedded {len(texts)} texts")
                return vectors

            except Exception as e:
                if attempt < self.config.max_retries:
                    logger.warning(
                        f"Batch {batch_index}: Embedding failed (attempt {attempt + 1}/{self.config.max_retries + 1}): {e}. "
                        f"Retrying in {delay:.1f}s..."
                    )
                    time.sleep(delay)
                    delay *= self.config.retry_backoff_base
                else:
                    logger.error(f"Batch {batch_index}: Embedding failed after {self.config.max_retries + 1} attempts: {e}")
                    return None

    def _batch_chunks(
        self, chunks: list[dict[str, Any]]
    ) -> AsyncGenerator[tuple[int, list[dict[str, Any]]], None]:
        """Yield batches of chunks for embedding."""
        for batch_index in range(0, len(chunks), self.config.batch_size):
            batch = chunks[batch_index : batch_index + self.config.batch_size]
            yield batch_index // self.config.batch_size, batch

    async def run(self) -> EmbeddingPipelineStats:
        """Run the embedding pipeline."""
        self.start_time = time.time()

        # Load progress
        progress = self._load_progress()
        self.processed_chunk_ids = set(progress.get("processed", []))
        self.failed_chunk_ids = set(progress.get("failed", []))

        # Load and filter chunks
        all_chunks = self._load_chunks()
        chunks_to_embed = self._chunks_to_embed(all_chunks)

        successfully_embedded = 0
        failed_count = 0

        # Process batches
        with open(self.embeddings_file, "a", encoding="utf-8") as emb_f, \
             open(self.failed_items_file, "a", encoding="utf-8") as fail_f:

            async for batch_index, chunk_batch in self._batch_chunks(chunks_to_embed):
                # Extract text
                texts = [c.get("text", "") for c in chunk_batch]

                # Embed with retry
                vectors = self._embed_with_retry(texts, batch_index)

                if vectors is None:
                    # Batch failed
                    for chunk in chunk_batch:
                        chunk_id = chunk.get("chunk_id")
                        self.failed_chunk_ids.add(chunk_id)
                        fail_f.write(
                            json.dumps({
                                "chunk_id": chunk_id,
                                "error": "batch_embedding_failed",
                                "timestamp": datetime.utcnow().isoformat() + "Z",
                            })
                            + "\n"
                        )
                    failed_count += len(chunk_batch)
                    continue

                # Success: write embeddings
                for chunk, vector in zip(chunk_batch, vectors):
                    chunk_id = chunk.get("chunk_id")
                    content = chunk.get("text", "")
                    content_hash = hashlib.sha256(content.encode("utf-8")).hexdigest()

                    record = EmbeddingRecord(
                        chunk_id=chunk_id,
                        content_hash=content_hash,
                        embedding_model=self.config.model,
                        embedding_version=self.config.embedding_version,
                        embedding=vector,
                        corpus_version=self.config.corpus_version,
                    )

                    emb_f.write(json.dumps(record.to_dict()) + "\n")
                    self.processed_chunk_ids.add(chunk_id)
                    successfully_embedded += 1

                # Save progress periodically
                if (batch_index + 1) % 10 == 0:  # Every 10 batches
                    self._save_progress()
                    logger.info(
                        f"Progress: {successfully_embedded + failed_count}/{len(chunks_to_embed)} chunks "
                        f"({100 * (successfully_embedded + failed_count) / len(chunks_to_embed):.1f}%)"
                    )

                # Rate limiting
                if self.config.rate_limit_per_minute:
                    batch_delay = 60.0 / self.config.rate_limit_per_minute
                    await asyncio.sleep(batch_delay)

        # Final save
        self._save_progress()

        # Generate manifest
        elapsed = time.time() - self.start_time
        stats = EmbeddingPipelineStats(
            total_chunks=len(all_chunks),
            successfully_embedded=successfully_embedded,
            failed_chunks=failed_count,
            skipped_chunks=len(all_chunks) - len(chunks_to_embed),
            elapsed_seconds=elapsed,
        )

        self._write_manifest(stats)
        return stats

    def _write_manifest(self, stats: EmbeddingPipelineStats) -> None:
        """Write embedding manifest."""
        manifest = {
            "corpus_version": self.config.corpus_version,
            "embedding_model": self.config.model,
            "embedding_version": self.config.embedding_version,
            "embedding_dimension": self.config.dimension,
            "similarity_metric": self.config.similarity_metric,
            "total_chunks": stats.total_chunks,
            "successfully_embedded": stats.successfully_embedded,
            "failed_chunks": stats.failed_chunks,
            "skipped_chunks": stats.skipped_chunks,
            "throughput_chunks_per_second": stats.throughput_chunks_per_second,
            "elapsed_seconds": stats.elapsed_seconds,
            "created_at": datetime.utcnow().isoformat() + "Z",
            "embeddings_file": str(self.embeddings_file),
            "failed_items_file": str(self.failed_items_file),
            "progress_file": str(self.progress_file),
        }

        with open(self.manifest_file, "w", encoding="utf-8") as f:
            json.dump(manifest, f, indent=2)

        logger.info(f"Embedding manifest written to {self.manifest_file}")
