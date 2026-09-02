from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = ROOT.parent
EXPECTED_CHUNKS_HASH = "827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d"
EXPECTED_TESTS = 162
EXPECTED_PASSED = 162
EXPECTED_FAILED = 0
EXPECTED_RECALL = 1.0
EXPECTED_MRR = 0.9888888888888889
EXPECTED_ABSTENTION = 0.9444444444444444
EXPECTED_CITATION = 1.0
EXPECTED_ANSWER_QUALITY = 1.9259259259259258


REQUIRED_FILES = (
    ROOT / "dataset/canonical/chunks.jsonl",
    ROOT / "dataset/canonical/documents.jsonl",
    ROOT / "dataset/evaluation/deep_rag/deep_rag_summary.json",
    ROOT / "dataset/evaluation/deep_rag/deep_rag_results.json",
    ROOT / "dataset/evaluation/deep_rag/deep_rag_failures.json",
    ROOT / "app/legal_aliases.py",
    ROOT / "app/service.py",
    ROOT / "app/retrieval/query_analysis.py",
    ROOT / "app/retrieval/hybrid.py",
    ROOT / "app/retrieval/local_store.py",
    ROOT / "app/retrieval/reranker.py",
    ROOT / "app/guardrails/policy.py",
    ROOT / "app/generation/grounded.py",
    ROOT / "app/citations/engine.py",
    REPO_ROOT / "docs/RAG_V1_BASELINE.md",
)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8-sig"))


def close(actual: float, expected: float, tolerance: float = 0.0002) -> bool:
    return abs(float(actual) - expected) <= tolerance


def check_dataset_hash() -> tuple[bool, str]:
    actual = sha256(ROOT / "dataset/canonical/chunks.jsonl")
    ok = actual == EXPECTED_CHUNKS_HASH
    return ok, f"Dataset hash: {'PASS' if ok else 'FAIL'} ({actual})"


def check_required_files() -> tuple[bool, str]:
    missing = [str(path.relative_to(REPO_ROOT)) for path in REQUIRED_FILES if not path.exists()]
    if missing:
        return False, "Required files: FAIL (missing: " + ", ".join(missing) + ")"
    return True, "Required files: PASS"


def check_baseline_manifest() -> tuple[bool, str]:
    path = REPO_ROOT / "docs/RAG_V1_BASELINE.md"
    if not path.exists():
        return False, "Baseline manifest: FAIL (missing docs/RAG_V1_BASELINE.md)"
    text = path.read_text(encoding="utf-8")
    required = (EXPECTED_CHUNKS_HASH, "RAG QUALITY GATE: PASSED", "162", "0.9889", "PERFORMANCE WARNING")
    missing = [item for item in required if item not in text]
    if missing:
        return False, "Baseline manifest: FAIL (missing expected content)"
    return True, "Baseline manifest: PASS"


def check_deep_summary() -> tuple[bool, str]:
    path = ROOT / "dataset/evaluation/deep_rag/deep_rag_summary.json"
    if not path.exists():
        return False, "Deep evaluation artifact: FAIL (summary missing)"
    summary = load_json(path)
    checks = {
        "tests": summary.get("question_count") == EXPECTED_TESTS,
        "passed": summary.get("passed_count") == EXPECTED_PASSED,
        "failed": summary.get("failed_count") == EXPECTED_FAILED,
        "recall": close(summary.get("retrieval", {}).get("recall_at_k", -1), EXPECTED_RECALL),
        "mrr": close(summary.get("retrieval", {}).get("mrr", -1), EXPECTED_MRR),
        "abstention": close(summary.get("abstention", {}).get("accuracy", -1), EXPECTED_ABSTENTION),
        "citation": close(summary.get("grounding", {}).get("citation_integrity", -1), EXPECTED_CITATION),
        "answer_quality": close(summary.get("answer_quality_average", -1), EXPECTED_ANSWER_QUALITY),
        "dataset_unchanged": summary.get("dataset_changed") is False,
    }
    if not all(checks.values()):
        failed = [name for name, ok in checks.items() if not ok]
        return False, "Deep evaluation artifact: FAIL (" + ", ".join(failed) + ")"
    return True, "Deep evaluation artifact: PASS"


def check_test_suite_invocable() -> tuple[bool, str]:
    pyproject = ROOT / "pyproject.toml"
    tests_dir = ROOT / "tests"
    if not pyproject.exists() or not tests_dir.exists():
        return False, "Test suite: FAIL (pyproject/tests missing)"
    test_files = sorted(tests_dir.glob("test_*.py"))
    if len(test_files) < 1:
        return False, "Test suite: FAIL (no pytest files found)"
    return True, f"Test suite: PASS ({len(test_files)} pytest files present; run with python -m pytest)"


def main() -> int:
    checks = [
        check_dataset_hash(),
        check_required_files(),
        check_baseline_manifest(),
        check_deep_summary(),
        check_test_suite_invocable(),
    ]
    print("RAG V1.0 BASELINE VERIFICATION")
    print("------------------------------")
    for _, message in checks:
        print(message)
    overall = all(ok for ok, _ in checks)
    print(f"Overall: {'PASS' if overall else 'FAIL'}")
    return 0 if overall else 1


if __name__ == "__main__":
    raise SystemExit(main())

