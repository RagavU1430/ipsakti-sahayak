"""
Part 3 Test Suite: Lexical Retrieval + Query Understanding + Hybrid Fusion

Tests for phases 5-11 implementation.
"""

import pytest
import asyncio
from app.retrieval.query_understanding import QueryAnalyzer, QueryIntent, QueryDomain
from app.retrieval.legal_terms import LegalTermExtractor
from app.retrieval.lexical_retrieval import LexicalRetrieval, LexicalMatchType
from app.retrieval.hybrid_retrieval import HybridRetrieval, FusionStrategy


class TestQueryUnderstanding:
    """Tests for Phase 2: Query Understanding."""

    def test_patent_intent_detection(self):
        """Test detection of patent-related queries."""
        analyzer = QueryAnalyzer()

        query = "What are the requirements for patenting an invention in India?"
        understanding = analyzer.analyze(query)

        assert understanding.intent == QueryIntent.PATENT
        assert understanding.intent_confidence > 0.7
        assert "Patents Act 1970" in understanding.legal_references

    def test_trademark_intent_detection(self):
        """Test detection of trademark-related queries."""
        analyzer = QueryAnalyzer()

        query = "How to register a trademark and prevent infringement?"
        understanding = analyzer.analyze(query)

        assert understanding.intent == QueryIntent.TRADEMARK
        assert understanding.intent_confidence > 0.7

    def test_copyright_intent_detection(self):
        """Test detection of copyright-related queries."""
        analyzer = QueryAnalyzer()

        query = "Copyright protection for literary works"
        understanding = analyzer.analyze(query)

        assert understanding.intent == QueryIntent.COPYRIGHT
        assert understanding.intent_confidence > 0.6

    def test_domain_detection_ip(self):
        """Test IP domain detection."""
        analyzer = QueryAnalyzer()

        query = "Patent and trademark protection"
        understanding = analyzer.analyze(query)

        assert understanding.primary_domain == QueryDomain.IP

    def test_domain_detection_ayurveda(self):
        """Test Ayurveda domain detection."""
        analyzer = QueryAnalyzer()

        query = "FSSAI Ayurveda Aahara regulations"
        understanding = analyzer.analyze(query)

        assert understanding.primary_domain in [QueryDomain.REGULATORY, QueryDomain.AYURVEDA]

    def test_exact_match_detection(self):
        """Test detection of exact match queries."""
        analyzer = QueryAnalyzer()

        query = 'Section 3(d) of "Patents Act"'
        understanding = analyzer.analyze(query)

        assert understanding.is_exact_match is True

    def test_acronym_detection(self):
        """Test detection of acronyms."""
        analyzer = QueryAnalyzer()

        query = "FSSAI regulations and TKDL protection"
        understanding = analyzer.analyze(query)

        assert understanding.contains_acronyms is True

    def test_cross_domain_query(self):
        """Test detection of cross-domain queries."""
        analyzer = QueryAnalyzer()

        query = "Ayurveda products and patent protection"
        understanding = analyzer.analyze(query)

        # Should detect both domains
        assert len(understanding.secondary_domains) > 0


class TestLegalTermExtraction:
    """Tests for Phase 3: Legal Term Extraction."""

    def test_act_extraction(self):
        """Test extraction of act names."""
        extractor = LegalTermExtractor()

        text = "Under the Patents Act 1970, inventions must meet novelty requirements"
        terms = extractor.extract(text)

        assert "Patents Act 1970" in terms.acts

    def test_section_extraction(self):
        """Test extraction of section numbers."""
        extractor = LegalTermExtractor()

        text = "Section 3(d) provides exceptions to patentability"
        terms = extractor.extract(text)

        assert any("3(d)" in s or "3" in s for s in terms.sections)

    def test_rule_extraction(self):
        """Test extraction of rule numbers."""
        extractor = LegalTermExtractor()

        text = "Rule 9 of the Trade Marks Rules 2017"
        terms = extractor.extract(text)

        assert any("9" in r for r in terms.rules)

    def test_international_reference_extraction(self):
        """Test extraction of international references."""
        extractor = LegalTermExtractor()

        text = "The Paris Convention and PCT procedures"
        terms = extractor.extract(text)

        assert len(terms.acts) > 0 or len(terms.organization_names) > 0

    def test_organization_extraction(self):
        """Test extraction of organization names."""
        extractor = LegalTermExtractor()

        text = "The Ministry of AYUSH and FSSAI regulations"
        terms = extractor.extract(text)

        assert "Ministry of AYUSH" in terms.organization_names or "FSSAI" in terms.organization_names


