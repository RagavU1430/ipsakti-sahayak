# IP-SAKTI Sahayak Voice Chat V2

Date: 2026-09-04

## Architecture

Voice V2 is an interface over the existing intelligence path:

`Brave microphone -> MediaRecorder -> POST /api/v1/voice/ask -> Gemini STT -> QuestionService or ConversationService -> existing router -> existing RAG/general path -> existing translation -> Gemini TTS -> WAV -> browser audio`

There is no voice-specific router, RAG, knowledge base, citation engine, or legal-answer generator. The client cannot select an internal route.

## Backend components

- `voice/VoiceController.java`: multipart API and provider health contract.
- `voice/VoiceService.java`: validation, STT orchestration, canonical question dispatch, TTS orchestration, and response assembly.
- `voice/provider/SpeechToTextProvider.java` and `TextToSpeechProvider.java`: provider boundaries.
- `GeminiSpeechToTextProvider.java`: real Gemini multimodal transcription with the existing verified generation-model chain and bounded fallback.
- `GeminiTextToSpeechProvider.java`: real Gemini TTS using `gemini-3.1-flash-tts-preview` by default.
- `PcmWaveEncoder.java`: wraps Gemini 24 kHz mono 16-bit PCM output in a valid WAV container.
- `VoiceClientConfig.java`: a dedicated bounded voice client so TTS is not terminated by the shared 10-second text-client timeout.
- Voice DTOs and `VoiceException`: predictable response/error contracts.

Spoken English variants such as `Section 3P`, `Section three pee`, `Section 3E`, and `Section three ee` are normalized before entering the existing intelligence pipeline. The raw transcript remains visible in the response. Existing multilingual placeholder protection preserves `Section 3(p)`, `Section 3(e)`, `ABS`, `GRATK`, and other protected legal identifiers during translation.

## Frontend components

- `hooks/useVoiceRecorder.ts`: one `getUserMedia`/`MediaRecorder` implementation, supported-MIME detection, non-empty Blob enforcement, duplicate-start guard, track cleanup, and local recording preview.
- `api/voice.ts`: one multipart client, a 120-second browser timeout, normalized errors, and server-audio Blob creation.
- `components/VoiceChatOverlay.tsx`: explicit start/stop controls and the states IDLE, LISTENING, PROCESSING, THINKING, ANSWER_READY, SPEAKING, and ERROR.

The answer uses the server-generated audio returned by the API. The browser attempts playback, retains native controls for replay, and displays a manual-play message if autoplay is blocked.

## API contract

### `POST /api/v1/voice/ask`

Content type: `multipart/form-data`

Fields:

- `audio` (required): supported non-empty audio file.
- `language` (optional, default `en`): `en`, `hi`, `ta`, `te`, `kn`, or `ml`.
- `jurisdiction` (optional, default `INDIA`): `INDIA`, `INTERNATIONAL`, or `AUTO`.
- `conversationId` (optional): an owned conversation UUID; requires an authenticated principal.

Successful JSON includes `transcript`, `language`, `jurisdiction`, `answer`, `answerType`, `route`, `domain`, `confidence`, `citations`, `sources`, `abstained`, `audioBase64`, `audioMimeType`, `status`, `latencyMs`, and optional persisted message identifiers.

### `GET /api/v1/voice/health`

Reports STT/TTS provider names and configured state, supported languages, permitted MIME types, and maximum audio bytes. It does not disclose credentials.

### Error contract

Voice errors use `{ "code": "...", "error": "...", "status": 400 }`. Implemented codes include `AUDIO_REQUIRED`, `AUDIO_EMPTY`, `AUDIO_TOO_LARGE`, `UNSUPPORTED_AUDIO`, `INVALID_LANGUAGE`, `INVALID_JURISDICTION`, `INVALID_CONVERSATION_ID`, `AUTH_REQUIRED`, `STT_FAILED`, `STT_EMPTY`, `TTS_FAILED`, `VOICE_PROVIDER_UNAVAILABLE`, and `TIMEOUT`.

## Configuration

All credentials remain server-side. `.env.example` contains placeholders only.

```text
GEMINI_API_KEY=
GEMINI_FALLBACK_MODELS=gemini-2.5-flash,gemini-2.5-flash-lite,gemini-3.1-flash-lite,gemini-3.5-flash-lite,gemini-flash-lite-latest
VOICE_ENABLED=true
VOICE_MAX_AUDIO_BYTES=10485760
VOICE_REQUEST_TIMEOUT=90s
GEMINI_TTS_MODEL=gemini-3.1-flash-tts-preview
GEMINI_TTS_VOICE=Kore
```

Spring multipart limits are 10 MB per file and 11 MB per request. The frontend timeout is 120 seconds, longer than the dedicated backend voice-provider timeout.

## Security and data handling

- The Gemini key is sent only from the backend in the `x-goog-api-key` header and is never returned or logged.
- MIME type, non-empty content, and application size limits are checked.
- Audio remains in memory and is not written to application storage.
- Conversation requests reuse existing authentication, ownership, authorization, persistence, and ordering logic.
- Configured localhost CORS allows the frontend origin; an unapproved origin was rejected with HTTP 403.

## Browser requirements

Brave must expose `navigator.mediaDevices.getUserMedia` and `MediaRecorder`, and the user must grant microphone permission. The implementation selects the first supported type from WebM/Opus, WebM, Ogg/Opus, or MP4 and sends the actual recorder MIME type.

## Known limitations

- Gemini quota produced HTTP 429 for the first two generation candidates during live testing; bounded fallback to `gemini-3.1-flash-lite` succeeded.
- A long translated RAG answer required about 79 seconds end to end. The TTS stage was the largest contributor.
- The six required native-language human microphone scenarios have not all been performed; see the live matrix and release gate.

