#!/usr/bin/env python3
"""Build RAG V2 index from markdown files → JSONL chunks for LocalCorpusStore."""
from __future__ import annotations

import hashlib, json, re, sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "ip-sakti-rag"))

MD_DIR = Path(__file__).resolve().parents[1] / "dataset_markdown"
OUTPUT_PATH = Path(__file__).resolve().parents[1] / "dataset" / "canonical" / "chunks_v2.jsonl"
OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)

DOC_MAP = {
    "trips_agreement":   ("INT-TRIPS-1994",   "INTERNATIONAL", "INTERNATIONAL", "TREATY", "Agreement on Trade-Related Aspects of Intellectual Property Rights", "TRIPS"),
    "pct":               ("INT-WIPO-PCT",      "INTERNATIONAL", "INTERNATIONAL", "TREATY", "Patent Cooperation Treaty", "PCT"),
    "madrid_protocol":   ("INT-WIPO-MADRID",   "INTERNATIONAL", "INTERNATIONAL", "TREATY", "Madrid Protocol", "MADRID"),
    "budapest_treaty":   ("INT-WIPO-BUDAPEST", "INTERNATIONAL", "INTERNATIONAL", "TREATY", "Budapest Treaty", "BUDAPEST"),
    "gratk_treaty":      ("INT-WIPO-GRATK-2024","INTERNATIONAL","INTERNATIONAL","TREATY", "WIPO Treaty on Genetic Resources and Associated Traditional Knowledge", "GRATK"),
    "paris_convention":  ("INT-WIPO-PARIS",    "INTERNATIONAL", "INTERNATIONAL", "TREATY", "Paris Convention for the Protection of Industrial Property", "PARIS"),
    "patents_act_1970":  ("IND-PAT-ACT-1970",  "INDIA",         "PATENT",        "ACT",    "The Patents Act, 1970", "PATENT"),
    "patents_rules_2003":("IND-PAT-RULES-2003","INDIA",         "PATENT",        "RULES",  "The Patents Rules, 2003", "PATENT"),
    "trade_marks_act_1999":("IND-TM-ACT-1999","INDIA",         "TRADEMARK",     "ACT",    "The Trade Marks Act, 1999", "TRADEMARK"),
    "trade_marks_rules_2017":("IND-TM-RULES-2017","INDIA",     "TRADEMARK",     "RULES",  "The Trade Marks Rules, 2017", "TRADEMARK"),
    "gi_act_1999":       ("IND-GI-ACT-1999",   "INDIA",         "GI",            "ACT",    "The Geographical Indications of Goods (Registration and Protection) Act, 1999", "GI"),
    "gi_rules_2002":     ("IND-GI-RULES-2002", "INDIA",         "GI",            "RULES",  "The Geographical Indications of Goods (Registration and Protection) Rules, 2002", "GI"),
    "copyright_act_1957":("IND-CR-ACT-1957",   "INDIA",         "COPYRIGHT",     "ACT",    "The Copyright Act, 1957", "COPYRIGHT"),
    "designs_act_2000":  ("IND-DES-ACT-2000",  "INDIA",         "DESIGN",        "ACT",    "The Designs Act, 2000", "DESIGN"),
    "designs_rules_2001":("IND-DES-RULES-2001","INDIA",         "DESIGN",        "RULES",  "The Designs Rules, 2001", "DESIGN"),
    "ppvfr_act_2001":    ("IND-PPV-ACT-2001",  "INDIA",         "PLANT_VARIETY", "ACT",    "The Protection of Plant Varieties and Farmers' Rights Act, 2001", "PLANT_VARIETY"),
    "ppvfr_rules_2003":  ("IND-PPV-RULES-2003","INDIA",         "PLANT_VARIETY", "RULES",  "The Protection of Plant Varieties and Farmers' Rights Rules, 2003", "PLANT_VARIETY"),
    "biological_diversity_act_2002":("IND-BD-ACT-2002","INDIA","ABS",           "ACT",    "The Biological Diversity Act, 2002", "ABS"),
    "biological_diversity_rules_2024":("IND-BD-RULES-2024","INDIA","ABS",       "RULES",  "The Biological Diversity Rules, 2024", "ABS"),
    "biological_diversity_amendment_act_2023":("IND-BD-AMEND-2023","INDIA","ABS","ACT","The Biological Diversity (Amendment) Act, 2023", "ABS"),
    "ayush_in_india_2024":("IND-AYUSH-2024",   "INDIA",         "AYURVEDA",      "REPORT", "Ayush in India 2024", "AYURVEDA"),
    "annual_report_2024_25":("IND-AYUSH-AR-2024-25","INDIA",   "AYURVEDA",      "REPORT", "Annual Report 2024-25", "AYURVEDA"),
    "ayurveda_aahara_order_2025":("IND-FSS-AA-ORDER-2025","INDIA","FOOD",       "ORDER",  "Ayurveda Aahara Order 2025", "FOOD"),
    "ayurveda_aahara_regulations_2022":("IND-FSS-AA-2022","INDIA","FOOD",       "REGULATION","Ayurveda Aahara Regulations 2022", "FOOD"),
}


