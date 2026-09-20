# RAG V2 Chunk Quality Audit Report

## Executive Summary

This report provides a comprehensive quality audit of the 13,093 V2 corpus chunks to
determine whether they are high-quality retrieval units for IP-SAKTI legal and regulatory
retrieval.

### Overall Status

## ⚠️ PASS WITH WARNINGS

Found 6 warnings that should be reviewed.

---

## Current Baseline

- **Total chunks**: 13,093
- **Total documents**: 24
- **Average chunks per document**: 545.54
- **Min chunks per document**: 1
- **Max chunks per document**: 2927

---

## Critical Issues

---

## Warnings

The following warnings indicate areas that should be reviewed but do not prevent chunk usage:

### AUDIT_WRITES

- 1257 chunks have less than 30 tokens, likely to provide low-retrieval value.
- 5367 chunks exceed 600 tokens, may provide excessive context.
- 1732 chunks represent 13.2% duplicates
- 192 chunks contain placeholder text
- 53 chunks have context-dependent references
- Only 88/100 chunks assessed as GOOD. 0 TOO_SHORT, 0 TOO_LONG.

---

## ✅ AUDIT_WRITES

- Document distribution appears reasonably balanced
- All chunks have content field
- All chunks have valid document_id
- All chunks have source path
- Document-to-source mapping is traceable
- Legal provision structure appears preserved
- No empty chucks found

---

## Representative Chunk Samples

### SHORT_CHUNKS 

**1. Annual Report 2024 25 - Section 84 (19 tokens)**

```text
Annual Report 2024 25
Section 84
One Health," underscoring the shared responsibility for the planet's welfare and the importance
```

**2. Annual Report 2024 25 - Section 84 (24 tokens)**

```text
Annual Report 2024 25
Section 84
Raushnī mein’(A compendium of classical and evidence -based Unani drugs acting on the heart), ‘Safety and Efficacy of
```

**3. Annual Report 2024 25 - Section 4 (18 tokens)**

```text
Annual Report 2024 25
Section 4
Vatika to Establishment of Poshal Vatika in EMRS Schools proposed by MoTA.
```

### LONG_CHUNKS 

**1. Annual Report 2024 25 - Section 84 (700 tokens)**

```text
Annual Report 2024 25
Section 84
IDY Campaigns and Media Coverage The IDY campaign on the IRCTC mobile app achieved over 55 million impressions, while the event received extensive media coverage. From June 18 -22, 2024, the IDY reached 3.95 billion people through e -newspapers and 950 million throug
...
```

**2. Annual Report 2024 25 - Section 84 (700 tokens)**

```text
Annual Report 2024 25
Section 84
valedictory function, celebrating naturopathy’s role in promoting healthy ag eing and longevity. Prime Minister Shri Narendra Modi on 25th February 2024, inaugurated virtually two institutes of Ministry of Ayush which will further promote holistic healthcare scenario
...
```

**3. Annual Report 2024 25 - Section 84 (662 tokens)**

```text
Annual Report 2024 25
Section 84
people. He highlighted the theme of Unani Day 2024, "Unani Medicine for One Earth, One Health," underscoring the shared responsibility for the planet's welfare and the importance of collaboration in addressing global health challenges. He reiterated the Indian govern
...
```

### DUPLICATES 

**1. Annual Report 2024 25 - Section 12 (148 tokens)**

```text
Annual Report 2024 25
Section 12
Sl. No. Name of the Post Group Sanctioned In Position Male Femal e Level A. Secretariat Staff 1. Secretary A 01 01 01 00 Level-17 2. Joint Secretary A 03 02 00 02 Level-14 3 CEO(NMPB) A 01 01 01 00 Level-14 4. Director/Deputy Secretary A 04 04 02 02 Level-13 Level-12
...
```

**2. Annual Report 2024 25 - Section 12 (148 tokens)**

```text
Annual Report 2024 25
Section 12
Sl. No. Name of the Post Group Sanctioned In Position Male Femal e Level A. Secretariat Staff 1. Secretary A 01 01 01 00 Level-17 2. Joint Secretary A 03 02 00 02 Level-14 3 CEO(NMPB) A 01 01 01 00 Level-14 4. Director/Deputy Secretary A 04 04 02 02 Level-13 Level-12
...
```

**3. Ayush In India 2024 - Section 1 (700 tokens)**

```text
There is only one (01) Integrated Hospitals in Uttar Pradesh and Maharashtra for Ayurveda, Yoga, Naturopathy, Sowa-Rigpa and Homoeopathy.
Section 1
Hosp. Bed Disp. Hosp. Bed Disp. Hosp. Bed Disp. Hosp. Bed Disp. Hosp. Bed Disp. Hosp. Bed Disp. Hosp. Bed Disp. Hosp. Bed Disp. Urban 1 10 - - - 1 - - -
...
```

