"""Turn a reviewed OpenStreetMap place snapshot into bounded Blobs records.

The source snapshot is deliberately separate from this extractor. A reviewer
captures a small Overpass JSON response, records its byte digest and query in a
lock, then this module converts only that exact response. The Android app never
contacts Overpass or interprets map data directly.
"""

from __future__ import annotations

import argparse
from datetime import date
import hashlib
import json
import os
from pathlib import Path
import re
from typing import Any
import urllib.parse
import urllib.request

from .extract import write_lines
from .records import validate_document


MAX_SNAPSHOT_BYTES = 5_000_000
MAX_ELEMENTS = 1_000
_IDENTITY = re.compile(r"[a-z0-9-]{1,64}")
_EDITION = re.compile(r"[0-9A-Za-z.-]{1,40}")
_SHA256 = re.compile(r"[a-f0-9]{64}")
_OSM_TIME = re.compile(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z")
_OSM_TYPES = {"node", "way", "relation"}
_AMENITIES = {"restaurant", "cafe", "fast_food"}
_VEGAN_VALUES = {"yes", "only"}
_CAPTURE_RECEIPT_FORMAT = 1


def _strict_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    value: dict[str, Any] = {}
    for key, item in pairs:
        if key in value:
            raise ValueError(f"Duplicate JSON key: {key}")
        value[key] = item
    return value


def _read_json(path: Path) -> dict[str, Any]:
    raw = path.read_bytes()
    try:
        value = json.loads(raw.decode("utf-8", "strict"), object_pairs_hook=_strict_object)
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ValueError(f"Invalid UTF-8 JSON: {path}") from error
    if not isinstance(value, dict):
        raise ValueError(f"Expected JSON object: {path}")
    return value


def _write_exclusive_json(path: Path, value: dict[str, Any]) -> None:
    """Write one JSON artifact without replacing an earlier capture or lock."""
    if path.exists():
        raise FileExistsError(path)
    partial = path.with_name(path.name + ".partial")
    if partial.exists():
        raise FileExistsError(partial)
    try:
        with partial.open("x", encoding="utf-8", newline="\n") as stream:
            json.dump(value, stream, ensure_ascii=False, sort_keys=True, indent=2)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.link(partial, path)
    finally:
        partial.unlink(missing_ok=True)


def _required_text(value: object, label: str, limit: int) -> str:
    if not isinstance(value, str) or not value.strip() or len(value) > limit:
        raise ValueError(f"Invalid {label}")
    if "\x00" in value or "\ufffd" in value:
        raise ValueError(f"Invalid {label}")
    try:
        value.encode("utf-8", "strict")
    except UnicodeError as error:
        raise ValueError(f"Invalid {label}") from error
    return value


def _iso_date(value: object, label: str) -> str:
    value = _required_text(value, label, 80)
    try:
        if date.fromisoformat(value).isoformat() != value:
            raise ValueError
    except ValueError as error:
        raise ValueError(f"Invalid {label}") from error
    return value


def _https_url(value: object, label: str) -> str:
    value = _required_text(value, label, 2_000)
    parsed = urllib.parse.urlsplit(value)
    if (
        parsed.scheme != "https"
        or not parsed.hostname
        or parsed.username
        or parsed.password
        or parsed.fragment
    ):
        raise ValueError(f"Invalid {label}")
    return value


class _SameHostRedirect(urllib.request.HTTPRedirectHandler):
    def __init__(self, host: str):
        self._host = host

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        destination = urllib.parse.urlsplit(newurl)
        if destination.scheme != "https" or destination.hostname != self._host:
            raise ValueError("Place snapshot redirect changes source host")
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def _validated_snapshot_source(value: object) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ValueError("Missing place snapshot source")
    _https_url(value.get("endpoint"), "snapshot endpoint")
    query = _required_text(value.get("query"), "snapshot query", 20_000)
    query_digest = value.get("query_sha256")
    if not isinstance(query_digest, str) or not _SHA256.fullmatch(query_digest):
        raise ValueError("Invalid snapshot query digest")
    if hashlib.sha256(query.encode("utf-8")).hexdigest() != query_digest:
        raise ValueError("Snapshot query digest differs from query")
    snapshot_digest = value.get("snapshot_sha256")
    if not isinstance(snapshot_digest, str) or not _SHA256.fullmatch(snapshot_digest):
        raise ValueError("Invalid snapshot digest")
    if type(value.get("snapshot_bytes")) is not int or not 1 <= value["snapshot_bytes"] <= MAX_SNAPSHOT_BYTES:
        raise ValueError("Invalid snapshot byte count")
    if not isinstance(value.get("osm_base"), str) or not _OSM_TIME.fullmatch(value["osm_base"]):
        raise ValueError("Invalid OpenStreetMap base timestamp")
    for field in ("elements", "matching_elements"):
        if type(value.get(field)) is not int or not 1 <= value[field] <= MAX_ELEMENTS:
            raise ValueError(f"Invalid snapshot {field}")
    if value["matching_elements"] > value["elements"]:
        raise ValueError("Snapshot matching count exceeds element count")
    return value


def read_lock(path: Path) -> dict[str, Any]:
    """Read a reviewed immutable place-source lock."""
    lock = _read_json(path)
    if lock.get("format") != 1 or not _IDENTITY.fullmatch(lock.get("id", "")):
        raise ValueError("Invalid place-source lock identity")
    if not _EDITION.fullmatch(lock.get("edition", "")):
        raise ValueError("Invalid place-source lock edition")
    for field, limit in (
        ("publisher", 300),
        ("license", 4_000),
        ("collection", 200),
        ("origin", 2_000),
    ):
        _required_text(lock.get(field), field, limit)
    _iso_date(lock.get("saved"), "saved")
    _https_url(lock["origin"], "origin")

    _validated_snapshot_source(lock.get("source"))
    review = lock.get("review")
    if not isinstance(review, dict):
        raise ValueError("Missing place snapshot review")
    _required_text(review.get("reviewer"), "reviewer", 300)
    _iso_date(review.get("reviewed"), "reviewed")
    _required_text(review.get("notes"), "review notes", 2_000)
    return lock


def _digest(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def _vegan_indicator(tags: dict[str, str]) -> str | None:
    diet = tags.get("diet:vegan", "").strip().lower()
    if diet in _VEGAN_VALUES:
        return f"diet:vegan={diet}"
    cuisines = {value.strip().lower() for value in tags.get("cuisine", "").split(";")}
    if "vegan" in cuisines:
        return "cuisine includes vegan"
    return None


def _snapshot_summary(snapshot: dict[str, Any]) -> dict[str, Any]:
    metadata, elements = snapshot.get("osm3s"), snapshot.get("elements")
    if (
        not isinstance(metadata, dict)
        or not isinstance(metadata.get("timestamp_osm_base"), str)
        or not _OSM_TIME.fullmatch(metadata["timestamp_osm_base"])
        or not isinstance(elements, list)
        or not 1 <= len(elements) <= MAX_ELEMENTS
    ):
        raise ValueError("Unexpected OpenStreetMap snapshot format")
    matching = 0
    for element in elements:
        if not isinstance(element, dict):
            raise ValueError("Invalid OpenStreetMap element")
        tags = element.get("tags")
        if not isinstance(tags, dict) or not all(
            isinstance(key, str) and isinstance(value, str) for key, value in tags.items()
        ):
            raise ValueError("Invalid OpenStreetMap tags")
        matching += int(
            element.get("type") in _OSM_TYPES
            and type(element.get("id")) is int
            and element["id"] > 0
            and tags.get("amenity") in _AMENITIES
            and bool(_tag_text(tags, "name"))
            and _vegan_indicator(tags) is not None
        )
    if matching == 0:
        raise ValueError("Snapshot has no supported named vegan places")
    return dict(
        osm_base=metadata["timestamp_osm_base"],
        elements=len(elements),
        matching_elements=matching,
    )


def _capture_receipt_path(snapshot: Path) -> Path:
    return snapshot.with_name(snapshot.name + ".capture.json")


def _receipt_source(receipt: dict[str, Any]) -> dict[str, Any]:
    if receipt.get("format") != _CAPTURE_RECEIPT_FORMAT:
        raise ValueError("Unsupported place capture receipt")
    if receipt.get("state") != "captured-unreviewed":
        raise ValueError("Place capture receipt is not unreviewed")
    snapshot_file = _required_text(receipt.get("snapshot_file"), "snapshot filename", 300)
    if Path(snapshot_file).name != snapshot_file:
        raise ValueError("Place capture snapshot filename must not contain a path")
    return _validated_snapshot_source(receipt.get("source"))


def read_capture_receipt(path: Path) -> dict[str, Any]:
    """Read a capture artifact that still requires human source review."""
    receipt = _read_json(path)
    _receipt_source(receipt)
    return receipt


def capture(endpoint: str, query_path: Path, output: Path) -> dict[str, Any]:
    """Capture one small Overpass JSON response for review, without making a lock."""
    endpoint = _https_url(endpoint, "snapshot endpoint")
    parsed = urllib.parse.urlsplit(endpoint)
    if parsed.query or parsed.fragment:
        raise ValueError("Snapshot endpoint must not include a query or fragment")
    query = _required_text(query_path.read_text(encoding="utf-8"), "snapshot query", 20_000)
    if "[out:json]" not in query.replace(" ", ""):
        raise ValueError("Snapshot query must request JSON output")
    receipt_path = _capture_receipt_path(output)
    if output.exists():
        raise FileExistsError(output)
    if receipt_path.exists():
        raise FileExistsError(receipt_path)
    output.parent.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(
        endpoint,
        data=urllib.parse.urlencode({"data": query}).encode("ascii"),
        headers={
            "Accept": "application/json",
            "Accept-Encoding": "identity",
            "Content-Type": "application/x-www-form-urlencoded; charset=utf-8",
            "User-Agent": "BlobsCorpus/0.1 (reviewed offline place library)",
        },
        method="POST",
    )
    opener = urllib.request.build_opener(_SameHostRedirect(parsed.hostname))
    with opener.open(request, timeout=60) as response:
        response_url = urllib.parse.urlsplit(response.geturl())
        if response.status != 200 or response_url.hostname != parsed.hostname:
            raise ValueError("Unexpected place snapshot response")
        if not response.headers.get("Content-Type", "").lower().startswith("application/json"):
            raise ValueError("Place snapshot response is not JSON")
        raw = response.read(MAX_SNAPSHOT_BYTES + 1)
    if len(raw) > MAX_SNAPSHOT_BYTES:
        raise ValueError("Place snapshot exceeds byte limit")
    try:
        snapshot = json.loads(raw.decode("utf-8", "strict"), object_pairs_hook=_strict_object)
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ValueError("Place snapshot is not valid UTF-8 JSON") from error
    if not isinstance(snapshot, dict):
        raise ValueError("Place snapshot must be a JSON object")
    summary = _snapshot_summary(snapshot)
    partial = output.with_suffix(output.suffix + ".partial")
    if partial.exists():
        raise FileExistsError(partial)
    try:
        with partial.open("xb") as stream:
            stream.write(raw)
            stream.flush()
            os.fsync(stream.fileno())
        os.link(partial, output)
    finally:
        partial.unlink(missing_ok=True)
    receipt = dict(
        format=_CAPTURE_RECEIPT_FORMAT,
        state="captured-unreviewed",
        snapshot_file=output.name,
        source=dict(
            endpoint=endpoint,
            query=query,
            query_sha256=hashlib.sha256(query.encode("utf-8")).hexdigest(),
            snapshot_bytes=len(raw),
            snapshot_sha256=hashlib.sha256(raw).hexdigest(),
            **summary,
        ),
    )
    _write_exclusive_json(receipt_path, receipt)
    return dict(receipt_path=str(receipt_path), **receipt)


def make_lock(
    receipt_path: Path,
    snapshot_path: Path,
    output: Path,
    *,
    identifier: str,
    edition: str,
    publisher: str,
    license_text: str,
    collection: str,
    origin: str,
    saved: str,
    reviewer: str,
    reviewed: str,
    notes: str,
) -> dict[str, Any]:
    """Turn a reviewed capture receipt into the lock accepted by extraction."""
    receipt = read_capture_receipt(receipt_path)
    source = _receipt_source(receipt)
    if not snapshot_path.is_file():
        raise FileNotFoundError(snapshot_path)
    if snapshot_path.stat().st_size != source["snapshot_bytes"]:
        raise ValueError("Place snapshot byte count differs from the capture receipt")
    if _digest(snapshot_path) != source["snapshot_sha256"]:
        raise ValueError("Place snapshot digest differs from the capture receipt")
    if output.exists():
        raise FileExistsError(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    lock = dict(
        format=1,
        id=identifier,
        edition=edition,
        publisher=publisher,
        license=license_text,
        collection=collection,
        origin=origin,
        saved=saved,
        source=source,
        review=dict(reviewer=reviewer, reviewed=reviewed, notes=notes),
    )
    # Validate the exact future extraction contract before the immutable lock is written.
    temporary = output.with_name(output.name + ".validation.json")
    if temporary.exists():
        raise FileExistsError(temporary)
    try:
        _write_exclusive_json(temporary, lock)
        read_lock(temporary)
    finally:
        temporary.unlink(missing_ok=True)
    _write_exclusive_json(output, lock)
    return lock


def _coordinate(element: dict[str, Any]) -> tuple[float, float]:
    point = element if element.get("type") == "node" else element.get("center")
    if not isinstance(point, dict):
        raise ValueError("Place snapshot lacks a center coordinate")
    latitude, longitude = point.get("lat"), point.get("lon")
    if (
        type(latitude) not in {int, float}
        or type(longitude) not in {int, float}
        or not -90 <= latitude <= 90
        or not -180 <= longitude <= 180
    ):
        raise ValueError("Invalid place coordinate")
    return float(latitude), float(longitude)


def _address(tags: dict[str, str]) -> str | None:
    street = " ".join(
        part for part in (tags.get("addr:housenumber"), tags.get("addr:street")) if part
    )
    locality = ", ".join(
        part for part in (tags.get("addr:suburb"), tags.get("addr:city")) if part
    )
    postcode = tags.get("addr:postcode")
    parts = [part for part in (street, locality, postcode) if part]
    return ", ".join(parts) if parts else None


def _tag_text(tags: dict[str, str], key: str) -> str | None:
    value = tags.get(key, "").strip()
    return value if value else None


def _place_document(
    lock: dict[str, Any], element: dict[str, Any], snapshot_sha256: str
) -> dict[str, Any] | None:
    element_type, element_id, tags = (
        element.get("type"),
        element.get("id"),
        element.get("tags"),
    )
    if element_type not in _OSM_TYPES or type(element_id) is not int or element_id <= 0:
        raise ValueError("Invalid OpenStreetMap element identity")
    if not isinstance(tags, dict) or not all(
        isinstance(key, str) and isinstance(value, str) for key, value in tags.items()
    ):
        raise ValueError("Invalid OpenStreetMap tags")
    amenity = _tag_text(tags, "amenity")
    name = _tag_text(tags, "name")
    indicator = _vegan_indicator(tags)
    if amenity not in _AMENITIES or name is None or indicator is None:
        return None
    latitude, longitude = _coordinate(element)
    timestamp = element.get("timestamp")
    updated = (
        timestamp[:10]
        if isinstance(timestamp, str) and _OSM_TIME.fullmatch(timestamp)
        else lock["saved"]
    )
    source_url = f"https://www.openstreetmap.org/{element_type}/{element_id}"
    lines = [
        f"OpenStreetMap place: {name}",
        f"Local collection: {lock['collection']}",
        f"Type: {amenity.replace('_', ' ')}",
        f"Vegan indicator in OpenStreetMap: {indicator}",
    ]
    for label, value in (
        ("Cuisine", _tag_text(tags, "cuisine")),
        ("Address", _address(tags)),
        ("Opening hours", _tag_text(tags, "opening_hours")),
        ("Phone", _tag_text(tags, "phone")),
        ("Website", _tag_text(tags, "website")),
    ):
        if value is not None:
            lines.append(f"{label}: {value}")
    lines.extend(
        (
            f"Coordinates: {latitude:.6f}, {longitude:.6f}",
            f"OpenStreetMap object: {source_url}",
            f"Snapshot base time: {lock['source']['osm_base']}",
        )
    )
    document = dict(
        id=f"osm-{element_type}-{element_id}",
        title=name,
        publisher=lock["publisher"],
        url=source_url,
        license=lock["license"],
        updated=updated,
        saved=lock["saved"],
        collection=lock["collection"],
        body="\n".join(lines),
        provenance=dict(
            source_id=lock["id"],
            edition=lock["edition"],
            source_url=lock["origin"],
            snapshot_sha256=snapshot_sha256,
            query_sha256=lock["source"]["query_sha256"],
            osm_base=lock["source"]["osm_base"],
            osm_type=element_type,
            osm_id=element_id,
            osm_version=element.get("version"),
            osm_timestamp=timestamp,
            vegan_indicator=indicator,
            latitude=latitude,
            longitude=longitude,
            extractor="blobs_corpus.places/1",
        ),
    )
    validate_document(document)
    return document


def extract(lock_path: Path, snapshot_path: Path, output: Path) -> Path:
    """Validate one locked OSM snapshot and write normalized place records."""
    lock = read_lock(lock_path)
    if output.exists():
        raise FileExistsError(output)
    if (
        not snapshot_path.is_file()
        or snapshot_path.stat().st_size != lock["source"]["snapshot_bytes"]
    ):
        raise ValueError("Place snapshot byte count differs from the lock")
    snapshot_sha256 = _digest(snapshot_path)
    if snapshot_sha256 != lock["source"]["snapshot_sha256"]:
        raise ValueError("Place snapshot digest differs from the lock")
    snapshot = _read_json(snapshot_path)
    metadata, elements = snapshot.get("osm3s"), snapshot.get("elements")
    if (
        not isinstance(metadata, dict)
        or metadata.get("timestamp_osm_base") != lock["source"]["osm_base"]
    ):
        raise ValueError("Place snapshot OpenStreetMap base time differs from the lock")
    if not isinstance(elements, list) or len(elements) != lock["source"]["elements"]:
        raise ValueError("Place snapshot element count differs from the lock")

    documents = []
    identities: set[tuple[str, int]] = set()
    for element in elements:
        if not isinstance(element, dict):
            raise ValueError("Invalid OpenStreetMap element")
        identity = element.get("type"), element.get("id")
        if identity in identities:
            raise ValueError("Duplicate OpenStreetMap element")
        identities.add(identity)
        document = _place_document(lock, element, snapshot_sha256)
        if document is not None:
            documents.append(document)
    if len(documents) != lock["source"]["matching_elements"]:
        raise ValueError("Place snapshot matching count differs from the lock")
    documents.sort(key=lambda item: (item["title"].casefold(), item["id"]))
    output.parent.mkdir(parents=True, exist_ok=True)
    write_lines(output, documents)
    return output


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    capture_parser = commands.add_parser("capture")
    capture_parser.add_argument("endpoint")
    capture_parser.add_argument("query", type=Path)
    capture_parser.add_argument("output", type=Path)
    extract_parser = commands.add_parser("extract")
    extract_parser.add_argument("lock", type=Path)
    extract_parser.add_argument("snapshot", type=Path)
    extract_parser.add_argument("output", type=Path)
    lock_parser = commands.add_parser("make-lock")
    lock_parser.add_argument("receipt", type=Path)
    lock_parser.add_argument("snapshot", type=Path)
    lock_parser.add_argument("output", type=Path)
    lock_parser.add_argument("--id", dest="identifier", required=True)
    lock_parser.add_argument("--edition", required=True)
    lock_parser.add_argument("--publisher", required=True)
    lock_parser.add_argument("--license", dest="license_text", required=True)
    lock_parser.add_argument("--collection", required=True)
    lock_parser.add_argument("--origin", required=True)
    lock_parser.add_argument("--saved", required=True)
    lock_parser.add_argument("--reviewer", required=True)
    lock_parser.add_argument("--reviewed", required=True)
    lock_parser.add_argument("--notes", required=True)
    args = parser.parse_args()
    if args.command == "capture":
        print(json.dumps(capture(args.endpoint, args.query, args.output), indent=2))
    elif args.command == "extract":
        print(extract(args.lock, args.snapshot, args.output))
    else:
        print(
            json.dumps(
                make_lock(
                    args.receipt,
                    args.snapshot,
                    args.output,
                    identifier=args.identifier,
                    edition=args.edition,
                    publisher=args.publisher,
                    license_text=args.license_text,
                    collection=args.collection,
                    origin=args.origin,
                    saved=args.saved,
                    reviewer=args.reviewer,
                    reviewed=args.reviewed,
                    notes=args.notes,
                ),
                ensure_ascii=False,
                indent=2,
            )
        )


if __name__ == "__main__":
    main()
