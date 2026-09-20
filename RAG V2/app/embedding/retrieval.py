"""
RAG V2 Vector Retrieval - Basic Vector Search Implementation

Implements basic vector-similarity search for V2 corpus.
This is smoke-test retrieval only - NOT the final hybrid system.

Features:
- Query embedding generation
- pgvector similarity search
- Top-K retrieval
- Result metadata preservation
- Sanity test suite
"""

from __future__ import annotations

import asyncio
import logging
import time
from dataclasses import dataclass
from typing import Any

logger = logging.getLogger(__name__)


@dataclass
class VectorSearchResult:
    """Single vector search result."""
    chunk_id: str
    document_id: str
    title: str
    section: str
    content: str
    source: str
    domain: str
    similarity_score: float
    ranking: int


class V2VectorRetrieval:
    """Basic vector retrieval for V2 corpus."""

    def __init__(self, db_connection, embedding_provider, config):
        """Initialize retrieval with database and embedding provider."""
        self.db = db_connection
        self.embedding_provider = embedding_provider
        self.config = config

    async def search(
        self,
        query: str,
        top_k: int = 5,
        similarity_threshold: float = 0.0,
    ) -> list[VectorSearchResult]:
        """Execute vector similarity search."""

        logger.info(f"Vector search: query='{query[:100]}...', top_k={top_k}")

        # 1. Embed query
        start_time = time.time()
        try:
            query_embedding = self.embedding_provider.embed([query])[0]
        except Exception as e:
            logger.error(f"Failed to embed query: {e}")
            raise

        embedding_latency_ms = (time.time() - start_time) * 1000
        logger.debug(f"Query embedding generated in {embedding_latency_ms:.1f}ms")

        # 2. Search in pgvector
        start_time = time.time()
        try:
            results = self.db.execute(
                """
                SELECT
                    e.chunk_id,
                    c.document_id,
                    c.title,
                    c.section,
                    c.text,
                    c.source,
                    c.domain,
                    1 - (e.embedding <=> %s) as similarity_score
                FROM embeddings_v2 e
                JOIN chunks_v2 c ON e.chunk_id = c.chunk_id
                WHERE 1 - (e.embedding <=> %s) > %s
                ORDER BY similarity_score DESC
                LIMIT %s
                """,
                (query_embedding, query_embedding, similarity_threshold, top_k),
            ).fetchall()
        except Exception as e:
            logger.error(f"Vector search failed: {e}")
            raise

        search_latency_ms = (time.time() - start_time) * 1000
        logger.debug(f"Vector search completed in {search_latency_ms:.1f}ms")

        # 3. Convert to result objects
        search_results = []
        for ranking, row in enumerate(results, 1):
            result = VectorSearchResult(
                chunk_id=row[0],
                document_id=row[1],
                title=row[2],
                section=row[3],
                content=row[4],
                source=row[5],
                domain=row[6],
                similarity_score=float(row[7]),
                ranking=ranking,
            )
            search_results.append(result)
            logger.debug(f"Result {ranking}: {result.chunk_id} (score={result.similarity_score:.4f})")

        logger.info(f"Vector search returned {len(search_results)} results")
        return search_results


