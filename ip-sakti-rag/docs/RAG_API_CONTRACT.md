# RAG API contract

The official backend/runtime boundary is `POST /api/v1/ask`. The older `POST /rag/query` endpoint remains available for internal compatibility tests, but backend and frontend teams should integrate through `/api/v1/ask`.

## `POST /api/v1/ask`

Request:

```json
{
  "question": "What are the requirements for registering a trademark in India?",
  "domain": "TRADEMARK",
  "jurisdiction": "INDIA",
  "top_k": 8
}
```

Only `question` is required. It is normalized, must be non-empty after trimming, and is bounded to 4,000 characters. `top_k` is optional and must be between 1 and 20. `jurisdiction` accepts `INDIA`, `INTERNATIONAL`, or `BOTH`. `domain` accepts `PATENT`, `TRADEMARK`, `GI`, `COPYRIGHT`, `DESIGN`, `PLANT_VARIETY`, `ABS`, `FOOD`, `AYURVEDA`, or `INTERNATIONAL`.

Successful grounded response:

```json
{
  "answer": "Based on the retrieved provisions, ...",
  "confidence": 0.9062,
  "abstained": false,
  "citations": [
    {
      "document": "The Trade Marks Act, 1999",
      "document_id": "IND-TM-ACT-1999",
      "page": 11,
      "section": null,
      "authority": "Government of India / IP India",
      "source_url": "https://ipindia.gov.in/trade-marks-resources-act",
      "chunk_id": "IND-TM-ACT-1999-0102-0264b5427a61"
    }
  ],
  "sources": [
    {
      "document_id": "IND-TM-ACT-1999",
      "score": 0.7655
    }
  ]
}
```

Grounded answers are generated only from retrieved evidence. Citation metadata is not trusted from the model: the backend maps used evidence IDs to chunk/document metadata and validates citations programmatically before returning an answer.

General fallback response:

```json
{
  "answer": "I checked the verified IP-SAKTI corpus first, but the question does not appear to match that IP/legal corpus. This is a general question outside the available IP-SAKTI legal sources, so I cannot provide a corpus-grounded legal citation for it.",
  "confidence": 0.35,
  "abstained": false,
  "citations": [],
  "sources": []
}
```

General fallback answers are returned only after query processing and RAG relevance comparison determine that the verified corpus does not contain sufficiently relevant evidence for a grounded answer. They are explicitly citation-free: empty `citations` and `sources` means the answer is not grounded in the IP-SAKTI corpus. Local/dev mode uses a deterministic safe fallback. Production may enable remote general fallback with `RAG_ENABLE_GENERAL_LLM=true` plus provider credentials.

Abstained response:

```json
{
  "answer": "I couldn't find sufficiently relevant supporting evidence in the available authoritative sources.",
  "confidence": 0.18,
  "abstained": true,
  "citations": [],
  "sources": []
}
```

An abstention is reserved for fail-closed cases such as unsupported exact legal identifiers, quarantined sources, security/prompt-exfiltration requests, grounded generation failure, or citation validation failure. Invalid requests return 422. Infrastructure/configuration failures return 503 with a controlled `RAG_UNAVAILABLE` error and no stack trace, secret, prompt, embedding, or database detail.

## Runtime behavior

The endpoint follows a RAG-first policy:

`question -> query processing -> metadata detection/filtering -> hybrid vector + keyword retrieval -> score fusion -> legal reranking -> relevance/sufficiency comparison`.

If relevant corpus evidence is available, the runtime continues:

`grounded generation -> citation validation -> deterministic confidence -> RAG-grounded answer`.

If relevant corpus evidence is not available, the runtime returns:

`general fallback answer -> no citations -> no sources`.

If a strict safety/integrity check fails, the runtime returns:

`fail-closed abstention`.

The runtime can use the local canonical corpus for safe development/testing or Supabase for production retrieval when configured. Supabase credentials and model/provider keys must come from environment variables; service-role credentials must never be exposed to the frontend.

## Configuration

Required for Supabase-backed production retrieval:

```env
SUPABASE_URL=
SUPABASE_ANON_KEY=
SUPABASE_SERVICE_ROLE_KEY=
EMBEDDING_PROVIDER=openrouter
EMBEDDING_MODEL=openai/text-embedding-3-small
OPENROUTER_API_KEY=
LLM_PROVIDER=openrouter
LLM_MODEL=openai/gpt-4.1-mini
LLM_API_KEY=
```

Important runtime knobs:

