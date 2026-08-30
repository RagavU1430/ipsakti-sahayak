from __future__ import annotations

from app.models import Evidence


def assemble_context(evidence: list[Evidence], max_characters: int) -> tuple[str, list[Evidence]]:
    selected: list[Evidence] = []
    blocks: list[str] = []
    seen: set[str] = set()
    used = 0
    for item in evidence:
        if item.chunk_id in seen:
            continue
        provision = _provision(item)
        block = (
            f"<evidence chunk_id=\"{item.chunk_id}\" document_id=\"{item.document_id}\">\n"
            f"TITLE: {item.title}\nAUTHORITY: {item.authority}\nJURISDICTION: {item.jurisdiction}\n"
            f"PROVISION: {provision or 'not identified'}\nPAGES: {_pages(item)}\n"
            f"SOURCE_URL: {item.source_url}\n"
            "BEGIN_UNTRUSTED_DOCUMENT_DATA\n"
            f"{item.text}\n"
            "END_UNTRUSTED_DOCUMENT_DATA\n</evidence>"
        )
        if blocks and used + len(block) > max_characters:
            break
        blocks.append(block)
        selected.append(item)
        seen.add(item.chunk_id)
        used += len(block)
    return "\n\n".join(blocks), selected


def _provision(item: Evidence) -> str | None:
    if item.section:
        suffix = f"({item.subsection})" if item.subsection else (f"({item.clause})" if item.clause else "")
        return f"Section {item.section}{suffix}"
    if item.rule_number:
        suffix = f"({item.sub_rule})" if item.sub_rule else (f"({item.clause})" if item.clause else "")
        return f"Rule {item.rule_number}{suffix}"
    if item.regulation_number:
        suffix = f"({item.subsection})" if item.subsection else (f"({item.clause})" if item.clause else "")
        return f"Regulation {item.regulation_number}{suffix}"
    if item.article_number:
        suffix = f", paragraph {item.paragraph_number}" if item.paragraph_number else ""
        return f"Article {item.article_number}{suffix}"
    return None


def _pages(item: Evidence) -> str:
    if item.page_start is None:
        return "unverified/unavailable"
    return str(item.page_start) if item.page_start == item.page_end else f"{item.page_start}-{item.page_end}"
