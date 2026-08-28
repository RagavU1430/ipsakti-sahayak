# Dataset acquisition report

Generated after the initial bounded acquisition run on 2026-08-28. Counts below are refreshed after final validation in this repository state.

## Acquisition summary

- Sources registered: 25
- Files downloaded: 2
- Files validated: 2
- Files processed: 2
- Failed acquisitions: 2
- Discovery-pending records: 20
- Scope: official India Code, NBA, FSSAI, Ministry of Ayush, WTO/WIPO, and TKDL records
- Restricted: 1 — TKDL (metadata only; no download attempted)
- Manual-download status: 0 (failed endpoints remain failed rather than being silently relabelled)
- Raw-file policy: local/reproducible; PDFs excluded from Git
- Hashing: SHA-256 for every acquired file
- Embeddings: not generated

## Processed corpus

- Documents: 2
- Extracted pages: 541
- Legal-aware chunks: 223
- OCR-required files: 0
- Total acquired bytes: 23,094,234
- Domains represented in processed content: AYURVEDA (2)

Registered records by domain: ABS 3; AYURVEDA 2; COPYRIGHT 1; DESIGN 2; FOOD 1; GI 2; INTERNATIONAL 7; PATENT 2; PLANT_VARIETY 2; TKDL 1; TRADEMARK 2.

## Accessibility and integrity notes

- India Code Patents Rules endpoint timed out during the bounded retry window. No substitute was used.
- The supplied FSSAI Ayurveda Aahara URL returned HTML rather than a PDF to the automated client. It was rejected as an invalid PDF response.
- Ministry of Ayush publications downloaded from official `ayush.gov.in` URLs and are validated/extracted subject to the machine-generated manifests.
- Most Act/Rule and WIPO records remain discovery-pending until a stable official direct download is verified.
- TKDL is restricted; content was not requested or scraped.
- No OCR was run automatically. OCR-required identifiers, if any, are recorded in `metadata.json`.

## Licensing/access

The corpus records public access, not a blanket redistribution licence. Official legal text/Gazette disclaimers and authority-specific terms continue to apply. Raw files remain outside Git to avoid redistribution and repository bloat.

## Next steps

Resolve official India Code file links through ordinary public page mechanisms; manually inspect the FSSAI response and current amendment trail; obtain public PCIM&H pharmacopoeial material only where redistribution is permitted; verify current consolidations; add document-specific table/form extraction; and perform human citation QA before embeddings.
