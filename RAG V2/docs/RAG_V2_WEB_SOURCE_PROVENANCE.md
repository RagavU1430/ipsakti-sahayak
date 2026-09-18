# RAG V2 Phase 3 Web Source Provenance

Retrieved: 2026-09-14

## Acquisition outcome

No new source was ingested. One exact primary source was located and verified on the official FSSAI catalogue and in the official Gazette search result, but it could not be acquired as a valid file from this execution environment. The existing 3,221-byte file remains quarantined and is not indexed.

| document_id | title | domain | source type | authority | date/version | official URL | local filename | validation status |
|---|---|---|---|---|---|---|---|---|
| IND-FSS-AA-2022 | Food Safety and Standards (Ayurveda Aahara) Regulations, 2022 | FOOD | Gazette regulation | Food Safety and Standards Authority of India | 5 May 2022; original regulation | https://egazette.nic.in/WriteReadData/2022/235642.pdf | `dataset/fssai/ayurveda_aahara/ayurveda_aahara_regulations_2022.pdf` | REJECTED / QUARANTINED |

## Validation record — IND-FSS-AA-2022

- Exact title and authority: verified from FSSAI's official Food Law regulations catalogue and the FSSAI notification listing.
- Publication date: 5 May 2022 (Gazette publication); FSSAI uploaded the notification on 9 May 2022.
- Intended content: Regulation 2 definition and Regulations 12–13 labelling requirements; it is the exact source required by frozen Q08, Q09, and Q23.
- Primary URL: `https://egazette.nic.in/WriteReadData/2022/235642.pdf`.
- FSSAI catalogue URL: `https://www.fssai.gov.in/food-law/regulations`.
- FSSAI historical download URL: `https://fssai.gov.in/upload/notifications/2022/05/62789a20b54bdGazette_Notification_Ayurveda_Aahara_09_05_2022.pdf`.
- Download date: 2026-09-14.
- Download validation: FAIL. The FSSAI endpoint returned a 3,221-byte HTML single-page-app shell instead of a PDF (`<!doctype html>` rather than `%PDF-`); the eGazette host could not be resolved from either command-line or browser execution in this environment.
- File hash: not recorded for a replacement because no valid binary was acquired. The invalid response was removed and not ingested.
- Extraction method/status: pypdf / not attempted on the rejected response. Existing source remains `extraction_failed` (`PdfStreamError: Stream has ended unexpectedly`).
- Page count/quality notes: unavailable; no valid PDF was downloaded.

## Existing source verification

| document_id | title | official source checked | outcome |
|---|---|---|---|
| IND-BD-AMEND-2023 | Biological Diversity (Amendment) Act, 2023 | National Biodiversity Authority home page and India Code search result | Existing 15-page PDF is present and extractable. It is the exact amendment required by Q18; no extra amendment source was needed. |

The NBA page identifies the Authority as the Government of India statutory body and links the 2023 amendment and its corrigendum. The source already in the corpus was retained; its content was not redownloaded merely to increase document count.
