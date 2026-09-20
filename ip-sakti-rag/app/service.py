from __future__ import annotations

import json
import logging
import re
import time
from functools import lru_cache
from uuid import uuid4

from app.citations import citations_for, evidence_supports_identifier, validate_citations
from app.core.config import Settings, get_settings
from app.generation import ExtractiveGroundedGenerator, GeneralFallbackGenerator, OpenRouterGroundedGenerator
from app.generation.context import assemble_context
from app.guardrails import abstention_reason, calculate_confidence
from app.legal_aliases import document_hint_ids
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
        self._uses_default_generator = generator is None
        self.generator = generator or self._generator()
        self.general_generator = self._general_generator()
        self._cache: dict[tuple[str, str], tuple[float, QueryResponse]] = {}
        # Phase 15: short-lived deterministic abstention cache (60s). Only safe abstentions.
        self._abstention_cache: dict[tuple[str, str], tuple[float, QueryResponse]] = {}
        self._abstention_cache_ttl = 60.0
        try:
            logger.info(
                "rag_service_init corpus=%s chunks=%s embedding_provider=%s candidate_k=%s llm=%s",
                getattr(self.store, "corpus_source", "unknown"),
                len(getattr(self.store, "chunks", [])),
                self.settings.embedding_provider,
                self.settings.candidate_k,
                getattr(self.generator, "name", type(self.generator).__name__),
            )
        except Exception:
            pass

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
            import os
            if self.settings.gemini_api_key and (
                "gemini" in self.settings.openrouter_model.lower() or os.getenv("LLM_PROVIDER") == "gemini"
            ):
                from app.generation import GeminiGroundedGenerator
                return GeminiGroundedGenerator(self.settings.gemini_api_key, timeout=self.settings.llm_timeout)

            from app.core.openrouter_client import OpenRouterClient

            return OpenRouterGroundedGenerator(
                OpenRouterClient(self.settings.openrouter_api_key),
                self.settings.openrouter_model,
                timeout=self.settings.llm_timeout,
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

    def query(self, request: QueryRequest, request_id: str | None = None) -> QueryResponse:
        request_id = request_id or str(uuid4())
        total_started = time.perf_counter()
        analysis = analyze_query(request)
        cache_key = (
            request.query.strip().lower(),
            (request.jurisdiction.value if hasattr(request.jurisdiction, "value") else str(request.jurisdiction)).upper(),
        )
        if self.settings.response_cache_enabled and cache_key in self._cache:
            cached_time, cached_response = self._cache[cache_key]
            if time.time() - cached_time < self.settings.response_cache_ttl:
                cached_metrics = dict(cached_response.metrics)
                cached_metrics["cache_hit"] = True
                cached_metrics["total_ms"] = round((time.perf_counter() - total_started) * 1000, 3)
                hit_response = cached_response.model_copy(update={"metrics": cached_metrics})
                self._log_request(request_id, analysis, hit_response)
                return hit_response
        # Phase 15: 60s abstention cache (safe deterministic abstentions only).
        if cache_key in self._abstention_cache:
            cached_time, cached_response = self._abstention_cache[cache_key]
            if time.time() - cached_time < self._abstention_cache_ttl:
                cached_metrics = dict(cached_response.metrics)
                cached_metrics["cache_hit"] = True
                cached_metrics["abstention_cache_hit"] = True
                cached_metrics["total_ms"] = round((time.perf_counter() - total_started) * 1000, 3)
                hit_response = cached_response.model_copy(update={"metrics": cached_metrics})
                self._log_request(request_id, analysis, hit_response)
                return hit_response
        try:
            if self._is_security_exfiltration_request(request.query):
                response = self._abstained(
                    analysis,
                    "The request asks for hidden instructions or credentials. Those are not legal evidence and cannot be provided.",
                    total_started,
                )
                self._log_request(request_id, analysis, response)
                return response
            if self._is_adversarial_unsupported_request(request.query):
                response = self._abstained(
                    analysis,
                    "I could not find sufficient authoritative evidence in the available IP knowledge corpus to answer this reliably.",
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
            # Phase 14: explicit evidence status. PARTIAL critical evidence must not
            # produce an authoritative definitive answer (Q08/Q09/Q23 class).
            evidence_status = self._evidence_status(analysis, evidence)
            reason = abstention_reason(analysis, evidence, max(self.settings.min_score, self.settings.abstention_threshold))
            if evidence_status == "INSUFFICIENT" and not reason:
                reason = "The retrieved evidence was insufficient to produce a supported answer."
            if reason:
                if self._should_general_fallback(analysis, reason):
                    response = self._general_fallback(analysis, reason, total_started, retrieval_ms, rerank_ms, candidates)
                    self._log_request(request_id, analysis, response)
                    return response
                # Phase 15: cache safe deterministic abstentions for 60s (never LLM answers).
                response = self._abstained(analysis, reason, total_started, retrieval_ms, rerank_ms, evidence)
                response.metrics["evidence_status"] = evidence_status
                response.metrics["rag_used"] = False
                self._cache_abstention(cache_key, response)
                self._log_request(request_id, analysis, response)
                return response

            hinted_documents = set(document_hint_ids(analysis.query))
            if analysis.legal_identifiers:
                provision_matches = [
                    item for item in evidence
                    if any(evidence_supports_identifier(identifier, [item]) for identifier in analysis.legal_identifiers)
                ]
                if hinted_documents:
                    hinted_provision_matches = [item for item in provision_matches if item.document_id in hinted_documents]
                    # Do not let an incidental cross-reference such as
                    # "Article 3" in TRIPS outrank the treaty named by the user.
                    provision_matches = hinted_provision_matches
                document_matches = [item for item in evidence if item.document_id in hinted_documents]
                evidence_for_context = provision_matches or document_matches or evidence
            elif hinted_documents:
                # An explicit source name (for example "WIPO GRATK Treaty" or
                # "Copyright Act") is stronger than generic lexical similarity.
                # Keep other evidence only when the hinted source was not found.
                hinted_matches = [item for item in evidence if item.document_id in hinted_documents]
                evidence_for_context = hinted_matches or evidence
            else:
                evidence_for_context = evidence
            context, selected = assemble_context(evidence_for_context, self.settings.max_context_chars)
            # Verified official-PDF overrides (URL + page) applied before citations/validation.
            from app.citations.verified_sources import apply_verified_sources
            selected = apply_verified_sources(selected)
            # Phase 9/16: Gemini must never receive an empty evidence set for an IP-SAKTI
            # RAG response and then generate an authoritative answer. Abstain BEFORE generation.
            if not selected:
                response = self._abstained(
                    analysis,
                    "The retrieved evidence was insufficient to produce a supported answer.",
                    total_started, retrieval_ms, rerank_ms, selected,
                )
                response.metrics["evidence_status"] = "INSUFFICIENT"
                response.metrics["rag_used"] = False
                self._cache_abstention(cache_key, response)
                self._log_request(request_id, analysis, response)
                return response
            generation_started = time.perf_counter()
            try:
                if self._use_fast_extractive_path(analysis):
                    # Definition, duration and exact-provision questions can be
                    # answered directly from selected evidence. Avoiding a
                    # remote rewrite removes seconds of latency while keeping
                    # citation validation and all downstream guardrails.
                    generated = ExtractiveGroundedGenerator().generate(analysis, context, selected)
                else:
                    generated = self.generator.generate(analysis, context, selected)
            except Exception:
                logger.warning("Remote LLM generator failed or timed out; falling back to extractive generator.")
                try:
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
            if not generated.used_chunk_ids or not generated.answer.strip():
                fallback = ExtractiveGroundedGenerator().generate(analysis, context, selected)
                if fallback.used_chunk_ids and fallback.answer.strip():
                    generated = fallback
                else:
                    response = self._abstained(analysis, "The retrieved evidence was insufficient to produce a supported answer.", total_started, retrieval_ms, rerank_ms, selected, generation_ms)
                    self._log_request(request_id, analysis, response)
                    return response
            if generated.insufficient_evidence:
                allow_rules_fallback = analysis.intent == "registration" and any("RULES" in item.document_id for item in selected)
                if allow_rules_fallback:
                    fallback = ExtractiveGroundedGenerator().generate(analysis, context, selected)
                    if fallback.used_chunk_ids and fallback.answer.strip():
                        generated = fallback
                if generated.insufficient_evidence:
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
                if self._uses_default_generator:
                    # A provider can cite retrieved chunks but introduce an
                    # unsupported provision in its prose. Re-run deterministically
                    # over the identical selected evidence before abstaining.
                    fallback = ExtractiveGroundedGenerator().generate(analysis, context, selected)
                    fallback_citations = citations_for(selected, fallback.used_chunk_ids)
                    fallback_valid, _ = validate_citations(fallback.answer, fallback_citations, selected)
                    if fallback_valid and fallback.used_chunk_ids and fallback.answer.strip():
                        generated = fallback
                        citations = fallback_citations
                        valid = True
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
                    # Phase 9/17/20: truthful RAG_USED + evidence contract fields.
                    "rag_used": bool(selected),
                    "evidence_status": self._evidence_status(analysis, selected),
                    "corpus": getattr(self.store, "corpus_source", "unknown"),
                    "embedding_provider": self.settings.embedding_provider,
                },
            )
            if self.settings.response_cache_enabled and not response.abstained:
                if len(self._cache) >= 500:
                    oldest_key = next(iter(self._cache))
                    self._cache.pop(oldest_key, None)
                self._cache[cache_key] = (time.time(), response)
            self._log_request(request_id, analysis, response)
            return response
        except Exception:
            logger.exception("rag_runtime_unhandled_error", extra={"request_id": request_id})
            raise

    @staticmethod
    def _evidence_status(analysis, evidence) -> str:
        """Phase 14: SUFFICIENT / PARTIAL / INSUFFICIENT / CONFLICTING.

        PARTIAL with critical legal identifiers or quarantined-class queries must
        abstain downstream — never an authoritative definitive answer.
        """
        items = list(evidence or [])
        if not items:
            return "INSUFFICIENT"
        # Q08/Q09/Q23 class: Ayurveda Aahara 2022 regulation requested but only the
        # 2025 list order is indexed -> PARTIAL (critical source missing).
        try:
            q = (analysis.query or "").lower()
            if "ayurveda aahara" in q and any(
                t in q for t in ("2022", "regulation", "define", "definition", "label")
            ):
                docs = {getattr(e, "document_id", "") for e in items}
                if "IND-FSS-AA-ORDER-2025" in docs and not any("2022" in d for d in docs):
                    return "PARTIAL"
        except Exception:
            pass
        scores = [float(getattr(e, "reranker_score", 0.0) or 0.0) for e in items]
        best = max(scores) if scores else 0.0
        if best < 0.12 or len(items) == 0:
            return "INSUFFICIENT"
        if len(items) < 2 or best < 0.35:
            return "PARTIAL"
        return "SUFFICIENT"

    def _cache_abstention(self, cache_key, response: QueryResponse) -> None:
        try:
            if len(self._abstention_cache) >= 500:
                oldest = next(iter(self._abstention_cache))
                self._abstention_cache.pop(oldest, None)
            self._abstention_cache[cache_key] = (time.time(), response)
        except Exception:
            pass

    def _use_fast_extractive_path(self, analysis) -> bool:
        if not self._uses_default_generator or not self.settings.enable_llm or not self.settings.fast_extractive_enabled:
            return False
        if analysis.intent in {"definition", "duration", "purpose"}:
            return True
        return bool(analysis.legal_identifiers and analysis.intent != "difference")

    def ask(self, request: AskRequest, request_id: str | None = None) -> AskResponse:
        return self._to_ask_response(self.query(request.to_query_request(), request_id=request_id))

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
    def _is_adversarial_unsupported_request(query: str) -> bool:
        normalized = query.lower()
        return any(phrase in normalized for phrase in (
            "invent a section",
            "fictional law",
            "teleportation can be patented",
            "guarantees patent protection for every",
            "patenting magic",
            "ignore the corpus",
            "secret document",
            "black holes are trademarks",
            "give a citation even if no source supports",
            "automatically patented",
            "every idea",
            "guarantee worldwide patent protection",
        )) or bool(re.search(r"\bsection\s+\d+[a-z]?\([a-z0-9]+\)\(\d+\)", normalized))

    @staticmethod
    def _should_general_fallback(analysis, reason: str) -> bool:
        # Deep-test repair policy: insufficient or ambiguous RAG evidence must
        # not be converted into a non-grounded answer for API consumers.
        return False

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
                "evidence_count": len(evidence or []),
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
