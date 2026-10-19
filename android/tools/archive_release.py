"""Create a complete, verified offline-kit ZIP without overwriting an existing archive."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import tempfile
import zipfile

from verify_release import CHECKSUMS, digest, expected_checksums, files_in, verify_kit


def archive_release(directory: Path, output: Path) -> dict:
    directory, output = directory.resolve(), output.resolve()
    if output.exists():
        raise FileExistsError(output)
    if output.is_relative_to(directory):
        raise ValueError("Write the archive outside the immutable release folder")
    report = verify_kit(directory)
    expected = expected_checksums(directory / CHECKSUMS)
    expected[CHECKSUMS] = report['checksums']['sha256']
    if digest(directory / CHECKSUMS) != expected[CHECKSUMS]:
        raise ValueError("Checksum inventory changed after verification")
    files = files_in(directory)
    if set(files) != set(expected):
        raise ValueError("Release file inventory changed after verification")
    output.parent.mkdir(parents=True, exist_ok=True)
    prefix = f"blobs-{report['version']}/"
    with tempfile.TemporaryDirectory(prefix='.offline-kit-', dir=output.parent) as temp:
        staged = Path(temp) / 'kit.zip'
        # Models and packages are already compressed; preserve them without recompression.
        with zipfile.ZipFile(staged, 'x', compression=zipfile.ZIP_STORED) as archive:
            for name, path in sorted(files.items()):
                archive.write(path, prefix + name)
        with zipfile.ZipFile(staged) as archive:
            if set(archive.namelist()) != {prefix + name for name in expected}:
                raise ValueError("Archived file inventory differs from the verified kit")
            for name, checksum in expected.items():
                with archive.open(prefix + name) as stream:
                    if hashlib.file_digest(stream, 'sha256').hexdigest() != checksum:
                        raise ValueError(f"File changed while archiving: {name}")
        with staged.open('r+b') as stream:
            os.fsync(stream.fileno())
        result = dict(version=report['version'], file=output.name, bytes=staged.stat().st_size,
                      sha256=digest(staged), files=len(expected),
                      checksums_sha256=expected[CHECKSUMS])
        os.link(staged, output)
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    print(json.dumps(archive_release(args.directory, args.output), indent=2))
