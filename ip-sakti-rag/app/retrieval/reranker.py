from __future__ import annotations

import re
from typing import Protocol

from app.citations import evidence_supports_identifier
from app.models import Evidence, QueryAnalysis
from app.legal_aliases import document_hint_ids, document_hint_score, text_supports_identifier


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
            identifier = 1.0 if any(identifier.lower() in text or evidence_supports_identifier(identifier, [item]) or text_supports_identifier(identifier, item.text) for identifier in analysis.legal_identifiers) else 0.0
            verified = 1.0 if item.source_status == "VERIFIED" else 0.0
            intent = _intent_relevance(analysis, item)
            definition = _definition_relevance(analysis, item)
            topical = _topical_relevance(analysis, item)
            document = _document_relevance(analysis, item)
            document_hint = document_hint_score(item.document_id, analysis.query)
            noise_penalty = _noise_penalty(analysis, item)
            item.reranker_score = max(
                0.0,
                0.50 * item.fusion_score
                + 0.16 * coverage
                + 0.10 * identifier
                + 0.06 * verified
                + 0.12 * intent
                + 0.18 * definition
                + 0.20 * topical
                + 0.06 * document
                + 0.18 * document_hint
                - noise_penalty,
            )
        ranked = sorted(candidates, key=lambda item: item.reranker_score, reverse=True)
        if analysis.intent == "difference" and len(set(analysis.domains)) >= 2:
            return _balanced_difference_evidence(ranked, analysis.domains, final_count, document_hint_ids(analysis.query))
        if analysis.intent == "difference":
            return _balanced_document_evidence(ranked, document_hint_ids(analysis.query), final_count)
        hinted_documents = document_hint_ids(analysis.query)
        if len(hinted_documents) >= 2:
            return _balanced_document_evidence(ranked, hinted_documents, final_count)
        ranked = _prioritize_exact_tk_evidence(analysis, ranked)
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


def _definition_relevance(analysis: QueryAnalysis, item: Evidence) -> float:
    if analysis.intent != "definition":
        return 0.0
    text = item.text.lower()
    query = analysis.query.lower()
    targets: list[str] = []
    if "tkdl" in query or "traditional knowledge digital library" in query:
        targets.extend(["tkdl", "traditional knowledge digital library"])
    elif "traditional knowledge" in query or "indigenous knowledge" in query or "community knowledge" in query:
        targets.extend(["traditional knowledge", "codified traditional knowledge", "associated knowledge"])
    elif "PATENT" in analysis.domains:
        targets.extend(["patent", "invention"])
    if "GI" in analysis.domains or "geographical indication" in query:
        targets.extend(["geographical indication"])
    if "TRADEMARK" in analysis.domains:
        targets.extend(["trade mark", "trademark"])
    if "ABS" in analysis.domains:
        targets.extend(["benefit sharing", "access", "biological resources"])
    if "tkdl" in query:
        targets.extend(["traditional knowledge digital library", "traditional knowledge"])
    if not targets:
        return 0.0
    has_target = any(target in text for target in targets)
    has_definition_language = any(term in text for term in (" means ", " means a ", " means an ", " defined", " identifies ", " is "))
    has_context_language = any(term in text for term in ("associated thereto", "section 3", "digital library", "research database"))
    if has_target and has_definition_language:
        return 1.0
    if has_target and has_context_language:
        return 0.85
    if has_target:
        return 0.35
    return 0.0


def _topical_relevance(analysis: QueryAnalysis, item: Evidence) -> float:
    text = item.text.lower()
    query = analysis.query.lower()
    if "tkdl" in query or "traditional knowledge digital library" in query:
        if "tkdl" in text and "traditional knowledge digital library" in text:
            return 1.0
        if "tkdl" in text or "traditional knowledge digital library" in text:
            return 0.85
    if "traditional knowledge" in query or "indigenous knowledge" in query or "community knowledge" in query:
        if item.document_id == "IND-PAT-ACT-1970" and item.section == "3" and item.clause == "p":
            return 1.0
        if "traditional knowledge" in text and "patentability" in query and item.document_id == "IND-PAT-ACT-1970":
            return 0.95
        if "traditional knowledge" in text:
            return 0.75
    return 0.0


def _prioritize_exact_tk_evidence(analysis: QueryAnalysis, ranked: list[Evidence]) -> list[Evidence]:
    query = analysis.query.lower()
    if "tkdl" in query or "traditional knowledge digital library" in query:
        exact = [
            item for item in ranked
            if "tkdl" in item.text.lower() or "traditional knowledge digital library" in item.text.lower()
        ]
        if exact:
            return exact + [item for item in ranked if item not in exact]
    if "traditional knowledge" in query and ("PATENT" in analysis.domains or "patent" in query or "patentability" in query):
        exact = [
            item for item in ranked
            if item.document_id == "IND-PAT-ACT-1970" and item.section == "3" and item.clause == "p"
        ]
        if exact:
            return exact + [item for item in ranked if item not in exact]
    return ranked


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


def _balanced_difference_evidence(
    ranked: list[Evidence],
    domains: list[str],
    final_count: int,
    hinted_documents: list[str] | None = None,
) -> list[Evidence]:
    selected: list[Evidence] = []
    # For comparisons, named source families are more precise than domains:
    # e.g. Act + Rules, or Biological Diversity Act + GRATK.
    for document_id in hinted_documents or []:
        match = next((item for item in ranked if item.document_id == document_id and item not in selected), None)
        if match:
            selected.append(match)
        if len(selected) >= final_count:
            return selected[:final_count]
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


def _balanced_document_evidence(ranked: list[Evidence], document_ids: list[str], final_count: int) -> list[Evidence]:
    if not document_ids:
        return ranked[:final_count]
    selected: list[Evidence] = []
    for document_id in document_ids:
        match = next((item for item in ranked if item.document_id == document_id and item not in selected), None)
        if match:
            selected.append(match)
    for item in ranked:
        if len(selected) >= final_count:
            break
        if item not in selected:
            selected.append(item)
    return selected[:final_count]
