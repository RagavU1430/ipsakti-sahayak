from __future__ import annotations
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import csv, json, re
import fitz
from app.ingestion.downloader import looks_like_pdf, sha256_file
from app.ingestion.pipeline import ROOT, project_path
from app.ingestion.registry import FIELDS, read_registry

def normalize(value: str) -> set[str]: return {w for w in re.findall(r"[a-z]{4,}", value.lower()) if w not in {"with","from","this","that"}}

def main() -> int:
    registry=ROOT/"dataset/manifests/source_registry.csv"; manifest_path=ROOT/"dataset/manifests/download_manifest.json"
    rows=read_registry(registry); manifest=json.loads(manifest_path.read_text(encoding="utf-8")); by_id={x["source_id"]:x for x in manifest}; checks=[]
    for row in rows:
        if row["status"] not in {"DOWNLOADED","VALIDATED","EXTRACTED","PROCESSED"} and row["content_available"].lower() != "true": continue
        path=project_path(row["local_path"]); errors=[]; page_count=0; first=""
        if not path.exists() or path.stat().st_size == 0: errors.append("missing or empty file")
        elif path.suffix.lower()==".pdf":
            if not looks_like_pdf(path.read_bytes()[:8]): errors.append("invalid PDF header")
            else:
                try:
                    with fitz.open(path) as doc:
                        page_count=len(doc)
                        # Covers are often image-only; inspect the opening title matter without OCRing the document.
                        first="\n".join(doc[i].get_text() for i in range(min(60, page_count)))
                    if not first.strip(): errors.append("opening pages have no extractable title text")
                except Exception as exc: errors.append(f"PDF open failed: {exc}")
        if first and normalize(row["short_title"]) and not (normalize(row["short_title"]) & normalize(first)): errors.append("title does not approximately match first page")
        digest=sha256_file(path) if path.exists() else ""
        if row["sha256"] and digest != row["sha256"]: errors.append("checksum mismatch")
        row["sha256"]=digest; row["file_size_bytes"]=str(path.stat().st_size) if path.exists() else ""; row["status"]="FAILED" if errors else "VALIDATED"
        if not errors: row["notes"] = row["notes"].split(" | Download failed:", 1)[0]
        entry=by_id.get(row["source_id"], {"source_id":row["source_id"],"title":row["title"],"source_url":row["source_url"],"local_path":row["local_path"]})
        entry.update(sha256=digest,size_bytes=int(row["file_size_bytes"] or 0),validation_status="INVALID" if errors else "VALID",validation_errors=errors,page_count=page_count); by_id[row["source_id"]]=entry
        if digest and not errors: checks.append(f"{digest}  {row['local_path']}")
        print(("INVALID" if errors else "VALID"), row["source_id"], "; ".join(errors))
    with registry.open("w",encoding="utf-8-sig",newline="") as h: w=csv.DictWriter(h,fieldnames=FIELDS);w.writeheader();w.writerows(rows)
    manifest_path.write_text(json.dumps(sorted(by_id.values(),key=lambda x:x["source_id"]),indent=2),encoding="utf-8")
    (ROOT/"dataset/manifests/checksums.sha256").write_text("\n".join(checks)+( "\n" if checks else ""),encoding="utf-8")
    return 1 if any(x.get("validation_status")=="INVALID" for x in by_id.values()) else 0
if __name__=="__main__": raise SystemExit(main())
