"""Phase 8 — candidate-K retrieval benchmark (no LLM; retrieval+rerank latency + doc-recall)."""
import json, sys, time
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
src = [json.loads(l) for l in (ROOT / "dataset/evaluation/results/latest.jsonl").read_text(encoding="utf-8").splitlines() if l.strip()]
e2e = [q for q in src if q.get("suite") == "end_to_end" and q.get("expected_source_ids")]
from app.retrieval.local_store import LocalCorpusStore
from app.retrieval import HybridRetriever, LegalFeatureReranker, analyze_query
from app.core.config import get_settings
from app.models import QueryRequest
import argparse
ap = argparse.ArgumentParser(); ap.add_argument("--k", nargs="+", type=int, default=[8, 12, 16, 24]); ap.add_argument("--limit", type=int, default=65)
a = ap.parse_args()
settings = get_settings()
store = LocalCorpusStore(settings.canonical_chunks_path)
reranker = LegalFeatureReranker()
rows = []
for k in a.k:
    ret = HybridRetriever(store, k)
    rec, mrr, rl, kl = [], [], [], []
    for q in e2e[:a.limit]:
        an = analyze_query(QueryRequest(query=q["question"], jurisdiction=None, top_k=8))
        t = time.perf_counter()
        cands = ret.retrieve(an)
        r_ms = (time.perf_counter() - t) * 1000
        t2 = time.perf_counter()
        ev = reranker.rerank(an, cands, 8)
        k_ms = (time.perf_counter() - t2) * 1000
        docs = []
        for e in ev:
            if e.document_id not in docs:
                docs.append(e.document_id)
        exp = q["expected_source_ids"]
        rec.append(len(set(exp) & set(docs[:10])) / len(exp))
        rr = 0.0
        for i, d in enumerate(docs, 1):
            if d in exp:
                rr = 1.0 / i
                break
        mrr.append(rr); rl.append(r_ms); kl.append(k_ms)
    rows.append({"K": k, "n": len(rec), "recall@10": round(sum(rec) / len(rec), 4),
        "mrr": round(sum(mrr) / len(mrr), 4), "avg_retrieval_ms": round(sum(rl) / len(rl), 2),
        "avg_rerank_ms": round(sum(kl) / len(kl), 2)})
    print(rows[-1], flush=True)
p = ROOT / "reports" / "candidate_k_results.json"
p.write_text(json.dumps(rows, indent=2), encoding="utf-8")
print(p)
