# Ingestion

1. Validate registry schema, official hosts, statuses, and unique IDs with `discover_sources.py` and tests.
2. Download records that have a verified `download_url`. Requests use a descriptive user agent, redirects, bounded retries, and a 45-second timeout.
3. Reject empty responses and HTML masquerading as PDFs. Store retrieval headers beside local files.
4. Validate PDF signatures, opening title matter, file size, SHA-256, and approximate title match.
5. Extract with PyMuPDF. HTML uses BeautifulSoup. OCR is not automatic; low-text documents are reported for manual OCR workflow selection.
6. Normalize whitespace without rewriting legal wording.
7. Detect Chapter, Part, Section, Rule, Regulation, Article, Schedule, Annexure, and Appendix headings. Pack paragraphs to approximately 900 words without arbitrarily slicing sentences.
8. Generate processed JSONL and metadata. Production embeddings are deliberately out of scope.

The current regular expression is conservative. Complex multi-column Gazettes and headings split across lines require document-specific QA. Forms and tables remain page text and may need a later specialized loader.

