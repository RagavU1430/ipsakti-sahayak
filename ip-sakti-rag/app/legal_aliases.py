from __future__ import annotations

import re


TYPO_REPLACEMENTS: tuple[tuple[re.Pattern[str], str], ...] = (
    (re.compile(r"\btrademrk\b", re.IGNORECASE), "trademark"),
    (re.compile(r"\btradmark\b", re.IGNORECASE), "trademark"),
    (re.compile(r"\btm\b", re.IGNORECASE), "trademark"),
    (re.compile(r"\bregistation\b", re.IGNORECASE), "registration"),
    (re.compile(r"\bpatant\b", re.IGNORECASE), "patent"),
    (re.compile(r"\btraditonal\b", re.IGNORECASE), "traditional"),
    (re.compile(r"\bknowlege\b", re.IGNORECASE), "knowledge"),
    (re.compile(r"\bknwledge\b", re.IGNORECASE), "knowledge"),
    (re.compile(r"\bresorce\b", re.IGNORECASE), "resource"),
    (re.compile(r"\bresorces\b", re.IGNORECASE), "resources"),
    (re.compile(r"\bwat\b", re.IGNORECASE), "what"),
    (re.compile(r"\b3p\b", re.IGNORECASE), "Section 3(p)"),
    (re.compile(r"\b3e\b", re.IGNORECASE), "Section 3(e)"),
)


DOCUMENT_ALIASES: dict[str, tuple[str, ...]] = {
    "IND-PAT-ACT-1970": (
        "patents act",
        "patent act",
        "indian patent law",
        "patent law",
        "patentability",
        "traditional knowledge patent",
        "known mixtures patent",
        "admixture patent",
        "herbal product",
        "therapeutic use",
        "novel composition",
    ),
    "IND-TM-ACT-1999": (
        "trade marks act",
        "trademarks act",
        "trademark act",
        "trade mark act",
        "registered trade mark",
        "registered trademark",
        "trademark infringement",
        "trademark registration",
        "mark is registered",
        "rights does trademark registration",
        "trademark opposition and trademark infringement",
    ),
    "IND-TM-RULES-2017": (
        "trade marks rules",
        "trademark rules",
        "trademark opposition",
        "notice of opposition",
        "tm registration",
        "trademark opposition and trademark infringement",
    ),
    "IND-CR-ACT-1957": (
        "copyright act",
        "copyright law",
        "meaning of copyright",
        "copyright rights",
        "copyright infringement",
        "copyright registration",
        "song copyright",
    ),
    "IND-BD-ACT-2002": (
        "biological diversity act",
        "biodiversity act",
        "biological resources",
        "biological resource",
        "access and benefit sharing",
        "benefit sharing",
        "nba approval",
        "biodiversity approval",
        "plant extracts",
        "community traditional knowledge",
    ),
    "IND-BD-RULES-2024": (
        "biological diversity rules",
        "biodiversity rules",
        "benefit sharing rules",
        "fair sharing of benefits",
    ),
    "IND-BD-AMEND-2023": (
        "biological diversity amendment act",
        "biodiversity amendment",
    ),
    "INT-WIPO-GRATK-2024": (
        "gratk",
        "wipo gratk",
        "gratk treaty",
        "wipo treaty on intellectual property genetic resources",
        "genetic resources and associated traditional knowledge",
        "associated traditional knowledge",
        "traditional knowledge treaty",
        "community traditional knowledge",
    ),
    "IND-DES-ACT-2000": (
        "designs act",
        "design act",
        "design registration",
        "registered design",
        "package shape",
        "product shape",
        "external use cosmetic",
    ),
    "IND-GI-ACT-1999": (
        "geographical indications act",
        "geographical indication act",
        "gi act",
        "gi protection",
        "gi registration",
        "gi tag",
        "regional agricultural product",
        "regional traditional food name",
    ),
    "IND-GI-RULES-2002": (
        "geographical indications rules",
        "gi rules",
    ),
    "IND-PPV-ACT-2001": (
        "plant varieties act",
        "plant variety protection",
        "farmers rights",
        "farmer rights",
        "ppvfr",
    ),
    "INT-WIPO-PCT": ("patent cooperation treaty", "pct", "international patent filing"),
    "INT-WIPO-PARIS": ("paris convention",),
    "INT-WIPO-MADRID": ("madrid protocol",),
    "INT-WIPO-BUDAPEST": ("budapest treaty",),
    "INT-TRIPS-1994": ("trips", "trips agreement", "wto trips"),
    "IND-FSS-AA-ORDER-2025": ("ayurveda aahara", "ayurveda-based food", "nutritional claims"),
    "IND-AYUSH-INDIA-2024": ("ayush in india", "ayurveda-based food", "nutritional claims"),
    "IND-AYUSH-AR-2024-25": ("ministry of ayush annual report", "therapeutic use", "herbal product"),
}