```env
RAG_STORAGE_BACKEND=auto
RAG_ENABLE_LLM=false
RAG_ENABLE_GENERAL_LLM=false
OPENROUTER_MODEL=openai/gpt-4.1-mini
RAG_TOP_K=8
RAG_CANDIDATE_K=24
RAG_MIN_SCORE=0.10
RAG_ABSTENTION_THRESHOLD=0.12
RAG_SIMILARITY_THRESHOLD=0.10
RAG_MAX_CONTEXT_CHARS=18000
```

`GET /health` reports process health only. It is not a production dependency-readiness probe.

## Backend multilingual wrapper

The Spring Boot backend now exposes multilingual support around the canonical RAG boundary. The Python RAG service remains English/canonical and remains the source of legal evidence.

Supported backend request languages are the project-configured values:

- `en`
- `hi`
- `ta`

When `language` is omitted, existing English behavior is preserved. Non-English requests are translated to the canonical processing language before RAG/classification/regulatory analysis, and only user-facing textual response fields are translated back.

Translation does not modify:

- citation metadata
- `document_id`
- `chunk_id`
- section/page identifiers
- retrieval scores
- confidence values
- answer/source/status/classification enums

### `POST /api/v1/questions`

Request:

```json
{
  "question": "இந்தியாவில் வர்த்தக முத்திரையை பதிவு செய்ய என்ன தேவைகள்?",
  "jurisdiction": "INDIA",
  "language": "ta"
}
```

Response includes multilingual metadata in addition to the existing fields:

```json
{
  "answer": "...",
  "answerType": "rag_grounded",
  "confidence": 0.91,
  "abstained": false,
  "jurisdiction": "INDIA",
  "language": "ta",
  "detected_language": "ta",
  "processing_language": "en",
  "intent": "TRADEMARK",
  "citations": [],
  "sources": []
}
```

`answerType` remains one of:

- `rag_grounded`
- `general_fallback`
- `abstained`

### `POST /api/v1/formulations/classify`

The formulation classifier accepts optional `language`. The existing five categories are preserved:

- `CLASSICAL_DRUG`
- `PATENT_PROPRIETARY`
- `PHYTOPHARMACEUTICAL_NEW_DRUG`
- `AYURVEDA_AAHAR_NUTRACEUTICAL`
- `COSMETIC`

Only textual fields such as `reason` and clarification `questions` are translated. Classification/status enums and citations remain canonical.

### `POST /api/v1/regulatory/analyze`

The regulatory analyzer accepts optional `language`. The existing engines and status values are preserved:

- `SECTION_3P`
- `SECTION_3E`
- `ABS`
- `GRATK`

Only user-facing text such as `reason`, `questions`, and engine `considerations` is translated. Legal identifiers such as `Section 3(p)`, `Section 3(e)`, `ABS`, `GRATK`, citation metadata, confidence, and source scores remain unchanged.

### Conversation & History Management (`/api/v1/conversations`)

The Spring Boot backend persists conversations and user query/answer history with grounded citations and source linkages:

- `POST /api/v1/conversations`: Create conversation (returns `201 Created` with UUID `id`).
- `GET /api/v1/conversations`: List user's conversations with pagination (`page`, `size`, `sort`).
- `GET /api/v1/conversations/{id}`: Get full conversation transcript including messages, citations, and sources.
- `PATCH /api/v1/conversations/{id}`: Update conversation title.
- `DELETE /api/v1/conversations/{id}`: Cascade delete conversation and all associated messages/citations.
- `POST /api/v1/conversations/{id}/messages`: Submit message in conversation context, invoke intelligence pipeline, store user and assistant messages, citations, and sources, and return the grounded answer.

Security & Multi-Tenancy:
- All `/api/v1/conversations/**` endpoints enforce strict tenant isolation. Authenticated users can only read, update, message, or delete conversations they own (`403 Forbidden` if mismatched).

### Bhashini configuration

Bhashini integration is environment-based. Do not commit credentials.

```env
BHASHINI_ENABLED=false
BHASHINI_BASE_URL=
BHASHINI_API_KEY=
BHASHINI_USER_ID=
BHASHINI_TRANSLATION_SERVICE_ID=
BHASHINI_PIPELINE_ID=
BHASHINI_CONNECT_TIMEOUT=2s
BHASHINI_READ_TIMEOUT=15s
```

If non-English translation is requested and Bhashini is unavailable, disabled, times out, returns an HTTP error, or returns a malformed response, the backend returns a controlled translation error. It does not fabricate translations and does not convert abstentions into grounded answers.

