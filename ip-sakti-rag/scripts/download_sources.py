from __future__ import annotations
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import argparse, csv, json
from datetime import datetime, timezone
from app.ingestion.downloader import download
from app.ingestion.pipeline import ROOT, project_path
from app.ingestion.registry import FIELDS, read_registry

REGISTRY = ROOT / "dataset/manifests/source_registry.csv"

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-id", action="append", help="Download only selected source id(s)")
    args = parser.parse_args()
    rows, manifest = read_registry(REGISTRY), []
    old_manifest = ROOT / "dataset/manifests/download_manifest.json"
    if old_manifest.exists(): manifest = json.loads(old_manifest.read_text(encoding="utf-8"))
    by_id = {item["source_id"]: item for item in manifest}
    for row in rows:
        if args.source_id and row["source_id"] not in args.source_id: continue
        if row["status"] in {"RESTRICTED", "REQUIRES_MANUAL_DOWNLOAD"}: continue
        url = row["download_url"].strip()
        if not url: continue
        try:
            meta = download(url, project_path(row["local_path"]), row["source_url"])
            row.update(status="DOWNLOADED", retrieved_at=meta["retrieved_at"], sha256=meta["sha256"], file_size_bytes=str(meta["size_bytes"]), content_available="true")
            by_id[row["source_id"]] = {"source_id":row["source_id"],"title":row["title"],"source_url":row["source_url"],"download_url":url,"local_path":row["local_path"],"sha256":meta["sha256"],"size_bytes":meta["size_bytes"],"retrieved_at":meta["retrieved_at"],"validation_status":"PENDING"}
            print(f"DOWNLOADED {row['source_id']} {meta['size_bytes']} bytes")
        except Exception as exc:
            row["status"] = "FAILED"; row["notes"] = f"{row['notes']} | Download failed: {exc}".strip(" |"); print(f"FAILED {row['source_id']}: {exc}")
    with REGISTRY.open("w", encoding="utf-8-sig", newline="") as handle:
        writer=csv.DictWriter(handle, fieldnames=FIELDS); writer.writeheader(); writer.writerows(rows)
    old_manifest.write_text(json.dumps(sorted(by_id.values(), key=lambda x:x["source_id"]), indent=2), encoding="utf-8")
    return 0
if __name__ == "__main__": raise SystemExit(main())
