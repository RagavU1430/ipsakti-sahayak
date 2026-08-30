from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.ingestion.pipeline import build_canonical_dataset


def main() -> int:
    documents, chunks, validation = build_canonical_dataset()
    print(json.dumps({
        "documents": len(documents),
        "retrievable_documents": sum(document["included_in_retrieval"] for document in documents),
        "chunks": len(chunks),
        "validation_passed": validation.passed,
        "errors": len(validation.errors),
        "warnings": len(validation.warnings),
    }, indent=2))
    for error in validation.errors:
        print(f"ERROR: {error}")
    for warning in validation.warnings:
        print(f"WARNING: {warning}")
    return 0 if validation.passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
