#!/usr/bin/env bash
# Host-side M1 conf security: baseline + fail-closed modes / URL / lab_http.
# Patrick CHANGES: assert distinct debug.err reasons (not just UNAVAIL on a
# closed port) + positive stub control so the http:// guard is the only delta.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
. "${SCRIPT_DIR}/common.sh"

require_cmd docker
require_cmd ssh
require_integration_keys

CONTAINER="${EZKEY_PAM_CONTAINER:-ezkey-pam-ssh}"
CONF="/etc/security/pam_ezkey.conf"
PRISTINE="/etc/security/pam_ezkey.conf.m1pristine"
PORT="${SSH_PORT}"
STUB_PORT="${EZKEY_STUB_PORT:-18080}"
STUB_PID_FILE="/tmp/ezkey-pam-stub.pid"
ARTIFACT_DIR="${EZKEY_PAM_ARTIFACT_DIR:-/opt/cursor/artifacts}"
mkdir -p "${ARTIFACT_DIR}"

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

stop_stub() {
  docker exec "${CONTAINER}" sh -c \
    "if [ -f '${STUB_PID_FILE}' ]; then kill \"\$(cat '${STUB_PID_FILE}')\" 2>/dev/null || true; rm -f '${STUB_PID_FILE}'; fi" \
    || true
}

start_stub() {
  local mode="${1:-ok}"
  stop_stub
  docker exec -d "${CONTAINER}" sh -c \
    "EZKEY_STUB_MODE='${mode}' EZKEY_STUB_PORT='${STUB_PORT}' \
     python3 /opt/pam-ezkey/scripts/integration-api-stub.py \
     >/tmp/ezkey-pam-stub.log 2>&1 & echo \$! >'${STUB_PID_FILE}'"
  local i
  for i in $(seq 1 40); do
    if docker exec "${CONTAINER}" sh -c \
      "ss -ltn | grep -q ':${STUB_PORT}'" 2>/dev/null; then
      return 0
    fi
    sleep 0.1
  done
  fail "stub did not listen on :${STUB_PORT}"
  docker exec "${CONTAINER}" cat /tmp/ezkey-pam-stub.log 2>/dev/null || true
  return 1
}

cleanup() {
  stop_stub
  restore_conf
  docker exec "${CONTAINER}" rm -f "${PRISTINE}" || true
}

attempt_count() {
  docker exec "${CONTAINER}" sh -c \
    "grep -c 'pam_ezkey: attempt ' /var/log/secure 2>/dev/null || true" | tr -d ' \r'
}

last_attempt() {
  docker exec "${CONTAINER}" sh -c \
    "grep 'pam_ezkey: attempt ' /var/log/secure 2>/dev/null | tail -n 1" || true
}

clear_debug_err() {
  docker exec "${CONTAINER}" sh -c \
    ': > /var/log/ezkey-pam/debug.err; : > /var/log/ezkey-pam/debug.out' || true
}

last_debug_err() {
  docker exec "${CONTAINER}" sh -c \
    'tail -n 8 /var/log/ezkey-pam/debug.err 2>/dev/null' || true
}

wait_new_attempt() {
  local before="$1"
  local i after
  for i in $(seq 1 80); do
    after="$(attempt_count)"
    before="${before:-0}"
    after="${after:-0}"
    if [ "$((after - before))" -ge 1 ]; then
      return 0
    fi
    sleep 0.1
  done
  return 1
}

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

ssh_success_once() {
  local log="${ARTIFACT_DIR:-/opt/cursor/artifacts}/pam-conf-security-success.log"
  mkdir -p "$(dirname "${log}")"
  # #941: waiting prompt requires Enter before /wait.
  ezkey_ssh_kbdint_autoenter "${log}" 'echo PAM_STUB_OK; exit 0' 15 \
    || true
  tr -cd '\11\12\15\40-\176' <"${log}" 2>/dev/null | grep -q PAM_STUB_OK
}

