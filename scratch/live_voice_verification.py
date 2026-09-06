import urllib.request
import urllib.error
import json
import io
import wave
import struct
import math
import base64

BASE_URL = "http://localhost:8080"

def test_voice_health():
    print("\n--- 1. Testing GET /api/v1/voice/health ---")
    url = f"{BASE_URL}/api/v1/voice/health"
    req = urllib.request.Request(url)
    with urllib.request.urlopen(req, timeout=5) as resp:
        assert resp.status == 200, f"Expected 200, got {resp.status}"
        data = json.loads(resp.read().decode("utf-8"))
        print("Voice Health Response:", json.dumps(data, indent=2))
        assert data.get("status") == "UP"
        assert data.get("provider") == "gemini-multimodal"
        assert data.get("configured") is True
        print("PASS: Voice Health endpoint operational.")

def test_voice_cors_preflight():
    print("\n--- 2. Testing OPTIONS CORS Preflight on /api/v1/voice/ask ---")
    url = f"{BASE_URL}/api/v1/voice/ask"
    req = urllib.request.Request(url, method="OPTIONS")
    req.add_header("Origin", "http://localhost:5173")
    req.add_header("Access-Control-Request-Method", "POST")
    req.add_header("Access-Control-Request-Headers", "Content-Type,Authorization")
    with urllib.request.urlopen(req, timeout=5) as resp:
        assert resp.status == 200, f"Expected 200, got {resp.status}"
        allow_origin = resp.headers.get("Access-Control-Allow-Origin")
        assert allow_origin == "http://localhost:5173", f"CORS origin mismatch: {allow_origin}"
        print(f"PASS: CORS Preflight allowed for {allow_origin}")

def test_voice_empty_audio_rejection():
    print("\n--- 3. Testing Rejection of Empty Audio Payload ---")
    url = f"{BASE_URL}/api/v1/voice/ask"
    payload = json.dumps({"audioBase64": "", "mimeType": "audio/webm"}).encode("utf-8")
    req = urllib.request.Request(url, data=payload, headers={"Content-Type": "application/json"})
    try:
        urllib.request.urlopen(req, timeout=5)
        assert False, "Expected 400 Bad Request"
    except urllib.error.HTTPError as err:
        assert err.code == 400, f"Expected 400, got {err.code}"
        data = json.loads(err.read().decode("utf-8"))
        assert data.get("code") == "NO_AUDIO_CAPTURED"
        print("PASS: Empty audio correctly rejected with NO_AUDIO_CAPTURED.")

def test_voice_silence_detection():
    print("\n--- 4. Testing Silence / No-Speech Rejection ---")
    buf = io.BytesIO()
    with wave.open(buf, 'wb') as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(16000)
        for _ in range(8000): # 0.5s silence
            wf.writeframes(struct.pack('<h', 0))
    
    b64 = base64.b64encode(buf.getvalue()).decode('utf-8')
    url = f"{BASE_URL}/api/v1/voice/ask"
    payload = json.dumps({"audioBase64": b64, "mimeType": "audio/wav", "language": "EN"}).encode("utf-8")
    req = urllib.request.Request(url, data=payload, headers={"Content-Type": "application/json"})
    try:
        urllib.request.urlopen(req, timeout=15)
        assert False, "Expected 400 Bad Request for silence"
    except urllib.error.HTTPError as err:
        assert err.code == 400, f"Expected 400, got {err.code}"
        data = json.loads(err.read().decode("utf-8"))
        assert data.get("code") == "NO_SPEECH_DETECTED"
        print("PASS: Silent audio correctly returned NO_SPEECH_DETECTED.")

def test_voice_multipart_upload():
    print("\n--- 5. Testing Multipart Form-Data Upload ---")
    boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW"
    buf = io.BytesIO()
    with wave.open(buf, 'wb') as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(16000)
        for _ in range(8000):
            wf.writeframes(struct.pack('<h', 0))
    
    raw_wav = buf.getvalue()
    
    body = bytearray()
    body.extend(f"--{boundary}\r\n".encode("utf-8"))
    body.extend(b'Content-Disposition: form-data; name="audio"; filename="silence.wav"\r\n')
    body.extend(b'Content-Type: audio/wav\r\n\r\n')
    body.extend(raw_wav)
    body.extend(b"\r\n")
    body.extend(f"--{boundary}\r\n".encode("utf-8"))
    body.extend(b'Content-Disposition: form-data; name="language"\r\n\r\n')
    body.extend(b"ta\r\n")
    body.extend(f"--{boundary}--\r\n".encode("utf-8"))
    
    url = f"{BASE_URL}/api/v1/voice/ask"
    req = urllib.request.Request(url, data=bytes(body))
    req.add_header("Content-Type", f"multipart/form-data; boundary={boundary}")
    
    resp = urllib.request.urlopen(req, timeout=20)
    assert resp.status == 200, f"Expected 200, got {resp.status}"
    data = json.loads(resp.read().decode("utf-8"))
    assert "transcript" in data
    assert "answer" in data
    assert data.get("language") == "ta"
    assert data.get("status") in ["SUCCESS", "ABSTAINED"]
    print("PASS: Multipart audio successfully processed through STT + RAG pipeline.")

def main():
    print("==================================================")
    print("IP-SAKTI SAHAYAK VOICE ASSISTANT LIVE SMOKE TEST")
    print("==================================================")
    test_voice_health()
    test_voice_cors_preflight()
    test_voice_empty_audio_rejection()
    test_voice_silence_detection()
    test_voice_multipart_upload()
    print("\n==================================================")
    print("ALL LIVE VOICE SMOKE TESTS PASSED!")
    print("==================================================")

if __name__ == "__main__":
    main()
