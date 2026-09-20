"""
Phase 0: Vector Retrieval Baseline Measurement

Measure vector-only retrieval on representative evaluation queries.
Establishes baseline for comparison with lexical, hybrid, and reranked results.
"""

import json
import asyncio
import logging
from typing import Optional
from dataclasses import dataclass, asdict

logger = logging.getLogger(__name__)


@dataclass
class EvaluationQuery:
    """Single evaluation query with expected evidence."""
    qid: str
    query: str
    domain: str  # IP, AYURVEDA, TRADEMARK, COPYRIGHT, DESIGN, GI, BIODIVERSITY, ABS, TK, REGULATORY, INTERNATIONAL, GENERAL
    intent: str
    expected_document_ids: list[str] = None
    expected_chunk_ids: list[str] = None

    def __post_init__(self):
        if self.expected_document_ids is None:
            self.expected_document_ids = []
        if self.expected_chunk_ids is None:
            self.expected_chunk_ids = []


# Representative evaluation set covering all domains
EVALUATION_QUERIES = [
    # Patents (IP domain)
    EvaluationQuery(
        qid="Q001",
        query="What are the requirements for patenting an invention in India?",
        domain="IP",
        intent="PATENT"
    ),
    EvaluationQuery(
        qid="Q002",
        query="Section 3(d) of Patents Act - exclusions from patentability",
        domain="IP",
        intent="PATENT"
    ),

    # Trademarks (IP domain)
    EvaluationQuery(
        qid="Q003",
        query="How to register a trademark in India?",
        domain="IP",
        intent="TRADEMARK"
    ),
    EvaluationQuery(
        qid="Q004",
        query="Trademark infringement and remedies",
        domain="IP",
        intent="TRADEMARK"
    ),

    # Copyright (IP domain)
    EvaluationQuery(
        qid="Q005",
        query="Copyright protection for literary works",
        domain="IP",
        intent="COPYRIGHT"
    ),
    EvaluationQuery(
        qid="Q006",
        query="Fair use exceptions under Copyright Act",
        domain="IP",
        intent="COPYRIGHT"
    ),

    # Designs (IP domain)
    EvaluationQuery(
        qid="Q007",
        query="Design registration requirements",
        domain="IP",
        intent="DESIGN"
    ),

    # Geographical Indications (IP domain)
    EvaluationQuery(
        qid="Q008",
        query="Geographical indication protection in India",
        domain="IP",
        intent="GI"
    ),

    # Plant Variety Protection (IP domain)
    EvaluationQuery(
        qid="Q009",
        query="Plant variety protection rights",
        domain="IP",
        intent="GENERAL_INFORMATION"
    ),

    # Ayurveda Regulation (REGULATORY domain)
    EvaluationQuery(
        qid="Q010",
        query="FSSAI Ayurveda Aahara regulations",
        domain="REGULATORY",
        intent="AYURVEDA_REGULATION"
    ),
    EvaluationQuery(
        qid="Q011",
        query="Ayurveda aahara order 2025 compliance",
        domain="REGULATORY",
        intent="AYURVEDA_REGULATION"
    ),

    # AYUSH (AYURVEDA domain)
    EvaluationQuery(
        qid="Q012",
        query="Ministry of AYUSH annual report highlights",
        domain="AYURVEDA",
        intent="GENERAL_INFORMATION"
    ),
    EvaluationQuery(
        qid="Q013",
        query="AYUSH education and research initiatives",
        domain="AYURVEDA",
        intent="GENERAL_INFORMATION"
    ),

    # Biodiversity (BIODIVERSITY_ABS domain)
    EvaluationQuery(
        qid="Q014",
        query="Biodiversity Act 2002 provisions",
        domain="BIODIVERSITY_ABS",
        intent="BIODIVERSITY"
    ),

    # Access and Benefit Sharing (BIODIVERSITY_ABS domain)
    EvaluationQuery(
        qid="Q015",
        query="Access and benefit sharing regulations in India",
        domain="BIODIVERSITY_ABS",
        intent="ABS"
    ),

    # Traditional Knowledge (TRADITIONAL_KNOWLEDGE domain)
    EvaluationQuery(
        qid="Q016",
        query="Traditional knowledge protection and TKDL",
        domain="TRADITIONAL_KNOWLEDGE",
        intent="TRADITIONAL_KNOWLEDGE"
    ),

    # International IP (INTERNATIONAL domain)
    EvaluationQuery(
        qid="Q017",
        query="Paris Convention for patent protection",
        domain="INTERNATIONAL",
        intent="INTERNATIONAL_IP"
    ),
    EvaluationQuery(
        qid="Q018",
        query="PCT filing procedures and benefits",
        domain="INTERNATIONAL",
        intent="INTERNATIONAL_IP"
    ),
    EvaluationQuery(
        qid="Q019",
        query="TRIPS agreement minimum standards",
        domain="INTERNATIONAL",
        intent="INTERNATIONAL_IP"
    ),

    # Cross-domain queries
    EvaluationQuery(
        qid="Q020",
        query="Ayurveda products and patent protection",
        domain="GENERAL",
        intent="GENERAL_INFORMATION"
    ),
    EvaluationQuery(
        qid="Q021",
        query="Traditional knowledge and biodiversity access",
        domain="GENERAL",
        intent="GENERAL_INFORMATION"
    ),
    EvaluationQuery(
        qid="Q022",
        query="Ayurveda trademark registration",
        domain="GENERAL",
        intent="GENERAL_INFORMATION"
    ),

    # Exact reference queries
    EvaluationQuery(
        qid="Q023",
        query="Section 5 of the Copyright Act 1957",
        domain="IP",
        intent="COPYRIGHT"
    ),
    EvaluationQuery(
        qid="Q024",
        query="Rule 9 of the Trade Marks Rules 2017",
        domain="IP",
        intent="TRADEMARK"
    ),
]


