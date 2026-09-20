"""
Phase 10: Domain-Aware Retrieval

Uses query understanding to optionally prioritize domain-specific evidence.
Falls back gracefully when confidence is low to prevent false negatives.
"""

import logging
from typing import Optional

logger = logging.getLogger(__name__)


class DomainAwareRetrieval:
    """Domain-aware candidate filtering and prioritization."""

    def __init__(self, hybrid_retrieval):
        """Initialize with hybrid retrieval."""
        self.hybrid_retrieval = hybrid_retrieval
        logger.info("Domain-aware retrieval initialized")

    async def search(self, query: str, query_understanding) -> list:
        """
        Execute domain-aware retrieval.

        Process:
        1. Execute hybrid retrieval
        2. If high domain confidence: prioritize domain matches
        3. If low confidence: fall back to pure hybrid ranking
        4. Preserve metadata
        """

        logger.info(f"Domain-aware search: primary_domain={query_understanding.primary_domain}, confidence={query_understanding.domain_confidence:.2f}")

        # Execute hybrid retrieval
        hybrid_results = await self.hybrid_retrieval.search(query)

        if not hybrid_results:
            logger.info("No hybrid results, returning empty")
            return []

        # If domain confidence is too low, return hybrid results as-is
        if query_understanding.domain_confidence < 0.4:
            logger.info(f"Domain confidence too low ({query_understanding.domain_confidence:.2f}), returning hybrid results without filtering")
            return hybrid_results

        # High confidence: attempt domain filtering
        logger.info(f"High confidence domain detection, attempting domain filtering")

        # Separate results by domain match
        primary_domain_matches = []
        secondary_domain_matches = []
        other_domain_matches = []

        primary_domain_str = query_understanding.primary_domain.value.upper()
        secondary_domain_strs = {d.value.upper() for d in query_understanding.secondary_domains}

        for result in hybrid_results:
            result_domain = result.get("domain", "UNKNOWN").upper()

            if result_domain == "UNKNOWN":
                # Can't filter unknown domains, include but deprioritize
                other_domain_matches.append(result)
            elif result_domain == primary_domain_str:
                primary_domain_matches.append(result)
            elif result_domain in secondary_domain_strs:
                secondary_domain_matches.append(result)
            else:
                other_domain_matches.append(result)

        logger.info(f"Domain filtering: {len(primary_domain_matches)} primary, {len(secondary_domain_matches)} secondary, {len(other_domain_matches)} other")

        # If primary domain has results, use it; otherwise fall back
        if len(primary_domain_matches) > 0:
            logger.info(f"Using {len(primary_domain_matches)} primary domain results")
            results = primary_domain_matches + secondary_domain_matches + other_domain_matches
        else:
            logger.info("No primary domain matches, falling back to all results")
            results = hybrid_results

        # Re-rank after domain filtering
        for ranking, result in enumerate(results, 1):
            result["ranking"] = ranking
            result["domain_filtered"] = True

        return results


class DomainFallbackRetrieval:
    """Graceful fallback when domain detection fails."""

    @staticmethod
    def should_fallback(query_understanding) -> bool:
        """Determine if we should fall back from domain filtering."""
        # Fall back if:
        # - Domain confidence is low
        # - Intent is ambiguous (multiple equally likely intents)
        # - Query is cross-domain
        # - Domain is GENERAL

        if query_understanding.domain_confidence < 0.3:
            return True

        if query_understanding.is_ambiguous:
            return True

        if len(query_understanding.secondary_domains) > 1:
            return True

        return False
