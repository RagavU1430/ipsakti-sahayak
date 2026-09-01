from __future__ import annotations

import re
from typing import Protocol

from app.models import Evidence, QueryAnalysis


INTENT_TERMS: dict[str, tuple[str, ...]] = {
    "definition": ("means", "defined", "definition", "called", "includes"),
    "registration": ("application", "registration", "register", "registrar", "controller", "prescribed manner"),
    "rights": ("right", "rights", "exclusive", "protection", "infringement", "registered proprietor", "authorised user"),
    "duration": ("term", "duration", "years", "expiry", "expiration", "renewal", "valid"),
    "opposition": ("opposition", "opposed", "notice of opposition", "counter-statement"),
    "purpose": ("conservation", "sustainable use", "fair and equitable", "benefit sharing", "purpose", "objective"),
    "difference": ("means", "right", "rights", "protection", "exclusive", "registration"),
}
NOISY_FRAGMENT_TERMS = (
    "fee",
    "fees",
    "form",
    "schedule",
    "application for extension of time",
    "name, address and nationality",
    "complaint",
    "revocation",
    "cancellation",
)


class Reranker(Protocol):
    name: str
    learned: bool

    def rerank(self, analysis: QueryAnalysis, candidates: list[Evidence], final_count: int) -> list[Evidence]: ...


class LegalFeatureReranker:
    """Deterministic fallback, explicitly not a learned reranker."""

    name = "legal-feature-reranker-v1"
    learned = False

    def rerank(self, analysis: QueryAnalysis, candidates: list[Evidence], final_count: int) -> list[Evidence]:
        query_tokens = set(re.findall(r"[a-z0-9]+", analysis.retrieval_query.lower()))
        for item in candidates:
            text = item.text.lower()
            coverage = sum(token in text for token in query_tokens) / max(len(query_tokens), 1)
            identifier = 1.0 if any(identifier.lower() in text for identifier in analysis.legal_identifiers) else 0.0
            verified = 1.0 if item.source_status == "VERIFIED" else 0.0
            intent = _intent_relevance(analysis, item)
            document = _document_relevance(analysis, item)
            noise_penalty = _noise_penalty(analysis, item)
            item.reranker_score = max(
                0.0,
                0.50 * item.fusion_score
                + 0.16 * coverage
                + 0.10 * identifier
                + 0.06 * verified
                + 0.12 * intent
                + 0.06 * document
                - noise_penalty,
            )
        ranked = sorted(candidates, key=lambda item: item.reranker_score, reverse=True)
        if analysis.intent == "difference" and len(set(analysis.domains)) >= 2:
            return _balanced_difference_evidence(ranked, analysis.domains, final_count)
        return ranked[:final_count]


def _intent_relevance(analysis: QueryAnalysis, item: Evidence) -> float:
    if not analysis.intent:
        return 0.0
    text = item.text.lower()
    terms = INTENT_TERMS.get(analysis.intent, ())
    if not terms:
        return 0.0
    hits = sum(term in text for term in terms)
    return min(hits / 3, 1.0)


def _document_relevance(analysis: QueryAnalysis, item: Evidence) -> float:
    score = 0.0
    query = analysis.query.lower()
    title = item.title.lower()
    if item.domain in analysis.domains:
        score += 0.45
    elif analysis.domains:
        score -= 0.40
    if analysis.jurisdiction.value == "BOTH" or item.jurisdiction == analysis.jurisdiction.value:
        score += 0.25
    if analysis.intent in {"definition", "rights", "duration", "purpose", "difference"} and item.document_type in {"ACT", "TREATY", "AMENDMENT_ACT"}:
        score += 0.20
    if "act" in query and item.document_type in {"ACT", "AMENDMENT_ACT"}:
        score += 0.15
    if "rules" in query and item.document_type == "RULES":
        score += 0.15
    title_tokens = {token for token in re.findall(r"[a-z]{4,}", title) if token not in {"act", "rules", "india", "under"}}
    query_tokens = set(re.findall(r"[a-z]{4,}", query))
    if title_tokens and len(title_tokens & query_tokens) >= min(2, len(title_tokens)):
        score += 0.15
    if analysis.intent in {"registration", "opposition"} and item.document_type in {"ACT", "RULES"}:
        score += 0.10
    return min(score, 1.0)


def _noise_penalty(analysis: QueryAnalysis, item: Evidence) -> float:
    if analysis.intent not in {"definition", "rights", "duration", "purpose", "difference"}:
        return 0.0
    text = item.text.lower()
    if analysis.intent in {"registration", "rights"} and any(term in text for term in ("revocation", "cancellation")):
        return 0.14
    hits = sum(term in text for term in NOISY_FRAGMENT_TERMS)
    if hits and not any(term in text for term in INTENT_TERMS.get(analysis.intent or "", ())):
        return 0.12
    if hits >= 2:
        return 0.06
    return 0.0


def _balanced_difference_evidence(ranked: list[Evidence], domains: list[str], final_count: int) -> list[Evidence]:
    selected: list[Evidence] = []
    desired = list(dict.fromkeys(domains))
    for domain in desired:
        match = next((item for item in ranked if item.domain == domain and item not in selected), None)
        if match:
            selected.append(match)
    for item in ranked:
        if len(selected) >= final_count:
            break
        if item not in selected:
            selected.append(item)
    return selected[:final_count]
