#!/usr/bin/env bash
# Host-side M1 conf security: baseline + chmod 0644 / missing key fail-closed via SSH.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
. "${SCRIPT_DIR}/common.sh"

require_cmd docker
require_cmd ssh

CONTAINER="${EZKEY_PAM_CONTAINER:-ezkey-pam-ssh}"
CONF="/etc/security/pam_ezkey.conf"
BACKUP="/etc/security/pam_ezkey.conf.m1bak"
PORT="${SSH_PORT}"

if ! docker ps --format '{{.Names}}' | grep -qx "${CONTAINER}"; then
  echo "${CONTAINER} is not running. Start with ./scripts/up.sh" >&2
  exit 1
fi

PASS=0
FAIL=0
ok()   { echo "[PASS] $1"; PASS=$((PASS+1)); }
fail() { echo "[FAIL] $1"; FAIL=$((FAIL+1)); }

restore_conf() {
  docker exec "${CONTAINER}" sh -c \
    "if [ -f '${BACKUP}' ]; then mv -f '${BACKUP}' '${CONF}'; chown root:root '${CONF}'; chmod 600 '${CONF}'; fi"
}

ssh_once() {
  # Do not toggle set -e here — returning non-zero under set -e aborts the caller.
  ssh -tt \
    -p "${PORT}" \
    -o StrictHostKeyChecking=no \
    -o UserKnownHostsFile=/dev/null \
    -o PreferredAuthentications=keyboard-interactive \
    -o PubkeyAuthentication=no \
    -o PasswordAuthentication=no \
    -o ConnectTimeout=8 \
    -o NumberOfPasswordPrompts=0 \
    "${SSH_USER}@127.0.0.1" \
    'echo SHOULD_NOT_AUTH' \
    >/dev/null 2>&1 || return $?
  return 0
}

count_attempt_lines_since() {
  local marker="$1"
  docker exec "${CONTAINER}" sh -c \
    "awk 'found || /${marker}/{found=1} found && /pam_ezkey: attempt /{print}' /var/log/secure 2>/dev/null | wc -l"
}

trap restore_conf EXIT

echo "=== M1 conf security (container ${CONTAINER}) ==="

docker exec "${CONTAINER}" /opt/pam-ezkey/test/test_conf_security.sh
ok "baseline conf security self-check"

docker exec "${CONTAINER}" cp -a "${CONF}" "${BACKUP}"

# --- mode 0644 must fail closed (PAM_AUTHINFO_UNAVAIL → SSH deny) ---
MARKER="m1-conf-mode-$(date +%s)"
docker exec "${CONTAINER}" sh -c "logger -p authpriv.info '${MARKER}'; chmod 644 '${CONF}'"
: > "${RUNTIME_DIR}/conf-mode.log"
set +e
ssh_once
SSH_RC=$?
set -e
restore_conf
sleep 0.5
LINES="$(count_attempt_lines_since "${MARKER}")"
ATTEMPT="$(docker exec "${CONTAINER}" sh -c \
  "awk 'found || /${MARKER}/{found=1} found && /pam_ezkey: attempt /{print}' /var/log/secure" | tail -n 1 || true)"

if [ "${SSH_RC}" -ne 0 ] && echo "${ATTEMPT}" | grep -q 'result=UNAVAIL' && \
   echo "${ATTEMPT}" | grep -q 'pam_code='; then
  ok "chmod 0644 → SSH denied + UNAVAIL attempt line (lines=${LINES})"
else
  fail "chmod 0644 expected deny+UNAVAIL (ssh_rc=${SSH_RC} lines=${LINES} line='${ATTEMPT}')"
fi

# --- missing secret_key must fail closed ---
MARKER="m1-conf-miss-$(date +%s)"
docker exec "${CONTAINER}" sh -c \
  "logger -p authpriv.info '${MARKER}'; cp -a '${CONF}' '${BACKUP}'; sed -i 's/^secret_key=.*/secret_key=/' '${CONF}'; chown root:root '${CONF}'; chmod 600 '${CONF}'"
set +e
ssh_once
SSH_RC=$?
set -e
restore_conf
sleep 0.5
ATTEMPT="$(docker exec "${CONTAINER}" sh -c \
  "awk 'found || /${MARKER}/{found=1} found && /pam_ezkey: attempt /{print}' /var/log/secure" | tail -n 1 || true)"

if [ "${SSH_RC}" -ne 0 ] && echo "${ATTEMPT}" | grep -q 'result=UNAVAIL'; then
  ok "missing secret_key → SSH denied + UNAVAIL attempt line"
else
  fail "missing secret_key expected deny+UNAVAIL (ssh_rc=${SSH_RC} line='${ATTEMPT}')"
fi

# Hygiene: attempt lines must not contain secrets from .env
if [ -f "${ENV_FILE}" ]; then
  # shellcheck disable=SC1090
  set -a; . "${ENV_FILE}"; set +a
fi
SECURE_SNIP="$(docker exec "${CONTAINER}" sh -c 'tail -n 200 /var/log/secure' || true)"
LEAK=0
for needle in "${EZKEY_SECRET_KEY:-}" "${EZKEY_INTEGRATION_KEY:-}" authAttemptProofToken; do
  if [ -n "${needle}" ] && echo "${SECURE_SNIP}" | grep -Fq "${needle}"; then
    fail "secret/proof leaked into /var/log/secure: ${needle:0:12}..."
    LEAK=1
  fi
done
if [ "${LEAK}" -eq 0 ]; then
  ok "no ikey/skey/proof in recent /var/log/secure"
fi

echo ""
echo "=== Results: $PASS passed, $FAIL failed ==="
[ "${FAIL}" -eq 0 ]
