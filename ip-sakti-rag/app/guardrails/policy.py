from __future__ import annotations

from app.citations import evidence_supports_identifier
from app.models import Confidence, Evidence, QueryAnalysis


def abstention_reason(analysis: QueryAnalysis, evidence: list[Evidence], minimum_score: float = 0.12) -> str | None:
    if analysis.ambiguous:
        return "The question is too ambiguous to answer safely from the available legal corpus."
    if not evidence or evidence[0].reranker_score < minimum_score:
        return "I couldn't find sufficiently relevant supporting evidence in the available authoritative sources."
    for identifier in analysis.legal_identifiers:
        if not evidence_supports_identifier(identifier, evidence):
            return f"I couldn't find a supporting provision for {identifier} in the available authoritative sources."
    return None


def calculate_confidence(evidence: list[Evidence], citation_count: int, citation_valid: bool, abstained: bool) -> tuple[Confidence, float]:
    if abstained or not evidence or not citation_valid:
        return Confidence.INSUFFICIENT_EVIDENCE, 0.0
    top_score = min(max(evidence[0].reranker_score, 0.0), 1.0)
    citation_coverage = min(citation_count / max(min(len(evidence), 3), 1), 1.0)
    authority = sum(1.0 if item.source_status == "VERIFIED" else 0.5 for item in evidence[:3]) / min(len(evidence), 3)
    support = min(len(evidence) / 3, 1.0)
    consistency = 1.0 if len({item.jurisdiction for item in evidence[:3]}) == 1 else 0.6
    score = 0.40 * top_score + 0.20 * citation_coverage + 0.15 * authority + 0.15 * support + 0.10 * consistency
    all_verified = all(item.source_status == "VERIFIED" for item in evidence[:3])
    if score >= 0.80 and all_verified:
        return Confidence.HIGH, score
    if score >= 0.55 and any(item.source_status == "VERIFIED" for item in evidence[:3]):
        return Confidence.MEDIUM, score
    return Confidence.LOW, score
