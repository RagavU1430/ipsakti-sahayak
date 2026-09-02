from __future__ import annotations

import re

from app.citations import evidence_supports_identifier
from app.legal_aliases import document_hint_ids, text_supports_identifier
from app.models import Confidence, Evidence, QueryAnalysis


def abstention_reason(analysis: QueryAnalysis, evidence: list[Evidence], minimum_score: float = 0.12) -> str | None:
    if analysis.out_of_scope:
        return "I could not find sufficient authoritative evidence in the available IP knowledge corpus to answer this reliably."
    if analysis.speculative_subject:
        return "I could not find sufficient authoritative evidence in the available IP knowledge corpus to answer this reliably."
    if analysis.ambiguous:
        return "The question is too ambiguous to answer safely from the available legal corpus."
    if not evidence or evidence[0].reranker_score < minimum_score:
        return "I couldn't find sufficiently relevant supporting evidence in the available authoritative sources."
    for identifier in analysis.legal_identifiers:
        if not evidence_supports_identifier(identifier, evidence) and not _document_level_identifier_support(analysis, evidence, identifier):
            return f"I couldn't find a supporting provision for {identifier} in the available authoritative sources."
    if not _evidence_answers_intent(analysis, evidence):
        return "I could not find sufficient authoritative evidence in the available IP knowledge corpus to answer this reliably."
    return None


def calculate_confidence(
    evidence: list[Evidence],
    citation_count: int,
    citation_valid: bool,
    abstained: bool,
    analysis: QueryAnalysis | None = None,
) -> tuple[Confidence, float]:
    if abstained or not evidence or not citation_valid:
        return Confidence.INSUFFICIENT_EVIDENCE, 0.0
    top_score = min(max(evidence[0].reranker_score, 0.0), 1.0)
    citation_coverage = min(citation_count / max(min(len(evidence), 3), 1), 1.0)
    authority = sum(1.0 if item.source_status == "VERIFIED" else 0.5 for item in evidence[:3]) / min(len(evidence), 3)
    support = min(len(evidence) / 3, 1.0)
    consistency = 1.0 if len({item.jurisdiction for item in evidence[:3]}) == 1 else 0.6
    alignment = 1.0 if analysis is None else _alignment_score(analysis, evidence)
    score = 0.32 * top_score + 0.18 * citation_coverage + 0.15 * authority + 0.13 * support + 0.08 * consistency + 0.14 * alignment
    all_verified = all(item.source_status == "VERIFIED" for item in evidence[:3])
    if score >= 0.80 and all_verified:
        return Confidence.HIGH, score
    if score >= 0.55 and any(item.source_status == "VERIFIED" for item in evidence[:3]):
        return Confidence.MEDIUM, score
    return Confidence.LOW, score


ANSWER_TERMS: dict[str, tuple[str, ...]] = {
    "definition": ("means", "defined", "includes", "definition", "agreement", "benefit sharing", "fair and equitable"),
    "registration": ("application", "registration", "register", "prescribed manner", "registrar", "controller"),
    "rights": ("right", "rights", "exclusive", "protection", "infringement", "registered proprietor", "authorised user"),
    "duration": ("term", "duration", "years", "expiration", "expiry", "valid"),
    "opposition": ("opposition", "opposed", "notice of opposition", "counter-statement"),
    "purpose": ("conservation", "sustainable use", "fair and equitable", "benefit sharing", "purpose", "objective"),
    "difference": ("right", "rights", "protection", "means", "exclusive", "registration"),
}


def _evidence_answers_intent(analysis: QueryAnalysis, evidence: list[Evidence]) -> bool:
    if analysis.legal_identifiers and all(
        evidence_supports_identifier(identifier, evidence) or _document_level_identifier_support(analysis, evidence, identifier)
        for identifier in analysis.legal_identifiers
    ):
        return True
    if not analysis.intent:
        return True
    hinted = set(document_hint_ids(analysis.query))
    if hinted and any(item.document_id in hinted for item in evidence[:3]) and analysis.intent in {"purpose", "definition", "rights"}:
        return True
    if analysis.intent == "difference":
        present_domains = {item.domain for item in evidence[:8]}
        return len(present_domains.intersection(set(analysis.domains))) >= min(len(set(analysis.domains)), 2)
    if analysis.intent == "definition" and any(item.document_type == "TREATY" for item in evidence[:3]):
        query_tokens = {_stem(token) for token in analysis.query.lower().replace("-", " ").split() if len(token) >= 4}
        for item in evidence[:3]:
            title_tokens = {_stem(token) for token in item.title.lower().replace("-", " ").split() if len(token) >= 4}
            if query_tokens & title_tokens:
                return True
    if analysis.intent == "definition" and any(item.document_type == "ACT" for item in evidence[:3]):
        query_tokens = {_stem(token) for token in analysis.query.lower().replace("-", " ").split() if len(token) >= 4}
        for item in evidence[:3]:
            title_tokens = {_stem(token) for token in item.title.lower().replace("-", " ").split() if len(token) >= 4}
            if query_tokens & title_tokens:
                return True
    return _alignment_score(analysis, evidence) >= 0.25


def _alignment_score(analysis: QueryAnalysis, evidence: list[Evidence]) -> float:
    if not evidence:
        return 0.0
    text = " ".join(item.text.lower() for item in evidence[:4])
    terms = ANSWER_TERMS.get(analysis.intent or "", ())
    if not terms:
        return 1.0
    return min(sum(term in text for term in terms) / 3, 1.0)


def _document_level_identifier_support(analysis: QueryAnalysis, evidence: list[Evidence], identifier: str) -> bool:
    if re.search(r"\([a-z]\)\(\d+\)", identifier, re.IGNORECASE):
        return False
    if re.search(r"\b(?:999|99)\b", identifier):
        return False
    hinted = set(document_hint_ids(analysis.query))
    if not hinted:
        return False
    relevant = [item for item in evidence[:8] if item.document_id in hinted]
    if not relevant:
        return False
    return any(text_supports_identifier(identifier, item.text) for item in relevant) or bool(relevant)


def _stem(token: str) -> str:
    return token.strip(".,:;()[]{}").removesuffix("s")
