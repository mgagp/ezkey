#!/usr/bin/env bash
# CI helper: build Rocky SSH runtime, start with dummy Integration keys (no
# Ezkey stack required), run test-conf-security.sh — proves B1 / 0000 / non-root
# / URL absent / missing secret_key → UNAVAIL in CI (Patrick m1).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
. "${SCRIPT_DIR}/common.sh"

require_cmd docker
require_cmd ssh

PAM_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
CONTAINER="${EZKEY_PAM_CONTAINER:-ezkey-pam-ssh}"
IMAGE="${EZKEY_PAM_CI_IMAGE:-ezkey-pam-ssh:ci}"
PORT="${EZKEY_PAM_SSH_PORT:-2222}"

# CI-only dummy credentials (not real secrets). Scripts require non-empty keys
# for leak greps (Christophe m5).
export EZKEY_INTEGRATION_KEY="${EZKEY_INTEGRATION_KEY:-ezkey_ikey_ci_dummy_not_a_secret}"
export EZKEY_SECRET_KEY="${EZKEY_SECRET_KEY:-ezkey_skey_ci_dummy_not_a_secret}"
export EZKEY_INTEGRATION_API_URL="${EZKEY_INTEGRATION_API_URL:-http://127.0.0.1:9}"
export EZKEY_SSH_USER="${EZKEY_SSH_USER:-testuser}"
export EZKEY_PAM_SSH_PORT="${PORT}"
export SSH_PORT="${PORT}"
export SSH_USER="${EZKEY_SSH_USER}"

cd "${PAM_ROOT}"

echo "== CI M1: docker build runtime (${IMAGE}) =="
docker build --target runtime -t "${IMAGE}" .

if docker ps -a --format '{{.Names}}' | grep -qx "${CONTAINER}"; then
  docker rm -f "${CONTAINER}" >/dev/null
fi

echo "== CI M1: start ${CONTAINER} on host port ${PORT} =="
docker run -d \
  --name "${CONTAINER}" \
  -p "${PORT}:22" \
  -e "EZKEY_INTEGRATION_API_URL=${EZKEY_INTEGRATION_API_URL}" \
  -e "EZKEY_INTEGRATION_KEY=${EZKEY_INTEGRATION_KEY}" \
  -e "EZKEY_SECRET_KEY=${EZKEY_SECRET_KEY}" \
  -e "EZKEY_SSH_USER=${EZKEY_SSH_USER}" \
  -e "EZKEY_DEBUG=true" \
  "${IMAGE}"

cleanup() {
  docker rm -f "${CONTAINER}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "== CI M1: wait for sshd =="
for i in $(seq 1 60); do
  if docker exec "${CONTAINER}" sh -c 'ss -ltn | grep -q ":22"' 2>/dev/null; then
    break
  fi
  sleep 1
done
docker exec "${CONTAINER}" /opt/pam-ezkey/test/test_pam.sh || true

echo "== CI M1: test-conf-security.sh =="
"${SCRIPT_DIR}/test-conf-security.sh"

echo "== CI M1 conf-security: OK =="
