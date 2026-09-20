"""Phase 5 — retrieval baselines: vector-only / lexical-only / hybrid / hybrid+reranker.

Ground truth: document-level expected_source_ids from latest.jsonl end_to_end set.
Recall@K = |expected ∩ retrieved@K| / |expected|; MRR = mean 1/rank(first expected hit).
Chunk-level relevance judgments do not exist -> chunk-level recall marked NOT MEASURED.
"""
import json, sys
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
src = [json.loads(l) for l in (ROOT / "dataset/evaluation/results/latest.jsonl").read_text(encoding="utf-8").splitlines() if l.strip()]
e2e = [q for q in src if q.get("suite") == "end_to_end" and q.get("expected_source_ids")]
from app.retrieval.local_store import LocalCorpusStore
from app.retrieval import HybridRetriever, LegalFeatureReranker, analyze_query
from app.core.config import get_settings
from app.models import QueryRequest
settings = get_settings()
store = LocalCorpusStore(settings.canonical_chunks_path)
retriever = HybridRetriever(store, settings.candidate_k)
reranker = LegalFeatureReranker()

def docs(ranked):
    seen = []
    for r in ranked:
        did = r["document_id"] if isinstance(r, dict) else r.document_id
        if did not in seen:
            seen.append(did)
    return seen

def recall_mrr(ranked_docs, expected, k):
    top = ranked_docs[:k]
    hit = [d for d in expected if d in top]
    recall = len(hit) / max(len(expected), 1)
    rr = 0.0
    for i, d in enumerate(ranked_docs, 1):
        if d in expected:
            rr = 1.0 / i
            break
    return recall, rr

modes = ["vector", "lexical", "hybrid", "hybrid_rerank"]
acc = {m: {"r5": [], "r8": [], "r10": [], "mrr": []} for m in modes}
dom = {}
for q in e2e:
    an = analyze_query(QueryRequest(query=q["question"], jurisdiction=None, top_k=8))
    vec = store.vector_search(an, settings.candidate_k)
    lex = store.keyword_search(an, settings.candidate_k)
    # hybrid fusion identical to HybridRetriever (reuse retrieve then split not possible; emulate)
    hyb = retriever.retrieve(an)
    hyb_dicts = [{"document_id": e.document_id, "chunk_id": e.chunk_id} for e in hyb]
    vec_docs = docs(vec)
    lex_docs = docs(lex)
    hyb_docs = docs(hyb_dicts)
    ranked = reranker.rerank(an, hyb, 8)
    hr_docs = [e.document_id for e in ranked]
    exp = q["expected_source_ids"]
    for name, dd in (("vector", vec_docs), ("lexical", lex_docs), ("hybrid", hyb_docs), ("hybrid_rerank", hr_docs)):
        r5, _ = recall_mrr(dd, exp, 5); r8, _ = recall_mrr(dd, exp, 8); r10, mrr = recall_mrr(dd, exp, 10)
        acc[name]["r5"].append(r5); acc[name]["r8"].append(r8); acc[name]["r10"].append(r10); acc[name]["mrr"].append(mrr)
    cat = q.get("category", "unknown")
    dom.setdefault(cat, []).append((vec_docs, lex_docs, hyb_docs, hr_docs, exp))
    print(q["question_id"], flush=True)

def avg(v):
    return round(sum(v) / max(len(v), 1), 4)
table = {m: {"Recall@5": avg(acc[m]["r5"]), "Recall@8": avg(acc[m]["r8"]), "Recall@10": avg(acc[m]["r10"]), "MRR": avg(acc[m]["mrr"]), "n": len(e2e)} for m in modes}
domrows = []
for cat, rows in dom.items():
    r = {}
    for i, m in enumerate(modes):
        r5 = sum(recall_mrr(dd[i], dd[4], 5)[0] for dd in rows) / len(rows)
        r10 = sum(recall_mrr(dd[i], dd[4], 10)[0] for dd in rows) / len(rows)
        mrr = sum(recall_mrr(dd[i], dd[4], 10)[1] for dd in rows) / len(rows)
        r[m] = {"Recall@5": round(r5, 4), "Recall@10": round(r10, 4), "MRR": round(mrr, 4)}
    fails = sum(1 for dd in rows if not (set(dd[3]) & set(dd[4])))
    domrows.append({"domain": cat, "n": len(rows), "hybrid_rerank_failures": fails, **r})
out = {"table": table, "by_domain": domrows, "corpus": getattr(store, "corpus_source", "unknown"),
    "candidate_k": settings.candidate_k, "note": "document-level ground truth; chunk-level recall NOT MEASURED"}
p = ROOT / "reports" / "retrieval_comparison.json"
p.write_text(json.dumps(out, indent=2), encoding="utf-8")
print(json.dumps(table, indent=2))
