from __future__ import annotations

import argparse
import hashlib
import json
import statistics
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import httpx


ROOT = Path(__file__).resolve().parents[1]
RESULTS_PATH = ROOT / "dataset" / "evaluation" / "results" / "runtime_question_test.json"
ENDPOINT = "/api/v1/ask"


@dataclass(frozen=True)
class QuestionCase:
    id: str
    category: str
    question: str
    expected_domain: str | None
    expected_documents: tuple[str, ...]
    expect_abstain: bool = False
    allow_abstain: bool = False
    notes: str = ""


QUESTIONS: tuple[QuestionCase, ...] = (
    QuestionCase("Q1", "TRADEMARK", "What are the requirements for registering a trademark in India?", "TRADEMARK", ("IND-TM-ACT-1999", "IND-TM-RULES-2017")),
    QuestionCase("Q2", "TRADEMARK", "What is the procedure for applying for a trademark in India?", "TRADEMARK", ("IND-TM-ACT-1999", "IND-TM-RULES-2017")),
    QuestionCase("Q3", "TRADEMARK", "What rights does registration of a trademark provide?", "TRADEMARK", ("IND-TM-ACT-1999",)),
    QuestionCase("Q4", "TRADEMARK", "What happens when a trademark application is opposed?", "TRADEMARK", ("IND-TM-ACT-1999", "IND-TM-RULES-2017")),
    QuestionCase("Q5", "TRADEMARK", "What is the duration of trademark registration in India?", "TRADEMARK", ("IND-TM-ACT-1999",)),
    QuestionCase("Q6", "PATENTS", "What is a patent in India?", "PATENT", ("IND-PAT-ACT-1970",)),
    QuestionCase("Q7", "PATENTS", "What are the basic requirements for obtaining a patent?", "PATENT", ("IND-PAT-ACT-1970",)),
    QuestionCase("Q8", "PATENTS", "How is a patent application filed in India?", "PATENT", ("IND-PAT-ACT-1970", "IND-PAT-RULES-2003")),
    QuestionCase("Q9", "PATENTS", "What rights does a patent provide to the patent holder?", "PATENT", ("IND-PAT-ACT-1970",)),
    QuestionCase("Q10", "PATENTS", "How long does patent protection last?", "PATENT", ("IND-PAT-ACT-1970",)),
    QuestionCase("Q11", "COPYRIGHT", "What rights does copyright provide to an author?", "COPYRIGHT", ("IND-CR-ACT-1957",)),
    QuestionCase("Q12", "COPYRIGHT", "How is copyright protection obtained in India?", "COPYRIGHT", ("IND-CR-ACT-1957",)),
    QuestionCase("Q13", "COPYRIGHT", "How long does copyright protection last?", "COPYRIGHT", ("IND-CR-ACT-1957",)),
    QuestionCase("Q14", "COPYRIGHT", "What is copyright infringement?", "COPYRIGHT", ("IND-CR-ACT-1957",)),
    QuestionCase("Q15", "DESIGNS", "What is a design under Indian law?", "DESIGN", ("IND-DES-ACT-2000",)),
    QuestionCase("Q16", "DESIGNS", "What are the requirements for registering a design?", "DESIGN", ("IND-DES-ACT-2000", "IND-DES-RULES-2001")),
    QuestionCase("Q17", "DESIGNS", "How long is design protection valid?", "DESIGN", ("IND-DES-ACT-2000",)),
    QuestionCase("Q18", "GEOGRAPHICAL INDICATIONS", "What is a geographical indication?", "GI", ("IND-GI-ACT-1999",)),
    QuestionCase("Q19", "GEOGRAPHICAL INDICATIONS", "How is a GI registered in India?", "GI", ("IND-GI-ACT-1999", "IND-GI-RULES-2002")),
    QuestionCase("Q20", "GEOGRAPHICAL INDICATIONS", "What protection does a registered GI provide?", "GI", ("IND-GI-ACT-1999",)),
    QuestionCase("Q21", "PLANT VARIETIES", "What is the purpose of plant variety protection in India?", "PLANT_VARIETY", ("IND-PPV-ACT-2001",)),
    QuestionCase("Q22", "PLANT VARIETIES", "Who can apply for protection of a plant variety?", "PLANT_VARIETY", ("IND-PPV-ACT-2001", "IND-PPV-RULES-2003")),
    QuestionCase("Q23", "PLANT VARIETIES", "What rights are provided to registered plant varieties?", "PLANT_VARIETY", ("IND-PPV-ACT-2001",)),
    QuestionCase("Q24", "BIODIVERSITY", "What is the purpose of the Biological Diversity Act?", "ABS", ("IND-BD-ACT-2002",)),
    QuestionCase("Q25", "BIODIVERSITY", "What is access and benefit sharing?", "ABS", ("IND-BD-ACT-2002", "IND-BD-RULES-2024")),
    QuestionCase("Q26", "BIODIVERSITY", "What changed under the Biological Diversity Amendment Act 2023?", "ABS", ("IND-BD-AMEND-2023", "IND-BD-ACT-2002")),
    QuestionCase("Q27", "BIODIVERSITY", "What are the objectives of the Biological Diversity Rules?", "ABS", ("IND-BD-RULES-2024",)),
    QuestionCase("Q28", "AYURVEDA", "What is Ayurveda Aahara?", "FOOD", ("IND-FSS-AA-ORDER-2025",), allow_abstain=True),
    QuestionCase("Q29", "AYURVEDA", "What regulations apply to Ayurveda Aahara?", "FOOD", ("IND-FSS-AA-ORDER-2025",), allow_abstain=True),
    QuestionCase("Q30", "AYURVEDA", "What is covered by the Ayurveda Aahara framework?", "FOOD", ("IND-FSS-AA-ORDER-2025",), allow_abstain=True),
    QuestionCase("Q31", "INTERNATIONAL IP", "What is the TRIPS Agreement?", "INTERNATIONAL", ("INT-TRIPS-1994",)),
    QuestionCase("Q32", "INTERNATIONAL IP", "What is the Paris Convention for the Protection of Industrial Property?", "INTERNATIONAL", ("INT-WIPO-PARIS",)),
    QuestionCase("Q33", "INTERNATIONAL IP", "What is the Patent Cooperation Treaty?", "INTERNATIONAL", ("INT-WIPO-PCT",)),
    QuestionCase("Q34", "INTERNATIONAL IP", "What is the Madrid Protocol?", "INTERNATIONAL", ("INT-WIPO-MADRID",)),
    QuestionCase("Q35", "INTERNATIONAL IP", "What is the Budapest Treaty?", "INTERNATIONAL", ("INT-WIPO-BUDAPEST",)),
    QuestionCase("Q36", "INTERNATIONAL IP", "What is the WIPO GRATK Treaty?", "INTERNATIONAL", ("INT-WIPO-GRATK-2024",)),
    QuestionCase("Q37", "CROSS-DOMAIN", "What is the difference between a patent and a trademark?", None, ("IND-PAT-ACT-1970", "IND-TM-ACT-1999")),
    QuestionCase("Q38", "CROSS-DOMAIN", "What is the difference between copyright and design protection?", None, ("IND-CR-ACT-1957", "IND-DES-ACT-2000")),
    QuestionCase("Q39", "CROSS-DOMAIN", "How is a geographical indication different from a trademark?", None, ("IND-GI-ACT-1999", "IND-TM-ACT-1999")),
    QuestionCase("Q40", "CROSS-DOMAIN", "How does patent protection differ from plant variety protection?", None, ("IND-PAT-ACT-1970", "IND-PPV-ACT-2001")),
    QuestionCase("Q41", "NATURAL LANGUAGE", "I invented something new. How can I protect it in India?", "PATENT", ("IND-PAT-ACT-1970",)),
    QuestionCase("Q42", "NATURAL LANGUAGE", "I created a logo for my company. What IP protection should I consider?", "TRADEMARK", ("IND-TM-ACT-1999",)),
    QuestionCase("Q43", "NATURAL LANGUAGE", "I have a traditional product from my region. Can I protect its geographical identity?", "GI", ("IND-GI-ACT-1999",)),
    QuestionCase("Q44", "NATURAL LANGUAGE", "I created an original song. What kind of IP protection applies?", "COPYRIGHT", ("IND-CR-ACT-1957",)),
    QuestionCase("Q45", "NATURAL LANGUAGE", "I developed a new plant variety. What protection is available?", "PLANT_VARIETY", ("IND-PPV-ACT-2001",)),
    QuestionCase("Q46", "ADVERSARIAL", "Tell me a law that allows me to patent any idea even if it is already publicly known.", "PATENT", ("IND-PAT-ACT-1970",), allow_abstain=True, notes="Must not accept false premise."),
    QuestionCase("Q47", "ADVERSARIAL", "According to Section 9999 of the Trade Marks Act, what is the penalty?", "TRADEMARK", ("IND-TM-ACT-1999",), expect_abstain=True),
    QuestionCase("Q48", "ADVERSARIAL", "What does the Indian IP law say about owning land on Mars?", None, (), expect_abstain=True),
    QuestionCase("Q49", "ADVERSARIAL", "Tell me the exact patent law for teleportation in India.", "PATENT", ("IND-PAT-ACT-1970",), expect_abstain=True),
    QuestionCase("Q50", "ADVERSARIAL", "Which Indian law gives me automatic copyright ownership of every idea I think about?", "COPYRIGHT", ("IND-CR-ACT-1957",), allow_abstain=True, notes="Must not accept false premise."),
    QuestionCase("Q51", "OUT-OF-CORPUS", "What is the current weather in Chennai?", None, (), expect_abstain=True),
    QuestionCase("Q52", "OUT-OF-CORPUS", "What is today's Bitcoin price?", None, (), expect_abstain=True),
    QuestionCase("Q53", "OUT-OF-CORPUS", "Who won the latest cricket match?", None, (), expect_abstain=True),
    QuestionCase("Q54", "OUT-OF-CORPUS", "Write me a Python program to sort a list.", None, (), expect_abstain=True),
    QuestionCase("Q55", "OUT-OF-CORPUS", "What is the capital of France?", None, (), expect_abstain=True),
)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def dataset_fingerprint() -> dict[str, Any]:
    metadata = json.loads((ROOT / "dataset" / "canonical" / "metadata.json").read_text(encoding="utf-8"))
    files = [
        "dataset/canonical/documents.jsonl",
        "dataset/canonical/chunks.jsonl",
        "dataset/manifests/source_registry.csv",
        "dataset/manifests/download_manifest.json",
        "dataset/manifests/checksums.sha256",
    ]
    return {
        "document_count": metadata["document_count"],
        "retrievable_document_count": metadata["retrievable_document_count"],
        "chunk_count": metadata["chunk_count"],
        "warnings": metadata["warnings"],
        "file_hashes": {item: sha256(ROOT / item) for item in files},
    }


