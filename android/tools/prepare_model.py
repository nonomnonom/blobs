"""Download a pinned model, verify its bytes, and create a local-install package."""

import argparse
import json
from pathlib import Path
from build_package import package, verify_package
from blobs_corpus.acquire import download

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    pins = json.loads((ROOT / "models.lock.json").read_text(encoding="utf-8"))
    parser.add_argument("model", choices=sorted(pins), default="minicpm5", nargs="?")
    args = parser.parse_args()
    pin = pins[args.model]
    output = ROOT / "packages" / f"{pin['id']}.blobs"
    if output.exists():
        existing = verify_package(output)
        expected = dict(
            format=1,
            kind="model",
            id=pin["id"],
            title=pin["title"],
            version=pin["revision"][:8],
            sha256=pin["sha256"],
            bytes=pin["bytes"],
            license=pin["license"],
            origin=f"https://huggingface.co/{pin['repository']}/tree/{pin['revision']}",
        )
        if existing != expected:
            raise ValueError("Existing model package differs from the locked artifact")
        print(f"Existing package verified: {output}")
        return
    cache = ROOT / ".local"
    cache.mkdir(exist_ok=True)
    url = f"https://huggingface.co/{pin['repository']}/resolve/{pin['revision']}/{pin['file']}"
    target = download(
        dict(
            filename=pin["file"],
            url=url,
            bytes=pin["bytes"],
            sha256=pin["sha256"],
            allowed_hosts=[
                "huggingface.co",
                "cdn-lfs.huggingface.co",
                "cdn-lfs-us-1.hf.co",
                "cas-bridge.xethub.hf.co",
                "us.aws.cdn.hf.co",
            ],
        ),
        cache,
    )
    package(
        target,
        output,
        kind="model",
        id=pin["id"],
        version=pin["revision"][:8],
        title=pin["title"],
        license=pin["license"],
        origin=f"https://huggingface.co/{pin['repository']}/tree/{pin['revision']}",
    )


if __name__ == "__main__":
    main()
