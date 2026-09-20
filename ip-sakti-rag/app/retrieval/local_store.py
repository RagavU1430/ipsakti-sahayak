from __future__ import annotations

import json
import logging
import math
import re
from collections import Counter
from pathlib import Path
from typing import Any

from app.models import Jurisdiction, QueryAnalysis
from app.legal_aliases import document_hint_ids, document_hint_score, text_supports_identifier
from app.retrieval.v2_adapters import normalize_v2_chunk

logger = logging.getLogger(__name__)


TOKEN_RE = re.compile(r"[a-z0-9]+(?:\([a-z0-9]+\))?", re.IGNORECASE)
IDENTIFIER_RE = re.compile(r"(?P<kind>section|rule|regulation|article)\s+(?P<number>\d+[a-z]?(?:\([a-z0-9]+\))?(?:\.\d+)*)", re.IGNORECASE)


def tokens(text: str) -> list[str]:
    return TOKEN_RE.findall(text.lower())


class LocalCorpusStore:
    """Executable local fallback for tests/development; not a Supabase substitute in production."""

    def __init__(self, chunks_path: Path):
        raw = [json.loads(line) for line in chunks_path.read_text(encoding="utf-8").splitlines() if line.strip()]
        # Phase 2/3: V2 canonical chunks normalize into the V1 evidence interface.
        # normalize_v2_chunk is idempotent for V1 rows; required for V2 string-typed fields.
        from app.retrieval.v2_adapters import normalize_v2_chunks
        try:
            self.chunks = normalize_v2_chunks(raw)
        except Exception:
            logger.warning("v2_chunk_normalization_fallback_to_raw")
            self.chunks = raw
        corpus_tag = "v2-canonical" if "RAG V2" in str(chunks_path) or "chunks_v2" in str(chunks_path) else "v1-legacy"
        self.corpus_source = corpus_tag
        logger.info("local_corpus_loaded path=%s chunks=%d corpus=%s", str(chunks_path), len(self.chunks), corpus_tag)
        self.term_counts = [Counter(tokens(chunk["text"] + " " + chunk["title"])) for chunk in self.chunks]
        self.document_frequency: Counter[str] = Counter()
        for counts in self.term_counts:
            self.document_frequency.update(counts.keys())
        self.average_length = sum(sum(counts.values()) for counts in self.term_counts) / max(len(self.term_counts), 1)

        # Pre-compute IDF for all terms (cache for performance)
        self.total = len(self.chunks)
        self._idf_cache: dict[str, float] = {}
        self._init_idf_cache()
        # A document vector is immutable for the frozen corpus. The previous
        # implementation recomputed all 7,019 norms for every query.
        self.vector_norms = [self._vector_norm(counts) for counts in self.term_counts]

    def _init_idf_cache(self) -> None:
        """Pre-compute IDF values for all terms in the corpus."""
        for term in self.document_frequency:
            df = self.document_frequency.get(term, 0)
            self._idf_cache[term] = math.log(1 + (self.total - df + 0.5) / (df + 0.5))

    def _get_idf(self, term: str) -> float:
        """Get cached IDF value, computing if not yet cached."""
        if term not in self._idf_cache:
            df = self.document_frequency.get(term, 0)
            self._idf_cache[term] = math.log(1 + (self.total - df + 0.5) / (df + 0.5))
        return self._idf_cache[term]

    def _vector_norm(self, counts: Counter[str]) -> float:
        return math.sqrt(sum(
            (frequency * self._vector_idf(term)) ** 2
            for term, frequency in counts.items()
        )) or 1.0

    def _vector_idf(self, term: str) -> float:
        """Return the original TF-IDF weight used by vector search."""
        return math.log((self.total + 1) / (self.document_frequency.get(term, 0) + 1)) + 1

    def _eligible(self, chunk: dict[str, Any], analysis: QueryAnalysis) -> bool:
        jurisdiction_ok = analysis.jurisdiction == Jurisdiction.BOTH or chunk["jurisdiction"] == analysis.jurisdiction.value
        domain_ok = not analysis.domains or "IP" in analysis.domains or chunk["domain"] in analysis.domains or (
            analysis.jurisdiction == Jurisdiction.INTERNATIONAL and chunk["domain"] == "INTERNATIONAL"
        )
        return jurisdiction_ok and domain_ok

    def keyword_search(self, analysis: QueryAnalysis, count: int) -> list[dict[str, Any]]:
        query_terms = tokens(analysis.retrieval_query)
        results: list[dict[str, Any]] = []
        total = self.total
        for chunk, counts in zip(self.chunks, self.term_counts):
            if not self._eligible(chunk, analysis):
                continue
            length = sum(counts.values()) or 1
            score = 0.0
            for term in query_terms:
                frequency = counts.get(term, 0)
                if not frequency:
                    continue
                idf = self._get_idf(term)  # Use cached IDF
                score += idf * frequency * 2.2 / (frequency + 1.2 * (0.25 + 0.75 * length / self.average_length))
            if _identifier_match(chunk, analysis):
                score += 10.0
            score += 12.0 * document_hint_score(chunk.get("document_id", ""), analysis.hinted_documents)
            score += _title_intent_boost(chunk, analysis, lexical=True)
            if score:
                results.append({**chunk, "lexical_score": score})
        return _include_hinted_documents(results, self.chunks, analysis, "lexical_score", count)

    def vector_search(self, analysis: QueryAnalysis, count: int) -> list[dict[str, Any]]:
        # Hashed TF-IDF cosine provides a deterministic local vector signal. The
        # production path executes pgvector through SupabaseRAGStore.
        query_counts = Counter(tokens(analysis.retrieval_query))
        results: list[dict[str, Any]] = []
        total = self.total
        query_weights = {
            term: frequency * self._vector_idf(term)
            for term, frequency in query_counts.items()
        }
        query_norm = math.sqrt(sum(value * value for value in query_weights.values())) or 1.0
        for chunk, counts, document_norm in zip(self.chunks, self.term_counts, self.vector_norms):
            if not self._eligible(chunk, analysis):
                continue
            # Terms absent from the query contributed zero previously, so
            # iterating over the much shorter query is score-equivalent.
            dot = sum(
                counts.get(term, 0) * self._vector_idf(term) * query_weight
                for term, query_weight in query_weights.items()
            )
            score = dot / (document_norm * query_norm or 1.0)
            if _identifier_match(chunk, analysis):
                score += 0.75
            score += 0.85 * document_hint_score(chunk.get("document_id", ""), analysis.hinted_documents)
            score += _title_intent_boost(chunk, analysis, lexical=False)
            if score:
                results.append({**chunk, "vector_score": score})
        return _include_hinted_documents(results, self.chunks, analysis, "vector_score", count)


