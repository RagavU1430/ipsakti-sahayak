from app.ingestion.chunker import chunk_units, legal_units
from app.ingestion.extractor import PageText


def _document(document_type: str) -> dict:
    return {"document_id": "TEST-DOC", "document_version": "v1", "title": "Test",
            "authority": "Test Authority", "domain": "PATENT", "jurisdiction": "INDIA",
            "document_type": document_type, "source_url": "https://example.invalid/test.pdf",
            "language": "en", "ingestion_status": "VERIFIED"}


def test_act_section_and_clause_metadata() -> None:
    pages = [PageText(7, "3. Definitions.\u2014\n(p) patentee means the person entered on the register", False)]
    chunks = chunk_units(legal_units(pages, "ACT"), _document("ACT"), min_chars=1)
    clause = next(row for row in chunks if row["clause"] == "p")
    assert (clause["section"], clause["clause"], clause["page_start"]) == ("3", "p", 7)


def test_rule_and_article_metadata() -> None:
    rules = [PageText(2, "Rule 24 Request for examination\n(1) A request shall be made.", False)]
    rule_chunks = chunk_units(legal_units(rules, "RULES"), _document("RULES"), min_chars=1)
    assert any(row["rule_number"] == "24" and row["sub_rule"] == "1" for row in rule_chunks)
    treaty = [PageText(4, "Article 6\nDisclosure requirement", False)]
    treaty_chunks = chunk_units(legal_units(treaty, "TREATY"), _document("TREATY"), min_chars=1)
    assert any(row["article_number"] == "6" for row in treaty_chunks)


def test_new_subsection_clears_prior_clause() -> None:
    pages = [PageText(1, "3. Definitions.\u2014\n(1) text\n(a) clause\n(2) next subsection", False)]
    assert legal_units(pages, "ACT")[-1].metadata["clause"] is None


def test_footnote_is_not_misclassified_as_section() -> None:
    pages = [PageText(1, "6. Subs. by Act 15 of 2005 with effect from 1-1-2005", False)]
    assert all(unit.metadata["section"] is None for unit in legal_units(pages, "ACT"))
