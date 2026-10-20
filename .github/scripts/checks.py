"""Select component workflows and verify the required pull-request check."""

import json
import os
from pathlib import Path
import subprocess
import sys


def affected(paths):
    shared = any(path.startswith((".github/workflows/", ".github/scripts/")) for path in paths)
    return {
        "android": shared
        or any(
            path.startswith(("android/", "corpus/"))
            or path in {".clang-format", "ruff.toml", ".editorconfig"}
            for path in paths
        ),
        "docs": shared
        or any(
            path.startswith(("docs/", "android/app/src/main/res/font/"))
            or path == "android/app/src/main/assets/package-catalog.json"
            for path in paths
        ),
    }


def gate_passes(results):
    changes = results.get("changes", {})
    if changes.get("result") != "success":
        return False
    for component in ("android", "docs"):
        selected = changes.get("outputs", {}).get(component)
        if selected not in {"true", "false"}:
            return False
        expected = "success" if selected == "true" else "skipped"
        if results.get(component, {}).get("result") != expected:
            return False
    return True


def main():
    if sys.argv[1:] == ["gate"]:
        if not gate_passes(json.loads(os.environ["CHECK_RESULTS"])):
            raise SystemExit("A required component failed, was cancelled, or did not run.")
        print("Every selected component passed.")
    elif sys.argv[1:] == ["changes"]:
        before = os.environ.get("BASE_SHA", "")
        if os.environ["GITHUB_EVENT_NAME"] == "workflow_dispatch" or not before.strip("0"):
            selected = {"android": True, "docs": True}
        else:
            subprocess.run(
                ["git", "diff", "--check", before, os.environ["HEAD_SHA"], "--"], check=True
            )
            paths = (
                subprocess.check_output(
                    [
                        "git",
                        "diff",
                        "--name-only",
                        "--no-renames",
                        "-z",
                        before,
                        os.environ["HEAD_SHA"],
                        "--",
                    ]
                )
                .decode("utf-8", errors="surrogateescape")
                .split("\0")
            )
            selected = affected(paths)
        with Path(os.environ["GITHUB_OUTPUT"]).open("a", encoding="utf-8") as output:
            for component, needed in selected.items():
                output.write(f"{component}={str(needed).lower()}\n")
        print(json.dumps(selected))
    else:
        raise SystemExit("Usage: checks.py changes|gate")


if __name__ == "__main__":
    main()
