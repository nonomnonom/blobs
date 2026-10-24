"""Refuse a reviewed edition that duplicates supply already retained in the repository."""

from concurrent.futures import ThreadPoolExecutor
import gzip
import hashlib
import json
import os
from pathlib import Path

MAX_REPORTED = 5


def records_root() -> Path:
    return Path(__file__).resolve().parents[1] / "artifacts" / "records"


def cache_root() -> Path:
    return Path(__file__).resolve().parents[1] / "work" / "portfolio"


def digest(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


ENGLISH_TAGS = {"eng", "en"}


def install_identity(manifest: dict) -> tuple[str, str]:
    source = manifest.get("source")
    if not isinstance(source, dict) or not source.get("id"):
        raise ValueError("A portfolio edition must name its pinned source identity")
    source_id = str(source["id"])
    metadata = manifest.get("archive_metadata") or {}
    tag = str(source.get("language") or metadata.get("Language") or source_id.split("-")[1]).lower()
    if tag not in ENGLISH_TAGS:
        raise ValueError(
            f"Supply language policy: Blobs publishes English knowledge only, and this edition is "
            f"{tag!r}. Categories grow by subject, not by a per-language edition of one project."
        )
    lane = manifest.get("lane")
    if isinstance(lane, str) and lane:
        # A subject lane spans several archives, so the lane itself holds the slot.
        return lane, "eng"
    return source_id.split("-")[0], "eng"


def fingerprint(manifest_path: Path, cache: Path | None = None) -> dict:
    manifest_path = Path(manifest_path)
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    identity = install_identity(manifest)
    stamp = digest(manifest_path)
    directory = cache or cache_root()
    target = directory / f"{stamp}.json.gz"
    if not target.exists():
        urls, bodies = set(), set()
        for shard in manifest["shards"]:
            shard_path = manifest_path.parent / shard["files"]["documents"]["name"]
            with gzip.open(shard_path, "rt", encoding="utf-8") as stream:
                for line in stream:
                    record = json.loads(line)
                    urls.add(hashlib.sha256(record["url"].encode()).hexdigest())
                    bodies.add(record["provenance"]["body_sha256"])
        directory.mkdir(parents=True, exist_ok=True)
        temporary = target.with_suffix(".tmp")
        with gzip.open(temporary, "wt", encoding="utf-8") as stream:
            stream.write(
                json.dumps(
                    {
                        "manifest_sha256": stamp,
                        "source_id": manifest["source"]["id"],
                        "identity": list(identity),
                        "documents": len(urls),
                        "urls": sorted(urls),
                        "bodies": sorted(bodies),
                    },
                    separators=(",", ":"),
                )
            )
        os.replace(temporary, target)
    cached = json.loads(gzip.open(target, "rt", encoding="utf-8").read())
    return {**cached, "urls": set(cached["urls"]), "bodies": set(cached["bodies"])}


def retained_manifests(root: Path | None = None) -> list[Path]:
    root = Path(root or records_root())
    found = []
    for manifest in sorted(root.glob("*/manifest.json")):
        record = json.loads(manifest.read_text(encoding="utf-8"))
        if record.get("complete") and record.get("curation"):
            found.append(manifest)
    return found


def _peers(
    manifest_path: Path, root: Path | None, cache: Path | None
) -> tuple[dict, list[tuple[Path, dict]]]:
    subject = fingerprint(manifest_path, cache)
    others = [
        path for path in retained_manifests(root) if path.resolve() != Path(manifest_path).resolve()
    ]
    if not others:
        return subject, []
    with ThreadPoolExecutor(max_workers=4) as pool:
        return subject, list(zip(others, pool.map(lambda path: fingerprint(path, cache), others)))


def findings(manifest_path: Path, root: Path | None = None, cache: Path | None = None) -> dict:
    """Return retained editions that repeat this supply, and editions claiming the same slot."""
    subject, peers = _peers(manifest_path, root, cache)
    repeated, same_slot = [], []
    for path, peer in peers:
        counts = {
            "edition": path.parent.name,
            "identity": peer["identity"],
            "shared_canonical_urls": len(subject["urls"] & peer["urls"]),
            "shared_body_hashes": len(subject["bodies"] & peer["bodies"]),
        }
        if counts["shared_canonical_urls"] or counts["shared_body_hashes"]:
            repeated.append(counts)
        elif peer["identity"] == subject["identity"]:
            same_slot.append(counts)

    def rank(row):
        return row["shared_canonical_urls"] + row["shared_body_hashes"]

    return {
        "edition": Path(manifest_path).parent.name,
        "source_id": subject["source_id"],
        "identity": subject["identity"],
        "documents": subject["documents"],
        "repeated_supply": sorted(repeated, key=rank, reverse=True)[:MAX_REPORTED],
        "identity_slots_taken": sorted(same_slot, key=lambda row: row["edition"])[:MAX_REPORTED],
    }


def assert_distinct(
    manifest_path: Path, root: Path | None = None, cache: Path | None = None
) -> dict:
    result = findings(manifest_path, root, cache)
    repeated = result["repeated_supply"]
    if repeated:
        worst = repeated[0]
        raise ValueError(
            "Duplicate supply: this edition repeats "
            f"{worst['shared_canonical_urls']:,} canonical URLs and {worst['shared_body_hashes']:,} "
            f"body hashes retained in {worst['edition']}. Retire one edition before exporting."
        )
    if result["identity_slots_taken"]:
        taken = ", ".join(row["edition"] for row in result["identity_slots_taken"])
        project, language = result["identity"]
        raise ValueError(
            f"Install-identity collision: a {project!r} library in language {language!r} is already "
            f"retained as {taken}. A successor edition replaces that retained edition instead of "
            "adding a second library users must choose between."
        )
    return result


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path, help="reviewed records manifest to check")
    parser.add_argument("--records-root", type=Path, default=None)
    parser.add_argument("--cache", type=Path, default=None)
    args = parser.parse_args()
    print(json.dumps(findings(args.manifest, args.records_root, args.cache), indent=2))
