from __future__ import annotations

from typing import Any, Iterable

from supabase import Client, create_client

from app.core.config import Settings, get_settings


class SupabaseConfigurationError(RuntimeError):
    pass


class SupabaseRAGStore:
    """Supabase access boundary. Query paths use anon credentials; writes require service role."""

    def __init__(self, settings: Settings | None = None, *, administrative: bool = False):
        self.settings = settings or get_settings()
        key = self.settings.supabase_service_role_key if administrative else self.settings.supabase_anon_key
        if not self.settings.supabase_url or not key:
            role = "service-role" if administrative else "anon"
            raise SupabaseConfigurationError(f"Supabase URL and {role} key are required")
        self.client: Client = create_client(self.settings.supabase_url, key)
        self.administrative = administrative

    def _require_admin(self) -> None:
        if not self.administrative:
            raise PermissionError("administrative Supabase client required for ingestion")

    @staticmethod
    def _batches(rows: list[dict[str, Any]], size: int = 100) -> Iterable[list[dict[str, Any]]]:
        for start in range(0, len(rows), size):
            yield rows[start:start + size]

    def upsert_documents(self, documents: list[dict[str, Any]]) -> None:
        self._require_admin()
        rows = [{
            "id": item["document_id"],
            "document_version": item["document_version"],
            "title": item["title"],
            "authority": item["authority"],
            "domain": item["domain"],
            "jurisdiction": item["jurisdiction"],
            "document_type": item["document_type"],
            "publication_date": item.get("publication_date"),
            "effective_date": item.get("effective_date"),
            "version_label": item.get("version"),
            "source_url": item["source_url"],
            "checksum": item.get("checksum"),
            "page_count": item.get("page_count"),
            "language": item.get("language", "en"),
            "ingestion_status": item["ingestion_status"],
            "included_in_retrieval": item["included_in_retrieval"],
            "metadata": {"ocr": item.get("ocr"), "validation_errors": item.get("validation_errors", [])},
        } for item in documents]
        for batch in self._batches(rows):
            self.client.table("documents").upsert(batch).execute()

    def upsert_chunks(self, chunks: list[dict[str, Any]]) -> None:
        self._require_admin()
        rows = [{
            "id": item["chunk_id"],
            "document_id": item["document_id"],
            "document_version": item["document_version"],
            "ordinal": item["ordinal"],
            "text": item["text"],
            "chapter": item.get("chapter"),
            "section": item.get("section"),
            "subsection": item.get("subsection"),
            "rule_number": item.get("rule_number"),
            "sub_rule": item.get("sub_rule"),
            "regulation_number": item.get("regulation_number"),
            "article_number": item.get("article_number"),
            "paragraph_number": item.get("paragraph_number"),
            "clause": item.get("clause"),
            "page_start": item.get("page_start"),
            "page_end": item.get("page_end"),
            "language": item.get("language", "en"),
            "source_status": item.get("source_status", "UNVERIFIED"),
            "text_uncertain": item.get("text_uncertain", False),
            "metadata": {"structure_type": item.get("structure_type")},
        } for item in chunks]
        for batch in self._batches(rows):
            self.client.table("chunks").upsert(batch).execute()

    def upsert_embeddings(self, rows: list[dict[str, Any]]) -> None:
        self._require_admin()
        for batch in self._batches(rows):
            self.client.table("chunk_embeddings").upsert(batch).execute()

    def vector_search(self, vector: list[float], count: int, threshold: float, filters: dict[str, Any]) -> list[dict[str, Any]]:
        response = self.client.rpc("match_chunks_vector", {
            "query_embedding": vector,
            "match_threshold": threshold,
            "match_count": count,
            "filter_jurisdiction": filters.get("jurisdiction"),
            "filter_domains": filters.get("domains"),
            "filter_language": filters.get("language"),
        }).execute()
        return list(response.data or [])

    def keyword_search(self, query: str, count: int, filters: dict[str, Any]) -> list[dict[str, Any]]:
        response = self.client.rpc("match_chunks_keyword", {
            "search_query": query,
            "match_count": count,
            "filter_jurisdiction": filters.get("jurisdiction"),
            "filter_domains": filters.get("domains"),
            "filter_language": filters.get("language"),
        }).execute()
        return list(response.data or [])

    def log_retrieval(self, row: dict[str, Any]) -> None:
        self.client.table("retrieval_logs").insert(row).execute()
