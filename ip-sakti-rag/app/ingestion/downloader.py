from __future__ import annotations

import hashlib, json, time
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urljoin
import httpx
from bs4 import BeautifulSoup

UA = "IP-SAKTI-Sahayak/0.1 (authoritative legal corpus; research contact: repository maintainers)"

def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""): digest.update(block)
    return digest.hexdigest()

def looks_like_pdf(data: bytes) -> bool: return data.startswith(b"%PDF-")

def discover_pdf(page_url: str, html: str) -> str | None:
    soup = BeautifulSoup(html, "html.parser")
    candidates = []
    for link in soup.select("a[href]"):
        href = urljoin(page_url, link.get("href", ""))
        label = link.get_text(" ", strip=True).lower()
        if ".pdf" in href.lower() or "showfile" in href.lower() or "download" in label:
            candidates.append(href)
    return candidates[0] if candidates else None

def download(url: str, destination: Path, page_url: str = "") -> dict:
    destination.parent.mkdir(parents=True, exist_ok=True)
    with httpx.Client(headers={"User-Agent": UA}, follow_redirects=True, timeout=45) as client:
        last_error = None
        for attempt in range(3):
            try:
                response = client.get(url)
                response.raise_for_status()
                data = response.content
                content_type = response.headers.get("content-type", "").lower()
                if "html" in content_type and not destination.suffix.lower() == ".html":
                    found = discover_pdf(str(response.url), response.text)
                    if found and found != str(response.url):
                        response = client.get(found); response.raise_for_status()
                        data = response.content; content_type = response.headers.get("content-type", "").lower()
                if destination.suffix.lower() == ".pdf" and not looks_like_pdf(data):
                    raise ValueError(f"Expected PDF but received {content_type or 'unknown content type'}")
                if not data: raise ValueError("Empty response")
                if destination.exists() and sha256_file(destination) != hashlib.sha256(data).hexdigest():
                    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
                    destination.rename(destination.with_name(f"{destination.stem}.{stamp}{destination.suffix}"))
                destination.write_bytes(data)
                metadata = {"requested_url": url, "final_url": str(response.url), "source_page_url": page_url, "content_type": content_type, "etag": response.headers.get("etag"), "last_modified": response.headers.get("last-modified"), "retrieved_at": datetime.now(timezone.utc).isoformat(), "sha256": sha256_file(destination), "size_bytes": destination.stat().st_size}
                destination.with_suffix(destination.suffix + ".headers.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
                return metadata
            except (httpx.HTTPError, ValueError) as exc:
                last_error = exc
                if attempt < 2: time.sleep(2 ** attempt)
        raise RuntimeError(str(last_error))
