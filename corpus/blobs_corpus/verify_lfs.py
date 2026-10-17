"""Verify a reviewed edition through a disposable local Git LFS origin and clone."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

from .acquire import digest
from .extract import verified_shards


def verify_lfs(source: Path, output: Path, work: Path | None = None):
    manifest = verified_shards(source)
    if not manifest['complete'] or not manifest.get('curation'):
        raise ValueError('Verify a complete, reviewed edition')
    if output.exists():
        raise FileExistsError(output)
    files = [item for shard in manifest['shards'] for item in shard['files'].values()]
    if work is not None:
        work.mkdir(parents=True, exist_ok=True)
    report = dict(format=1, complete=False, input_sha256=digest(source),
                  files=len(files), unique_lfs_objects=len({item['sha256'] for item in files}),
                  compressed_bytes=sum(item['bytes'] for item in files),
                  largest_file_bytes=max(item['bytes'] for item in files),
                  scope='Local Git LFS object and clean-clone proof only. No public host, billing plan, remote upload or clean Android build is verified.')
    steps = []
    report['steps'] = steps

    def run(args, directory=None, env=None):
        result = subprocess.run(['git', *args], cwd=directory, env=env,
                                capture_output=True, text=True, encoding='utf-8', errors='replace')
        steps.append(dict(command=['git', *args], exit_code=result.returncode,
                          output=(result.stdout + result.stderr)[-12000:]))
        result.check_returncode()
        return result.stdout.strip()

    try:
        with tempfile.TemporaryDirectory(prefix='blobs-lfs-check-', dir=work) as temporary:
            folder = Path(temporary)
            origin, clone = folder / 'origin', folder / 'clone'
            run(['init', '-b', 'main', 'origin'], folder)
            run(['lfs', 'install', '--local'], origin)
            edition = origin / 'records'
            edition.mkdir()
            for item in files:
                shutil.copyfile(source.parent / item['name'], edition / item['name'])
            shutil.copyfile(source, edition / 'manifest.json')
            (origin / '.gitattributes').write_text('records/*.jsonl.gz filter=lfs diff=lfs merge=lfs -text\n')
            run(['add', '.gitattributes', 'records'], origin)
            run(['-c', 'user.name=Blobs verification', '-c', 'user.email=verification@blobs.invalid',
                 '-c', 'commit.gpgsign=false', 'commit', '-m', 'Verify reviewed corpus LFS objects'], origin)
            report['commit'] = run(['rev-parse', 'HEAD'], origin)
            report['git_lfs'] = run(['lfs', 'version'], origin)
            run(['lfs', 'fsck'], origin)
            run(['clone', '--no-local', 'origin', 'clone'], folder,
                env={**os.environ, 'GIT_LFS_SKIP_SMUDGE': '1'})
            run(['lfs', 'install', '--local'], clone)
            run(['lfs', 'pull'], clone)
            run(['lfs', 'fsck'], clone)
            checked = verified_shards(clone / 'records/manifest.json')
            if checked != manifest:
                raise ValueError('Cloned manifest differs from the input edition')
            report['complete'] = True
    except Exception as error:
        report['error'] = str(error)
        raise
    finally:
        output.parent.mkdir(parents=True, exist_ok=True)
        with output.open('x', encoding='utf-8') as stream:
            json.dump(report, stream, indent=2)
            stream.write('\n')
    print(f'Verified {len(files)} files through a clean local LFS clone; {output}', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--work-directory', type=Path)
    args = parser.parse_args()
    verify_lfs(args.source, args.output, args.work_directory)
