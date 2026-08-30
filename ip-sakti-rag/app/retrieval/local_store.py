from __future__ import annotations

import json
import math
import re
from collections import Counter
from pathlib import Path
from typing import Any

from app.models import Jurisdiction, QueryAnalysis


TOKEN_RE = re.compile(r"[a-z0-9]+(?:\([a-z0-9]+\))?", re.IGNORECASE)
IDENTIFIER_RE = re.compile(r"(?P<kind>section|rule|regulation|article)\s+(?P<number>\d+[a-z]?(?:\([a-z0-9]+\))?(?:\.\d+)*)", re.IGNORECASE)


def tokens(text: str) -> list[str]:
    return TOKEN_RE.findall(text.lower())


class LocalCorpusStore:
    """Executable local fallback for tests/development; not a Supabase substitute in production."""

    def __init__(self, chunks_path: Path):
        self.chunks = [json.loads(line) for line in chunks_path.read_text(encoding="utf-8").splitlines() if line.strip()]
        self.term_counts = [Counter(tokens(chunk["text"] + " " + chunk["title"])) for chunk in self.chunks]
        self.document_frequency: Counter[str] = Counter()
        for counts in self.term_counts:
            self.document_frequency.update(counts.keys())
        self.average_length = sum(sum(counts.values()) for counts in self.term_counts) / max(len(self.term_counts), 1)

    def _eligible(self, chunk: dict[str, Any], analysis: QueryAnalysis) -> bool:
        jurisdiction_ok = analysis.jurisdiction == Jurisdiction.BOTH or chunk["jurisdiction"] == analysis.jurisdiction.value
        domain_ok = not analysis.domains or chunk["domain"] in analysis.domains or (
            analysis.jurisdiction == Jurisdiction.INTERNATIONAL and chunk["domain"] == "INTERNATIONAL"
        )
        return jurisdiction_ok and domain_ok

    def keyword_search(self, analysis: QueryAnalysis, count: int) -> list[dict[str, Any]]:
        query_terms = tokens(analysis.retrieval_query)
        results: list[dict[str, Any]] = []
        total = len(self.chunks)
        for chunk, counts in zip(self.chunks, self.term_counts):
            if not self._eligible(chunk, analysis):
                continue
            length = sum(counts.values()) or 1
            score = 0.0
            for term in query_terms:
                frequency = counts.get(term, 0)
                if not frequency:
                    continue
                df = self.document_frequency.get(term, 0)
                idf = math.log(1 + (total - df + 0.5) / (df + 0.5))
                score += idf * frequency * 2.2 / (frequency + 1.2 * (0.25 + 0.75 * length / self.average_length))
            if _identifier_match(chunk, analysis):
                score += 10.0
            if score:
                results.append({**chunk, "lexical_score": score})
        return sorted(results, key=lambda item: item["lexical_score"], reverse=True)[:count]

    def vector_search(self, analysis: QueryAnalysis, count: int) -> list[dict[str, Any]]:
        # Hashed TF-IDF cosine provides a deterministic local vector signal. The
        # production path executes pgvector through SupabaseRAGStore.
        query_counts = Counter(tokens(analysis.retrieval_query))
        results: list[dict[str, Any]] = []
        total = len(self.chunks)
        query_weights = {
            term: frequency * (math.log((total + 1) / (self.document_frequency.get(term, 0) + 1)) + 1)
            for term, frequency in query_counts.items()
        }
        query_norm = math.sqrt(sum(value * value for value in query_weights.values())) or 1.0
        for chunk, counts in zip(self.chunks, self.term_counts):
            if not self._eligible(chunk, analysis):
                continue
            dot = 0.0
            norm = 0.0
            for term, frequency in counts.items():
                weight = frequency * (math.log((total + 1) / (self.document_frequency.get(term, 0) + 1)) + 1)
                norm += weight * weight
                dot += weight * query_weights.get(term, 0.0)
            score = dot / (math.sqrt(norm) * query_norm or 1.0)
            if _identifier_match(chunk, analysis):
                score += 0.75
            if score:
                results.append({**chunk, "vector_score": score})
        return sorted(results, key=lambda item: item["vector_score"], reverse=True)[:count]


def _identifier_match(chunk: dict[str, Any], analysis: QueryAnalysis) -> bool:
    for identifier in analysis.legal_identifiers:
        match = IDENTIFIER_RE.search(identifier)
        if not match:
            continue
        kind, expected = match.group("kind").lower(), match.group("number").lower()
        if kind == "section":
            actual = _combined(chunk.get("section"), chunk.get("subsection") or chunk.get("clause"))
        elif kind == "rule":
            actual = _combined(chunk.get("rule_number"), chunk.get("sub_rule") or chunk.get("clause"))
        elif kind == "regulation":
            actual = _combined(chunk.get("regulation_number"), chunk.get("subsection") or chunk.get("clause"))
        else:
            actual = chunk.get("article_number")
        if actual and actual.lower() == expected:
            return True
    return False


def _combined(parent: str | None, child: str | None) -> str | None:
    return f"{parent}({child})" if parent and child else parent
