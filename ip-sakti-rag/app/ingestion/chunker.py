from __future__ import annotations

import hashlib
import re
from dataclasses import dataclass, field
from typing import Any, Iterable

from app.ingestion.extractor import PageText


HIERARCHY_RE = re.compile(
    r"^\s*(?P<kind>CHAPTER|PART|SCHEDULE|ANNEXURE|ANNEX|APPENDIX)\s+(?P<number>[A-Z0-9IVXLCDM().-]+)\b",
    re.IGNORECASE,
)
EXPLICIT_SECTION_RE = re.compile(r"^\s*(?:SECTION|Section)\s+(?P<number>\d+[A-Z]?)\b")
EXPLICIT_RULE_RE = re.compile(r"^\s*(?:RULE|Rule)\s+(?P<number>\d+[A-Z]?)\b")
EXPLICIT_REGULATION_RE = re.compile(r"^\s*(?:REGULATION|Regulation)\s+(?P<number>\d+[A-Z]?)\b")
ARTICLE_RE = re.compile(r"^\s*(?:ARTICLE|Article)\s+(?P<number>\d+[A-Z]?(?:\.\d+)?)\b")
NUMBERED_PROVISION_RE = re.compile(
    r"^\s*(?P<number>\d+[A-Z]?)\.\s+(?P<title>[A-Z][^\n]{2,180}?)(?:\.?[—–])",
)
NUMBERED_SUBREGULATION_RE = re.compile(r"^\s*(?P<number>\d+[A-Z]?)\.\s+\(\d+[A-Z]?\)\s+")
SUBSECTION_RE = re.compile(r"^\s*\((?P<number>\d+[A-Z]?)\)\s+")
CLAUSE_RE = re.compile(r"^\s*\((?P<number>[a-z]{1,3})\)\s+", re.IGNORECASE)
PARAGRAPH_RE = re.compile(r"^\s*(?P<number>\d+\.\d+(?:\.\d+)*)\s+")


@dataclass
class Piece:
    page: int | None
    text: str
    uncertain: bool = False


@dataclass
class Unit:
    metadata: dict[str, str | None]
    pieces: list[Piece] = field(default_factory=list)


def _blank_metadata() -> dict[str, str | None]:
    return {
        "chapter": None,
        "section": None,
        "subsection": None,
        "rule_number": None,
        "sub_rule": None,
        "regulation_number": None,
        "article_number": None,
        "paragraph_number": None,
        "clause": None,
        "structure_type": "PREAMBLE",
    }


def _provision(line: str, document_type: str) -> tuple[str, str] | None:
    if match := ARTICLE_RE.match(line):
        return "article_number", match.group("number")
    if document_type in {"ACT", "AMENDMENT_ACT"} and (match := EXPLICIT_SECTION_RE.match(line)):
        return "section", match.group("number")
    if document_type == "RULES" and (match := EXPLICIT_RULE_RE.match(line)):
        return "rule_number", match.group("number")
    if document_type == "REGULATION" and (match := EXPLICIT_REGULATION_RE.match(line)):
        return "regulation_number", match.group("number")
    match = NUMBERED_PROVISION_RE.match(line)
    if not match and document_type == "REGULATION":
        match = NUMBERED_SUBREGULATION_RE.match(line)
    if not match:
        return None
    number = match.group("number")
    if document_type == "TREATY":
        return None
    if document_type == "RULES":
        return "rule_number", number
    if document_type == "REGULATION":
        return "regulation_number", number
    if document_type in {"ACT", "AMENDMENT_ACT"}:
        return "section", number
    return None