def normalize_legal_query(query: str) -> str:
    normalized = query
    for pattern, replacement in TYPO_REPLACEMENTS:
        normalized = pattern.sub(replacement, normalized)
    return " ".join(normalized.split())


def document_hint_ids(query: str) -> list[str]:
    normalized = normalize_legal_query(query).lower()
    hints: list[str] = []
    for document_id, aliases in DOCUMENT_ALIASES.items():
        if any(alias in normalized for alias in aliases):
            hints.append(document_id)
    if "section 18" in normalized or "section 28" in normalized:
        hints.append("IND-TM-ACT-1999")
    if "section 14" in normalized or "section 51" in normalized or "section 13" in normalized:
        hints.append("IND-CR-ACT-1957")
    if "section 3" in normalized and any(term in normalized for term in ("patent", "invention", "traditional knowledge", "known mixture", "admixture")):
        hints.append("IND-PAT-ACT-1970")
    if "section 3" in normalized and "biological diversity" in normalized:
        hints.append("IND-BD-ACT-2002")
    if "section 6" in normalized and "biological diversity" in normalized:
        hints.append("IND-BD-ACT-2002")
    if "article" in normalized and "gratk" in normalized:
        hints.append("INT-WIPO-GRATK-2024")
    if "herbal product" in normalized or "plant extracts" in normalized:
        hints.extend(["IND-PAT-ACT-1970", "IND-BD-ACT-2002", "IND-AYUSH-AR-2024-25"])
    if "community traditional knowledge" in normalized:
        hints.extend(["IND-BD-ACT-2002", "IND-PAT-ACT-1970", "INT-WIPO-GRATK-2024"])
    return list(dict.fromkeys(hints))


def document_hint_score(document_id: str, query: str) -> float:
    normalized = normalize_legal_query(query).lower()
    if document_id == "IND-PAT-ACT-1970" and any(term in normalized for term in ("simply mixing", "known plant extract", "known ingredients", "admixture")):
        return 1.6
    return 1.0 if document_id in document_hint_ids(query) else 0.0


def document_hint_expansion(query: str) -> str:
    hints = document_hint_ids(query)
    phrases: list[str] = []
    for document_id in hints:
        phrases.extend(DOCUMENT_ALIASES.get(document_id, ())[:4])
    return " ".join(dict.fromkeys(phrases))


def text_supports_identifier(identifier: str, text: str) -> bool:
    match = re.search(
        r"\b(?P<kind>section|rule|regulation|article)\s+(?P<number>\d+[a-z]?(?:\([a-z0-9]+\))?(?:\.\d+)*)",
        identifier,
        re.IGNORECASE,
    )
    if not match:
        return True
    number = match.group("number")
    base = re.sub(r"\(.*?\)", "", number)
    candidates = {
        rf"\b{re.escape(match.group('kind'))}\s+{re.escape(number)}\b",
        rf"(?<!\d){re.escape(number)}\.\s",
    }
    if base != number:
        clause = number[len(base):].strip("()")
        candidates.add(rf"(?<!\d){re.escape(base)}\.\s.*?\({re.escape(clause)}\)")
    return any(re.search(pattern, text, re.IGNORECASE | re.DOTALL) for pattern in candidates)
