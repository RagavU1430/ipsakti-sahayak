# IP-SAKTI Sahayak: Voice Assistant Pre-Implementation Audit Report

**Date**: September 3, 2026  
**Auditor**: Lead Full-Stack Engineer  
**Phase**: Phase 0 — Discovery & Pre-Implementation Audit  
**Status**: Completed — No Blockers  

---

## 1. Executive Summary

This pre-implementation audit documents the baseline architecture, existing voice capabilities, verified test baselines, and dataset integrity before implementing the server-side Voice Assistant API and integrating it with the existing intelligence pipeline.

The core architectural invariant is strictly maintained:
> **ONE INTELLIGENCE PIPELINE. TWO INTERFACES.**  
> Text Interface & Voice Interface → Same Backend → Same Frozen RAG → Same Grounding → Same Citations → Same Confidence → Same Abstention.

---

## 2. Current Architecture Overview

```
Frontend (React + TypeScript + Vite)
        ↓  (Browser Web Speech STT / VoiceChatOverlay / REST API)
Spring Boot Backend (Java 25, Spring Security, JWT / Dev auth)
        ↓  (HTTP REST client)
FastAPI RAG Service (Python 3.11, Hybrid Retrieval, Reranking, Grounded Generation)
        ↓
Supabase Cloud PostgreSQL 17.6 + pgvector (Data & Conversations)
```

- **Frontend Directory**: `Frontend/`
- **Spring Boot Backend**: `ip-sakti-backend/`
- **FastAPI RAG Service**: `ip-sakti-rag/`
- **Authentication**: JWT Bearer auth with fallback `dev` user header in development mode; Spring Security filter chain with public endpoints for `/health`, `/health/ready`, `/api/v1/ask`, `/api/v1/questions`, `/api/v1/formulations/**`, `/api/v1/regulatory/**`, `/api/v1/tk/**`.
- **Database**: Supabase PostgreSQL 17.6 live on AP-South-1 (Mumbai), 13 relational tables, pgvector vector store, zero silent H2 fallback in production guarded by `DatabaseEnvironmentValidator.java`.
- **Multilingual Pipeline**: 6 Indian languages supported (English, Hindi, Tamil, Telugu, Kannada, Malayalam). Input query detected/normalized → canonical English retrieval → frozen English RAG → grounded answer → translated back to user's selected language.

---

## 3. Existing Voice Implementation Audit

The repository already contains frontend voice presentation components:
1. **`Frontend/src/hooks/useSpeechRecognition.ts`**:
   - Browser Web Speech API (`window.SpeechRecognition` / `webkitSpeechRecognition`).
   - Supports 6 Indian locales: `en-IN`, `hi-IN`, `ta-IN`, `te-IN`, `kn-IN`, `ml-IN`.
   - Real-time interim and final transcript handling with automatic error classification (`no-speech`, `not-allowed`, `network`, `aborted`).
2. **`Frontend/src/hooks/useSpeechSynthesis.ts`**:
   - Browser `window.speechSynthesis` wrapper.
   - Text cleaner `cleanTextForSpeech` stripping Markdown formatting, code blocks, URLs, and citations before playback.
   - Localized Indian voice selection (`hi-IN`, `ta-IN`, `te-IN`, `kn-IN`, `ml-IN`, `en-IN`).
3. **`Frontend/src/components/VoiceAssistantControls.tsx`**:
   - Micro-interaction microphone button with pulsating ring and speech readout button.
4. **`Frontend/src/components/VoiceChatOverlay.tsx`**:
   - Full-screen conversational UI resembling ChatGPT / Gemini Live.
   - Animated multi-layer orb with reactive equalizer states (`idle`, `listening`, `thinking`, `speaking`).
   - Language pills, live transcript view, assistant formatted reply card, and dock controls.

### Identified Gaps:
1. **No Backend Voice API**:
   - Currently, `VoiceChatOverlay.tsx` calls `askQuestion` with text transcripts produced in the browser.
   - There is no server-side `POST /api/v1/voice/ask` endpoint accepting raw audio uploads.
2. **No Backend Speech-to-Text (STT) Service**:
   - Backend cannot transcribe audio files or streams server-side when browser STT is unsupported or degraded.
