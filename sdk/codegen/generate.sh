#!/bin/bash
# Generates full API clients from the CPay OpenAPI spec using openapi-generator-cli.
#
# This is a scaffold for a human to run when a generated client is actually needed — it is not part
# of any build, is not run in CI, and its output is never vendored into this repo (see
# Sdk/codegen/README.md and Sdk/codegen/.gitignore). Running it requires:
#   - Node.js (to run openapi-generator-cli via npx) — see repo root Readme.md for the required version
#   - A JRE (openapi-generator-cli is a Java tool under the hood, even when invoked through npx)
#   - Network access to fetch the generator package the first time it runs
#
# See Sdk/codegen/README.md for the full explanation, including the manual (non-generated) SDKs
# already shipped in Sdk/Node, Sdk/Python, and Sdk/Php, and why you might want a generated client on
# top of those.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

SPEC="${CPAY_OPENAPI_SPEC:-$REPO_ROOT/Docs/Api/cpay-v2-openapi.yaml}"
OUT_DIR="${CPAY_CODEGEN_OUT:-$SCRIPT_DIR/generated}"
GENERATOR_VERSION="${OPENAPI_GENERATOR_VERSION:-7.9.0}"

if [ ! -f "$SPEC" ]; then
  echo "OpenAPI spec not found at $SPEC" >&2
  echo "Set CPAY_OPENAPI_SPEC to point at a valid spec file, or check Docs/Api/cpay-v2-openapi.yaml exists." >&2
  exit 1
fi

if ! command -v npx >/dev/null 2>&1; then
  echo "npx not found. Install Node.js (see repo root Readme.md for the required version) before running this script." >&2
  exit 1
fi

run_generator() {
  local generator_id="$1"
  local target_dir="$2"
  local extra_args="${3:-}"

  echo "Generating '$generator_id' client into $target_dir ..."
  mkdir -p "$target_dir"
  # shellcheck disable=SC2086
  npx --yes "@openapitools/openapi-generator-cli@$GENERATOR_VERSION" generate \
    -i "$SPEC" \
    -g "$generator_id" \
    -o "$target_dir" \
    --additional-properties=npmName=cpay-api-client,packageName=cpay_api_client \
    $extra_args
}

# Node.js / TypeScript client (fetch-based; swap -g for typescript-axios or typescript-node if preferred).
run_generator "typescript-fetch" "$OUT_DIR/typescript"

# Python client.
run_generator "python" "$OUT_DIR/python"

cat <<EOF

Done. Generated clients are under:
  $OUT_DIR/typescript
  $OUT_DIR/python

These are throwaway/local build output (see Sdk/codegen/.gitignore) — do not commit them. A
generated client still needs the request-signing headers from Docs/Api-v2-signing.md layered on top;
see Sdk/codegen/README.md for how the existing hand-written helpers in Sdk/Node, Sdk/Python, and
Sdk/Php do that today.
EOF
