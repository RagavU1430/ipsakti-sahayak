# Voice V2 Release Validation

Date: 2026-09-06  
Scope: Voice acceptance and bounded Gemini TTS reliability only. The frozen multilingual text/RAG architecture and dataset were not changed.

## Executive result

The missing bounded TTS fallback and duplicate-playback defects are fixed. Live calls prove that the observed HTTP 429 is a primary-model quota/rate-limit condition under a burst, not a Malayalam translation defect: EN, HI, and TA succeeded on `gemini-3.1-flash-tts-preview`; later TE, KN, and ML requests received primary-model 429 responses, while the configured `gemini-2.5-flash-preview-tts` fallback produced valid target-language WAV audio. Malayalam succeeded both on its first fallback-assisted request and after cooldown.

Voice release is still **NOT RELEASE READY**. A real person has not completed the required EN/HI/TA/TE/KN/ML microphone/STT matrix, and browser playback was directly observed only for Tamil. These items are marked BLOCKED, not inferred from unit tests.

## Runtime and architecture

- Fresh current-source RAG worker: port 8000, `GET /health` = `{"status":"ok"}`.
- Fresh current-source packaged backend: port 8080, `GET /health` = `{"status":"ok"}`.
- `GET /api/v1/voice/health`: UP; Gemini multimodal STT and Gemini TTS configured; all six language codes advertised.
- Runtime path remains: MediaRecorder → `/api/v1/voice/ask` → Gemini STT → existing `QuestionService` or `ConversationService` → existing `TranslationService` → router → frozen RAG → answer translation → Gemini TTS → frontend audio.
- No second reasoning, translation, RAG, or voice path was introduced.

## Root cause and evidence

Three concrete causes existed:

1. The provider previously had one TTS model and converted 429/5xx into a terminal generic failure.
2. `AudioPlayerBar` generated a new non-English TTS request on every replay and had no synchronous in-flight guard. Replay and rapid clicks could therefore amplify quota usage.
3. Live request order made the issue appear Malayalam-specific. In the controlled run, the first three primary-model calls (EN/HI/TA) succeeded. Subsequent TE/KN/ML primary calls received 429. KN and ML immediately succeeded on the fallback model. This pattern supports a burst/quota or primary-model rate limit, not a Malayalam language defect.

Sanitized logs record only model, language, HTTP status, attempt, retry decision, bytes, and latency. They contain no text secrets, authorization headers, or API key.

## Minimal fix

- Added ordered `voice.tts-model`, `voice.tts-fallback-models`, and `voice.tts-max-attempts` configuration.
- Candidate models are de-duplicated and hard-clamped to at most two attempts.
- Fallback occurs only for HTTP 429, HTTP 5xx, or a timeout.
- HTTP 401/403 remains an explicit provider-configuration failure; malformed/non-retryable output does not trigger random probing.
- Exhaustion returns HTTP 503 with code `TTS_UNAVAILABLE`; it never substitutes English audio.
- Frontend synthesis errors now preserve the backend error code/message.
- A synchronous request guard prevents concurrent duplicate generation. Generated audio is retained for replay and cleared when text or language changes.

Models tested:

- Primary: `gemini-3.1-flash-tts-preview`
- Fallback: `gemini-2.5-flash-preview-tts`

## Automated fallback and failure evidence

- Primary 429 → one fallback → valid RIFF/WAV: PASS.
- Primary 429 + fallback 503 → exactly two requests → `TTS_UNAVAILABLE`: PASS.
- Duplicate and repeated model names are de-duplicated; configured value 99 still yields at most two candidates: PASS.
- Two rapid Indic playback clicks trigger one frontend synthesis call: PASS.
- Replay uses cached audio and triggers no new synthesis call: PASS in unit and live browser evidence.
- Explicit frontend TTS failure alert: PASS.
- Empty/missing audio and unsupported language controller responses: PASS.
- Unsupported MIME and oversized audio stop before STT: PASS.
- Blank STT output returns `STT_EMPTY` and cannot generate a fake answer: PASS.
- Permission-denied, unavailable-device, recorder error, timeout, and network-error user messages were code-audited; real device/provider fault injection was not performed.

## Live TTS matrix

Duration is calculated for generated 24 kHz, mono, 16-bit PCM WAV as `(bytes - 44) / 48000`.

| Language | Live result | Model behavior | MIME / validity | Bytes | Approx. duration | TTS |
|---|---|---|---|---:|---:|---|
| EN | HTTP 200 | Primary success | `audio/wav`, RIFF | 176,684 | 3.68 s | PASS |
| HI | HTTP 200 | Primary success | `audio/wav`, RIFF | 161,324 | 3.36 s | PASS |
| TA | HTTP 200 | Primary success | `audio/wav`, RIFF | 165,164 | 3.44 s | PASS |
| TE | Initial primary 429 and malformed fallback; cooldown request HTTP 200 | Cooldown request used fallback successfully | `audio/wav`, RIFF | 98,490 | 2.05 s | PASS after cooldown |
| KN | HTTP 200 | Primary 429 → fallback success | `audio/wav`, RIFF | 152,250 | 3.17 s | PASS |
| ML | HTTP 200 first request and HTTP 200 cooldown request | Primary 429 → fallback success both times | `audio/wav`, RIFF | 177,210 / 198,330 | 3.69 / 4.13 s | PASS |

