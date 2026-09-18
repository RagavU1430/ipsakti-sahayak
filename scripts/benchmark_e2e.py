#!/usr/bin/env python3
"""
IP-SAKTI Sahayak — End-to-End Performance Benchmark
====================================================

Exercises the REAL production API endpoints:
  - POST /api/v1/questions        (direct question)
  - POST /api/v1/conversations    (create conversation)
  - POST /api/v1/conversations/{id}/messages  (ask in conversation)

Collects:
  - Request start → response received timing (client-side)
  - Server-Timing breakdown from response headers
  - X-IPSAKTI-Provider, X-IPSAKTI-Route, X-IPSAKTI-Chunks, X-IPSAKTI-Backend-Total-Ms

Produces:
  docs/END_TO_END_PERFORMANCE_BASELINE.md
"""

from __future__ import annotations

import json
import os
import re
import statistics
import sys
import time
import uuid
from dataclasses import dataclass, field, asdict
from pathlib import Path

# Force utf-8 for Windows console
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")


# Ensure we can import from parent context
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

try:
    import requests
except ImportError:
    print("ERROR: 'requests' library is required. Install with: pip install requests")
    sys.exit(1)


# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

BACKEND_URL = os.getenv("BENCHMARK_BACKEND_URL", "http://localhost:8080")
DEV_USER_ID = os.getenv("BENCHMARK_DEV_USER_ID", "benchmark-user")
RUNS_PER_QUERY = int(os.getenv("BENCHMARK_RUNS", "3"))
REPORT_PATH = Path(__file__).resolve().parents[1] / "docs" / "END_TO_END_PERFORMANCE_BASELINE.md"

GENERAL_QUERIES = [
    ("G1", "Hello, who are you?"),
    ("G2", "What is Python?"),
    ("G3", "Tell me a joke"),
    ("G4", "What is machine learning?"),
    ("G5", "Write a simple email"),
]

RAG_QUERIES = [
    ("R1", "What is Section 3(p) of the Patents Act?"),
    ("R2", "Explain Access and Benefit Sharing compliance in India."),
    ("R3", "What are the requirements for trademark registration in India?"),
    ("R4", "What is the role of TKDL?"),
    ("R5", "Explain the Biological Diversity Act 2002"),
    ("R6", "What is Section 3(e) of the Patents Act?"),
]


# ---------------------------------------------------------------------------
# Data model
# ---------------------------------------------------------------------------

@dataclass
class TimingResult:
    query_id: str
    query: str
    run: int
    endpoint: str
    request_id: str = ""
    route: str = "UNKNOWN"
    provider: str = "unknown"
    rag_used: bool = False
    chunks: int = 0
    # Client-side
    request_ms: float = 0.0
    # Server-reported (from headers)
    routing_ms: float = 0.0
    retrieval_ms: float = 0.0
    llm_ms: float = 0.0
    history_save_ms: float = 0.0
    backend_total_ms: float = 0.0
    # Derived
    network_ms: float = 0.0
    http_status: int = 0
    error: str = ""
    answer_snippet: str = ""


@dataclass
class AggregateStats:
    label: str
    count: int = 0
    min_ms: float = 0.0
    max_ms: float = 0.0
    avg_ms: float = 0.0
    median_ms: float = 0.0
    p95_ms: float = 0.0


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def parse_server_timing(header: str | None) -> dict[str, float]:
    """Parse Server-Timing header into {name: duration_ms} dict."""
    result = {}
    if not header:
        return result
    for part in header.split(","):
        part = part.strip()
        name_match = re.match(r"^([a-zA-Z_][a-zA-Z0-9_]*)", part)
        dur_match = re.search(r"dur=([\d.]+)", part)
        if name_match and dur_match:
            result[name_match.group(1)] = float(dur_match.group(1))
    return result


def safe_float(val: str | None) -> float:
    if val is None:
        return 0.0
    try:
        return float(val)
    except ValueError:
        return 0.0


def safe_int(val: str | None) -> int:
    if val is None:
        return 0
    try:
        return int(val)
    except ValueError:
        return 0


def compute_stats(label: str, values: list[float]) -> AggregateStats:
    if not values:
        return AggregateStats(label=label)
    sorted_vals = sorted(values)
    p95_idx = max(0, int(len(sorted_vals) * 0.95) - 1)
    return AggregateStats(
        label=label,
        count=len(values),
        min_ms=min(values),
        max_ms=max(values),
        avg_ms=statistics.mean(values),
        median_ms=statistics.median(values),
        p95_ms=sorted_vals[p95_idx],
    )


