from __future__ import annotations

import re
from typing import Any

from app.models import Evidence, QueryAnalysis


def _normalize(rows: list[dict[str, Any]], field: str) -> dict[str, float]:
    maximum = max((float(row.get(field, 0.0)) for row in rows), default=0.0)
    return {row["chunk_id"]: (float(row.get(field, 0.0)) / maximum if maximum else 0.0) for row in rows}


class HybridRetriever:
    """Fusion = 0.55 vector + 0.35 lexical + 0.10 metadata relevance."""

    def __init__(self, store: Any, candidate_k: int = 24):
        self.store = store
        self.candidate_k = candidate_k

    def retrieve(self, analysis: QueryAnalysis) -> list[Evidence]:
        if not analysis.domains and (analysis.out_of_scope or analysis.ambiguous):
            return []
        vector = self.store.vector_search(analysis, self.candidate_k)
        lexical = self.store.keyword_search(analysis, self.candidate_k)
        if analysis.intent == "difference" and len(set(analysis.domains)) >= 2:
            for domain in dict.fromkeys(analysis.domains):
                domain_analysis = analysis.model_copy(update={"domains": [domain]})
                vector.extend(self.store.vector_search(domain_analysis, max(6, self.candidate_k // 2)))
                lexical.extend(self.store.keyword_search(domain_analysis, max(6, self.candidate_k // 2)))
        vector_scores = _normalize(vector, "vector_score")
        lexical_scores = _normalize(lexical, "lexical_score")
        combined = {row["chunk_id"]: dict(row) for row in vector + lexical}
        query_lower = analysis.query.lower()
        for chunk_id, row in combined.items():
            metadata = 0.0
            if row.get("domain") in analysis.domains:
                metadata += 0.5
            if row.get("jurisdiction") == analysis.jurisdiction.value or analysis.jurisdiction.value == "BOTH":
                metadata += 0.3
            identifiers = analysis.legal_identifiers
            if identifiers and any(identifier.lower() in row.get("text", "").lower() for identifier in identifiers):
                metadata += 0.2
            row["vector_score"] = vector_scores.get(chunk_id, 0.0)
            row["lexical_score"] = lexical_scores.get(chunk_id, 0.0)
            row["fusion_score"] = 0.55 * row["vector_score"] + 0.35 * row["lexical_score"] + 0.10 * metadata
            row["reranker_score"] = 0.0
            row.setdefault("document_version", row.get("document_version", "unknown"))
            row.setdefault("source_status", "UNVERIFIED")
            row.setdefault("language", "en")
            for field in ("chapter", "section", "subsection", "rule_number", "sub_rule", "regulation_number", "article_number", "paragraph_number", "clause", "page_start", "page_end"):
                row.setdefault(field, None)
            row["text"] = re.sub(r"\s+", " ", row["text"]).strip()
        return [Evidence.model_validate(row) for row in sorted(combined.values(), key=lambda item: item["fusion_score"], reverse=True)]