# Expect deny + UNAVAIL + debug.err matching reason_re (sanitized filelog).
expect_unavail_reason() {
  local label="$1"
  local reason_re="$2"
  local before ssh_rc after attempt new err
  settle_sshd
  clear_debug_err
  before="$(attempt_count)"
  before="${before:-0}"
  ssh_rc=0
  ssh_once || ssh_rc=$?
  wait_new_attempt "${before}" || true
  after="$(attempt_count)"
  after="${after:-0}"
  attempt="$(last_attempt)"
  err="$(last_debug_err)"
  new=$((after - before))
  restore_conf
  if [ "${ssh_rc}" -ne 0 ] && [ "${new}" -ge 1 ] && echo "${attempt}" | grep -q 'result=UNAVAIL'; then
    ok "${label} → deny+UNAVAIL (new=${new})"
    echo "  ${attempt}"
  else
    fail "${label} expected deny+UNAVAIL (ssh_rc=${ssh_rc} new=${new} line='${attempt}')"
  fi
  if [ "${new}" -ge 1 ] && ! echo "${attempt}" | grep -Eq 'lab_http=(true|false)'; then
    fail "${label}: attempt line missing lab_http=: ${attempt}"
  fi
  if echo "${err}" | grep -Eq "${reason_re}"; then
    ok "${label} → debug.err reason matches /${reason_re}/"
    echo "  err: $(echo "${err}" | tail -n 1)"
  else
    fail "${label}: debug.err missing /${reason_re}/ — got: ${err}"
  fi
}

trap cleanup EXIT

echo "=== M1 conf security (container ${CONTAINER}) ==="

docker exec "${CONTAINER}" /opt/pam-ezkey/test/test_conf_security.sh
ok "baseline conf security self-check"

docker exec "${CONTAINER}" cp -a "${CONF}" "${PRISTINE}"

# Ensure debug filelog is on for reason assertions.
docker exec "${CONTAINER}" sh -c \
  "grep -q '^debug=' '${PRISTINE}' || echo 'debug=true' >> '${PRISTINE}'; \
   sed -i 's/^debug=.*/debug=true/' '${PRISTINE}'; \
   cp -a '${PRISTINE}' '${CONF}'; chown root:root '${CONF}'; chmod 600 '${CONF}'"

# --- mode / owner (reason: conf rejected) ---
docker exec "${CONTAINER}" chmod 644 "${CONF}"
expect_unavail_reason "chmod 0644" 'Config_file_.*rejected'

restore_conf
docker exec "${CONTAINER}" sh -c "chown root:root '${CONF}'; chmod 000 '${CONF}'"
expect_unavail_reason "chmod 0000" 'Config_file_.*rejected'

restore_conf
docker exec "${CONTAINER}" sh -c \
  "chown ${SSH_USER}:${SSH_USER} '${CONF}'; chmod 600 '${CONF}'"
expect_unavail_reason "non-root owner (${SSH_USER})" 'Config_file_.*rejected'

# --- URL absent → specific missing-keys reason (never hits network) ---
restore_conf
docker exec "${CONTAINER}" sh -c \
  "sed -i 's|^integration_api_url=.*|integration_api_url=|' '${CONF}'; chown root:root '${CONF}'; chmod 600 '${CONF}'"
expect_unavail_reason "URL absent (empty value)" \
  'Missing_integration_api_url|integration_api_url,.*missing'

restore_conf
docker exec "${CONTAINER}" sh -c \
  "sed -i '/^integration_api_url=/d' '${CONF}'; chown root:root '${CONF}'; chmod 600 '${CONF}'"
expect_unavail_reason "URL absent (key line deleted)" \
  'Missing_integration_api_url|integration_api_url,.*missing'

# --- http:// without lab_http → scheme gate reason (not closed-port curl fail) ---
restore_conf
docker exec "${CONTAINER}" sh -c \
  "grep -q '^lab_http=' '${CONF}' || echo 'lab_http=true' >> '${CONF}'; \
   sed -i 's/^lab_http=.*/lab_http=false/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:9|' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'"
