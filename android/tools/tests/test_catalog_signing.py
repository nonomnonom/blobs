import base64
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from sign_catalog import sign_catalog


@unittest.skipUnless(
    importlib.util.find_spec("cryptography"), "requires the publisher dependency group"
)
class CatalogSigningTest(unittest.TestCase):
    def test_p256_envelope_preserves_and_authenticates_the_exact_utf8_payload(self):
        from cryptography.exceptions import InvalidSignature
        from cryptography.hazmat.primitives import hashes, serialization
        from cryptography.hazmat.primitives.asymmetric import ec

        root = Path(__file__).resolve().parents[2]
        payload = (root / "app/src/main/assets/package-catalog.json").read_bytes()
        key = ec.generate_private_key(ec.SECP256R1())
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            private = folder / "ephemeral.pem"
            private.write_bytes(
                key.private_bytes(
                    serialization.Encoding.PEM,
                    serialization.PrivateFormat.PKCS8,
                    serialization.NoEncryption(),
                )
            )
            catalog = folder / "catalog.json"
            catalog.write_bytes(payload)
            output = folder / "catalog.signed.json"
            sign_catalog(catalog, output, private)
            envelope = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(envelope["format"], 1)
            decoded = base64.b64decode(envelope["payload"], validate=True)
            signature = base64.b64decode(envelope["signature"], validate=True)
            self.assertEqual(decoded, payload)
            key.public_key().verify(signature, decoded, ec.ECDSA(hashes.SHA256()))
            with self.assertRaises(InvalidSignature):
                key.public_key().verify(signature, decoded + b" ", ec.ECDSA(hashes.SHA256()))


if __name__ == "__main__":
    unittest.main()
