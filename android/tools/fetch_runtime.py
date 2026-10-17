"""Prepare checksum-locked native sources; all network access stays off the phone."""
import hashlib
import json
from pathlib import Path
import tempfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def fetch(name, url, checksum):
    target = ROOT / 'third_party' / name
    cache = ROOT / '.local' / (name + '-src.zip')
    cache.parent.mkdir(parents=True, exist_ok=True)
    if not cache.exists():
        request = urllib.request.Request(url, headers={'User-Agent': 'BlobsBuild/0.1'})
        with urllib.request.urlopen(request, timeout=60) as response:
            with tempfile.NamedTemporaryFile(dir=cache.parent, delete=False) as stream:
                temporary = Path(stream.name)
                try:
                    while block := response.read(1024 * 1024):
                        stream.write(block)
                except BaseException:
                    stream.close()
                    temporary.unlink(missing_ok=True)
                    raise
        if digest(temporary) != checksum:
            temporary.unlink()
            raise ValueError('Native source archive checksum mismatch')
        temporary.rename(cache)
    if digest(cache) != checksum:
        raise ValueError(f'Changed cached source archive: {cache}')
    with zipfile.ZipFile(cache) as archive:
        expected = set()
        for member in archive.infolist():
            path = (ROOT / 'third_party' / member.filename).resolve()
            if not path.is_relative_to(target.resolve()):
                raise ValueError('Unsafe source archive path')
            if not member.is_dir():
                expected.add(path)
        if target.exists():
            actual = {p.resolve() for p in target.rglob('*') if p.is_file()}
            if actual != expected:
                raise ValueError(f'Native source tree differs from its archive: {target}')
            for member in archive.infolist():
                if member.is_dir():
                    continue
                path = ROOT / 'third_party' / member.filename
                with archive.open(member) as stream:
                    expected_hash = hashlib.file_digest(stream, 'sha256').hexdigest()
                if digest(path) != expected_hash:
                    raise ValueError(f'Modified native source: {path}')
            print(f'Verified existing source: {target.name}')
        else:
            with tempfile.TemporaryDirectory(prefix='.source-', dir=target.parent) as folder:
                archive.extractall(folder)
                (Path(folder) / name).rename(target)
            print(f'Verified and extracted: {target.name}')
    return target


def main():
    llama = json.loads((ROOT / 'third_party/llama.lock.json').read_text())
    fetch('llama.cpp-' + llama['commit'],
          f"https://codeload.github.com/ggml-org/llama.cpp/zip/{llama['commit']}",
          llama['archive_sha256'])
    arm = json.loads((ROOT / 'third_party/kleidiai.lock.json').read_text())
    target = fetch('kleidiai-' + arm['version'], arm['archive_url'], arm['archive_sha256'])
    notices = ROOT / 'app/src/main/assets/licenses/KleidiAI.txt'
    texts = ['KleidiAI ' + arm['version'] + '\nCopyright 2024-2026 Arm Limited and/or its affiliates\n' + arm['repository']]
    for name in ['Apache-2.0.txt', 'BSD-3-Clause.txt']:
        texts.append((target / 'LICENSES' / name).read_text(encoding='utf-8'))
    notices.write_text('\n\n'.join(texts), encoding='utf-8')


if __name__ == '__main__':
    main()
