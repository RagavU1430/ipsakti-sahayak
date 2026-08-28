from __future__ import annotations
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import csv, json
from datetime import datetime, timezone
from app.ingestion.chunker import chunks, structural_units
from app.ingestion.extractor import clean_text, extract
from app.ingestion.pipeline import ROOT, project_path
from app.ingestion.registry import FIELDS, read_registry

def main() -> int:
    registry=ROOT/"dataset/manifests/source_registry.csv"; rows=read_registry(registry); documents=[]; chunk_rows=[]; ocr=[]; pages=0
    for row in rows:
        if row["status"] not in {"VALIDATED","EXTRACTED","PROCESSED"}: continue
        result=extract(project_path(row["local_path"])); pages += result["page_count"]
        if result["ocr_required"]: ocr.append(row["source_id"]); continue
        for page in result["pages"]: page["text"]=clean_text(page["text"])
        version_id=f"{row['source_id']}:{row['sha256'][:12]}"
        documents.append({"document_id":row["source_id"],"document_version_id":version_id,"title":row["title"],"authority":row["authority"],"domain":row["domain"],"jurisdiction":row["jurisdiction"],"document_type":row["document_type"],"source_url":row["source_url"],"version":row["version"],"publication_date":row["publication_date"] or None,"effective_date":row["effective_date"] or None,"retrieved_at":row["retrieved_at"],"language":row["language"],"local_path":row["local_path"],"sha256":row["sha256"],"status":"VALIDATED","page_count":result["page_count"]})
        for n, item in enumerate(chunks(structural_units(result["pages"])),1):
            kind=item["kind"]; number=item["number"]; label=f"{kind.title()} {number}".strip(); ident=re_safe(number or str(n))
            chunk_rows.append({"chunk_id":f"{row['source_id']}-{kind[:3]}-{ident}-{n:03d}","document_id":row["source_id"],"document_version_id":version_id,"title":row["title"],"authority":row["authority"],"jurisdiction":row["jurisdiction"],"domain":row["domain"],"subdomain":row["subdomain"],"document_type":row["document_type"],"chapter":label if kind=="CHAPTER" else None,"section":label if kind=="SECTION" else None,"subsection":None,"rule":label if kind=="RULE" else None,"article":label if kind=="ARTICLE" else None,"structure_type":kind,"structure_number":number or None,"page_start":item["page_start"],"page_end":item["page_end"],"text":item["text"],"source_url":row["source_url"],"citation_label":f"{row['short_title']} — {label}" if label else row["short_title"],"language":row["language"]})
        row["status"]="PROCESSED"
    write_jsonl(ROOT/"dataset/processed/documents.jsonl",documents); write_jsonl(ROOT/"dataset/processed/chunks.jsonl",chunk_rows)
    now=datetime.now(timezone.utc).isoformat(); (ROOT/"dataset/processed/metadata.json").write_text(json.dumps({"generated_at":now,"document_count":len(documents),"chunk_count":len(chunk_rows),"total_pages":pages,"ocr_required":ocr},indent=2),encoding="utf-8")
    with registry.open("w",encoding="utf-8-sig",newline="") as h: w=csv.DictWriter(h,fieldnames=FIELDS);w.writeheader();w.writerows(rows)
    print(json.dumps({"documents":len(documents),"pages":pages,"chunks":len(chunk_rows),"ocr_required":ocr}))
    return 0
def re_safe(v:str)->str:
    import re
    return re.sub(r"[^A-Za-z0-9]+","-",v).strip("-") or "NA"
def write_jsonl(path, records): path.write_text("".join(json.dumps(x,ensure_ascii=False)+"\n" for x in records),encoding="utf-8")
if __name__=="__main__": raise SystemExit(main())