3. **No Backend Text-to-Speech (TTS) Provider**:
   - Backend cannot generate audio response streams or base64 audio server-side.
4. **No Unified VoiceProvider Abstraction**:
   - Need `VoiceService`, `VoiceProvider`, `SpeechToTextProvider`, and `TextToSpeechProvider` in Spring Boot with Gemini multimodal audio capabilities.
5. **No Strict State Machine in Frontend**:
   - The UI uses informal states (`idle`, `listening`, `thinking`, `speaking`). Must adhere to normalized state machine (`IDLE`, `LISTENING`, `PROCESSING`, `TRANSCRIBING`, `THINKING`, `ANSWER_READY`, `SPEAKING`, `ERROR`).
6. **No Normalized Error Model**:
   - Voice errors must follow standard codes (`MIC_PERMISSION_DENIED`, `AUDIO_TOO_LARGE`, `NO_SPEECH_DETECTED`, `TRANSCRIPTION_FAILED`, etc.).

---

## 4. Baseline Test Metrics & Build Status

| Component | Test Runner | Executed | Passed | Failed | Skipped | Status |
|---|---|---|---|---|---|---|
| **Spring Boot Backend** | Maven / JUnit 5 | 151 | 151 | 0 | 0 | **PASS** |
| **FastAPI RAG Microservice** | Pytest | 105 | 75 | 0 | 30* | **PASS** |
| **React Frontend** | Vitest | 20 | 20 | 0 | 0 | **PASS** |
| **Frontend Production Build** | Vite / TypeScript | - | - | 0 | - | **PASS (1.47s)** |
| **RAG Baseline Script** | Python | - | - | 0 | - | **PASS** |

*\*Note: All executed tests passed; 30 RAG tests were intentionally skipped as designed.*

---

## 5. Baseline Dataset Integrity Verification

- **Target File**: `ip-sakti-rag/dataset/canonical/chunks.jsonl`
- **Expected Hash**: `827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d`
- **Actual Computed SHA-256**: `827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d`
- **Total Chunks**: 7,019 chunks
- **Status**: **LOCKED & VERIFIED (EXACT MATCH)**

---

## 6. Files Likely to Change vs. Files That Must Remain Frozen

### Files That Must Remain FROZEN:
- `ip-sakti-rag/dataset/canonical/chunks.jsonl` (LOCKED)
- `ip-sakti-rag/dataset/**` (LOCKED)
- `ip-sakti-rag/app/retrieval/**` (LOCKED)
- `ip-sakti-rag/app/generation/**` (LOCKED)
- `ip-sakti-backend/src/main/java/com/ipsakti/ip_sakti_backend/config/DatabaseEnvironmentValidator.java` (LOCKED)
- `ip-sakti-backend/src/main/resources/application-prod.yaml` (LOCKED)

### Files Likely to Change / Be Created:
- **Backend (New)**:
  - `com.ipsakti.ip_sakti_backend.voice.VoiceController`
  - `com.ipsakti.ip_sakti_backend.voice.VoiceService`
  - `com.ipsakti.ip_sakti_backend.voice.dto.*` (VoiceAskRequest, VoiceAskResponse, VoiceErrorResponse)
  - `com.ipsakti.ip_sakti_backend.voice.provider.*` (VoiceProvider, SpeechToTextProvider, TextToSpeechProvider, GeminiVoiceProvider)
- **Backend (Modified)**:
  - `SecurityConfig.java` (Allow `/api/v1/voice/**` in security filter chain)
  - `application.yaml` (Voice configuration properties, max upload sizes, timeouts)
- **Frontend (Enhanced)**:
  - `VoiceChatOverlay.tsx` (Complete state machine, backend fallback, audio recording)
  - `useSpeechRecognition.ts` / `useSpeechSynthesis.ts` (Robust error handling & terminal states)
- **Tests (New)**:
  - `VoiceControllerIntegrationTest.java`
  - `VoiceEquivalenceTest.java`
  - `VoiceMultilingualRegressionTest.java`

---

## 7. Verdict: Ready to Proceed

No architectural blockers were discovered. Proceeding to Phase 1: Voice Architecture Design and Provider Abstraction.
