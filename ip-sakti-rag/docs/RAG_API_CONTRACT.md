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

An abstention is a successful HTTP 200 response because the RAG system completed safely and refused to overclaim. Invalid requests return 422. Infrastructure/configuration failures return 503 with a controlled `RAG_UNAVAILABLE` error and no stack trace, secret, prompt, embedding, or database detail.

## Runtime behavior

The endpoint executes:

`question -> query processing -> metadata detection/filtering -> hybrid vector + keyword retrieval -> score fusion -> legal reranking -> evidence sufficiency -> grounded generation -> citation validation -> deterministic confidence -> answer or abstention`.

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
OPENROUTER_MODEL=openai/gpt-4.1-mini
RAG_TOP_K=8
RAG_CANDIDATE_K=24
RAG_MIN_SCORE=0.10
RAG_ABSTENTION_THRESHOLD=0.12
RAG_SIMILARITY_THRESHOLD=0.10
RAG_MAX_CONTEXT_CHARS=18000
```

`GET /health` reports process health only. It is not a production dependency-readiness probe.
