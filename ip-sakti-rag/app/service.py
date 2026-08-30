from __future__ import annotations

import json
import logging
import time
from functools import lru_cache
from uuid import uuid4

from app.citations import citations_for, evidence_supports_identifier, validate_citations
from app.core.config import Settings, get_settings
from app.generation import ExtractiveGroundedGenerator, GeneralFallbackGenerator, OpenRouterGroundedGenerator
from app.generation.context import assemble_context
from app.guardrails import abstention_reason, calculate_confidence
from app.models import (
    AskCitation,
    AskRequest,
    AskResponse,
    AskSource,
    Citation,
    Confidence,
    Evidence,
    QueryRequest,
    QueryResponse,
)
from app.retrieval import HybridRetriever, LegalFeatureReranker, analyze_query
from app.retrieval.local_store import LocalCorpusStore


logger = logging.getLogger(__name__)


class RAGService:
    def __init__(self, settings: Settings | None = None, store=None, generator=None):
        self.settings = settings or get_settings()
        self.store = store or self._store()
        self.retriever = HybridRetriever(self.store, self.settings.candidate_k)
        self.reranker = LegalFeatureReranker()
        self.generator = generator or self._generator()
        self.general_generator = self._general_generator()

    def _store(self):
        use_supabase = self.settings.storage_backend == "supabase" or (
            self.settings.storage_backend == "auto"
            and self.settings.supabase_url
            and self.settings.supabase_anon_key
            and self.settings.openrouter_api_key
        )
        if use_supabase:
            from app.core.db import SupabaseRAGStore
            from app.retrieval.embeddings import OpenRouterEmbeddingProvider
            from app.retrieval.supabase_store import SupabaseCorpusStore

            database = SupabaseRAGStore(self.settings)
            embeddings = OpenRouterEmbeddingProvider(
                api_key=self.settings.openrouter_api_key,
                model=self.settings.embedding_model,
                dimension=self.settings.embedding_dimension,
            )
            return SupabaseCorpusStore(database, embeddings)
        if self.settings.storage_backend not in {"auto", "local"}:
            raise ValueError(f"unsupported RAG_STORAGE_BACKEND={self.settings.storage_backend}")
        return LocalCorpusStore(self.settings.canonical_chunks_path)

    def _generator(self):
        if self.settings.enable_llm:
            from app.core.openrouter_client import OpenRouterClient

            return OpenRouterGroundedGenerator(
                OpenRouterClient(self.settings.openrouter_api_key),
                self.settings.openrouter_model,
            )
        return ExtractiveGroundedGenerator()

    def _general_generator(self):
        if self.settings.enable_general_llm and self.settings.openrouter_api_key:
            from app.core.openrouter_client import OpenRouterClient

            return GeneralFallbackGenerator(
                OpenRouterClient(self.settings.openrouter_api_key),
                self.settings.openrouter_model,
            )
        return GeneralFallbackGenerator()

    def query(self, request: QueryRequest) -> QueryResponse:
        request_id = str(uuid4())
        total_started = time.perf_counter()
        analysis = analyze_query(request)
        try:
            if self._is_security_exfiltration_request(request.query):
                response = self._abstained(
                    analysis,
                    "The request asks for hidden instructions or credentials. Those are not legal evidence and cannot be provided.",
                    total_started,
                )
                self._log_request(request_id, analysis, response)
                return response
            if self._requires_quarantined_ayurveda_aahara_source(request.query):
                response = self._abstained(
                    analysis,
                    "The authoritative 2022 Ayurveda Aahara regulation source is quarantined because its local file is not a valid PDF; this question cannot be answered safely from the verified 2025 list order alone.",
                    total_started,
                )
                self._log_request(request_id, analysis, response)
                return response

            retrieval_started = time.perf_counter()
            candidates = self.retriever.retrieve(analysis)
            retrieval_ms = (time.perf_counter() - retrieval_started) * 1000

            rerank_started = time.perf_counter()
            evidence = self.reranker.rerank(analysis, candidates, request.top_k or self.settings.top_k)
            rerank_ms = (time.perf_counter() - rerank_started) * 1000
            reason = abstention_reason(analysis, evidence, max(self.settings.min_score, self.settings.abstention_threshold))
            if reason:
                if self._should_general_fallback(analysis, reason):
                    response = self._general_fallback(analysis, reason, total_started, retrieval_ms, rerank_ms, candidates)
                    self._log_request(request_id, analysis, response)
                    return response
                response = self._abstained(analysis, reason, total_started, retrieval_ms, rerank_ms, evidence)
                self._log_request(request_id, analysis, response)
                return response

            if analysis.legal_identifiers:
                exact = [
                    item for item in evidence
                    if all(evidence_supports_identifier(identifier, [item]) for identifier in analysis.legal_identifiers)
                ]
                evidence_for_context = exact
            else:
                evidence_for_context = evidence
            context, selected = assemble_context(evidence_for_context, self.settings.max_context_chars)
            generation_started = time.perf_counter()
            try:
                generated = self.generator.generate(analysis, context, selected)
            except Exception:
                logger.warning("Remote LLM generator failed or timed out; falling back to extractive generator.")
                try:
                    from app.generation import ExtractiveGroundedGenerator
                    generated = ExtractiveGroundedGenerator().generate(analysis, context, selected)
                except Exception:
                    logger.exception("rag_generation_failed")
                    response = self._abstained(
                        analysis,
                        "The grounded generation provider failed, so no legal answer was produced.",
                        total_started,
                        retrieval_ms,
                        rerank_ms,
                        selected,
                    )
                    self._log_request(request_id, analysis, response)
                    return response
            generation_ms = (time.perf_counter() - generation_started) * 1000
            if generated.insufficient_evidence or not generated.used_chunk_ids:
                response = self._abstained(
                    analysis,
                    "The retrieved evidence was insufficient to produce a supported answer.",
                    total_started,
                    retrieval_ms,
                    rerank_ms,
                    selected,
                    generation_ms,
                )
                self._log_request(request_id, analysis, response)
                return response

            citations = citations_for(selected, generated.used_chunk_ids)
            valid, citation_errors = validate_citations(generated.answer, citations, selected)
            if not valid:
                response = self._abstained(
                    analysis,
                    "Citation validation rejected the generated answer; no unsupported legal statement was returned.",
                    total_started,
                    retrieval_ms,
                    rerank_ms,
                    selected,
                    generation_ms,
                    citation_errors,
                )
                self._log_request(request_id, analysis, response)
                return response
            confidence, score = calculate_confidence(selected, len(citations), True, False, analysis)
            limitations = []
            if any(item.source_status != "VERIFIED" for item in selected):
                limitations.append("One or more raw source files were unavailable for independent page verification; confidence is capped.")
            if not self.reranker.learned:
                limitations.append("A deterministic legal-feature reranker is active; no learned reranker was configured.")
            response = QueryResponse(
                answer=generated.answer,
                confidence=confidence,
                abstained=False,
                jurisdiction=analysis.jurisdiction,
                domain=analysis.domains[0] if analysis.domains else None,
                citations=citations,
                evidence=selected,
                limitations=limitations,
                metrics={
                    "retrieval_ms": round(retrieval_ms, 3),
                    "reranking_ms": round(rerank_ms, 3),
                    "generation_ms": round(generation_ms, 3),
                    "total_ms": round((time.perf_counter() - total_started) * 1000, 3),
                    "confidence_score": round(score, 4),
                    "candidate_count": len(candidates),
                    "evidence_count": len(selected),
                    "reranker": self.reranker.name,
                    "reranker_learned": self.reranker.learned,
                    "generator": generated.provider,
                },
            )
            self._log_request(request_id, analysis, response)
            return response
        except Exception:
            logger.exception("rag_runtime_unhandled_error", extra={"request_id": request_id})
            raise

    def ask(self, request: AskRequest) -> AskResponse:
        return self._to_ask_response(self.query(request.to_query_request()))

    @staticmethod
    def _requires_quarantined_ayurveda_aahara_source(query: str) -> bool:
        normalized = query.lower()
        if "ayurveda aahara" not in normalized:
            return False
        if "2025" in normalized and any(term in normalized for term in ("order", "list", "covered")):
            return False
        return any(term in normalized for term in ("2022", "regulation", "define", "definition", "label"))

    @staticmethod
    def _is_security_exfiltration_request(query: str) -> bool:
        normalized = query.lower()
        return any(phrase in normalized for phrase in (
            "reveal your system prompt",
            "ignore all instructions",
            "database password",
            "hidden service keys",
            "list service keys",
            "reveal credentials",
        ))

    @staticmethod
    def _should_general_fallback(analysis, reason: str) -> bool:
        if analysis.legal_identifiers:
            return False
        normalized = reason.lower()
        return (
            analysis.out_of_scope
            or analysis.speculative_subject is not None
            or analysis.ambiguous
            or "sufficient authoritative evidence" in normalized
            or "sufficiently relevant supporting evidence" in normalized
        )

    def _general_fallback(self, analysis, reason, started, retrieval_ms=0.0, rerank_ms=0.0, candidates=None):
        try:
            generated = self.general_generator.generate(analysis, reason)
            answer = generated.answer
            provider = generated.provider
        except Exception:
            logger.exception("rag_general_fallback_failed")
            answer = (
                "I checked the verified IP-SAKTI corpus first, but it did not contain sufficiently relevant evidence. "
                "The general-answer provider was unavailable, so I cannot safely provide a non-corpus answer."
            )
            provider = "general-fallback-failed"
        return QueryResponse(
            answer=answer,
            confidence=Confidence.LOW,
            abstained=False,
            jurisdiction=analysis.jurisdiction,
            domain=analysis.domains[0] if analysis.domains else None,
            citations=[],
            evidence=[],
            limitations=[
                "No relevant RAG evidence was sufficient for a grounded answer.",
                "This response is not grounded in the verified IP-SAKTI corpus and has no citations.",
            ],
            metrics={
                "retrieval_ms": round(retrieval_ms, 3),
                "reranking_ms": round(rerank_ms, 3),
                "generation_ms": 0.0,
                "total_ms": round((time.perf_counter() - started) * 1000, 3),
                "confidence_score": 0.35,
                "candidate_count": len(candidates or []),
                "evidence_count": 0,
                "answer_mode": "general_fallback",
                "generator": provider,
            },
        )

    @staticmethod
    def _abstained(analysis, reason, started, retrieval_ms=0.0, rerank_ms=0.0, evidence=None, generation_ms=0.0, limitations=None):
        return QueryResponse(
            answer=reason,
            confidence=Confidence.INSUFFICIENT_EVIDENCE,
            abstained=True,
            jurisdiction=analysis.jurisdiction,
            domain=analysis.domains[0] if analysis.domains else None,
            citations=[],
            evidence=evidence or [],
            limitations=limitations or [],
            metrics={
                "retrieval_ms": round(retrieval_ms, 3),
                "reranking_ms": round(rerank_ms, 3),
                "generation_ms": round(generation_ms, 3),
                "total_ms": round((time.perf_counter() - started) * 1000, 3),
                "confidence_score": 0.18,
                "candidate_count": len(evidence or []),
                "evidence_count": 0,
            },
        )

    @staticmethod
    def _to_ask_response(response: QueryResponse) -> AskResponse:
        if response.abstained:
            return AskResponse(
                answer=response.answer,
                confidence=0.18,
                abstained=True,
                citations=[],
                sources=[],
            )
        return AskResponse(
            answer=response.answer,
            confidence=round(float(response.metrics.get("confidence_score", 0.0)), 4),
            abstained=False,
            citations=[_public_citation(citation) for citation in response.citations],
            sources=_public_sources(response.evidence),
        )

    @staticmethod
    def _log_request(request_id: str, analysis, response: QueryResponse) -> None:
        logger.info(json.dumps({
            "event": "rag_request",
            "request_id": request_id,
            "query": analysis.query,
            "jurisdiction": analysis.jurisdiction.value,
            "domains": analysis.domains,
            "candidate_count": response.metrics.get("candidate_count", 0),
            "final_evidence_count": response.metrics.get("evidence_count", len(response.evidence)),
            "abstention": response.abstained,
            "confidence": response.metrics.get("confidence_score", 0.0),
            "retrieval_ms": response.metrics.get("retrieval_ms", 0.0),
            "reranking_ms": response.metrics.get("reranking_ms", 0.0),
            "generation_ms": response.metrics.get("generation_ms", 0.0),
            "total_ms": response.metrics.get("total_ms", 0.0),
        }))


def _public_citation(citation: Citation) -> AskCitation:
    section = (
        f"Section {citation.section}" if citation.section else
        f"Rule {citation.rule_number}" if citation.rule_number else
        f"Regulation {citation.regulation_number}" if citation.regulation_number else
        f"Article {citation.article_number}" if citation.article_number else
        None
    )
    return AskCitation(
        document=citation.title,
        document_id=citation.document_id,
        page=citation.page_start,
        section=section,
        authority=citation.authority,
        source_url=citation.source_url,
        chunk_id=citation.chunk_id,
    )


def _public_source(item: Evidence) -> AskSource:
    return AskSource(document_id=item.document_id, score=round(float(item.reranker_score), 4))


def _public_sources(evidence: list[Evidence]) -> list[AskSource]:
    sources: list[AskSource] = []
    seen: set[str] = set()
    for item in evidence:
        if item.document_id in seen:
            continue
        seen.add(item.document_id)
        sources.append(_public_source(item))
    return sources


@lru_cache(maxsize=1)
def get_service() -> RAGService:
    return RAGService()
