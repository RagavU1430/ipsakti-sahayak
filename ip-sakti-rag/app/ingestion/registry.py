from __future__ import annotations

import csv
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class SourceRecord:
    source_id: str
    title: str
    short_title: str
    authority: str
    domain: str
    subdomain: str
    jurisdiction: str
    document_type: str
    source_url: str
    download_url: str
    language: str
    publication_date: str | None
    effective_date: str | None
    version: str
    status: str
    local_path: str
    retrieved_at: str | None
    sha256: str
    file_size_bytes: int | None
    content_available: bool

    @classmethod
    def from_row(cls, row: dict[str, str]) -> "SourceRecord":
        return cls(
            source_id=row["source_id"].strip(),
            title=row["title"].strip(),
            short_title=row["short_title"].strip(),
            authority=row["authority"].strip(),
            domain=row["domain"].strip().upper(),
            subdomain=row["subdomain"].strip().upper(),
            jurisdiction=row["jurisdiction"].strip().upper(),
            document_type=row["document_type"].strip().upper(),
            source_url=row["source_url"].strip(),
            download_url=row["download_url"].strip(),
            language=(row["language"].strip() or "en"),
            publication_date=row["publication_date"].strip() or None,
            effective_date=row["effective_date"].strip() or None,
            version=row["version"].strip(),
            status=row["status"].strip().upper(),
            local_path=row["local_path"].strip(),
            retrieved_at=row["retrieved_at"].strip() or None,
            sha256=row["sha256"].strip().lower(),
            file_size_bytes=int(row["file_size_bytes"]) if row["file_size_bytes"].strip() else None,
            content_available=row["content_available"].strip().lower() == "true",
        )


def read_registry(path: Path) -> list[SourceRecord]:
    with path.open(encoding="utf-8-sig", newline="") as handle:
        records = [SourceRecord.from_row(row) for row in csv.DictReader(handle)]
    ids = [record.source_id for record in records]
    if len(ids) != len(set(ids)):
        raise ValueError("source_registry.csv contains duplicate source_id values")
    return records
