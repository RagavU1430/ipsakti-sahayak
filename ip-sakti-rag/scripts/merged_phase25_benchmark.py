"""Phase 2 — merged 25Q benchmark (in-process, no question changes)."""
import json, time, sys
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
cases = json.loads((ROOT / "dataset/evaluation/phase16_rag_questions.json").read_text(encoding="utf-8"))
from app.service import RAGService
from app.core.config import get_settings
from app.models import QueryRequest
svc = RAGService(settings=get_settings())
rows = []
for c in cases:
    t = time.perf_counter()
    try:
        r = svc.query(QueryRequest(query=c["question"], jurisdiction=c.get("jurisdiction", "INDIA"), top_k=8))
        m = r.metrics
        docs = {x.document_id for x in r.evidence} | {x.document_id for x in r.citations}
        exp = set(c.get("expected_documents", []))
        rows.append({**{k: c.get(k) for k in ("id", "question", "jurisdiction")},
            "route": "RAG", "corpus": m.get("corpus"), "RAG_USED": bool(m.get("rag_used")),
            "evidence_count": int(m.get("evidence_count", 0)), "context_chunks": len(r.evidence),
            "evidence_status": m.get("evidence_status"), "retrieval_ms": m.get("retrieval_ms"),
            "rerank_ms": m.get("reranking_ms"), "generation_ms": m.get("generation_ms"),
            "total_ms": m.get("total_ms"), "generator": m.get("generator"),
            "grounded": (not r.abstained and bool(r.citations)), "citation_success": bool(r.citations),
            "abstained": r.abstained, "expected_hit": (not exp or bool(exp & docs)),
            "answer": r.answer[:500]})
    except Exception as e:
        rows.append({"id": c.get("id"), "question": c.get("question"), "error": f"{type(e).__name__}: {e}",
            "RAG_USED": False, "grounded": False, "abstained": True,
            "total_ms": round((time.perf_counter() - t) * 1000, 2)})
    print(rows[-1].get("id"), rows[-1].get("RAG_USED"), rows[-1].get("grounded"), rows[-1].get("abstained"), flush=True)
g = sum(1 for r in rows if r.get("grounded")); a = sum(1 for r in rows if r.get("abstained"))
u = sum(1 for r in rows if r.get("RAG_USED"))
out = {"summary": {"n": len(rows), "rag_used": u, "grounded": g, "abstained": a,
    "avg_retrieval_ms": round(sum(r.get("retrieval_ms", 0) or 0 for r in rows) / len(rows), 2),
    "avg_total_ms": round(sum(r.get("total_ms", 0) or 0 for r in rows) / len(rows), 2),
    "corpus": rows[0].get("corpus") if rows else None,
    "generator_note": "mixed extractive/live-Gemini per intent; see per-row generator"}, "results": rows}
p = Path("reports/merged_phase25_results.json"); p.parent.mkdir(parents=True, exist_ok=True)
p.write_text(json.dumps(out, indent=2), encoding="utf-8")
print(json.dumps(out["summary"], indent=2))
