from __future__ import annotations
import re

HEADING = re.compile(r"^(?P<kind>CHAPTER|PART|SECTION|Section|Rule|RULE|Regulation|Article|ARTICLE|SCHEDULE|ANNEX(?:URE)?|APPENDIX)\s+(?P<number>[A-Z0-9IVXLCDM().-]+)\b", re.MULTILINE)

def structural_units(pages: list[dict]) -> list[dict]:
    units, current = [], None
    for page in pages:
        text = page["text"]
        matches = list(HEADING.finditer(text))
        if not matches:
            if current: current["text"] += "\n" + text; current["page_end"] = page["page"]
            elif text.strip(): current = {"kind": "PREAMBLE", "number": "", "page_start": page["page"], "page_end": page["page"], "text": text}
            continue
        cursor = 0
        for match in matches:
            prefix = text[cursor:match.start()].strip()
            if prefix:
                if current: current["text"] += "\n" + prefix
                else: current = {"kind":"PREAMBLE","number":"","page_start":page["page"],"page_end":page["page"],"text":prefix}
            if current: units.append(current)
            current = {"kind":match.group("kind").upper(),"number":match.group("number"),"page_start":page["page"],"page_end":page["page"],"text":""}
            cursor = match.start()
        current["text"] = text[cursor:].strip()
    if current: units.append(current)
    return [u for u in units if u["text"].strip()]

def chunks(units: list[dict], max_words: int = 900) -> list[dict]:
    output = []
    for unit in units:
        paragraphs = [p.strip() for p in unit["text"].split("\n\n") if p.strip()]
        buf = []
        for paragraph in paragraphs:
            if buf and len(" ".join(buf + [paragraph]).split()) > max_words:
                output.append({**unit, "text":"\n\n".join(buf)}); buf = []
            buf.append(paragraph)
        if buf: output.append({**unit, "text":"\n\n".join(buf)})
    return output

