from __future__ import annotations

import re

from app.models import Jurisdiction, QueryAnalysis, QueryRequest


DOMAIN_TERMS: dict[str, tuple[str, ...]] = {
    "PATENT": ("patent", "invention", "inventive", "novelty", "specification"),
    "TRADEMARK": ("trademark", "trade mark", "brand", "mark registration"),
    "GI": ("geographical indication", " gi ", "origin-linked"),
    "COPYRIGHT": ("copyright", "literary work", "artistic work"),
    "DESIGN": ("industrial design", "design registration", "designs act"),
    "PLANT_VARIETY": ("plant variety", "farmer rights", "farmer", "ppvfr", "seed variety", "seed"),
    "ABS": ("biodiversity", "biological diversity", "biological resource", "benefit sharing", "nba", "access and benefit", " abs "),
    "FOOD": ("fssai", "food safety", "ayurveda aahara", "label", "food business"),
    "AYURVEDA": ("ayurveda", "ayush", "traditional medicine"),
    "INTERNATIONAL": ("wipo", "trips", "treaty", "convention", "pct", "madrid", "budapest", "gratk"),
}
INTERNATIONAL_TERMS = set(DOMAIN_TERMS["INTERNATIONAL"])
LEGAL_IDENTIFIER_RE = re.compile(
    r"\b(?:section|rule|regulation|article)\s+\d+[A-Za-z]?(?:\([A-Za-z0-9]+\))?(?:\.\d+)*",
    re.IGNORECASE,
)


def analyze_query(request: QueryRequest) -> QueryAnalysis:
    query = f" {request.query.lower()} "
    domains = [domain for domain, terms in DOMAIN_TERMS.items() if any(term in query for term in terms)]
    if request.domain:
        requested = request.domain.value
        domains = [requested] + [domain for domain in domains if domain != requested]
    if not domains:
        domains = ["PATENT"] if "patent" in query else []
    explicit_both = any(term in query for term in ("compare", "both", "india and", "indian and international"))
    international = any(term in query for term in INTERNATIONAL_TERMS)
    india = "india" in query or any(domain != "INTERNATIONAL" for domain in domains)
    if request.jurisdiction:
        jurisdiction = request.jurisdiction
    elif explicit_both or (international and india):
        jurisdiction = Jurisdiction.BOTH
    elif international:
        jurisdiction = Jurisdiction.INTERNATIONAL
    else:
        jurisdiction = Jurisdiction.INDIA
    ambiguous = len(request.query.split()) < 2 or not domains
    retrieval_query = _retrieval_query(request.query, domains)
    return QueryAnalysis(
        query=request.query,
        retrieval_query=retrieval_query,
        jurisdiction=jurisdiction,
        domains=domains,
        legal_identifiers=[match.group(0) for match in LEGAL_IDENTIFIER_RE.finditer(request.query)],
        language=request.language,
        requested_top_k=request.top_k,
        ambiguous=ambiguous,
    )


def _retrieval_query(query: str, domains: list[str]) -> str:
    normalized = query.lower()
    expansions: list[str] = []
    if "TRADEMARK" in domains and any(term in normalized for term in ("register", "registration", "requirements", "apply", "application")):
        expansions.append("trade mark registration application section 18 accepted advertised registrar")
    if "PATENT" in domains and any(term in normalized for term in ("patentability", "not invention", "excluded", "section 3")):
        expansions.append("section 3 what are not inventions patentability")
    if "COPYRIGHT" in domains and "registration" in normalized:
        expansions.append("copyright registration register of copyrights application")
    if "DESIGN" in domains and any(term in normalized for term in ("register", "registration", "application")):
        expansions.append("design registration application controller")
    if "GI" in domains and any(term in normalized for term in ("register", "registration", "application")):
        expansions.append("geographical indication registration application registrar")
    return " ".join([query, *expansions])
