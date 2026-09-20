# RAG Source Coverage (Phases 10-11) — explicit, no placeholders

## FSSAI 2022 regulation — NOT COVERED
- Required source: Food Safety and Standards (Ayurveda Aahara) Regulations, 2022 (official FSSAI).
- Current state: local bytes are HTML, not a PDF -> quarantined in V1 and excluded from V2 canonical.
- Only indexed substitute: `IND-FSS-AA-ORDER-2025` (2025 list order). It lists the 2022 regulation
  but cannot define it. Q08/Q09/Q23-class questions MUST abstain (evidence_status=PARTIAL).
- Action to resolve: acquire official FSSAI PDF, verify title/date/checksum, add to
  `RAG V2/dataset/`, re-run corpus validation, rebuild chunks, re-index, rerun retrieval tests
  + 25-question benchmark. Do NOT use unofficial translations.

## TKDL — NOT COVERED
- 0 chunks in V1 and V2 canonical. No authoritative bulk source currently ingested.
- Do not fabricate. If an official TKDL extract becomes legally ingestible, add provenance and re-index.

## IP India (publications / status data) — NOT COVERED
- 0 chunks in V2 canonical. No live registry scrape ingested.
- Do not fabricate. Mark NOT COVERED until an authoritative, licensable source is added.

## Verified coverage (V2 canonical, 1961 chunks / 22 docs)
- All 22 docs `source_status=VERIFIED`. Excluded legacy-unverified V1 docs:
  `IND-PAT-RULES-2003`, `IND-CR-RULES-2013` (raw missing) — correctly excluded, not a regression.
- Domains present: PATENT, TRADEMARK, COPYRIGHT, DESIGN, GI, PLANT_VARIETY, ABS, AYURVEDA, FOOD, INTERNATIONAL.
