"""Phases 4-7 — V2 retrieval intelligence inside the V1 production shell.

Additive boosters only (never replace semantic retrieval):
- Phase 4: deterministic query-understanding lite (intent/domain/keywords, no LLM)
- Phase 5: legal-term extraction (Acts, sections, rules, articles, regulations, treaties, orgs)
- Phase 6: domain-aware soft prioritization (confidence-gated, graceful fallback)
- Phase 7: RRF-style fusion assist (used as additive signal; V1 weighted fusion stays authoritative)

Ported from RAG V2/app/retrieval/{query_understanding,legal_terms,domain_aware,hybrid_retrieval}.py
Adapted to V1 interfaces (plain functions + small dataclasses, no async, no new deps).
"""
from __future__ import annotations

import re
from dataclasses import dataclass, field

# ---- Phase 4: query understanding lite ----

_INTENT_PATTERNS: dict[str, list[str]] = {
    "definition": [r"\bwhat is\b", r"\bdefine\b", r"\bmeaning of\b", r"\bdefinition\b"],
    "duration": [r"\bhow long\b", r"\bduration\b", r"\bterm of\b", r"\bvalidity\b", r"\bexpiry\b"],
    "registration": [r"\bregister\b", r"\bregistration\b", r"\bfile\b", r"\bapplication\b", r"\bprocedure\b", r"\bhow to\b"],
    "rights": [r"\bright(s)?\b", r"\binfringement\b", r"\bexclusive\b", r"\bprotection\b"],
    "difference": [r"\bdifference\b", r"\bvs\b", r"\bversus\b", r"\bcompare\b"],
    "purpose": [r"\bpurpose\b", r"\bobjective\b", r"\bobject\b"],
}

_DOMAIN_KEYWORDS: dict[str, list[str]] = {
    "PATENT": ["patent", "patents", "invention", "prior art", "section 3"],
    "TRADEMARK": ["trademark", "trade mark", "trademark", "brand", "infringement of.*mark"],
    "COPYRIGHT": ["copyright", "literary work", "artistic work", "fair dealing"],
    "DESIGN": ["design", "industrial design"],
    "GI": ["geographical indication", r"\bgi\b", "gi tag"],
    "PLANT_VARIETY": ["plant variety", "ppvfr", "farmer", "seed", "breeder"],
    "ABS": ["biodiversity", "benefit sharing", "nba", "access and benefit"],
    "AYURVEDA": ["ayush", "ayurveda"],
    "FOOD": ["fssai", "ayurveda aahara", "food safety"],
    "INTERNATIONAL": ["trips", "pct", "madrid", "paris convention", "budapest", "gratk", "wipo", "treaty"],
}


@dataclass
class V2QuerySignals:
    intent_hint: str | None = None
    domain_hint: str | None = None
    domain_confidence: float = 0.0
    keywords: set[str] = field(default_factory=set)
    is_exact_reference: bool = False


def understand_query_lite(query: str) -> V2QuerySignals:
    q = query.lower()
    intent_hint: str | None = None
    for intent, patterns in _INTENT_PATTERNS.items():
        if any(re.search(p, q) for p in patterns):
            intent_hint = intent
            break
    best_domain: str | None = None
    best_hits = 0
    for domain, keywords in _DOMAIN_KEYWORDS.items():
        hits = 0
        for kw in keywords:
            try:
                if re.search(kw, q):
                    hits += 1
            except re.error:
                if kw.lower() in q:
                    hits += 1
        if hits > best_hits:
            best_hits = hits
            best_domain = domain
    confidence = min(0.9, 0.4 + 0.2 * best_hits) if best_domain else 0.0
    keywords = set(re.findall(r"[a-z]{4,}", q))
    is_exact = bool(re.search(r"(section|rule|regulation|article)\s+\d+", q, re.IGNORECASE))
    return V2QuerySignals(intent_hint, best_domain, confidence, keywords, is_exact)


# ---- Phase 5: legal term extraction ----

_ACT_PATTERNS = [
    "patents act", "trade marks act", "trademarks act", "copyright act", "designs act",
    "geographical indications act", "protection of plant varieties", "biological diversity act",
    "ayurveda aahara",
]
_TREATY_PATTERNS = ["paris convention", "pct", "madrid protocol", "budapest treaty", "trips", "gratk", "wipo"]
_SECTION_RE = re.compile(r"(section|rule|regulation|article)\s+(\d+[a-z]?(?:\([a-z0-9]+\))?)", re.IGNORECASE)


@dataclass
class V2LegalTerms:
    acts: set[str] = field(default_factory=set)
    references: set[str] = field(default_factory=set)  # e.g. "section 3(p)"
    treaties: set[str] = field(default_factory=set)


def extract_legal_terms_lite(query: str) -> V2LegalTerms:
    q = query.lower()
    acts = {a for a in _ACT_PATTERNS if a in q}
    treaties = {t for t in _TREATY_PATTERNS if t in q}
    references = {f"{m.group(1).lower()} {m.group(2).lower()}" for m in _SECTION_RE.finditer(query)}
    return V2LegalTerms(acts, references, treaties)


def legal_term_boost(chunk_text: str, title: str, terms: V2LegalTerms) -> float:
    """Additive boost for exact legal-term overlap (complements vector score)."""
    if not terms.acts and not terms.references and not terms.treaties:
        return 0.0
    hay = f"{title} {chunk_text}".lower()
    boost = 0.0
    for act in terms.acts:
        if act in hay:
            boost += 0.30
    for treaty in terms.treaties:
        if treaty in hay:
            boost += 0.30
    for ref in terms.references:
        if ref in hay:
            boost += 0.50
    return min(boost, 1.0)


# ---- Phase 6: domain-aware soft prioritization ----

def domain_priority_boost(chunk_domain: str, signals: V2QuerySignals) -> float:
    """Soft boost only. Confidence < 0.4 -> 0.0 (graceful fallback, never hard-filter)."""
    if not signals.domain_hint or signals.domain_confidence < 0.4:
        return 0.0
    if (chunk_domain or "").upper() == signals.domain_hint.upper():
        return 0.25 * signals.domain_confidence
    return 0.0


# ---- Phase 7: RRF assist ----

def rrf_score(rank: int, k: int = 60) -> float:
    return 1.0 / (k + rank + 1)
