#!/usr/bin/env python3
"""Fail a PR when API-affecting code changes without API documentation changes."""
from __future__ import annotations

import subprocess
import sys
from pathlib import PurePosixPath

API_CODE_PREFIX = "InitializrSpringbootProjectFresh/src/main/java/"
API_DOC_PREFIXES = (
    "Docs/Api/",
    "Docs/Api-v2-",
    "Docs/developer-guide.md",
    "Docs/Error-catalog.md",
    "Docs/Webhook-events.md",
)

API_CODE_MARKERS = (
    "Controller.java",
    "/api/",
    "/dto/",
    "/webhook/",
    "/security/",
    "/communication/",
    "/identity/",
    "/crossborder/",
    "/compliance/",
    "/vending/",
)


def changed_files(base: str, head: str) -> list[str]:
    output = subprocess.check_output(
        ["git", "diff", "--name-only", f"{base}...{head}"], text=True
    )
    return [line.strip() for line in output.splitlines() if line.strip()]


def is_api_code(path: str) -> bool:
    if not path.startswith(API_CODE_PREFIX):
        return False
    return path.endswith("Controller.java") or any(marker in path for marker in API_CODE_MARKERS)


def is_api_doc(path: str) -> bool:
    return any(path.startswith(prefix) for prefix in API_DOC_PREFIXES)


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: require_docs_change.py BASE_SHA HEAD_SHA")
    files = changed_files(sys.argv[1], sys.argv[2])
    api_code = sorted(path for path in files if is_api_code(path))
    if not api_code:
        print("No API-affecting source files changed.")
        return
    docs = sorted(path for path in files if is_api_doc(path))
    if docs:
        print("API-affecting change has matching documentation changes:")
        for path in docs:
            print(f"  docs: {path}")
        return

    print("API-affecting source files changed without an API documentation change:", file=sys.stderr)
    for path in api_code:
        print(f"  code: {path}", file=sys.stderr)
    print(
        "Update Docs/Api/cpay-v2-openapi.yaml and/or the relevant API documentation in this PR.",
        file=sys.stderr,
    )
    raise SystemExit(1)


if __name__ == "__main__":
    main()
