#!/usr/bin/env bash
# Build and start the Amazon Linux 2023 PAM mirror (SSH demo parity with Rocky).
# Uses a separately compiled AL2023 .so — never copies the Rocky binary.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
. "${SCRIPT_DIR}/common.sh"

require_cmd docker

if [ ! -f "${ENV_FILE}" ]; then
  echo "Missing ${ENV_FILE}. Run ./scripts/provision.sh first." >&2
  exit 1
fi

NETWORK="$(detect_ezkey_network)"
if [ -z "${NETWORK}" ]; then
  echo "Ezkey Docker network not found. Start the main stack with ezkey-tests/clean-start.sh" >&2
  exit 1
fi
export EZKEY_DOCKER_NETWORK="${NETWORK}"
echo "Using Docker network: ${NETWORK}"

SSH_PORT_AL="${EZKEY_PAM_SSH_PORT_AL2023:-2223}"
export EZKEY_PAM_SSH_PORT_AL2023="${SSH_PORT_AL}"

cd "${PAM_ROOT}"
echo "== Building AL2023 PAM module + SSH mirror image =="
docker compose --profile al2023 build pam-builder-al2023 ezkey-pam-ssh-al2023

echo "== Starting AL2023 SSH mirror =="
docker compose --profile al2023 up -d ezkey-pam-ssh-al2023

echo "== Waiting for sshd =="
for i in $(seq 1 30); do
  if docker exec ezkey-pam-ssh-al2023 sh -c 'ss -ltn | grep -q ":22"' 2>/dev/null; then
    break
  fi
  sleep 1
done

echo "== Running container self-checks =="
docker exec ezkey-pam-ssh-al2023 /opt/pam-ezkey/test/test_pam.sh

echo ""
echo "AL2023 SSH mirror is up on localhost:${SSH_PORT_AL}"
echo "  ssh -p ${SSH_PORT_AL} -o PreferredAuthentications=keyboard-interactive -o PubkeyAuthentication=no ${SSH_USER}@127.0.0.1"
echo "Approve on Demo Device: ${DEMO_DEVICE_URL}/phone/ezkey"
echo "Smoke-only (module load): ./scripts/smoke-al2023.sh"
