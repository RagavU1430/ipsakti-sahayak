"""Deterministic client benchmark through the same conversation API used by React."""
import json, statistics, time, uuid
import os
from pathlib import Path
from urllib.request import Request, urlopen
ROOT=Path(__file__).resolve().parents[1]
CASES=json.loads((ROOT/'dataset/evaluation/phase16_rag_questions.json').read_text())
GENERAL=["Hello, how are you?","What is the capital of France?","Explain photosynthesis in one sentence.","Write a Python function to reverse a string.","What is the weather today?","Who wrote Hamlet?","Give me a pasta recipe.","What is 2 plus 2?","Tell me a joke.","How do I improve my sleep? "]
def post(path, payload, rid):
 req=Request('http://127.0.0.1:8080'+path,data=json.dumps(payload).encode(),headers={'Content-Type':'application/json','Accept':'application/json','X-Dev-User-Id':'demo-user','X-Request-ID':rid},method='POST')
 with urlopen(req,timeout=90) as r: return r.status,json.load(r),dict(r.headers.items())
rows=[]
start_i=int(os.getenv('PHASE16_START','0')); end_i=int(os.getenv('PHASE16_END',str(len(CASES))))
for c in CASES[start_i:end_i]:
 rid=str(uuid.uuid4()); t=time.perf_counter()
 try:
  _,conv,_=post('/api/v1/conversations',{'title':'Phase 16'},rid); cid=conv['id']
  tmsg=time.perf_counter(); status,body,h=post(f'/api/v1/conversations/{cid}/messages',{'question':c['question'],'jurisdiction':c['jurisdiction'],'language':'en'},rid); end=time.perf_counter()
  rows.append({**c,'request_id':rid,'http_status':status,'client_total_ms':round((end-tmsg)*1000,3),'request_setup_ms':round((tmsg-t)*1000,3),'backend_request_id':h.get('X-Request-ID'),'route':body.get('route'),'answer_type':body.get('answerType'),'answer':body.get('answer',''),'grounded':body.get('answerType','').lower()=='rag_grounded' and not body.get('abstained',False) and bool(body.get('citations')),'citation_valid':bool(body.get('citations')),'citations':body.get('citations',[]),'sources':body.get('sources',[]),'abstained':body.get('abstained',False),'retrieval_ms':None,'llm_ms':None,'frontend_rendered':bool(body.get('answer')),'error':None})
 except Exception as e: rows.append({**c,'request_id':rid,'http_status':None,'client_total_ms':round((time.perf_counter()-t)*1000,3),'error':str(e),'route':None,'grounded':False,'citation_valid':False})
 print(c['id'],rows[-1]['http_status'],rows[-1]['client_total_ms'],rows[-1]['route'],flush=True)
if start_i != 0: GENERAL=[]
gen=[]
for q in GENERAL:
 rid=str(uuid.uuid4()); t=time.perf_counter()
 try:
  _,conv,_=post('/api/v1/conversations',{'title':'Phase 16 general'},rid); tmsg=time.perf_counter(); status,body,h=post(f"/api/v1/conversations/{conv['id']}/messages",{'question':q,'jurisdiction':'AUTO','language':'en'},rid); end=time.perf_counter(); gen.append({'question':q,'request_id':rid,'http_status':status,'client_total_ms':round((end-tmsg)*1000,3),'route':body.get('route'),'retrieval_called':body.get('route')!='GENERAL','answer':body.get('answer',''),'error':None})
 except Exception as e: gen.append({'question':q,'request_id':rid,'http_status':None,'client_total_ms':round((time.perf_counter()-t)*1000,3),'route':None,'retrieval_called':None,'error':str(e)})
OUT=ROOT/'dataset/evaluation/results/phase16_conversation_results.json'
existing={'rag':[],'general':[]}
if OUT.exists() and start_i != 0:
 existing=json.loads(OUT.read_text(encoding='utf8'))
rag_by={x['id']:x for x in existing.get('rag',[])}; rag_by.update({x['id']:x for x in rows}); rows=list(rag_by.values())
existing_gen=existing.get('general',[]) if start_i != 0 else gen
summary={'rag_count':len(rows),'rag_http_success':sum(x.get('http_status')==200 for x in rows),'rag_correlated':sum(x.get('request_id')==x.get('backend_request_id') for x in rows),'rag_render_available':sum(bool(x.get('answer')) for x in rows),'rag_grounded':sum(bool(x.get('grounded')) for x in rows),'rag_citation_valid':sum(bool(x.get('citation_valid')) for x in rows),'general_count':len(existing_gen),'general_http_success':sum(x.get('http_status')==200 for x in existing_gen),'general_no_retrieval':sum(not x.get('retrieval_called') for x in existing_gen)}
OUT.write_text(json.dumps({'rag':rows,'general':existing_gen,'summary':summary},indent=2),encoding='utf8')