expect_unavail_reason "http:// with lab_http=false" 'must_be_https'

# Case-insensitive HTTP:// also rejected when lab_http=false
restore_conf
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=false/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=HTTP://127.0.0.1:9|' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'"
expect_unavail_reason "HTTP:// (uppercase) with lab_http=false" 'must_be_https'

# Case-insensitive HTTPS:// is allowed through the scheme gate (then connect fails).
restore_conf
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=false/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=HTTPS://127.0.0.1:9|' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'"
expect_unavail_reason "HTTPS:// (uppercase) passes scheme gate" \
  'Failed_to_POST|Failed_to_GET|curl|SSL|connect|Connection'

# --- missing secret_key ---
restore_conf
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^secret_key=.*/secret_key=/' '${CONF}'; chown root:root '${CONF}'; chmod 600 '${CONF}'"
expect_unavail_reason "missing secret_key" \
  'Missing_integration_api_url|secret_key|missing'

# --- Positive control: stub ACCEPTED with lab_http=true ---
start_stub ok
restore_conf
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=false/' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=15/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'"
settle_sshd
clear_debug_err
before="$(attempt_count)"; before="${before:-0}"
if ssh_success_once; then
  ok "stub ACCEPTED + lab_http=true → SSH SUCCESS (positive control)"
else
  fail "stub positive control: SSH did not succeed"
  docker exec "${CONTAINER}" cat /tmp/ezkey-pam-stub.log 2>/dev/null || true
fi
if ezkey_ssh_capture_has_prewait_prompt \
  "${ARTIFACT_DIR}/pam-conf-security-success.log"; then
  ok "#941 waiting prompt visible on stub SUCCESS path"
else
  fail "#941 waiting prompt missing on stub SUCCESS path"
fi
wait_new_attempt "${before}" || true
attempt="$(last_attempt)"
if echo "${attempt}" | grep -q 'result=SUCCESS' && echo "${attempt}" | grep -q 'lab_http=true'; then
  ok "stub positive control attempt line SUCCESS lab_http=true"
  echo "  ${attempt}"
else
  fail "stub positive control attempt line: ${attempt}"
fi

# #941 / #956 E1: no-challenge + failing PAM conversation → UNAVAIL (display_ok guard).
# Neutering the display_ok check in pam_ezkey.c would let stub ok → SUCCESS here.
docker exec "${CONTAINER}" test -x /opt/pam-ezkey/test/test_no_conv_client \
  || { fail "test_no_conv_client missing"; }
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=false/' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=15/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'"
before="$(attempt_count)"; before="${before:-0}"
set +e
noconv_out="$(docker exec "${CONTAINER}" /opt/pam-ezkey/test/test_no_conv_client testuser 2>&1)"
noconv_rc=$?
set -e
echo "no_conv_nochal rc=${noconv_rc} out=${noconv_out}"
if [ "${noconv_rc}" -eq 0 ] && echo "${noconv_out}" | grep -q 'pam_authenticate_rc=9'; then
  ok "#941 no-challenge failing conv → UNAVAIL (display_ok guard)"
else
  fail "#941 no-challenge failing conv expected UNAVAIL rc=0 pam_rc=9, got rc=${noconv_rc} ${noconv_out}"
fi
wait_new_attempt "${before}" || true
attempt="$(last_attempt)"
if echo "${attempt}" | grep -q 'result=UNAVAIL'; then
  ok "#941 no-challenge no-conv attempt line UNAVAIL"
else
  fail "#941 no-challenge no-conv attempt line: ${attempt}"
fi

# Same stub URL, lab_http=false → scheme gate only (proves guard is the delta).
restore_conf
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=false/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'"
expect_unavail_reason "stub URL + lab_http=false (guard-only delta)" 'must_be_https'
stop_stub

# Hygiene: secrets must not appear anywhere in secure or debug filelogs.
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
