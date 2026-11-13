#!/usr/bin/env bash
# Offline quality gate for ezkey_dart (no Ezkey stack needed): pub get, format check, analyze,
# unit tests. Host requirement: Docker only.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DART="${SCRIPT_DIR}/dart-docker.sh"

"$DART" pub get
"$DART" format --output=none --set-exit-if-changed lib bin test
"$DART" analyze --fatal-infos
"$DART" test --reporter expanded "$@"
