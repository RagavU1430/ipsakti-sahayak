from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

def load_env(env_path: str = ".env") -> None:
    """
    Manually load environment variables from a .env file into os.environ.
    This avoids dependencies on external libraries like python-dotenv.
    """
    if not os.path.exists(env_path):
        # Check parent directory in case script runs from a subdirectory
        env_path = str(Path(__file__).resolve().parents[2] / ".env")
        if not os.path.exists(env_path):
            return
            
    with open(env_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            if "=" in line:
                key, val = line.split("=", 1)
                key = key.strip()
                val = val.strip()
                if (val.startswith('"') and val.endswith('"')) or (val.startswith("'") and val.endswith("'")):
                    val = val[1:-1]
                if key:
                    os.environ.setdefault(key, val)


def _int(name: str, default: int) -> int:
    return int(os.getenv(name, str(default)))


def _float(name: str, default: float) -> float:
    return float(os.getenv(name, str(default)))


def _bool(name: str, default: bool = False) -> bool:
    value = os.getenv(name)
    return default if value is None else value.strip().lower() in {"1", "true", "yes", "on"}


@dataclass(frozen=True)
class Settings:
    root: Path
    canonical_documents_path: Path
    canonical_chunks_path: Path
    supabase_url: str | None
    supabase_anon_key: str | None
    supabase_service_role_key: str | None
    storage_backend: str
    embedding_provider: str
    embedding_model: str
    embedding_dimension: int
    openrouter_api_key: str | None
    openrouter_model: str
    enable_llm: bool
    enable_general_llm: bool
    top_k: int
    candidate_k: int
    similarity_threshold: float
    min_score: float
    abstention_threshold: float
    max_context_chars: int


def get_settings() -> Settings:
    load_env()
    root = Path(__file__).resolve().parents[2]
    return Settings(
        root=root,
        canonical_documents_path=root / "dataset" / "canonical" / "documents.jsonl",
        canonical_chunks_path=root / "dataset" / "canonical" / "chunks.jsonl",
        supabase_url=os.getenv("SUPABASE_URL") or None,
        supabase_anon_key=os.getenv("SUPABASE_ANON_KEY") or None,
        supabase_service_role_key=os.getenv("SUPABASE_SERVICE_ROLE_KEY") or None,
        storage_backend=os.getenv("RAG_STORAGE_BACKEND", "auto").lower(),
        embedding_provider=os.getenv("EMBEDDING_PROVIDER", "openrouter").lower(),
        embedding_model=os.getenv("EMBEDDING_MODEL", "openai/text-embedding-3-small"),
        embedding_dimension=_int("EMBEDDING_DIMENSION", 1536),
        openrouter_api_key=os.getenv("OPENROUTER_API_KEY") or os.getenv("LLM_API_KEY") or None,
        openrouter_model=os.getenv("OPENROUTER_MODEL") or os.getenv("LLM_MODEL") or "openai/gpt-4.1-mini",
        enable_llm=_bool("RAG_ENABLE_LLM"),
        enable_general_llm=_bool("RAG_ENABLE_GENERAL_LLM"),
        top_k=_int("RAG_TOP_K", 8),
        candidate_k=_int("RAG_CANDIDATE_K", 24),
        similarity_threshold=_float("RAG_SIMILARITY_THRESHOLD", 0.10),
        min_score=_float("RAG_MIN_SCORE", 0.10),
        abstention_threshold=_float("RAG_ABSTENTION_THRESHOLD", 0.12),
        max_context_chars=_int("RAG_MAX_CONTEXT_CHARS", 18000),
    )
