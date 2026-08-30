from __future__ import annotations

from enum import Enum
from typing import Any

from pydantic import BaseModel, Field, field_validator


class Jurisdiction(str, Enum):
    INDIA = "INDIA"
    INTERNATIONAL = "INTERNATIONAL"
    BOTH = "BOTH"


class Confidence(str, Enum):
    HIGH = "HIGH"
    MEDIUM = "MEDIUM"
    LOW = "LOW"
    INSUFFICIENT_EVIDENCE = "INSUFFICIENT_EVIDENCE"


class Domain(str, Enum):
    PATENT = "PATENT"
    TRADEMARK = "TRADEMARK"
    GI = "GI"
    COPYRIGHT = "COPYRIGHT"
    DESIGN = "DESIGN"
    PLANT_VARIETY = "PLANT_VARIETY"
    ABS = "ABS"
    FOOD = "FOOD"
    AYURVEDA = "AYURVEDA"
    INTERNATIONAL = "INTERNATIONAL"


class QueryRequest(BaseModel):
    query: str = Field(min_length=2, max_length=4000)
    jurisdiction: Jurisdiction | None = None
    domain: Domain | None = None
    top_k: int | None = Field(default=None, ge=1, le=20)
    language: str = Field(default="en", pattern=r"^[a-z]{2}(?:-[A-Z]{2})?$")

    @field_validator("query")
    @classmethod
    def normalize_query(cls, value: str) -> str:
        normalized = " ".join(value.split())
        if len(normalized) < 2:
            raise ValueError("query must be a non-empty string")
        return normalized


class AskRequest(BaseModel):
    question: str = Field(min_length=2, max_length=4000)
    domain: Domain | None = None
    jurisdiction: Jurisdiction | None = None
    top_k: int | None = Field(default=None, ge=1, le=20)

    @field_validator("question")
    @classmethod
    def normalize_question(cls, value: str) -> str:
        normalized = " ".join(value.split())
        if len(normalized) < 2:
            raise ValueError("question must be a non-empty string")
        return normalized

    def to_query_request(self) -> QueryRequest:
        return QueryRequest(
            query=self.question,
            domain=self.domain,
            jurisdiction=self.jurisdiction,
            top_k=self.top_k,
        )


class Citation(BaseModel):
    document_id: str
    title: str
    authority: str
    chunk_id: str
    source_url: str
    section: str | None = None
    subsection: str | None = None
    rule_number: str | None = None
    sub_rule: str | None = None
    regulation_number: str | None = None
    article_number: str | None = None
    paragraph_number: str | None = None
    clause: str | None = None
    page_start: int | None = None
    page_end: int | None = None


class Evidence(BaseModel):
    chunk_id: str
    document_id: str
    text: str
    title: str
    authority: str
    domain: str
    jurisdiction: str
    document_type: str
    source_url: str
    document_version: str
    chapter: str | None = None
    section: str | None = None
    subsection: str | None = None
    rule_number: str | None = None
    sub_rule: str | None = None
    regulation_number: str | None = None
    article_number: str | None = None
    paragraph_number: str | None = None
    clause: str | None = None
    page_start: int | None = None
    page_end: int | None = None
    language: str = "en"
    source_status: str = "UNVERIFIED"
    vector_score: float = 0.0
    lexical_score: float = 0.0
    fusion_score: float = 0.0
    reranker_score: float = 0.0


class QueryAnalysis(BaseModel):
    query: str
    retrieval_query: str
    jurisdiction: Jurisdiction
    domains: list[str]
    intent: str | None = None
    legal_identifiers: list[str]
    language: str
    requested_top_k: int | None = None
    out_of_scope: bool = False
    speculative_subject: str | None = None
    ambiguous: bool = False


class QueryResponse(BaseModel):
    answer: str
    confidence: Confidence
    abstained: bool
    jurisdiction: Jurisdiction
    domain: str | None
    citations: list[Citation]
    evidence: list[Evidence] = Field(default_factory=list, exclude=True)
    limitations: list[str] = Field(default_factory=list)
    metrics: dict[str, Any] = Field(default_factory=dict, exclude=True)


class ErrorResponse(BaseModel):
    error: str
    code: str
    detail: str | None = None


class AskCitation(BaseModel):
    document: str
    document_id: str
    page: int | None = None
    section: str | None = None
    authority: str
    source_url: str
    chunk_id: str


class AskSource(BaseModel):
    document_id: str
    score: float


class AskResponse(BaseModel):
    answer: str
    confidence: float = Field(ge=0.0, le=1.0)
    abstained: bool
    citations: list[AskCitation]
    sources: list[AskSource]
