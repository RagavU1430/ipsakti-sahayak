# Voice Chat V2 Live Test Matrix

Date: 2026-09-04

`PASS` means that exact stage was exercised against the running local system. `UNVERIFIED` is intentionally not inferred from unit tests or another language.

| Language | Capture | STT | Router | RAG/LLM | Translation | TTS | Playback |
|---|---|---|---|---|---|---|---|
| English | UNVERIFIED (human Brave); synthetic WAV PASS | PASS (synthetic WAV) | PASS | PASS (RAG) | N/A for English | PASS | UNVERIFIED in Brave |
| Hindi | UNVERIFIED | UNVERIFIED | UNVERIFIED by voice | UNVERIFIED by voice | PASS via live text pipeline | UNVERIFIED | UNVERIFIED |
| Tamil | PASS (real Brave microphone) | PASS | PASS | PASS (GENERAL and RAG runs) | PASS | PASS | PASS (browser media state observed) |
| Telugu | UNVERIFIED | UNVERIFIED | UNVERIFIED by voice | UNVERIFIED by voice | PASS via live text pipeline | UNVERIFIED | UNVERIFIED |
| Kannada | UNVERIFIED | UNVERIFIED | UNVERIFIED by voice | UNVERIFIED by voice | PASS via live text pipeline | UNVERIFIED | UNVERIFIED |
| Malayalam | UNVERIFIED | UNVERIFIED | UNVERIFIED by voice | UNVERIFIED by voice | PASS via live text pipeline | UNVERIFIED | UNVERIFIED |

## Required browser scenarios

| Scenario | Result | Evidence |
|---|---|---|
| English `Hi` | UNVERIFIED | No English human-microphone run was performed. |
| English `What is a patent?` | UNVERIFIED | No English human-microphone run was performed. |
| English Section 3(p) | PARTIAL | A synthetic WAV went through real Gemini STT, legal-reference normalization, RAG, citations, real TTS, and returned HTTP 200. |
| Natural Tamil | PASS through browser playback | Brave captured `வணக்கம். என் பெயர் ராகவ்.`; the system used GENERAL, translated the response, generated Tamil audio, and completed in 20.419 s. |
| Tamil domain/RAG | PASS through browser playback | Brave captured an Ayurvedic-market question; route RAG, domain AYURVEDA, confidence 0.9061, three citations, two sources, Tamil WAV, total 79.252 s. |
| Natural Hindi | UNVERIFIED | Native microphone flow not run. |
| Natural Telugu | UNVERIFIED | Native microphone flow not run. |
| Natural Kannada | UNVERIFIED | Native microphone flow not run. |
| Natural Malayalam | UNVERIFIED | Native microphone flow not run. |

Browser inspection showed the Tamil answer audio as a loaded Blob (`readyState=4`) and actively playing (`paused=false`, approximately 70.0/76.76 seconds). Whether a human audibly heard the result was not independently confirmed and remains a release-gate item.

## Negative/live checks

| Check | Result |
|---|---|
| Missing `audio` part | PASS: HTTP 400 `AUDIO_REQUIRED` after repair |
| Invalid language | PASS: HTTP 400 `INVALID_LANGUAGE` |
| Unsupported MIME | PASS: HTTP 415 `UNSUPPORTED_AUDIO` |
| Oversized multipart recording | PASS: HTTP 413 `AUDIO_TOO_LARGE` |
| Empty/silent speech | PASS in an earlier live provider run: controlled no-speech failure |
| Approved localhost CORS preflight | PASS: HTTP 200 with the configured origin |
| Unapproved origin | PASS: HTTP 403 |
| Gemini 429 fallback | PASS: first two models returned 429, third compatible model succeeded |
| Duplicate start protection | PASS by code/unit boundary; not stress-tested by human double-click |
| Microphone denied/disconnected | UNVERIFIED live |
| Network disconnected | UNVERIFIED live |
| Autoplay blocked fallback | Implemented; browser-blocked branch not forced live |
| TTS failure | PASS during debugging: controlled failure exposed; timeout root cause repaired |
| Repeat/second question | PASS: two consecutive Tamil recordings reached the endpoint and completed |
