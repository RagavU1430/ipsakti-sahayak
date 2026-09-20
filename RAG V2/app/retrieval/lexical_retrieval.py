"""
Phase 4: Lexical Retrieval via PostgreSQL Full-Text Search

Implements lexical/keyword-based retrieval using PostgreSQL full-text search.
Complements vector retrieval for exact term matching and legal identifiers.
"""

import logging
import time
from dataclasses import dataclass
from typing import Optional
from enum import Enum

logger = logging.getLogger(__name__)


class LexicalMatchType(str, Enum):
    """Type of lexical match."""
    EXACT = "exact"  # Exact term match
    PHRASE = "phrase"  # Phrase match
    PARTIAL = "partial"  # Substring match
    SEMANTIC = "semantic"  # Full-text search match


@dataclass
class LexicalResult:
    """Single lexical search result."""
    chunk_id: str
    document_id: str
    content: str
    domain: str
    source: str
    title: str
    section: str
    lexical_score: float  # 0.0-1.0 normalized
    match_type: LexicalMatchType
    matched_terms: list[str]  # Which terms matched
    ranking: int = 0


class LexicalRetrieval:
    """PostgreSQL-based lexical retrieval."""

    def __init__(self, db_connection):
        """Initialize with database connection."""
        self.db = db_connection
        logger.info("Lexical retrieval initialized (PostgreSQL full-text search)")

    async def search(
        self,
        query: str,
        top_k: int = 10,
        match_type: LexicalMatchType = LexicalMatchType.SEMANTIC,
    ) -> list[LexicalResult]:
        """
        Execute lexical search.

        Args:
            query: Search query
            top_k: Maximum results
            match_type: Type of lexical matching to use

        Returns:
            List of LexicalResult ranked by relevance
        """

        logger.info(f"Lexical search: query='{query[:100]}...', top_k={top_k}, match_type={match_type.value}")

        start_time = time.time()

        try:
            if match_type == LexicalMatchType.EXACT:
                results = await self._exact_search(query, top_k)
            elif match_type == LexicalMatchType.PHRASE:
                results = await self._phrase_search(query, top_k)
            elif match_type == LexicalMatchType.PARTIAL:
                results = await self._partial_search(query, top_k)
            else:  # SEMANTIC (full-text search)
                results = await self._full_text_search(query, top_k)

        except Exception as e:
            logger.error(f"Lexical search failed: {e}")
            raise

        elapsed_ms = (time.time() - start_time) * 1000
        logger.debug(f"Lexical search completed in {elapsed_ms:.1f}ms, returned {len(results)} results")

        # Rank results
        for ranking, result in enumerate(results, 1):
            result.ranking = ranking

        return results

    async def _exact_search(self, query: str, top_k: int) -> list[LexicalResult]:
        """Search for exact phrase match."""
        # Use PostgreSQL phrase search with quotes
        try:
            results_rows = self.db.execute(
                """
                SELECT
                    c.chunk_id,
                    c.document_id,
                    c.text,
                    c.domain,
                    c.source,
                    c.title,
                    c.section,
                    1.0 as lexical_score
                FROM chunks_v2 c
                WHERE c.text ILIKE %s
                LIMIT %s
                """,
                (f"%{query}%", top_k),
            ).fetchall()

            results = []
            for row in results_rows:
                result = LexicalResult(
                    chunk_id=row[0],
                    document_id=row[1],
                    content=row[2],
                    domain=row[3],
                    source=row[4],
                    title=row[5],
                    section=row[6],
                    lexical_score=float(row[7]),
                    match_type=LexicalMatchType.EXACT,
                    matched_terms=[query],
                )
                results.append(result)

            logger.debug(f"Exact search returned {len(results)} results")
            return results

        except Exception as e:
            logger.warning(f"Exact search failed: {e}, falling back to empty results")
            return []

    async def _phrase_search(self, query: str, top_k: int) -> list[LexicalResult]:
        """Search for phrase using PostgreSQL full-text search."""
        try:
            # Convert query to PostgreSQL phrase query
            # E.g., "section 3" -> 'section' <-> 'section' (adjacent)
            phrase_query = " <-> ".join(query.split())

            results_rows = self.db.execute(
                """
                SELECT
                    c.chunk_id,
                    c.document_id,
                    c.text,
                    c.domain,
                    c.source,
                    c.title,
                    c.section,
                    ts_rank(to_tsvector('english', c.text),
                           plainto_tsquery('english', %s)) as lexical_score
                FROM chunks_v2 c
                WHERE to_tsvector('english', c.text) @@ plainto_tsquery('english', %s)
                ORDER BY lexical_score DESC
                LIMIT %s
                """,
                (query, query, top_k),
            ).fetchall()

            results = []
            for row in results_rows:
                result = LexicalResult(
                    chunk_id=row[0],
                    document_id=row[1],
                    content=row[2],
                    domain=row[3],
                    source=row[4],
                    title=row[5],
                    section=row[6],
                    lexical_score=min(float(row[7]), 1.0),  # Normalize to 0-1
                    match_type=LexicalMatchType.PHRASE,
                    matched_terms=query.split(),
                )
                results.append(result)

            logger.debug(f"Phrase search returned {len(results)} results")
            return results

        except Exception as e:
            logger.warning(f"Phrase search failed: {e}")
            return []

    async def _partial_search(self, query: str, top_k: int) -> list[LexicalResult]:
        """Search for partial/substring matches."""
        try:
            results_rows = self.db.execute(
                """
                SELECT
                    c.chunk_id,
                    c.document_id,
                    c.text,
                    c.domain,
                    c.source,
                    c.title,
                    c.section,
                    CASE
                        WHEN c.text ILIKE %s THEN 1.0
                        WHEN c.text ILIKE %s THEN 0.7
                        ELSE 0.5
                    END as lexical_score
                FROM chunks_v2 c
                WHERE c.text ILIKE %s
                ORDER BY lexical_score DESC
                LIMIT %s
                """,
                (f"{query}%", f"% {query}%", f"%{query}%", top_k),
            ).fetchall()

            results = []
            for row in results_rows:
                result = LexicalResult(
                    chunk_id=row[0],
                    document_id=row[1],
                    content=row[2],
                    domain=row[3],
                    source=row[4],
                    title=row[5],
                    section=row[6],
                    lexical_score=float(row[7]),
                    match_type=LexicalMatchType.PARTIAL,
                    matched_terms=[query],
                )
                results.append(result)

            logger.debug(f"Partial search returned {len(results)} results")
            return results

        except Exception as e:
            logger.warning(f"Partial search failed: {e}")
            return []

    async def _full_text_search(self, query: str, top_k: int) -> list[LexicalResult]:
        """Full-text semantic search using PostgreSQL tsearch."""
        try:
            results_rows = self.db.execute(
                """
                SELECT
                    c.chunk_id,
                    c.document_id,
                    c.text,
                    c.domain,
                    c.source,
                    c.title,
                    c.section,
                    ts_rank(to_tsvector('english', c.text),
                           plainto_tsquery('english', %s)) as lexical_score
                FROM chunks_v2 c
                WHERE to_tsvector('english', c.text) @@ plainto_tsquery('english', %s)
                ORDER BY lexical_score DESC
                LIMIT %s
                """,
                (query, query, top_k),
            ).fetchall()

            results = []
            for row in results_rows:
                result = LexicalResult(
                    chunk_id=row[0],
                    document_id=row[1],
                    content=row[2],
                    domain=row[3],
                    source=row[4],
                    title=row[5],
                    section=row[6],
                    lexical_score=min(float(row[7]), 1.0),  # Normalize
                    match_type=LexicalMatchType.SEMANTIC,
                    matched_terms=query.split(),
                )
                results.append(result)

            logger.debug(f"Full-text search returned {len(results)} results")
            return results

        except Exception as e:
            logger.warning(f"Full-text search failed: {e}")
            return []
