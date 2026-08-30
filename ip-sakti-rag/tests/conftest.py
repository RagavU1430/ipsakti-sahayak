from __future__ import annotations

import json
from pathlib import Path

import pytest

from app.core.config import Settings
from app.service import RAGService


ROOT = Path(__file__).resolve().parents[1]


@pytest.fixture(scope="session")
def settings() -> Settings:
    return Settings(
        root=ROOT,
        canonical_documents_path=ROOT / "dataset/canonical/documents.jsonl",
        canonical_chunks_path=ROOT / "dataset/canonical/chunks.jsonl",
        supabase_url=None,
        supabase_anon_key=None,
        supabase_service_role_key=None,
        storage_backend="local",
        embedding_provider="hash",
        embedding_model="test",
        embedding_dimension=64,
        openrouter_api_key=None,
        openrouter_model="test",
        enable_llm=False,
        enable_general_llm=False,
        top_k=8,
        candidate_k=24,
        similarity_threshold=0.1,
        min_score=0.1,
        abstention_threshold=0.12,
        max_context_chars=18000,
    )


@pytest.fixture(scope="session")
def service(settings: Settings) -> RAGService:
    return RAGService(settings=settings)


@pytest.fixture(autouse=True)
def override_api_service(service: RAGService):
    from app.api.main import app
    from app.service import get_service
    app.dependency_overrides[get_service] = lambda: service
    yield
    app.dependency_overrides.clear()


@pytest.fixture(scope="session")
def chunks() -> list[dict]:
    path = ROOT / "dataset/canonical/chunks.jsonl"
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line]
