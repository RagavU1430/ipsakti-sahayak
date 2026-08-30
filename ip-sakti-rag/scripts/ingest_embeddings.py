from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path

from tqdm import tqdm

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.core.config import get_settings
from app.core.db import SupabaseRAGStore
from app.ingestion.validator import validate_dataset
from app.retrieval.embeddings import HashEmbeddingProvider, OpenRouterEmbeddingProvider


def load(path: Path) -> list[dict]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--provider", choices=("openrouter", "hash"), default="openrouter")
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--limit", type=int)
    args = parser.parse_args()
    settings = get_settings()
    documents = load(settings.canonical_documents_path)
    chunks = load(settings.canonical_chunks_path)
    validation = validate_dataset(documents, chunks)
    if not validation.passed:
        print(json.dumps({"error": "dataset validation failed", "details": validation.errors}, indent=2))
        return 1
    if args.limit:
        chunks = chunks[:args.limit]
    provider = (
        HashEmbeddingProvider(settings.embedding_dimension)
        if args.provider == "hash"
        else OpenRouterEmbeddingProvider(settings.openrouter_api_key, settings.embedding_model, settings.embedding_dimension)
    )
    if args.dry_run:
        sample = provider.embed([chunk["text"] for chunk in chunks[: min(2, len(chunks))]])
        print(json.dumps({
            "dry_run": True,
            "provider": args.provider,
            "model": provider.model,
            "dimension": provider.dimension,
            "sample_count": len(sample),
            "dataset_chunks": len(chunks),
        }, indent=2))
        return 0

    database = SupabaseRAGStore(settings, administrative=True)
    database.upsert_documents(documents)
    database.upsert_chunks(chunks)
    completed = 0
    for start in tqdm(range(0, len(chunks), args.batch_size), desc="Embedding canonical chunks"):
        batch = chunks[start:start + args.batch_size]
        vectors = provider.embed([chunk["text"] for chunk in batch])
        rows = [{
            "chunk_id": chunk["chunk_id"],
            "document_version": chunk["document_version"],
            "provider": args.provider,
            "model": provider.model,
            "dimension": provider.dimension,
            "embedding": vector,
            "text_checksum": hashlib.sha256(chunk["text"].encode("utf-8")).hexdigest(),
        } for chunk, vector in zip(batch, vectors, strict=True)]
        database.upsert_embeddings(rows)
        completed += len(rows)
    if completed != len(chunks):
        raise RuntimeError(f"embedding ingestion incomplete: {completed}/{len(chunks)}")
    print(json.dumps({"embedded": completed, "model": provider.model, "dimension": provider.dimension}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
