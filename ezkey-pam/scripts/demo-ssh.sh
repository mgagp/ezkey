#!/usr/bin/env bash
# End-to-end: SSH into the PAM demo VM and approve via Demo Device.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
. "${SCRIPT_DIR}/common.sh"

require_cmd ssh
require_cmd docker
require_cmd python3

if ! docker ps --format '{{.Names}}' | grep -qx 'ezkey-pam-ssh'; then
  echo "ezkey-pam-ssh is not running. Start it with ./scripts/up.sh" >&2
  exit 1
fi

LOG="${RUNTIME_DIR}/ssh-demo.log"
: > "${LOG}"

echo "== Starting SSH (keyboard-interactive / PAM, waits for Ezkey approval) =="
echo "== Pre-wait prompt (#941): auto-Enter when Approve/challenge line appears =="
echo "== Approval uses respond-pending.sh with the digits from that prompt =="
set +e
ezkey_ssh_kbdint_autoenter "${LOG}" 'echo PAM_SSH_OK; id; hostname' 10 &
SSH_PID=$!
set -e

digits=""
for _i in $(seq 1 90); do
  if ! kill -0 "${SSH_PID}" 2>/dev/null; then
    break
  fi
  flat="$(tr -cd '\11\12\15\40-\176' <"${LOG}" 2>/dev/null || true)"
  digits="$(printf '%s\n' "${flat}" | grep -oE 'Ezkey challenge code: [0-9]{2}' | tail -1 | awk '{print $NF}')" || true
  if [ -n "${digits}" ]; then
    break
  fi
  sleep 1
done

if [ -z "${digits}" ]; then
  echo "SSH prompt did not show challenge digits. Approval needs them." >&2
  echo "The demo conf requests a challenge (challenge_requested=true, #995)." >&2
  kill "${SSH_PID}" 2>/dev/null || true
  wait "${SSH_PID}" 2>/dev/null || true
  exit 1
fi

echo "== Approving with challenge digits from the SSH prompt =="
"${SCRIPT_DIR}/respond-pending.sh" approve "" "${digits}"

set +e
wait "${SSH_PID}"
SSH_RC=$?
set -e

echo "SSH exit code: ${SSH_RC}"
if grep -q 'PAM_SSH_OK' "${LOG}"; then
  echo "SSH session succeeded through Ezkey PAM."
  grep -E 'PAM_SSH_OK|^uid=|Approve this SSH|Ezkey challenge' "${LOG}" \
    | tr -cd '\11\12\15\40-\176\n' || true
  exit 0
fi

echo "SSH did not print PAM_SSH_OK. Log:" >&2
tail -n 80 "${LOG}" >&2
exit 1
