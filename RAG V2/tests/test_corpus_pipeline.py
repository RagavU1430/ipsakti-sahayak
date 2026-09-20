from __future__ import annotations

import json
import importlib.util
import sys
from pathlib import Path

PIPELINE_PATH = Path(__file__).resolve().parents[1] / "app" / "corpus" / "pipeline.py"
spec = importlib.util.spec_from_file_location("rag_v2_corpus_pipeline", PIPELINE_PATH)
pipeline = importlib.util.module_from_spec(spec)
assert spec and spec.loader
sys.modules[spec.name] = pipeline
spec.loader.exec_module(pipeline)
UNKNOWN = pipeline.UNKNOWN
build_corpus = pipeline.build_corpus


ROOT = Path(__file__).resolve().parents[1]


def test_build_is_reproducible_and_ids_are_stable():
    first = build_corpus(ROOT / "dataset_markdown")
    second = build_corpus(ROOT / "dataset_markdown")
    assert first.manifest == second.manifest
    assert [item["document_id"] for item in first.documents] == [item["document_id"] for item in second.documents]
    assert [item["chunk_id"] for item in first.chunks] == [item["chunk_id"] for item in second.chunks]


def test_chunks_preserve_context_and_provenance():
    result = build_corpus(ROOT / "dataset_markdown", max_tokens=120, min_tokens=10, overlap_tokens=10)
    assert result.chunks
    for chunk in result.chunks:
        assert chunk["document_id"].startswith("V2-DOC-")
        assert chunk["content_hash"]
        assert chunk["title"]
        assert chunk["source"]
        assert "text" in chunk
        assert chunk["token_count"] <= 140


def test_unknown_metadata_is_explicit_and_failed_sources_are_not_chunked():
    result = build_corpus(ROOT / "dataset_markdown")
    assert any(document["domain"] == UNKNOWN for document in result.documents)
    failed_ids = {document["document_id"] for document in result.documents if document["extraction_status"] != "VALID"}
    assert not any(chunk["document_id"] in failed_ids for chunk in result.chunks)


def test_manifest_counts_match_records(tmp_path: Path):
    result = build_corpus(ROOT / "dataset_markdown")
    output = tmp_path / "v2"
    pipeline.write_result(result, output)
    manifest = json.loads((output / "validated_manifest.json").read_text(encoding="utf-8"))
    chunk_count = len((output / "chunks_v2.jsonl").read_text(encoding="utf-8").splitlines())
    assert manifest["manifest"]["document_count"] == len(result.documents)
    assert manifest["manifest"]["chunk_count"] == chunk_count
