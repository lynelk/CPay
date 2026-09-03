#!/usr/bin/env bash
set -euo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
: "${IOS_TEAM_ID:?IOS_TEAM_ID is required}"
: "${IOS_PROVISIONING_PROFILE_NAME:?IOS_PROVISIONING_PROFILE_NAME is required}"

python3 - "${APP_DIR}/ios/Runner.xcodeproj/project.pbxproj" "${IOS_TEAM_ID}" "${IOS_PROVISIONING_PROFILE_NAME}" <<'PY'
from pathlib import Path
import re
import sys

path = Path(sys.argv[1])
team = sys.argv[2]
profile = sys.argv[3]
text = path.read_text(encoding='utf-8')

# Remove generated/previous signing values so repeated runs remain deterministic.
text = re.sub(r'^\s*DEVELOPMENT_TEAM = [^;]*;\n', '', text, flags=re.MULTILINE)
text = re.sub(r'^\s*PROVISIONING_PROFILE_SPECIFIER = [^;]*;\n', '', text, flags=re.MULTILINE)
text = re.sub(r'^\s*CODE_SIGN_IDENTITY = [^;]*;\n', '', text, flags=re.MULTILINE)
text = text.replace('CODE_SIGN_STYLE = Automatic;', 'CODE_SIGN_STYLE = Manual;')
text = text.replace(
    'CODE_SIGN_STYLE = Manual;',
    'CODE_SIGN_STYLE = Manual;\n'
    f'\t\t\t\tDEVELOPMENT_TEAM = {team};\n'
    '\t\t\t\tCODE_SIGN_IDENTITY = "Apple Distribution";\n'
    f'\t\t\t\tPROVISIONING_PROFILE_SPECIFIER = "{profile}";',
)
path.write_text(text, encoding='utf-8')
PY
