# RAG V2 Knowledge Gap Register

Generated: 2026-09-14
Framework: Master Prompt Phase 16 rules (RULE 5: Do not expand dataset yet)
Basis: Phase 16 frozen benchmark (`dataset/evaluation/phase16_rag_questions.json`) + corpus audit

---

## Gap G001 — IND-PAT-RULES-2003 (Patents Rules 2003)

**Gap ID:** G001
**Domain:** PATENT
**Question/use case:** Q03 — "What procedure do the Patents Rules prescribe for requesting examination of a patent application?"
**Existing evidence:** V2 has `IND-PAT-ACT-1970` (Patents Act 1970) but no Patents Rules 2003 document.
**Why insufficient:** The Patents Rules 2003 prescribe the examination request procedure (Form 2, Section 11). The Act alone does not contain the detailed procedural rules for filing examination requests, fees, timelines, or controller powers under the Rules.
**Required source:** Official Patents Rules 2003 document (India Code / IP India).
**Authoritative source:** `https://www.indiacode.nic.in/handle/123456789/1392` (India Code official upload).
**Decision:** NOT ADDED — per RULE 5, do not expand dataset yet. The V1 `source_registry.csv` already records this as `REQUIRES_MANUAL_DOWNLOAD` with legacy chunks retained.
**Added / Not Added:** Not Added
**Reason:** V1 has legacy chunks for this document but the raw source is unavailable. The V2 raw `dataset/` does not contain it. Re-adding would be a dataset expansion, which requires a benchmark failure to justify.

---

## Gap G002 — IND-FSS-AA-2022 (Ayurveda Aahara Regulations 2022)

**Gap ID:** G002
**Domain:** FSS (Food Safety and Standards Authority)
**Question/use case:** Q08/Q09/Q23 — "What labelling requirements apply to Ayurveda Aahara products?" / "How do the Ayurveda Aahara Regulations define Ayurveda Aahara?"
**Existing evidence:** V2 has `ayurveda_aahara_regulations_2022.pdf.md` but it has `conversion_status: "extraction_failed"`. The PDF stream is truncated (`PdfStreamError: Stream has ended unexpectedly`). No extractable text exists.
**Why insufficient:** Without extractable text, the document is effectively absent from the corpus. Q08, Q09, and Q23 all expect `IND-FSS-AA-2022` as the source. The current V1 benchmark aborts these questions with a quarantine message.
**Required source:** Valid copy of the FSSAI Ayurveda Aahara Regulations 2022 PDF.
**Authoritative source:** FSSAI official website (`https://www.fssai.gov.in/`).
**Decision:** NOT ADDED — but RECOMMENDED for re-extraction once a valid PDF is obtained. This is a corpus-quality fix, not an expansion.
**Added / Not Added:** Not Added (but re-extraction is a priority)
**Reason:** The existing PDF file is corrupt/truncated. Replacing it with a valid copy and re-extracting does not expand the dataset — it fixes a broken document.

---

## Gap G003 — IND-BD-RULES-2024 (Biological Diversity Rules 2024)

**Gap ID:** G003
**Domain:** ABS (Access and Benefit Sharing)
**Question/use case:** Q22 — "What application procedures and forms are prescribed by the Biological Diversity Rules, 2024?"
**Existing evidence:** V2 has `nba/biological_diversity_rules_2024.pdf` in the raw dataset. However, the Phase 16 audit shows Q22 retrieves `IND-BD-RULES-2024` with only 48 candidates and the retrieval confidence is 0.71 (low). The document exists but may have extraction or chunking issues.
**Why insufficient:** The Biological Diversity Rules 2024 prescribe application procedures (Form A, Form B, etc.) for access to biological resources and traditional knowledge. If the chunking or extraction quality is poor, retrieval may miss the relevant procedural sections.
**Required source:** `RAG V2/dataset/nba/biological_diversity_rules_2024.pdf` (already present).
**Authoritative source:** National Biodiversity Authority (`https://www.nbaindian.org/`).
**Decision:** EXISTS BUT QUALITY UNCERTAIN — verify extraction and chunking quality.
**Added / Not Added:** Not Added (document exists, needs verification)
**Reason:** The raw PDF exists. The gap is likely in extraction/chunking quality, not document absence.

