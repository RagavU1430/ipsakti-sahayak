# Final Pre-Agentic RAG Release Gate

## 1. Executive Summary

This gate was run to decide whether the current normal RAG + backend routing system is reliable enough to freeze before Agentic RAG development.

Result: **NOT READY FOR AGENTIC RAG**.

The frozen dataset is intact, TK/TKDL evidence exists, and the code-level RAG defect was repaired with narrow retrieval/reranking/generation/context changes. The fixed RAG process verified successfully on an isolated fresh runtime at `127.0.0.1:8001`.

However, the official live backend path is **not clean**:

- Backend `8080` still calls stale RAG `8000`.
- Stale RAG `8000` still returns incorrect TK/TKDL abstentions.
- Windows reports `0.0.0.0:8000` owned by PID `26700`, but both `Stop-Process` and `taskkill` report that PID does not exist.
- Browser UI therefore still fails TK and TKDL because it uses backend `8080`.
- Conversation follow-up context fails: after "What is a patent?", "How long does it last?" remains ambiguous and abstains.

Backend unit/integration tests, RAG tests, frontend tests, lint/typecheck, and frontend production build all pass.

## 2. Current System State

Observed listeners:

| Component | Intended port | Observed |
|---|---:|---|
| Spring Boot backend | 8080 | Listening PID `5004` |
| Python RAG | 8000 | Listening PID `26700`, but PID cannot be resolved/killed |
| Duplicate stale RAG | 8765 | Not observed |
| Vite dev frontend | 5173 | Listening on `::1` |
| Isolated fixed RAG verification | 8001 | Listening PID `4360` |

Health checks:

- `http://127.0.0.1:8000/health` returned `{"status":"ok"}`.
- `http://127.0.0.1:8080/health/ready` returned backend/db/rag ready.
- `http://127.0.0.1:8001/api/v1/ask` verified the fixed RAG code path.

Active configuration was inspected without printing secrets. Relevant non-secret settings included:

- `SERVER_PORT=8080`
- `RAG_BASE_URL=http://localhost:8000`
- `RAG_STORAGE_BACKEND=local`
- `RAG_TOP_K=8`
- `RAG_CANDIDATE_K=24`
- `RAG_MIN_SCORE=0.10`
- `RAG_ABSTENTION_THRESHOLD=0.12`
- `RAG_SIMILARITY_THRESHOLD=0.10`
- `RAG_ENABLE_LLM=true`
- `RAG_ENABLE_GENERAL_LLM=true`

## 3. Runtime Audit

The backend and RAG health endpoints are up. No port `8765` duplicate was observed.

The critical runtime issue is that port `8000` is stale and cannot be restarted normally:

- `Stop-Process -Id 26700 -Force` failed: process not found.
- `taskkill /PID 26700 /F` failed: process not found.
- `netstat -ano` continued to report `0.0.0.0:8000 LISTENING 26700`.
- Live `8000` still served pre-fix TKDL behavior.

Because backend `8080` uses `RAG_BASE_URL=http://localhost:8000`, the official live backend/browser path still sees stale RAG behavior.

## 4. Dataset Integrity

Required frozen hash:

`827f8a209ff7cbc86c00931fbb97d6eeaa861cdf360fa8770dafcf0fab05700d`

Initial observed hash:

`827F8A209FF7CBC86C00931FBB97D6EEAA861CDF360FA8770DAFCF0FAB05700D`

Final observed hash:

`827F8A209FF7CBC86C00931FBB97D6EEAA861CDF360FA8770DAFCF0FAB05700D`

Dataset integrity: **PASS**.

No canonical dataset file was regenerated, rewritten, normalized, re-chunked, re-embedded, deleted, or reverted.

## 5. TK Evidence Audit

`TK_EVIDENCE_EXISTS = TRUE`

Dataset search found 49 chunks containing traditional-knowledge evidence.

Strongest authoritative chunks:

| Document ID | Domain | Chunk ID | Provision/context | Evidence |
|---|---|---|---|---|
| `IND-PAT-ACT-1970` | PATENT | `IND-PAT-ACT-1970-0193-8b91e02346c5` | Section 3(p) | Traditional knowledge / aggregation or duplication of known properties of traditionally known components is excluded from inventions. |
| `IND-BD-AMEND-2023` | ABS | `IND-BD-AMEND-2023-0009-57ce5ac22436` | Section 3, clause aa | Benefit claimers include creators or holders of traditional knowledge associated with biological resources. |
| `IND-BD-AMEND-2023` | ABS | `IND-BD-AMEND-2023-0008-8b9ee3406f91` | Section 3, clause i | Access includes biological resources from India or traditional knowledge associated thereto. |
| `IND-BD-AMEND-2023` | ABS | `IND-BD-AMEND-2023-0012-11eeed8ee3ae` | Section 3, clause iv | Codified traditional knowledge is defined by reference to authoritative books specified in the First Schedule to the Drugs and Cosmetics Act, 1940. |
| `IND-BD-RULES-2024` | ABS | multiple | forms/rules | Multiple forms record access to traditional knowledge associated with biological resources. |

