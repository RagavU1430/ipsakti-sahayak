# RAG-First Response Policy Repair Report

Generated: 2026-08-30

## Scope

Implemented a strict RAG-first response policy for the existing IP-SAKTI Sahayak RAG runtime.

No dataset rebuild, document download, canonical corpus modification, citation validation weakening, or chatbot replacement was performed.

## Policy Implemented

Every `/api/v1/ask` request now follows this decision structure:

1. Normalize/analyze the user question.
2. Compare the question against known legal/RAG domains.
3. Run RAG retrieval/reranking when a corpus domain is available.
4. If relevant RAG evidence is sufficient, produce a citation-validated RAG-grounded answer.
5. If relevant RAG evidence is unavailable or insufficient, produce a general fallback answer with no citations and no sources.
6. If the request hits a strict safety/integrity condition, fail closed with abstention.

## RAG-Grounded Path

When corpus evidence is relevant and sufficient, behavior is unchanged:

- answer is generated from retrieved evidence
- citations are mapped from actual retrieved chunks
- citations are validated programmatically
- confidence is deterministic
- `abstained=false`
- `citations` and `sources` are populated

## General Fallback Path

When the RAG corpus does not contain relevant/sufficient evidence:

- the system returns a general answer instead of a RAG abstention
- `abstained=false`
- `confidence=0.35`
- `citations=[]`
- `sources=[]`
- the answer states that the verified IP-SAKTI corpus was checked first
- the answer states that no IP-SAKTI citations are attached because RAG evidence was insufficient

Local/dev behavior is deterministic and does not require network credentials.

Remote general fallback can be enabled only with:

```env
RAG_ENABLE_GENERAL_LLM=true
OPENROUTER_API_KEY=
OPENROUTER_MODEL=
```

This avoids accidental local network calls and avoids fabricating credentials.

## Fail-Closed Abstention Still Preserved

The following still abstain rather than falling back to a general answer:

- unsupported exact legal identifiers, such as nonexistent sections/rules/articles
- quarantined source requests
- security/prompt/credential exfiltration attempts
- grounded generator failure where a RAG-grounded answer was required
- citation validation failure

This preserves citation integrity and prevents the fallback path from laundering unsupported legal claims.

## Files Modified

- `app/core/config.py`
- `app/generation/__init__.py`
- `app/generation/grounded.py`
- `app/retrieval/hybrid.py`
- `app/service.py`
- `tests/conftest.py`
- `tests/test_api.py`
- `tests/test_grounding.py`
- `docs/RAG_API_CONTRACT.md`
- `docs/RAG_FIRST_RESPONSE_POLICY_REPAIR_REPORT.md`

## Configuration Added

```env
RAG_ENABLE_GENERAL_LLM=false
```

Also added to `.env.example`.

## Verification

Unit/integration test command:

```powershell
python -m pytest
```

Result:

- 36 passed
- 5 warnings from PyMuPDF/SWIG import deprecations

Additional fix found during self-test:

- The remote grounded-generator path previously allowed a permissive fallback where an answer with missing `used_chunk_ids` could be associated with the first retrieved chunks.
- This was tightened so remote grounded generation must explicitly return valid `used_chunk_ids`.
- A regression test now verifies that missing `used_chunk_ids` remain empty, causing the service layer to fail closed instead of inventing citation linkage.

Local HTTP spot checks:

- Trademark registration question:
  - HTTP 200
  - `abstained=false`
  - citations present
  - sources include `IND-TM-ACT-1999` and `IND-TM-RULES-2017`
- Weather in Chennai:
  - HTTP 200
  - `abstained=false`
  - `confidence=0.35`
  - no citations
  - no sources
  - answer states the verified IP-SAKTI corpus was checked first
- Fake Trade Marks Act Section 9999:
  - HTTP 200
  - `abstained=true`
  - no citations
  - no sources

## Dataset Integrity

No canonical dataset or manifest files were modified by this repair.

## Known Limitations

- In local/dev mode, the general fallback is deterministic and deliberately conservative; it is not a full remote LLM answer unless `RAG_ENABLE_GENERAL_LLM=true` is configured.
- General fallback answers intentionally have no IP-SAKTI citations or sources.
- Existing RAG-grounded prose remains extractive in local mode.

## Final Status

RAG-FIRST RESPONSE POLICY: IMPLEMENTED.

The runtime now compares every question against the RAG system before final response selection and cleanly separates grounded corpus answers from non-corpus general fallback answers.

SELF-TEST REPAIR: COMPLETED.

The additional citation-linkage issue discovered during testing was fixed without weakening validation or modifying the dataset.