def load_corpus() -> tuple[dict[str, dict[str, Any]], dict[str, dict[str, Any]]]:
    documents = {
        item["document_id"]: item
        for item in _read_jsonl(ROOT / "dataset" / "canonical" / "documents.jsonl")
    }
    chunks = {
        item["chunk_id"]: item
        for item in _read_jsonl(ROOT / "dataset" / "canonical" / "chunks.jsonl")
    }
    return documents, chunks


def _read_jsonl(path: Path) -> list[dict[str, Any]]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def schema_errors(body: Any) -> list[str]:
    errors: list[str] = []
    if not isinstance(body, dict):
        return ["response is not a JSON object"]
    for field in ("answer", "confidence", "abstained", "citations", "sources"):
        if field not in body:
            errors.append(f"missing field: {field}")
    if "answer" in body and not isinstance(body["answer"], str):
        errors.append("answer is not a string")
    if "confidence" in body and not isinstance(body["confidence"], (int, float)):
        errors.append("confidence is not numeric")
    if "abstained" in body and not isinstance(body["abstained"], bool):
        errors.append("abstained is not boolean")
    if "citations" in body and not isinstance(body["citations"], list):
        errors.append("citations is not a list")
    if "sources" in body and not isinstance(body["sources"], list):
        errors.append("sources is not a list")
    return errors


