import json
from pathlib import Path
import sqlite3
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from build_package import build_library, package, passages
from package_release import write_source_archive
import package_release
import prepare_model
import verify_release


def fixture_source_archive(path):
    with zipfile.ZipFile(path, 'x') as archive:
        for name in sorted(verify_release.SOURCE_REQUIRED):
            relative = name.removeprefix('blobs/')
            source = (package_release.ROOT / relative.removeprefix('android/'))
            if not relative.startswith('android/'):
                source = package_release.ROOT.parent / relative
            if source.is_file():
                archive.write(source, name)
            else:
                archive.writestr(name, 'fixture source')


class PackageTests(unittest.TestCase):
    def test_complete_release_publishes_selected_files_with_matching_checksums(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            apk = root / 'app/build/outputs/apk/release/app-release.apk'
            apk.parent.mkdir(parents=True)
            apk.write_bytes(b'APK fixture; signature and APK inspection are mocked in this packaging test')
            for relative in ('docs/RELEASE.md', 'THIRD_PARTY_NOTICES.md',
                             'app/src/main/assets/licenses/TEST.txt',
                             'app/src/main/kotlin/dev/blobs/app/data/packages/PackageInstaller.kt',
                             'app/build/reports/release-evidence/old-build.txt'):
                path = root / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text('Original fixture text', encoding='utf-8')
            installer = root / 'app/src/main/kotlin/dev/blobs/app/data/packages/PackageInstaller.kt'
            installer.write_text(
                'expected.bytes - resumeBytes + 64L * 1024 * 1024\nexpected.bytes < 48_000_000_000L\n',
                encoding='utf-8',
            )
            inputs = []
            for kind, payload in [('model', b'GGUF fixture'), ('library', b'Original library fixture')]:
                raw = root / f'{kind}.raw'
                raw.write_bytes(payload)
                target = root / f'selected-{kind}.blobs'
                package(raw, target, kind=kind, id=kind, version='1', title=kind,
                        license='CC0', origin='local:test')
                inputs.append(target)
            report = dict(apk='app-release.apk', application_id='dev.blobs.app',
                          version_code=2, version_name='0.2.0',
                          bytes=apk.stat().st_size, sha256=package_release.digest(apk),
                          internet_permission=True, zip_alignment_16kb=True,
                          native_libraries=[
                              {'file': 'lib/arm64-v8a/libblobs.so'},
                              {'file': 'lib/x86_64/libblobs.so'},
                          ])
            catalog = {'format': 1, 'release_tag': 'v0.2.0', 'packages': []}
            for item in inputs:
                manifest = package_release.check_package(item)
                catalog['packages'].append({
                    'file_name': item.name,
                    'url': f'https://github.com/nonomnonom/blobs/releases/download/v0.2.0/{item.name}',
                    'archive_bytes': item.stat().st_size,
                    'archive_sha256': package_release.digest(item),
                    'manifest': manifest,
                })
            catalog_path = root / 'app/src/main/assets/package-catalog.json'
            catalog_path.parent.mkdir(parents=True, exist_ok=True)
            catalog_path.write_text(json.dumps(catalog), encoding='utf-8')
            with patch.object(package_release, 'ROOT', root), patch.object(package_release, 'verify', return_value=report), patch.object(package_release, 'verify_catalog_packages'), patch.object(package_release, 'sdk_directory', return_value=root), patch.object(package_release.subprocess, 'check_output', return_value='Fixture certificate'), patch.object(package_release, 'write_source_archive', side_effect=fixture_source_archive), patch.object(sys, 'argv', ['package_release.py', '--model', str(inputs[0]), '--library', str(inputs[1])]):
                package_release.main()
            kit = root / 'release/0.2.0'
            self.assertEqual(list((root / 'release').iterdir()), [kit])
            self.assertIn('selected-model.blobs', (kit / 'INSTALL.md').read_text())
            self.assertIn('selected-library.blobs', (kit / 'INSTALL.md').read_text())
            self.assertTrue(json.loads((kit / 'FOOTPRINT.json').read_text())['checks']['bounty_storage_limit']['passes'])
            self.assertEqual(json.loads((kit / 'APK-VERIFICATION.json').read_text())['apk'], 'blobs-0.2.0.apk')
            self.assertFalse((kit / 'evidence').exists(), 'Old runtime evidence must not enter a new release')
            for line in (kit / 'SHA256SUMS.txt').read_text().splitlines():
                checksum, relative = line.split('  ', 1)
                self.assertEqual(package_release.digest(kit / relative), checksum)
            self.assertEqual((kit / inputs[0].name).read_bytes(), inputs[0].read_bytes())
            verified = verify_release.verify_kit(kit)
            self.assertTrue(verified['complete'])
            self.assertEqual({item['kind'] for item in verified['packages']}, {'model', 'library'})
            self.assertEqual(verified['checksums']['entries'],
                             len([path for path in kit.rglob('*') if path.is_file()]) - 1)
            original_install = (kit / 'INSTALL.md').read_text(encoding='utf-8')
            (kit / 'INSTALL.md').write_text('tampered after transport', encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'Checksum mismatch: INSTALL.md'):
                verify_release.verify_kit(kit)
            (kit / 'INSTALL.md').write_text(original_install, encoding='utf-8')
            replacement_raw = root / 'replacement.gguf'
            replacement_raw.write_bytes(b'GGUF replacement payload')
            replacement = root / 'replacement.blobs'
            package(replacement_raw, replacement, kind='model', id='replacement', version='2',
                    title='replacement', license='CC0', origin='local:replacement')
            (kit / inputs[0].name).write_bytes(replacement.read_bytes())
            lines = (kit / 'SHA256SUMS.txt').read_text(encoding='utf-8').splitlines()
            lines = [
                f'{package_release.digest(kit / inputs[0].name)}  {inputs[0].name}'
                if line.endswith(f'  {inputs[0].name}') else line
                for line in lines
            ]
            (kit / 'SHA256SUMS.txt').write_text('\n'.join(lines) + '\n', encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'Package manifest differs from PACKAGES.json'):
                verify_release.verify_kit(kit)

    def test_verified_existing_model_does_not_redownload_a_working_copy(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            pin = dict(id='model', revision='123456789', repository='Publisher/Model',
                       title='Model', bytes=4, sha256='0' * 64, license='Test', file='model.gguf')
            (root / 'models.lock.json').write_text(json.dumps({'small': pin}))
            output = root / 'packages/qwen3-small.blobs'
            output.parent.mkdir()
            output.touch()
            manifest = dict(format=1, kind='model', id='model', version='12345678', title='Model',
                            bytes=4, sha256='0' * 64, license='Test',
                            origin='https://huggingface.co/Publisher/Model/tree/123456789')
            with patch.object(prepare_model, 'ROOT', root), patch.object(prepare_model, 'verify_package', return_value=manifest), patch.object(prepare_model, 'download') as download, patch.object(sys, 'argv', ['prepare_model.py', 'small']):
                prepare_model.main()
                download.assert_not_called()
                manifest['sha256'] = '1' * 64
                with self.assertRaisesRegex(ValueError, 'differs'):
                    prepare_model.main()
                download.assert_not_called()
            self.assertFalse((root / '.local').exists())

    def test_failed_release_is_not_visible_and_a_retry_can_publish(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            destination = root / '0.2.0'
            with self.assertRaisesRegex(OSError, 'copy interrupted'):
                with package_release.staged_release(destination) as staged:
                    (staged / 'model.blobs').write_bytes(b'partial copy')
                    self.assertFalse(destination.exists())
                    raise OSError('copy interrupted')
            self.assertFalse(destination.exists())
            self.assertEqual(list(root.iterdir()), [])
            with package_release.staged_release(destination) as staged:
                (staged / 'marker').write_bytes(b'complete')
                with self.assertRaises(FileExistsError):
                    with package_release.staged_release(destination):
                        self.fail('A second assembler must not own this edition')
            self.assertEqual((destination / 'marker').read_bytes(), b'complete')
            with self.assertRaises(FileExistsError):
                with package_release.staged_release(destination):
                    self.fail('An existing edition must remain immutable')
            self.assertEqual((destination / 'marker').read_bytes(), b'complete')

    def test_changed_apk_never_enters_a_release(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            apk = root / 'app.apk'
            apk.write_bytes(b'changed after inspection')
            destination = root / 'release'
            with self.assertRaisesRegex(ValueError, 'APK changed'):
                with package_release.staged_release(destination) as staged:
                    package_release.write_release(staged, apk, '1', {'sha256': '0' * 64}, '', [], {})
            self.assertFalse(destination.exists())

    def test_existing_release_is_refused_before_any_copy_or_signer_call(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            previous = root / 'release/0.2.0'
            previous.mkdir(parents=True)
            marker = previous / 'original.apk'
            marker.write_bytes(b'preserve this release')
            with patch.object(package_release, 'ROOT', root), patch.object(package_release, 'verify', return_value={'application_id':'dev.blobs.app','version_name':'0.2.0'}), patch.object(package_release.subprocess, 'check_output') as signer, patch.object(sys, 'argv', ['package_release.py','--model','model.blobs','--library','library.blobs']):
                with self.assertRaises(FileExistsError):
                    package_release.main()
                signer.assert_not_called()
            self.assertEqual(marker.read_bytes(), b'preserve this release')
            self.assertEqual(list(previous.iterdir()), [marker])

    def test_engine_apk_cannot_be_assembled_as_production_delivery(self):
        with patch.object(package_release, 'verify', return_value={'application_id':'dev.blobs.app.engine','version_name':'0.2.0'}), patch.object(sys, 'argv', ['package_release.py','--model','model.blobs','--library','library.blobs']):
            with self.assertRaisesRegex(ValueError, 'production application ID'):
                package_release.main()

    def test_release_package_names_must_match_the_in_app_catalog(self):
        with self.assertRaisesRegex(ValueError, 'filename differs from the in-app download catalog'):
            package_release.verify_catalog_packages(Path('selected-model.blobs'),
                                                    Path('selected-library.blobs'), '0.3.0')

    def test_package_names_cannot_collide_on_a_case_insensitive_filesystem(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            with patch.object(package_release, 'ROOT', root), patch.object(package_release, 'verify', return_value={'application_id':'dev.blobs.app','version_name':'0.2.0'}), patch.object(package_release, 'sdk_directory', return_value=root), patch.object(package_release.subprocess, 'check_output', return_value='Fixture certificate'), patch.object(sys, 'argv', ['package_release.py','--model','model/Package.blobs','--library','library/package.blobs']):
                with self.assertRaisesRegex(ValueError, 'filenames must differ'):
                    package_release.main()
            self.assertFalse((root / 'release').exists())

    def test_source_delivery_keeps_package_installer_code_but_excludes_generated_assets(self):
        with tempfile.TemporaryDirectory() as folder:
            workspace = Path(folder) / 'workspace'
            root = workspace / 'android'
            output = Path(folder) / 'source.zip'
            expected = [
                'android/app/src/main/assets/package-catalog.json',
                'android/app/src/main/kotlin/dev/blobs/app/data/packages/PackageCatalog.kt',
                'android/app/src/main/kotlin/dev/blobs/app/data/packages/PackageDownloader.kt',
                'android/app/src/main/kotlin/dev/blobs/app/data/packages/PackageInstaller.kt',
                'android/tools/verify_release.py',
                'corpus/uv.lock',
                'corpus/blobs_corpus/curate.py',
                'android/docs/ARCHITECTURE.md',
                'android/docs/RELEASE.md',
                'corpus/README.md',
                'README.md',
                'CONTRIBUTING.md',
                '.gitignore',
                '.gitattributes',
            ]
            for name in expected:
                path = workspace / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text('public source fixture', encoding='utf-8')
            for name in [
                'android/packages/library.blobs',
                'android/.local/signing/key.jks',
                'corpus/work/checkpoint.json',
                'corpus/artifacts/records/shard.jsonl.gz',
                'corpus/reports/private-run.json',
                'corpus/evaluation/reports/template.md',
                'android/build/generated.txt',
            ]:
                path = workspace / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text('generated fixture', encoding='utf-8')
            with patch.object(package_release, 'ROOT', root):
                write_source_archive(output)
            with zipfile.ZipFile(output) as archive:
                names = archive.namelist()
                self.assertIn('blobs/android/app/src/main/assets/package-catalog.json', names)
                self.assertIn('blobs/android/app/src/main/kotlin/dev/blobs/app/data/packages/PackageDownloader.kt', names)
                self.assertIn('blobs/android/app/src/main/kotlin/dev/blobs/app/data/packages/PackageInstaller.kt', names)
                self.assertIn('blobs/android/tools/verify_release.py', names)
                self.assertIn('blobs/corpus/uv.lock', names)
                self.assertIn('blobs/corpus/blobs_corpus/curate.py', names)
                self.assertIn('blobs/android/docs/ARCHITECTURE.md', names)
                self.assertIn('blobs/android/docs/RELEASE.md', names)
                self.assertIn('blobs/corpus/README.md', names)
                self.assertNotIn('blobs/docs/BOUNTY_COMPLIANCE.md', names)
                self.assertFalse(any(name.startswith('blobs/design/') for name in names))
                self.assertFalse(any(name.startswith('blobs/corpus/reports/') for name in names))
                self.assertFalse(any(name.startswith('blobs/corpus/work/') for name in names))
                self.assertFalse(any(name.startswith('blobs/corpus/artifacts/') for name in names))
                self.assertNotIn('blobs/AGENTS.md', names)
                self.assertNotIn('blobs/DESIGN.md', names)
                self.assertNotIn('blobs/UX-RESEARCH.md', names)
                self.assertFalse(any(name.startswith(('blobs/android/packages/', 'blobs/android/.local/', 'blobs/corpus/work/', 'blobs/corpus/artifacts/')) for name in names))
                self.assertFalse(any(name.endswith(('.jks', '.keystore', '.gguf')) for name in names))
            with self.assertRaises(FileExistsError):
                write_source_archive(output)

    def test_heading_owns_its_passage_without_swallowing_body(self):
        rows = list(passages('Intro.\n\n=== Cooling ===\nTrees provide shade.\n\n'
                             '==== Water ====\nEvaporation cools air.', 'Trees'))
        self.assertEqual([r[0] for r in rows], ['Trees', 'Cooling', 'Water'])
        self.assertEqual(rows[1][1], 'Trees provide shade.')
        self.assertNotIn('===', ''.join(r[1] for r in rows))

    def test_long_paragraph_keeps_words_and_bounds(self):
        body = ' '.join('word' + str(i) for i in range(1200))
        rows = list(passages(body, 'Long'))
        self.assertGreater(len(rows), 1)
        self.assertTrue(all(len(text) <= 1400 for _, text in rows))
        self.assertEqual(' '.join(text for _, text in rows).split(), body.split())

    def test_built_library_is_searchable_with_provenance(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            item = dict(id='trees', title='Trees', publisher='Test author', url='local:test',
                        license='Test text', updated='2026-10-17', saved='2026-10-17',
                        body='=== Cooling ===\nTrees shade paved streets.', collection='Test')
            source, db = root / 'input.jsonl', root / 'library.sqlite'
            source.write_text(json.dumps(item) + '\n', encoding='utf-8')
            build_library(source, db)
            with sqlite3.connect(db) as connection:
                self.assertEqual(connection.execute(
                    "SELECT count(*) FROM passages_fts WHERE passages_fts MATCH 'shade'"
                ).fetchone()[0], 1)
                self.assertEqual(connection.execute('SELECT section FROM passages').fetchone()[0], 'Cooling')
            connection.close()
            target = root / 'test.blobs'
            package(db, target, kind='library', id='test', version='1', title='Test',
                    license='Test text', origin='local:test')
            with zipfile.ZipFile(target) as archive:
                self.assertEqual(archive.namelist(), ['manifest.json', 'library.sqlite'])
                manifest = json.loads(archive.read('manifest.json'))
                self.assertEqual(manifest['bytes'], db.stat().st_size)
            with self.assertRaises(FileExistsError):
                package(db, target, kind='library', id='test', version='1', title='Test',
                        license='Test text', origin='local:test')


if __name__ == '__main__':
    unittest.main()
