import json,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]; sys.path.insert(0,str(ROOT))
from app.models import QueryRequest
from app.service import get_service
cases=json.loads((ROOT/'dataset/evaluation/phase16_rag_questions.json').read_text())
svc=get_service()
for c in cases:
 if c['id'] not in {'Q03','Q04','Q06','Q07','Q13','Q18','Q22'}: continue
 r=svc.query(QueryRequest(query=c['question'],jurisdiction=c['jurisdiction']))
 print(json.dumps({'id':c['id'],'answer':r.answer,'abstained':r.abstained,'metrics':r.metrics,'evidence_count':len(r.evidence),'evidence_docs':[e.document_id for e in r.evidence],'citations':[x.model_dump() for x in r.citations],'limitations':r.limitations},default=str),flush=True)