def classify_bottleneck(results: list[TimingResult]) -> tuple[str, str]:
    """Classify the primary bottleneck based on measured data."""
    if not results:
        return "UNKNOWN", "No data"

    avg_llm = statistics.mean([r.llm_ms for r in results if r.llm_ms > 0] or [0])
    avg_rag = statistics.mean([r.retrieval_ms for r in results if r.retrieval_ms > 0] or [0])
    avg_network = statistics.mean([r.network_ms for r in results if r.network_ms > 0] or [0])
    avg_history = statistics.mean([r.history_save_ms for r in results if r.history_save_ms > 0] or [0])
    avg_backend = statistics.mean([r.backend_total_ms for r in results if r.backend_total_ms > 0] or [0])
    avg_routing = statistics.mean([r.routing_ms for r in results if r.routing_ms > 0] or [0])

    components = {
        "LLM_API": avg_llm,
        "NETWORK": avg_network,
        "RAG": avg_rag,
        "HISTORY": avg_history,
        "BACKEND": avg_backend - avg_llm - avg_rag - avg_history - avg_routing,  # overhead
        "ROUTER": avg_routing,
    }

    # The bottleneck is the largest measured contributor
    bottleneck = max(components, key=components.get)
    detail = ", ".join(f"{k}={v:.0f}ms" for k, v in sorted(components.items(), key=lambda x: -x[1]))
    return bottleneck, detail


# ---------------------------------------------------------------------------
# API callers
# ---------------------------------------------------------------------------

def check_health() -> bool:
    try:
        resp = requests.get(f"{BACKEND_URL}/health", timeout=5)
        return resp.status_code == 200
    except Exception:
        return False


def create_conversation() -> str | None:
    """Create a test conversation, return ID."""
    try:
        resp = requests.post(
            f"{BACKEND_URL}/api/v1/conversations",
            json={"title": f"Benchmark {time.strftime('%H:%M:%S')}"},
            headers={"X-Dev-User-Id": DEV_USER_ID, "Content-Type": "application/json"},
            timeout=10,
        )
        if resp.status_code == 201:
            return resp.json().get("id")
    except Exception as e:
        print(f"  ⚠ Could not create conversation: {e}")
    return None


def run_query_direct(query_id: str, query: str, run: int) -> TimingResult:
    """POST /api/v1/questions"""
    request_id = str(uuid.uuid4())
    result = TimingResult(query_id=query_id, query=query, run=run, endpoint="/api/v1/questions")

    start = time.perf_counter()
    try:
        resp = requests.post(
            f"{BACKEND_URL}/api/v1/questions",
            json={"question": query, "jurisdiction": "INDIA", "language": "en"},
            headers={
                "X-Dev-User-Id": DEV_USER_ID,
                "X-Request-ID": request_id,
                "Content-Type": "application/json",
                "Accept": "application/json",
            },
            timeout=120,
        )
        elapsed = (time.perf_counter() - start) * 1000
        result.request_ms = elapsed
        result.http_status = resp.status_code

        if resp.status_code == 200:
            _extract_headers(result, resp, request_id)
            body = resp.json()
            result.answer_snippet = (body.get("answer") or "")[:80]
        else:
            result.error = f"HTTP {resp.status_code}"

    except Exception as e:
        result.request_ms = (time.perf_counter() - start) * 1000
        result.error = str(e)

    return result


def run_query_conversation(conv_id: str, query_id: str, query: str, run: int) -> TimingResult:
    """POST /api/v1/conversations/{id}/messages"""
    request_id = str(uuid.uuid4())
    result = TimingResult(query_id=query_id, query=query, run=run, endpoint=f"/api/v1/conversations/{conv_id[:8]}../messages")

    start = time.perf_counter()
    try:
        resp = requests.post(
            f"{BACKEND_URL}/api/v1/conversations/{conv_id}/messages",
            json={"question": query, "jurisdiction": "INDIA", "language": "en"},
            headers={
                "X-Dev-User-Id": DEV_USER_ID,
                "X-Request-ID": request_id,
                "Content-Type": "application/json",
                "Accept": "application/json",
            },
            timeout=120,
        )
        elapsed = (time.perf_counter() - start) * 1000
        result.request_ms = elapsed
        result.http_status = resp.status_code

        if resp.status_code == 200:
            _extract_headers(result, resp, request_id)
            body = resp.json()
            result.answer_snippet = (body.get("answer") or "")[:80]
        else:
            result.error = f"HTTP {resp.status_code}"

    except Exception as e:
        result.request_ms = (time.perf_counter() - start) * 1000
        result.error = str(e)

    return result


