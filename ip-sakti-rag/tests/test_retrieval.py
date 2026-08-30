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


def test_natural_language_domain_routing() -> None:
    assert "PATENT" in analyze_query(QueryRequest(query="I invented something new. How can I protect it in India?")).domains
    assert "TRADEMARK" in analyze_query(QueryRequest(query="I created a logo for my company. What IP protection should I consider?")).domains
    assert "COPYRIGHT" in analyze_query(QueryRequest(query="I created an original song. What kind of IP protection applies?")).domains
    assert "GI" in analyze_query(QueryRequest(query="I have a traditional product from my region. Can I protect its geographical identity?")).domains
    assert "PLANT_VARIETY" in analyze_query(QueryRequest(query="I developed a new plant variety. What protection is available?")).domains
    assert "ABS" in analyze_query(QueryRequest(query="I want to use biological resources. What approvals matter?")).domains


def test_out_of_scope_and_speculative_detection() -> None:
    weather = analyze_query(QueryRequest(query="What is the current weather in Chennai?"))
    assert weather.out_of_scope is True and not weather.domains
    teleportation = analyze_query(QueryRequest(query="Tell me the exact patent law for teleportation in India."))
    assert teleportation.speculative_subject == "teleportation" and "PATENT" in teleportation.domains


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
