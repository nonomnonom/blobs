"""Create and enforce deterministic, complementary library partitions."""

from __future__ import annotations

from dataclasses import dataclass
import argparse
import hashlib
import json
from pathlib import Path
import re

from .acquire import digest
from .extract import verified_shards, write_json
from .records import documents


FORMAT = 1
ALGORITHM = "sha256-document-id-modulo-v1"
MAX_PARTITIONS = 128


def _part_index(document_id: str, count: int) -> int:
    return int.from_bytes(hashlib.sha256(document_id.encode("utf-8")).digest()[:8], "big") % count


def _reviewed_manifest(source: Path) -> dict:
    if source.name != "manifest.json":
        raise ValueError("A partition source must be a reviewed extraction manifest")
    manifest = verified_shards(source)
    if not manifest.get("complete") or not manifest.get("curation"):
        raise ValueError("A partition source must be complete and reviewed")
    return manifest


def _digest_id(stream, document_id: str) -> None:
    stream.update(document_id.encode("utf-8"))
    stream.update(b"\n")


def create_plan(source: Path, output: Path, count: int) -> dict:
    """Write a fixed partition plan for one reviewed source edition."""
    if not 2 <= count <= MAX_PARTITIONS:
        raise ValueError(f"Partition count must be between 2 and {MAX_PARTITIONS}")
    manifest = _reviewed_manifest(source)
    if output.exists():
        raise FileExistsError(output)
    source_lock_sha256 = manifest.get("identity", {}).get("lock_sha256")
    source_id = manifest.get("source", {}).get("id")
    source_edition = manifest.get("source", {}).get("edition")
    if (
        not isinstance(source_lock_sha256, str)
        or not re.fullmatch(r"[a-f0-9]{64}", source_lock_sha256)
        or not isinstance(source_id, str)
        or not re.fullmatch(r"[a-z0-9-]{1,64}", source_id)
        or not isinstance(source_edition, str)
        or not re.fullmatch(r"[A-Za-z0-9.-]{1,40}", source_edition)
    ):
        raise ValueError("A partition source must identify an immutable source lock")

    totals = [0] * count
    identities = [hashlib.sha256() for _ in range(count)]
    for document in documents(source):
        index = _part_index(document["id"], count)
        totals[index] += 1
        _digest_id(identities[index], document["id"])
    if not all(totals):
        raise ValueError("Partition count produces an empty library volume")

    plan = dict(
        format=FORMAT,
        algorithm=ALGORITHM,
        source_manifest_sha256=digest(source),
        source_lock_sha256=source_lock_sha256,
        source_id=source_id,
        source_edition=source_edition,
        partitions=[
            dict(
                id=f"p{index:03d}",
                index=index,
                documents=totals[index],
                document_ids_sha256=identities[index].hexdigest(),
            )
            for index in range(count)
        ],
    )
    output.parent.mkdir(parents=True, exist_ok=True)
    write_json(output, plan)
    return plan


