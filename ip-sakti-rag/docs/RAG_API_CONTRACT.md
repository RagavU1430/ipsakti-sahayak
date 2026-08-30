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
