#!/usr/bin/env bash
# Host-side M1 conf security: baseline + fail-closed modes / missing URL / owner.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
. "${SCRIPT_DIR}/common.sh"

require_cmd docker
require_cmd ssh

CONTAINER="${EZKEY_PAM_CONTAINER:-ezkey-pam-ssh}"
CONF="/etc/security/pam_ezkey.conf"
# Stable pristine copy — never moved, only copied from (restore must not consume it).
PRISTINE="/etc/security/pam_ezkey.conf.m1pristine"
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
    "if [ -f '${PRISTINE}' ]; then cp -a '${PRISTINE}' '${CONF}'; chown root:root '${CONF}'; chmod 600 '${CONF}'; fi" \
    || true
}

cleanup() {
  restore_conf
  docker exec "${CONTAINER}" rm -f "${PRISTINE}" || true
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

# Poll until attempt_count advances past BEFORE (deterministic; no bare sleep-only wait).
wait_new_attempt() {
  local before="$1"
  local i after
  for i in $(seq 1 80); do
    after="$(attempt_count)"
    # Treat empty/non-numeric counts as 0 so arithmetic never aborts under set -e.
    before="${before:-0}"
    after="${after:-0}"
    if [ "$((after - before))" -ge 1 ]; then
      return 0
    fi
    sleep 0.1
  done
  return 1
}

# Let sshd finish the previous keyboard-interactive failure before the next try.
settle_sshd() {
  local i
  for i in $(seq 1 20); do
    if docker exec "${CONTAINER}" sh -c 'ss -ltn | grep -q ":22"' 2>/dev/null; then
      sleep 0.2
      return 0
    fi
    sleep 0.1
  done
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

# Expect SSH deny + at least one new UNAVAIL attempt line after mutating conf.
expect_unavail() {
  local label="$1"
  local before ssh_rc after attempt new
  settle_sshd
  before="$(attempt_count)"
  before="${before:-0}"
  ssh_rc=0
  ssh_once || ssh_rc=$?
  wait_new_attempt "${before}" || true
  after="$(attempt_count)"
  after="${after:-0}"
  attempt="$(last_attempt)"
  new=$((after - before))
  restore_conf
  if [ "${ssh_rc}" -ne 0 ] && [ "${new}" -ge 1 ] && echo "${attempt}" | grep -q 'result=UNAVAIL'; then
    ok "${label} → SSH denied + UNAVAIL attempt line (new=${new})"
    echo "  ${attempt}"
  else
    fail "${label} expected deny+UNAVAIL (ssh_rc=${ssh_rc} new=${new} line='${attempt}')"
  fi
}

trap cleanup EXIT

echo "=== M1 conf security (container ${CONTAINER}) ==="

docker exec "${CONTAINER}" /opt/pam-ezkey/test/test_conf_security.sh
ok "baseline conf security self-check"

docker exec "${CONTAINER}" cp -a "${CONF}" "${PRISTINE}"

# --- mode 0644 must fail closed (PAM_AUTHINFO_UNAVAIL → SSH deny) ---
docker exec "${CONTAINER}" chmod 644 "${CONF}"
expect_unavail "chmod 0644"

# --- mode 0000 must fail closed (root bypasses DAC; exact 0600|0400 required) ---
restore_conf
docker exec "${CONTAINER}" sh -c "chown root:root '${CONF}'; chmod 000 '${CONF}'"
expect_unavail "chmod 0000"

# --- non-root owner must fail closed ---
restore_conf
docker exec "${CONTAINER}" sh -c \
  "chown ${SSH_USER}:${SSH_USER} '${CONF}'; chmod 600 '${CONF}'"
expect_unavail "non-root owner (${SSH_USER})"

# --- URL absent (empty integration_api_url) → UNAVAIL (Patrick B1) ---
restore_conf
docker exec "${CONTAINER}" sh -c \
  "sed -i 's|^integration_api_url=.*|integration_api_url=|' '${CONF}'; chown root:root '${CONF}'; chmod 600 '${CONF}'"
expect_unavail "URL absent"

# --- missing secret_key must fail closed ---
restore_conf
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^secret_key=.*/secret_key=/' '${CONF}'; chown root:root '${CONF}'; chmod 600 '${CONF}'"
expect_unavail "missing secret_key"

# Hygiene: secrets must not appear anywhere in secure or debug filelogs (not only attempt lines).
assert_no_secret_leak() {
  local blob="$1"
  local label="$2"
  local leak=0
  local needle
  for needle in "${EZKEY_SECRET_KEY:-}" "${EZKEY_INTEGRATION_KEY:-}" authAttemptProofToken; do
    if [ -n "${needle}" ] && echo "${blob}" | grep -Fq "${needle}"; then
      fail "secret/proof leaked into ${label}: ${needle:0:12}..."
      leak=1
    fi
  done
  if [ "${leak}" -eq 0 ]; then
    ok "no ikey/skey/proof in ${label}"
  fi
}

SECURE_ALL="$(docker exec "${CONTAINER}" sh -c 'cat /var/log/secure 2>/dev/null' || true)"
assert_no_secret_leak "${SECURE_ALL}" "/var/log/secure"

DEBUG_ALL="$(docker exec "${CONTAINER}" sh -c \
  'cat /var/log/ezkey-pam/debug.out /var/log/ezkey-pam/debug.err 2>/dev/null' || true)"
assert_no_secret_leak "${DEBUG_ALL}" "/var/log/ezkey-pam/debug.*"

echo ""
echo "=== Results: $PASS passed, $FAIL failed ==="
[ "${FAIL}" -eq 0 ]
