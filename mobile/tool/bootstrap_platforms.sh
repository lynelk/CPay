#!/usr/bin/env bash
set -euo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REQUIRED_FLUTTER_VERSION="${CITO_FLUTTER_VERSION:-3.47.2}"

if ! command -v flutter >/dev/null 2>&1; then
  echo "Flutter ${REQUIRED_FLUTTER_VERSION} is required but was not found." >&2
  exit 1
fi

INSTALLED_VERSION="$(flutter --version --machine | python3 -c 'import json,sys; print(json.load(sys.stdin)["frameworkVersion"])')"
if [[ "${INSTALLED_VERSION}" != "${REQUIRED_FLUTTER_VERSION}" ]]; then
  echo "Expected Flutter ${REQUIRED_FLUTTER_VERSION}; found ${INSTALLED_VERSION}." >&2
  exit 1
fi

TEMP_ROOT="$(mktemp -d)"
trap 'rm -rf "${TEMP_ROOT}"' EXIT

flutter create \
  --platforms=android,ios \
  --org net.citotech.cito \
  --project-name cito_business \
  --empty \
  "${TEMP_ROOT}/cito_business"

rm -rf "${APP_DIR}/android" "${APP_DIR}/ios"
cp -R "${TEMP_ROOT}/cito_business/android" "${APP_DIR}/android"
cp -R "${TEMP_ROOT}/cito_business/ios" "${APP_DIR}/ios"
cp "${TEMP_ROOT}/cito_business/.metadata" "${APP_DIR}/.metadata"

python3 "${APP_DIR}/tool/patch_platforms.py"

cd "${APP_DIR}"
flutter pub get
dart run flutter_launcher_icons
dart run flutter_native_splash:create

echo "Cito Business Android and iOS runners generated successfully."
