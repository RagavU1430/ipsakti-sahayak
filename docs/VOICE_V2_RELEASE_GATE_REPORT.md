# Voice Chat V2 Release Gate Report

Date: 2026-09-04

## Decision

**NOT RELEASE READY**

Voice V2 is implemented and a real Tamil Brave microphone-to-browser-playback flow completed, but the mandatory six-language human acceptance matrix and explicit human-heard confirmation are incomplete. Automated success and one real language do not satisfy the requested release gate.

## Implementation gate

| Requirement | Status |
|---|---|
| Old voice-only implementation removed/replaced | PASS |
| One voice interface over existing intelligence | PASS |
| Multipart voice API | PASS |
| Real Gemini STT | PASS |
| Existing router/RAG/general/translation reused | PASS |
| Programmatic citations preserved | PASS |
| Real Gemini TTS and WAV response | PASS |
| Browser MediaRecorder capture and local preview | PASS in Brave/Tamil |
| Browser answer playback | PASS by media-state observation |
| Conversation ownership path reused | PASS by architecture/tests; live authenticated voice persistence UNVERIFIED |
| No corpus/retrieval changes | PASS |

## Automated regression gate

- Spring Boot: **184 passed, 0 failed, 0 skipped** across 39 reports.
- React/Vitest: **24 passed, 0 failed** across 8 files.
- Frontend TypeScript lint: **PASS**.
- Frontend production build: **PASS** (72 modules transformed).
- Python RAG pytest: **75 passed, 30 skipped, 0 failed**, 6 warnings. Skips are configuration-dependent tests and are not represented as passes.

## Live integration gate

- `GET /api/v1/voice/health`: HTTP 200, STT and TTS configured.
- Synthetic English Section 3(p): HTTP 200 through real STT, RAG, citations, and TTS; 30.485 s.
- Real Brave Tamil general question: completed through STT, GENERAL, translation, TTS; 20.419 s.
- Real Brave Tamil Ayurvedic question: completed through STT, RAG, translation, three citations, two sources, TTS, and browser playback; 79.252 s.
- Invalid language: HTTP 400.
- Missing audio: HTTP 400 after repair.
- Unsupported MIME: HTTP 415.
- Oversized multipart recording: HTTP 413 `AUDIO_TOO_LARGE` after repair.
- Approved/unapproved CORS: HTTP 200/403 respectively.
- Existing text question API regression check: PASS; Section 3(p) routed to PATENT RAG with two citations and no abstention.

## Frozen dataset gate

`ip-sakti-rag/dataset/canonical/chunks.jsonl`

SHA-256 after implementation:

`827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d`

Result: **MATCH**.

## Blocking acceptance items

1. Perform the three required English human Brave scenarios: `Hi`, `What is a patent?`, and the Section 3(p) question.
2. Perform native human microphone flows for Hindi, Telugu, Kannada, and Malayalam.
3. Have a human explicitly confirm audible output for each of the six languages.
4. Live-test permission denial, microphone disconnection, forced autoplay blocking, and deliberate network failure.
5. Live-test authenticated conversation persistence and ownership for a voice message.

The system must remain **NOT RELEASE READY** until every blocking acceptance item is recorded as an actual pass.
