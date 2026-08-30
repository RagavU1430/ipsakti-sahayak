from __future__ import annotations

import re

from app.models import Jurisdiction, QueryAnalysis, QueryRequest


DOMAIN_TERMS: dict[str, tuple[str, ...]] = {
    "PATENT": ("patent", "invention", "inventive", "novelty", "specification", "invented", "inventor", "machine", "new device", "new process"),
    "TRADEMARK": ("trademark", "trade mark", "brand", "mark registration", "logo", "company name", "company logo", "business name"),
    "GI": ("geographical indication", " gi ", "origin-linked", "regional product", "traditional product", "geographical identity", "place of origin", "from my region"),
    "COPYRIGHT": ("copyright", "literary work", "artistic work", "song", "music", "lyrics", "creative work", "original work", "author"),
    "DESIGN": (" design ", "designed", "industrial design", "design registration", "design protection", "designs act", "product shape", "shape", "configuration", "pattern", "ornament", "visual design"),
    "PLANT_VARIETY": ("plant variety", "plant varieties", "farmer rights", "farmer", "ppvfr", "seed variety", "seed", "crop variety", "new variety"),
    "ABS": ("biodiversity", "biological diversity", "biological resource", "benefit sharing", "nba", "access and benefit", " abs "),
    "FOOD": ("fssai", "food safety", "ayurveda aahara", "label", "food business"),
    "AYURVEDA": ("ayurveda", "ayush", "traditional medicine"),
    "INTERNATIONAL": ("wipo", "trips", "treaty", "convention", "pct", "madrid", "budapest", "gratk"),
}
INTERNATIONAL_TERMS = set(DOMAIN_TERMS["INTERNATIONAL"])
OUT_OF_SCOPE_TERMS = (
    "weather",
    "bitcoin",
    "cricket match",
    "capital of france",
    "python program",
    "sort a list",
)
SPECULATIVE_SUBJECTS = ("teleportation", "mars", "time travel", "perpetual motion")
INTENT_TERMS: dict[str, tuple[str, ...]] = {
    "definition": ("what is", "define", "meaning", "under indian law"),
    "registration": ("register", "registration", "applying", "apply", "application", "filed", "filing"),
    "rights": ("rights", "provide", "protection", "protect", "ownership"),
    "duration": ("how long", "duration", "term", "valid", "last"),
    "opposition": ("opposed", "opposition"),
    "infringement": ("infringement", "infringe"),
    "purpose": ("purpose", "objectives", "objective"),
    "difference": ("difference", "differ", "compare"),
}
LEGAL_IDENTIFIER_RE = re.compile(
    r"\b(?:section|rule|regulation|article)\s+\d+[A-Za-z]?(?:\([A-Za-z0-9]+\))?(?:\.\d+)*",
    re.IGNORECASE,
)


def analyze_query(request: QueryRequest) -> QueryAnalysis:
    query = f" {request.query.lower()} "
    domains = [domain for domain, terms in DOMAIN_TERMS.items() if any(term in query for term in terms)]
    domains = _refine_domains(query, domains)
    if request.domain:
        requested = request.domain.value
        domains = [requested] + [domain for domain in domains if domain != requested]
    domains = list(dict.fromkeys(domains))
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
    out_of_scope = not domains and any(term in query for term in OUT_OF_SCOPE_TERMS)
    intent = _intent(query)
    speculative_subject = next((term for term in SPECULATIVE_SUBJECTS if term in query), None)
    ambiguous = len(request.query.split()) < 2 or (not domains and not out_of_scope)
    retrieval_query = _retrieval_query(request.query, domains, intent)
    return QueryAnalysis(
        query=request.query,
        retrieval_query=retrieval_query,
        jurisdiction=jurisdiction,
        domains=domains,
        intent=intent,
        legal_identifiers=[match.group(0) for match in LEGAL_IDENTIFIER_RE.finditer(request.query)],
        language=request.language,
        requested_top_k=request.top_k,
        out_of_scope=out_of_scope,
        speculative_subject=speculative_subject,
        ambiguous=ambiguous,
    )


def _refine_domains(query: str, domains: list[str]) -> list[str]:
    refined = list(domains)
    words = set(re.findall(r"[a-z]+", query))
    if "design" in words and "DESIGN" not in refined:
        refined.append("DESIGN")
    if "logo" in query and "TRADEMARK" not in refined:
        refined.append("TRADEMARK")
    if "logo" in query and any(term in query for term in ("shape", "appearance", "visual")) and "DESIGN" not in refined:
        refined.append("DESIGN")
    return refined


def _intent(query: str) -> str | None:
    for intent in ("duration", "opposition", "purpose", "difference", "rights", "registration", "definition", "infringement"):
        terms = INTENT_TERMS[intent]
        if any(term in query for term in terms):
            return intent
    return None


def _retrieval_query(query: str, domains: list[str], intent: str | None) -> str:
    normalized = query.lower()
    expansions: list[str] = []
    if "TRADEMARK" in domains and intent == "registration":
        expansions.append("trade mark registration application section 18 accepted advertised registrar")
    if "TRADEMARK" in domains and intent in {"rights", "definition", "difference"}:
        expansions.append("registered trade mark exclusive right use proprietor infringement mark goods services")
    if "PATENT" in domains and any(term in normalized for term in ("patentability", "not invention", "excluded", "section 3")):
        expansions.append("section 3 what are not inventions patentability")
    if "PATENT" in domains and intent in {"definition", "rights", "duration", "registration", "difference"}:
        expansions.append("invention patent patentee exclusive right application term twenty years")
    if "COPYRIGHT" in domains and intent == "registration":
        expansions.append("copyright registration register of copyrights application")
    if "COPYRIGHT" in domains and intent in {"definition", "rights", "duration", "infringement", "difference"}:
        expansions.append("copyright exclusive right author term infringement literary dramatic musical artistic work")
    if "DESIGN" in domains and intent == "registration":
        expansions.append("design registration application controller")
    if "DESIGN" in domains and intent in {"definition", "rights", "duration", "difference"}:
        expansions.append("design features shape configuration pattern ornament article registration copyright in design ten years")
    if "GI" in domains and intent == "registration":
        expansions.append("geographical indication registration application registrar")
    if "GI" in domains and intent in {"definition", "rights", "difference"}:
        expansions.append("geographical indication registered proprietor authorised user infringement protection goods territory origin")
    if "PLANT_VARIETY" in domains and intent in {"definition", "rights", "registration", "purpose", "difference"}:
        expansions.append("plant variety protection registration breeder farmer rights registered variety certificate exclusive right produce sell market distribute import export")
    if "ABS" in domains and intent in {"definition", "purpose", "rights"}:
        expansions.append("biological diversity conservation sustainable use fair equitable sharing benefits biological resources knowledge")
    if "INTERNATIONAL" in domains:
        expansions.append("treaty agreement intellectual property contracting parties protection")
    return " ".join([query, *expansions])