def parse_markdown_chunks(md_path: Path, doc_info):
    """Parse a markdown file into chunk dicts."""
    doc_id, jurisdiction, domain, doc_type, title, _short = doc_info
    content = md_path.read_text(encoding="utf-8")
    if "conversion_status" in content and "extraction_failed" in content:
        return [], "EXTRACTION_FAILED"

    # Remove YAML frontmatter
    if content.startswith("---"):
        end = content.find("---", 3)
        if end > 0:
            content = content[end + 3:].strip()

    # Remove title line (# Title)
    lines = content.split("\n")
    body_lines = []
    for line in lines:
        stripped = line.strip()
        if stripped.startswith("# ") and "title" not in stripped.lower():
            continue
        body_lines.append(line)
    body = "\n".join(body_lines)

    # India Code's captured HTML includes portal navigation before the Act.  Keep
    # only the legal instrument for this source, then split its numbered sections.
    # This is a source-cleaning repair; no legal text is added or inferred.
    if doc_id == "IND-BD-ACT-2002":
        legal_start = body.find("An Act to provide for conservation of biological diversity")
        if legal_start >= 0:
            body = body[legal_start:]

    # Split by ## Page N sections (PDF-derived) or Article/Section markers
    chunks = []
    current_text = ""
    current_page = 0
    page_pattern = re.compile(r"^##\s+Page\s+(\d+)", re.IGNORECASE)
    article_pattern = re.compile(r"^Article\s+(\d+)", re.IGNORECASE)
    section_pattern = re.compile(r"^Section\s+(\d+)", re.IGNORECASE)
    numbered_section_pattern = re.compile(r"^\d+\.\s")

    for line in body.split("\n"):
        stripped = line.strip()

        page_match = page_pattern.match(stripped)
        if page_match and current_text.strip():
            chunks.append({"text": current_text.strip(), "page": current_page})
            current_text = ""
            current_page = int(page_match.group(1))
            continue

        is_boundary = article_pattern.match(stripped) or section_pattern.match(stripped)
        if doc_id == "IND-BD-ACT-2002" and numbered_section_pattern.match(stripped):
            is_boundary = True
        if is_boundary:
            if current_text.strip():
                chunks.append({"text": current_text.strip(), "page": current_page})
                current_text = ""

        current_text += line + "\n"

    if current_text.strip():
        chunks.append({"text": current_text.strip(), "page": current_page})

    return chunks, "VALID" if chunks else "EMPTY"


