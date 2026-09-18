# IP-SAKTI Sahayak — Query Flow & Regression Test Report

**Date:** 2026-09-14 23:40:36  
**Target:** End-to-End Query Routing & RAG Quality Preservation  

---

## 1. Test Suite Summary

| Test Suite | Total Tests | Passed | Failed | Status |
|---|---|---|---|---|
| **RAG V2 Automated Tests** (`test_rag_v2.py`) | 26 | 26 | 0 | **100% PASS** |
| **RAG V2 Frozen Benchmark** (`run_rag_v2_benchmark.py`) | 25 | 25 | 0 | **100% PASS** |
| **10-Question GENERAL Benchmark** (`run_general_benchmark.py`) | 10 | 10 | 0 | **100% PASS** |

---

## 2. 10-Question GENERAL Benchmark Results

| ID | Question | Route | Citations | RAG Used | Latency | Status |
|---|---|---|---|---|---|---|
| GEN-01 | Hello, who are you?... | GENERAL | 0 | No | 1843 ms | **PASS** |
| GEN-02 | What is Python and why is it popular?... | GENERAL | 0 | No | 4005 ms | **PASS** |
| GEN-03 | Tell me a short science joke... | GENERAL | 0 | No | 4602 ms | **PASS** |
| GEN-04 | What is machine learning?... | GENERAL | 0 | No | 4823 ms | **PASS** |
| GEN-05 | Draft a polite email declining an invita... | GENERAL | 0 | No | 4418 ms | **PASS** |
| GEN-06 | What is the capital of France?... | GENERAL | 0 | No | 3627 ms | **PASS** |
| GEN-07 | How does photosynthesis work?... | GENERAL | 0 | No | 5567 ms | **PASS** |
| GEN-08 | Give me a 3-sentence summary of the sola... | GENERAL | 0 | No | 5119 ms | **PASS** |
| GEN-09 | What is the difference between a fruit a... | GENERAL | 0 | No | 5326 ms | **PASS** |
| GEN-10 | Write a haiku about technology... | GENERAL | 0 | No | 13187 ms | **PASS** |

---

## 3. RAG V2 Frozen 25-Question Benchmark Summary

- **Total Questions:** 25
- **Questions Routed to RAG:** 25 (100%)
- **Questions Routed to GENERAL:** 0 (0% false general routing)
- **Average Chunks Retrieved:** 5.4 chunks/question
- **Grounded Responses:** 22/25
- **Appropriate Abstentions / Limitations:** 3/25 (Q08, Q09, Q23)
- **Average Retrieval Latency:** 59.5 ms
- **Average Generation Latency:** 4.8 ms
- **Average RAG Service Latency:** 78.5 ms

---

## 4. RAG V2 26-Test Regression Suite Summary

All 26 automated unit and integration tests passed:
- Valid document ingestion (chunks have metadata, domains, jurisdiction)
- Failed extraction exclusions (no corrupt chunks)
- Legal alias normalization and query routing preservation
- Extractive grounding policy and abstention rules
- Citation validity and chunk correlation
- Unique request ID tracking through metrics
