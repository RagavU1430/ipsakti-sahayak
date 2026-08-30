from __future__ import annotations

import logging

from fastapi import Depends, FastAPI, HTTPException

from app.models import AskRequest, AskResponse, ErrorResponse, QueryRequest, QueryResponse
from app.service import RAGService, get_service


logger = logging.getLogger(__name__)

app = FastAPI(
    title="IP-SAKTI Sahayak RAG API",
    version="0.1.0",
    description="Source-grounded IP and Ayurveda regulatory retrieval boundary.",
)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post(
    "/api/v1/ask",
    response_model=AskResponse,
    responses={422: {"model": ErrorResponse}, 503: {"model": ErrorResponse}},
)
def ask(request: AskRequest, service: RAGService = Depends(get_service)) -> AskResponse:
    try:
        return service.ask(request)
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