The initial Telugu malformed/non-audio fallback was correctly returned as `TTS_FAILED`; it did not trigger a third model attempt. The subsequent controlled cooldown request succeeded. This is retained as provider-instability evidence rather than hidden.

## Malayalam-specific result

1. First measured ML request: primary 429, fallback HTTP 200, WAV 177,210 bytes.
2. Second request after cooldown: primary 429, fallback HTTP 200, WAV 198,330 bytes.
3. Replay logic: cached audio reuse is proven by the frontend unit test and by a live Tamil replay where the backend success count increased once for generation and did not increase for replay.
4. If both bounded candidates fail, the tested result is explicit `TTS_UNAVAILABLE`, never English audio.

Verdict for Malayalam TTS: **PASS**. The remaining 429 is an observable primary-provider quota limitation successfully handled by the bounded fallback.

## Browser evidence

- Production frontend served by the packaged backend at `localhost:8080`.
- Tamil selector and Tamil response rendering: PASS.
- Grounded response displayed citation `IND-PAT-ACT-1970`, source title, section metadata, confidence, and Tamil answer.
- Playback moved from `Preparing audio` to `Pause audio`; elapsed time advanced to 0:27.
- The long Tamil answer used primary 429 → fallback success and generated a 1,498,170-byte WAV.
- Replay restarted at 0:01 using the same browser audio object. Backend synthesis/success counts did not increase for replay.
- Browser console captured no errors during the accepted playback flow.
- The automated browser could not provide native spoken microphone input. One microphone-button interaction did not establish a semantic STT result.

Browser verdict: **BLOCKED** for the complete six-language microphone/playback matrix; **PASS** for the directly executed Tamil text-to-audio playback and replay-deduplication flow.

## Final acceptance matrix

The query translation, RAG, and answer translation columns reference the unchanged passing multilingual text regression. They do not substitute for microphone evidence.

| Language | STT | Query Translation | RAG | Answer Translation | TTS | Playback | Overall |
|---|---|---|---|---|---|---|---|
| EN | BLOCKED | PASS / N/A | PASS | PASS / N/A | PASS | BLOCKED | BLOCKED |
| HI | BLOCKED | PASS | PASS | PASS | PASS | BLOCKED | BLOCKED |
| TA | BLOCKED | PASS | PASS | PASS | PASS | PASS | BLOCKED |
| TE | BLOCKED | PASS | PASS | PASS | PASS | BLOCKED | BLOCKED |
| KN | BLOCKED | PASS | PASS | PASS | PASS | BLOCKED | BLOCKED |
| ML | BLOCKED | PASS | PASS | PASS | PASS | BLOCKED | BLOCKED |

## Voice legal and conversational safety

- Automated Voice tests prove canonicalization of spoken `section 3P` → `Section 3(p)` and `section three ee` → `Section 3(e)`; Section 377 travels through the existing question path and TTS.
- Frozen multilingual tests continue to cover Section 6, TKDL, NBA, ABS, citations, abstention, and conversation language switching.
- A live microphone-based legal-identifier, false-premise/abstention, and TA/HI/additional-language follow-up matrix was not executed. Strict Voice legal/conversation acceptance is therefore BLOCKED even though the shared text path and automated Voice bridge pass.

## Regression results

- Backend: **203 passed, 0 failed, 0 errors, 0 skipped** across 42 suites.
- RAG: **77 passed, 30 skipped**, 6 dependency deprecation warnings.
- Frontend: **31 passed, 0 failed** across 10 files.
- Build: **PASS**, TypeScript + Vite, 75 modules transformed. One existing non-fatal mixed static/dynamic import warning remains for `VoiceChatOverlay.tsx`.

## Security audit

- Frontend production bundle files containing `GEMINI_API_KEY`, Gemini endpoint, or `x-goog-api-key`: 0.
- Git-tracked files matching Gemini/secret-key value patterns: 0.
- Live Voice logs containing authorization or `x-goog-api-key` headers: 0.
- ElevenLabs source/config references after the requested revert: 0.
- Gemini calls remain backend-only.

Security verdict: **PASS**.

## Dataset integrity

Before: `827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d`  
After:  `827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d`

Result: **PASS — exact match**.

## Release decision

- Multilingual text release: **PASS**.
- TTS fallback: **PASS**.
- Six-language TTS generation: **PASS**.
- Malayalam TTS: **PASS** with observable primary-model quota pressure handled by fallback.
- Voice STT: **BLOCKED — no actual human microphone matrix**.
- Complete browser matrix: **BLOCKED — playback directly observed only for Tamil**.
- Final verdict: **NOT RELEASE READY**.

Exact remaining blocker: a human must complete and record the EN/HI/TA/TE/KN/ML microphone/STT semantic matrix, including language/routing/final-language checks, and directly confirm playback for the five remaining languages. The legal-identifier, abstention, and conversational voice cases must be included before changing the strict verdict.
