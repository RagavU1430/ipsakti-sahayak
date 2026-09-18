import json
import urllib.request

req = urllib.request.Request(
    'http://localhost:8080/api/v1/conversations',
    data=b'{"title":"test"}',
    headers={'Content-Type': 'application/json', 'X-Dev-User-Id': 'test-user'}
)
res = urllib.request.urlopen(req)
conv = json.loads(res.read())
cid = conv['id']
print('Created conv:', cid)

msg_req = urllib.request.Request(
    f'http://localhost:8080/api/v1/conversations/{cid}/messages',
    data=b'{"question":"What is Section 3(p) of the Patents Act?","jurisdiction":"INDIA","language":"en"}',
    headers={'Content-Type': 'application/json', 'X-Dev-User-Id': 'test-user'}
)
msg_res = urllib.request.urlopen(msg_req)
print('Response code:', msg_res.status)
print('Headers:')
for k, v in msg_res.headers.items():
    if any(h in k.lower() for h in ['timing', 'ipsakti', 'request-id', 'cors']):
        print(f'  {k}: {v}')
body = json.loads(msg_res.read().decode('utf-8'))
print('Answer snippet:', body.get('answer', '')[:100])
print('Route:', body.get('route'))
