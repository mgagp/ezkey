#!/usr/bin/env bash
# Build (in Docker) and start the Ezkey PAM SSH demo VM.
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

cd "${PAM_ROOT}"
echo "== Building PAM module (Docker builder stage) and SSH demo image =="
docker compose --profile build build pam-builder
docker compose build ezkey-pam-ssh

echo "== Starting SSH demo VM =="
docker compose up -d ezkey-pam-ssh

echo "== Waiting for sshd =="
for i in $(seq 1 30); do
  if docker exec ezkey-pam-ssh sh -c 'ss -ltn | grep -q ":22"' 2>/dev/null; then
    break
  fi
  sleep 1
done

echo "== Running container self-checks =="
docker exec ezkey-pam-ssh /opt/pam-ezkey/test/test_pam.sh

echo ""
echo "SSH demo is up on localhost:${SSH_PORT}"
echo "  ssh -p ${SSH_PORT} -o PreferredAuthentications=keyboard-interactive -o PubkeyAuthentication=no ${SSH_USER}@127.0.0.1"
echo "Approve the request on Demo Device: ${DEMO_DEVICE_URL}/phone/ezkey"
echo "Or run: ./scripts/demo-ssh.sh"
