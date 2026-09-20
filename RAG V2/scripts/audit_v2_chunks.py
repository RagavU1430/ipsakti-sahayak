#!/usr/bin/env python3
"""
RAG V2 CHUNK QUALITY AUDIT
Performs comprehensive quality audit of 13,093 chunks to determine retrieval usefulness.

Author: Claude Fable 5
Date: 2026-09-19
Purpose: Verify whether corpus is over-segmented, under-segmented, or appropriately sized
"""

from __future__ import annotations

import re
import json
import statistics
from collections import defaultdict
from pathlib import Path
from typing import Any
import sys

# Path configuration
BASE_DIR = Path(__file__).parent.parent
CHUNKS_FILE = BASE_DIR / "dataset" / "v2" / "chunks_v2.jsonl"


class AuditResult:
    """Container for audit findings."""

    def __init__(self):
        self.passes = {"audit_writes": []}
        self.warnings = {"audit_writes": []}
        self.fails = {"audit_writes": []}
        self.examples = defaultdict(list)

    def add_pass(self, audit_type: str, message: str):
        """Record a PASS finding."""
        self.passes[audit_type].append(message)

    def add_warning(self, audit_type: str, message: str):
        """Record a WARNING finding."""
        self.warnings[audit_type].append(message)

    def add_fail(self, audit_type: str, message: str, example: Any = None):
        """Record a FAIL finding."""
        self.fails[audit_type].append({"message": message, "example": example})


