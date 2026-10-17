"""Assemble a local sideload folder after verification; never publish or copy signing keys."""
import argparse
from contextlib import contextmanager
import hashlib
import json
import os
import re
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile
from verify_apk import verify, sdk_directory
from build_package import verify_package
from verify_release import verify_kit
from blobs_corpus.footprint import release_report as build_footprint_report

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


check_package = verify_package


def verification_guide(version, source_name):
    return (
        f'# Verify Blobs {version}\n\n'
        'Keep every file in this folder together. Before copying it to a phone, extract '
        f'`{source_name}` into a temporary folder and run:\n\n'
        '```text\n'
        'python blobs/android/tools/verify_release.py PATH_TO_THIS_RELEASE_FOLDER\n'
        '```\n\n'
        'The command checks `FOOTPRINT.json`, the recorded storage and import peaks, every SHA-256 entry, '
        'both streamed package payloads, the recorded offline APK gates and the source archive contents. '
        'It prints the SHA-256 of `SHA256SUMS.txt`; compare that value with the digest published '
        'alongside the release. For an independent APK inspection, also run `verify_apk.py` and '
        '`apksigner verify --verbose --print-certs` as described in `PREPARATION.md`.\n'
    )


def write_source_archive(output):
    excluded = {'.local', '.gradle', '.kotlin', '.idea', '.cxx', 'build', 'reports',
                '__pycache__', 'node_modules', 'evidence', '.venv', '.cache'}
    generated = {ROOT / 'packages', ROOT / 'release', ROOT.parent / 'corpus/work',
                 ROOT.parent / 'corpus/artifacts'}
    with zipfile.ZipFile(output, 'x', zipfile.ZIP_DEFLATED) as archive:
        for base in [ROOT, ROOT.parent / 'corpus', ROOT.parent / '.github', ROOT.parent / 'docs']:
            for directory, folders, files in os.walk(base):
                folders[:] = sorted(p for p in folders if p not in excluded and
                                    Path(directory) / p not in generated and
                                    not p.startswith(('llama.cpp-', 'kleidiai-')))
                for name in sorted(files):
                    path = Path(directory) / name
                    if name == 'local.properties' or path.suffix in ('.jks', '.keystore', '.pyc', '.log'):
                        continue
                    archive.write(path, 'blobs/' + path.relative_to(ROOT.parent).as_posix())
        for name in ['README.md', 'CONTRIBUTING.md', '.gitignore', '.gitattributes']:
            archive.write(ROOT.parent / name, 'blobs/' + name)


@contextmanager
def staged_release(destination):
    destination.parent.mkdir(parents=True, exist_ok=True)
    lock = destination.with_name(f'.{destination.name}.assembling')
    # Exclusive ownership prevents two assemblers publishing the same edition.
    # A crashed process leaves the lock for inspection before an explicit retry.
    with lock.open('x', encoding='utf-8') as owner:
        try:
            owner.write(str(os.getpid()))
            owner.flush()
            if destination.exists():
                raise FileExistsError(destination)
            with tempfile.TemporaryDirectory(prefix=f'.{destination.name}-', dir=destination.parent) as folder:
                staged = Path(folder) / 'kit'
                staged.mkdir()
                yield staged
                if destination.exists():
                    raise FileExistsError(destination)
                os.rename(staged, destination)
        finally:
            # Close before unlinking: Windows does not permit deleting this handle.
            owner.close()
            lock.unlink()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--model', type=Path, required=True)
    parser.add_argument('--library', type=Path, required=True)
    args = parser.parse_args()
    apk = ROOT / 'app/build/outputs/apk/release/app-release.apk'
    report = verify(apk)
    if report['application_id'] != 'dev.blobs.app':
        raise ValueError('Only the production application ID can enter a release kit')
    version = report['version_name']
    if not re.fullmatch(r'[0-9A-Za-z][0-9A-Za-z.+-]{0,63}', version):
        raise ValueError('Invalid release version')
    destination = ROOT / 'release' / version
    if destination.exists():
        raise FileExistsError(f'Release editions are immutable: {destination}')
    signer = sdk_directory() / 'build-tools/36.0.0' / ('apksigner.bat' if os.name == 'nt' else 'apksigner')
    signature = subprocess.check_output([str(signer), 'verify', '--verbose', '--print-certs', str(apk)], text=True)
    packages = [(args.model, args.model.name), (args.library, args.library.name)]
    if any(source.suffix != '.blobs' for source, _ in packages):
        raise ValueError('Release packages must use .blobs filenames')
    if args.model.name.casefold() == args.library.name.casefold():
        raise ValueError('Model and library package filenames must differ')
    manifests = {name: check_package(source) for source, name in packages}
    if manifests[args.model.name]['kind'] != 'model' or manifests[args.library.name]['kind'] != 'library':
        raise ValueError('Expected one model package and one library package')
    with staged_release(destination) as staged:
        count = write_release(staged, apk, version, report, signature, packages, manifests)
        verification = verify_kit(staged)
    print(f'Prepared {destination}; {count} files, no publication performed.')
    print(f"SHA256SUMS.txt SHA-256: {verification['checksums']['sha256']}")


