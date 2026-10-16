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
    "if [ -f '${BACKUP}' ]; then mv -f '${BACKUP}' '${CONF}'; chown root:root '${CONF}'; chmod 600 '${CONF}'; fi" \
    || true
}

attempt_count() {
  # GNU grep -c prints 0 and exits 1 when there are no matches — do not append another 0.
  docker exec "${CONTAINER}" sh -c \
    "grep -c 'pam_ezkey: attempt ' /var/log/secure 2>/dev/null || true" | tr -d ' \r'
}

last_attempt() {
  docker exec "${CONTAINER}" sh -c \
    "grep 'pam_ezkey: attempt ' /var/log/secure 2>/dev/null | tail -n 1" || true
}

ssh_once() {
  ssh -tt \
    -p "${PORT}" \
    -o StrictHostKeyChecking=no \
    -o UserKnownHostsFile=/dev/null \
    -o PreferredAuthentications=keyboard-interactive \
    -o PubkeyAuthentication=no \
    -o PasswordAuthentication=no \
    -o ConnectTimeout=8 \
    "${SSH_USER}@127.0.0.1" \
    'echo SHOULD_NOT_AUTH' \
    >/dev/null 2>&1 || return $?
  return 0
}

trap restore_conf EXIT

echo "=== M1 conf security (container ${CONTAINER}) ==="

docker exec "${CONTAINER}" /opt/pam-ezkey/test/test_conf_security.sh
ok "baseline conf security self-check"

docker exec "${CONTAINER}" cp -a "${CONF}" "${BACKUP}"

# --- mode 0644 must fail closed (PAM_AUTHINFO_UNAVAIL → SSH deny) ---
BEFORE="$(attempt_count)"
docker exec "${CONTAINER}" chmod 644 "${CONF}"
SSH_RC=0
ssh_once || SSH_RC=$?
restore_conf
sleep 0.5
AFTER="$(attempt_count)"
ATTEMPT="$(last_attempt)"
NEW=$((AFTER - BEFORE))

if [ "${SSH_RC}" -ne 0 ] && [ "${NEW}" -ge 1 ] && echo "${ATTEMPT}" | grep -q 'result=UNAVAIL'; then
  ok "chmod 0644 → SSH denied + UNAVAIL attempt line (new=${NEW})"
  echo "  ${ATTEMPT}"
else
  fail "chmod 0644 expected deny+UNAVAIL (ssh_rc=${SSH_RC} new=${NEW} line='${ATTEMPT}')"
fi

# --- missing secret_key must fail closed ---
BEFORE="$(attempt_count)"
docker exec "${CONTAINER}" sh -c \
  "cp -a '${CONF}' '${BACKUP}'; sed -i 's/^secret_key=.*/secret_key=/' '${CONF}'; chown root:root '${CONF}'; chmod 600 '${CONF}'"
SSH_RC=0
ssh_once || SSH_RC=$?
restore_conf
sleep 0.5
AFTER="$(attempt_count)"
ATTEMPT="$(last_attempt)"
NEW=$((AFTER - BEFORE))

if [ "${SSH_RC}" -ne 0 ] && [ "${NEW}" -ge 1 ] && echo "${ATTEMPT}" | grep -q 'result=UNAVAIL'; then
  ok "missing secret_key → SSH denied + UNAVAIL attempt line (new=${NEW})"
  echo "  ${ATTEMPT}"
else
  fail "missing secret_key expected deny+UNAVAIL (ssh_rc=${SSH_RC} new=${NEW} line='${ATTEMPT}')"
fi

# Hygiene: attempt lines must not contain secrets from .env
SECURE_SNIP="$(docker exec "${CONTAINER}" sh -c 'grep "pam_ezkey: attempt " /var/log/secure | tail -n 50' || true)"
LEAK=0
for needle in "${EZKEY_SECRET_KEY:-}" "${EZKEY_INTEGRATION_KEY:-}" authAttemptProofToken; do
  if [ -n "${needle}" ] && echo "${SECURE_SNIP}" | grep -Fq "${needle}"; then
    fail "secret/proof leaked into attempt lines: ${needle:0:12}..."
    LEAK=1
  fi
done
if [ "${LEAK}" -eq 0 ]; then
  ok "no ikey/skey/proof in attempt lines"
fi

echo ""
echo "=== Results: $PASS passed, $FAIL failed ==="
[ "${FAIL}" -eq 0 ]
