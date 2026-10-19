import hashlib
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from archive_release import archive_release


class ArchiveReleaseTest(unittest.TestCase):
    def fixture(self, root):
        kit = root / 'release'
        (kit / 'licenses').mkdir(parents=True)
        payload = b'License notice\n'
        (kit / 'licenses/NOTICE.txt').write_bytes(payload)
        inventory = f'{hashlib.sha256(payload).hexdigest()}  licenses/NOTICE.txt\n'.encode()
        (kit / 'SHA256SUMS.txt').write_bytes(inventory)
        report = {'version': '1.2.3', 'checksums': {'sha256': hashlib.sha256(inventory).hexdigest()}}
        return kit, report

    def test_preserves_nested_files_and_refuses_existing_or_internal_output(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            kit, report = self.fixture(root)
            output = root / 'kit.zip'
            with patch('archive_release.verify_kit', return_value=report):
                result = archive_release(kit, output)
                with self.assertRaises(FileExistsError):
                    archive_release(kit, output)
                with self.assertRaises(ValueError):
                    archive_release(kit, kit / 'nested/kit.zip')
            with zipfile.ZipFile(output) as archive:
                self.assertEqual(archive.read('blobs-1.2.3/licenses/NOTICE.txt'), b'License notice\n')
                self.assertEqual(len(archive.namelist()), 2)
            self.assertEqual(result['sha256'], hashlib.sha256(output.read_bytes()).hexdigest())

    def test_rejects_mutation_after_verification_without_publishing(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            kit, report = self.fixture(root)
            def verified_then_changed(_):
                (kit / 'licenses/NOTICE.txt').write_bytes(b'changed')
                return report
            output = root / 'kit.zip'
            with patch('archive_release.verify_kit', side_effect=verified_then_changed):
                with self.assertRaisesRegex(ValueError, 'changed while archiving'):
                    archive_release(kit, output)
            self.assertFalse(output.exists())
            self.assertFalse(list(root.glob('.offline-kit-*')))


if __name__ == '__main__':
    unittest.main()
