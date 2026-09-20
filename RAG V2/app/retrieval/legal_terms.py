"""
Phase 3: Legal Term Extraction

Extract exact legal identifiers and terms from queries for precise lexical matching.
"""

import re
from dataclasses import dataclass
from typing import Set


@dataclass
class LegalTerms:
    """Extracted legal identifiers from text."""
    acts: Set[str]
    sections: Set[str]
    rules: Set[str]
    regulations: Set[str]
    articles: Set[str]
    schedules: Set[str]
    years: Set[str]
    organization_names: Set[str]
    document_titles: Set[str]


class LegalTermExtractor:
    """Extract exact legal terms for lexical retrieval."""

    # Indian legal acts
    ACTS = {
        "Patents Act 1970": r"Patents?\s*Act\s*(?:1970)?",
        "Trademarks Act 1999": r"(?:Trade\s*)?Marks?\s*Act\s*(?:1999)?",
        "Copyright Act 1957": r"Copyright\s*Act\s*(?:1957)?",
        "Designs Act 2000": r"Designs?\s*Act\s*(?:2000)?",
        "Geographical Indications Act 1999": r"(?:Geographical\s*Indications?|GI)\s*Act\s*(?:1999)?",
        "Biodiversity Act 2002": r"Biodiversity\s*Act\s*(?:2002)?",
        "Protection of Plant Varieties Act": r"(?:Protection\s*of\s*)?Plant\s*Varieties\s*(?:Farmers\s*)?(?:Rights\s*)?Act",
        "Indian Penal Code": r"Indian\s*Penal\s*Code|IPC",
        "Indian Evidence Act 1872": r"(?:Indian\s*)?Evidence\s*Act\s*(?:1872)?",
        "Code of Civil Procedure 1908": r"(?:Code\s*of\s*)?Civil\s*Procedure",
        "Code of Criminal Procedure 1973": r"(?:Code\s*of\s*)?Criminal\s*Procedure",
    }

    # FSSAI and regulatory
    REGULATIONS = {
        "FSSAI Regulations": r"FSSAI|Food\s*Safety.*Authority",
        "Ayurveda Aahara": r"Ayurveda\s*(?:Aahara|Dietary)",
        "Ayurveda Order 2025": r"Ayurveda\s*(?:Aahara\s*)?Order\s*2025?",
    }

    # WIPO and international
    INTERNATIONAL_REFS = {
        "Paris Convention": r"Paris\s*Convention",
        "PCT": r"Patent\s*Cooperation\s*Treaty|PCT",
        "Madrid Protocol": r"Madrid\s*(?:Protocol|Agreement)",
        "Budapest Treaty": r"Budapest\s*Treaty",
        "TRIPS": r"TRIPS|Agreement\s*on\s*TRIPS",
        "WIPO": r"WIPO|World\s*Intellectual\s*Property",
        "Nagoya Protocol": r"Nagoya\s*Protocol",
    }

    # Organizations
    ORGANIZATIONS = {
        "Ministry of AYUSH": r"Ministry\s*of\s*AYUSH|MoA",
        "FSSAI": r"Food\s*Safety\s*and\s*Standards\s*Authority|FSSAI",
        "NBA": r"National\s*Biodiversity\s*Authority|NBA",
        "WIPO": r"World\s*Intellectual\s*Property\s*Organization|WIPO",
        "Government of India": r"Government\s*of\s*India|GoI",
    }

    def __init__(self):
        """Initialize extractor."""
        pass

    def extract(self, text: str) -> LegalTerms:
        """Extract all legal terms from text."""
        return LegalTerms(
            acts=self._extract_acts(text),
            sections=self._extract_sections(text),
            rules=self._extract_rules(text),
            regulations=self._extract_regulations(text),
            articles=self._extract_articles(text),
            schedules=self._extract_schedules(text),
            years=self._extract_years(text),
            organization_names=self._extract_organizations(text),
            document_titles=self._extract_document_titles(text),
        )

    def _extract_acts(self, text: str) -> Set[str]:
        """Extract act names."""
        acts = set()
        for act_name, pattern in self.ACTS.items():
            if re.search(pattern, text, re.IGNORECASE):
                acts.add(act_name)
        return acts

    def _extract_sections(self, text: str) -> Set[str]:
        """Extract section numbers (e.g., 'Section 3(d)', 'Section 9(1)(a)')."""
        sections = set()
        # Match "Section 123", "Section 3(d)", "Section 9(1)(a)", "s.123"
        patterns = [
            r"[Ss]ection\s+(\d+(?:\s*\(\s*[a-zA-Z0-9]+\s*\))+)",
            r"[Ss]ection\s+(\d+)",
            r"[Ss]\.?\s*(\d+)",
        ]
        for pattern in patterns:
            matches = re.findall(pattern, text)
            sections.update(matches)
        return {f"Section {s}" for s in sections}

    def _extract_rules(self, text: str) -> Set[str]:
        """Extract rule numbers."""
        rules = set()
        patterns = [
            r"[Rr]ule\s+(\d+(?:\s*\(\s*[a-zA-Z0-9]+\s*\))?)",
        ]
        for pattern in patterns:
            matches = re.findall(pattern, text)
            rules.update(matches)
        return {f"Rule {r}" for r in rules}

    def _extract_regulations(self, text: str) -> Set[str]:
        """Extract regulation numbers."""
        regs = set()
        for reg_name, pattern in self.REGULATIONS.items():
            if re.search(pattern, text, re.IGNORECASE):
                regs.add(reg_name)

        # Also extract specific regulation numbers
        patterns = [
            r"[Rr]egulation\s+(\d+(?:\s*\(\s*[a-zA-Z0-9]+\s*\))?)",
        ]
        for pattern in patterns:
            matches = re.findall(pattern, text)
            regs.update(matches)

        return regs

    def _extract_articles(self, text: str) -> Set[str]:
        """Extract article numbers."""
        articles = set()
        patterns = [
            r"[Aa]rticle\s+(\d+(?:\s*\(\s*[a-zA-Z0-9]+\s*\))?)",
        ]
        for pattern in patterns:
            matches = re.findall(pattern, text)
            articles.update(matches)
        return {f"Article {a}" for a in articles}

    def _extract_schedules(self, text: str) -> Set[str]:
        """Extract schedule references."""
        schedules = set()
        patterns = [
            r"[Ss]chedule\s+([A-Z]|\d+)",
        ]
        for pattern in patterns:
            matches = re.findall(pattern, text)
            schedules.update(matches)
        return {f"Schedule {s}" for s in schedules}

    def _extract_years(self, text: str) -> Set[str]:
        """Extract years mentioned in legal context."""
        years = set()
        # Match years typically in legal context (1800s-2100s)
        matches = re.findall(r"\b(?:19|20)\d{2}\b", text)
        return set(matches)

    def _extract_organizations(self, text: str) -> Set[str]:
        """Extract organization names."""
        orgs = set()
        for org_name, pattern in self.ORGANIZATIONS.items():
            if re.search(pattern, text, re.IGNORECASE):
                orgs.add(org_name)
        return orgs

    def _extract_document_titles(self, text: str) -> Set[str]:
        """Extract document titles and names."""
        # Look for quoted titles or capitalized phrases
        titles = set()

        # Quoted titles
        quoted = re.findall(r'"([^"]{5,})"', text)
        titles.update(quoted)

        # Common legal documents
        doc_patterns = {
            "Patents Act": r"Patents?\s*Act",
            "Trademark Rules": r"Trade\s*Marks?\s*Rules",
            "Annual Report": r"Annual\s*Report",
        }

        for doc_name, pattern in doc_patterns.items():
            if re.search(pattern, text, re.IGNORECASE):
                titles.add(doc_name)

        return titles


# Global extractor
_extractor = None


def get_legal_term_extractor() -> LegalTermExtractor:
    """Get global extractor instance."""
    global _extractor
    if _extractor is None:
        _extractor = LegalTermExtractor()
    return _extractor


def extract_legal_terms(text: str) -> LegalTerms:
    """Extract legal terms from text."""
    return get_legal_term_extractor().extract(text)