@dataclass
class BaselineResult:
    """Single result in baseline measurement."""
    qid: str
    query: str
    method: str  # "vector", "lexical", "hybrid", "reranked"
    rank: int
    chunk_id: str
    document_id: str
    score: float
    latency_ms: float


@dataclass
class BaselineMeasurement:
    """Aggregated baseline measurements."""
    method: str
    total_queries: int
    queries_with_results: int
    avg_results_per_query: float
    median_latency_ms: float
    p95_latency_ms: float
    results: list[BaselineResult]


class BaselineVectorMeasurement:
    """Measure vector retrieval baseline."""

    def __init__(self, vector_retrieval):
        """Initialize with vector retrieval."""
        self.vector_retrieval = vector_retrieval
        logger.info("Baseline vector measurement initialized")

    async def measure_all(self, top_k: int = 10) -> BaselineMeasurement:
        """Measure vector retrieval on all evaluation queries."""

        logger.info(f"Starting baseline vector measurement on {len(EVALUATION_QUERIES)} queries")

        results = []
        latencies = []

        for query_obj in EVALUATION_QUERIES:
            query_results = await self.vector_retrieval.search(
                query_obj.query,
                top_k=top_k,
            )

            latency_ms = 0  # TODO: capture from retrieval
            latencies.append(latency_ms)

            for rank, result in enumerate(query_results, 1):
                baseline_result = BaselineResult(
                    qid=query_obj.qid,
                    query=query_obj.query,
                    method="vector",
                    rank=rank,
                    chunk_id=result.chunk_id,
                    document_id=result.document_id,
                    score=result.similarity_score,
                    latency_ms=latency_ms,
                )
                results.append(baseline_result)

        # Compute aggregates
        latencies_sorted = sorted(latencies)
        median_latency = latencies_sorted[len(latencies) // 2] if latencies else 0.0
        p95_latency = latencies_sorted[int(len(latencies) * 0.95)] if latencies else 0.0

        measurement = BaselineMeasurement(
            method="vector",
            total_queries=len(EVALUATION_QUERIES),
            queries_with_results=sum(1 for r in results if r.rank == 1),
            avg_results_per_query=len(results) / len(EVALUATION_QUERIES) if EVALUATION_QUERIES else 0,
            median_latency_ms=median_latency,
            p95_latency_ms=p95_latency,
            results=results,
        )

        logger.info(f"Baseline measurement complete: {measurement.queries_with_results}/{measurement.total_queries} queries with results")

        return measurement


def save_baseline_measurement(measurement: BaselineMeasurement, filepath: str):
    """Save baseline measurement to JSON."""
    with open(filepath, 'w') as f:
        json.dump({
            "metadata": {
                "method": measurement.method,
                "total_queries": measurement.total_queries,
                "queries_with_results": measurement.queries_with_results,
                "avg_results_per_query": measurement.avg_results_per_query,
                "median_latency_ms": measurement.median_latency_ms,
                "p95_latency_ms": measurement.p95_latency_ms,
            },
            "results": [asdict(r) for r in measurement.results],
        }, f, indent=2)

    logger.info(f"Baseline measurement saved to {filepath}")
