"""Create immutable .blobs packages understood by the native stream importer."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import tempfile
import zipfile

from .library import build_library, passages


def verify_package(path: Path):
    """Check the complete streamed payload against the native package contract."""
    with zipfile.ZipFile(path) as archive:
        if not archive.namelist() or archive.namelist()[0] != 'manifest.json':
            raise ValueError('Package manifest must be first')
        if archive.getinfo('manifest.json').file_size > 65536:
            raise ValueError('Package manifest exceeds 65,536 bytes')
        manifest = json.loads(archive.read('manifest.json'))
        if not isinstance(manifest, dict) or type(manifest.get('format')) is not int or manifest['format'] != 1:
            raise ValueError('Unsupported package format')
        for field, limit in [('id', 64), ('version', 40), ('kind', 7), ('sha256', 64),
                             ('title', 160), ('license', 4000), ('origin', 2000)]:
            value = manifest.get(field)
            if not isinstance(value, str) or not 1 <= len(value.encode('utf-16-le')) // 2 <= limit:
                raise ValueError(f'Invalid package {field}')
        if (not re.fullmatch('[a-z0-9-]{1,64}', manifest['id']) or
                not re.fullmatch('[a-zA-Z0-9.-]{1,40}', manifest['version']) or
                not re.fullmatch('[a-f0-9]{64}', manifest['sha256']) or
                manifest['kind'] not in {'model', 'library'} or
                type(manifest.get('bytes')) is not int or not 0 < manifest['bytes'] <= 40_000_000_000):
            raise ValueError('Invalid package identity or payload size')
        payload = 'model.gguf' if manifest['kind'] == 'model' else 'library.sqlite'
        if archive.namelist() != ['manifest.json', payload]:
            raise ValueError('Unexpected package contents')
        if archive.getinfo(payload).file_size != manifest['bytes']:
            raise ValueError('Package payload length disagrees with manifest')
        count, digest = 0, hashlib.sha256()
        with archive.open(payload) as stream:
            while block := stream.read(1024 * 1024):
                count += len(block)
                if count > manifest['bytes']:
                    raise ValueError('Package payload exceeds declared length')
                digest.update(block)
        if count != manifest['bytes'] or digest.hexdigest() != manifest['sha256']:
            raise ValueError('Invalid package hash or byte count')
        return manifest


def package(payload: Path, output: Path, *, kind: str, id: str, version: str, title: str, license: str, origin: str):
    if kind not in {'model', 'library'}:
        raise ValueError('Invalid package kind')
    if not re.fullmatch('[a-z0-9-]{1,64}', id) or not re.fullmatch('[a-zA-Z0-9.-]{1,40}', version):
        raise ValueError('Invalid package identity')
    if any(not 1 <= len(text.encode('utf-16-le')) // 2 <= limit
           for text, limit in [(title, 160), (license, 4000), (origin, 2000)]):
        raise ValueError('Package metadata exceeds native import limits')
    if not 0 < payload.stat().st_size <= 40_000_000_000:
        raise ValueError('Payload exceeds native byte limits')
    if output.exists():
        raise FileExistsError(output)
    if kind == 'model':
        with payload.open('rb') as stream:
            if stream.read(4) != b'GGUF':
                raise ValueError('Expected GGUF')
    with payload.open('rb') as stream:
        checksum = hashlib.file_digest(stream, 'sha256').hexdigest()
    manifest = dict(format=1, id=id, version=version, kind=kind, title=title,
                    bytes=payload.stat().st_size, sha256=checksum, license=license, origin=origin)
    output.parent.mkdir(parents=True, exist_ok=True)
    compression = zipfile.ZIP_DEFLATED if kind == 'library' else zipfile.ZIP_STORED
    with tempfile.TemporaryDirectory(prefix='.package-', dir=output.parent) as folder:
        staged = Path(folder) / 'package.blobs'
        with zipfile.ZipFile(staged, 'x', allowZip64=True) as archive:
            header = zipfile.ZipInfo('manifest.json', date_time=(1980, 1, 1, 0, 0, 0))
            archive.writestr(header, json.dumps(manifest, ensure_ascii=False, sort_keys=True))
            info = zipfile.ZipInfo('model.gguf' if kind == 'model' else 'library.sqlite', date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = compression
            info.file_size = payload.stat().st_size
            with payload.open('rb') as source, archive.open(info, 'w', force_zip64=info.file_size >= zipfile.ZIP64_LIMIT) as dest:
                shutil.copyfileobj(source, dest, 1024 * 1024)
        os.link(staged, output)
    print(json.dumps(manifest, indent=2), flush=True)
    print(output, flush=True)
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('kind', choices=['model', 'library', 'database'])
    parser.add_argument('input', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--schema', type=int, choices=[1, 2, 3], default=3)
    parser.add_argument('--work-directory', type=Path,
                        help='Optional database staging volume; final packages still publish atomically on the output volume')
    for name in ['id', 'version', 'title', 'license', 'origin']:
        parser.add_argument('--' + name, required=True)
    args = vars(parser.parse_args())
    source, output, kind = args.pop('input'), args.pop('output'), args.pop('kind')
    schema = args.pop('schema')
    work = args.pop('work_directory')
    output.parent.mkdir(parents=True, exist_ok=True)
    if kind == 'library':
        work = work or output.parent
        work.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(prefix='.build-', dir=work) as folder:
            db = Path(folder) / 'library.sqlite'
            build = build_library(source, db, schema=schema)
            manifest = package(db, output, kind=kind, **args)
    else:
        if work is not None:
            parser.error('--work-directory applies to library builds only')
        build = None
        manifest = package(source, output, kind='library' if kind == 'database' else 'model', **args)
    from .acquire import digest
    from .extract import write_json
    write_json(output.with_suffix('.receipt.json'),
               dict(format=1, manifest=manifest, build=build,
                    package=dict(file=output.name, bytes=output.stat().st_size, sha256=digest(output)),
                    input_sha256=digest(source), python=platform.python_version(), platform=platform.system()))


if __name__ == '__main__':
    main()
