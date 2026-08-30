from __future__ import annotations

import re

from app.models import Citation, Evidence


PROVISION_MENTION_RE = re.compile(
    r"\b(?P<kind>Section|Rule|Regulation|Article)\s+(?P<number>\d+[A-Za-z]?(?:\([A-Za-z0-9]+\))?(?:\.\d+)*)",
    re.IGNORECASE,
)


def citation_from_evidence(item: Evidence) -> Citation:
    return Citation(
        document_id=item.document_id,
        title=item.title,
        authority=item.authority,
        chunk_id=item.chunk_id,
        source_url=item.source_url,
        section=_combined(item.section, item.subsection or item.clause),
        subsection=item.subsection,
        rule_number=_combined(item.rule_number, item.sub_rule or item.clause),
        sub_rule=item.sub_rule,
        regulation_number=_combined(item.regulation_number, item.subsection or item.clause),
        article_number=item.article_number,
        paragraph_number=item.paragraph_number,
        clause=item.clause,
        page_start=item.page_start,
        page_end=item.page_end,
    )


def citations_for(evidence: list[Evidence], used_chunk_ids: list[str]) -> list[Citation]:
    by_id = {item.chunk_id: item for item in evidence}
    return [citation_from_evidence(by_id[chunk_id]) for chunk_id in dict.fromkeys(used_chunk_ids) if chunk_id in by_id]


def validate_citations(answer: str, citations: list[Citation], evidence: list[Evidence]) -> tuple[bool, list[str]]:
    errors: list[str] = []
    evidence_by_id = {item.chunk_id: item for item in evidence}
    for citation in citations:
        item = evidence_by_id.get(citation.chunk_id)
        if item is None or item.document_id != citation.document_id or item.source_url != citation.source_url:
            errors.append(f"citation {citation.chunk_id} is not attached to retrieved evidence")
    available = {_citation_key(citation) for citation in citations}
    cited_evidence = [evidence_by_id[citation.chunk_id] for citation in citations if citation.chunk_id in evidence_by_id]
    for mention in PROVISION_MENTION_RE.finditer(answer):
        key = (mention.group("kind").lower(), mention.group("number").lower())
        literal_support = any(
            re.search(rf"(?<!\w){re.escape(mention.group(0))}(?!\w)", item.text, re.IGNORECASE)
            for item in cited_evidence
        )
        if key not in available and not literal_support:
            errors.append(f"unsupported provision mentioned in answer: {mention.group(0)}")
    return not errors, errors


def evidence_supports_identifier(identifier: str, evidence: list[Evidence]) -> bool:
    match = PROVISION_MENTION_RE.search(identifier)
    if not match:
        return True
    kind, number = match.group("kind").lower(), match.group("number").lower()
    return any((kind, number) == _evidence_key(item) for item in evidence)


def _combined(number: str | None, child: str | None) -> str | None:
    return f"{number}({child})" if number and child else number


def _citation_key(citation: Citation) -> tuple[str, str]:
    if citation.section:
        return "section", citation.section.lower()
    if citation.rule_number:
        return "rule", citation.rule_number.lower()
    if citation.regulation_number:
        return "regulation", citation.regulation_number.lower()
    if citation.article_number:
        return "article", citation.article_number.lower()
    return "", ""


def _evidence_key(item: Evidence) -> tuple[str, str]:
    if item.section:
        return "section", _combined(item.section, item.subsection or item.clause).lower()
    if item.rule_number:
        return "rule", _combined(item.rule_number, item.sub_rule or item.clause).lower()
    if item.regulation_number:
        return "regulation", _combined(item.regulation_number, item.subsection or item.clause).lower()
    if item.article_number:
        return "article", item.article_number.lower()
    return "", ""
