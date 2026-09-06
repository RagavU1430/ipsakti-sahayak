from __future__ import annotations

import re

from app.models import Jurisdiction, QueryAnalysis, QueryRequest
from app.legal_aliases import document_hint_expansion, normalize_legal_query


DOMAIN_TERMS: dict[str, tuple[str, ...]] = {
    "PATENT": ("patent", "patented", "patentability", "invention", "inventive", "novelty", "specification", "invented", "inventor", "machine", "new device", "new process", "traditional knowledge", "tkdl", "traditional knowledge digital library", "indigenous knowledge", "community knowledge", "admixture", "known ingredients", "known plant extract", "simply mixing", "formulation", "composition", "herbal thing", "traditional remedy", "remedy"),
    "TRADEMARK": ("trademark", "trade mark", "brand", "mark registration", "logo", "company name", "company logo", "business name", "mark is registered", "distinctive label"),
    "GI": ("geographical indication", " gi ", "origin-linked", "regional product", "regional agricultural product", "traditional product", "geographical identity", "place of origin", "from my region"),
    "COPYRIGHT": ("copyright", "literary work", "artistic work", "song", "music", "lyrics", "creative work", "original work", " author "),
    "DESIGN": (" design ", "designed", "industrial design", "design registration", "design protection", "designs act", "product shape", "shape", "configuration", "pattern", "ornament", "visual design"),
    "PLANT_VARIETY": ("plant variety", "plant varieties", "farmer rights", "farmer", "ppvfr", "seed variety", "seed", "crop variety", "new variety", "breeder rights"),
    "ABS": ("biodiversity", "biological diversity", "biological resource", "biological resources", "benefit sharing", "benefit share", "nba", "access and benefit", " abs ", "associated traditional knowledge", "plant extracts", "genetic resource"),
    "FOOD": ("fssai", "food safety", "ayurveda aahara", "label", "food business", "nutraceutical", "dietary supplement", "health supplement", "nutritional claims"),
    "AYURVEDA": ("ayurveda", "ayush", "traditional medicine", "classical formulation", "traditional use", "churna", "guggulu", "taila", "kwath", "avaleha", "herbal product", "plant extracts", "herbal formulation", "medicinal plant"),
    "INTERNATIONAL": ("wipo", "trips", "treaty", "convention", "pct", "madrid", "budapest", "gratk", "genetic resources and associated traditional knowledge"),
    "IP": (" ip ", "i.p.", "ip rules", "ip law", "ip rights", "ip protection", "ip registration", "intellectual property", "trade secret"),
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
    r"\b(?P<kind>sections?|rules?|regulations?|articles?)\s+(?P<numbers>\d+[A-Za-z]?(?:\([A-Za-z0-9]+\))?(?:\.\d+)*(?:\s*(?:&|and|,)\s*\d+[A-Za-z]?(?:\([A-Za-z0-9]+\))?(?:\.\d+)*)*)",
    re.IGNORECASE,
)


def _extract_legal_identifiers(query: str) -> list[str]:
    identifiers: list[str] = []
    for match in LEGAL_IDENTIFIER_RE.finditer(query):
        kind = match.group("kind").rstrip("sS").capitalize()
        numbers = re.split(r"\s*(?:&|and|,)\s*", match.group("numbers"))
        for num in numbers:
            num = num.strip()
            if num:
                identifiers.append(f"{kind} {num}")
    return list(dict.fromkeys(identifiers))