def validate_citations(body: dict[str, Any], documents: dict[str, dict[str, Any]], chunks: dict[str, dict[str, Any]]) -> tuple[bool, list[str]]:
    errors: list[str] = []
    for citation in body.get("citations", []):
        document_id = citation.get("document_id")
        chunk_id = citation.get("chunk_id")
        if document_id not in documents:
            errors.append(f"citation document does not exist: {document_id}")
            continue
        document = documents[document_id]
        if document.get("ingestion_status") == "QUARANTINED_INVALID_SOURCE" or document.get("included_in_retrieval") is False:
            errors.append(f"citation points to quarantined/non-retrievable document: {document_id}")
        if chunk_id not in chunks:
            errors.append(f"citation chunk does not exist: {chunk_id}")
            continue
        chunk = chunks[chunk_id]
        if chunk.get("document_id") != document_id:
            errors.append(f"citation chunk/document mismatch: {chunk_id}")
        page = citation.get("page")
        page_count = document.get("page_count")
        if page is not None and page_count is not None and not (1 <= int(page) <= int(page_count)):
            errors.append(f"citation page out of bounds: {document_id} page {page}")
    return not errors, errors


def evaluate_case(case: QuestionCase, response: httpx.Response, body: Any, documents: dict[str, dict[str, Any]], chunks: dict[str, dict[str, Any]], latency_ms: float) -> dict[str, Any]:
    errors: list[str] = []
    schema = schema_errors(body)
    if response.status_code != 200:
        errors.append(f"HTTP {response.status_code}")
    errors.extend(schema)
    body_dict = body if isinstance(body, dict) else {}
    citation_ok, citation_errors = validate_citations(body_dict, documents, chunks)
    if not citation_ok:
        errors.extend(citation_errors)
    confidence = body_dict.get("confidence")
    confidence_valid = isinstance(confidence, (int, float)) and 0.0 <= float(confidence) <= 1.0
    if not confidence_valid:
        errors.append("confidence outside 0.0-1.0 or non-numeric")
    abstained = body_dict.get("abstained")
    source_ids = [source.get("document_id") for source in body_dict.get("sources", []) if isinstance(source, dict)]
    citation_doc_ids = [citation.get("document_id") for citation in body_dict.get("citations", []) if isinstance(citation, dict)]
    returned_doc_ids = tuple(dict.fromkeys([*source_ids, *citation_doc_ids]))
    expected_hit = not case.expected_documents or any(document_id in case.expected_documents for document_id in returned_doc_ids)
    if case.expected_documents and abstained is False and not expected_hit:
        errors.append("no expected document returned")
    if case.expect_abstain and abstained is not True:
        errors.append("expected abstention but received grounded answer")
    if not case.expect_abstain and not case.allow_abstain and abstained is True:
        errors.append("unexpected abstention")
    if abstained is False and not body_dict.get("citations"):
        errors.append("grounded answer has no citations")
    if "IND-FSS-AA-2022" in returned_doc_ids:
        errors.append("quarantined FSSAI 2022 source appeared in public evidence")
    answer = body_dict.get("answer", "")
    if case.id == "Q46" and "allows me to patent any idea" in answer.lower():
        errors.append("accepted false patentability premise")
    if case.id == "Q50" and "every idea i think about" in answer.lower() and abstained is False:
        errors.append("accepted false copyright premise")
    return {
        "id": case.id,
        "category": case.category,
        "question": case.question,
        "expected_domain": case.expected_domain,
        "expected_documents": list(case.expected_documents),
        "http_status": response.status_code,
        "answer": answer,
        "abstained": abstained,
        "confidence": confidence,
        "citations": body_dict.get("citations", []),
        "source_document_ids": source_ids,
        "source_scores": [source.get("score") for source in body_dict.get("sources", []) if isinstance(source, dict)],
        "latency_ms": round(latency_ms, 3),
        "schema_valid": not schema,
        "citation_integrity": citation_ok,
        "confidence_valid": confidence_valid,
        "expected_document_hit": expected_hit,
        "passed": not errors,
        "failure_reason": "; ".join(errors),
    }


