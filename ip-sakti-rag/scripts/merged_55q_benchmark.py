"""Phase 4 (+12 raw) — run existing latest.jsonl question set against merged runtime, unmodified."""
import json, sys, time
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
src = [json.loads(l) for l in (ROOT / "dataset/evaluation/results/latest.jsonl").read_text(encoding="utf-8").splitlines() if l.strip()]
from app.service import RAGService
from app.core.config import get_settings
from app.models import QueryRequest
svc = RAGService(settings=get_settings())
rows = []
for q in src:
    t = time.perf_counter()
    try:
        r = svc.query(QueryRequest(query=q["question"], jurisdiction=None, top_k=8))
        m = r.metrics
        rows.append({"question_id": q.get("question_id"), "suite": q.get("suite"), "category": q.get("category"),
            "expected_source_ids": q.get("expected_source_ids"), "expected_abstain": q.get("expected_abstain"),
            "RAG_USED": bool(m.get("rag_used")), "evidence_count": int(m.get("evidence_count", 0)),
            "evidence_status": m.get("evidence_status"), "retrieval_ms": m.get("retrieval_ms"),
            "rerank_ms": m.get("reranking_ms"), "generation_ms": m.get("generation_ms"),
            "total_ms": m.get("total_ms"), "generator": m.get("generator"),
            "abstained": r.abstained, "citations": len(r.citations),
            "grounded": (not r.abstained and bool(r.citations)),
            "answer": r.answer[:400]})
    except Exception as e:
        rows.append({"question_id": q.get("question_id"), "suite": q.get("suite"),
            "error": f"{type(e).__name__}: {e}", "RAG_USED": False, "grounded": False, "abstained": True,
            "total_ms": round((time.perf_counter() - t) * 1000, 2)})
    print(rows[-1]["question_id"], rows[-1].get("RAG_USED"), rows[-1].get("grounded"), rows[-1].get("abstained"), flush=True)
e2e = [r for r in rows if r.get("suite") == "end_to_end"]
adv = [r for r in rows if r.get("suite") == "adversarial"]
out = ROOT / "reports" / "merged_55q_results.json"
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps({"summary": {"n": len(e2e),
    "rag_used": sum(1 for r in e2e if r.get("RAG_USED")), "grounded": sum(1 for r in e2e if r.get("grounded")),
    "abstained": sum(1 for r in e2e if r.get("abstained")),
    "avg_retrieval_ms": round(sum(r.get("retrieval_ms", 0) or 0 for r in e2e) / max(len(e2e), 1), 2),
    "avg_total_ms": round(sum(r.get("total_ms", 0) or 0 for r in e2e) / max(len(e2e), 1), 2),
    "corpus": "v1-legacy"}, "results": e2e,
    "adversarial_raw": adv,
    "adversarial_summary": {"n": len(adv), "abstained": sum(1 for r in adv if r.get("abstained")),
    "grounded": sum(1 for r in adv if r.get("grounded"))}}, indent=2), encoding="utf-8")
print(out)
