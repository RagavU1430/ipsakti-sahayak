from fastapi.testclient import TestClient

from app.api.main import app
from app.generation.grounded import GenerationResult
from app.models import AskRequest, Confidence, QueryRequest
from app.service import RAGService


def test_health() -> None:
    response = TestClient(app).get("/health")
    assert response.status_code == 200 and response.json() == {"status": "ok"}


def test_rag_query_contract() -> None:
    response = TestClient(app).post("/rag/query", json={"query": "What does Section 3(p) of the Patents Act say?"})
    assert response.status_code == 200
    body = response.json()
    assert set(body) == {"answer", "confidence", "abstained", "jurisdiction", "domain", "citations", "limitations"}
    assert body["abstained"] is False and body["citations"][0]["chunk_id"]


def test_api_v1_ask_grounded_contract() -> None:
    response = TestClient(app).post("/api/v1/ask", json={
        "question": "What are the requirements for registering a trademark in India?",
        "domain": "TRADEMARK",
        "jurisdiction": "INDIA",
        "top_k": 5,
    })
    assert response.status_code == 200
    body = response.json()
    assert set(body) == {"answer", "confidence", "abstained", "citations", "sources"}
    assert body["abstained"] is False
    assert 0.0 <= body["confidence"] <= 1.0
    assert body["citations"] and body["sources"]
    assert any(source["document_id"].startswith("IND-TM") for source in body["sources"])
    assert {"document", "document_id", "page", "section", "authority", "source_url", "chunk_id"} <= set(body["citations"][0])


def test_api_v1_ask_general_fallback_contract() -> None:
    response = TestClient(app).post("/api/v1/ask", json={"question": "What is the IP registration procedure on Mars?"})
    assert response.status_code == 200
    body = response.json()
    assert body["abstained"] is False
    assert body["confidence"] == 0.35
    assert body["citations"] == []
    assert body["sources"] == []
    assert "IP-SAKTI corpus first" in body["answer"]


def test_api_v1_ask_validation_errors() -> None:
    client = TestClient(app)
    assert client.post("/api/v1/ask", json={}).status_code == 422
    assert client.post("/api/v1/ask", json={"question": "   "}).status_code == 422
    assert client.post("/api/v1/ask", json={"question": "valid question", "domain": "TAX"}).status_code == 422
    assert client.post("/api/v1/ask", json={"question": "valid question", "top_k": 99}).status_code == 422


def test_service_ask_maps_public_schema(service: RAGService) -> None:
    response = service.ask(AskRequest(question="What does Section 3(p) of the Patents Act say?", top_k=3))
    assert response.abstained is False
    assert isinstance(response.confidence, float)
    assert response.citations[0].document_id == "IND-PAT-ACT-1970"
    assert len(response.sources) <= 3


def test_citation_failure_abstains_in_runtime(settings) -> None:
    class InventingGenerator:
        def generate(self, analysis, context, evidence):
            return GenerationResult("Section 999 creates a made-up right.", [evidence[0].chunk_id], False, "test-inventor")

    service = RAGService(settings=settings, generator=InventingGenerator())
    response = service.query(QueryRequest(query="What does Section 3(p) of the Patents Act say?"))
    assert response.abstained is True
    assert response.confidence == Confidence.INSUFFICIENT_EVIDENCE


def test_api_v1_ask_quarantined_source_abstains() -> None:
    response = TestClient(app).post("/api/v1/ask", json={
        "question": "What does Regulation 4 of the 2022 Ayurveda Aahara Regulations require?"
    })
    assert response.status_code == 200
    assert response.json()["abstained"] is True
