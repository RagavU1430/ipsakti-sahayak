from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

def project_path(value: str) -> Path:
    """Resolve a registry path while preventing writes outside this project."""
    path = (ROOT / value).resolve()
    if path != ROOT and ROOT not in path.parents:
        raise ValueError(f"Path escapes project: {value}")
    return path