def main():
    all_chunks = []
    status_counts = {"VALID": 0, "EMPTY": 0, "EXTRACTION_FAILED": 0}

    for md_path in sorted(MD_DIR.rglob("*.md")):
        if ".gitkeep" in str(md_path):
            continue

        stem = md_path.stem
        doc_info = None
        for key, info in DOC_MAP.items():
            if key in stem:
                doc_info = info
                break

        if doc_info is None:
            continue

        doc_id, jurisdiction, domain, doc_type, title, _short = doc_info
        chunks, status = parse_markdown_chunks(md_path, doc_info)
        status_counts[status] = status_counts.get(status, 0) + 1

        if status == "VALID":
            for idx, chunk in enumerate(chunks):
                chunk_hash = hashlib.md5(
                    f"{doc_id}-{idx}-{chunk['text'][:100]}".encode()
                ).hexdigest()[:12]
                chunk_id = f"{doc_id}-{idx:04d}-{chunk_hash}"

                # Determine structure type
                text_start = chunk["text"][:200]
                structure_type = "UNKNOWN"
                if re.search(r"^CHAPTER", chunk["text"], re.IGNORECASE):
                    structure_type = "CHAPTER"
                elif re.search(r"^Article\s+\d+", chunk["text"], re.IGNORECASE):
                    structure_type = "ARTICLE"
                elif re.search(r"^Section\s+\d+", chunk["text"], re.IGNORECASE):
                    structure_type = "SECTION"
                elif re.search(r"^Rule\s+\d+", chunk["text"], re.IGNORECASE):
                    structure_type = "RULE"
                elif re.search(r"^\d+\.\s+", chunk["text"]):
                    structure_type = "SUBSECTION"
                elif re.search(r"^##\s+Page", chunk["text"], re.IGNORECASE):
                    structure_type = "PAGE"
                elif chunk["page"] > 0:
                    structure_type = "PAGE"

                # Domain adjustment for treaties and specific docs
                chunk_domain = domain
                if doc_type == "TREATY":
                    chunk_domain = "INTERNATIONAL"
                elif doc_id.startswith("IND-FSS-AA") and "AAHARA" in doc_id:
                    chunk_domain = "FOOD"
                elif doc_id.startswith("IND-AYUSH"):
                    chunk_domain = "AYURVEDA"
                elif doc_id.startswith("IND-BD"):
                    chunk_domain = "ABS"

                all_chunks.append({
                    "chunk_id": chunk_id,
                    "ordinal": idx + 1,
                    "document_id": doc_id,
                    "document_version": f"{doc_id}:v1",
                    "text": chunk["text"][:5000],
                    "title": title,
                    "authority": "Government of India / IP India" if doc_id.startswith("IND") else "WIPO",
                    "domain": chunk_domain,
                    "jurisdiction": jurisdiction,
                    "document_type": doc_type,
                    "source_url": f"dataset_markdown/{md_path.relative_to(MD_DIR).as_posix()}",
                    "language": "en",
                    "source_status": "VERIFIED",
                    "page_start": chunk["page"] if chunk["page"] > 0 else None,
                    "page_end": chunk["page"] if chunk["page"] > 0 else None,
                    "text_uncertain": False,
                    "structure_anchor": True,
                    "structure_type": structure_type,
                })

    # Write JSONL
    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        for chunk in all_chunks:
            f.write(json.dumps(chunk, ensure_ascii=False) + "\n")

    print(f"=== V2 Index Build Complete ===")
    print(f"Total chunks: {len(all_chunks)}")
    print(f"Status counts: {status_counts}")
    print(f"Output: {OUTPUT_PATH}")
    print()

    doc_counts = {}
    for chunk in all_chunks:
        doc_counts[chunk["document_id"]] = doc_counts.get(chunk["document_id"], 0) + 1
    for doc_id, cnt in sorted(doc_counts.items(), key=lambda x: -x[1]):
        print(f"  {doc_id}: {cnt} chunks")

    print(f"\nTotal documents with chunks: {len(doc_counts)}")


if __name__ == "__main__":
    main()
