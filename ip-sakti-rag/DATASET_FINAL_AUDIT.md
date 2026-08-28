# Dataset Final Audit Report - IP Sakti A RAG

This document details the final metrics and audit results of the ingestion corpus for **IP Sakthi A RAG**.

## 1. Corpus Summary Metrics

*   **Total Document Registries:** 27
*   **Successfully Ingested & Processed:** 25
*   **Restricted Access (Registry Only):** 1 (`IND-TKDL-REGISTRY`)
*   **Pending Direct File Discovery:** 1 (`INT-WIPO-HAGUE`)
*   **Total Pages Ingested:** 1,556
*   **Total Chunks Generated:** 830
*   **Pending OCR Action Items:** 0 (All OCR fallbacks executed successfully)

---

## 2. Ingestion Status Breakdown

| Status | Count | Description |
| :--- | :---: | :--- |
| **PROCESSED** | 25 | Document fully validated, text extracted (with OCR fallback where needed), and structural chunks generated. |
| **RESTRICTED** | 1 | Traditional Knowledge Digital Library (`IND-TKDL-REGISTRY`). Explicitly restricted from automated scraping. |
| **DISCOVERED** | 1 | Hague Agreement (`INT-WIPO-HAGUE`). Direct file link pending official discovery. |

---

## 3. Domain Distribution

| Domain | Count |
| :--- | :---: |
| **INTERNATIONAL** | 7 |
| **ABS (Access & Benefit Sharing)** | 3 |
| **PATENT** | 2 |
| **TRADEMARK** | 2 |
| **GI (Geographical Indications)** | 2 |
| **COPYRIGHT** | 2 |
| **DESIGN** | 2 |
| **PLANT_VARIETY** | 2 |
| **FOOD** | 2 |
| **AYURVEDA** | 2 |
| **TKDL** | 1 |

---

## 4. Document Type Distribution

| Document Type | Count |
| :--- | :---: |
| **ACT** | 7 |
| **RULES** | 7 |
| **TREATY** | 7 |
| **REPORT** | 2 |
| **AMENDMENT_ACT** | 1 |
| **REGULATION** | 1 |
| **ORDER** | 1 |
| **RESTRICTED_DATABASE** | 1 |

---

## 5. Authority Distribution

| Authority | Count |
| :--- | :---: |
| **Government of India / India Code** | 12 |
| **World Intellectual Property Organization (WIPO)** | 6 |
| **National Biodiversity Authority (NBA)** | 2 |
| **Food Safety and Standards Authority of India (FSSAI)** | 2 |
| **Ministry of Ayush** | 2 |
| **CSIR / Ministry of Ayush** | 1 |
| **World Trade Organization (WTO)** | 1 |
| **Government of India** | 1 |

---

## 6. OCR Pipeline Audit

Scanned and image-heavy PDFs automatically fall back to the native Windows UWP OCR engine if average text density is low (< 80 characters/page).

*   **OCR Processed Document:** `ayurveda_aahara_order_2025.pdf` (`IND-FSS-AA-ORDER-2025`)
*   **Total Pages OCR'd:** 172 pages
*   **Total Characters Extracted:** 105,245 characters
*   **Average Chars/Page:** 611.9
*   **Blank Pages Detected:** 42 pages (bilingual blank sheets and stamps)
*   **Page OCR Text Coverage:** 75.6%
*   **OCR Engine:** Native Windows UWP `OcrEngine` with `en-US` and `en-GB` recognizers.
*   **Local Caching:** OCR output is cached to `ayurveda_aahara_order_2025.ocr.json` to prevent slow re-runs.

---

## 7. Structure & Chunking Audit

All legal files have been partitioned according to logical legal boundaries (Acts $\rightarrow$ Sections; Rules $\rightarrow$ Rules; Treaties $\rightarrow$ Articles). Chunks have been limited to a maximum of 900 words.

*   Every chunk successfully preserves the following metadata:
    *   `document_id`
    *   `title`
    *   `authority`
    *   `domain`
    *   `document_type`
    *   `jurisdiction`
    *   `section`
    *   `subsection` (retained as `None` where not structured)
    *   `chapter`
    *   `rule_number` (populated if kind is `RULE`)
    *   `article_number` (populated if kind is `ARTICLE`)
    *   `page_start`
    *   `page_end`
    *   `source_url`
    *   `text` (cleaned of byte marks, soft hyphens, and duplicate spacing)