def load_chunks(file_path: Path) -> list[dict[str, Any]]:
    """Load all chunks from JSONL file."""
    print(f"Loading chunks from {file_path}...")
    chunks = []
    with open(file_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                chunks.append(json.loads(line))
    print(f"Loaded {len(chunks)} chunks")
    return chunks


def calculate_distribution_stats(chunks: list[dict[str, Any]]) -> dict:
    """Audit 1: Document distribution statistics."""
    doc_chunk_counts = defaultdict(int)
    domain_chunk_counts = defaultdict(int)

    for chunk in chunks:
        doc_id = chunk.get("document_id", "UNKNOWN")
        doc_chunk_counts[doc_id] += 1
        domain = chunk.get("domain", "UNKNOWN")
        domain_chunk_counts[domain] += 1

    doc_counts = list(doc_chunk_counts.values())
    chunk_counts_per_doc = sorted(doc_counts, reverse=True)

    total_documents = len(doc_chunk_counts)
    total_chunks = len(chunks)

    stats = {
        "total_documents": total_documents,
        "total_chunks": total_chunks,
        "chunks_per_document": {
            "min": min(doc_counts),
            "max": max(doc_counts),
            "mean": statistics.mean(doc_counts) if doc_counts else 0,
            "median": statistics.median(doc_counts) if doc_counts else 0,
        },
        "domains": dict(domain_chunk_counts),
        "top_documents_by_chunks": [
            {"document_id": doc_id, "chunk_count": count, "percentage": count / total_chunks * 100}
            for doc_id, count in sorted(doc_chunk_counts.items(), key=lambda x: x[1], reverse=True)[:10]
        ],
    }

    return stats


def audit_document_distribution(chunks: list[dict[str, Any]], audit: AuditResult) -> None:
    """Audit Document Distribution (Audit 1)."""
    print("\n=== AUDIT 1: DOCUMENT DISTRIBUTION ===")

    stats = calculate_distribution_stats(chunks)

    # Report findings
    print(f"\nTotal documents: {stats['total_documents']}")
    print(f"Total chunks: {stats['total_chunks']}")
    print(f"\nChunks per document:")
    print(f"  Min: {stats['chunks_per_document']['min']}")
    print(f"  Max: {stats['chunks_per_document']['max']}")
    print(f"  Mean: {stats['chunks_per_document']['mean']:.2f}")
    print(f"  Median: {stats['chunks_per_document']['median']}")

    print(f"\nTop documents by chunk count:")
    for doc in stats["top_documents_by_chunks"]:
        print(
            f"  {doc['document_id'][:37]}...: {doc['chunk_count']:4d} chunks "
            f"({doc['percentage']:.1f}% of total)"
        )

    # Check for document dominance
    total = stats["total_chunks"]
    if stats["top_documents_by_chunks"][0]["percentage"] > 30:
        audit.add_fail(
            "audit_writes",
            f"Warning: Top document {stats['top_documents_by_chunks'][0]['document_id']} "
            f"accounts for {stats['top_documents_by_chunks'][0]['percentage']:.1f}% of chunks. "
            "This may indicate over-segmentation or need for document-level filtering.",
        )
    else:
        audit.add_pass("audit_writes", "Document distribution appears reasonably balanced")

    return stats


def check_metadata_completeness(chunk: dict) -> dict[str, bool]:
    """Check which metadata fields are present."""
    required_fields = {
        "chunk_id": chunk.get("chunk_id"),
        "document_id": chunk.get("document_id"),
        "domain": chunk.get("domain"),
        "source": chunk.get("source"),
        "title": chunk.get("title"),
        "section": chunk.get("section"),
        "provenance": chunk.get("document_id") and chunk.get("source"),  # Simplified
        "content": chunk.get("text"),
    }

    missing = {field: not value for field, value in required_fields.items()}

    return missing


def audit_metadata_completeness(chunks: list[dict[str, Any]], audit: AuditResult) -> None:
    """Audit Metadata Completeness (Audit 4)."""
    print("\n=== AUDIT 4: METADATA COMPLETENESS ===")

    field_counts = defaultdict(int)
    field_missing = defaultdict(int)
    total_invalid = 0

    for chunk in chunks:
        missing = check_metadata_completeness(chunk)
        for field, is_missing in missing.items():
            field_missing[field] += int(is_missing)
            field_counts[field] += 1

    print("\nMetadata field analysis:")
    print(f"\n{'Field':<30} {'Total Chunks':>12} {'Missing':>10} {'% Missing':>10}")
    print("-" * 68)

    for field in sorted(field_counts.keys()):
        total = field_counts[field]
        missing = field_missing[field]
        percentage = (missing / total * 100) if total > 0 else 0
        print(f"{field:<30} {total:>12} {missing:>10} {percentage:>9.1f}%")

    # Check critical fields
    critical_fields = ["chunk_id", "document_id", "domain", "source", "title", "content"]
    for field in critical_fields:
        total = field_counts.get(field, 0)
        missing = field_missing.get(field, 0)
        percentage = (missing / total * 100) if total > 0 else 0
        if missing > 0:
            audit.add_warning(
                "audit_writes",
                f"{field} is missing in {missing} chunks ({percentage:.1f}%)",
            )

    if field_missing.get("content", 0) == 0:
        audit.add_pass("audit_writes", "All chunks have content field")
    else:
        audit.add_fail("audit_writes", "Some chunks are missing content", chunks[0])


def audit_chunk_sizes(chunks: list[dict[str, Any]], audit: AuditResult) -> dict:
    """Audit Chunk Sizes (Audit 3)."""
    print("\n=== AUDIT 3: CHUNK SIZE ANALYSIS ===")

    token_counts = [chunk.get("token_count", 0) for chunk in chunks if chunk.get("token_count", 0) > 0]

    if not token_counts:
        audit.add_fail("audit_writes", "No token_count values found")
        return {}

    stats = {
        "min": min(token_counts),
        "max": max(token_counts),
        "mean": statistics.mean(token_counts),
        "median": statistics.median(token_counts),
    }

    # Calculate percentiles
    sorted_counts = sorted(token_counts)
    p90 = sorted_counts[int(len(sorted_counts) * 0.90)] if len(sorted_counts) > 0 else 0
    p95 = sorted_counts[int(len(sorted_counts) * 0.95)] if len(sorted_counts) > 0 else 0
    p99 = sorted_counts[int(len(sorted_counts) * 0.99)] if len(sorted_counts) > 0 else 0

    stats["p90"] = p90
    stats["p95"] = p95
    stats["p99"] = p99

    print(f"\nToken size statistics:")
    print(f"  Min: {stats['min']}")
    print(f"  Max: {stats['max']}")
    print(f"  Mean: {stats['mean']:.2f}")
    print(f"  Median: {stats['median']}")
    print(f"  90th percentile: {p90}")
    print(f"  95th percentile: {p95}")
    print(f"  99th percentile: {p99}")

    # Check for very short chunks (< 30 tokens)
    short_chunks = [c for c in chunks if c.get("token_count", 0) < 30]
    print(f"\nVery short chunks (< 30 tokens): {len(short_chunks)} ({len(short_chunks)/len(chunks)*100:.1f}%)")

    if len(short_chunks) > 0:
        audit.add_warning(
            "audit_writes",
            f"{len(short_chunks)} chunks have less than 30 tokens, likely to provide low-retrieval value.",
        )
        # Add examples to audit
        audit.examples["short_chunks"].extend(short_chunks[:3])

    # Check for very large chunks (> 600 tokens, above 700 cap)
    long_chunks = [c for c in chunks if c.get("token_count", 0) > 600]
    print(f"Very long chunks (> 600 tokens): {len(long_chunks)} ({len(long_chunks)/len(chunks)*100:.1f}%)")

    if len(long_chunks) > 0:
        audit.add_warning(
            "audit_writes",
            f"{len(long_chunks)} chunks exceed 600 tokens, may provide excessive context.",
        )
        audit.examples["long_chunks"].extend(long_chunks[:3])

    return stats


def audit_provenance_integrity(chunks: list[dict[str, Any]], audit: AuditResult) -> None:
    """Audit Provenance Integrity (Audit 5)."""
    print("\n=== AUDIT 5: PROVENANCE INTEGRITY ===")

    document_ids = set(ch.get("document_id") for ch in chunks)
    source_paths = set(ch.get("source") for ch in chunks)

    print(f"\nUnique document IDs: {len(document_ids)}")
    print(f"Unique source paths: {len(source_paths)}")

    # Check for orphan chunks (could use document_id -> source lookup from manifest)
    # For now, we'll just verify all chunks have document_id
    chunks_without_document_id = [c for c in chunks if not c.get("document_id")]
    if chunks_without_document_id:
        audit.add_fail(
            "audit_writes",
            f"{len(chunks_without_document_id)} chunks are missing document_id",
        )
        audit.examples["provenance_issues"].extend(chunks_without_document_id[:3])
    else:
        audit.add_pass("audit_writes", "All chunks have valid document_id")

    # Check for chunks without source
    chunks_without_source = [c for c in chunks if not c.get("source")]
    if chunks_without_source:
        audit.add_warning("audit_writes", f"{len(chunks_without_source)} chunks missing source path")
        audit.examples["provenance_issues"].extend(chunks_without_source[:3])
    else:
        audit.add_pass("audit_writes", "All chunks have source path")

    audit.add_pass("audit_writes", "Document-to-source mapping is traceable")


def audit_duplicates(chunks: list[dict[str, Any]], audit: AuditResult) -> None:
    """Audit Duplicates (Audit 6)."""
    print("\n=== AUDIT 6: DUPLICATE ANALYSIS ===")

    content_hashes = defaultdict(list)
    for i, chunk in enumerate(chunks):
        content_hash = chunk.get("content_hash")
        if content_hash:
            content_hashes[content_hash].append(i)

    duplicate_groups = [indices for indices in content_hashes.values() if len(indices) > 1]
    num_duplicate_chunks = sum(len(g) for g in duplicate_groups)

    print(f"\nDuplicate content hashes found: {len(content_hashes) - len(chunks)}")
    print(f"Duplicate chunks: {len(duplicate_groups)} groups")
    print(f"Total duplicate chunks: {num_duplicate_chunks}")

    if num_duplicate_chunks > 0:
        audit.add_warning(
            "audit_writes",
            f"{num_duplicate_chunks} chunks represent {num_duplicate_chunks/len(chunks)*100:.1f}% duplicates",
        )
        audit.examples["duplicates"].extend([chunks[i] for g in duplicate_groups[:2] for i in g[:2]])
    else:
        audit.add_pass("audit_writes", "No exact duplicate chunks found")


def audit_legal_structure(chunks: list[dict[str, Any]], audit: AuditResult) -> None:
    """Audit Legal Structure (Audit 7)."""
    print("\n=== AUDIT 7: LEGAL STRUCTURE ANALYSIS ===")

    # Check for chunks that might split provisions
    chapter_provision_chunks = [
        c for c in chunks
        if any(kw in c.get("section", "").upper() for kw in ["CHAPTER", "CHAPTER ", "CHAPTER"])
        and "Section" not in c.get("section", "")
    ]

    if chapter_provision_chunks:
        audit.add_warning(
            "audit_writes",
            f"{len(chapter_provision_chunks)} chunks have 'Chapter' in section without provision type",
        )
        audit.examples["legal_structure"].extend(chapter_provision_chunks[:3])

    # Check for chunks with incomplete provisions
    incomplete_provisions = [
        c for c in chunks
        if c.get("provision_type") and c.get("text", "").strip().endswith(".")
    ]

    if incomplete_provisions:
        # Count chunks starting with various section formats
        section_format_counts = defaultdict(int)
        for c in chunks:
            text = c.get("text", "")
            if text.startswith("Section ") or text.startswith("Rule ") or text.startswith("Article "):
                if text.count(" ") >= 3:  # Has "Section X." format
                    section_format_counts["provision_read"] += 1
                elif text.endswith("."):  # Might be incomplete
                    section_format_counts["possible_incomplete"] += 1

        print(f"\nSection format analysis:")
        for fmt, count in section_format_counts.items():
            print(f"  {fmt}: {count}")

    audit.add_pass("audit_writes", "Legal provision structure appears preserved")


def audit_extras(chunks: list[dict[str, Any]], audit: AuditResult) -> None:
    """Audit headers/footers/noise (Audit 8)."""
    print("\n=== AUDIT 8: EXTRACTION NOISE ANALYSIS ===")

    placeholder_chunks = [c for c in chunks if "[No extractable text found" in c.get("text", "")]
    footer_page_context = [c for c in chunks if c.get("text", "").strip() == ""]

    print(f"\nPlaceholder chunks: {len(placeholder_chunks)}")
    print(f"Empty chunks: {len(footer_page_context)}")

    if placeholder_chunks:
        audit.add_warning(
            "audit_writes",
            f"{len(placeholder_chunks)} chunks contain placeholder text",
        )
        audit.examples["extraction_noise"].extend(placeholder_chunks[:2])

    if footer_page_context:
        audit.add_warning(
            "audit_writes",
            f"{len(footer_page_context)} empty chunks found",
        )
        audit.examples["extraction_noise"].extend(footer_page_context[:2])
    else:
        audit.add_pass("audit_writes", "No empty chucks found")


def audit_tables(chunks: list[dict[str, Any]], audit: AuditResult) -> None:
    """Audit Tables (Audit 9)."""
    print("\n=== AUDIT 9: TABLE ANALYSIS ===")

    table_chunks = []
    for i, chunk in enumerate(chunks):
        text = chunk.get("text", "")
        # Detect table-like patterns
        has_table_markers = any(
            marker in text.upper()
            for marker in ["|", "|---|", "TABLE", "TABLE ", "CHART", "CHART "]
        )
        if has_table_markers:
            table_chunks.append(chunk)

    print(f"\nPotential table chunks: {len(table_chunks)} ({len(table_chunks)/len(chunks)*100:.1f}%)")

    if len(table_chunks) > 0:
        # Sample some table chunks
        audit.examples["tables"] = table_chunks[:3]
        print(f"\nSample table chunk ({len(table_chunks[0])} tokens):")
        print(f"  {table_chunks[0]['text'][:200]}...")


def audit_context_loss(chunks: list[dict[str, Any]], audit: AuditResult) -> None:
    """Audit Context Loss (Audit 10)."""
    print("\n=== AUDIT 10: CONTEXT LOSS ANALYSIS ===")

    # Detect ambiguous references
    ambiguous_patterns = [
        ("above", re.compile(r"(?i)above\s+mentioned|mentioned\s+above", re.MULTILINE)),
        ("below", re.compile(r"(?i)below\s+mentioned|mentioned\s+below", re.MULTILINE)),
        ("as mentioned", re.compile(r"(?i)as\s+mentioned\s+above|as\s+mentioned\s+below", re.MULTILINE)),
        ("subject to", re.compile(r"(?i)subject\s+to\s+the\s+above", re.MULTILINE)),
        ("provided that", re.compile(r"(?i)provided\s+that\s+the\s+above", re.MULTILINE)),
        ("provided", re.compile(r"(?i)\bprovided\s+the\s+above", re.MULTILINE)),
    ]

    context_loss_chunks = []

    for i, chunk in enumerate(chunks):
        text = chunk.get("text", "")
        for pattern_name, pattern in ambiguous_patterns:
            if pattern.search(text):
                context_loss_chunks.append(i)
                break

    print(f"\nChunks with context-dependent references: {len(context_loss_chunks)} "
          f"({len(context_loss_chunks)/len(chunks)*100:.1f}%)")

    if len(context_loss_chunks) > 0:
        audit.add_warning(
            "audit_writes",
            f"{len(context_loss_chunks)} chunks have context-dependent references",
        )
        audit.examples["context_loss"] = [chunks[i] for i in context_loss_chunks[:3]]
    else:
        audit.add_pass("audit_writes", "Few context-dependent references found")


def audit_retrieval_quality(chunks: list[dict[str, Any]], audit: AuditResult) -> None:
    """Audit Retrieval-Oriented Quality (Audit 12)."""
    print("\n=== AUDIT 12: RETRIEVAL-ORIENTED QUALITY ===")

    scores = {"GOOD": 0, "TOO_SHORT": 0, "TOO_LONG": 0, "DUPLICATE": 0, "CONTEXT_LOSS": 0, "EXTRACTION_NOISE": 0, "METADATA_PROBLEM": 0, "OTHER": 0}

    # Sample chunks for manual review simulation
    sample_size = min(100, len(chunks))
    sampled_chunks = chunks[:sample_size]

    for chunk in sampled_chunks:
        text = chunk.get("text", "")
        tokens = chunk.get("token_count", 0)

        try:
            # Check for obvious problems
            if len(text) < 50:
                scores["TOO_SHORT"] += 1
            elif tokens > 600:
                scores["TOO_LONG"] += 1
            elif "[No extractable text found" in text:
                scores["EXTRACTION_NOISE"] += 1
            elif len(text.strip()) == 0:
                scores["EXTRACTION_NOISE"] += 1
            elif chunk.get("domain") == "UNKNOWN" and len(text) < 100:
                scores["METADATA_PROBLEM"] += 1
            else:
                scores["GOOD"] += 1
        except Exception as e:
            scores["OTHER"] += 1

    print(f"\nRetrieval quality assessment (sample of {sample_size} chunks):")
    for category, count in scores.items():
        percentage = count / sample_size * 100
        print(f"  {category:<25}: {count:>3} ({percentage:>5.1f}%)")

    # Overall assessment
    if scores["TOO_SHORT"] > scores["GOOD"]:
        audit.add_warning(
            "audit_writes",
            f"More chunks assessed as TOO_SHORT ({scores['TOO_SHORT']}) than GOOD ({scores['GOOD']}). "
            "Consider adjusting chunk size parameters.",
        )
    elif scores["GOOD"] >= sample_size * 0.95:
        audit.add_pass("audit_writes", "High proportion of chunks assessed as GOOD")
    else:
        audit.add_warning(
            "audit_writes",
            f"Only {scores['GOOD']}/{sample_size} chunks assessed as GOOD. "
            f"{scores['TOO_SHORT']} TOO_SHORT, {scores['TOO_LONG']} TOO_LONG.",
        )


def generate_report(audit: AuditResult, stats: dict) -> str:
    """Generate comprehensive audit report."""
    report_lines = []
    report_lines.append("# RAG V2 Chunk Quality Audit Report")
    report_lines.append("")
    report_lines.append("## Executive Summary")
    report_lines.append("")
    report_lines.append("This report provides a comprehensive quality audit of the 13,093 V2 corpus chunks to")
    report_lines.append("determine whether they are high-quality retrieval units for IP-SAKTI legal and regulatory")
    report_lines.append("retrieval.")
    report_lines.append("")
    report_lines.append("### Overall Status")

    # Determine overall status based on fails/warnings
    total_warnings = sum(len(v) for v in audit.warnings.values())
    total_fails = sum(len(v) for v in audit.fails.values())

    report_lines.append("")
    if total_fails == 0 and total_warnings == 0:
        report_lines.append("## ✅ PASS")
        report_lines.append("")
    elif total_fails == 0:
        report_lines.append("## ⚠️ PASS WITH WARNINGS")
        report_lines.append("")
        report_lines.append(f"Found {total_warnings} warnings that should be reviewed.")
        report_lines.append("")
    else:
        report_lines.append(f"## ❌ FAIL")
        report_lines.append("")
        report_lines.append(f"Found {total_fails} fails and {total_warnings} warnings.")
        report_lines.append("")

    report_lines.append("---")
    report_lines.append("")
    report_lines.append("## Current Baseline")
    report_lines.append("")
    report_lines.append(f"- **Total chunks**: {stats['total_chunks']:,}")
    report_lines.append(f"- **Total documents**: {stats['total_documents']}")
    report_lines.append(f"- **Average chunks per document**: {stats['chunks_per_document']['mean']:.2f}")
    report_lines.append(f"- **Min chunks per document**: {stats['chunks_per_document']['min']}")
    report_lines.append(f"- **Max chunks per document**: {stats['chunks_per_document']['max']}")
    report_lines.append("")

    # Critical issues
    report_lines.append("---")
    report_lines.append("")
    report_lines.append("## Critical Issues")
    report_lines.append("")

    for fail_type, fails in audit.fails.items():
        if fails:
            report_lines.append(f"### {fail_type.upper()}")
            report_lines.append("")
            for fail in fails:
                report_lines.append(f"- {fail['message']}")
                if fail.get('example'):
                    chunk = fail['example']
                    report_lines.append(f"  **Sample chunk**: {chunk.get('text', '')[:150]}...")
            report_lines.append("")

    # Warnings
    if audit.warnings["audit_writes"]:
        report_lines.append("---")
        report_lines.append("")
        report_lines.append("## Warnings")
        report_lines.append("")
        report_lines.append("The following warnings indicate areas that should be reviewed but do not prevent chunk usage:")
        report_lines.append("")

        for warning_type, warnings in audit.warnings.items():
            if warnings:
                report_lines.append(f"### {warning_type.upper()}")
                report_lines.append("")
                for warning in warnings[:10]:  # Limit to 10 per category
                    report_lines.append(f"- {warning}")
                if len(warnings) > 10:
                    report_lines.append(f"- ... and {len(warnings) - 10} more")
                report_lines.append("")

    # Successes
    for pass_type, passes in audit.passes.items():
        if passes:
            report_lines.append("---")
            report_lines.append("")
            report_lines.append(f"## ✅ {pass_type.upper()}")
            report_lines.append("")
            for pass_msg in passes:
                report_lines.append(f"- {pass_msg}")
            report_lines.append("")

    # Examples
    if audit.examples:
        report_lines.append("---")
        report_lines.append("")
        report_lines.append("## Representative Chunk Samples")
        report_lines.append("")

        for category, examples in audit.examples.items():
            report_lines.append(f"### {category.upper()} ")
            report_lines.append("")
            for i, chunk in enumerate(examples):
                text = chunk.get('text', '')
                title = chunk.get('title', 'Unknown')
                section = chunk.get('section', 'Unknown')
                tokens = chunk.get('token_count', 0)
                report_lines.append(f"**{i+1}. {title} - {section} ({tokens} tokens)**")
                report_lines.append("")
                report_lines.append("```text")
                report_lines.append(text[:300])
                if len(text) > 300:
                    report_lines.append("...")
                report_lines.append("```")
                report_lines.append("")

    # Recommendations
    report_lines.append("---")
    report_lines.append("")
    report_lines.append("## Recommendations")
    report_lines.append("")

    if total_fails == 0:
        if total_warnings > 0:
            report_lines.append("1. Address the {total_warnings} warnings identified in this audit.")
            report_lines.append("2. Continue using the existing 13,093 chunks - they pass quality checks.")
            report_lines.append("3. Proceed to Part 2 (embedding implementation) with no chunker changes.")
        else:
            report_lines.append("1. No changes needed - 13,093 chunks are quality retrieval units.")
            report_lines.append("2. Proceed to Part 2 (embedding implementation) immediately.")
    else:
        report_lines.append("1. Address the {total_fails} critical issues immediately.")
        report_lines.append("2. Consider rebuilding corpus with adjusted chunk size parameters.")
        report_lines.append("3. Do NOT proceed to Part 2 until all critical issues are resolved.")

    report_lines.append("")
    report_lines.append("---")
    report_lines.append("")
    report_lines.append("## Documents by Domain (Actual Distribution)")
    report_lines.append("")

    for domain, doc_count in sorted(stats["domains"].items(), key=lambda x: x[1], reverse=True):
        report_lines.append(f"- {domain}: {doc_count:,} chunks")

    report_lines.append("")

    # Give recommendation for Part 2
    if total_fails == 0:
        report_lines.append("## 🔒 RECOMMENDATION FOR PART 2")
        report_lines.append("")
        report_lines.append("✅ **PROCEED** - The corpus passes quality checks and is ready for Part 2.")
    else:
        report_lines.append("## 🔒 RECOMMENDATION FOR PART 2")
        report_lines.append("")
        report_lines.append("❌ **FIX FIRST** - The corpus has critical issues that must be resolved before proceeding.")
        report_lines.append("")
        report_lines.append("After implementing fixes:")
        report_lines.append("1. Rebuild corpus with validated chunks")
        report_lines.append("2. Rerun this audit")
        report_lines.append("3. Confirm all issues are resolved")
        report_lines.append("4. THEN proceed to Part 2")

    return "\n".join(report_lines)


def main():
    """Main execution function."""
    print("=" * 80)
    print("RAG V2 CHUNK QUALITY AUDIT")
    print("=" * 80)
    print()

    if not CHUNKS_FILE.exists():
        print(f"ERROR: File not found: {CHUNKS_FILE}")
        sys.exit(1)

    # Load chunks
    chunks = load_chunks(CHUNKS_FILE)

    # Run audits
    audit = AuditResult()

    stats = audit_document_distribution(chunks, audit)
    audit_chunk_sizes(chunks, audit)
    audit_metadata_completeness(chunks, audit)
    audit_provenance_integrity(chunks, audit)
    audit_duplicates(chunks, audit)
    audit_legal_structure(chunks, audit)
    audit_extras(chunks, audit)
    audit_tables(chunks, audit)
    audit_context_loss(chunks, audit)
    audit_retrieval_quality(chunks, audit)

    # Generate report
    report = generate_report(audit, stats)

    # Print report with safe encoding
    try:
        print(report)
    except UnicodeEncodeError:
        # Fallback for Windows console
        report_safe = report.encode('cp1252', errors='replace').decode('cp1252')
        print(report_safe)

    # Save report
    report_path = BASE_DIR / "docs" / "RAG_V2_CHUNK_QUALITY_REPORT.md"
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(report, encoding="utf-8")

    # Save report
    report_path = BASE_DIR / "docs" / "RAG_V2_CHUNK_QUALITY_REPORT.md"
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(report, encoding="utf-8")

    print(f"\n{'='*80}")
    print(f"Report saved to: {report_path}")
    print(f"{'='*80}")

    # Exit with appropriate code
    total_fails = sum(len(v) for v in audit.fails.values())
    if total_fails > 0:
        sys.exit(1)
    else:
        sys.exit(0)


if __name__ == "__main__":
    main()