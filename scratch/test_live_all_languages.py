import urllib.request
import urllib.error
import json
import os
import sys

# Ensure UTF-8 output
sys.stdout.reconfigure(encoding='utf-8')

BASE_URL = "http://localhost:8080"

LANGUAGES = [
    {
        "code": "en",
        "name": "English",
        "native": "English (IN)",
        "query": "What is Section 3(p) of the Patents Act, 1970?",
    },
    {
        "code": "hi",
        "name": "Hindi",
        "native": "हिन्दी",
        "query": "पेटेंट अधिनियम की धारा 3(p) क्या है?",
    },
    {
        "code": "ta",
        "name": "Tamil",
        "native": "தமிழ்",
        "query": "காப்புரிமைச் சட்டத்தின் பிரிவு 3(p) என்றால் என்ன?",
    },
    {
        "code": "te",
        "name": "Telugu",
        "native": "తెలుగు",
        "query": "పేటెంట్ చట్టంలోని సెక్షన్ 3(p) అంటే ఏమిటి?",
    },
    {
        "code": "kn",
        "name": "Kannada",
        "native": "ಕನ್ನಡ",
        "query": "ಪೇಟೆಂಟ್ ಕಾಯ್ದೆಯ ಸೆಕ್ಷನ್ 3(p) ಎಂದರೇನು?",
    },
    {
        "code": "ml",
        "name": "Malayalam",
        "native": "മലയാളം",
        "query": "പേറ്റന്റ് നിയമത്തിലെ സെക്ഷൻ 3(p) എന്താണ്?",
    },
]

def test_voice_health():
    print("==================================================")
    print("STEP 1: VERIFYING VOICE HEALTH & SUPPORTED LANGUAGES")
    print("==================================================")
    url = f"{BASE_URL}/api/v1/voice/health"
    req = urllib.request.Request(url)
    with urllib.request.urlopen(req, timeout=5) as resp:
        assert resp.status == 200
        data = json.loads(resp.read().decode('utf-8'))
        supported = data.get("supportedLanguages", [])
        print(f"Health Status: {data.get('status')}")
        print(f"Provider: {data.get('provider')}")
        print(f"Supported Languages Advertised: {supported}")
        for lang in LANGUAGES:
            assert lang["code"] in supported, f"Language {lang['code']} missing from voice health!"
        print("PASS: All 6 languages (en, hi, ta, te, kn, ml) supported and active.\n")

def test_real_audio_stt():
    print("==================================================")
    print("STEP 2: TESTING LIVE AUDIO STT WITH GEMINI VOICE")
    print("==================================================")
    wav_path = "scratch/sample_speech.wav"
    if not os.path.exists(wav_path):
        print(f"WAV file {wav_path} not found, skipping raw audio STT test.")
        return

    with open(wav_path, "rb") as f:
        wav_bytes = f.read()

    boundary = "----WebKitFormBoundaryVoiceLangTest"
    body = bytearray()
    body.extend(f"--{boundary}\r\n".encode("utf-8"))
    body.extend(b'Content-Disposition: form-data; name="audio"; filename="sample.wav"\r\n')
    body.extend(b"Content-Type: audio/wav\r\n\r\n")
    body.extend(wav_bytes)
    body.extend(b"\r\n")
    body.extend(f"--{boundary}\r\n".encode("utf-8"))
    body.extend(b'Content-Disposition: form-data; name="language"\r\n\r\n')
    body.extend(b"en\r\n")
    body.extend(f"--{boundary}--\r\n".encode("utf-8"))

    url = f"{BASE_URL}/api/v1/voice/ask"
    req = urllib.request.Request(url, data=bytes(body))
    req.add_header("Content-Type", f"multipart/form-data; boundary={boundary}")

    with urllib.request.urlopen(req, timeout=30) as resp:
        assert resp.status == 200
        data = json.loads(resp.read().decode("utf-8"))
        print(f"STT Pipeline Status: {data.get('status')}")
        print(f"Transcribed User Speech: '{data.get('transcript')}'")
        print(f"Confidence Score: {data.get('confidence')}")
        print(f"Citations Found: {len(data.get('citations', []))}")
        print(f"Answer Preview: {data.get('answer', '')[:120]}...\n")
        assert len(data.get("transcript", "")) > 0
        assert len(data.get("answer", "")) > 0
        print("PASS: Live voice audio transcribed and resolved via RAG successfully.\n")

def test_all_languages_multilingual_pipeline():
    print("==================================================")
    print("STEP 3: TESTING VOICE INTELLIGENCE FOR ALL 6 LANGUAGES")
    print("==================================================")

    results = []
    for lang in LANGUAGES:
        code = lang["code"]
        name = lang["name"]
        native = lang["native"]
        query = lang["query"]

        print(f"--- Testing Language: {name} ({native} - [{code.upper()}]) ---")
        print(f"Query: {query}")

        # Test through /api/v1/questions (the canonical intelligence pipeline used by VoiceService)
        payload = json.dumps({
            "question": query,
            "language": code,
            "jurisdiction": "INDIA"
        }).encode("utf-8")

        req = urllib.request.Request(
            f"{BASE_URL}/api/v1/questions",
            data=payload,
            headers={"Content-Type": "application/json"}
        )

        try:
            with urllib.request.urlopen(req, timeout=45) as resp:
                assert resp.status == 200
                data = json.loads(resp.read().decode("utf-8"))
                confidence = data.get("confidence", 0)
                abstained = data.get("abstained", False)
                citations = data.get("citations", [])
                answer = data.get("answer", "")

                print(f"  Result Status: HTTP 200 OK")
                print(f"  Confidence: {confidence:.2f}")
                print(f"  Abstained: {abstained}")
                print(f"  Statutory Citations: {len(citations)}")
                print(f"  Answer Preview: {answer[:100]}...")

                results.append({
                    "language": name,
                    "code": code,
                    "status": "PASS",
                    "citations": len(citations),
                    "confidence": confidence,
                    "answer_preview": answer[:60] + "..."
                })
        except Exception as e:
            print(f"  Result Status: FAILED ({e})")
            results.append({
                "language": name,
                "code": code,
                "status": f"FAIL: {e}"
            })

        print()

    print("==================================================")
    print("MULTILINGUAL VOICE INTELLIGENCE SUMMARY")
    print("==================================================")
    for r in results:
        status = r.get("status")
        print(f"[{status}] {r['language']} ({r['code'].upper()}): Citations={r.get('citations')}, Confidence={r.get('confidence')}")

if __name__ == "__main__":
    test_voice_health()
    test_real_audio_stt()
    test_all_languages_multilingual_pipeline()
