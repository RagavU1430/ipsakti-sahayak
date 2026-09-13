"""
Stage-by-stage RAG latency breakdown for IP Sakthi.
- HTTP end-to-end via /api/v1/ask (real network path, includes backend translations? no: direct to RAG)
- In-process stage timing: analyze_query, hybrid retrieve, rerank, generation.

Server must already be running on 127.0.0.1:8000 (python run_service.py).
"""
import sys, os, time, statistics, httpx

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

QUESTIONS = [
    "What is required to register a trademark in India?",
    "How to file a patent application in India?",
    "What is a GI tag and how to get one?",
    "What is the difference between patent and copyright?",
    "How long does copyright protection last in India?",
    "What is prior art in patent law?",
    "Can a business name be trademarked?",
    "What is the renewal process for a trademark?",
    "What are the requirements for a design registration?",
    "How to protect a software invention?",
    "What is plant variety protection?",
    "What is fair use in copyright law?",
    "Who can apply for a patent in India?",
    "What is the validity period of a trademark?",
    "How do I register a partnership firm's IP?",
    "What is the procedure for copyright registration?",
]

BASE = "http://127.0.0.1:8000"


def http_ask(q: str):
    t0 = time.perf_counter()
    r = httpx.post(f"{BASE}/api/v1/ask", json={"question": q, "jurisdiction": "INDIA"}, timeout=90.0)
    ms = (time.perf_counter() - t0) * 1000
    try:
        d = r.json()
        conf = d.get("confidence")
        ev = len(d.get("evidence") or [])
        ans = (d.get("answer") or "")[:50]
        gen = d.get("generation_ms")
    except Exception:
        d, conf, ev, ans, gen = {}, "?", "?", "", None
    return r.status_code, ms, conf, ev, ans, gen


def main():
    from app.models.schemas import QueryRequest, Jurisdiction  # may differ
    from app.service import get_service
    from app.retrieval.query_analysis import analyze_query
    from app.retrieval.hybrid import HybridRetriever
    from app.retrieval.reranker import LegalFeatureReranker

    svc = get_service()
    print(f"config: provider={getattr(svc.settings, 'embedding_provider', '?')} emb_model={getattr(svc.settings, 'embedding_model', '?')} "
          f"candidate_k={svc.settings.candidate_k} top_k={svc.settings.top_k} cache_enabled={svc.settings.response_cache_enabled} "
          f"fast_extractive={svc.settings.fast_extractive_enabled} enable_llm={svc.settings.enable_llm}")

    # figure out how to build a QueryRequest
    try:
        from app.models.schemas import QueryRequest as QR
        import inspect
        sig = inspect.signature(QR)
        print("QueryRequest fields:", list(sig.parameters.keys()))
    except Exception as e:
        print("QueryRequest introspection failed:", e)

    # warmup
    try:
        http_ask("warmup trademark india")
        print("[warmup] done\n")
    except Exception as e:
        print("[warmup] failed:", e)

    rows = []
    for i, q in enumerate(QUESTIONS, 1):
        status, ms, conf, ev, ans, gen = http_ask(q)
        rows.append((q, ms, status, conf, ev, gen))
        print(f"[{i:2d}] HTTP {ms:7.0f} ms | conf={conf} ev={ev} | {q[:48]}")

    print()
    vals = [r[1] for r in rows]
    print("=== HTTP END-TO-END /api/v1/ask (ms) ===")
    print(f"n={len(vals)}  min={min(vals):.0f}  avg={statistics.mean(vals):.0f}  median={statistics.median(vals):.0f}  max={max(vals):.0f}")
    slow = [(q, ms) for q, ms, *_ in rows if ms > 2000]
    if slow:
        print("Slow (>2s):")
        for q, ms in slow:
            print(f"  {ms:7.0f} ms  {q}")

    print()
    print("=== IN-PROCESS STAGE BREAKDOWN (first 6) ===")
    print(f"{'question':44s} {'analyze':>8s} {'retrieve':>9s} {'rerank':>8s} {'gen':>8s}")
    # build QueryRequest similarly to how api does it
    from app.models.schemas import AskRequest
    try:
        req = AskRequest(question="test trademark", jurisdiction="INDIA")
        print("AskRequest ok:", req)
    except Exception as e:
        print("AskRequest build failed:", e)

    for q in QUESTIONS[:6]:
        try:
            # analyze
            t0 = time.perf_counter()
            analysis = analyze_query(QueryRequest(query=q, jurisdiction="INDIA"))
            t_an = (time.perf_counter() - t0) * 1000

            # retrieve
            t0 = time.perf_counter()
            candidates = svc.retriever.retrieve(analysis)
            t_rt = (time.perf_counter() - t0) * 1000
            t0 = time.perf_counter()
            evidence = svc.reranker.rerank(analysis, candidates, svc.settings.top_k)
            t_rr = (time.perf_counter() - t0) * 1000

            # generation: replicate fast/extractive decision
            from app.generation.grounded import ExtractiveGroundedGenerator
            from app.generation.context import assemble_context
            context, selected = assemble_context(evidence, svc.settings.max_context_chars)
            t0 = time.perf_counter()
            if svc._use_fast_extractive_path(analysis):
                g = ExtractiveGroundedGenerator().generate(analysis, context, selected)
            else:
                g = svc.generator.generate(analysis, context, selected)
            t_gen = (time.perf_counter() - t0) * 1000

            print(f"{q[:44]:44s} {t_an:8.0f} {t_rt:9.0f} {t_rr:8.0f} {t_gen:8.0f}")
        except Exception as e:
            print(f"{q[:44]:44s} breakdown error: {e}")


if __name__ == "__main__":
    main()