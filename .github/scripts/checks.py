"""Select component workflows and verify the required pull-request check."""

import json
import os
from pathlib import Path
import subprocess
import sys

RELEASE_NOTE_PREFIXES = ("android/", "corpus/", "docs/")


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


def changeset_missing(paths):
    """Shipped content needs a changeset; changesets themselves need none."""
    shipped = any(path.startswith(RELEASE_NOTE_PREFIXES) for path in paths)
    noted = any(
        path.startswith(".changeset/") and path != ".changeset/config.json" for path in paths
    )
    return shipped and not noted


def changed_paths(before, after):
    subprocess.run(["git", "diff", "--check", before, after, "--"], check=True)
    listing = subprocess.check_output(
        ["git", "diff", "--name-only", "--no-renames", "-z", before, after, "--"]
    ).decode("utf-8", errors="surrogateescape")
    return [path for path in listing.split("\0") if path]


def pull_request_paths():
    """Return the pull-request diff paths, or None when there is nothing to compare."""
    before = os.environ.get("BASE_SHA", "")
    if os.environ["GITHUB_EVENT_NAME"] != "pull_request" or not before.strip("0"):
        return None
    return changed_paths(before, os.environ["HEAD_SHA"])


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
    elif sys.argv[1:] == ["changesets"]:
        paths = pull_request_paths()
        if paths is None:
            print("No pull request diff to compare; skipping the release note check.")
        elif changeset_missing(paths):
            raise SystemExit(
                "This change reaches a shipped component but carries no release note.\n"
                "Run `npx changeset` in the repository root and commit the new .changeset file, "
                "or `npx changeset add --empty` when it should not appear in the changelog."
            )
        else:
            print("Release note requirement satisfied.")
    elif sys.argv[1:] == ["changes"]:
        before = os.environ.get("BASE_SHA", "")
        if os.environ["GITHUB_EVENT_NAME"] == "workflow_dispatch" or not before.strip("0"):
            selected = {"android": True, "docs": True}
        else:
            selected = affected(changed_paths(before, os.environ["HEAD_SHA"]))
        with Path(os.environ["GITHUB_OUTPUT"]).open("a", encoding="utf-8") as output:
            for component, needed in selected.items():
                output.write(f"{component}={str(needed).lower()}\n")
        print(json.dumps(selected))
    else:
        raise SystemExit("Usage: checks.py changes|changesets|gate")


if __name__ == "__main__":
    main()
