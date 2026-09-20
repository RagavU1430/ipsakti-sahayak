"""
Phase 2: Query Understanding

Lightweight query analysis to identify language, intent, domain, entities, and terms.
Prefer deterministic extraction over LLM where possible.
"""

import re
from enum import Enum
from dataclasses import dataclass
from typing import Optional, Set


class QueryIntent(str, Enum):
    """Intent categories for IP-SAKTI queries."""
    PATENT = "patent"
    TRADEMARK = "trademark"
    COPYRIGHT = "copyright"
    DESIGN = "design"
    GI = "gi"  # Geographical Indication
    BIODIVERSITY = "biodiversity"
    ABS = "abs"  # Access and Benefit Sharing
    TRADITIONAL_KNOWLEDGE = "traditional_knowledge"
    AYURVEDA_REGULATION = "ayurveda_regulation"
    FOOD_REGULATION = "food_regulation"
    INTERNATIONAL_IP = "international_ip"
    GENERAL_INFORMATION = "general_information"


class QueryDomain(str, Enum):
    """Document domains in corpus."""
    IP = "IP"
    AYURVEDA = "AYURVEDA"
    TRADITIONAL_KNOWLEDGE = "TRADITIONAL_KNOWLEDGE"
    BIODIVERSITY_ABS = "BIODIVERSITY_ABS"
    REGULATORY = "REGULATORY"
    INTERNATIONAL = "INTERNATIONAL"
    GENERAL = "GENERAL"


@dataclass
class QueryUnderstanding:
    """Result of query analysis."""

    original_query: str
    language: str = "en"
    intent: QueryIntent = QueryIntent.GENERAL_INFORMATION
    primary_domain: QueryDomain = QueryDomain.GENERAL
    secondary_domains: list[QueryDomain] = None

    # Extracted entities
    legal_references: Set[str] = None
    regulation_references: Set[str] = None
    organization_names: Set[str] = None
    entities: Set[str] = None
    exact_terms: Set[str] = None
    dates: Set[str] = None
    keywords: Set[str] = None

    # Confidence scores (0.0-1.0)
    intent_confidence: float = 0.5
    domain_confidence: float = 0.5

    # Features
    is_exact_match: bool = False
    is_long_query: bool = False
    is_ambiguous: bool = False
    contains_acronyms: bool = False

    def __post_init__(self):
        if self.secondary_domains is None:
            self.secondary_domains = []
        if self.legal_references is None:
            self.legal_references = set()
        if self.regulation_references is None:
            self.regulation_references = set()
        if self.organization_names is None:
            self.organization_names = set()
        if self.entities is None:
            self.entities = set()
        if self.exact_terms is None:
            self.exact_terms = set()
        if self.dates is None:
            self.dates = set()
        if self.keywords is None:
            self.keywords = set()


