import httpx, time, sys
url = 'http://localhost:8000/api/v1/ask'
p = {"question": "What is trademark registration in India?", "jurisdiction": "INDIA"}
start = time.perf_counter()
try:
    r = httpx.post(url, json=p, timeout=30.0)
    ms = (time.perf_counter() - start) * 1000
    print("Latency_ms:", round(ms, 2))
    print("Status:", r.status_code)
    if r.status_code == 200:
        d = r.json()
        print("Answer_snippet:", d.get("answer", "")[:120], "...")
        ev = d.get("evidence", [])
        print("Evidence_count:", len(ev))
        if ev:
            print("First_evidence_chunk:", ev[0].get("chunk_id", "N/A")[:60])
    else:
        print("Error:", r.text[:300])
except Exception as e:
    print("Failed:", e)
