# Phase 16 failure matrix (baseline, immutable)

| Q | Route | Retrieved | Context passed | LLM answer | Citation | Grounded | Root cause |
|---|---|---:|---:|---:|---:|---:|---|
| Q03 | RAG | yes (32 candidates; Patents Act dominated before hint fix) | yes | no usable supported answer | no | false | Missing Patents Rules document hint; provider output failed validation |
| Q04 | RAG | yes (32; Ayush annual report) | yes | empty/invalid provider payload | no | false | Provider returned no usable chunk IDs; no deterministic fallback |
| Q06 | RAG | yes (33; Biodiversity Act) | yes | yes | invalid in baseline | false in baseline | Provider cited retrieved chunks but introduced unsupported provisions; citation validator correctly rejected |
| Q07 | RAG | yes (41; GRATK top-ranked) | yes | insufficient-content answer | no | false | Indexed source is effectively a title/navigation chunk; corpus lacks treaty provision text |
| Q08 | RAG | quarantined path | no safe context | abstention | n/a | insufficient evidence | Authoritative 2022 Ayurveda Aahara PDF is quarantined (expected) |
| Q09 | RAG | quarantined path | no safe context | abstention | n/a | insufficient evidence | Same quarantined-source guard (expected) |
| Q13 | RAG | yes (33; GI Act) | yes | empty/irrelevant provider output | no | false | Provider failure plus weak extractive sentence selection |
| Q18 | RAG | yes (35; Biodiversity Act) | yes | empty/irrelevant provider output | no | false | Amendment source hint was not recognized; cross-source synthesis was not selected |
| Q22 | RAG | yes (48; Biodiversity Rules) | yes | not generated | no | false | Intent guard rejected strong Rules evidence because OCR text lacked registration terms |
| Q23 | RAG | quarantined path | no safe context | abstention | n/a | insufficient evidence | Same quarantined-source guard (expected) |

The baseline response serializer also reported `evidence_count=0` for abstentions even when selected evidence existed. That was corrected to preserve evidence counts for diagnostics.
