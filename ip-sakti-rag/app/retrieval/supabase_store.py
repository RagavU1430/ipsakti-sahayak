from __future__ import annotations

from typing import Any

from app.core.db import SupabaseRAGStore
from app.models import Jurisdiction, QueryAnalysis
from app.retrieval.embeddings import EmbeddingProvider


class SupabaseCorpusStore:
    def __init__(self, database: SupabaseRAGStore, embeddings: EmbeddingProvider):
        self.database = database
        self.embeddings = embeddings

    @staticmethod
    def _filters(analysis: QueryAnalysis) -> dict[str, Any]:
        return {
            "jurisdiction": None if analysis.jurisdiction == Jurisdiction.BOTH else analysis.jurisdiction.value,
            "domains": analysis.domains or None,
            "language": analysis.language,
        }

    @staticmethod
    def _defaults(row: dict[str, Any]) -> dict[str, Any]:
        row = dict(row)
        row.setdefault("document_version", "unknown")
        row.setdefault("language", "en")
        row.setdefault("source_status", "UNVERIFIED")
        row.setdefault("text_uncertain", False)
        return row

    def vector_search(self, analysis: QueryAnalysis, count: int) -> list[dict[str, Any]]:
        vector = self.embeddings.embed([analysis.retrieval_query])[0]
        rows = self.database.vector_search(vector, count, 0.0, self._filters(analysis))
        return [{**self._defaults(row), "vector_score": float(row.pop("similarity", 0.0))} for row in rows]

    def keyword_search(self, analysis: QueryAnalysis, count: int) -> list[dict[str, Any]]:
        rows = self.database.keyword_search(analysis.retrieval_query, count, self._filters(analysis))
        return [{**self._defaults(row), "lexical_score": float(row.get("lexical_score", 0.0))} for row in rows]
