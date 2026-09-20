from __future__ import annotations

import argparse
import importlib.util
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PIPELINE_PATH = ROOT / "app" / "corpus" / "pipeline.py"
spec = importlib.util.spec_from_file_location("rag_v2_corpus_pipeline", PIPELINE_PATH)
pipeline = importlib.util.module_from_spec(spec)
assert spec and spec.loader
sys.modules[spec.name] = pipeline
spec.loader.exec_module(pipeline)


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate and structure the RAG V2 Markdown corpus.")
    parser.add_argument("--input", type=Path, default=ROOT / "dataset_markdown")
    parser.add_argument("--output", type=Path, default=ROOT / "dataset" / "v2")
    parser.add_argument("--max-chunk-tokens", type=int, default=700)
    parser.add_argument("--min-chunk-tokens", type=int, default=40)
    parser.add_argument("--overlap-tokens", type=int, default=40)
    args = parser.parse_args()
    if args.max_chunk_tokens <= args.min_chunk_tokens or args.overlap_tokens >= args.max_chunk_tokens:
        raise SystemExit("chunk configuration must satisfy max > min and overlap < max")
    result = pipeline.build_corpus(args.input, args.max_chunk_tokens, args.min_chunk_tokens, args.overlap_tokens)
    pipeline.write_result(result, args.output)
    print(json.dumps(result.manifest, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
