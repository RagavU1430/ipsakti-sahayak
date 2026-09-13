import json,time,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from app.models import QueryRequest
from app.service import get_service
from app.retrieval.query_analysis import analyze_query
cases=json.loads((ROOT/'dataset/evaluation/phase16_rag_questions.json').read_text())
failed={'Q03','Q04','Q06','Q07','Q08','Q09','Q13','Q18','Q22','Q23'}
svc=get_service(); out=[]
for c in cases:
 if c['id'] not in failed: continue
 a=analyze_query(QueryRequest(query=c['question'],jurisdiction=c['jurisdiction']))
 t=time.perf_counter(); cand=svc.retriever.retrieve(a); rt=(time.perf_counter()-t)*1000
 ev=svc.reranker.rerank(a,cand,svc.settings.top_k)
 out.append({'id':c['id'],'domains':a.domains,'intent':a.intent,'legal_identifiers':a.legal_identifiers,'candidate_count':len(cand),'retrieve_ms':round(rt,2),'top_candidates':[{'document_id':x.document_id,'chunk_id':x.chunk_id,'vector_score':x.vector_score,'lexical_score':x.lexical_score,'fusion_score':x.fusion_score} for x in cand[:8]],'reranked':[{'document_id':x.document_id,'chunk_id':x.chunk_id,'score':x.reranker_score} for x in ev[:8]]})
(ROOT/'dataset/evaluation/results/phase16_failure_audit.json').write_text(json.dumps(out,indent=2),encoding='utf8')
print(json.dumps(out,indent=2))