def analyze_query(request: QueryRequest) -> QueryAnalysis:
    normalized_request_query = normalize_legal_query(request.query)
    clean_query = re.sub(r"[^\w\s]", " ", normalized_request_query)
    query = f" {clean_query.lower()} "
    domains = [domain for domain, terms in DOMAIN_TERMS.items() if any(term in query for term in terms)]
    domains = _refine_domains(query, domains)
    legal_identifiers = _extract_legal_identifiers(normalized_request_query)
    if any(term in query for term in ("admixture", "known ingredients", "known plant extract", "simply mixing", "mixing known")) and "Section 3(e)" not in legal_identifiers:
        legal_identifiers.append("Section 3(e)")
    if any(term in query for term in ("traditional knowledge", "indigenous knowledge", "community knowledge")) and any(term in query for term in ("patentability", "patentable", "not invention", "excluded", "exclusion")) and "Section 3(p)" not in legal_identifiers:
        legal_identifiers.append("Section 3(p)")
    if any(identifier.lower() in {"section 3(p)", "section 3(e)"} for identifier in legal_identifiers) and "PATENT" not in domains:
        domains.insert(0, "PATENT")
    if request.domain:
        requested = request.domain.value
        domains = [requested] + [domain for domain in domains if domain != requested]
    tkdl_query = "tkdl" in query or "traditional knowledge digital library" in query
    traditional_knowledge_query = "traditional knowledge" in query or "indigenous knowledge" in query or "community knowledge" in query
    if tkdl_query:
        domains.extend(["AYURVEDA", "PATENT", "ABS"])
    elif traditional_knowledge_query:
        domains.extend(["PATENT", "ABS"])
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
    words = set(re.findall(r"[a-z]+", query))
    vague_pronoun_question = len(words) <= 5 and bool(words & {"this", "it"}) and not any(
        term in query for term in ("herbal", "traditional", "biological", "logo", "song", "section")
    )
    # A query is only truly ambiguous if it is extremely short, uses vague pronouns,
    # or has no recognized domain AND no recognized IP-relevant regulatory terms.
    # Broad IP/legal queries that were explicitly routed to RAG by the backend should
    # NOT be classified as ambiguous.
    _has_ip_regulatory_signal = bool(domains) or _has_broad_ip_signal(query)
    ambiguous = len(request.query.split()) < 2 or vague_pronoun_question or (not _has_ip_regulatory_signal and not out_of_scope)
    retrieval_query = _retrieval_query(request.query, domains, intent)
    return QueryAnalysis(
        query=request.query,
        retrieval_query=retrieval_query,
        jurisdiction=jurisdiction,
        domains=domains,
        intent=intent,
        legal_identifiers=legal_identifiers,
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
    if "logo" in query:
        if "TRADEMARK" not in refined:
            refined.append("TRADEMARK")
        refined = ["TRADEMARK"] + [d for d in refined if d != "TRADEMARK"]
    if "logo" in query and any(term in query for term in ("shape", "appearance", "visual")) and "DESIGN" not in refined:
        refined.append("DESIGN")
    if "IP" in refined and len(refined) > 1:
        refined = [d for d in refined if d != "IP"] + ["IP"]
    return refined


_BROAD_IP_TERMS = (
    "ip ", " ip", "intellectual property", "patent", "trademark", "trade mark", "copyright",
    "traditional knowledge", "tkdl", "biodiversity", "geographical indication",
    "ayurveda", "ayush", "abs", "gratk", "section ", "act", "rule",
    "infringement", "registration", "ip rules", "ip law", "ip rights",
    "protection of", "design protection", "plant variety", "ppvfr", "nba",
    "wipo", "pct", "trade secret", "breeder", "prior art", "disclosure",
    "herbal formulation", "medicinal plant", "indigenous knowledge",
    "community knowledge", "genetic resource", "biological resource",
)


def _has_broad_ip_signal(query: str) -> bool:
    """Check if query contains broad IP/legal terminology that indicates it is
    a domain-relevant question even if no specific DOMAIN_TERMS key matched."""
    return any(term in query for term in _BROAD_IP_TERMS)


def _intent(query: str) -> str | None:
    for intent in ("difference", "duration", "opposition", "purpose", "rights", "registration", "definition", "infringement"):
        terms = INTENT_TERMS[intent]
        if any(term in query for term in terms):
            return intent
    return None


def _retrieval_query(query: str, domains: list[str], intent: str | None) -> str:
    normalized_query = normalize_legal_query(query)
    normalized = normalized_query.lower()
    expansions: list[str] = []
    hint_expansion = document_hint_expansion(normalized_query)
    if hint_expansion:
        expansions.append(hint_expansion)
    if "TRADEMARK" in domains and intent == "registration":
        expansions.append("trade mark registration application section 18 accepted advertised registrar")
    if "TRADEMARK" in domains and intent == "purpose":
        expansions.append("trade marks act objective protect trade marks registration better protection prevention fraudulent use")
    if "TRADEMARK" in domains and intent == "definition":
        expansions.append("trade mark means mark capable represented graphically distinguish goods services")
    if "TRADEMARK" in domains and intent in {"rights", "difference"}:
        expansions.append("registered trade mark exclusive right use proprietor infringement mark goods services")
    if "PATENT" in domains and any(term in normalized for term in ("patentability", "not invention", "excluded", "section 3")):
        expansions.append("section 3 what are not inventions patentability")
    if "PATENT" in domains and "traditional knowledge" in normalized and any(term in normalized for term in ("patentability", "patentable", "not invention", "excluded", "exclusion")):
        expansions.append("section 3(p) traditional knowledge aggregation duplication known properties traditionally known components")
    if "PATENT" in domains and any(term in normalized for term in ("admixture", "known ingredients", "known plant extract", "simply mixing", "mixing known")):
        expansions.append("section 3(e) mere admixture aggregation known substances properties")
    if "PATENT" in domains and intent == "definition" and "tkdl" in normalized:
        expansions.append("traditional knowledge digital library TKDL research database library AYUSH traditional knowledge")
        expansions.append("section 3(p) traditional knowledge aggregation duplication known properties traditionally known components")
    elif "PATENT" in domains and intent == "definition" and "traditional knowledge" in normalized:
        expansions.append("section 3(p) traditional knowledge aggregation duplication known properties traditionally known components")
        expansions.append("traditional knowledge associated biological resources codified traditional knowledge benefit claimers")
    elif "PATENT" in domains and intent == "definition":
        expansions.append("patent means patent for any invention granted under this act invention means new product process inventive step industrial application")
    if "PATENT" in domains and intent in {"rights", "duration", "registration", "difference"}:
        expansions.append("invention patent patentee exclusive right application term twenty years")
    if "COPYRIGHT" in domains and intent == "registration":
        expansions.append("copyright registration register of copyrights application")
    if "COPYRIGHT" in domains and intent == "purpose":
        expansions.append("copyright registration purpose evidence ownership authorship register of copyrights")
    if "COPYRIGHT" in domains and intent in {"definition", "rights", "duration", "infringement", "difference"}:
        expansions.append("copyright exclusive right author term infringement literary dramatic musical artistic work")
    if "DESIGN" in domains and intent == "registration":
        expansions.append("design registration application controller")
    if "DESIGN" in domains and intent in {"definition", "rights", "duration", "difference"}:
        expansions.append("design features shape configuration pattern ornament article registration copyright in design ten years")
    if "GI" in domains and intent == "registration":
        expansions.append("geographical indication registration application registrar")
    if "GI" in domains and intent == "definition":
        expansions.append("geographical indication means indication identifies goods originating territory country region locality quality reputation characteristic attributable geographical origin")
    if "GI" in domains and intent in {"rights", "difference"}:
        expansions.append("geographical indication registered proprietor authorised user infringement protection goods territory origin")
    if "GI" in domains and "regional agricultural product" in normalized:
        expansions.append("geographical indication agricultural goods regional origin registered proprietor authorised user")
    if "PLANT_VARIETY" in domains and intent in {"definition", "rights", "registration", "purpose", "difference"}:
        expansions.append("plant variety protection registration breeder farmer rights registered variety certificate exclusive right produce sell market distribute import export")
    if "ABS" in domains and intent in {"definition", "purpose", "rights"}:
        expansions.append("access benefit sharing fair equitable sharing benefits biological resources associated traditional knowledge")
    if "PATENT" in domains and "tkdl" in normalized:
        expansions.append("traditional knowledge digital library traditional knowledge section 3(p) prior art")
    if "INTERNATIONAL" in domains:
        expansions.append("treaty agreement intellectual property contracting parties protection")
    if "worldwide" in normalized and "patent" in normalized:
        expansions.append("territorial patent rights Paris Convention Patent Cooperation Treaty international filing no worldwide patent")
    if "IP" in domains:
        expansions.append("intellectual property patent trademark copyright design geographical indication plant variety trade secret rights protection registration")
    # For queries that matched no specific sub-domain but have broad IP signals,
    # add general IP expansion to improve retrieval
    if not domains and _has_broad_ip_signal(f" {normalized.strip()} "):
        expansions.append("intellectual property patent trademark copyright design geographical indication")
    return " ".join([normalized_query, *expansions])
