# IP-SAKTI multilingual translation root-cause audit and fix

## 1. Executive summary

English remains the canonical RAG path. The corpus, chunks, embeddings, retrieval ranking, RAG implementation, and English answer pipeline were not changed. The multilingual failures were caused by weak immutable-content protection, a stale-cache retry, a prompt that could itself introduce a placeholder, an over-strict placeholder-order check, and a conversation handoff that retained the route but did not give a referential follow-up enough topic context for retrieval. These defects were corrected in the existing centralized Gemini translation and conversation paths.

Text translation is working end-to-end for EN, HI, TA, TE, KN, and ML. The strict overall verdict remains **NOT RELEASE READY** because an actual six-language microphone/STT run was not possible in this environment and live Malayalam TTS encountered provider HTTP 429 responses during repeated playback probing.

## 2. User-visible symptoms

- Indic legal answers could change protected terms, identifiers, or formatting.
- Some TA/TE/KN/ML answers failed with a malformed-translation response even though Gemini returned all protected identifiers.
- A Tamil follow-up such as “அது எவ்வளவு காலம் நீடிக்கும்?” retained the PATENT route but initially reached RAG without the patent topic and abstained as vague.
- Indic TTS can be temporarily unavailable when the configured Gemini TTS model is rate-limited.

## 3. Root cause

1. Provider-local regular expressions protected too little legal content and restoration did not prove token integrity.
2. The old cache key omitted prompt versioning and used Java `hashCode`, so prompt changes and retries could reuse stale output.
3. A literal placeholder example in the Gemini prompt was copied into translations whose input contained no placeholder.
4. Validation required placeholder order to remain unchanged. Gemini correctly moved whole placeholders for target-language grammar, so valid TE/KN/ML translations were rejected.
5. Integrity retry did not evict the cached malformed response.
6. Conversation history supplied only previous route/domain to the router; a referential question still reached translation/RAG without a canonical topic anchor.

## 4. Evidence proving the root cause

- Sanitized live traces showed expected token count `0`, returned token count `1` for no-token inputs: the token came from the prompt example.
- After removing that literal example, a Telugu response returned all 9 expected token identities exactly once, but in grammar-appropriate order; the old order check rejected it.
- Cache eviction plus one bounded retry recovered transient malformed responses.
- Before the conversation fix, the live Tamil follow-up routed `DOMAIN_RAG/PATENT` but abstained as vague. After the fix, the same browser sequence returned a grounded 20-year answer with `Section 53`, `Section 53(2)`, and `Section 55`.

## 5. Existing architecture

User language → query translation to English → intelligent router → existing English RAG → grounded English answer/evidence validation → answer translation to selected language → frontend. English bypasses translation.

## 6. Pipeline before the fix

The same architecture was present, but protection and validation were incomplete, cache identity was weak, endpoint list translation could bypass normal handling, and referential conversation context did not reach the effective RAG query.

## 7. Pipeline after the fix

`TranslationService` now protects immutable spans, invokes Gemini, restores only an exact token identity/count set, permits whole-token grammatical reordering, validates restoration, evicts malformed cache entries, retries once, and otherwise fails explicitly as `TRANSLATION_UNAVAILABLE`. Referential follow-ups receive a private canonical topic suffix in the effective request; the original user message remains unchanged in history.

## 8. Files changed

- `TranslationProtection.java`: centralized legal/identifier/Markdown placeholder policy and fail-closed restoration.
- `TranslationService.java`: unified query/answer/list protection, one integrity retry, sanitized trace metadata.
- `TranslationProvider.java`: cache invalidation contract.
- `GeminiTranslationProvider.java`: faithful prompt v4, temperature 0, SHA-256/versioned cache key, dynamic placeholder instruction, eviction.
- `ConversationService.java`: narrow multilingual referential-follow-up topic bridge.
- Focused multilingual, TK, integration, controller, and conversation tests.

No RAG or corpus file was modified.

## 9. Legal terminology policy

Canonical protected content includes Patent, Trademark, Copyright, Design, Geographical Indication, Traditional Knowledge, Access and Benefit Sharing, Biological Diversity Act, National Biodiversity Authority, Ministry of AYUSH, IP India, WIPO, FSSAI, CDSCO, TKDL, GRATK, NBA, ABS, GI, PCT, Act, Rule, Schedule, section references, source/document IDs, URLs, values, dates, percentages, and Markdown syntax. Natural-language explanation may be translated; protected legal identities may not be improvised.

