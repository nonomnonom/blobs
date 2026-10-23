"""The bounded document contract shared by extraction and package building."""

import gzip
import json
from pathlib import Path
import re

LIMITS = dict(
    id=100,
    title=300,
    publisher=300,
    url=2000,
    license=4000,
    updated=80,
    saved=80,
    body=1_000_000,
    collection=200,
)
MAX_PROVENANCE_JSON_CHARACTERS = 20_000


def valid_utf8_text(value: str) -> bool:
    try:
        value.encode("utf-8", "strict")
    except UnicodeError:
        return False
    return True


def provenance_json(item: dict) -> str:
    """Return the one bounded JSON object stored with a document's provenance."""
    provenance = item.get("provenance", {})
    if not isinstance(provenance, dict):
        raise ValueError("Invalid provenance: must be a JSON object")
    try:
        encoded = json.dumps(
            provenance,
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
            allow_nan=False,
        )
    except (TypeError, ValueError) as error:
        raise ValueError("Invalid provenance: must be JSON-serializable") from error
    if (
        len(encoded) > MAX_PROVENANCE_JSON_CHARACTERS
        or "\x00" in encoded
        or "\ufffd" in encoded
        or not valid_utf8_text(encoded)
    ):
        raise ValueError("Invalid provenance: exceeds the package text contract")
    return encoded


def validate_document(item: dict):
    for field, limit in LIMITS.items():
        value = item[field]
        if (
            not isinstance(value, str)
            or not value.strip()
            or len(value) > limit
            or "\x00" in value
            or not valid_utf8_text(value)
        ):
            raise ValueError(
                f"Invalid {field}: must be nonempty text of at most {limit} characters"
            )
        if field != "body" and "\ufffd" in value:
            raise ValueError(f"Invalid {field}: contains a Unicode replacement character")
    if not re.fullmatch(r"[a-zA-Z0-9_-]{1,100}", item["id"]):
        raise ValueError("Invalid source ID")
    provenance_json(item)


def documents(source: Path):
    if source.name == "manifest.json":
        from .extract import verified_shards

        manifest = verified_shards(source)
        if not manifest["complete"]:
            raise ValueError(
                "Extraction is incomplete; finish it before building a release library"
            )
        sources = [source.parent / row["files"]["documents"]["name"] for row in manifest["shards"]]
    else:
        sources = [source]
    for path in sources:
        opener = gzip.open if path.suffix == ".gz" else open
        with opener(path, "rt", encoding="utf-8") as lines:
            for number, line in enumerate(lines, 1):
                try:
                    item = json.loads(line)
                    validate_document(item)
                    yield item
                except (KeyError, ValueError, TypeError) as error:
                    raise ValueError(f"{path.name}:{number}: {error}") from error
