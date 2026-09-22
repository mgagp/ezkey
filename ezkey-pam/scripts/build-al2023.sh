#!/usr/bin/env bash
# Build pam_ezkey.so for Amazon Linux 2023 ABI and optionally extract it.
#
# Usage:
#   ./scripts/build-al2023.sh              # build image (builder-al2023)
#   ./scripts/build-al2023.sh --extract    # also copy .so to ./artifacts/
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
. "${SCRIPT_DIR}/common.sh"

require_cmd docker

EXTRACT=0
if [ "${1:-}" = "--extract" ]; then
  EXTRACT=1
fi

cd "${PAM_ROOT}"
echo "== Building Amazon Linux 2023 PAM builder =="
docker build -f Dockerfile.al2023 --target builder-al2023 -t ezkey-pam-builder-al2023:local .

if [ "${EXTRACT}" -eq 1 ]; then
  mkdir -p "${PAM_ROOT}/artifacts"
  OUT="${PAM_ROOT}/artifacts/pam_ezkey-al2023.so"
  cid="$(docker create ezkey-pam-builder-al2023:local)"
  docker cp "${cid}:/src/build/pam_ezkey.so" "${OUT}"
  docker rm "${cid}" >/dev/null
  echo "Extracted AL2023 module to ${OUT}"
  ls -la "${OUT}"
fi

echo "Done. Smoke / SSH mirror: ./scripts/up-al2023.sh (requires Ezkey network + .env)"