def _extract_headers(result: TimingResult, resp: requests.Response, sent_request_id: str):
    """Extract timing/metadata from response headers."""
    result.request_id = resp.headers.get("X-Request-ID", sent_request_id)
    result.route = resp.headers.get("X-IPSAKTI-Route", "UNKNOWN")
    result.provider = resp.headers.get("X-IPSAKTI-Provider", "unknown")
    result.chunks = safe_int(resp.headers.get("X-IPSAKTI-Chunks"))
    result.backend_total_ms = safe_float(resp.headers.get("X-IPSAKTI-Backend-Total-Ms"))
    result.rag_used = result.route in ("DOMAIN_RAG", "RAG")

    timing = parse_server_timing(resp.headers.get("Server-Timing"))
    result.routing_ms = timing.get("route", 0.0)
    result.retrieval_ms = timing.get("retrieval", timing.get("rag", 0.0))
    result.llm_ms = timing.get("llm", 0.0)
    result.history_save_ms = timing.get("history_save", timing.get("history_read_write", 0.0))

    if result.backend_total_ms == 0 and "total" in timing:
        result.backend_total_ms = timing["total"]

    result.network_ms = max(0, result.request_ms - result.backend_total_ms) if result.backend_total_ms > 0 else 0


# ---------------------------------------------------------------------------
# Report generation
# ---------------------------------------------------------------------------

