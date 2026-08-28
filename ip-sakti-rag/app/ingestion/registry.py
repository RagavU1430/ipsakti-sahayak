from __future__ import annotations

import csv
from pathlib import Path
from urllib.parse import urlparse

FIELDS = ["source_id","title","short_title","authority","domain","subdomain","jurisdiction","country","document_type","source_url","download_url","language","publication_date","enactment_date","effective_date","version","status","access_type","license_notes","local_path","retrieved_at","sha256","file_size_bytes","content_available","notes"]
STATUSES = {"DISCOVERED","DOWNLOAD_PENDING","DOWNLOADED","VALIDATED","EXTRACTED","PROCESSED","RESTRICTED","NOT_FOUND","FAILED","REQUIRES_MANUAL_DOWNLOAD"}
OFFICIAL_HOSTS = {"www.indiacode.nic.in","indiacode.nic.in","upload.indiacode.nic.in","www.ipindia.gov.in","ipindia.gov.in","ayush.gov.in","www.ayush.gov.in","pcimh.gov.in","www.pcimh.gov.in","nbaindia.org","www.nbaindia.org","fssai.gov.in","www.fssai.gov.in","www.wipo.int","wipo.int","www.wto.org","wto.org","www.tkdl.res.in","tkdl.res.in"}

def read_registry(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        if reader.fieldnames != FIELDS:
            raise ValueError("Registry columns do not match the canonical schema")
        return list(reader)

def validate_row(row: dict[str, str]) -> list[str]:
    errors = []
    for key in ("source_id","title","authority","domain","jurisdiction","document_type","source_url","status"):
        if not row.get(key, "").strip(): errors.append(f"missing {key}")
    if row.get("status") not in STATUSES: errors.append("invalid status")
    for key in ("source_url", "download_url"):
        value = row.get(key, "").strip()
        if value:
            parsed = urlparse(value)
            if parsed.scheme != "https" or parsed.hostname not in OFFICIAL_HOSTS:
                errors.append(f"non-official or invalid {key}")
    return errors

