from pathlib import Path
from app.ingestion.downloader import looks_like_pdf, sha256_file
from app.ingestion.chunker import structural_units, chunks
from app.ingestion.pipeline import project_path
def test_pdf_signature(): assert looks_like_pdf(b"%PDF-1.7\n") and not looks_like_pdf(b"<html>")
def test_sha256(tmp_path):
    p=tmp_path/"x"; p.write_bytes(b"abc"); assert sha256_file(p)=="ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
def test_section_preservation():
    units=structural_units([{"page":1,"text":"CHAPTER II\nHeading\n\nSection 3\nExcluded subject matter."}]); out=chunks(units)
    assert any(x["kind"]=="SECTION" and x["number"]=="3" for x in out)
def test_project_path_rejects_escape():
    import pytest
    with pytest.raises(ValueError): project_path("../../outside")

