import json,uuid,time
from concurrent.futures import ThreadPoolExecutor,as_completed
from pathlib import Path
from urllib.request import Request,urlopen
ROOT=Path(__file__).resolve().parents[1]
cases=json.loads((ROOT/'dataset/evaluation/phase16_rag_questions.json').read_text())
def run(c):
 rid=str(uuid.uuid4()); t=time.perf_counter()
 def p(path,payload):
  q=Request('http://127.0.0.1:8080'+path,data=json.dumps(payload).encode(),headers={'Content-Type':'application/json','Accept':'application/json','X-Dev-User-Id':'demo-user','X-Request-ID':rid},method='POST')
  with urlopen(q,timeout=90) as r:return r.status,json.load(r),dict(r.headers.items())
 try:
  _,cv,_=p('/api/v1/conversations',{'title':'Phase 16'}); s,b,h=p('/api/v1/conversations/'+cv['id']+'/messages',{'question':c['question'],'jurisdiction':c['jurisdiction'],'language':'en'}); return {**c,'request_id':rid,'backend_request_id':h.get('X-Request-ID'),'http_status':s,'client_total_ms':round((time.perf_counter()-t)*1000,3),'route':b.get('route'),'answer_type':b.get('answerType'),'answer':b.get('answer',''),'abstained':b.get('abstained',False),'grounded':b.get('answerType','').lower()=='rag_grounded' and not b.get('abstained',False) and bool(b.get('citations')),'citation_valid':bool(b.get('citations')),'citations':b.get('citations',[]),'sources':b.get('sources',[]),'frontend_rendered':bool(b.get('answer'))}
 except Exception as e:return {**c,'request_id':rid,'http_status':None,'client_total_ms':round((time.perf_counter()-t)*1000,3),'error':str(e),'grounded':False,'citation_valid':False}
with ThreadPoolExecutor(max_workers=8) as ex: rows=[f.result() for f in as_completed([ex.submit(run,c) for c in cases])]
rows.sort(key=lambda x:x['id']); out=ROOT/'dataset/evaluation/results/phase16_conversation_results.json'; out.write_text(json.dumps({'rag':rows,'summary':{'rag_count':len(rows),'rag_http_success':sum(x.get('http_status')==200 for x in rows),'rag_correlated':sum(x.get('request_id')==x.get('backend_request_id') for x in rows),'rag_render_available':sum(bool(x.get('answer')) for x in rows),'rag_grounded':sum(x.get('grounded',False) for x in rows),'rag_citation_valid':sum(x.get('citation_valid',False) for x in rows)}},indent=2),encoding='utf8'); print(json.dumps({'count':len(rows),'success':sum(x.get('http_status')==200 for x in rows),'correlated':sum(x.get('request_id')==x.get('backend_request_id') for x in rows),'grounded':sum(x.get('grounded',False) for x in rows)}))
