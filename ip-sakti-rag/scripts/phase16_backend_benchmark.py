"""Phase 16 real-RAG benchmark. Run only against a live configured RAG service."""
import json, statistics, time
from pathlib import Path
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
CASES = json.loads((ROOT / "dataset/evaluation/phase16_rag_questions.json").read_text(encoding="utf-8"))
OUT = ROOT / "dataset/evaluation/results/phase16_backend_results.json"

def percentile(values, p):
    values = sorted(values); return values[round((len(values)-1)*p)]

rows=[]
for case in CASES:
    payload=json.dumps({"question":case["question"],"jurisdiction":case["jurisdiction"],"top_k":8}).encode()
    started=time.perf_counter()
    try:
        req=Request("http://127.0.0.1:8000/api/v1/ask",data=payload,headers={"Content-Type":"application/json"},method="POST")
        with urlopen(req,timeout=90) as reply:
            body=json.load(reply); status=reply.status; headers=dict(reply.headers.items())
        elapsed=(time.perf_counter()-started)*1000
        citations=body.get("citations",[]); sources=body.get("sources",[])
        answer=body.get("answer",""); abstained=body.get("abstained",False)
        docs={x.get("document_id") for x in citations+sources}
        expected=set(case.get("expected_documents",[]))
        grounded=not abstained and bool(answer) and bool(citations) and bool(sources)
        # Phase 20 fix: headers are case-insensitive on the wire; FastAPI emits
        # lowercase. Also accept X-RAG-context-chunks fallback for evidence count.
        lower_headers={str(k).lower(): v for k, v in headers.items()}
        def metric(name: str):
            return lower_headers.get(("x-rag-"+name.replace("_","-")).lower())
        def num(name):
            value=metric(name)
            try:
                return float(value) if value not in (None, "") else None
            except (TypeError, ValueError):
                return None
        evidence_count_raw=metric("evidence_count")
        if evidence_count_raw in (None, ""):
            evidence_count_raw=metric("context_chunks")
        try:
            chunks_retrieved=int(evidence_count_raw or 0)
        except (TypeError, ValueError):
            chunks_retrieved=0
        rag_used_raw=metric("evidence_passed_to_llm")
        if rag_used_raw in (None, ""):
            rag_used_raw=metric("rag_used")
        row={**case,"http_status":status,"total_backend_time_ms":round(elapsed,3),"routing_time_ms":None,"embedding_time_ms":None,"retrieval_time_ms":num("retrieval_ms"),"reranking_time_ms":num("reranking_ms"),"context_validation_time_ms":None,"LLM_time_ms":num("generation_ms"),"time_to_first_token_ms":None,"number_of_chunks_retrieved":chunks_retrieved,"top_similarity_relevance_score":max([x.get("score",0) for x in sources],default=None),"source_count":len(sources),"answer_generated":bool(answer),"grounded":grounded,"citations_returned":bool(citations),"RAG_USED":str(rag_used_raw).lower()=="true","expected_source_hit":not expected or bool(expected & docs),"error":None,"failure_reason":None,"answer":answer,"citations":citations,"sources":sources}
    except Exception as exc:
        row={**case,"http_status":None,"total_backend_time_ms":round((time.perf_counter()-started)*1000,3),"error":type(exc).__name__,"failure_reason":str(exc),"answer_generated":False,"grounded":False,"citations_returned":False,"RAG_USED":False}
    rows.append(row); print(case["id"],row["http_status"],row["total_backend_time_ms"],row.get("grounded"),flush=True)

times=[x["total_backend_time_ms"] for x in rows]
summary={"total_rag_questions":len(rows),"successful_http":sum(x.get("http_status")==200 for x in rows),"grounded_answers":sum(x.get("grounded") for x in rows),"citation_success":sum(x.get("citations_returned") for x in rows),"average_backend_total_ms":round(statistics.mean(times),3),"median_backend_total_ms":round(statistics.median(times),3),"p95_backend_total_ms":round(percentile(times,.95),3)}
OUT.parent.mkdir(parents=True,exist_ok=True); OUT.write_text(json.dumps({"summary":summary,"results":rows},indent=2),encoding="utf-8")
print(json.dumps(summary,indent=2))
