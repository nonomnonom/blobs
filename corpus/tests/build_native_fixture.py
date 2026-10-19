"""Regenerate original, freely redistributable text used by the Android contract test."""

from pathlib import Path
import os
import tempfile
from unittest.mock import patch
import zipfile

from blobs_corpus.extract import write_lines
from blobs_corpus.library import build_library
from blobs_corpus.package import package


def main():
    target = (
        Path(__file__).resolve().parents[2]
        / "android/app/src/androidTest/assets/corpus-fixture.blobs"
    )
    document = dict(
        id="energy-fixture",
        title="Thermal notebook",
        publisher="Blobs test authors",
        url="local:original-test-text",
        license="CC0-1.0; original test fixture",
        updated="2026-10-17",
        saved="2026-10-17",
        collection="Contract test",
        body="This original test text describes an imaginary experiment.\n\n"
        "== Measurements ==\n\nWater | Temperature (°C)\nSample A | 20\nSample B | 30\n\n"
        "=== Interpretation ===\n\n"
        + "This is an imaginary observation. " * 32
        + "Evaporation transfers energy. A lower temperature does not prove lower total energy.",
    )
    target.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".fixture-", dir=target.parent) as folder:
        source, database = Path(folder) / "document.jsonl.gz", Path(folder) / "library.sqlite"
        write_lines(source, [document])
        build_library(source, database)
        for name, threshold in [
            ("corpus-fixture.blobs", zipfile.ZIP64_LIMIT),
            ("corpus-zip64-fixture.blobs", 1024),
        ]:
            staged = Path(folder) / name
            # Exercise the production writer's large-file branch with a tiny payload.
            with patch("zipfile.ZIP64_LIMIT", threshold):
                package(
                    database,
                    staged,
                    kind="library",
                    id="corpus-fixture",
                    version="1",
                    title="Corpus contract fixture",
                    license=document["license"],
                    origin=document["url"],
                )
            with zipfile.ZipFile(staged) as archive:
                if threshold == 1024:
                    assert archive.getinfo("library.sqlite").extract_version == 45
            os.replace(staged, target.with_name(name))


if __name__ == "__main__":
    main()
