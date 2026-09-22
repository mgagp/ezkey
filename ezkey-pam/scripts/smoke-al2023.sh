#!/usr/bin/env bash
# Minimal AL2023 smoke: build the ABI-matched .so, install into a throwaway
# container, verify it loads (dlopen / ldd). Does not require the Ezkey stack.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
. "${SCRIPT_DIR}/common.sh"

require_cmd docker

cd "${PAM_ROOT}"
echo "== Building AL2023 builder =="
docker build -f Dockerfile.al2023 --target builder-al2023 -t ezkey-pam-builder-al2023:local .

echo "== Building AL2023 smoke runtime =="
docker build -f Dockerfile.al2023 --target smoke-al2023 -t ezkey-pam-smoke-al2023:local .

echo "== Running smoke checks =="
docker run --rm ezkey-pam-smoke-al2023:local

echo "AL2023 smoke OK"