def summarize(results: list[dict[str, Any]], before: dict[str, Any], after: dict[str, Any]) -> dict[str, Any]:
    latencies = [item["latency_ms"] for item in results]
    grounded = [item for item in results if item["abstained"] is False]
    expected_with_docs = [item for item in results if item["expected_documents"] and item["abstained"] is False]
    expected_abstentions = {case.id for case in QUESTIONS if case.expect_abstain}
    abstention_cases = [item for item in results if item["id"] in expected_abstentions]
    reciprocal_ranks = []
    for item in expected_with_docs:
        rank = next((idx for idx, document_id in enumerate(item["source_document_ids"], 1) if document_id in item["expected_documents"]), None)
        reciprocal_ranks.append(1.0 / rank if rank else 0.0)
    return {
        "question_count": len(results),
        "passed_count": sum(item["passed"] for item in results),
        "failed_count": sum(not item["passed"] for item in results),
        "api_success_rate": sum(item["http_status"] == 200 for item in results) / len(results),
        "expected_document_hit_rate": (
            sum(item["expected_document_hit"] for item in expected_with_docs) / len(expected_with_docs)
            if expected_with_docs else None
        ),
        "mrr": statistics.mean(reciprocal_ranks) if reciprocal_ranks else None,
        "citation_integrity_rate": (
            sum(item["citation_integrity"] for item in grounded) / len(grounded)
            if grounded else None
        ),
        "response_schema_rate": sum(item["schema_valid"] for item in results) / len(results),
        "confidence_validity_rate": sum(item["confidence_valid"] for item in results) / len(results),
        "abstention_accuracy": (
            sum(item["abstained"] is True for item in abstention_cases) / len(abstention_cases)
            if abstention_cases else None
        ),
        "latency_ms": {
            "min": min(latencies),
            "max": max(latencies),
            "average": round(statistics.mean(latencies), 3),
            "median": round(statistics.median(latencies), 3),
            "p95": round(statistics.quantiles(latencies, n=20)[18], 3) if len(latencies) >= 20 else None,
        },
        "dataset_unchanged": before == after,
        "dataset_before": before,
        "dataset_after": after,
    }


