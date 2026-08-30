from __future__ import annotations

import re
from typing import Protocol

from app.models import Evidence, QueryAnalysis


class Reranker(Protocol):
    name: str
    learned: bool

    def rerank(self, analysis: QueryAnalysis, candidates: list[Evidence], final_count: int) -> list[Evidence]: ...


class LegalFeatureReranker:
    """Deterministic fallback, explicitly not a learned reranker."""

    name = "legal-feature-reranker-v1"
    learned = False

    def rerank(self, analysis: QueryAnalysis, candidates: list[Evidence], final_count: int) -> list[Evidence]:
        query_tokens = set(re.findall(r"[a-z0-9]+", analysis.query.lower()))
        for item in candidates:
            text = item.text.lower()
            coverage = sum(token in text for token in query_tokens) / max(len(query_tokens), 1)
            identifier = 1.0 if any(identifier.lower() in text for identifier in analysis.legal_identifiers) else 0.0
            verified = 1.0 if item.source_status == "VERIFIED" else 0.0
            item.reranker_score = 0.65 * item.fusion_score + 0.20 * coverage + 0.10 * identifier + 0.05 * verified
        return sorted(candidates, key=lambda item: item.reranker_score, reverse=True)[:final_count]
