#!/usr/bin/env python3
"""Fail a PR when paths in changed Spring controllers are absent from committed OpenAPI contracts."""
from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

import yaml

ROOT = Path("InitializrSpringbootProjectFresh/src/main/java")
API_ROOT = Path("Docs/Api")
MAPPING = re.compile(r"@(GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping|RequestMapping)\s*(?:\((.*?)\))?", re.DOTALL)
QUOTED = re.compile(r'"([^"\\]*(?:\\.[^"\\]*)*)"')
CLASS_DECL = re.compile(r"\bclass\s+[A-Za-z0-9_]+")


def normalize(path: str) -> str:
    path = path.replace("\\/", "/").strip()
    if not path.startswith("/"):
        path = "/" + path
    while "//" in path:
        path = path.replace("//", "/")
    return path.rstrip("/") or "/"


def first_path(args: str | None) -> str:
    if not args:
        return ""
    match = QUOTED.search(args)
    return normalize(match.group(1)) if match else ""


def controller_paths(text: str) -> set[str]:
    mappings = list(MAPPING.finditer(text))
    class_match = CLASS_DECL.search(text)
    if not mappings or not class_match:
        return set()
    prefix = ""
    for mapping in mappings:
        if mapping.start() >= class_match.start():
            break
        if mapping.group(1) == "RequestMapping":
            prefix = first_path(mapping.group(2))
    found: set[str] = set()
    for mapping in mappings:
        if mapping.start() < class_match.start() or mapping.group(1) == "RequestMapping":
            continue
        suffix = first_path(mapping.group(2))
        found.add(normalize((prefix or "") + "/" + (suffix or "")))
    return {p for p in found if p.startswith("/api/")}


def changed_controllers(base: str, head: str) -> list[Path]:
    output = subprocess.check_output(["git", "diff", "--name-only", f"{base}...{head}"], text=True)
    files = []
    for raw in output.splitlines():
        path = Path(raw.strip())
        if str(path).startswith(str(ROOT)) and path.name.endswith("Controller.java") and path.exists():
            files.append(path)
    return files


def all_contracts(requested: list[str]) -> list[str]:
    """Use explicit contracts plus every committed OpenAPI contract in Docs/Api.

    Pull-request workflow definitions are evaluated from the protected base in some GitHub
    contexts. Auto-discovery prevents a newly added API contract from being invisible to the
    controller coverage gate until the workflow file itself has first reached main.
    """
    discovered = [str(path) for path in sorted(API_ROOT.glob("*-openapi.yaml"))]
    return list(dict.fromkeys([*requested, *discovered]))


def documented_paths(specs: list[str]) -> set[str]:
    paths: set[str] = set()
    for spec_path in all_contracts(specs):
        with Path(spec_path).open("r", encoding="utf-8") as handle:
            spec = yaml.safe_load(handle) or {}
        paths.update(normalize(p) for p in (spec.get("paths") or {}).keys())
    return paths


def main() -> None:
    if len(sys.argv) < 4:
        raise SystemExit("usage: changed_controller_contract.py BASE_SHA HEAD_SHA OPENAPI [OPENAPI ...]")
    base, head, *specs = sys.argv[1:]
    documented = documented_paths(specs)
    missing: list[tuple[str, str]] = []
    for controller in changed_controllers(base, head):
        text = controller.read_text(encoding="utf-8")
        for path in sorted(controller_paths(text)):
            if path not in documented:
                missing.append((str(controller), path))
    if missing:
        print("Changed controller paths missing from OpenAPI contracts:", file=sys.stderr)
        for source, path in missing:
            print(f"  {path}  ({source})", file=sys.stderr)
        raise SystemExit(1)
    print("Changed-controller OpenAPI coverage check passed.")


if __name__ == "__main__":
    main()
