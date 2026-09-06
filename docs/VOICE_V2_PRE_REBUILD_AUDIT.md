# Voice Chat V2 Pre-Rebuild Audit

Date: 2026-09-04

## Scope and protected systems

This audit covers only the voice interface. The canonical question path (`QuestionService` / `ConversationService`), QueryRouter, RAG service, translation service, authentication, persistence, and the frozen dataset are shared infrastructure and must remain unchanged.

Frozen `chunks.jsonl` SHA-256 before rebuild:

`827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d`

## Existing voice-only backend files

- `voice/VoiceController.java`
- `voice/VoiceService.java`
- `voice/config/VoiceProperties.java`
- `voice/dto/VoiceAskRequest.java`
- `voice/dto/VoiceAskResponse.java`
- `voice/dto/VoiceTranscript.java`
- `voice/exception/VoiceException.java`
- `voice/provider/SpeechToTextProvider.java`
- `voice/provider/TextToSpeechProvider.java`
- `voice/provider/VoiceProvider.java`
- `voice/provider/GeminiVoiceProvider.java`
- `VoiceControllerTest.java`, `VoiceEquivalenceTest.java`, and `VoiceMultilingualRegressionTest.java`

The package is isolated from the rest of the application except for deliberate calls into `QuestionService`, `ConversationService`, shared language/jurisdiction/answer DTOs, Gemini configuration, and Spring Security.

## Existing frontend voice files

Voice-overlay-specific and safe to replace:

- `api/voice.ts`
- `components/VoiceChatOverlay.tsx`
- `components/VoiceChatOverlay.test.tsx`

Shared with the existing text-chat UI and therefore retained:

- `components/VoiceAssistantControls.tsx`
- `components/AudioPlayerBar.tsx`
- `hooks/useSpeechRecognition.ts`
- `hooks/useSpeechSynthesis.ts`

The shared files provide optional browser dictation and browser-native read-aloud for typed chat. Removing them would break `AskPage`, `ConversationDetailPage`, and `ResultCard`, which are outside the rebuild scope.

## Existing endpoints and contract

- `GET /api/v1/voice/health`
- `POST /api/v1/voice/ask` as multipart (`audio`, `language`, `jurisdiction`, optional `conversationId`)
- A second JSON/base64 form of `POST /api/v1/voice/ask`

The response carries transcript, answer metadata, citations/sources, and `audioBase64`, but no audio MIME type.

## Configuration and dependencies

- No dedicated frontend audio dependency; the implementation uses browser `getUserMedia`, `MediaRecorder`, Web Speech Recognition, and Speech Synthesis.
- Backend uses Spring `RestClient` and the existing Gemini configuration/model candidate chain.
- Voice limits are in `VoiceProperties`. Before this audit, Spring multipart limits were not explicitly aligned with the 10 MB voice limit.
- Frontend backend URL comes from `VITE_BACKEND_BASE_URL`.

## Live evidence and known problems

1. Brave preferred Web Speech Recognition when available. That path sent text to `/api/v1/questions` and bypassed the server voice endpoint, so runtime behavior differed by browser.
2. `GeminiVoiceProvider.synthesize()` always returned `null`; no real backend TTS existed despite the old release report claiming a complete speech-to-speech flow.
3. The old overlay mixed two capture systems (Web Speech and MediaRecorder), two submission paths, and browser-native TTS, producing difficult-to-reason-about state transitions.
4. The MediaRecorder callback was tied to stale render state and previously suppressed later turns when a prior answer existed.
5. The uploaded filename and reconstructed Blob could claim WebM even when the recorder selected another MIME type.
6. Frontend voice fetch had no bounded timeout or normalized error mapping before the debugging pass.
7. Invalid language/jurisdiction strings silently defaulted instead of returning a clean client error.
8. Spoken legal notation can be transcribed as `Section 3P`; the same typed canonical query `Section 3(p)` retrieves correctly, while the noncanonical voice transcript can abstain.
9. Existing tests mock the microphone, STT, question service, translation, or provider. They prove contracts, not real microphone-to-playback operation.
10. A real Brave Tamil capture reached Gemini STT and the canonical GENERAL path in 13.927 seconds. A repeated silent capture reached the endpoint and returned `NO_SPEECH_DETECTED`. Real TTS remained absent.

## Removal decision

The three overlay/API files and the isolated backend `voice` package/tests are safe to remove and replace as a unit. Shared text-chat controls/hooks, translation, routing, RAG, security, and conversation services must not be deleted or redesigned.

## V2 boundary

V2 will keep one multipart endpoint and introduce a small explicit stack:

`MediaRecorder -> voice API -> STT provider -> existing QuestionService/ConversationService -> TTS provider -> browser audio element`

The backend remains solely responsible for routing. The client will not submit a route selector.
