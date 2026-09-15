#!/usr/bin/env bash
# End-to-end: SSH into the PAM demo VM and approve via Demo Device.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
. "${SCRIPT_DIR}/common.sh"

require_cmd ssh
require_cmd docker

if ! docker ps --format '{{.Names}}' | grep -qx 'ezkey-pam-ssh'; then
  echo "ezkey-pam-ssh is not running. Start it with ./scripts/up.sh" >&2
  exit 1
fi

LOG="${RUNTIME_DIR}/ssh-demo.log"
: > "${LOG}"

echo "== Starting SSH (will block in PAM until Demo Device approval) =="
set +e
ssh -vvv \
  -p "${SSH_PORT}" \
  -o StrictHostKeyChecking=no \
  -o UserKnownHostsFile=/dev/null \
  -o PreferredAuthentications=keyboard-interactive \
  -o PubkeyAuthentication=no \
  -o NumberOfPasswordPrompts=0 \
  -o ConnectTimeout=10 \
  "${SSH_USER}@127.0.0.1" \
  'echo PAM_SSH_OK; id; hostname' \
  >"${LOG}" 2>&1 &
SSH_PID=$!
set -e

sleep 2
echo "== Approving on Demo Device =="
"${SCRIPT_DIR}/approve-pending.sh"

echo "== Waiting for SSH to complete =="
WAIT=0
while kill -0 "${SSH_PID}" 2>/dev/null; do
  WAIT=$((WAIT + 1))
  if [ "${WAIT}" -gt 60 ]; then
    echo "SSH still running after 60s; last log:" >&2
    tail -n 40 "${LOG}" >&2
    kill "${SSH_PID}" 2>/dev/null || true
    exit 1
  fi
  sleep 1
done

set +e
wait "${SSH_PID}"
SSH_RC=$?
set -e

echo "SSH exit code: ${SSH_RC}"
if grep -q 'PAM_SSH_OK' "${LOG}"; then
  echo "SSH session succeeded through Ezkey PAM."
  grep -E 'PAM_SSH_OK|^uid=' "${LOG}" || true
  exit 0
fi

echo "SSH did not print PAM_SSH_OK. Log:" >&2
tail -n 80 "${LOG}" >&2
exit 1
