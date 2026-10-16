#!/usr/bin/env bash
# M1 observability: exactly one authpriv attempt line per scenario in /var/log/secure.
# Scenarios: success, reject, timeout, network error. Never secrets in the line.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
. "${SCRIPT_DIR}/common.sh"

require_cmd docker
require_cmd ssh
require_cmd curl

CONTAINER="${EZKEY_PAM_CONTAINER:-ezkey-pam-ssh}"
CONF="/etc/security/pam_ezkey.conf"
BACKUP="/etc/security/pam_ezkey.conf.obsbak"
PORT="${SSH_PORT}"
PASS=0
FAIL=0

ok()   { echo "[PASS] $1"; PASS=$((PASS+1)); }
fail() { echo "[FAIL] $1"; FAIL=$((FAIL+1)); }

if ! docker ps --format '{{.Names}}' | grep -qx "${CONTAINER}"; then
  echo "${CONTAINER} is not running. Start with ./scripts/up.sh" >&2
  exit 1
fi

restore_conf() {
  docker exec "${CONTAINER}" sh -c \
    "if [ -f '${BACKUP}' ]; then mv -f '${BACKUP}' '${CONF}'; chown root:root '${CONF}'; chmod 600 '${CONF}'; fi" \
    || true
}
trap restore_conf EXIT

mark_secure() {
  local marker="$1"
  docker exec "${CONTAINER}" logger -p authpriv.info "${marker}"
  sleep 0.4
}

attempt_lines_since() {
  local marker="$1"
  docker exec "${CONTAINER}" sh -c \
    "awk 'found || index(\$0, \"${marker}\"){found=1} found && /pam_ezkey: attempt /{print}' /var/log/secure"
}

count_attempts_since() {
  attempt_lines_since "$1" | wc -l | tr -d ' '
}

assert_one_clean_line() {
  local marker="$1"
  local expect_result="$2"
  local label="$3"
  local lines count line

  sleep 0.5
  lines="$(attempt_lines_since "${marker}")"
  count="$(echo "${lines}" | grep -c 'pam_ezkey: attempt ' || true)"
  line="$(echo "${lines}" | grep 'pam_ezkey: attempt ' | tail -n 1 || true)"

  if [ "${count}" != "1" ]; then
    fail "${label}: expected exactly 1 attempt line, got ${count}"
    echo "${lines}" | sed 's/^/  /' || true
    return 0
  fi
  if ! echo "${line}" | grep -q "result=${expect_result}"; then
    fail "${label}: expected result=${expect_result} in: ${line}"
    return 0
  fi
  if ! echo "${line}" | grep -Eq 'user=[^ ]+ authAttemptId=[^ ]+ result=[^ ]+ pam_code=[0-9]+ latency_ms=[0-9]+'; then
    fail "${label}: line missing required fields: ${line}"
    return 0
  fi
  # Hygiene
  if echo "${line}" | grep -Eiq 'secret|proof|token|skey|ikey_|Authorization|body=\{|"status"'; then
    fail "${label}: sensitive pattern in attempt line: ${line}"
    return 0
  fi
  if [ -n "${EZKEY_SECRET_KEY:-}" ] && echo "${line}" | grep -Fq "${EZKEY_SECRET_KEY}"; then
    fail "${label}: secret_key leaked"
    return 0
  fi
  if [ -n "${EZKEY_INTEGRATION_KEY:-}" ] && echo "${line}" | grep -Fq "${EZKEY_INTEGRATION_KEY}"; then
    fail "${label}: integration_key leaked"
    return 0
  fi
  ok "${label}: one clean line (${expect_result})"
  echo "  ${line}"
}

ssh_bg_cmd() {
  local out="$1"
  local cmd="$2"
  set +e
  ssh -tt \
    -p "${PORT}" \
    -o StrictHostKeyChecking=no \
    -o UserKnownHostsFile=/dev/null \
    -o PreferredAuthentications=keyboard-interactive \
    -o PubkeyAuthentication=no \
    -o PasswordAuthentication=no \
    -o ConnectTimeout=10 \
    "${SSH_USER}@127.0.0.1" \
    "${cmd}" \
    >"${out}" 2>&1
  echo $? >"${out}.rc"
  set -e
}