Conclusion: TK evidence is present. The failure was not due to missing corpus evidence.

## 6. TKDL Evidence Audit

`TKDL_EVIDENCE_EXISTS = TRUE`

Dataset search found 2 literal TKDL chunks.

Strongest available chunks:

| Document ID | Domain | Chunk ID | Source | Evidence |
|---|---|---|---|---|
| `IND-AYUSH-2024` | AYURVEDA | `IND-AYUSH-2024-0196-c14137216e56` | Ayush in India 2024 | Lists "The Traditional Knowledge Digital Library (TKDL)" under research database/library initiatives. |
| `IND-AYUSH-AR-2024-25` | AYURVEDA | `IND-AYUSH-AR-2024-25-0002-5ead864d7b45` | Ministry of Ayush Annual Report 2024-25 | Abbreviation list: TKDL = Traditional Knowledge Digital Library. |

Important limitation: the frozen corpus contains TKDL abbreviation/listing evidence, but not a dedicated TKDL legal procedure or prior-art-search source. Therefore, a narrow answer explaining the acronym/database-list context is supported; broader claims about TKDL operation or prior-art legal effect should remain abstained or caveated unless future corpus evidence is added.

## 7. Direct RAG Trace

### Official live RAG on port 8000

`POST http://127.0.0.1:8000/api/v1/ask`

| Query | Result |
|---|---|
| What is TKDL? | Incorrect abstention: "The retrieved evidence was insufficient..." |

This proves the official `8000` process is stale relative to the repaired code.

### Fresh fixed RAG on port 8001

`POST http://127.0.0.1:8001/api/v1/ask`

| Query | Abstained | Confidence | Citations | Sources | Result |
|---|---:|---:|---:|---|---|
| What is a patent? | false | 0.9969 | 3 | `IND-PAT-ACT-1970` | PASS |
| What is a GI? | false | 0.7789 | 1 | `IND-GI-ACT-1999`, `IND-GI-RULES-2002` | PASS |
| What is a trademark? | false | 0.8203 | 1 | `IND-TM-ACT-1999`, `IND-TM-RULES-2017` | PASS |
| What is traditional knowledge? | false | 0.9533 | 8 | `IND-PAT-ACT-1970`, `IND-BD-AMEND-2023`, `IND-BD-RULES-2024` | PASS |
| What is TKDL? | false | 0.8467 | 2 | `IND-AYUSH-2024`, `IND-AYUSH-AR-2024-25` | PASS |
| What is ABS? | false | 0.9042 | 4 | `IND-BD-AMEND-2023`, `IND-BD-RULES-2024` | PASS |
| What are IP rules in India? | false | 0.8829 | 6 | `IND-BD-ACT-2002`, `IND-GI-RULES-2002`, `IND-PPV-RULES-2003` | PASS WITH WARNING |
| Tell me about intellectual property law in India. | true | 0.18 | 0 | none | FAIL/WARNING: broad IP answer rejected by citation validation |
| What is Section 3(p)? | false | 0.6864 | 1 | `IND-PAT-ACT-1970` | PASS |
| What is Section 377? | true | 0.18 | 0 | none | PASS safe abstention |
| Hi | true | 0.18 | 0 | none | RAG-only safe abstention; backend handles general chat |
| What can you do? | true | 0.18 | 0 | none | RAG-only safe abstention; backend handles general chat |
| What is Python? | true | 0.18 | 0 | none | RAG-only safe abstention; backend handles general chat |
| What is 2 + 2? | true | 0.18 | 0 | none | RAG-only safe abstention; backend handles general chat |
| What is a patent? | false | 0.9969 | 4 | `IND-PAT-ACT-1970` | PASS |
| How long does it last? | true | 0.18 | 0 | none | RAG-only safe abstention; backend follow-up context required |

## 8. Root Cause

TK root cause:

- Evidence exists in Patent and ABS corpus chunks.
- Backend routes `TRADITIONAL_KNOWLEDGE` to RAG domain `PATENT`.
- Pre-fix RAG domain handling over-constrained TK questions and did not preserve cross-domain ABS evidence.
- Reranking also allowed adjacent/generic legal provisions to outrank exact TK evidence.
- For patentability wording, query analysis did not infer `Section 3(p)`.

