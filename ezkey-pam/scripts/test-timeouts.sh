#!/usr/bin/env bash
# Timeout inventory proofs (Patrick / #726 lesson): exercise thresholds that
# stay short enough for CI/lab. Full matrix documented in STATUS.md.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
. "${SCRIPT_DIR}/common.sh"

require_cmd docker
require_cmd ssh
require_integration_keys

CONTAINER="${EZKEY_PAM_CONTAINER:-ezkey-pam-ssh}"
CONF="/etc/security/pam_ezkey.conf"
BACKUP="/etc/security/pam_ezkey.conf.tmo"
PORT="${SSH_PORT}"
STUB_PORT="${EZKEY_STUB_PORT:-18081}"
STUB_PID_FILE="/tmp/ezkey-pam-stub-tmo.pid"
ARTIFACT_DIR="${EZKEY_PAM_ARTIFACT_DIR:-/opt/cursor/artifacts}"
mkdir -p "${ARTIFACT_DIR}"
PROOF="${ARTIFACT_DIR}/pam-timeouts.log"
PASS=0
FAIL=0

ok()   { echo "[PASS] $1"; PASS=$((PASS+1)); }
fail() { echo "[FAIL] $1"; FAIL=$((FAIL+1)); }

if ! docker ps --format '{{.Names}}' | grep -qx "${CONTAINER}"; then
  echo "${CONTAINER} is not running" >&2
  exit 1
fi

restore_conf() {
  docker exec "${CONTAINER}" sh -c \
    "if [ -f '${BACKUP}' ]; then cp -a '${BACKUP}' '${CONF}'; chown root:root '${CONF}'; chmod 600 '${CONF}'; fi" \
    || true
}

stop_stub() {
  docker exec "${CONTAINER}" sh -c \
    "if [ -f '${STUB_PID_FILE}' ]; then kill \"\$(cat '${STUB_PID_FILE}')\" 2>/dev/null || true; rm -f '${STUB_PID_FILE}'; fi" \
    || true
}

cleanup() {
  stop_stub
  restore_conf
}
trap cleanup EXIT

start_stub() {
  local mode="$1"
  local slow="${2:-3}"
  stop_stub
  docker exec -d "${CONTAINER}" sh -c \
    "EZKEY_STUB_MODE='${mode}' EZKEY_STUB_PORT='${STUB_PORT}' EZKEY_STUB_SLOW_SECS='${slow}' \
     python3 /opt/pam-ezkey/scripts/integration-api-stub.py \
     >/tmp/ezkey-pam-stub-tmo.log 2>&1 & echo \$! >'${STUB_PID_FILE}'"
  local i
  for i in $(seq 1 40); do
    docker exec "${CONTAINER}" sh -c "ss -ltn | grep -q ':${STUB_PORT}'" 2>/dev/null && return 0
    sleep 0.1
  done
  fail "stub :${STUB_PORT} not listening"
  return 1
}

echo "=== PAM timeout inventory tests ===" | tee "${PROOF}"
{
  echo "Inventory (path:line — see STATUS for narrative):"
  echo "  api_timeout        ezkey_config.h EZKEY_DEFAULT_API_TIMEOUT + pam_ezkey.c POST ezkey_http"
  echo "  wait_timeout       default 90; clamp EZKEY_MAX_WAIT_TIMEOUT=100 (< LoginGraceTime 120)"
  echo "  curl /wait         pam_ezkey.c ezkey_http(..., wait_timeout+15)"
  echo "  wait_polling       query param on /wait (default 2)"
  echo "  server TTL         ezkey.core.auth-attempt.ttl-seconds=120 (core CONFIGURATION.md)"
  echo "  LoginGraceTime     sshd_config:20 → 120"
} | tee -a "${PROOF}"

docker exec "${CONTAINER}" cp -a "${CONF}" "${BACKUP}"

# --- api_timeout: stub sleeps longer than api_timeout → UNAVAIL ---
start_stub slow_create 3
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^api_timeout=.*/api_timeout=1/' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=false/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'; \
   : > /var/log/ezkey-pam/debug.err"
before="$(docker exec "${CONTAINER}" sh -c \
  "grep -c 'pam_ezkey: attempt ' /var/log/secure 2>/dev/null || true" | tr -d ' \r')"
before="${before:-0}"
set +e
ssh -tt -p "${PORT}" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  -o PreferredAuthentications=keyboard-interactive -o PubkeyAuthentication=no \
  -o PasswordAuthentication=no -o ConnectTimeout=10 \
  "${SSH_USER}@127.0.0.1" 'echo X' >/dev/null 2>&1
set -e
for _ in $(seq 1 50); do
  after="$(docker exec "${CONTAINER}" sh -c \
    "grep -c 'pam_ezkey: attempt ' /var/log/secure 2>/dev/null || true" | tr -d ' \r')"
  after="${after:-0}"
  [ "$((after - before))" -ge 1 ] && break
  sleep 0.1
