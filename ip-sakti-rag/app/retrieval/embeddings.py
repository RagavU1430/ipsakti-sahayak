from typing import Protocol, Sequence

class EmbeddingProvider(Protocol):
    @property
    def dimension(self) -> int: ...
    def embed(self, texts: Sequence[str]) -> list[list[float]]: ...

def configured_dimension(default: int = 1536) -> int:
    import os
    return int(os.getenv("EMBEDDING_DIMENSION", str(default)))