---

## Gap G004 — IND-AYUSH-2024 (Ayush 2024 Document)

**Gap ID:** G004
**Domain:** AYUSH
**Question/use case:** Q04 — "What does the Ministry of Ayush report about official pharmacopoeial standard-setting for Ayurveda?"
**Existing evidence:** V2 has `ayush/ayush_in_india_2024.pdf` and `ayush/annual_report_2024_25.pdf`. The Phase 16 benchmark shows Q04 retrieves from `IND-AYUSH-AR-2024-25` (annual report). The question references `IND-AYUSH-2024` (a separate document).
**Why insufficient:** `IND-AYUSH-2024` may refer to a separate publication from the annual report. If the annual report covers pharmacopoeial standard-setting, the question is answerable. If not, the specific document is missing.
**Required source:** `IND-AYUSH-2024` (unclear if this is the annual report or a separate document).
**Authoritative source:** Ministry of AYUSH (`https://www.ayush.gov.in/`).
**Decision:** NOT ADDED — verify whether the annual report (already present) answers Q04.
**Added / Not Added:** Not Added
**Reason:** The `ayush/ayush_in_india_2024.pdf` and `annual_report_2024_25.pdf` are present. Q04 may be answerable from these. Need benchmark verification.

---

## Gap G005 — TKDL Documents (Traditional Knowledge Digital Library)

**Gap ID:** G005
**Domain:** TKDL
**Question/use case:** Q19 (farmer rights under PPVFR) may benefit from TKDL context on prior art and traditional knowledge.
**Existing evidence:** `RAG V2/dataset/tkdl/` is EMPTY. Only `.gitkeep` placeholder exists.
**Why insufficient:** TKDL contains traditional knowledge documentation relevant to patent examination and prior art. Questions about farmer rights, traditional knowledge, and biological diversity may benefit from TKDL context.
**Required source:** TKDL corpus documents (large collection of traditional knowledge records).
**Authoritative source:** TKDL official database (`https://tkdl.gov.in/`).
**Decision:** NOT ADDED — per RULE 5. TKDL is a large specialized corpus; adding it would be a dataset expansion.
**Added / Not Added:** Not Added
**Reason:** No benchmark question currently requires TKDL-specific evidence. Q19 (farmer rights) is answerable from PPVFR Act documents already present.

---

## Gap G006 — IND-IP-INDIA (IP India Office Documents)

**Gap ID:** G006
**Domain:** ip_india
**Question/use case:** General queries about IP India office procedures, patent filing at the Patent Office, etc.
**Existing evidence:** `RAG V2/dataset/ip_india/` is EMPTY. Only `.gitkeep` placeholder exists.
**Why insufficient:** IP India office documents may contain procedural information not covered by the Acts and Rules.
**Required source:** IP India official publications.
**Authoritative source:** IP India (`https://ipindia.gov.in/`).
**Decision:** NOT ADDED — per RULE 5.
**Added / Not Added:** Not Added
**Reason:** No benchmark question requires IP India office-specific documents. The Acts and Rules already cover all substantive questions.

---

## Summary

| Gap ID | Domain | Question | Status | Priority |
|--------|--------|----------|--------|----------|
| G001 | PATENT | Q03 | Raw source unavailable | HIGH |
| G002 | FSS | Q08/Q09/Q23 | Extraction failed | HIGH |
| G003 | ABS | Q22 | Document exists, quality uncertain | MEDIUM |
| G004 | AYUSH | Q04 | Document exists, verify coverage | MEDIUM |
| G005 | TKDL | Q19 (potential) | Empty directory | LOW |
| G006 | ip_india | General | Empty directory | LOW |

## Decision Summary

Per RULE 5: **Do not expand the dataset yet.**

The only action that does NOT constitute dataset expansion is **G002** — replacing the corrupt PDF and re-extracting. This is a corpus-quality fix, not an expansion.

All other gaps (G001, G003, G004, G005, G006) require either a benchmark failure proving the missing source is needed, or retrieval quality analysis proving insufficient coverage.
