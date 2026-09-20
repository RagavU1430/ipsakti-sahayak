from __future__ import annotations

import hashlib
import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any

UNKNOWN = "UNKNOWN"
DEFAULT_MAX_CHUNK_TOKENS = 700
DEFAULT_MIN_CHUNK_TOKENS = 40
DEFAULT_OVERLAP_TOKENS = 40
PAGE_RE = re.compile(r"^##\s+Page\s+(\d+)\s*$", re.IGNORECASE)
HEADING_RE = re.compile(r"^(#{1,6})\s+(.+?)\s*$")
PROVISION_RE = re.compile(
    r"^(?:(?:Section|Rule|Regulation|Article)\s+([0-9]+[A-Za-z]?(?:\([A-Za-z0-9]+\))?)|([0-9]+[A-Za-z]?)\.(?:\s+|$))",
    re.IGNORECASE,
)
PLACEHOLDER_RE = re.compile(r"^\[(?:No extractable text found on this page|Empty source file)\.\]$", re.IGNORECASE)


@dataclass(frozen=True)
class CorpusBuildResult:
    documents: list[dict[str, Any]]
    chunks: list[dict[str, Any]]
    manifest: dict[str, Any]


def _token_count(text: str) -> int:
    return len(re.findall(r"\S+", text))


def _hash_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def _parse_frontmatter(text: str) -> tuple[dict[str, Any], str, list[str]]:
    if not text.startswith("---\n"):
        return {}, text, ["missing_frontmatter"]
    end = text.find("\n---", 4)
    if end < 0:
        return {}, text, ["malformed_frontmatter"]
    metadata: dict[str, Any] = {}
    errors: list[str] = []
    for line in text[4:end].splitlines():
        if not line.strip() or ":" not in line:
            continue
        key, raw_value = line.split(":", 1)
        value = raw_value.strip()
        try:
            metadata[key.strip()] = json.loads(value)
        except json.JSONDecodeError:
            metadata[key.strip()] = value.strip('"')
            errors.append(f"unparsed_frontmatter:{key.strip()}")
    return metadata, text[end + 4 :].lstrip("\n"), errors


def _title(lines: list[str], fallback: str) -> str:
    for line in lines:
        match = HEADING_RE.match(line.strip())
        if match and len(match.group(1)) == 1:
            return match.group(2).strip()
    return fallback.replace("_", " ").replace("-", " ").rsplit(".", 1)[0].strip().title()


def _stable_document_id(relative_path: str) -> str:
    digest = hashlib.sha256(relative_path.replace("\\", "/").encode("utf-8")).hexdigest()[:16]
    return f"V2-DOC-{digest}"


def _metadata_value(metadata: dict[str, Any], key: str) -> Any:
    value = metadata.get(key, UNKNOWN)
    return value if value not in (None, "", []) else UNKNOWN


def _provision(line: str) -> tuple[str, str] | None:
    match = PROVISION_RE.match(line.strip())
    if not match:
        return None
    number = match.group(1) or match.group(2)
    prefix = line.strip().split()[0].lower().rstrip(".")
    kind = {"section": "SECTION", "rule": "RULE", "regulation": "REGULATION", "article": "ARTICLE"}.get(prefix, "SECTION")
    return kind, number


def _flush_chunk(
    chunks: list[dict[str, Any]],
    text_lines: list[str],
    context: dict[str, Any],
    document: dict[str, Any],
    chunk_index: int,
    max_tokens: int,
) -> int:
    text = "\n".join(line.rstrip() for line in text_lines).strip()
    if not text or PLACEHOLDER_RE.fullmatch(text):
        return chunk_index
    prefix = "\n".join(value for value in (context.get("title"), context.get("section"), context.get("subsection")) if value and value != UNKNOWN)
    prefix_tokens = _token_count(prefix)
    body_tokens = text.split()
    segment_size = max(1, max_tokens - prefix_tokens)
    for start in range(0, len(body_tokens), segment_size):
        segment = " ".join(body_tokens[start : start + segment_size])
        content = "\n".join(value for value in (prefix, segment) if value)
        chunks.append({
            "chunk_id": f"{document['document_id']}-C{chunk_index:05d}-{_hash_text(content)[:12]}",
            "document_id": document["document_id"],
            "document_version": document["document_version"],
            "domain": document["domain"],
            "source": document["source"],
            "title": document["title"],
            "section": context.get("section", UNKNOWN),
            "subsection": context.get("subsection", UNKNOWN),
            "provision_type": context.get("provision_type", UNKNOWN),
            "provision_number": context.get("provision_number", UNKNOWN),
            "page_start": context.get("page_start", UNKNOWN),
            "page_end": context.get("page_end", UNKNOWN),
            "jurisdiction": document["jurisdiction"],
            "document_type": document["document_type"],
            "authority": document["authority"],
            "publication_date": document["publication_date"],
            "effective_date": document["effective_date"],
            "source_url": document["source_url"],
            "language": document["language"],
            "parent_section": context.get("parent_section", UNKNOWN),
            "chunk_index": chunk_index,
            "content_hash": _hash_text(content),
            "token_count": _token_count(content),
            "text": content,
        })
        chunk_index += 1
    return chunk_index


