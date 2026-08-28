import json
from pathlib import Path
def test_chunks_are_valid_jsonl():
    for line in filter(str.strip, Path("dataset/processed/chunks.jsonl").read_text(encoding="utf-8").splitlines()):
        row=json.loads(line); assert {"chunk_id","document_id","text","source_url","citation_label"} <= row.keys(); assert row["text"].strip()
def test_evaluation_sources_exist():
    import csv
    ids={r["source_id"] for r in csv.DictReader(Path("dataset/manifests/source_registry.csv").open(encoding="utf-8-sig"))}
    for line in filter(str.strip, Path("dataset/evaluation/questions.jsonl").read_text(encoding="utf-8").splitlines()): assert set(json.loads(line)["expected_source_ids"]) <= ids