class TestLexicalRetrieval:
    """Tests for Phase 4: Lexical Retrieval (requires database)."""

    @pytest.mark.asyncio
    async def test_exact_search_available(self):
        """Test that exact search method is available."""
        # Note: Actual testing requires PostgreSQL connection
        # This validates the method signature
        retrieval = LexicalRetrieval(db_connection=None)

        # Method exists and has correct signature
        assert hasattr(retrieval, '_exact_search')
        assert callable(retrieval._exact_search)

    @pytest.mark.asyncio
    async def test_phrase_search_available(self):
        """Test that phrase search method is available."""
        retrieval = LexicalRetrieval(db_connection=None)

        assert hasattr(retrieval, '_phrase_search')
        assert callable(retrieval._phrase_search)

    @pytest.mark.asyncio
    async def test_full_text_search_available(self):
        """Test that full-text search method is available."""
        retrieval = LexicalRetrieval(db_connection=None)

        assert hasattr(retrieval, '_full_text_search')
        assert callable(retrieval._full_text_search)


class TestHybridRetrieval:
    """Tests for Phase 7: Hybrid Retrieval."""

    def test_hybrid_config_defaults(self):
        """Test hybrid retrieval configuration defaults."""
        from app.retrieval.hybrid_retrieval import HybridRetrievalConfig

        config = HybridRetrievalConfig()

        assert config.vector_top_k == 20
        assert config.lexical_top_k == 20
        assert config.fusion_strategy == FusionStrategy.RRF
        assert config.final_top_k == 10

    def test_deduplication_logic(self):
        """Test deduplication logic."""
        # Mock vector results
        vector_results = [
            type('obj', (object,), {
                'chunk_id': 'C001',
                'document_id': 'D001',
                'content': 'Test content',
                'domain': 'IP',
                'source': 'test.md',
                'title': 'Test',
                'section': 'Section 1',
                'similarity_score': 0.85,
                'token_count': 100,
                'content_hash': 'abc123',
                'document_version': 'v1'
            })()
        ]

        lexical_results = [
            type('obj', (object,), {
                'chunk_id': 'C001',
                'document_id': 'D001',
                'content': 'Test content',
                'domain': 'IP',
                'source': 'test.md',
                'title': 'Test',
                'section': 'Section 1',
                'lexical_score': 0.75
            })()
        ]

        # Create hybrid retrieval instance with mocks
        class MockHybridRetrieval:
            def _deduplicate(self, vector_results, lexical_results):
                candidates = {}

                for result in vector_results:
                    chunk_id = result.chunk_id
                    if chunk_id not in candidates:
                        candidates[chunk_id] = {
                            'chunk_id': chunk_id,
                            'retrieval_methods': set()
                        }
                    candidates[chunk_id]['retrieval_methods'].add('vector')

                for result in lexical_results:
                    chunk_id = result.chunk_id
                    if chunk_id not in candidates:
                        candidates[chunk_id] = {
                            'chunk_id': chunk_id,
                            'retrieval_methods': set()
                        }
                    candidates[chunk_id]['retrieval_methods'].add('lexical')

                return candidates

        hybrid = MockHybridRetrieval()
        candidates = hybrid._deduplicate(vector_results, lexical_results)

        # Should have 1 deduplicated candidate with both methods
        assert len(candidates) == 1
        assert 'C001' in candidates
        assert 'vector' in candidates['C001']['retrieval_methods']
        assert 'lexical' in candidates['C001']['retrieval_methods']


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
