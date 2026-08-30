# Dataset source verification report

Date: 2026-08-29

## Outcome

The source-verification pass repaired 21 of the 23 `LEGACY_UNVERIFIED_RAW_MISSING` warnings. The rebuilt canonical dataset now has 25 registered documents, 24 retrievable documents, 6,514 chunks, 0 validation errors, and 2 validation warnings.

The remaining warnings are intentional, not accidental: `IND-PAT-RULES-2003` and `IND-CR-RULES-2013` were not force-verified because the available official sources were unreachable, incomplete, or incompatible with the current single-artifact ingestion path.

## Verification method

For each candidate source, the pass checked authority, stable source URL, local raw path, SHA-256 checksum, file size, readability, page count where applicable, and identity markers in extracted text. Wrong or incomplete candidate files were removed instead of retained as “close enough” evidence.

## Final status of the 23 warnings

| Source ID | Final status | Local raw source | Notes |
|---|---|---|---|
| `IND-PAT-ACT-1970` | VERIFIED | `dataset/raw/india_code/patents/patents_act_1970.pdf` | Official IP India PDF; 69 pages. |
| `IND-PAT-RULES-2003` | REQUIRES_MANUAL_DOWNLOAD | legacy fallback only | India Code upload endpoint was unavailable; wrong/incomplete IP India candidates rejected. |
| `IND-TM-ACT-1999` | VERIFIED | `dataset/raw/india_code/trademarks/trade_marks_act_1999.pdf` | Official IP India PDF; 51 pages. |
| `IND-TM-RULES-2017` | VERIFIED | `dataset/raw/india_code/trademarks/trade_marks_rules_2017.pdf` | Official IP India PDF; 97 pages. |
| `IND-GI-ACT-1999` | VERIFIED | `dataset/raw/india_code/gi/gi_act_1999.html` | Official IP India HTML text. |
| `IND-GI-RULES-2002` | VERIFIED | `dataset/raw/india_code/gi/gi_rules_2002.pdf` | Official IP India PDF; 71 pages. |
| `IND-CR-ACT-1957` | VERIFIED | `dataset/raw/india_code/copyright/copyright_act_1957.pdf` | Official IP India PDF; 57 pages. |
| `IND-CR-RULES-2013` | REQUIRES_INGESTION_REPAIR | legacy fallback only | Official Copyright Office source is chapterized HTML; no single verified raw artifact available. |
| `IND-DES-ACT-2000` | VERIFIED | `dataset/raw/india_code/designs/designs_act_2000.html` | Official IP India HTML text. |
| `IND-DES-RULES-2001` | VERIFIED | `dataset/raw/india_code/designs/designs_rules_2001.html` | Official IP India HTML text. |
| `IND-PPV-ACT-2001` | VERIFIED | `dataset/raw/india_code/ppvfr/ppvfr_act_2001.pdf` | Official PPVFR Authority PDF; 50 pages. |
| `IND-PPV-RULES-2003` | VERIFIED | `dataset/raw/india_code/ppvfr/ppvfr_rules_2003.pdf` | Official PPVFR Authority PDF; 56 pages. |
| `IND-BD-ACT-2002` | VERIFIED_WITH_CAVEAT | `dataset/raw/india_code/biodiversity/biological_diversity_act_2002.html` | Full WIPO Lex original Act text; not a consolidated 2023-amended India Code file. |
| `IND-BD-AMEND-2023` | VERIFIED | `dataset/raw/nba/biological_diversity_amendment_act_2023.pdf` | Official MoEFCC PDF; 15 pages. |
| `IND-BD-RULES-2024` | VERIFIED | `dataset/raw/nba/biological_diversity_rules_2024.pdf` | Official India Code source PDF; 86 pages. |
| `IND-AYUSH-2024` | VERIFIED | `dataset/raw/ayush/ayush_in_india_2024.pdf` | Official Ministry of Ayush PDF; 326 pages. |
| `IND-AYUSH-AR-2024-25` | VERIFIED | `dataset/raw/ayush/annual_report_2024_25.pdf` | Official Ministry of Ayush PDF; 215 pages. |
| `INT-TRIPS-1994` | VERIFIED_WITH_CAVEAT | `dataset/raw/wipo/trips_agreement.html` | Full WIPO Lex treaty text; WTO source page carries a reproduced-text legal-standing caveat. |
| `INT-WIPO-PARIS` | VERIFIED | `dataset/raw/wipo/paris_convention.pdf` | Official WIPO Lex PDF; 20 pages. |
| `INT-WIPO-PCT` | VERIFIED | `dataset/raw/wipo/pct.pdf` | Official WIPO Lex PDF; 52 pages. |
| `INT-WIPO-MADRID` | VERIFIED | `dataset/raw/wipo/madrid_protocol.html` | Official WIPO Lex HTML text. |
| `INT-WIPO-BUDAPEST` | VERIFIED | `dataset/raw/wipo/budapest_treaty.html` | Official WIPO Lex HTML text. |
| `INT-WIPO-GRATK-2024` | VERIFIED | `dataset/raw/wipo/gratk_treaty.html` | Official WIPO Lex HTML text; treaty adoption must not be described as entry into force. |

## Rebuild and validation evidence

- `scripts/build_dataset.py`: 25 documents, 24 retrievable, 6,514 chunks, 0 errors, 2 warnings.
- `scripts/validate_dataset.py`: passed with warnings only for `IND-PAT-RULES-2003` and `IND-CR-RULES-2013`.
- `python -m pytest`: 25 passed.
- `scripts/evaluate_rag.py`: release gate remains false because production backend verification and complete authoritative raw corpus are still pending.

## Next source work

1. Obtain a verified full official `IND-PAT-RULES-2003` artifact. The previous IP India candidate was rejected because it was a 2024 gazette/amendment, not the full rules.
2. Implement a documented chapter-consolidation ingestion path for `IND-CR-RULES-2013`, or obtain a single official full PDF/HTML source.
3. Rebuild after those two are resolved; only then should the authoritative raw corpus gate be reconsidered.
