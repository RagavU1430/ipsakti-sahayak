from __future__ import annotations

from contextlib import asynccontextmanager
import logging
import time

from fastapi import Depends, FastAPI, Header, HTTPException, Response

from app.models import AskRequest, AskResponse, ErrorResponse, QueryRequest, QueryResponse
from app.service import RAGService, get_service


logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(_: FastAPI):
    """Build the corpus index before the API advertises readiness."""
    started = time.perf_counter()
    service = get_service()
    logger.info(
        "rag_service_ready startup_ms=%.3f chunks=%s generator=%s",
        (time.perf_counter() - started) * 1000,
        len(getattr(service.store, "chunks", [])),
        getattr(service.generator, "name", type(service.generator).__name__),
    )
    yield


app = FastAPI(
    title="IP-SAKTI Sahayak RAG API",
    version="0.1.0",
    description="Source-grounded IP and Ayurveda regulatory retrieval boundary.",
    lifespan=lifespan,
)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post(
    "/api/v1/ask",
    response_model=AskResponse,
    responses={422: {"model": ErrorResponse}, 503: {"model": ErrorResponse}},
)
def ask(request: AskRequest, response: Response, x_request_id: str | None = Header(default=None), service: RAGService = Depends(get_service)) -> AskResponse:
    try:
        # Public response shape remains unchanged; benchmark-safe headers expose
        # stage timings from the actual request rather than a synthetic probe.
        query_response = service.query(request.to_query_request(), request_id=x_request_id)
        for key, value in query_response.metrics.items():
            if isinstance(value, (int, float, str, bool)):
                response.headers[f"X-RAG-{key.replace('_', '-')}"] = str(value)
        response.headers["Server-Timing"] = ", ".join(
            f"{name};dur={float(query_response.metrics.get(metric, 0)):.1f}"
            for name, metric in (("retrieval", "retrieval_ms"), ("rerank", "reranking_ms"), ("llm", "generation_ms"), ("total", "total_ms"))
        )
        response.headers["X-RAG-evidence-passed-to-llm"] = str(bool(query_response.evidence)).lower()
        response.headers["X-RAG-context-chunks"] = str(len(query_response.evidence))
        if x_request_id:
            response.headers["X-Request-ID"] = x_request_id
        return service._to_ask_response(query_response)
    except (FileNotFoundError, RuntimeError, ValueError):
        logger.exception("rag_ask_unavailable")
        raise HTTPException(status_code=503, detail={
            "error": "RAG service unavailable",
            "code": "RAG_UNAVAILABLE",
            "detail": "The RAG runtime could not complete the request safely.",
        }) from None


@app.post(
    "/rag/query",
    response_model=QueryResponse,
    responses={422: {"model": ErrorResponse}, 503: {"model": ErrorResponse}},
)
def rag_query(request: QueryRequest, service: RAGService = Depends(get_service)) -> QueryResponse:
    try:
        return service.query(request)
    except (FileNotFoundError, RuntimeError, ValueError) as exc:
        logger.exception("rag_query_unavailable")
        raise HTTPException(status_code=503, detail={
            "error": "RAG service unavailable",
            "code": "RAG_UNAVAILABLE",
            "detail": "The RAG runtime could not complete the request safely.",
        }) from exc
