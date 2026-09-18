import os
import json
import time
import urllib.request
from pathlib import Path

env_path = Path(__file__).resolve().parents[1] / ".env"
key = None
if env_path.exists():
    for line in env_path.read_text(encoding="utf-8").splitlines():
        if line.startswith("GEMINI_API_KEY="):
            key = line.split("=", 1)[1].strip().strip('"\'')

if not key:
    print("No GEMINI_API_KEY found")
    exit(1)

models = [
    "gemini-2.5-flash-lite",
    "gemini-2.5-flash",
    "gemini-3.1-flash-lite",
    "gemini-3.5-flash-lite",
    "gemini-flash-lite-latest",
]

for m in models:
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{m}:generateContent"
    body = json.dumps({"contents": [{"parts": [{"text": "Say hi in 3 words"}]}]}).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=body,
        headers={"Content-Type": "application/json", "x-goog-api-key": key},
    )
    t0 = time.perf_counter()
    try:
        res = urllib.request.urlopen(req, timeout=10)
        dt = (time.perf_counter() - t0) * 1000
        data = json.loads(res.read().decode("utf-8"))
        ans = data.get("candidates", [{}])[0].get("content", {}).get("parts", [{}])[0].get("text", "").strip()
        print(f"{m:25s} -> SUCCESS ({dt:.0f}ms) | Ans: {ans}")
    except Exception as e:
        dt = (time.perf_counter() - t0) * 1000
        print(f"{m:25s} -> FAILED  ({dt:.0f}ms) | Err: {e}")
