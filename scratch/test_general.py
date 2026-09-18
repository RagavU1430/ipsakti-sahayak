import json
import urllib.request

req = urllib.request.Request(
    'http://localhost:8080/api/v1/questions',
    data=b'{"question":"Tell me a short science joke","jurisdiction":"INDIA","language":"en"}',
    headers={'Content-Type': 'application/json', 'X-Dev-User-Id': 'test-user'}
)
res = urllib.request.urlopen(req)
print('Status:', res.status)
for k, v in res.headers.items():
    if any(h in k.lower() for h in ['timing', 'ipsakti', 'request-id']):
        print(f'  {k}: {v}')
body = json.loads(res.read().decode('utf-8'))
print('Answer:', body.get('answer'))
print('Route:', body.get('route'))
