"""
RAG V2 Embedding Configuration

Centralized configuration for V2 embedding pipeline.
Manages model selection, batch parameters, retry logic, and provider settings.
"""

from __future__ import annotations

import os
from dataclasses import dataclass
from enum import Enum


class EmbeddingProvider(str, Enum):
    """Supported embedding providers."""
    OPENROUTER = "openrouter"
    HASH = "hash"  # Deterministic test fallback


@dataclass(frozen=True)
class EmbeddingConfig:
    """V2 embedding configuration."""

    # Model and provider
    provider: EmbeddingProvider
    model: str
    dimension: int

    # Batch parameters
    batch_size: int
    max_batch_items: int  # Provider limit (e.g., 2048 for OpenRouter)

    # Timeout and retry
    timeout_seconds: int
    max_retries: int
    retry_backoff_base: float  # Exponential backoff multiplier

    # Rate limiting
    rate_limit_per_minute: int | None
    concurrent_requests: int

    # Normalization
    normalize_vectors: bool

    # Similarity metric
    similarity_metric: str  # "cosine", "inner_product", "l2"

    # Corpus version tracking
    corpus_version: str
    embedding_version: str

    @classmethod
    def from_environment(cls) -> EmbeddingConfig:
        """Load configuration from environment variables."""

        provider_str = os.getenv("V2_EMBEDDING_PROVIDER", "openrouter").lower()
        try:
            provider = EmbeddingProvider(provider_str)
        except ValueError:
            raise ValueError(f"Invalid V2_EMBEDDING_PROVIDER: {provider_str}. Must be one of: {', '.join(p.value for p in EmbeddingProvider)}")

        model = os.getenv("V2_EMBEDDING_MODEL", "openai/text-embedding-3-small")
        dimension = int(os.getenv("V2_EMBEDDING_DIMENSION", "1536"))

        batch_size = int(os.getenv("V2_EMBEDDING_BATCH_SIZE", "100"))
        max_batch_items = int(os.getenv("V2_EMBEDDING_MAX_BATCH_ITEMS", "2048"))

        timeout_seconds = int(os.getenv("V2_EMBEDDING_TIMEOUT_SECONDS", "60"))
        max_retries = int(os.getenv("V2_EMBEDDING_MAX_RETRIES", "3"))
        retry_backoff_base = float(os.getenv("V2_EMBEDDING_RETRY_BACKOFF_BASE", "2.0"))

        rate_limit_per_minute = os.getenv("V2_EMBEDDING_RATE_LIMIT_PER_MINUTE")
        rate_limit_per_minute = int(rate_limit_per_minute) if rate_limit_per_minute else None

        concurrent_requests = int(os.getenv("V2_EMBEDDING_CONCURRENT_REQUESTS", "4"))

        normalize_vectors = os.getenv("V2_EMBEDDING_NORMALIZE_VECTORS", "false").lower() == "true"
        similarity_metric = os.getenv("V2_EMBEDDING_SIMILARITY_METRIC", "cosine")

        corpus_version = os.getenv("V2_CORPUS_VERSION", "RAG_V2_DATASET_001")
        embedding_version = os.getenv("V2_EMBEDDING_VERSION", "v1-2026-09-19")

        return cls(
            provider=provider,
            model=model,
            dimension=dimension,
            batch_size=batch_size,
            max_batch_items=max_batch_items,
            timeout_seconds=timeout_seconds,
            max_retries=max_retries,
            retry_backoff_base=retry_backoff_base,
            rate_limit_per_minute=rate_limit_per_minute,
            concurrent_requests=concurrent_requests,
            normalize_vectors=normalize_vectors,
            similarity_metric=similarity_metric,
            corpus_version=corpus_version,
            embedding_version=embedding_version,
        )

    def validate(self) -> None:
        """Validate configuration consistency."""
        if self.dimension <= 0:
            raise ValueError(f"dimension must be positive, got {self.dimension}")

        if self.batch_size <= 0 or self.batch_size > self.max_batch_items:
            raise ValueError(f"batch_size must be between 1 and {self.max_batch_items}, got {self.batch_size}")

        if self.timeout_seconds <= 0:
            raise ValueError(f"timeout_seconds must be positive, got {self.timeout_seconds}")

        if self.max_retries < 0:
            raise ValueError(f"max_retries must be non-negative, got {self.max_retries}")

        if self.retry_backoff_base <= 1.0:
            raise ValueError(f"retry_backoff_base must be > 1.0, got {self.retry_backoff_base}")

        if self.concurrent_requests <= 0:
            raise ValueError(f"concurrent_requests must be positive, got {self.concurrent_requests}")

        if self.similarity_metric not in ("cosine", "inner_product", "l2"):
            raise ValueError(f"similarity_metric must be one of: cosine, inner_product, l2; got {self.similarity_metric}")

    def to_dict(self) -> dict[str, any]:
        """Convert to dictionary for serialization."""
        return {
            "provider": self.provider.value,
            "model": self.model,
            "dimension": self.dimension,
            "batch_size": self.batch_size,
            "max_batch_items": self.max_batch_items,
            "timeout_seconds": self.timeout_seconds,
            "max_retries": self.max_retries,
            "retry_backoff_base": self.retry_backoff_base,
            "rate_limit_per_minute": self.rate_limit_per_minute,
            "concurrent_requests": self.concurrent_requests,
            "normalize_vectors": self.normalize_vectors,
            "similarity_metric": self.similarity_metric,
            "corpus_version": self.corpus_version,
            "embedding_version": self.embedding_version,
        }


# Global config instance (lazy-loaded)
_config: EmbeddingConfig | None = None


def get_embedding_config() -> EmbeddingConfig:
    """Get the global embedding configuration."""
    global _config
    if _config is None:
        _config = EmbeddingConfig.from_environment()
        _config.validate()
    return _config


def reset_embedding_config() -> None:
    """Reset the global configuration (for testing)."""
    global _config
    _config = None