TKDL root cause:

- Evidence exists only in AYUSH-domain corpus chunks.
- Backend maps `TRADITIONAL_KNOWLEDGE`/TKDL-style routing to `PATENT`.
- Pre-fix RAG domain filtering excluded AYUSH evidence, so TKDL could incorrectly abstain.
- Once retrieved, deterministic extractive generation ignored short exact TKDL list items inside long chunks and could summarize adjacent AYUSH portal text instead.

Live/backend root cause:

- The code-level fix is not reflected on the official port `8000` process.
- Windows reports a listener owned by non-resolvable PID `26700`, preventing normal restart.
- Backend `8080` uses `RAG_BASE_URL=http://localhost:8000`, so backend/browser still call stale RAG.

Follow-up root cause:

- Conversation routing records previous route/domain, but does not rewrite vague follow-up text into a standalone canonical RAG query.
- Therefore, "How long does it last?" is routed to Patent/RAG by context but sent to RAG as an ambiguous pronoun query, producing a safe abstention.

## 9. Fix Applied

Minimal RAG-only fixes were applied. No dataset files were modified.

Files modified:

- `ip-sakti-rag/app/retrieval/query_analysis.py`
- `ip-sakti-rag/app/retrieval/reranker.py`
- `ip-sakti-rag/app/generation/grounded.py`
- `ip-sakti-rag/app/service.py`
- `ip-sakti-rag/app/legal_aliases.py`
- `ip-sakti-rag/tests/test_retrieval.py`

Fix details:

- TK/TKDL query detection now preserves cross-domain evidence instead of forcing Patent-only retrieval.
- TKDL query expansion includes AYUSH research database/library evidence.
- Traditional knowledge + patentability now infers `Section 3(p)`.
- Reranker adds targeted TK/TKDL topical relevance.
- Reranker prioritizes exact TKDL chunks for TKDL queries and exact Section 3(p) evidence for TK patentability queries.
- Extractive generator now selects a short evidence window around exact TK/TKDL terms inside long chunks.
- Exact legal identifier context selection now prefers exact provision matches before same-document fallback.
- Tests were added for TK/TKDL cross-domain retrieval preservation.

Not changed:

- Dataset
- Embeddings
- Chunking
- Citation validation
- Abstention thresholds
- Voice V2
- Providers
- Frontend behavior for this gate, except generated static bundle from the production build

## 10. Live Regression Matrix

### Fixed RAG process on 8001

Core RAG behavior: **PASS WITH WARNING**.

Warnings:

- Broad "Tell me about intellectual property law in India" abstained after citation validation. This is conservative, but not ideal for a broad IP overview query.
- Pure general chat correctly abstains at RAG-only layer; backend handles general chat.

### Official backend process on 8080

`POST http://127.0.0.1:8080/api/v1/questions`

| Query | Result |
|---|---|
| What is a patent? | PASS |
| What is a GI? | PASS |
| What are the requirements for registering a trademark in India? | FAIL: citation validation rejection via stale RAG |
| What is traditional knowledge? | FAIL: incorrect abstention via stale RAG |
| What is TKDL? | FAIL: incorrect abstention via stale RAG |
| What is Section 3(p)? | PASS |
| How does traditional knowledge relate to patentability? | FAIL: incorrect abstention via stale RAG |
| What is ABS? | FAIL in one backend run via stale RAG; browser later showed ABS answer |
| What is Section 377? | PASS safe abstention |
| Hi | PASS general fallback |
| What can you do? | PASS general fallback |
| What is Python? | PASS general fallback |
| What is 2 + 2? | PASS general fallback |

Backend live parity: **FAIL** until backend points to a fresh fixed RAG runtime.

## 11. Browser Verification

Browser verification used the actual running web UI at:

`http://localhost:8080/ask`

The browser was connected through the external-browser extension path. Brave-specific identity could not be proven from the available browser API, so this is recorded as external-browser UI verification, not confirmed Brave-only verification.

Visible UI checks:

| Query | Visible UI result |
|---|---|
| What is a patent? | PASS: evidence-backed answer, percentage confidence, citation/source displayed |
| What is a GI? | PASS: evidence-backed answer, percentage confidence, citation/source displayed |
| What is a trademark? | PASS: evidence-backed answer, percentage confidence, citation/source displayed |
| What is traditional knowledge? | FAIL: stale incorrect abstention displayed |
| What is TKDL? | FAIL: stale incorrect abstention displayed |
| What is ABS? | PASS: evidence-backed answer displayed |
| What are IP rules in India? | PASS: evidence-backed answer displayed |
| What is Section 3(p)? | PASS: evidence-backed answer displayed |
| Hi | PASS: general chat response displayed |
| What is Python? | PASS: general response displayed |