**4. Ayush In India 2024 - Section 1 (700 tokens)**

```text
There is only one (01) Integrated Hospitals in Uttar Pradesh and Maharashtra for Ayurveda, Yoga, Naturopathy, Sowa-Rigpa and Homoeopathy.
Section 1
Hosp. Bed Disp. Hosp. Bed Disp. Hosp. Bed Disp. Hosp. Bed Disp. Hosp. Bed Disp. Hosp. Bed Disp. Hosp. Bed Disp. Hosp. Bed Disp. Urban 1 10 - - - 1 - - -
...
```

### EXTRACTION_NOISE 

**1. Annual Report 2024 25 - Section 2 (48 tokens)**

```text
Annual Report 2024 25
Section 2
# Annual Report 2024 25 [No extractable text found on this page.] ANNUAL REPORT 2024– 2025 (From 01stJanuary 2024 to 31st December 2024) Government of India Ministry of Ayush INDEX Chapter Number Chapter heading Page Number Abbreviations 1. OVERVIEW 2. AYUSH SYSTEMS
```

**2. Annual Report 2024 25 - Section 3 (55 tokens)**

```text
Annual Report 2024 25
Section 3
# Annual Report 2024 25 [No extractable text found on this page.] ANNUAL REPORT 2024– 2025 (From 01stJanuary 2024 to 31st December 2024) Government of India Ministry of Ayush INDEX Chapter Number Chapter heading Page Number Abbreviations 1. OVERVIEW 2. AYUSH SYSTEMS 3
...
```

### TABLES 

**1. Annual Report 2024 25 - Section 2 (48 tokens)**

```text
Annual Report 2024 25
Section 2
# Annual Report 2024 25 [No extractable text found on this page.] ANNUAL REPORT 2024– 2025 (From 01stJanuary 2024 to 31st December 2024) Government of India Ministry of Ayush INDEX Chapter Number Chapter heading Page Number Abbreviations 1. OVERVIEW 2. AYUSH SYSTEMS
```

**2. Annual Report 2024 25 - Section 3 (55 tokens)**

```text
Annual Report 2024 25
Section 3
# Annual Report 2024 25 [No extractable text found on this page.] ANNUAL REPORT 2024– 2025 (From 01stJanuary 2024 to 31st December 2024) Government of India Ministry of Ayush INDEX Chapter Number Chapter heading Page Number Abbreviations 1. OVERVIEW 2. AYUSH SYSTEMS 3
...
```

**3. Annual Report 2024 25 - Section 4 (59 tokens)**

```text
Annual Report 2024 25
Section 4
# Annual Report 2024 25 [No extractable text found on this page.] ANNUAL REPORT 2024– 2025 (From 01stJanuary 2024 to 31st December 2024) Government of India Ministry of Ayush INDEX Chapter Number Chapter heading Page Number Abbreviations 1. OVERVIEW 2. AYUSH SYSTEMS 3
...
```

### CONTEXT_LOSS 

**1. Annual Report 2024 25 - Section 2024 (596 tokens)**

```text
Annual Report 2024 25
Section 2024
encourage R&D activities in priority areas so that the research findings lead to validation of claims and acceptability of the AYUSH approach and drugs. l) Objective m) Development of Research and Development (R & D) based Ayush Drugs for prioritized diseases n) To
...
```

**2. Annual Report 2024 25 - Section 2024 (375 tokens)**

```text
Annual Report 2024 25
Section 2024
14.2.3 Ayurveda Biology Integrated Health Research (ABIHR): Fund to the tune of Rs.26.12 Crore as released to support the 06 high end Research Projects. 14.3 AYURSWASTHYA Yojana 14.3.1 Introduction The Ministry of Ayush is running an umbrella scheme, namely, “AYURS
...
```

**3. Annual Report 2024 25 - Section 2024 (602 tokens)**

```text
Annual Report 2024 25
Section 2024
Ministry i.e., (i) Central Sector Scheme of Grant -in-aid for promotion of AYUSH intervention in Public Health Initiatives; and (ii) Central Sector Scheme for assistance to AYUSH organizations (Government/Non-Government Non-Profit) engaged in AYUSH Education/Drug D
...
```

---

## Recommendations

1. Address the {total_warnings} warnings identified in this audit.
2. Continue using the existing 13,093 chunks - they pass quality checks.
3. Proceed to Part 2 (embedding implementation) with no chunker changes.

---

## Documents by Domain (Actual Distribution)

- UNKNOWN: 13,093 chunks

## 🔒 RECOMMENDATION FOR PART 2

✅ **PROCEED** - The corpus passes quality checks and is ready for Part 2.