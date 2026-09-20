"""
Phase 12: Reranking Framework

Reranking is a second-stage ranking of candidates from hybrid retrieval.
This phase selects reranker and measures its impact on recall metrics.
"""

import logging
from enum import Enum
from abc import ABC, abstractmethod

logger = logging.getLogger(__name__)


class RerankerType(str, Enum):
    """Reranker selection options."""
    CROSS_ENCODER = "cross_encoder"  # Cross-encoder LLM-based reranking
    LIGHTWEIGHT = "lightweight"  # Lightweight model (ColBERT, etc.)
    LEXICAL_BOOSTING = "lexical_boosting"  # Boost results with lexical matches
    DOMAIN_RANKING = "domain_ranking"  # Rank by domain relevance
    NONE = "none"  # No reranking (hybrid only)


class Reranker(ABC):
    """Abstract base for reranker implementations."""

    @abstractmethod
    async def rerank(self, query: str, candidates: list, top_k: int = 10) -> list:
        """
        Rerank candidates.

        Args:
            query: Original query
            candidates: List of candidate results from hybrid retrieval
            top_k: Number of results to return

        Returns:
            Reranked results (same structure, different order)
        """
        pass


class LexicalBoostingReranker(Reranker):
    """
    Simple reranking: boost results where query terms appear in content.

    Strategy:
    1. Count exact query term matches in each candidate
    2. Add boost to score
    3. Re-rank by boosted score

    Advantage: Fast, no external LLM needed, explainable.
    Disadvantage: Simple heuristic, doesn't capture semantic relevance.
    """

    def __init__(self, boost_factor: float = 0.2):
        """Initialize with boost magnitude."""
        self.boost_factor = boost_factor
        logger.info(f"Lexical boosting reranker initialized (boost_factor={boost_factor})")

    async def rerank(self, query: str, candidates: list, top_k: int = 10) -> list:
        """Rerank by lexical match boost."""

        if not candidates:
            return []

        query_terms = set(query.lower().split())
        query_terms = {t.strip('.,;:!?') for t in query_terms if len(t) > 2}

        logger.info(f"Lexical reranking: {len(candidates)} candidates, {len(query_terms)} query terms")

        # Compute boost for each candidate
        for candidate in candidates:
            content_lower = candidate.get("content", "").lower()
            match_count = sum(1 for term in query_terms if term in content_lower)

            # Boost score based on match count
            boost = self.boost_factor * (match_count / len(query_terms)) if query_terms else 0.0
            candidate["reranking_score"] = min(candidate.get("score", 0.0) + boost, 1.0)

        # Re-rank
        candidates.sort(key=lambda c: c["reranking_score"], reverse=True)

        # Return top-K with rankings
        results = candidates[:top_k]
        for ranking, result in enumerate(results, 1):
            result["ranking"] = ranking
            result["retrieval_method"] = "reranked"

        logger.info(f"Lexical reranking complete: {len(results)} results")
        return results


class DomainRankingReranker(Reranker):
    """
    Rerank by domain relevance using document metadata.

    Strategy:
    1. Identify query domain
    2. Score results by domain match
    3. Adjust score based on domain relevance
    4. Re-rank
    """

    def __init__(self, domain_boost: float = 0.15):
        """Initialize with domain boost factor."""
        self.domain_boost = domain_boost
        logger.info(f"Domain ranking reranker initialized (domain_boost={domain_boost})")

    async def rerank(self, query: str, candidates: list, top_k: int = 10, query_domain: str = None) -> list:
        """Rerank by domain relevance."""

        if not candidates or not query_domain:
            # Fall back to no reranking if no query domain
            candidates.sort(key=lambda c: c.get("score", 0.0), reverse=True)
            results = candidates[:top_k]
            for ranking, result in enumerate(results, 1):
                result["ranking"] = ranking
            return results

        logger.info(f"Domain ranking: query_domain={query_domain}, {len(candidates)} candidates")

        # Boost scores for domain matches
        for candidate in candidates:
            candidate_domain = candidate.get("domain", "UNKNOWN").upper()

            if candidate_domain == query_domain.upper():
                # Exact domain match
                boost = self.domain_boost
            elif candidate_domain == "UNKNOWN":
                # Unknown domain - no boost, no penalty
                boost = 0.0
            else:
                # Different domain - no boost
                boost = 0.0

            candidate["reranking_score"] = min(candidate.get("score", 0.0) + boost, 1.0)

        # Re-rank
        candidates.sort(key=lambda c: c["reranking_score"], reverse=True)

        # Return top-K
        results = candidates[:top_k]
        for ranking, result in enumerate(results, 1):
            result["ranking"] = ranking
            result["retrieval_method"] = "reranked"

        logger.info(f"Domain ranking complete: {len(results)} results")
        return results


class RerankerFactory:
    """Factory for creating reranker instances."""

    _rerankers = {}

    @classmethod
    def create(cls, reranker_type: RerankerType, **kwargs) -> Reranker:
        """Create reranker instance."""

        if reranker_type == RerankerType.LEXICAL_BOOSTING:
            return LexicalBoostingReranker(**kwargs)
        elif reranker_type == RerankerType.DOMAIN_RANKING:
            return DomainRankingReranker(**kwargs)
        elif reranker_type == RerankerType.NONE:
            return None
        else:
            raise ValueError(f"Unknown reranker type: {reranker_type}")

    @classmethod
    def get_reranker(cls, reranker_type: RerankerType, **kwargs) -> Reranker:
        """Get or create cached reranker."""
        key = f"{reranker_type}:{str(kwargs)}"

        if key not in cls._rerankers:
            cls._rerankers[key] = cls.create(reranker_type, **kwargs)

        return cls._rerankers[key]