def _split_document(md_path: Path, relative_path: str, max_tokens: int, min_tokens: int, overlap_tokens: int) -> tuple[dict[str, Any], list[dict[str, Any]], list[str]]:
    raw = md_path.read_text(encoding="utf-8")
    metadata, body, errors = _parse_frontmatter(raw)
    lines = body.splitlines()
    document_id = _stable_document_id(relative_path)
    source_hash = _hash_text(raw)
    document = {
        "document_id": document_id,
        "document_version": f"{document_id}:{source_hash[:12]}",
        "source": relative_path.replace("\\", "/"),
        "title": _title(lines, md_path.name),
        "domain": _metadata_value(metadata, "domain"),
        "jurisdiction": _metadata_value(metadata, "jurisdiction"),
        "document_type": _metadata_value(metadata, "document_type"),
        "authority": _metadata_value(metadata, "authority"),
        "publication_date": _metadata_value(metadata, "publication_date"),
        "effective_date": _metadata_value(metadata, "effective_date"),
        "source_url": _metadata_value(metadata, "source_url"),
        "language": _metadata_value(metadata, "language"),
        "page_count": _metadata_value(metadata, "page_count"),
        "extraction_status": "VALID",
        "source_hash": source_hash,
        "metadata_errors": errors,
    }
    if not raw.strip():
        document["extraction_status"] = "EMPTY"
    if "conversion_status: extraction_failed" in raw.lower() or "[no extractable text found" in raw.lower() and not re.search(r"\S+", body.replace("[No extractable text found on this page.]", "")):
        document["extraction_status"] = "EXTRACTION_FAILED"
        return document, [], errors + ["extraction_failed"]

    context: dict[str, Any] = {"title": document["title"], "page_start": UNKNOWN, "page_end": UNKNOWN}
    current: list[str] = []
    chunks: list[dict[str, Any]] = []
    chunk_index = 0
    for line in lines:
        stripped = line.strip()
        page_match = PAGE_RE.match(stripped)
        if page_match:
            page = int(page_match.group(1))
            if current and _token_count(" ".join(current)) >= min_tokens:
                chunk_index = _flush_chunk(chunks, current, context, document, chunk_index, max_tokens)
                current = current[-overlap_tokens:] if overlap_tokens else []
            context["page_start"] = page
            context["page_end"] = page
            continue
        heading = HEADING_RE.match(stripped)
        if heading:
            level = len(heading.group(1))
            text = heading.group(2).strip()
            if level <= 3:
                if level == 1:
                    context["title"] = text
                elif level == 2:
                    context["section"] = text
                    context["subsection"] = UNKNOWN
                    context["parent_section"] = text
                else:
                    context["subsection"] = text
                if current and _token_count(" ".join(current)) >= min_tokens:
                    chunk_index = _flush_chunk(chunks, current, context, document, chunk_index, max_tokens)
                    current = current[-overlap_tokens:] if overlap_tokens else []
            current.append(stripped)
            continue
        provision = _provision(stripped)
        if provision:
            if current and _token_count(" ".join(current)) >= min_tokens:
                chunk_index = _flush_chunk(chunks, current, context, document, chunk_index, max_tokens)
                current = current[-overlap_tokens:] if overlap_tokens else []
            context["provision_type"], context["provision_number"] = provision
            context["section"] = f"{context['provision_type'].title()} {context['provision_number']}"
        current.append(line)
        if _token_count(" ".join(current)) >= max_tokens:
            chunk_index = _flush_chunk(chunks, current, context, document, chunk_index, max_tokens)
            current = current[-overlap_tokens:] if overlap_tokens else []
    if current:
        _flush_chunk(chunks, current, context, document, chunk_index, max_tokens)
    return document, chunks, errors


def build_corpus(input_dir: Path, max_tokens: int = DEFAULT_MAX_CHUNK_TOKENS, min_tokens: int = DEFAULT_MIN_CHUNK_TOKENS, overlap_tokens: int = DEFAULT_OVERLAP_TOKENS) -> CorpusBuildResult:
    documents: list[dict[str, Any]] = []
    chunks: list[dict[str, Any]] = []
    seen_content: dict[str, str] = {}
    for md_path in sorted(input_dir.rglob("*.md")):
        if md_path.name == "conversion_manifest.json" or md_path.name.startswith(".gitkeep"):
            continue
        relative_path = md_path.relative_to(input_dir).as_posix()
        document, document_chunks, errors = _split_document(md_path, relative_path, max_tokens, min_tokens, overlap_tokens)
        content_hash = document["source_hash"]
        document["duplicate_of"] = seen_content.get(content_hash, UNKNOWN)
        if content_hash not in seen_content:
            seen_content[content_hash] = document["document_id"]
        document["chunk_count"] = len(document_chunks)
        documents.append(document)
        chunks.extend(document_chunks)
    manifest = {
        "dataset_version": "RAG_V2_DATASET_001",
        "document_count": len(documents),
        "valid_document_count": sum(document["extraction_status"] == "VALID" for document in documents),
        "chunk_count": len(chunks),
        "max_chunk_tokens": max_tokens,
        "min_chunk_tokens": min_tokens,
        "overlap_tokens": overlap_tokens,
        "duplicate_document_count": sum(document["duplicate_of"] != UNKNOWN for document in documents),
        "missing_metadata_fields": {field: sum(document[field] == UNKNOWN for document in documents) for field in ("domain", "jurisdiction", "document_type", "authority", "source_url", "publication_date", "effective_date", "language")},
    }
    return CorpusBuildResult(documents, chunks, manifest)


def write_result(result: CorpusBuildResult, output_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / "validated_manifest.json").write_text(json.dumps({"manifest": result.manifest, "documents": result.documents}, indent=2, ensure_ascii=True) + "\n", encoding="utf-8")
    with (output_dir / "chunks_v2.jsonl").open("w", encoding="utf-8") as handle:
        for chunk in result.chunks:
            handle.write(json.dumps(chunk, ensure_ascii=True) + "\n")