def _identifier_match(chunk: dict[str, Any], analysis: QueryAnalysis) -> bool:
    for identifier in analysis.legal_identifiers:
        match = IDENTIFIER_RE.search(identifier)
        if not match:
            continue
        kind, expected = match.group("kind").lower(), match.group("number").lower()
        base_num = re.sub(r"\(.*?\)", "", expected).strip()
        has_subclause = base_num != expected
        if kind == "section":
            actual = _combined(chunk.get("section"), chunk.get("subsection") or chunk.get("clause"))
            if actual and actual.lower() == expected:
                return True
            if not has_subclause and chunk.get("section") and chunk.get("section").lower() == base_num:
                return True
            if text_supports_identifier(identifier, chunk.get("text", "")):
                return True
        elif kind == "rule":
            actual = _combined(chunk.get("rule_number"), chunk.get("sub_rule") or chunk.get("clause"))
            if actual and actual.lower() == expected:
                return True
            if not has_subclause and chunk.get("rule_number") and chunk.get("rule_number").lower() == base_num:
                return True
            if text_supports_identifier(identifier, chunk.get("text", "")):
                return True
        elif kind == "regulation":
            actual = _combined(chunk.get("regulation_number"), chunk.get("subsection") or chunk.get("clause"))
            if actual and actual.lower() == expected:
                return True
            if not has_subclause and chunk.get("regulation_number") and chunk.get("regulation_number").lower() == base_num:
                return True
            if text_supports_identifier(identifier, chunk.get("text", "")):
                return True
        else:
            actual = chunk.get("article_number")
            if actual and (actual.lower() == expected or (not has_subclause and actual.lower() == base_num)):
                return True
            if text_supports_identifier(identifier, chunk.get("text", "")):
                return True
    return False


def _combined(parent: str | None, child: str | None) -> str | None:
    return f"{parent}({child})" if parent and child else parent


def _title_intent_boost(chunk: dict[str, Any], analysis: QueryAnalysis, *, lexical: bool) -> float:
    query = analysis.query.lower()
    title = chunk.get("title", "").lower()
    boost = 0.0
    if title:
        title_tokens = {token for token in tokens(title) if len(token) >= 4 and token not in {"india", "rules", "rule", "act"}}
        query_tokens = {token for token in tokens(query) if len(token) >= 4}
        overlap = len(title_tokens & query_tokens)
        if overlap >= 2:
            boost += 4.0 if lexical else 0.20
        if "act" in query and chunk.get("document_type") == "ACT":
            boost += 2.5 if lexical else 0.12
        if "rules" in query and chunk.get("document_type") == "RULES":
            boost += 2.5 if lexical else 0.12
    if analysis.intent in {"rights", "difference"} and chunk.get("document_type") == "ACT":
        boost += 2.0 if lexical else 0.10
    if analysis.intent == "purpose" and chunk.get("document_type") == "ACT":
        boost += 3.0 if lexical else 0.14
    return boost


def _include_hinted_documents(
    results: list[dict[str, Any]],
    chunks: list[dict[str, Any]],
    analysis: QueryAnalysis,
    score_field: str,
    count: int,
) -> list[dict[str, Any]]:
    """Guarantee an explicitly named source is available to the reranker.

    A single treaty or a coarse Act chunk can have weak lexical/vector overlap
    even when the user names it exactly. Dropping it before reranking makes the
    system answer from a more generic but wrong source.
    """
    hinted = set(analysis.hinted_documents)
    if not hinted:
        return sorted(results, key=lambda item: item[score_field], reverse=True)[:count]
    by_id = {item["chunk_id"]: item for item in results}
    for chunk in chunks:
        if chunk.get("document_id") not in hinted or not _eligible_chunk(chunk, analysis):
            continue
        if chunk["chunk_id"] not in by_id:
            row = {**chunk, score_field: 0.0}
            if score_field == "lexical_score":
                row[score_field] = 25.0
            else:
                row[score_field] = 1.0
            by_id[row["chunk_id"]] = row
    return sorted(by_id.values(), key=lambda item: item[score_field], reverse=True)[:count]


def _eligible_chunk(chunk: dict[str, Any], analysis: QueryAnalysis) -> bool:
    jurisdiction_ok = analysis.jurisdiction == Jurisdiction.BOTH or chunk["jurisdiction"] == analysis.jurisdiction.value
    domain_ok = not analysis.domains or "IP" in analysis.domains or chunk["domain"] in analysis.domains or (
        analysis.jurisdiction == Jurisdiction.INTERNATIONAL and chunk["domain"] == "INTERNATIONAL"
    )
    return jurisdiction_ok and domain_ok
