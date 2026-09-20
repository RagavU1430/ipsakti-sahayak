"""
Phase 7: Hybrid Retrieval - Vector + Lexical Fusion

Combines vector and lexical results using reciprocal rank fusion (RRF).
RRF is robust and doesn't require tuning fusion weights.
"""

import logging
import asyncio
from dataclasses import dataclass
from typing import Optional
from enum import Enum

logger = logging.getLogger(__name__)


class FusionStrategy(str, Enum):
    """Hybrid fusion strategy."""
    RRF = "reciprocal_rank_fusion"  # Robust, no tuning needed
    WEIGHTED = "weighted_score"  # Tuned weights (default 0.6 vector, 0.4 lexical)


@dataclass
class HybridRetrievalConfig:
    """Configuration for hybrid retrieval."""
    vector_top_k: int = 20  # Get more candidates for fusion
    lexical_top_k: int = 20
    fusion_strategy: FusionStrategy = FusionStrategy.RRF
    vector_weight: float = 0.6  # For weighted fusion
    lexical_weight: float = 0.4
    final_top_k: int = 10  # Results after fusion


class HybridRetrieval:
    """Hybrid vector + lexical retrieval."""

    def __init__(self, vector_retrieval, lexical_retrieval, config: Optional[HybridRetrievalConfig] = None):
        """Initialize hybrid retrieval."""
        self.vector_retrieval = vector_retrieval
        self.lexical_retrieval = lexical_retrieval
        self.config = config or HybridRetrievalConfig()
        logger.info(f"Hybrid retrieval initialized (strategy: {self.config.fusion_strategy.value})")

    async def search(self, query: str) -> list:
        """
        Execute hybrid search: vector + lexical + fusion.

        Returns:
            List of canonical retrieval results, ranked by hybrid score
        """

        logger.info(f"Hybrid search: query='{query[:100]}...'")

        # 1. Execute vector and lexical searches in parallel
        vector_task = asyncio.create_task(
            self.vector_retrieval.search(query, top_k=self.config.vector_top_k)
        )
        lexical_task = asyncio.create_task(
            self.lexical_retrieval.search(query, top_k=self.config.lexical_top_k)
        )

        vector_results, lexical_results = await asyncio.gather(vector_task, lexical_task)

        logger.info(f"Vector search returned {len(vector_results)} results")
        logger.info(f"Lexical search returned {len(lexical_results)} results")

        # 2. Deduplicate candidates
        candidates = self._deduplicate(vector_results, lexical_results)
        logger.info(f"After deduplication: {len(candidates)} unique candidates")

        # 3. Fuse scores
        if self.config.fusion_strategy == FusionStrategy.RRF:
            fused_results = self._fuse_rrf(candidates)
        else:
            fused_results = self._fuse_weighted(candidates)

        # 4. Rank and limit
        fused_results.sort(key=lambda r: r.score, reverse=True)
        final_results = fused_results[: self.config.final_top_k]

        # 5. Re-rank with final ranking
        for ranking, result in enumerate(final_results, 1):
            result.ranking = ranking

        logger.info(f"Hybrid search returned {len(final_results)} final results")
        return final_results

    def _deduplicate(self, vector_results, lexical_results) -> dict:
        """
        Deduplicate by chunk_id.

        Returns:
            Dict mapping chunk_id to enriched result with all retrieval methods
        """

        candidates = {}

        # Add vector results
        for result in vector_results:
            chunk_id = result.chunk_id
            if chunk_id not in candidates:
                candidates[chunk_id] = {
                    "chunk_id": chunk_id,
                    "document_id": result.document_id,
                    "content": result.content,
                    "domain": result.domain,
                    "source": result.source,
                    "title": result.title,
                    "section": result.section,
                    "token_count": getattr(result, "token_count", 0),
                    "content_hash": getattr(result, "content_hash", ""),
                    "document_version": getattr(result, "document_version", ""),
                    "retrieval_methods": set(),
                    "vector_score": None,
                    "lexical_score": None,
                }
            candidates[chunk_id]["vector_score"] = result.similarity_score
            candidates[chunk_id]["retrieval_methods"].add("vector")

        # Add lexical results (merge if already present)
        for result in lexical_results:
            chunk_id = result.chunk_id
            if chunk_id not in candidates:
                candidates[chunk_id] = {
                    "chunk_id": chunk_id,
                    "document_id": result.document_id,
                    "content": result.content,
                    "domain": result.domain,
                    "source": result.source,
                    "title": result.title,
                    "section": result.section,
                    "token_count": 0,
                    "content_hash": "",
                    "document_version": "",
                    "retrieval_methods": set(),
                    "vector_score": None,
                    "lexical_score": None,
                }
            candidates[chunk_id]["lexical_score"] = result.lexical_score
            candidates[chunk_id]["retrieval_methods"].add("lexical")

        logger.debug(f"Deduplication: {len(vector_results)} vector + {len(lexical_results)} lexical → {len(candidates)} unique")

        return candidates

    def _fuse_rrf(self, candidates: dict) -> list:
        """
        Reciprocal Rank Fusion (RRF).

        RRF score = Σ 1 / (k + rank)
        where k is a constant (typically 60) and rank is 1-indexed position.

        Advantage: No tuning needed, robust to score distribution differences.
        """

        k = 60  # Standard RRF constant
        results = []

        for chunk_id, candidate in candidates.items():
            rrf_score = 0.0

            if candidate["vector_score"] is not None:
                # Find vector rank (lower score = higher rank, so invert)
                # For now, approximate as normalized score
                vector_component = 1.0 / (k + 1)  # Placeholder
                rrf_score += vector_component

            if candidate["lexical_score"] is not None:
                # Similar for lexical
                lexical_component = 1.0 / (k + 1)
                rrf_score += lexical_component

            candidate["score"] = min(rrf_score, 1.0)  # Normalize to 0-1
            candidate["retrieval_method"] = "hybrid"
            candidate["retrieval_methods"] = list(candidate["retrieval_methods"])

            results.append(candidate)

        return results

    def _fuse_weighted(self, candidates: dict) -> list:
        """
        Weighted score fusion.

        hybrid_score = vector_weight * vector_score + lexical_weight * lexical_score

        Weights should sum to 1.0. Default: 0.6 vector, 0.4 lexical.
        """

        results = []

        for chunk_id, candidate in candidates.items():
            hybrid_score = 0.0

            if candidate["vector_score"] is not None:
                hybrid_score += self.config.vector_weight * candidate["vector_score"]

            if candidate["lexical_score"] is not None:
                hybrid_score += self.config.lexical_weight * candidate["lexical_score"]

            # Normalize
            if candidate["vector_score"] is not None and candidate["lexical_score"] is not None:
                # Both present, already balanced by weights
                pass
            elif candidate["vector_score"] is not None:
                # Only vector present
                hybrid_score = candidate["vector_score"]
            elif candidate["lexical_score"] is not None:
                # Only lexical present
                hybrid_score = candidate["lexical_score"]

            candidate["score"] = min(hybrid_score, 1.0)
            candidate["retrieval_method"] = "hybrid"
            candidate["retrieval_methods"] = list(candidate["retrieval_methods"])

            results.append(candidate)

        logger.debug(f"Weighted fusion: {len(results)} results with hybrid scores")

        return results
