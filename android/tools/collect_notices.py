"""Collect notices from resolved runtime artifacts already in the Gradle cache."""
import hashlib
import io
import os
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CACHE = Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle')) / 'caches/modules-2/files-2.1'
output = ['Blobs runtime dependency inventory',
          'Coordinates include platform constraints and variants; R8 may remove unused code.',
          'Collected from the locked Gradle runtime graph and cached publisher artifacts.\n']
notices = {}
missing = []


def inspect(data, owner):
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        for name in archive.namelist():
            leaf = name.rsplit('/', 1)[-1].lower()
            if (leaf.startswith(('license', 'notice', 'copyright')) and
                    not name.endswith('/') and not leaf.endswith('.class')):
                content = archive.read(name).decode('utf-8', errors='replace')
                digest = hashlib.sha256(content.encode()).hexdigest()
                entry = notices.setdefault(digest, [content, set()])
                entry[1].add(owner + ': ' + name)
            elif leaf == 'classes.jar':
                inspect(archive.read(name), owner)


for line in (ROOT / 'app/gradle.lockfile').read_text().splitlines():
    if '=' not in line or line.startswith('#'):
        continue
    coordinate, configurations = line.split('=', 1)
    if 'releaseRuntimeClasspath' not in configurations.split(',') or coordinate == 'empty':
        continue
    group, artifact, version = coordinate.split(':')
    folder = CACHE / group / artifact / version
    poms = list(folder.glob('*/*.pom'))
    names = []
    for pom in poms:
        root = ET.fromstring(pom.read_text(encoding='utf-8'))
        for license in root.findall('.//{*}licenses/{*}license'):
            name = license.findtext('{*}name', '')
            url = license.findtext('{*}url', '')
            names.append(f'{name} ({url})')
    if not names and coordinate == 'com.google.guava:listenablefuture:1.0':
        names = ['Apache License 2.0 (https://github.com/google/guava/blob/master/LICENSE)']
    if not names:
        missing.append(coordinate)
    output.append(coordinate + '\n  ' + '; '.join(names or ['See bundled upstream notices.']))
    for path in folder.glob('*/*'):
        if path.suffix in ('.jar', '.aar'):
            inspect(path.read_bytes(), coordinate)

for content, owners in notices.values():
    output.append('\n' + '\n'.join(sorted(owners)) + '\n\n' + content)
target = ROOT / 'app/src/main/assets/licenses/Runtime-dependencies.txt'
target.write_text('\n\n'.join(output).rstrip('\n') + '\n', encoding='utf-8')
print(f'Collected {len(notices)} distinct upstream notice texts into {target.name}')
if missing:
    print('No POM license element: ' + ', '.join(missing))
