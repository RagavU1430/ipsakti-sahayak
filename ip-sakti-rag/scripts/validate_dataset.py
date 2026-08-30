from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.ingestion.pipeline import ROOT
from app.ingestion.validator import validate_dataset


def load(path: Path) -> list[dict]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def main() -> int:
    documents = load(ROOT / "dataset" / "canonical" / "documents.jsonl")
    chunks = load(ROOT / "dataset" / "canonical" / "chunks.jsonl")
    result = validate_dataset(documents, chunks)
    print(json.dumps({"passed": result.passed, "errors": result.errors, "warnings": result.warnings}, indent=2))
    return 0 if result.passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
