#!/usr/bin/env python3
"""Strict, dependency-light validation for the committed CPay OpenAPI contract."""
from __future__ import annotations

import argparse
from pathlib import Path
from typing import Any

import yaml
from openapi_spec_validator import validate_spec


def load(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as handle:
        value = yaml.safe_load(handle)
    if not isinstance(value, dict):
        raise SystemExit(f"{path}: root must be an object")
    return value


def resolve_local_ref(root: dict[str, Any], ref: str) -> Any:
    if not ref.startswith("#/"):
        return None
    current: Any = root
    for token in ref[2:].split("/"):
        token = token.replace("~1", "/").replace("~0", "~")
        if not isinstance(current, dict) or token not in current:
            raise KeyError(ref)
        current = current[token]
    return current


def walk(value: Any):
    if isinstance(value, dict):
        yield value
        for child in value.values():
            yield from walk(child)
    elif isinstance(value, list):
        for child in value:
            yield from walk(child)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("spec", type=Path)
    args = parser.parse_args()

    spec = load(args.spec)
    validate_spec(spec)

    if not str(spec.get("openapi", "")).startswith("3."):
        raise SystemExit("CPay API contract must use OpenAPI 3.x")
    info = spec.get("info") or {}
    for field in ("title", "version", "description"):
        if not info.get(field):
            raise SystemExit(f"info.{field} is required")

    paths = spec.get("paths")
    if not isinstance(paths, dict) or not paths:
        raise SystemExit("paths must contain at least one operation")

    operation_ids: dict[str, str] = {}
    methods = {"get", "post", "put", "patch", "delete", "options", "head", "trace"}
    operation_count = 0
    for route, path_item in paths.items():
        if not isinstance(path_item, dict):
            continue
        for method, operation in path_item.items():
            if method.lower() not in methods or not isinstance(operation, dict):
                continue
            operation_count += 1
            operation_id = operation.get("operationId")
            if not operation_id:
                raise SystemExit(f"{method.upper()} {route}: operationId is required")
            if operation_id in operation_ids:
                raise SystemExit(
                    f"duplicate operationId {operation_id!r}: {operation_ids[operation_id]} and {method.upper()} {route}"
                )
            operation_ids[operation_id] = f"{method.upper()} {route}"
            if not operation.get("summary"):
                raise SystemExit(f"{method.upper()} {route}: summary is required")
            if not operation.get("responses"):
                raise SystemExit(f"{method.upper()} {route}: responses are required")

    for node in walk(spec):
        ref = node.get("$ref")
        if isinstance(ref, str) and ref.startswith("#/"):
            try:
                resolve_local_ref(spec, ref)
            except KeyError:
                raise SystemExit(f"unresolved local $ref: {ref}") from None

    print(
        f"OpenAPI validation passed: version={info.get('version')} paths={len(paths)} operations={operation_count}"
    )


if __name__ == "__main__":
    main()