## 10. Placeholder protection

Immutable spans become `[[IPSAKTI_TOKEN_NNNN]]`. Restoration requires every expected token exactly once and rejects missing, duplicate, changed, or unknown tokens. Whole placeholders may move to satisfy target grammar. Reserved-token injection in user input is rejected.

## 11. Cache behavior

Cache identity is `ip-sakti-legal-v4:source:target:SHA-256(protected text)`. Language direction and prompt version are therefore isolated. An integrity failure evicts that entry before the single retry.

## 12. Endpoint parity

Questions, conversations, formulation classification/readiness, regulatory analysis, TK overlap, and voice converge on `TranslationService` through their existing service paths. Citations and sources remain authoritative response metadata and are not translated as free text. Translation failure is explicit; no successful localized response silently substitutes English.

## 13. Six-language test matrix

The 25-case live Gemini matrix initially produced 20 successes and 5 protected-token false rejections. Targeted final-path reruns recovered all five (TA Ayurveda plus TA/TE/KN/ML TK/ABS), yielding cumulative 25/25 HTTP 200 evidence. The single-intent English router handles a combined “TKDL and ABS” prompt as ABS; this is an existing English multi-intent limitation, not translation drift.

| Language | General | Patent/legal | TK/ABS | Ayurveda | Text result |
| --- | --- | --- | --- | --- | --- |
| EN | PASS | PASS | PASS | PASS | PASS |
| HI | PASS | PASS | PASS | PASS | PASS |
| TA | PASS | PASS | PASS after fix | PASS after fix | PASS |
| TE | PASS | PASS | PASS after fix | PASS | PASS |
| KN | PASS | PASS | PASS after fix | PASS | PASS |
| ML | PASS | PASS | PASS after fix | PASS | PASS |

## 14. Semantic translation results

The fixed English answer fixture and live outputs preserve meaning, negation/uncertainty, list structure, numbers, percentages, dates, and canonical legal terms. Real Gemini output was reviewed for script correctness and legal-reference integrity, not merely for presence of a target script.

## 15. Legal identifier preservation

Automated tests cover `Section 3(p)`, `Section 3(e)`, `Section 6`, `Section 377`, NBA, IP India, WIPO, FSSAI, CDSCO, TKDL, and GRATK. Live patent and ABS citations retained canonical sections/document IDs. Result: **PASS (100% in executed cases)**.

## 16. False-premise and abstention results

Automated tests preserve explicit abstention and do not translate uncertainty into certainty. The frozen RAG tests, including grounding and abstention cases, pass. Result: **PASS for automated coverage**; the complete six-language live false-premise grid was not independently rerun.

## 17. Conversation-context results

Browser sequence Tamil patent → “அது எவ்வளவு காலம் நீடிக்கும்?” now returns a grounded patent-duration answer. A single conversation was then switched HI → TE → KN → ML → EN → TA; every greeting response followed the current selected language with no stale-language leakage. Result: **PASS**.

## 18. Voice parity

The voice path still uses the same `QuestionService`/`ConversationService`; Voice V2 was not rebuilt. Browser answers exposed audio controls in all six languages. Live Gemini TTS returned valid WAV payloads for TA (182,444 bytes), TE (203,564 bytes), and KN (186,284 bytes). Repeated ML playback attempts received provider HTTP 429. An actual microphone/STT six-language recording matrix was not available. Result: **FAIL for strict release gate**.

## 19. Backend tests

`mvnw.cmd test`: **197 passed, 0 failures, 0 errors, 0 skipped** across 41 suites.

## 20. RAG tests

`pytest -q`: **77 passed, 30 skipped**, 6 dependency deprecation warnings.

## 21. Frontend tests

Vitest: **10 files passed, 29 tests passed**.

## 22. Build result

`npm run build`: **PASS** (TypeScript and Vite; 75 modules transformed). Vite reports one non-fatal existing warning that `VoiceChatOverlay.tsx` is both static and dynamic imported.

## 23. Browser verification

The current packaged backend and production frontend were exercised at `localhost:8080`. The selector, six-language response switching, Tamil grounded citations, Tamil referential follow-up, history rendering, and audio controls were verified. Six simultaneous `/api/v1/questions` greeting requests all returned HTTP 200 in approximately 6.0 seconds without cross-request failure.

## 24. Dataset SHA-256 before and after

Before: `827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d`

After:  `827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d`