No internal prompts, credentials, embeddings, or stack traces were visible in the UI. Voice/audio UI was not rebuilt or modified.

Browser verdict: **FAIL** because the official UI path still uses stale RAG `8000` for TK/TKDL.

## 12. Backend Regression

Initial backend rerun failed because Maven could not resolve a missing plugin dependency from Maven Central under restricted network:

- Missing dependency chain included `org.apache.maven.shared:maven-filtering:jar:3.5.0`.

After allowing Maven network access:

- Command: `cmd.exe /c "mvn -Dmaven.repo.local=.mvnrepo test"`
- Result: **BUILD SUCCESS**
- Tests: `169`
- Failures: `0`
- Errors: `0`
- Skipped: `0`
- Total time: `01:01 min`

Diagnosis of previous 20-minute timeout:

- The issue was environmental/dependency resolution, not a specific hanging test.
- Maven needed additional plugin dependencies in the local `.mvnrepo`.

Note: this created/updated `ip-sakti-backend/.mvnrepo/` as an untracked local Maven repository.

## 13. RAG Regression

Command:

`python -m pytest -q`

Environment:

- `RAG_ENABLE_LLM=false`
- `RAG_STORAGE_BACKEND=local`

Result:

- `77 passed`
- `30 skipped`
- `6 warnings`
- Duration: `36.24s`

Focused RAG tests:

- `tests/test_retrieval.py`
- `tests/test_grounding.py`
- `tests/test_api.py`

Result:

- `33 passed`
- `1 warning`

## 14. Frontend Regression

Commands:

- `npm.cmd test`
- `npm.cmd run lint`
- `npm.cmd run build`

Results:

- Vitest: `9` test files passed, `27` tests passed.
- Typecheck/lint: passed via `tsc --noEmit`.
- Production build: passed via `tsc -b && vite build`.

Build output refreshed static artifacts in:

- `ip-sakti-backend/src/main/resources/static/`

## 15. Security/Secret Check

Secret-oriented scan was run against the modified RAG files and docs without printing `.env` secrets.

Findings:

- No new hardcoded API keys, service-role keys, passwords, or tokens were introduced by this gate.
- Existing docs include an older warning that `.env` previously contained secrets; values were not printed in this report.
- `ip-sakti-rag/app/service.py` contains configuration field references such as `openrouter_api_key`; this is expected code, not a secret value.

## 16. Remaining Warnings

1. Official RAG port `8000` is stale and cannot be killed through normal PID-based commands.
2. Backend `8080` points to stale `8000`.
3. Browser UI therefore fails TK/TKDL despite fixed code passing on fresh RAG `8001`.
4. Conversation follow-up context fails for pronoun-only follow-up questions.
5. Broad IP-law overview query can be conservatively rejected by RAG citation validation.
6. Maven created/updated `.mvnrepo/` during backend test dependency resolution.
7. Frontend production build changed generated static assets.
8. Existing worktree contains many pre-existing unrelated modifications from prior phases; none were reverted.

## 17. Agentic RAG Readiness

Subsystem classifications:

| Subsystem | Status |
|---|---|
| Dataset integrity | PASS |
| Fixed RAG code | PASS |
| Direct fixed RAG runtime | PASS WITH WARNING |
| Official RAG runtime on 8000 | FAIL |
| Backend automated tests | PASS |
| Backend live integration | FAIL |
| Frontend tests/lint/build | PASS |
| Browser UI against official backend | FAIL |
| General chat via backend | PASS |
| Follow-up context | FAIL |
| Security/secret check for touched files | PASS |

Agentic RAG should not begin until the official backend/browser path is using the fixed RAG runtime and follow-up context is repaired or explicitly deferred outside the release criteria.

## 18. Final Verdict

**NOT READY FOR AGENTIC RAG**

Reason:

This is closest to Case C/E from the release rules:

- TK/TKDL evidence exists.
- Minimal RAG fix resolves it in fresh fixed runtime.
- But official live backend/browser integration still fails because backend uses stale RAG `8000`.
- Follow-up context also fails.

Recommended next actions:

1. Clear the stale `8000` listener at OS/session level, or reboot the dev machine if normal PID tools cannot resolve it.
2. Start exactly one fixed RAG process on `8000`.
3. Restart backend `8080`.
4. Re-run backend live matrix and browser matrix.
5. Repair conversation follow-up canonicalization so "How long does it last?" becomes a standalone patent-duration query when prior assistant context is Patent/RAG.
6. Re-run this gate before starting Agentic RAG.
