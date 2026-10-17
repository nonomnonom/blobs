"""Acquire only the exact source named by a reviewed local lock file."""
import errno
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import time
import urllib.error
import urllib.parse
import urllib.request


def digest(path: Path) -> str:
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def read_lock(path: Path) -> dict:
    lock = json.loads(path.read_text(encoding='utf-8'))
    if lock['format'] != 1 or not re.fullmatch(r'[a-zA-Z0-9_.-]+\.zim', lock['filename']):
        raise ValueError('Invalid source lock')
    if not re.fullmatch('[a-f0-9]{64}', lock['sha256']) or lock['bytes'] <= 0:
        raise ValueError('Invalid source size or digest')
    checked_url(lock['url'], lock['allowed_hosts'])
    return lock


def checked_url(url: str, hosts: list[str]) -> str:
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme != 'https' or parsed.hostname not in hosts or parsed.username or parsed.password:
        raise ValueError(f'Unapproved source origin: {parsed.scheme}://{parsed.hostname}')
    return url


class SourceRedirect(urllib.request.HTTPRedirectHandler):
    def __init__(self, hosts):
        self.hosts = hosts

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        checked_url(newurl, self.hosts)
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def verify_source(path: Path, lock: dict):
    if path.stat().st_size != lock['bytes'] or digest(path) != lock['sha256']:
        raise ValueError(f'Source bytes do not match the lock: {path}')


def download(lock: dict, directory: Path) -> Path:
    if (not isinstance(lock.get('filename'), str) or
            not re.fullmatch(r'[a-zA-Z0-9][a-zA-Z0-9_.-]{0,239}', lock['filename']) or
            type(lock.get('bytes')) is not int or lock['bytes'] <= 0 or
            not isinstance(lock.get('sha256'), str) or not re.fullmatch('[a-f0-9]{64}', lock['sha256'])):
        raise ValueError('Invalid locked download filename, size or digest')
    checked_url(lock['url'], lock['allowed_hosts'])
    directory.mkdir(parents=True, exist_ok=True)
    target = directory / lock['filename']
    if target.exists():
        verify_source(target, lock)
        print(f'Already verified: {target}', flush=True)
        return target
    partial = target.with_suffix(target.suffix + '.part')
    size = partial.stat().st_size if partial.exists() else 0
    if size > lock['bytes']:
        raise ValueError('Partial file exceeds locked source size; inspect it before retrying')
    # Keep enough room to avoid turning a failed download into a full volume.
    if shutil.disk_usage(directory).free < lock['bytes'] - size + 1024**3:
        raise OSError(errno.ENOSPC, 'Not enough free space for this source plus 1 GiB working headroom')
    opener = urllib.request.build_opener(SourceRedirect(lock['allowed_hosts']))
    last_report = time.monotonic()
    for attempt in range(4):
        size = partial.stat().st_size if partial.exists() else 0
        if size == lock['bytes']:
            break
        headers = {'User-Agent': 'BlobsCorpus/0.1', 'Accept-Encoding': 'identity'}
        if size:
            headers['Range'] = f'bytes={size}-'
        try:
            with opener.open(urllib.request.Request(lock['url'], headers=headers), timeout=45) as response:
                checked_url(response.url, lock['allowed_hosts'])
                if response.status == 206:
                    expected = f'bytes {size}-{lock["bytes"] - 1}/{lock["bytes"]}'
                    if response.headers.get('Content-Range') != expected:
                        raise ValueError('Unexpected Content-Range; partial file left untouched')
                elif response.status == 200:
                    size = 0  # Server ignored Range. Restart; never append a full response.
                else:
                    raise ValueError(f'Unexpected HTTP status {response.status}')
                length = response.headers.get('Content-Length')
                if length and int(length) != lock['bytes'] - size:
                    raise ValueError('Response length disagrees with locked source')
                with partial.open('ab' if size else 'wb') as stream:
                    while block := response.read(1024 * 1024):
                        if shutil.disk_usage(directory).free < len(block) + 1024**3:
                            raise OSError(errno.ENOSPC, 'Download paused to preserve 1 GiB working headroom; partial file retained')
                        size += len(block)
                        if size > lock['bytes']:
                            raise ValueError('Source exceeds locked byte count')
                        stream.write(block)
                        if time.monotonic() - last_report > 15:
                            print(f'Downloaded {size:,}/{lock["bytes"]:,} bytes ({size / lock["bytes"]:.1%})', flush=True)
                            last_report = time.monotonic()
                    stream.flush()
                    os.fsync(stream.fileno())
                if size != lock['bytes']:
                    raise OSError('Download ended early')
            break
        except (OSError, urllib.error.URLError) as error:
            if error.errno == errno.ENOSPC or attempt == 3:
                raise
            time.sleep(2 ** attempt)
    verify_source(partial, lock)
    partial.rename(target)
    print(f'Verified SHA-256: {target}', flush=True)
    return target


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('lock', type=Path)
    parser.add_argument('directory', type=Path)
    args = parser.parse_args()
    download(read_lock(args.lock), args.directory)
