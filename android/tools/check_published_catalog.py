"""Verify the published Blobs catalog the way the app does: signature, revision, and content."""

import base64
import json
import urllib.request
from pathlib import Path

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

URL = "https://huggingface.co/datasets/nonomnonom/blobs-catalog/resolve/main/catalog.signed.json"
ANDROID = Path(__file__).resolve().parents[1]


def main() -> None:
    envelope = json.loads(urllib.request.urlopen(URL, timeout=120).read())
    payload = base64.b64decode(envelope["payload"])
    for name in ("catalog-public-key-v3.pem", "catalog-public-key-v2.pem"):
        key = serialization.load_pem_public_key(
            (ANDROID / "app/src/main/assets" / name).read_bytes()
        )
        try:
            key.verify(base64.b64decode(envelope["signature"]), payload, ec.ECDSA(hashes.SHA256()))
            print(f"signature verified with {name}")
            break
        except Exception:
            print(f"{name} did not verify")
    else:
        raise SystemExit("catalog signature did not verify with either trusted key")

    catalog = json.loads(payload)
    models = [p["manifest"] for p in catalog["packages"] if p["manifest"]["kind"] == "model"]
    libraries = [p["manifest"] for p in catalog["packages"] if p["manifest"]["kind"] == "library"]
    print("revision", catalog["revision"], "release_tag", catalog.get("release_tag"))
    print(
        "packages", len(catalog["packages"]), f"({len(models)} models, {len(libraries)} libraries)"
    )
    for model in models:
        entry = next(p for p in catalog["packages"] if p["manifest"]["id"] == model["id"])
        print(
            "  model %-18s %-9s %10d B  band=%-6s min_ram=%-6s %s"
            % (
                model["id"],
                model["version"],
                model["bytes"],
                entry["recommended_up_to_memory_mb"],
                entry["compatibility"]["minimum_ram_mb"],
                entry["download_sha256"][:16],
            )
        )
    shipped = json.loads((ANDROID / "app/src/main/assets/package-catalog.json").read_text("utf-8"))
    same = shipped == catalog
    print("bundled asset matches published catalog:", same)
    if not same:
        print("  bundled revision", shipped["revision"], "packages", len(shipped["packages"]))
        print(
            "  ids differ:",
            sorted({p["manifest"]["id"] for p in shipped["packages"]})
            != sorted({p["manifest"]["id"] for p in catalog["packages"]}),
        )


if __name__ == "__main__":
    main()
