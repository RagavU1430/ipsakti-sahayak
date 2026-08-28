from pathlib import Path
from app.ingestion.registry import read_registry, validate_row

REGISTRY=Path("dataset/manifests/source_registry.csv")
def test_registry_schema_and_rows():
    rows=read_registry(REGISTRY); assert 20 <= len(rows) <= 30
    assert not [(r["source_id"],validate_row(r)) for r in rows if validate_row(r)]
def test_source_ids_unique():
    ids=[r["source_id"] for r in read_registry(REGISTRY)]; assert len(ids)==len(set(ids))
def test_restricted_sources_have_no_download_url():
    for row in read_registry(REGISTRY):
        if row["status"]=="RESTRICTED": assert not row["download_url"] and row["content_available"]=="false"

