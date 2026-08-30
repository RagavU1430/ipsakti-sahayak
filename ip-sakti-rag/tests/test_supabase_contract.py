from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MIGRATION = (ROOT / "supabase/migrations/003_rag_repair.sql").read_text(encoding="utf-8").lower()


def test_migration_has_vector_and_keyword_rpcs() -> None:
    assert "match_chunks_vector" in MIGRATION and "match_chunks_keyword" in MIGRATION
    assert "security definer" in MIGRATION and "set search_path" in MIGRATION


def test_rls_and_indexes_are_declared() -> None:
    assert "enable row level security" in MIGRATION
    assert "using hnsw" in MIGRATION and "using gin" in MIGRATION and "revoke all" in MIGRATION
