# TK Overlap Implementation

## Architecture

Phase 3 adds Traditional Knowledge overlap analysis as a Spring Boot intelligence layer on top of the existing RAG service.

```text
React frontend
-> Spring Boot /api/v1/tk/overlap
-> TranslationService
-> GeminiTranslationProvider for non-English input/output only
-> TkQueryAnalyzer
-> existing RagClient
-> existing Python RAG /api/v1/ask
-> TkEvidenceAnalyzer
-> deterministic classification, confidence, abstention
-> response with preserved citations and sources
```

No new RAG system, vector database, corpus, chunker, embedding pipeline, or dataset file was created.

Direct browser navigation is served through the Spring Boot SPA forwarder:

```text
GET /tk -> React TK Overlap page
```

## API

Endpoint:

```text
POST /api/v1/tk/overlap
```

Request:

```json
{
  "description": "A turmeric and neem herbal formulation for traditional Ayurvedic therapeutic use in India.",
  "language": "en"
}
```

Response:

```json
{
  "classification": "POTENTIAL_TK_OVERLAP",
  "confidence": 0.7162,
  "overlap_types": ["INGREDIENT_OVERLAP", "TRADITIONAL_USE_OVERLAP"],
  "explanation": "...",
  "evidence": [],
  "recommendation": "...",
  "citations": [],
  "sources": [],
  "abstained": false,
  "language": "en",
  "detected_language": "en",
  "processing_language": "en"
}
```

## Classification Rules

Supported classifications:

- `NO_TK_OVERLAP_FOUND`
- `POTENTIAL_TK_OVERLAP`
- `STRONG_TK_OVERLAP`
- `INSUFFICIENT_EVIDENCE`

The classifier is deterministic and rule-based. It uses:

- whether RAG abstained,
- RAG confidence,
- presence of citations and sources,
- number of overlap dimensions,
- source score strength,
- traditional-knowledge terms in the retrieved RAG answer.

It does not ask Gemini to determine overlap.

## Overlap Types

Supported overlap type vocabulary:

- `INGREDIENT_OVERLAP`
- `TRADITIONAL_USE_OVERLAP`
- `FORMULATION_OVERLAP`
- `PREPARATION_METHOD_OVERLAP`
- `PROCESS_OVERLAP`
- `KNOWLEDGE_DOMAIN_OVERLAP`
- `GEOGRAPHIC_OR_COMMUNITY_OVERLAP`
- `BIOLOGICAL_RESOURCE_OVERLAP`

Only overlap types supported by query signals and retrieved evidence are returned.

## Evidence Model

Evidence comes only from the existing Python RAG response through `RagClient`.

The TK layer preserves:

- document name,
- document ID,
- section,
- page,
- authority,
- source URL,
- chunk ID,
- source score where available.

## Multilingual Flow

Supported languages:

- `en` English
- `hi` Hindi
- `ta` Tamil
- `te` Telugu
- `kn` Kannada
- `ml` Malayalam

English bypasses translation.

For non-English:

```text
user description
-> Gemini translation to English
-> TK/RAG analysis in English
-> translate explanation and recommendation back
-> preserve classification, overlap types, confidence, abstention, citations, and sources unchanged
```

## Gemini-Only Translation

Active code uses:

```text
TranslationService -> TranslationProvider -> GeminiTranslationProvider
```

Gemini is used only for translation. It is not used as the TK knowledge source and does not generate legal conclusions.

Live Gemini verification on 2026-09-02 confirmed the configured API key can call `generateContent`.

Verified compatible text-generation models for the default fallback chain:

- `gemini-2.5-flash`
- `gemini-2.5-flash-lite`
- `gemini-3.1-flash-lite`
- `gemini-3.5-flash-lite`
- `gemini-flash-lite-latest`

Models removed from the default fallback chain after live probing:

- `gemini-2.5-pro` returned 404 for this key/request despite being listed.
- `gemini-1.5-flash` returned 404.
- `gemini-1.5-pro` returned 404.

## Security

- `GEMINI_API_KEY` is server-side only.
- The frontend does not call Gemini.
- The frontend has no Gemini key environment variable.
- Direct `/tk` page loading is handled by Spring Boot static SPA forwarding; the page does not expose backend secrets.
- Error responses use existing backend error envelopes.
- The active code/resources scan found no Bhashini references.

## Legal Safety

The TK engine avoids legal certainty claims such as:

- patent invalidity,
- guaranteed rejection,
- theft accusations,
- community ownership claims without evidence.

The result is a system-generated screening assessment, not legal advice or an official government classification.

## Limitations

- Live Gemini translation was verified for the current local key without exposing the key.
- Kannada and Malayalam TK smoke tests needed explicit preserved legal identifiers such as `Section 3(p)` / `traditional knowledge` to retrieve non-abstained TK evidence reliably.
- TK overlap quality is bounded by the frozen RAG corpus and existing retrieval behavior.
- The engine does not add or consult TKDL or any external TK corpus.
- Historical documentation and raw downloaded source HTML may still mention Bhashini, but active application code no longer does.
