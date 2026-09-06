# Voice Chat V2 Debug Report

Date: 2026-09-04

## Defects traced and repaired

### V2-001 — browser-dependent bypass of the voice API

The old overlay preferred Web Speech Recognition in Brave and submitted recognized text to `/api/v1/questions`. Other browsers used MediaRecorder and `/api/v1/voice/ask`. The same feature therefore had different intelligence and failure paths.

Repair: replaced both branches with one MediaRecorder path and one multipart endpoint.

### V2-002 — no real backend TTS

The old Gemini voice provider returned `null` from synthesis, so “speech-to-speech” depended on browser-native speech synthesis.

Repair: added a real Gemini TTS provider, parsed actual inline audio, wrapped raw PCM as WAV, returned its MIME type, and used a browser `<audio>` element.

### V2-003 — spoken legal identifier retrieval regression

Live Gemini STT rendered `Section 3(p)` as `Section 3P`. The typed canonical question retrieved correctly, but the noncanonical spoken form could abstain.

Repair: normalized narrowly defined spoken Section 3(p)/3(e) variants before the existing router/RAG path. The raw transcript is not rewritten in the UI.

Verification: the unchanged synthetic recording transcribed as `What is Section 3P of the Patents Act?`, routed to PATENT RAG, returned confidence 0.7533, two citations, one source, and real WAV audio.

### V2-004 — TTS terminated by the shared text-client timeout

The first V2 end-to-end request reached STT and RAG successfully but the TTS call exceeded the shared 10-second Gemini text timeout and returned a controlled server failure.

Repair: created a dedicated voice Gemini client with `VOICE_REQUEST_TIMEOUT=90s` while keeping the frontend timeout at 120 seconds. Retest returned HTTP 200; TTS took 15.413 seconds and the whole request 30.485 seconds.

### V2-005 — missing multipart audio returned HTTP 500

Spring rejected the request before controller invocation with `MissingServletRequestPartException`, which the global fallback mapped to HTTP 500.

Repair: added a voice-controller boundary handler returning HTTP 400 with code `AUDIO_REQUIRED`, plus a regression test. The running API now returns the documented error.

### V2-006 — duplicate recording start race

Two near-simultaneous start events could request a second stream before React completed its state update.

Repair: `useVoiceRecorder.start()` now returns immediately when its recorder is already recording.

### V2-007 — oversized multipart body returned HTTP 500

Spring's multipart resolver rejected a 10,485,761-byte file before `VoiceService` validation, so the generic exception handler returned HTTP 500.

Repair: added a narrow `MaxUploadSizeExceededException` mapping to HTTP 413 with code `AUDIO_TOO_LARGE`, plus an automated mapping test. The same live upload then returned HTTP 413.

## Live provider behavior

- STT: Gemini returned 429 for `gemini-2.5-flash` and `gemini-2.5-flash-lite`; bounded fallback to `gemini-3.1-flash-lite` succeeded. No credential was logged.
- Translation: the same bounded existing fallback chain handled 429s and succeeded with `gemini-3.1-flash-lite`.
- TTS: `gemini-3.1-flash-tts-preview` returned PCM audio, converted to valid WAV.
- RAG: voice reused the existing endpoint and preserved citations/sources. No dataset or retrieval code was changed.

## Latency observations

| Run | STT observed | Core processing | TTS | Total |
|---|---:|---:|---:|---:|
| Synthetic English Section 3(p) | about 10.0 s including fallback | RAG 5.733 s | 15.413 s | 30.485 s |
| Brave Tamil general | about 5.0 s including fallback | translation/general/translation 8.614 s | 6.827 s | 20.419 s |
| Brave Tamil Ayurvedic RAG | about 5.7 s including fallback | translation/RAG/translation 21.290 s | 52.262 s | 79.252 s |

The slowest measured stage was TTS for the long Tamil legal answer.

## Remaining unverified failure modes

Live microphone denial/disconnection, deliberate network loss, forced autoplay blocking, and native human speech in Hindi, Telugu, Kannada, and Malayalam were not executed. These are not marked as passes.