def legal_units(pages: Iterable[PageText], document_type: str) -> list[Unit]:
    context = _blank_metadata()
    units: list[Unit] = []
    current = Unit(dict(context))

    def start(metadata: dict[str, str | None], line: str, page: PageText) -> None:
        nonlocal current
        if any(piece.text.strip() for piece in current.pieces):
            units.append(current)
        current = Unit(dict(metadata), [Piece(page.page, line, page.uncertain)])

    for page in pages:
        for raw_line in page.text.splitlines():
            line = raw_line.strip()
            if not line:
                if current.pieces and current.pieces[-1].text:
                    current.pieces.append(Piece(page.page, "", page.uncertain))
                continue
            if heading := HIERARCHY_RE.match(line):
                kind = heading.group("kind").upper()
                number = heading.group("number")
                if kind == "CHAPTER":
                    context["chapter"] = number
                context["structure_type"] = kind
                start(context, line, page)
                continue
            if provision := _provision(line, document_type):
                key, number = provision
                for name in ("section", "subsection", "rule_number", "sub_rule", "regulation_number", "article_number", "paragraph_number", "clause"):
                    context[name] = None
                context[key] = number
                context["structure_type"] = key.replace("_number", "").upper()
                start(context, line, page)
                continue
            if document_type == "TREATY" and (paragraph := PARAGRAPH_RE.match(line)):
                context["paragraph_number"] = paragraph.group("number")
                context["structure_type"] = "PARAGRAPH"
                start(context, line, page)
                continue
            if subsection := SUBSECTION_RE.match(line):
                number = subsection.group("number")
                context["clause"] = None
                if context.get("rule_number"):
                    context["sub_rule"] = number
                    context["structure_type"] = "SUB_RULE"
                else:
                    context["subsection"] = number
                    context["structure_type"] = "SUBSECTION"
                start(context, line, page)
                continue
            if clause := CLAUSE_RE.match(line):
                context["clause"] = clause.group("number").lower()
                context["structure_type"] = "CLAUSE"
                start(context, line, page)
                continue
            current.pieces.append(Piece(page.page, line, page.uncertain))
    if any(piece.text.strip() for piece in current.pieces):
        units.append(current)
    return units


def _split_piece(piece: Piece, max_chars: int) -> list[Piece]:
    if len(piece.text) <= max_chars:
        return [piece]
    words = piece.text.split()
    parts: list[Piece] = []
    buffer: list[str] = []
    for word in words:
        if buffer and len(" ".join(buffer)) + len(word) + 1 > max_chars:
            parts.append(Piece(piece.page, " ".join(buffer), piece.uncertain))
            buffer = []
        buffer.append(word)
    if buffer:
        parts.append(Piece(piece.page, " ".join(buffer), piece.uncertain))
    return parts


def chunk_units(
    units: Iterable[Unit],
    document: dict[str, Any],
    max_chars: int = 5000,
    min_chars: int = 80,
) -> list[dict[str, Any]]:
    chunks: list[dict[str, Any]] = []
    ordinal = 0
    for unit in units:
        expanded = [part for piece in unit.pieces for part in _split_piece(piece, max_chars)]
        groups: list[list[Piece]] = []
        current: list[Piece] = []
        size = 0
        for piece in expanded:
            addition = len(piece.text) + (1 if current else 0)
            if current and size + addition > max_chars:
                groups.append(current)
                current, size = [], 0
            current.append(piece)
            size += addition
        if current:
            groups.append(current)

        for group_index, group in enumerate(groups):
            text = "\n".join(piece.text for piece in group).strip()
            if not text:
                continue
            if len(text) < min_chars and chunks and all(
                chunks[-1].get(key) == unit.metadata.get(key)
                for key in ("section", "rule_number", "regulation_number", "article_number")
            ):
                chunks[-1]["text"] += "\n" + text
                continue
            ordinal += 1
            pages = [piece.page for piece in group if piece.page is not None]
            fingerprint = hashlib.sha256(
                f"{document['document_version']}|{ordinal}|{text}".encode("utf-8")
            ).hexdigest()[:12]
            chunk_id = f"{document['document_id']}-{ordinal:04d}-{fingerprint}"
            row = {
                "chunk_id": chunk_id,
                "ordinal": ordinal,
                "document_id": document["document_id"],
                "document_version": document["document_version"],
                "text": text,
                "title": document["title"],
                "authority": document["authority"],
                "domain": document["domain"],
                "jurisdiction": document["jurisdiction"],
                "document_type": document["document_type"],
                "source_url": document["source_url"],
                "language": document["language"],
                "source_status": document["ingestion_status"],
                "page_start": min(pages) if pages else None,
                "page_end": max(pages) if pages else None,
                "text_uncertain": any(piece.uncertain for piece in group),
                "structure_anchor": group_index == 0,
                **unit.metadata,
            }
            chunks.append(row)
    return chunks
