from app.models import Jurisdiction, QueryRequest
from app.retrieval.embeddings import HashEmbeddingProvider
from app.retrieval.query_analysis import analyze_query
from app.retrieval.reranker import LegalFeatureReranker


def test_hash_embedding_has_stable_valid_dimension() -> None:
    provider = HashEmbeddingProvider(dimension=64)
    first = provider.embed(["Section 3(p) patentee"])[0]
    assert len(first) == 64
    assert first == provider.embed(["Section 3(p) patentee"])[0]
    assert abs(sum(value * value for value in first) - 1.0) < 1e-6


def test_query_routing() -> None:
    patent = analyze_query(QueryRequest(query="What does Section 3(p) of the Patents Act mean?"))
    assert patent.jurisdiction == Jurisdiction.INDIA
    assert "PATENT" in patent.domains
    assert patent.legal_identifiers == ["Section 3(p)"]
    assert analyze_query(QueryRequest(query="What is Article 6 of GRATK?")).jurisdiction == Jurisdiction.INTERNATIONAL


def test_hybrid_retrieval_uses_both_signals(service) -> None:
    analysis = analyze_query(QueryRequest(query="What does Section 3(p) of the Patents Act say?"))
    rows = service.retriever.retrieve(analysis)
    assert rows
    assert any(row.vector_score > 0 for row in rows)
    assert any(row.lexical_score > 0 for row in rows)
    assert rows[0].domain == "PATENT" and rows[0].jurisdiction == "INDIA"


def test_exact_identifier_survives_reranking(service) -> None:
    analysis = analyze_query(QueryRequest(query="What does Section 3(p) of the Patents Act say?"))
    ranked = service.reranker.rerank(analysis, service.retriever.retrieve(analysis), 8)
    assert any(row.section == "3" and row.clause == "p" for row in ranked)
    assert LegalFeatureReranker.learned is False


def test_domain_filter_excludes_unrelated_corpus(service) -> None:
    rows = service.retriever.retrieve(analyze_query(QueryRequest(query="Explain copyright in literary works in India")))
    assert rows and {row.domain for row in rows} == {"COPYRIGHT"}
