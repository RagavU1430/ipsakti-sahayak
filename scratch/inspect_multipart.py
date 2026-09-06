import urllib.request
import urllib.error
import io, wave, struct, json

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

url = "http://localhost:8080/api/v1/voice/ask"
req = urllib.request.Request(url, data=bytes(body))
req.add_header("Content-Type", f"multipart/form-data; boundary={boundary}")

try:
    resp = urllib.request.urlopen(req, timeout=15)
    print("HTTP status:", resp.status)
    body_text = resp.read().decode("utf-8")
    data = json.loads(body_text)
    print("Status:", data.get("status"))
    print("Transcript:", repr(data.get("transcript")))
    print("Language:", data.get("language"))
    print("Answer:", repr(data.get("answer")[:80]))
    print("Confidence:", data.get("confidence"))
    print("Citations Count:", len(data.get("citations", [])))
except urllib.error.HTTPError as err:
    print("HTTP Error:", err.code)
    print("Response Body:", repr(err.read().decode("utf-8")))