# Sanity Test Suite
class V2VectorSearchSanityTests:
    """Sanity tests for vector retrieval."""

    @staticmethod
    async def test_query_embedding_generation(embedding_provider) -> bool:
        """Test that queries can be embedded."""
        try:
            test_query = "What are patent filing requirements in India?"
            embeddings = embedding_provider.embed([test_query])
            assert len(embeddings) == 1
            assert len(embeddings[0]) == 1536  # Expected dimension
            logger.info("✓ Query embedding generation works")
            return True
        except Exception as e:
            logger.error(f"✗ Query embedding generation failed: {e}")
            return False

    @staticmethod
    async def test_vector_search_ip_query(retrieval: V2VectorRetrieval) -> bool:
        """Test vector search on IP/Patent query."""
        try:
            results = await retrieval.search(
                "What are the provisions for patent prosecution in India?",
                top_k=5,
            )
            assert len(results) > 0, "No results returned"
            assert results[0].similarity_score > 0.0, "Invalid similarity score"
            logger.info(f"✓ IP query retrieval returned {len(results)} results")
            return True
        except Exception as e:
            logger.error(f"✗ IP query retrieval failed: {e}")
            return False

    @staticmethod
    async def test_vector_search_trademark_query(retrieval: V2VectorRetrieval) -> bool:
        """Test vector search on Trademark query."""
        try:
            results = await retrieval.search(
                "How are trademarks registered and protected?",
                top_k=5,
            )
            assert len(results) > 0, "No results returned"
            logger.info(f"✓ Trademark query retrieval returned {len(results)} results")
            return True
        except Exception as e:
            logger.error(f"✗ Trademark query retrieval failed: {e}")
            return False

    @staticmethod
    async def test_vector_search_ayurveda_query(retrieval: V2VectorRetrieval) -> bool:
        """Test vector search on Ayurveda query."""
        try:
            results = await retrieval.search(
                "What are FSSAI regulations for Ayurveda products?",
                top_k=5,
            )
            # Ayurveda may have fewer results, so just check it doesn't crash
            logger.info(f"✓ Ayurveda query retrieval returned {len(results)} results")
            return True
        except Exception as e:
            logger.error(f"✗ Ayurveda query retrieval failed: {e}")
            return False

    @staticmethod
    async def test_metadata_preservation(retrieval: V2VectorRetrieval) -> bool:
        """Test that metadata is preserved in results."""
        try:
            results = await retrieval.search(
                "Geographical indications protection",
                top_k=3,
            )
            for result in results:
                assert result.chunk_id, "chunk_id missing"
                assert result.document_id, "document_id missing"
                assert result.title, "title missing"
                assert result.source, "source missing"
                assert 0.0 <= result.similarity_score <= 1.0, "Invalid similarity score"
            logger.info("✓ Metadata preservation verified")
            return True
        except Exception as e:
            logger.error(f"✗ Metadata preservation check failed: {e}")
            return False

    @staticmethod
    async def test_top_k_parameter(retrieval: V2VectorRetrieval) -> bool:
        """Test that top_k parameter works correctly."""
        try:
            results_k5 = await retrieval.search("patent protection", top_k=5)
            results_k10 = await retrieval.search("patent protection", top_k=10)

            assert len(results_k5) <= 5, f"top_k=5 returned {len(results_k5)} results"
            assert len(results_k10) <= 10, f"top_k=10 returned {len(results_k10)} results"
            assert len(results_k10) >= len(results_k5), "top_k=10 should return at least as many as top_k=5"

            logger.info(f"✓ Top-K parameter works (k=5: {len(results_k5)}, k=10: {len(results_k10)})")
            return True
        except Exception as e:
            logger.error(f"✗ Top-K parameter test failed: {e}")
            return False

    @staticmethod
    async def test_result_ranking(retrieval: V2VectorRetrieval) -> bool:
        """Test that results are properly ranked."""
        try:
            results = await retrieval.search(
                "copyright infringement remedies",
                top_k=10,
            )

            # Check ranking is monotonic
            for i, result in enumerate(results):
                assert result.ranking == i + 1, f"Invalid ranking: expected {i+1}, got {result.ranking}"

            # Check scores are descending
            for i in range(len(results) - 1):
                assert results[i].similarity_score >= results[i + 1].similarity_score, \
                    f"Scores not descending: {results[i].similarity_score} < {results[i+1].similarity_score}"

            logger.info(f"✓ Result ranking verified ({len(results)} results)")
            return True
        except Exception as e:
            logger.error(f"✗ Result ranking test failed: {e}")
            return False

    @staticmethod
    async def run_all_tests(
        retrieval: V2VectorRetrieval,
        embedding_provider,
    ) -> dict[str, bool]:
        """Run all sanity tests."""
        logger.info("Starting V2 Vector Search Sanity Tests")

        results = {
            "query_embedding_generation": await V2VectorSearchSanityTests.test_query_embedding_generation(
                embedding_provider
            ),
            "ip_query": await V2VectorSearchSanityTests.test_vector_search_ip_query(retrieval),
            "trademark_query": await V2VectorSearchSanityTests.test_vector_search_trademark_query(retrieval),
            "ayurveda_query": await V2VectorSearchSanityTests.test_vector_search_ayurveda_query(retrieval),
            "metadata_preservation": await V2VectorSearchSanityTests.test_metadata_preservation(retrieval),
            "top_k_parameter": await V2VectorSearchSanityTests.test_top_k_parameter(retrieval),
            "result_ranking": await V2VectorSearchSanityTests.test_result_ranking(retrieval),
        }

        passed = sum(1 for v in results.values() if v)
        total = len(results)
        logger.info(f"\nSanity Test Results: {passed}/{total} passed")

        for test_name, passed in results.items():
            status = "✓ PASS" if passed else "✗ FAIL"
            logger.info(f"  {status}: {test_name}")

        return results