def _read_plan(source: Path, plan_path: Path) -> dict:
    _reviewed_manifest(source)
    try:
        plan = json.loads(plan_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise ValueError(f"Invalid partition plan: {plan_path}") from error
    if (
        not isinstance(plan, dict)
        or plan.get("format") != FORMAT
        or plan.get("algorithm") != ALGORITHM
        or plan.get("source_manifest_sha256") != digest(source)
        or not isinstance(plan.get("source_lock_sha256"), str)
        or not re.fullmatch(r"[a-f0-9]{64}", plan["source_lock_sha256"])
        or not isinstance(plan.get("source_id"), str)
        or not re.fullmatch(r"[a-z0-9-]{1,64}", plan["source_id"])
        or not isinstance(plan.get("source_edition"), str)
        or not re.fullmatch(r"[A-Za-z0-9.-]{1,40}", plan["source_edition"])
        or not isinstance(plan.get("partitions"), list)
        or not 2 <= len(plan["partitions"]) <= MAX_PARTITIONS
    ):
        raise ValueError("Unsupported or mismatched partition plan")
    manifest = _reviewed_manifest(source)
    if (
        plan["source_lock_sha256"] != manifest.get("identity", {}).get("lock_sha256")
        or plan["source_id"] != manifest.get("source", {}).get("id")
        or plan["source_edition"] != manifest.get("source", {}).get("edition")
    ):
        raise ValueError("Partition plan does not identify this reviewed source")

    indexes, ids = set(), set()
    for part in plan["partitions"]:
        if (
            not isinstance(part, dict)
            or not isinstance(part.get("id"), str)
            or not re.fullmatch(r"p\d{3}", part["id"])
            or type(part.get("index")) is not int
            or part["index"] < 0
            or type(part.get("documents")) is not int
            or part["documents"] < 1
            or not isinstance(part.get("document_ids_sha256"), str)
            or not re.fullmatch(r"[a-f0-9]{64}", part["document_ids_sha256"])
            or part["id"] in ids
            or part["index"] in indexes
        ):
            raise ValueError("Invalid partition descriptor")
        ids.add(part["id"])
        indexes.add(part["index"])
    if indexes != set(range(len(plan["partitions"]))):
        raise ValueError("Partition indexes must be contiguous")
    return plan


@dataclass(frozen=True)
class PartitionSelection:
    source_manifest_sha256: str
    source_lock_sha256: str
    source_id: str
    source_edition: str
    algorithm: str
    partition_id: str
    partition_index: int
    partition_count: int
    expected_documents: int
    expected_document_ids_sha256: str
    plan_sha256: str

    def iter_documents(self, source: Path):
        count = 0
        identities = hashlib.sha256()
        for document in documents(source):
            if _part_index(document["id"], self.partition_count) != self.partition_index:
                continue
            count += 1
            _digest_id(identities, document["id"])
            yield document
        if (
            count != self.expected_documents
            or identities.hexdigest() != self.expected_document_ids_sha256
        ):
            raise ValueError(
                f"Reviewed records no longer match the selected partition plan: {self.partition_id}"
            )

    def metadata(self) -> dict:
        return dict(
            algorithm=self.algorithm,
            plan_sha256=self.plan_sha256,
            source_manifest_sha256=self.source_manifest_sha256,
            source_lock_sha256=self.source_lock_sha256,
            source_id=self.source_id,
            source_edition=self.source_edition,
            id=self.partition_id,
            index=self.partition_index,
            count=self.partition_count,
            documents=self.expected_documents,
            document_ids_sha256=self.expected_document_ids_sha256,
        )


def select_partition(source: Path, plan_path: Path, partition_id: str) -> PartitionSelection:
    """Load one source-bound partition selector for a library build."""
    plan = _read_plan(source, plan_path)
    match = next((part for part in plan["partitions"] if part["id"] == partition_id), None)
    if match is None:
        raise ValueError(f"Partition does not exist in plan: {partition_id}")
    return PartitionSelection(
        source_manifest_sha256=plan["source_manifest_sha256"],
        source_lock_sha256=plan["source_lock_sha256"],
        source_id=plan["source_id"],
        source_edition=plan["source_edition"],
        algorithm=plan["algorithm"],
        partition_id=match["id"],
        partition_index=match["index"],
        partition_count=len(plan["partitions"]),
        expected_documents=match["documents"],
        expected_document_ids_sha256=match["document_ids_sha256"],
        plan_sha256=digest(plan_path),
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, help="Reviewed extraction manifest.json")
    parser.add_argument("output", type=Path, help="New immutable partition-plan JSON path")
    parser.add_argument("--count", type=int, required=True, help="Number of complementary volumes")
    args = parser.parse_args()
    print(json.dumps(create_plan(args.source, args.output, args.count), indent=2))


if __name__ == "__main__":
    main()
