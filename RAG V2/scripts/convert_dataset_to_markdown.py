"""Convert every source file in RAG V2/dataset into a Markdown training corpus."""
from __future__ import annotations

import argparse
import html
import json
import re
from datetime import datetime, timezone
from html.parser import HTMLParser
from pathlib import Path

from pypdf import PdfReader


class TextHTMLParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.parts: list[str] = []
        self._skip_depth = 0

    def handle_starttag(self, tag: str, attrs) -> None:
        if tag in {"script", "style", "noscript", "svg", "nav", "header", "footer"}:
            self._skip_depth += 1
        elif tag in {"p", "div", "section", "article", "li", "br", "h1", "h2", "h3", "h4", "tr"} and not self._skip_depth:
            self.parts.append("\n")

    def handle_endtag(self, tag: str) -> None:
        if tag in {"script", "style", "noscript", "svg", "nav", "header", "footer"} and self._skip_depth:
            self._skip_depth -= 1

    def handle_data(self, data: str) -> None:
        if not self._skip_depth:
            self.parts.append(data)


def clean_text(value: str) -> str:
    value = html.unescape(value).replace("\x00", "")
    value = re.sub(r"[ \t]+", " ", value)
    value = re.sub(r"\n[ \t]*\n[ \t]*\n+", "\n\n", value)
    return value.strip()


def markdown_for(source: Path, relative: Path) -> tuple[str, dict]:
    suffix = source.suffix.lower()
    metadata = {"source": relative.as_posix(), "source_type": suffix.lstrip(".") or "extensionless"}
    if suffix == ".pdf":
        reader = PdfReader(str(source))
        pages = []
        for number, page in enumerate(reader.pages, start=1):
            text = clean_text(page.extract_text() or "")
            pages.append(f"## Page {number}\n\n{text or '[No extractable text found on this page.]'}")
        metadata["page_count"] = len(reader.pages)
        body = "\n\n".join(pages)
    elif suffix in {".html", ".htm"}:
        parser = TextHTMLParser()
        parser.feed(source.read_text(encoding="utf-8", errors="replace"))
        body = clean_text("".join(parser.parts))
    elif suffix == ".json":
        parsed = json.loads(source.read_text(encoding="utf-8", errors="replace"))
        body = "```json\n" + json.dumps(parsed, ensure_ascii=False, indent=2) + "\n```"
    else:
        raw = source.read_text(encoding="utf-8", errors="replace")
        body = clean_text(raw) or "[Empty source file.]"
    frontmatter = "---\n" + "\n".join(f"{key}: {json.dumps(value, ensure_ascii=False)}" for key, value in metadata.items()) + "\n---"
    title = source.stem.replace("_", " ").replace("-", " ").title()
    return f"{frontmatter}\n\n# {title}\n\n{body}\n", metadata


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, default=Path(__file__).resolve().parents[1] / "dataset")
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[1] / "dataset_markdown")
    args = parser.parse_args()
    input_dir, output_dir = args.input.resolve(), args.output.resolve()
    if output_dir == input_dir or input_dir in output_dir.parents:
        raise ValueError("output must be outside the input folder")
    output_dir.mkdir(parents=True, exist_ok=True)
    report = {"created_at": datetime.now(timezone.utc).isoformat(), "input": str(input_dir), "output": str(output_dir), "converted": [], "failed": []}
    for source in sorted(path for path in input_dir.rglob("*") if path.is_file() and output_dir not in path.parents):
        relative = source.relative_to(input_dir)
        destination = output_dir / relative.with_suffix(relative.suffix + ".md")
        try:
            content, metadata = markdown_for(source, relative)
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_text(content, encoding="utf-8")
            report["converted"].append({"source": relative.as_posix(), "markdown": destination.relative_to(output_dir).as_posix(), **metadata})
        except Exception as exc:
            report["failed"].append({"source": relative.as_posix(), "error": f"{type(exc).__name__}: {exc}"})
    (output_dir / "conversion_manifest.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"converted": len(report["converted"]), "failed": len(report["failed"]), "output": str(output_dir)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