Result: **PASS — exact match**. The legacy `verify_rag_baseline.py` separately reports an incompatible deep-summary artifact schema, while its dataset hash, required files, manifest, and test-suite checks pass.

## 25. Remaining limitations

- Actual six-language microphone/STT acceptance still requires recorded native speech or a human microphone run.
- Gemini TTS can return HTTP 429; the voice provider currently has no equivalent bounded model fallback to the translation provider.
- Combined multi-intent prompts are reduced to one domain by the frozen English router.
- The legacy baseline verifier expects old deep-evaluation summary fields and should be updated separately without changing the dataset.

## 26. Final release verdict

**NOT RELEASE READY** under the strict master-prompt rules. Text translation, legal identifiers, citations, conversation switching, automated suites, concurrency, build, and dataset integrity pass. Strict Voice Parity does not pass because the actual microphone matrix was not executed and live ML TTS rate limiting remains observable.

## Required final summary

ROOT CAUSE: Incomplete centralized protection; unversioned weak cache identity; prompt-introduced placeholder; order-sensitive validation; retry without eviction; missing topic anchor for referential history follow-ups.

FIX: Central protection/restoration with exact identity/count validation and grammar-safe reordering; prompt v4; SHA-256/versioned language-direction cache; eviction plus one retry; sanitized traces; narrow conversation topic bridge.

LANGUAGE RESULTS: EN PASS, HI PASS, TA PASS, TE PASS, KN PASS, ML PASS (text translation).

LEGAL IDENTIFIER PRESERVATION: PASS.

CITATION INTEGRITY: 100% in executed automated/live cases.

ABSTENTION SAFETY: PASS in automated coverage.

CONVERSATION LANGUAGE SWITCHING: PASS.

VOICE PARITY: FAIL strict gate (microphone matrix not executed; ML TTS 429 observed).

BACKEND: 197 passed, 0 failed.

RAG: 77 passed, 30 skipped.

FRONTEND: 29 passed, 0 failed.

BUILD: PASS.

DATASET HASH: BEFORE = `827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d`; AFTER = `827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d`.

FINAL VERDICT: **NOT RELEASE READY**.

## 27. Final Voice V2 Validation

The dedicated validation is recorded in `docs/VOICE_V2_RELEASE_VALIDATION.md`.

- Current-source backend and RAG health: PASS on ports 8080 and 8000.
- STT matrix: **BLOCKED**; no human native-speech microphone run was available, so no STT language is marked PASS from mocks.
- Gemini TTS models: primary `gemini-3.1-flash-tts-preview`; ordered fallback `gemini-2.5-flash-preview-tts`; maximum two de-duplicated attempts.
- 429 behavior: first EN/HI/TA primary calls succeeded; subsequent TE/KN/ML primary calls returned 429. KN/ML fallback succeeded immediately, proving model/burst quota pressure rather than a Malayalam translation defect. TE initially received malformed fallback output and correctly stopped; a cooldown retry succeeded.
- Six-language TTS: valid non-zero RIFF/WAV evidence for EN 176,684 bytes, HI 161,324, TA 165,164, TE 98,490, KN 152,250, and ML 177,210/198,330.
- Malayalam: first request and cooldown request both returned HTTP 200 through the bounded fallback after primary 429.
- Duplicate-request audit: a synchronous frontend guard blocks concurrent generation; generated audio is reused on replay and reset on text/language change. Unit and live Tamil browser evidence pass.
- Browser: Tamil answer playback advanced to 0:27 and replay restarted without a second synthesis request. Remaining language playback and all real microphone tests are BLOCKED.
- Legal identifiers: Voice unit evidence passes for Section 3(p), Section 3(e), and Section 377; frozen text evidence passes for Section 6, TKDL, NBA, and ABS. Real spoken legal-identifier acceptance remains BLOCKED.
- Abstention and conversation: shared text paths remain PASS; microphone-based voice acceptance remains BLOCKED.
- Backend: **203 passed**; RAG: **77 passed, 30 skipped**; frontend: **31 passed**; build: **PASS**.
- Security: no Gemini key/provider header in frontend bundle, tracked files, logs, or browser console; Gemini remains server-side; ElevenLabs remains fully reverted.
- Dataset SHA-256 before/after: `827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d` / identical.

Voice release verdict: **NOT RELEASE READY**. The bounded TTS and Malayalam blockers are resolved, but the required real six-language microphone/STT matrix and remaining direct playback matrix have not been completed.
