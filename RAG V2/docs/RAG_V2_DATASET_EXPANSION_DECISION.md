# RAG V2 Phase 3 Dataset Expansion Decision

Decision: **ADDITIONAL SOURCE REQUIRED**

The corpus was not expanded speculatively. The 2023 Biological Diversity amendment already exists in the corpus; Phase 3 repaired its companion India Code Act extraction and chunk boundaries instead of adding a duplicate source.

The only outstanding required source is:

| Frozen questions | Missing knowledge | Exact required source | Status |
|---|---|---|---|
| Q08, Q09, Q23 | Ayurveda Aahara definition and complete labelling rules (Regulations 2, 12 and 13) | Food Safety and Standards (Ayurveda Aahara) Regulations, 2022, Gazette of India document 235642 | Located as an official FSSAI/Gazette source, but no valid PDF could be retrieved in this environment. Existing local file is corrupt and excluded. |

No TKDL, IP India directory, bulk dataset, or unrelated regulation is required by the frozen benchmark. A future acquisition must validate the actual Gazette PDF (PDF signature, non-empty pages, title, Regulations 2/12/13, coherent extraction) before replacing the quarantined file and rebuilding.
