# RAG quality repair plan

Date: 2026-08-30

This plan is based on the real runtime evaluation in `dataset/evaluation/results/runtime_question_test.json` and `docs/RAG_RUNTIME_QUESTION_TEST_REPORT.md`. Dataset state is locked: 25 documents, 24 retrievable documents, 6,514 chunks, 2 source warnings, and 1 quarantined invalid source.

No dataset rebuild or document download is part of this repair.

## Failed-question repair map

| Question | Failure category | Root cause | Affected component | Proposed fix | Expected behavior |
|---|---|---|---|---|---|
| Q15: What is a design under Indian law? | Over-abstention | Domain detector only recognized narrow phrases such as `industrial design`; generic `design` was treated as ambiguous. | Query processing | Add design-intent phrases and safe ambiguity handling. | Retrieve `IND-DES-ACT-2000` and answer with definition evidence. |
| Q16: What are the requirements for registering a design? | Over-abstention | Same design-domain miss; registration expansion did not fire. | Query processing / expansion | Detect `design` + registration intent and expand toward design application/registration terms. | Retrieve `IND-DES-ACT-2000` / `IND-DES-RULES-2001`, answer or abstain if evidence is weak. |
| Q17: How long is design protection valid? | Over-abstention | Generic design + duration wording missed. | Query processing / expansion | Add term/duration expansion for design. | Retrieve design term provisions and answer with citations. |
| Q20: What protection does a registered GI provide? | Wrong source priority / noisy generation | Reranker favored rules/fee fragments over Act protection/infringement provisions. | Reranking / generation | Add intent-aware boosts for protection/rights questions and down-rank fee/form-only fragments. | Prefer `IND-GI-ACT-1999` evidence; answer directly about protection/rights or abstain. |
| Q23: What rights are provided to registered plant varieties? | Over-abstention | `plant varieties` plural was not recognized; rights intent was not expanded. | Query processing / expansion | Add plural/crop-variety terms and rights expansion. | Retrieve `IND-PPV-ACT-2001` and answer with rights evidence. |
| Q24: What is the purpose of the Biological Diversity Act? | Wrong source priority | Rules/amendment snippets outranked Act purpose/preamble evidence. | Reranking / expansion | Add purpose/objective intent terms and Act-preference boosts for Act-purpose questions. | Prefer `IND-BD-ACT-2002` evidence. |
| Q36: What is the WIPO GRATK Treaty? | Unexpected abstention | Generated answer likely mentioned unsupported article/provision text, triggering citation validation. | Grounded generation / citation safety | Make deterministic generator produce concise document-summary answers and avoid unsupported provision mentions unless present in selected citation metadata or text. | Grounded answer from `INT-WIPO-GRATK-2024` without citation-validation rejection. |
| Q41: I invented something new. How can I protect it in India? | Over-abstention | Natural-language patent intent did not map to PATENT. | Query processing | Add invention/new-machine/protect-it patent-intent vocabulary. | Retrieve patent evidence and answer cautiously. |
| Q42: I created a logo for my company. What IP protection should I consider? | Over-abstention | Logo/company-brand intent was not mapped to trademark/design. | Query processing | Detect logo/brand/company mark as trademark, with design as secondary if shape/look terms appear. | Retrieve trademark evidence, optionally design evidence if supported. |
| Q43: I have a traditional product from my region. Can I protect its geographical identity? | Over-abstention | Regional-product/geographical-identity intent was not mapped to GI. | Query processing | Add regional/traditional product/place-origin terms. | Retrieve GI evidence and answer cautiously. |
| Q44: I created an original song. What kind of IP protection applies? | Over-abstention | Song/music/creative-work intent was not mapped to copyright. | Query processing | Add song/music/creative/original-work terms. | Retrieve copyright evidence and answer. |
| Q49: Tell me the exact patent law for teleportation in India. | Under-abstention / false premise | Domain terms caused generic patent retrieval; sufficiency did not require evidence alignment to the speculative subject. | Evidence sufficiency / false-premise protection / confidence | Add speculative/impossible-subject guard and query/evidence subject alignment check. | Abstain because no authoritative evidence specifically supports teleportation law. |

## Cross-cutting fixes

1. Improve query intent detection for `PATENT`, `TRADEMARK`, `COPYRIGHT`, `DESIGN`, `GI`, `PLANT_VARIETY`, `ABS`, `AYURVEDA`, `INTERNATIONAL`, `GENERAL_IP`, and `OUT_OF_SCOPE`.
2. Preserve the original question while adding retrieval-only expansion terms for common intents: definition, registration/application, rights/protection, duration/term, opposition/infringement, purpose/objectives.
3. Strengthen legal reranking with intent relevance, document-type relevance, expected domain/jurisdiction match, and penalties for fee/form-only chunks when the question asks for rights, purpose, definitions, or duration.
4. Improve evidence sufficiency with source validity, domain coverage, answerability terms, exact identifier support, and speculative-premise checks.
5. Improve deterministic extractive generation so answers are direct summaries with legal-basis sentences rather than raw OCR/PDF fragments.
6. Preserve citation validation and quarantined-source exclusion.
7. Calibrate confidence downward for weak answerability, legacy-only evidence, low citation coverage, cross-domain under-coverage, and fragment-heavy evidence.

## Acceptance criteria

- Existing tests continue passing.
- New targeted tests cover natural-language routing, false-premise abstention, confidence calibration, citation safety, and quarantined-source behavior.
- The same 55-question public-API runtime evaluation is rerun.
- API success, response schema validity, citation integrity, and security/source integrity must not regress from 100%.
- Dataset canonical/manifest/source-registry hashes remain unchanged.
