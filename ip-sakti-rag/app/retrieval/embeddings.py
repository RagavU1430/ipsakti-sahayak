from __future__ import annotations

import hashlib
import math
import os
import re
from typing import Protocol, Sequence


class EmbeddingProvider(Protocol):
    model: str

    @property
    def dimension(self) -> int: ...

    def embed(self, texts: Sequence[str]) -> list[list[float]]: ...


class OpenRouterEmbeddingProvider:
    def __init__(self, api_key: str | None = None, model: str | None = None, dimension: int | None = None):
        from app.core.openrouter_client import OpenRouterClient

        self.client = OpenRouterClient(api_key=api_key)
        self.model = model or os.getenv("EMBEDDING_MODEL", "openai/text-embedding-3-small")
        self._dimension = dimension or int(os.getenv("EMBEDDING_DIMENSION", "1536"))

    @property
    def dimension(self) -> int:
        return self._dimension

    def embed(self, texts: Sequence[str]) -> list[list[float]]:
        if not texts:
            return []
        vectors = self.client.embed(list(texts), model=self.model)
        _validate_vectors(vectors, len(texts), self.dimension)
        return vectors


class HashEmbeddingProvider:
    """Deterministic local/test fallback; it is not a learned embedding model."""

    model = "deterministic-hash-v1"

    def __init__(self, dimension: int = 384):
        self._dimension = dimension

    @property
    def dimension(self) -> int:
        return self._dimension

    def embed(self, texts: Sequence[str]) -> list[list[float]]:
        vectors: list[list[float]] = []
        for text in texts:
            vector = [0.0] * self.dimension
            tokens = re.findall(r"[a-z0-9]+", text.lower())
            for token in tokens + [f"{a}_{b}" for a, b in zip(tokens, tokens[1:])]:
                digest = hashlib.blake2b(token.encode(), digest_size=8).digest()
                value = int.from_bytes(digest, "big")
                index = value % self.dimension
                vector[index] += -1.0 if value & 1 else 1.0
            norm = math.sqrt(sum(value * value for value in vector))
            vectors.append([value / norm for value in vector] if norm else vector)
        return vectors


def _validate_vectors(vectors: list[list[float]], expected_count: int, expected_dimension: int) -> None:
    if len(vectors) != expected_count:
        raise ValueError(f"embedding count mismatch: expected {expected_count}, got {len(vectors)}")
    for index, vector in enumerate(vectors):
        if len(vector) != expected_dimension:
            raise ValueError(f"embedding {index} dimension mismatch: expected {expected_dimension}, got {len(vector)}")
        if not all(math.isfinite(value) for value in vector):
            raise ValueError(f"embedding {index} contains a non-finite value")