def generate_report(results: list[TimingResult]) -> str:
    timestamp = time.strftime("%Y-%m-%d %H:%M:%S %Z")
    general_results = [r for r in results if not r.rag_used and r.http_status == 200]
    rag_results = [r for r in results if r.rag_used and r.http_status == 200]
    all_ok = [r for r in results if r.http_status == 200]
    failed = [r for r in results if r.http_status != 200]

    # Compute per-stage stats
    gen_e2e = compute_stats("GENERAL E2E", [r.request_ms for r in general_results])
    rag_e2e = compute_stats("RAG E2E", [r.request_ms for r in rag_results])
    all_e2e = compute_stats("ALL E2E", [r.request_ms for r in all_ok])

    gen_backend = compute_stats("GENERAL Backend", [r.backend_total_ms for r in general_results])
    rag_backend = compute_stats("RAG Backend", [r.backend_total_ms for r in rag_results])

    gen_llm = compute_stats("GENERAL LLM", [r.llm_ms for r in general_results if r.llm_ms > 0])
    rag_llm = compute_stats("RAG LLM", [r.llm_ms for r in rag_results if r.llm_ms > 0])

    gen_network = compute_stats("GENERAL Network", [r.network_ms for r in general_results if r.network_ms > 0])
    rag_network = compute_stats("RAG Network", [r.network_ms for r in rag_results if r.network_ms > 0])

    rag_retrieval = compute_stats("RAG Retrieval", [r.retrieval_ms for r in rag_results if r.retrieval_ms > 0])

    # Bottleneck
    bottleneck, bottleneck_detail = classify_bottleneck(all_ok)

    # Provider summary
    providers = set(r.provider for r in all_ok if r.provider != "unknown")
    routes = set(r.route for r in all_ok)

    lines = []
    lines.append("# End-to-End Performance Baseline")
    lines.append("")
    lines.append(f"**Generated:** {timestamp}")
    lines.append(f"**Backend URL:** `{BACKEND_URL}`")
    lines.append(f"**Runs per query:** {RUNS_PER_QUERY}")
    lines.append(f"**Total queries:** {len(results)} ({len(general_results)} GENERAL OK, {len(rag_results)} RAG OK, {len(failed)} failed)")
    lines.append(f"**Providers observed:** {', '.join(providers) or 'none detected'}")
    lines.append(f"**Routes observed:** {', '.join(routes) or 'none detected'}")
    lines.append("")

    lines.append("## Summary Statistics")
    lines.append("")
    lines.append("| Metric | Min | Max | Avg | Median | P95 |")
    lines.append("|--------|-----|-----|-----|--------|-----|")
    for stat in [gen_e2e, rag_e2e, all_e2e, gen_backend, rag_backend, gen_llm, rag_llm, gen_network, rag_network, rag_retrieval]:
        if stat.count > 0:
            lines.append(f"| {stat.label} (n={stat.count}) | {stat.min_ms:.0f} ms | {stat.max_ms:.0f} ms | {stat.avg_ms:.0f} ms | {stat.median_ms:.0f} ms | {stat.p95_ms:.0f} ms |")
    lines.append("")

    lines.append("## Bottleneck Classification")
    lines.append("")
    lines.append(f"**PRIMARY BOTTLENECK:** `{bottleneck}`")
    lines.append("")
    lines.append(f"**Component breakdown:** {bottleneck_detail}")
    lines.append("")

    # Optimization recommendation
    lines.append("## Optimization Recommendation")
    lines.append("")
    if bottleneck == "LLM_API":
        lines.append("The LLM API call is the dominant latency contributor. Consider:")
        lines.append("- Expanding fast-extractive path coverage (avoids remote LLM entirely)")
        lines.append("- Using a faster model (e.g., Gemini Flash vs larger models)")
        lines.append("- Enabling response caching for repeated queries")
        lines.append("- Reducing prompt/context size")
    elif bottleneck == "NETWORK":
        lines.append("Network transport is the dominant latency contributor. Consider:")
        lines.append("- Co-locating backend and RAG services")
        lines.append("- Checking for proxy/firewall overhead")
        lines.append("- Verifying connection pooling/keep-alive")
    elif bottleneck == "RAG":
        lines.append("RAG retrieval is the dominant latency contributor. Consider:")
        lines.append("- Reducing candidate_k")
        lines.append("- Optimizing vector search index")
        lines.append("- Caching embeddings")
    elif bottleneck == "HISTORY":
        lines.append("Database history operations are the dominant latency contributor. Consider:")
        lines.append("- Making history save asynchronous (fire-and-forget after response)")
        lines.append("- Batching citation/source persistence")
    else:
        lines.append(f"The `{bottleneck}` component dominates. Investigate further.")
    lines.append("")

    lines.append("## Per-Query Results")
    lines.append("")
    lines.append("| Query | Route | Provider | RAG | Chunks | Routing | Retrieval | LLM | History | Backend | Network | E2E | Status |")
    lines.append("|-------|-------|----------|-----|--------|---------|-----------|-----|---------|---------|---------|-----|--------|")
    for r in results:
        status = "✅" if r.http_status == 200 else f"❌ {r.error}"
        lines.append(
            f"| {r.query_id} R{r.run} | {r.route} | {r.provider} | {'Yes' if r.rag_used else 'No'} "
            f"| {r.chunks} | {r.routing_ms:.0f} | {r.retrieval_ms:.0f} | {r.llm_ms:.0f} "
            f"| {r.history_save_ms:.0f} | {r.backend_total_ms:.0f} | {r.network_ms:.0f} "
            f"| **{r.request_ms:.0f}** | {status} |"
        )
    lines.append("")

    if failed:
        lines.append("## Failed Queries")
        lines.append("")
        for r in failed:
            lines.append(f"- **{r.query_id}** R{r.run}: `{r.query}` → {r.error}")
        lines.append("")

    lines.append("## Final Summary")
    lines.append("")
    lines.append("```")
    lines.append(f"Average GENERAL E2E:      {gen_e2e.avg_ms:.0f} ms")
    lines.append(f"Average RAG E2E:          {rag_e2e.avg_ms:.0f} ms")
    lines.append(f"Median GENERAL E2E:       {gen_e2e.median_ms:.0f} ms")
    lines.append(f"Median RAG E2E:           {rag_e2e.median_ms:.0f} ms")
    lines.append(f"P95 GENERAL E2E:          {gen_e2e.p95_ms:.0f} ms")
    lines.append(f"P95 RAG E2E:              {rag_e2e.p95_ms:.0f} ms")
    lines.append(f"Average Backend:          {compute_stats('', [r.backend_total_ms for r in all_ok]).avg_ms:.0f} ms")
    lines.append(f"Average LLM:              {compute_stats('', [r.llm_ms for r in all_ok if r.llm_ms > 0]).avg_ms:.0f} ms")
    lines.append(f"Average RAG Retrieval:    {rag_retrieval.avg_ms:.0f} ms")
    lines.append(f"Average Network:          {compute_stats('', [r.network_ms for r in all_ok if r.network_ms > 0]).avg_ms:.0f} ms")
    lines.append(f"PRIMARY BOTTLENECK:       {bottleneck}")
    lines.append(f"TESTS:                    {len(all_ok)}/{len(results)} PASS")
    lines.append("```")
    lines.append("")

    lines.append("---")
    lines.append(f"*Report generated by `scripts/benchmark_e2e.py` at {timestamp}*")
    lines.append("")

    return "\n".join(lines)


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    print("=" * 60)
    print("IP-SAKTI Sahayak — End-to-End Performance Benchmark")
    print("=" * 60)
    print(f"Backend:        {BACKEND_URL}")
    print(f"Runs per query: {RUNS_PER_QUERY}")
    print(f"Report:         {REPORT_PATH}")
    print()

    # Step 1: Health check
    print("Step 1: Health check...")
    if not check_health():
        print(f"  ❌ Backend at {BACKEND_URL} is not reachable.")
        print("  Please start all services first (backend on :8080, RAG on :8000).")
        sys.exit(1)
    print("  ✅ Backend is healthy")
    print()

    # Step 2: Create a conversation for conversation-based queries
    print("Step 2: Creating test conversation...")
    conv_id = create_conversation()
    if conv_id:
        print(f"  ✅ Conversation created: {conv_id[:8]}...")
    else:
        print("  ⚠ Conversation creation failed; will use direct /api/v1/questions only")
    print()

    results: list[TimingResult] = []

    # Step 3: Run GENERAL queries
    print("Step 3: Running GENERAL queries...")
    for qid, query in GENERAL_QUERIES:
        for run in range(1, RUNS_PER_QUERY + 1):
            print(f"  {qid} run {run}: {query[:50]}... ", end="", flush=True)
            if conv_id:
                result = run_query_conversation(conv_id, qid, query, run)
            else:
                result = run_query_direct(qid, query, run)
            results.append(result)
            status = f"✅ {result.request_ms:.0f}ms route={result.route} provider={result.provider}" if result.http_status == 200 else f"❌ {result.error}"
            print(status)
            time.sleep(0.5)  # Brief delay between queries to avoid rate limiting
    print()

    # Step 4: Create a fresh conversation for RAG queries
    if conv_id:
        conv_id_rag = create_conversation()
        if not conv_id_rag:
            conv_id_rag = conv_id
    else:
        conv_id_rag = None

    # Step 5: Run RAG queries
    print("Step 4: Running RAG queries...")
    for qid, query in RAG_QUERIES:
        for run in range(1, RUNS_PER_QUERY + 1):
            print(f"  {qid} run {run}: {query[:50]}... ", end="", flush=True)
            if conv_id_rag:
                result = run_query_conversation(conv_id_rag, qid, query, run)
            else:
                result = run_query_direct(qid, query, run)
            results.append(result)
            status = f"✅ {result.request_ms:.0f}ms route={result.route} provider={result.provider}" if result.http_status == 200 else f"❌ {result.error}"
            print(status)
            time.sleep(0.5)
    print()

    # Step 6: Generate report
    print("Step 5: Generating performance report...")
    report = generate_report(results)
    REPORT_PATH.parent.mkdir(parents=True, exist_ok=True)
    REPORT_PATH.write_text(report, encoding="utf-8")
    print(f"  ✅ Report written to: {REPORT_PATH}")
    print()

    # Step 7: Print summary
    all_ok = [r for r in results if r.http_status == 200]
    general_ok = [r for r in all_ok if not r.rag_used]
    rag_ok = [r for r in all_ok if r.rag_used]

    print("=" * 60)
    print("SUMMARY")
    print("=" * 60)
    if general_ok:
        gen_avg = statistics.mean([r.request_ms for r in general_ok])
        print(f"  GENERAL avg E2E:  {gen_avg:.0f} ms  (n={len(general_ok)})")
    if rag_ok:
        rag_avg = statistics.mean([r.request_ms for r in rag_ok])
        print(f"  RAG avg E2E:      {rag_avg:.0f} ms  (n={len(rag_ok)})")

    bottleneck, detail = classify_bottleneck(all_ok)
    print(f"  BOTTLENECK:       {bottleneck}")
    print(f"  BREAKDOWN:        {detail}")
    print(f"  PASS RATE:        {len(all_ok)}/{len(results)}")
    print()


if __name__ == "__main__":
    main()
