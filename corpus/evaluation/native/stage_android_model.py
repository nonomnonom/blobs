"""Stage one pinned GGUF for native Android evaluation and record its identity."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

from blobs_corpus.extract import write_json
from ..retrieval.host_replay import sha


HEADROOM_BYTES = 256 * 1024 * 1024


def run(adb: Path, serial: str, *arguments, check=True):
    return subprocess.run(
        [str(adb), '-s', serial, *arguments],
        capture_output=True,
        check=check,
        text=True,
        encoding='utf-8',
    )


def shell(adb: Path, serial: str, command: str, check=True):
    return run(adb, serial, 'shell', command, check=check)


def free_bytes(adb: Path, serial: str):
    rows = [line.split() for line in shell(adb, serial, 'df -k /data').stdout.splitlines()]
    if len(rows) < 2 or len(rows[-1]) < 4 or not rows[-1][3].isdigit():
        raise ValueError('Could not parse device storage')
    return int(rows[-1][3]) * 1024


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', type=Path, required=True)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--lock', type=Path, required=True)
    parser.add_argument('--target', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise FileExistsError(args.output)
    if not re.fullmatch(r'/data/local/tmp/[a-zA-Z0-9._-]{1,120}', args.target):
        raise ValueError('Target must be one safe filename under /data/local/tmp')
    lock = json.loads(args.lock.read_text(encoding='utf-8'))['model']
    if args.source.name != lock['filename'] or args.source.stat().st_size != lock['bytes']:
        raise ValueError('Source identity differs from lock')
    with args.source.open('rb') as stream:
        source_hash = hashlib.file_digest(stream, 'sha256').hexdigest()
    if source_hash != lock['sha256']:
        raise ValueError('Source hash differs from lock')
    if shell(args.adb, args.serial, f'test ! -e {args.target}', check=False).returncode != 0:
        raise FileExistsError(args.target)
    available = free_bytes(args.adb, args.serial)
    if available <= lock['bytes'] + HEADROOM_BYTES:
        raise OSError('Device needs model bytes plus more than 256 MiB free')
    report = dict(
        format=1,
        complete=False,
        scope=('ADB staging evidence for a native test fixture. This is not model-package import, '
               'phone-performance evidence or a product-model selection.'),
        serial=args.serial,
        source=dict(file=args.source.name, bytes=lock['bytes'], sha256=source_hash),
        source_lock=dict(file=args.lock.as_posix(), sha256=sha(args.lock)),
        target=args.target,
        preflight_free_bytes=available,
        required_free_bytes_exclusive=lock['bytes'] + HEADROOM_BYTES,
        cleanup=f'adb -s {args.serial} shell rm -f {args.target}',
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    write_json(args.output, report)
    try:
        pushed = run(args.adb, args.serial, 'push', str(args.source.resolve()), args.target)
        device_size = int(shell(args.adb, args.serial, f'stat -c %s {args.target}').stdout.strip())
        device_hash = shell(args.adb, args.serial, f'sha256sum {args.target}').stdout.split()[0]
        if device_size != lock['bytes'] or device_hash != source_hash:
            raise ValueError('Staged model identity differs')
        report.update(
            complete=True,
            adb_version=subprocess.run(
                [str(args.adb), 'version'], capture_output=True, check=True,
                text=True, encoding='utf-8',
            ).stdout.strip(),
            device=dict(
                fingerprint=shell(args.adb, args.serial, 'getprop ro.build.fingerprint').stdout.strip(),
                abi=shell(args.adb, args.serial, 'getprop ro.product.cpu.abi').stdout.strip(),
            ),
            push_output=(pushed.stdout + pushed.stderr).strip(),
            staged=dict(bytes=device_size, sha256=device_hash),
            remaining_free_bytes=free_bytes(args.adb, args.serial),
        )
    except Exception as error:
        report['error'] = repr(error)
        shell(args.adb, args.serial, f'rm -f {args.target}', check=False)
        raise
    finally:
        write_json(args.output, report)
    print(json.dumps({key: report[key] for key in ('complete', 'serial', 'target', 'staged')}, indent=2))


if __name__ == '__main__':
    main()
