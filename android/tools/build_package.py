"""Compatibility entry point; corpus/ owns the shared package format and builder."""

from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "corpus"))
from blobs_corpus.package import build_library, main, package, passages, verify_package

if __name__ == "__main__":
    main()
