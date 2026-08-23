#!/usr/bin/env python3
"""Conservative Spring controller to OpenAPI drift report.

This scanner is intentionally advisory because Spring mappings can be composed dynamically.
The PR documentation-change gate is the hard enforcement mechanism. Missing paths reported here
are written to the workflow log so the baseline can be reduced without creating false failures.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path
from typing import Iterable

import yaml

ROOT = Path("InitializrSpringbootProjectFresh/src/main/java")
MAPPING = re.compile(
    r"@(GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping|RequestMapping)\s*\((.*?)\)",
    re.DOTALL,
)
QUOTED = re.compile(r'"([^"\\]*(?:\\.[^"\\]*)*)"')
CLASS_DECL = re.compile(r"\bclass\s+[A-Za-z0-9_]+")

# These surfaces are intentionally not part of the merchant OpenAPI contract.
IGNORED_PREFIXES = (
    "/actuator",
    "/status",
    "/transactions",
    "/admins",
    "/audittrail",
    "/merchants",
    "/settings",
)


def normalize(path: str) -> str:
    path = path.replace("\\/", "/").strip()
    if not path.startswith("/"):
        path = "/" + path
    while "//" in path:
        path = path.replace("//", "/")
    return path.rstrip("/") or "/"


def first_path(mapping_args: str) -> str | None:
    match = QUOTED.search(mapping_args)
    if not match:
        return None
    return normalize(match.group(1))


def controller_paths(text: str) -> set[str]:
    mappings = list(MAPPING.finditer(text))
    if not mappings:
        return set()

    class_pos = CLASS_DECL.search(text)
    class_prefix = ""
    if class_pos:
        before = [m for m in mappings if m.start() < class_pos.start() and m.group(1) == "RequestMapping"]
        if before:
            class_prefix = first_path(before[-1].group(2)) or ""

    found: set[str] = set()
    for mapping in mappings:
        if class_pos and mapping.start() < class_pos.start():
            continue
        if mapping.group(1) == "RequestMapping":
            continue
        suffix = first_path(mapping.group(2)) or ""
        combined = normalize((class_prefix or "") + "/" + (suffix or ""))
        if not any(combined.startswith(prefix) for prefix in IGNORED_PREFIXES):
            found.add(combined)
    return found


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: controller_drift.py OPENAPI_YAML")
    with Path(sys.argv[1]).open("r", encoding="utf-8") as handle:
        spec = yaml.safe_load(handle)
    documented = {normalize(path) for path in (spec.get("paths") or {}).keys()}

    discovered: set[str] = set()
    for source in ROOT.rglob("*Controller.java"):
        try:
            discovered.update(controller_paths(source.read_text(encoding="utf-8")))
        except UnicodeDecodeError:
            continue

    missing = sorted(path for path in discovered if path.startswith("/api/") and path not in documented)
    print(f"Controller drift scan: discovered={len(discovered)} documented={len(documented)} missing={len(missing)}")
    if missing:
        print("::warning title=OpenAPI controller drift::Controller paths not found in the committed OpenAPI contract:")
        for path in missing:
            print(f"  {path}")
        print("These are advisory baseline findings. New API-affecting PRs are still hard-gated to update documentation.")


if __name__ == "__main__":
    main()