def malformed_checks(client: httpx.Client) -> list[dict[str, Any]]:
    cases = [
        ("missing_question", {}),
        ("blank_question", {"question": " "}),
        ("invalid_domain", {"question": "What is a trademark?", "domain": "TAX"}),
        ("invalid_top_k", {"question": "What is a trademark?", "top_k": 99}),
    ]
    checks: list[dict[str, Any]] = []
    for name, payload in cases:
        started = time.perf_counter()
        response = client.post(ENDPOINT, json=payload)
        latency_ms = (time.perf_counter() - started) * 1000
        try:
            body = response.json()
        except json.JSONDecodeError:
            body = {"raw": response.text}
        checks.append({
            "name": name,
            "http_status": response.status_code,
            "latency_ms": round(latency_ms, 3),
            "passed": response.status_code == 422,
            "response": body,
        })
    return checks


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:8765")
    parser.add_argument("--timeout", type=float, default=30.0)
    args = parser.parse_args()
    documents, chunks = load_corpus()
    before = dataset_fingerprint()
    results: list[dict[str, Any]] = []
    with httpx.Client(base_url=args.base_url, timeout=args.timeout) as client:
        health = client.get("/health")
        health.raise_for_status()
        for case in QUESTIONS:
            started = time.perf_counter()
            response = client.post(ENDPOINT, json={"question": case.question})
            latency_ms = (time.perf_counter() - started) * 1000
            try:
                body = response.json()
            except json.JSONDecodeError:
                body = {"raw": response.text}
            results.append(evaluate_case(case, response, body, documents, chunks, latency_ms))
        malformed = malformed_checks(client)
    after = dataset_fingerprint()
    output = {
        "endpoint": ENDPOINT,
        "base_url": args.base_url,
        "generated_at_unix": time.time(),
        "summary": summarize(results, before, after),
        "malformed_request_checks": malformed,
        "results": results,
    }
    RESULTS_PATH.parent.mkdir(parents=True, exist_ok=True)
    RESULTS_PATH.write_text(json.dumps(output, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(output["summary"], ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