class QueryAnalyzer:
    """Deterministic query understanding."""

    # Legal/regulatory terminology patterns
    INTENT_KEYWORDS = {
        QueryIntent.PATENT: {
            "patent", "invention", "novelty", "inventive step", "specification",
            "claims", "prosecution", "filing", "prior art", "infringement",
            "design patent", "utility patent"
        },
        QueryIntent.TRADEMARK: {
            "trademark", "brand", "mark", "registration", "infringement",
            "dilution", "passing off", "goodwill", "distinctiveness",
            "similarity", "confusion"
        },
        QueryIntent.COPYRIGHT: {
            "copyright", "author", "literary", "artistic", "dramatic",
            "musical", "infringement", "reproduction", "derivative",
            "fair use", "licensing", "work"
        },
        QueryIntent.DESIGN: {
            "design", "ornamental", "article", "shape", "configuration",
            "appearance", "novelty", "distinctiveness", "registered design"
        },
        QueryIntent.GI: {
            "geographical indication", "GI", "geographical origin",
            "reputation", "quality", "darjeeling", "basmati", "champagne",
            "paridera", "indication of source"
        },
        QueryIntent.BIODIVERSITY: {
            "biodiversity", "species", "ecosystem", "conservation",
            "endangered", "habitat", "genetic resource", "biological"
        },
        QueryIntent.ABS: {
            "access and benefit sharing", "ABS", "benefit sharing",
            "genetic resources", "traditional knowledge", "nagoya protocol"
        },
        QueryIntent.TRADITIONAL_KNOWLEDGE: {
            "traditional knowledge", "TK", "indigenous", "folk",
            "customary", "community", "traditional innovation", "TKDL"
        },
        QueryIntent.AYURVEDA_REGULATION: {
            "ayurveda", "AYUSH", "FSSAI", "aahara", "dietary supplement",
            "herbal", "vaidya", "classical formulation"
        },
        QueryIntent.FOOD_REGULATION: {
            "food safety", "FSSAI", "food standards", "labeling",
            "additives", "contaminants", "food business operator"
        },
        QueryIntent.INTERNATIONAL_IP: {
            "WIPO", "international", "treaty", "paris convention",
            "PCT", "TRIPS", "MADRID", "BUDAPEST", "agreement"
        },
    }

    DOMAIN_KEYWORDS = {
        QueryDomain.IP: {"patent", "trademark", "copyright", "design", "IP", "intellectual property"},
        QueryDomain.AYURVEDA: {"ayurveda", "ayush", "herbal", "vaidya", "formulation"},
        QueryDomain.TRADITIONAL_KNOWLEDGE: {"traditional", "indigenous", "folk", "customary", "community"},
        QueryDomain.BIODIVERSITY_ABS: {"biodiversity", "genetic", "species", "ecosystem", "ABS"},
        QueryDomain.REGULATORY: {"regulation", "rule", "standard", "compliance", "enforcement", "FSSAI"},
        QueryDomain.INTERNATIONAL: {"international", "WIPO", "treaty", "agreement", "convention"},
    }

    # Exact legal references (section, act, rule patterns)
    ACT_PATTERNS = {
        "Patents Act": r"Patents?Act\s*(?:1970)?",
        "Trademarks Act": r"(?:Trade\s*)?Marks?\s*Act\s*(?:1999)?",
        "Copyright Act": r"Copyright\s*Act\s*(?:1957)?",
        "Designs Act": r"Designs?\s*Act\s*(?:2000)?",
        "GI Act": r"(?:Geographical\s*Indications?|GI)\s*Act\s*(?:1999)?",
        "Biodiversity Act": r"Biodiversity\s*Act\s*(?:2002)?",
        "FSSAI": r"FSSAI|Food\s*Safety.*Authority",
    }

    REFERENCE_PATTERNS = {
        "section": r"[Ss]ection\s*(\d+(?:\([a-z]\))?(?:\([i]{1,3}\))?)",
        "rule": r"[Rr]ule\s*(\d+(?:\([a-z]\))?)",
        "regulation": r"[Rr]egulation\s*(\d+(?:\([a-z]\))?)",
        "article": r"[Aa]rticle\s*(\d+(?:\([a-z]\))?)",
        "schedule": r"[Ss]chedule\s*([A-Z]?)",
    }

    DATE_PATTERN = r"\b(?:19|20)\d{2}\b|\b\d{1,2}[/-]\d{1,2}[/-](?:19|20)\d{2}\b"

    def __init__(self):
        """Initialize patterns."""
        pass

    def analyze(self, query: str) -> QueryUnderstanding:
        """Analyze query and return understanding."""

        understanding = QueryUnderstanding(
            original_query=query,
            language=self._detect_language(query),
        )

        # Extract terms and entities
        understanding.keywords = self._extract_keywords(query)
        understanding.legal_references = self._extract_legal_references(query)
        understanding.regulation_references = self._extract_regulations(query)
        understanding.dates = self._extract_dates(query)
        understanding.entities = self._extract_entities(query)
        understanding.exact_terms = self._extract_exact_terms(query)

        # Detect intent
        understanding.intent, understanding.intent_confidence = self._detect_intent(query, understanding)

        # Detect domain
        understanding.primary_domain, understanding.domain_confidence = self._detect_domain(query, understanding)

        # Detect secondary domains (cross-domain queries)
        understanding.secondary_domains = self._detect_secondary_domains(query, understanding.primary_domain)

        # Query features
        understanding.is_exact_match = self._is_exact_match(query)
        understanding.is_long_query = len(query) > 150
        understanding.is_ambiguous = understanding.intent_confidence < 0.5
        understanding.contains_acronyms = self._has_acronyms(query)

        return understanding

    def _detect_language(self, query: str) -> str:
        """Detect query language (simple heuristic)."""
        # For now, default to English
        # Could be extended for Devanagari, Tamil, etc.
        return "en"

    def _extract_keywords(self, query: str) -> Set[str]:
        """Extract keywords (lowercased, stopwords removed)."""
        words = query.lower().split()
        # Simple filtering (can be enhanced with stopwords list)
        return {w.strip('.,;:!?') for w in words if len(w) > 3}

    def _extract_legal_references(self, query: str) -> Set[str]:
        """Extract act names and legal references."""
        refs = set()
        for act_name, pattern in self.ACT_PATTERNS.items():
            if re.search(pattern, query, re.IGNORECASE):
                refs.add(act_name)
        return refs

    def _extract_regulations(self, query: str) -> Set[str]:
        """Extract regulation/section/rule numbers."""
        refs = set()
        for ref_type, pattern in self.REFERENCE_PATTERNS.items():
            matches = re.findall(pattern, query, re.IGNORECASE)
            for match in matches:
                refs.add(f"{ref_type}_{match}")
        return refs

    def _extract_dates(self, query: str) -> Set[str]:
        """Extract dates from query."""
        return set(re.findall(self.DATE_PATTERN, query))

    def _extract_entities(self, query: str) -> Set[str]:
        """Extract organization/entity names (capitalized words)."""
        # Simple heuristic: capitalized words
        entities = set()
        for word in query.split():
            word = word.strip('.,;:!?()')
            if word and word[0].isupper() and len(word) > 2:
                entities.add(word)
        return entities

    def _extract_exact_terms(self, query: str) -> Set[str]:
        """Extract quoted terms or exact phrases."""
        # Extract content between quotes
        quoted = re.findall(r'"([^"]+)"', query)
        return set(quoted)

    def _detect_intent(self, query: str, understanding: QueryUnderstanding) -> tuple[QueryIntent, float]:
        """Detect intent from keywords."""
        query_lower = query.lower()
        scores = {}

        for intent, keywords in self.INTENT_KEYWORDS.items():
            matches = sum(1 for kw in keywords if kw in query_lower)
            scores[intent] = matches

        if not scores or max(scores.values()) == 0:
            return QueryIntent.GENERAL_INFORMATION, 0.3

        best_intent = max(scores, key=scores.get)
        confidence = min(scores[best_intent] / len(self.INTENT_KEYWORDS[best_intent]), 1.0)

        return best_intent, confidence

    def _detect_domain(self, query: str, understanding: QueryUnderstanding) -> tuple[QueryDomain, float]:
        """Detect primary domain."""
        query_lower = query.lower()
        scores = {}

        for domain, keywords in self.DOMAIN_KEYWORDS.items():
            matches = sum(1 for kw in keywords if kw in query_lower)
            scores[domain] = matches

        if not scores or max(scores.values()) == 0:
            return QueryDomain.GENERAL, 0.3

        best_domain = max(scores, key=scores.get)
        confidence = min(scores[best_domain] / len(self.DOMAIN_KEYWORDS[best_domain]), 1.0)

        return best_domain, confidence

    def _detect_secondary_domains(self, query: str, primary: QueryDomain) -> list[QueryDomain]:
        """Detect secondary domains (for cross-domain queries)."""
        query_lower = query.lower()
        secondary = []

        for domain, keywords in self.DOMAIN_KEYWORDS.items():
            if domain != primary and any(kw in query_lower for kw in keywords):
                secondary.append(domain)

        return secondary[:2]  # Limit to 2 secondary domains

    def _is_exact_match(self, query: str) -> bool:
        """Check if query is looking for exact term."""
        return bool(re.search(r'["\']|exact|specific|section\s*\d', query, re.IGNORECASE))

    def _has_acronyms(self, query: str) -> bool:
        """Check for acronyms (consecutive capitals)."""
        return bool(re.search(r'\b[A-Z]{2,}\b', query))


# Global analyzer instance
_analyzer = None


def get_query_analyzer() -> QueryAnalyzer:
    """Get global query analyzer instance."""
    global _analyzer
    if _analyzer is None:
        _analyzer = QueryAnalyzer()
    return _analyzer


def analyze_query(query: str) -> QueryUnderstanding:
    """Convenience function to analyze a query."""
    return get_query_analyzer().analyze(query)