echo "=== M1 observability (/var/log/secure on ${CONTAINER}) ==="

# Prove rsyslog probe path works
PROBE="m1-rsyslog-probe-$(date +%s)"
docker exec "${CONTAINER}" logger -p authpriv.info "${PROBE}"
sleep 0.5
if docker exec "${CONTAINER}" grep -q "${PROBE}" /var/log/secure; then
  ok "rsyslog authpriv → /var/log/secure"
else
  fail "rsyslog authpriv probe missing from /var/log/secure"
fi

# --- success ---
MARKER="m1-obs-success-$(date +%s)"
mark_secure "${MARKER}"
LOG="${RUNTIME_DIR}/obs-success.log"
"${SCRIPT_DIR}/respond-pending.sh" approve >"${RUNTIME_DIR}/obs-approve.log" 2>&1 &
APID=$!
ssh_bg_cmd "${LOG}" 'echo PAM_SSH_OK; exit 0'
wait "${APID}" 2>/dev/null || true
if grep -q PAM_SSH_OK "${LOG}"; then
  ok "success path SSH PAM_SSH_OK"
else
  fail "success path did not print PAM_SSH_OK"
  tail -n 40 "${LOG}" || true
fi
assert_one_clean_line "${MARKER}" "SUCCESS" "success"

# --- reject ---
MARKER="m1-obs-reject-$(date +%s)"
mark_secure "${MARKER}"
LOG="${RUNTIME_DIR}/obs-reject.log"
"${SCRIPT_DIR}/respond-pending.sh" reject >"${RUNTIME_DIR}/obs-reject-resp.log" 2>&1 &
APID=$!
ssh_bg_cmd "${LOG}" 'echo SHOULD_NOT'
wait "${APID}" 2>/dev/null || true
RC="$(cat "${LOG}.rc" 2>/dev/null || echo 1)"
if [ "${RC}" != "0" ] && ! grep -q SHOULD_NOT "${LOG}"; then
  ok "reject path SSH denied"
else
  fail "reject path unexpectedly succeeded (rc=${RC})"
fi
assert_one_clean_line "${MARKER}" "DENIED" "reject"

# --- timeout (short wait, no approve) ---
MARKER="m1-obs-timeout-$(date +%s)"
mark_secure "${MARKER}"
docker exec "${CONTAINER}" cp -a "${CONF}" "${BACKUP}"
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^wait_timeout=.*/wait_timeout=8/' '${CONF}'; chown root:root '${CONF}'; chmod 600 '${CONF}'"
LOG="${RUNTIME_DIR}/obs-timeout.log"
# No respond-pending — wait expires
ssh_bg_cmd "${LOG}" 'echo SHOULD_NOT'
restore_conf
RC="$(cat "${LOG}.rc" 2>/dev/null || echo 1)"
if [ "${RC}" != "0" ]; then
  ok "timeout path SSH denied"
else
  fail "timeout path unexpectedly succeeded"
fi
assert_one_clean_line "${MARKER}" "DENIED" "timeout"

# --- network error (unreachable Integration API) ---
MARKER="m1-obs-net-$(date +%s)"
mark_secure "${MARKER}"
docker exec "${CONTAINER}" cp -a "${CONF}" "${BACKUP}"
docker exec "${CONTAINER}" sh -c \
  "sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:9|' '${CONF}'; chown root:root '${CONF}'; chmod 600 '${CONF}'"
LOG="${RUNTIME_DIR}/obs-net.log"
ssh_bg_cmd "${LOG}" 'echo SHOULD_NOT'
restore_conf
RC="$(cat "${LOG}.rc" 2>/dev/null || echo 1)"
if [ "${RC}" != "0" ]; then
  ok "network-error path SSH denied"
else
  fail "network-error path unexpectedly succeeded"
fi
assert_one_clean_line "${MARKER}" "UNAVAIL" "network-error"

echo ""
echo "=== Results: $PASS passed, $FAIL failed ==="
[ "${FAIL}" -eq 0 ]