def write_release(destination, apk, version, report, signature, packages, manifests):
    shutil.copy2(apk, destination / f'blobs-{version}.apk')
    if digest(destination / f'blobs-{version}.apk') != report['sha256']:
        raise ValueError('APK changed after verification')
    for source, name in packages:
        shutil.copy2(source, destination / name)
        if check_package(destination / name) != manifests[name]:
            raise ValueError(f'Package changed after verification: {name}')
    for source, name in [('docs/RELEASE.md', 'PREPARATION.md'),
                         ('THIRD_PARTY_NOTICES.md', 'THIRD_PARTY_NOTICES.md')]:
        shutil.copy2(ROOT / source, destination / name)
    installed_bytes = sum(manifest['bytes'] for manifest in manifests.values())
    incoming_bytes = sum(source.stat().st_size for source, _ in packages)
    (destination / 'INSTALL.md').write_text(
        f'# Blobs {version}\n\n'
        'Before copying files to a phone, follow `VERIFY.md` and compare the reported checksum-inventory digest with the value published alongside this release.\n\n'
        f'1. Copy `blobs-{version}.apk`, `{packages[0][1]}` and `{packages[1][1]}` to local Android storage.\n'
        f'2. Open `blobs-{version}.apk` and allow installation from that file source if Android requests it.\n'
        f'3. Open Blobs. Choose **Add from this phone**, select `{packages[0][1]}`, review it and choose **Add package**.\n'
        f'4. Add `{packages[1][1]}` the same way. Wait for verification and installation to finish.\n'
        '5. Start a conversation. Tap a citation to read its saved passage or full source inside Blobs.\n\n'
        'No account, Play Store or runtime internet is required.\n\n'
        f'Incoming packages occupy {incoming_bytes:,} bytes; installed payloads occupy {installed_bytes:,} bytes. '
        '`FOOTPRINT.json` records the edition total and import peak with its assumptions. '
        'Allow additional space for the APK, app data and at least 64 MiB import headroom. '
        'After a successful import, the original package may be moved off the phone if a safe reinstall copy exists. '
        'To update an existing installation, install an APK signed with the same key over it. '
        'Uninstalling the app removes its private conversations and packages; export conversations first.\n\n'
        'Android 9+ on ARM64 or x86_64 is the API/ABI boundary. Model performance depends on the device. '
        'PACKAGES.json and SHA256SUMS.txt identify these exact files. '
        'The source archive includes the public preparation, build and verification tooling.\n', encoding='utf-8')
    shutil.copytree(ROOT / 'app/src/main/assets/licenses', destination / 'licenses', dirs_exist_ok=True)
    delivered_report = dict(report, apk=f'blobs-{version}.apk')
    (destination / 'APK-VERIFICATION.json').write_text(json.dumps(delivered_report, indent=2), encoding='utf-8')
    footprint = build_footprint_report(
        destination / 'APK-VERIFICATION.json',
        [destination / name for _, name in packages],
        ROOT / 'app/src/main/kotlin/dev/blobs/app/data/packages/PackageInstaller.kt',
    )
    (destination / 'FOOTPRINT.json').write_text(json.dumps(footprint, indent=2), encoding='utf-8')
    (destination / 'SIGNATURE.txt').write_text(signature, encoding='utf-8')
    (destination / 'PACKAGES.json').write_text(json.dumps(manifests, indent=2), encoding='utf-8')
    source_name = f'blobs-{version}-source.zip'
    write_source_archive(destination / source_name)
    (destination / 'VERIFY.md').write_text(verification_guide(version, source_name), encoding='utf-8')
    checksums = [f'{digest(p)}  {p.relative_to(destination).as_posix()}' for p in sorted(destination.rglob('*'))
                 if p.is_file() and p.name != 'SHA256SUMS.txt']
    (destination / 'SHA256SUMS.txt').write_text('\n'.join(checksums) + '\n', encoding='utf-8')
    return len(checksums)


if __name__ == '__main__':
    main()