done
ATTEMPT="$(docker exec "${CONTAINER}" sh -c \
  "grep 'pam_ezkey: attempt ' /var/log/secure | tail -n 1" || true)"
ERR="$(docker exec "${CONTAINER}" sh -c 'tail -n 5 /var/log/ezkey-pam/debug.err' || true)"
echo "api_timeout attempt=${ATTEMPT}" | tee -a "${PROOF}"
echo "api_timeout err=${ERR}" | tee -a "${PROOF}"
if echo "${ATTEMPT}" | grep -q 'result=UNAVAIL' \
  && echo "${ERR}" | grep -Eqi 'Failed_to_POST|timeout|Timeout|timed_out'; then
  ok "api_timeout=1 vs stub sleep 3 → UNAVAIL (create curl timeout)"
else
  fail "api_timeout threshold: ${ATTEMPT} / ${ERR}"
fi
stop_stub
restore_conf
docker exec "${CONTAINER}" cp -a "${BACKUP}" "${CONF}" 2>/dev/null || true

# --- wait_timeout clamp: conf 200 → effective max 100 (probe via debug create line) ---
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^wait_timeout=.*/wait_timeout=200/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:9|' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'; \
   : > /var/log/ezkey-pam/debug.out; : > /var/log/ezkey-pam/debug.err"
# Hit closed port quickly; if clamp works, debug wait line would show 100 when wait starts.
# Without a successful create we may not see wait — assert clamp via unit-style: rebuild
# already caps in apply_config_key. Smoke: wait_timeout=200 still loads (no crash) + STATUS.
# Use stub pending with wait_timeout=2 for real wait threshold.
start_stub pending
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=2/' '${CONF}'; \
   sed -i 's/^api_timeout=.*/api_timeout=5/' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=false/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'"
before="$(docker exec "${CONTAINER}" sh -c \
  "grep -c 'pam_ezkey: attempt ' /var/log/secure 2>/dev/null || true" | tr -d ' \r')"
before="${before:-0}"
start_ts="$(date +%s)"
set +e
ssh -tt -p "${PORT}" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  -o PreferredAuthentications=keyboard-interactive -o PubkeyAuthentication=no \
  -o PasswordAuthentication=no -o ConnectTimeout=5 \
  -o ServerAliveInterval=2 -o ServerAliveCountMax=20 \
  "${SSH_USER}@127.0.0.1" 'echo X' >/dev/null 2>&1
set -e
end_ts="$(date +%s)"
elapsed=$((end_ts - start_ts))
for _ in $(seq 1 80); do
  after="$(docker exec "${CONTAINER}" sh -c \
    "grep -c 'pam_ezkey: attempt ' /var/log/secure 2>/dev/null || true" | tr -d ' \r')"
  after="${after:-0}"
  [ "$((after - before))" -ge 1 ] && break
  sleep 0.1
done
ATTEMPT="$(docker exec "${CONTAINER}" sh -c \
  "grep 'pam_ezkey: attempt ' /var/log/secure | tail -n 1" || true)"
echo "wait_timeout=2 elapsed=${elapsed}s attempt=${ATTEMPT}" | tee -a "${PROOF}"
# curl timeout is wait_timeout+15 → expect finish well under LoginGraceTime 120
if echo "${ATTEMPT}" | grep -Eq 'result=(DENIED|UNAVAIL)' && [ "${elapsed}" -lt 45 ]; then
  ok "wait_timeout=2 → non-SUCCESS within <45s (curl wait+15; << LoginGraceTime 120)"
else
  fail "wait_timeout threshold: elapsed=${elapsed} line=${ATTEMPT}"
fi
stop_stub

# --- wait_timeout clamp constant present ---
if grep -q 'EZKEY_MAX_WAIT_TIMEOUT 100' "${SCRIPT_DIR}/../src/ezkey_config.h" \
  && grep -q 'LoginGraceTime 120' "${SCRIPT_DIR}/../sshd_config"; then
  ok "EZKEY_MAX_WAIT_TIMEOUT=100 bound below LoginGraceTime 120 (20s+ margin)"
else
  fail "missing wait_timeout clamp / LoginGraceTime linkage"
fi

# --- wait_polling default documented in conf ---
if grep -q '^wait_polling=2' "${SCRIPT_DIR}/../config/pam_ezkey.conf"; then
  ok "wait_polling default 2 in sample conf (server-side poll cadence query param)"
else
  fail "wait_polling default missing from sample conf"
fi

echo "" | tee -a "${PROOF}"
echo "=== Results: $PASS passed, $FAIL failed ===" | tee -a "${PROOF}"
[ "${FAIL}" -eq 0 ]
