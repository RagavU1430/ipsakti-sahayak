from __future__ import annotations
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from app.ingestion.registry import read_registry, validate_row
from app.ingestion.pipeline import ROOT
def main()->int:
    errors=0
    for row in read_registry(ROOT/"dataset/manifests/source_registry.csv"):
        found=validate_row(row)
        print(row["source_id"], "OK" if not found else "; ".join(found)); errors += bool(found)
    return int(bool(errors))
if __name__=="__main__": raise SystemExit(main())
