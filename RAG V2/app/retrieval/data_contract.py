"""
Phase 1: Canonical Retrieval Result Data Contract

Every retrieval stage uses this unified structure to preserve provenance.
No data is lost as results pass through vector → lexical → hybrid → reranking stages.
"""

from dataclasses import dataclass, field
from typing import Optional
from enum import Enum


class RetrievalMethod(str, Enum):
    """How a result was retrieved."""
    VECTOR = "vector"
    LEXICAL = "lexical"
    HYBRID = "hybrid"
    RERANKED = "reranked"


@dataclass
class CanonicalRetrievalResult:
    """
    Unified result format across all retrieval stages.

    Preserves provenance, metadata, and retrieval source information.
    """

    # Chunk identification
    chunk_id: str
    document_id: str
    content: str

    # Metadata (preserved from corpus)
    domain: str
    source: str
    title: str
    section: str

    # Provenance (where to find original)
    provenance: dict = field(default_factory=dict)

    # Retrieval scores
    score: float = 0.0  # Normalized 0.0-1.0

    # Retrieval method and intermediate scores
    retrieval_method: RetrievalMethod = RetrievalMethod.VECTOR
    vector_score: Optional[float] = None  # Raw vector similarity (0-1)
    lexical_score: Optional[float] = None  # Raw lexical score (0-1)
    reranking_score: Optional[float] = None  # Reranker score (0-1)

    # Ranking across all retrieved results
    ranking: Optional[int] = None

    # Metadata preservation
    token_count: int = 0
    content_hash: str = ""
    document_version: str = ""

    def __post_init__(self):
        """Validate and initialize provenance."""
        if not self.provenance:
            self.provenance = {
                "chunk_id": self.chunk_id,
                "document_id": self.document_id,
                "source": self.source,
                "title": self.title,
                "domain": self.domain,
            }

    def to_dict(self) -> dict:
        """Convert to dictionary for serialization."""
        return {
            "chunk_id": self.chunk_id,
            "document_id": self.document_id,
            "content": self.content,
            "domain": self.domain,
            "source": self.source,
            "title": self.title,
            "section": self.section,
            "provenance": self.provenance,
            "score": self.score,
            "retrieval_method": self.retrieval_method.value,
            "vector_score": self.vector_score,
            "lexical_score": self.lexical_score,
            "reranking_score": self.reranking_score,
            "ranking": self.ranking,
            "token_count": self.token_count,
            "content_hash": self.content_hash,
            "document_version": self.document_version,
        }
