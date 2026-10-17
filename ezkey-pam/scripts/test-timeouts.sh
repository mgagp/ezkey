#!/usr/bin/env bash
# Timeout inventory proofs (Patrick / #726 / P3 budget): exercise thresholds
# that stay short enough for CI/lab. Full matrix documented in STATUS.md.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
. "${SCRIPT_DIR}/common.sh"

require_cmd docker
require_cmd ssh
require_cmd python3
require_integration_keys

CONTAINER="${EZKEY_PAM_CONTAINER:-ezkey-pam-ssh}"
CONF="/etc/security/pam_ezkey.conf"
BACKUP="/etc/security/pam_ezkey.conf.tmo"
PORT="${SSH_PORT}"
STUB_PORT="${EZKEY_STUB_PORT:-18081}"
STUB_PID_FILE="/tmp/ezkey-pam-stub-tmo.pid"
STUB_REQ_LOG="/tmp/ezkey-pam-stub-reqs.log"
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
  docker exec "${CONTAINER}" sh -c "rm -f '${STUB_REQ_LOG}'; : >'${STUB_REQ_LOG}'" || true
  docker exec -d "${CONTAINER}" sh -c \
    "EZKEY_STUB_MODE='${mode}' EZKEY_STUB_PORT='${STUB_PORT}' EZKEY_STUB_SLOW_SECS='${slow}' \
     EZKEY_STUB_REQ_LOG='${STUB_REQ_LOG}' \
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

stub_wait_timeout_param() {
  # Last GET …/wait?timeout=N from stub request log.
  docker exec "${CONTAINER}" sh -c \
    "grep 'GET .*/wait' '${STUB_REQ_LOG}' 2>/dev/null | tail -n 1" \
    | grep -oE 'timeout=[0-9]+' | tail -n 1 | cut -d= -f2 || true
}

echo "=== PAM timeout inventory tests ===" | tee "${PROOF}"
{
  echo "Inventory (path:line — see STATUS for narrative):"
  echo "  EZKEY_AUTH_BUDGET_SECS 105  ezkey_config.h (margin under LoginGraceTime 120)"
  echo "  api_timeout max 10          ezkey_config.h + apply_config_key pam_ezkey.c"
  echo "  wait effective              min(conf, rem-CURL_SLACK) before /wait"
  echo "  curl /wait slack 15         EZKEY_CURL_WAIT_SLACK"
  echo "  Enter measure               EZKEY_MAX_ENTER_SECS 25 (CLOCK_MONOTONIC; no signals)"
  echo "  LoginGraceTime 120          sshd_config:20"
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

# --- wait_timeout clamp: conf 200 → effective ?timeout=90 in stub log ---
# Use mode ok (instant ACCEPTED) so we only assert the query param, not a 90s wait.
start_stub ok
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=200/' '${CONF}'; \
   sed -i 's/^api_timeout=.*/api_timeout=5/' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=false/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'"
before="$(docker exec "${CONTAINER}" sh -c \
  "grep -c 'pam_ezkey: attempt ' /var/log/secure 2>/dev/null || true" | tr -d ' \r')"
before="${before:-0}"
set +e
ssh -tt -p "${PORT}" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  -o PreferredAuthentications=keyboard-interactive -o PubkeyAuthentication=no \
  -o PasswordAuthentication=no -o ConnectTimeout=10 \
  "${SSH_USER}@127.0.0.1" 'echo X' >/dev/null 2>&1
set -e
for _ in $(seq 1 40); do
  after="$(docker exec "${CONTAINER}" sh -c \
    "grep -c 'pam_ezkey: attempt ' /var/log/secure 2>/dev/null || true" | tr -d ' \r')"
  after="${after:-0}"
  [ "$((after - before))" -ge 1 ] && break
  sleep 0.1
done
ATTEMPT="$(docker exec "${CONTAINER}" sh -c \
  "grep 'pam_ezkey: attempt ' /var/log/secure | tail -n 1" || true)"
GOT_TO="$(stub_wait_timeout_param)"
echo "clamp wait_timeout=200 → stub ?timeout=${GOT_TO} attempt=${ATTEMPT}" \
  | tee -a "${PROOF}"
docker exec "${CONTAINER}" cat "${STUB_REQ_LOG}" 2>/dev/null | tee -a "${PROOF}" || true
# Instant create → rem≈105 → wait=min(90,105-15)=90
if [ "${GOT_TO}" = "90" ] && echo "${ATTEMPT}" | grep -q 'result=SUCCESS'; then
  ok "wait_timeout=200 clamped: stub received ?timeout=90 (EZKEY_MAX_WAIT_TIMEOUT)"
else
  fail "clamp expected ?timeout=90 + SUCCESS, got timeout='${GOT_TO}' line=${ATTEMPT}"
fi
stop_stub
restore_conf

# --- wait_timeout=2 short path (still under budget) ---
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
GOT_TO="$(stub_wait_timeout_param)"
echo "wait_timeout=2 stub=?timeout=${GOT_TO} elapsed=${elapsed}s attempt=${ATTEMPT}" \
  | tee -a "${PROOF}"
if [ "${GOT_TO}" = "2" ] && echo "${ATTEMPT}" | grep -Eq 'result=(DENIED|UNAVAIL)' \
  && [ "${elapsed}" -lt 45 ]; then
  ok "wait_timeout=2 → stub ?timeout=2 + non-SUCCESS within <45s"
else
  fail "wait_timeout=2: timeout='${GOT_TO}' elapsed=${elapsed} line=${ATTEMPT}"
fi
stop_stub
restore_conf

# --- delayed Enter (under 25s): shrinks ?timeout= and still emits attempt line ---
start_stub ok
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=90/' '${CONF}'; \
   sed -i 's/^api_timeout=.*/api_timeout=5/' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=true/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'"

run_chal_ssh() {
  local log="$1"
  local ctrl="$2"
  : >"${log}"
  set +e
  python3 "${SCRIPT_DIR}/ssh-pty-capture.py" "${log}" "${ctrl}" \
    ssh -tt -p "${PORT}" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
    -o PreferredAuthentications=keyboard-interactive -o PubkeyAuthentication=no \
    -o PasswordAuthentication=no -o ConnectTimeout=15 \
    "${SSH_USER}@127.0.0.1" 'echo PAM_TMO_OK; exit 0' \
    >/dev/null 2>&1 &
  SSH_BG=$!
  set -e
}

wait_chal_visible() {
  local log="$1"
  local i
  for i in $(seq 1 80); do
    if tr -cd '\11\12\15\40-\176' <"${log}" 2>/dev/null \
      | grep -q 'Ezkey challenge code'; then
      return 0
    fi
    sleep 0.25
  done
  return 1
}

SSH_LOG="${ARTIFACT_DIR}/pam-timeouts-delayed-enter.log"
SSH_CTRL="$(mktemp -u /tmp/pam-tmo-ctrl.XXXXXX)"
mkfifo "${SSH_CTRL}"
run_chal_ssh "${SSH_LOG}" "${SSH_CTRL}"
wait_chal_visible "${SSH_LOG}" || true
DELAY_SECS=8
echo "Delayed Enter ${DELAY_SECS}s after challenge visible" | tee -a "${PROOF}"
sleep "${DELAY_SECS}"
printf 'enter\n' >"${SSH_CTRL}" 2>/dev/null || true
set +e
wait "${SSH_BG}"
SSH_RC=$?
set -e
rm -f "${SSH_CTRL}"

ATTEMPT="$(docker exec "${CONTAINER}" sh -c \
  "grep 'pam_ezkey: attempt ' /var/log/secure | tail -n 1" || true)"
GOT_TO="$(stub_wait_timeout_param)"
echo "delayed-enter stub=?timeout=${GOT_TO} ssh_rc=${SSH_RC} attempt=${ATTEMPT}" \
  | tee -a "${PROOF}"
docker exec "${CONTAINER}" cat "${STUB_REQ_LOG}" 2>/dev/null | tee -a "${PROOF}" || true

# After ~8s Enter, rem≈97 → wait=min(90,97-15)=82 (± a few for create/latency).
if echo "${ATTEMPT}" | grep -Eq 'result=(SUCCESS|UNAVAIL|DENIED)' \
  && [ -n "${GOT_TO}" ] && [ "${GOT_TO}" -lt 90 ] && [ "${GOT_TO}" -ge 70 ]; then
  ok "delayed Enter ${DELAY_SECS}s → ?timeout=${GOT_TO} (shrunk) + attempt line"
else
  fail "delayed Enter expected shrunk timeout in [70,89] + attempt, got '${GOT_TO}' ${ATTEMPT}"
fi
stop_stub
restore_conf

# --- Enter >25s → UNAVAIL + attempt line (CLOCK_MONOTONIC measure; no signals) ---
start_stub ok
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=90/' '${CONF}'; \
   sed -i 's/^api_timeout=.*/api_timeout=5/' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=true/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'; \
   : > /var/log/ezkey-pam/debug.err"
SSH_LOG="${ARTIFACT_DIR}/pam-timeouts-enter-over-25.log"
SSH_CTRL="$(mktemp -u /tmp/pam-tmo-ctrl.XXXXXX)"
mkfifo "${SSH_CTRL}"
before="$(docker exec "${CONTAINER}" sh -c \
  "grep -c 'pam_ezkey: attempt ' /var/log/secure 2>/dev/null || true" | tr -d ' \r')"
before="${before:-0}"
run_chal_ssh "${SSH_LOG}" "${SSH_CTRL}"
wait_chal_visible "${SSH_LOG}" || true
ENTER_OVER=26
echo "Enter after ${ENTER_OVER}s (> EZKEY_MAX_ENTER_SECS 25)" | tee -a "${PROOF}"
sleep "${ENTER_OVER}"
printf 'enter\n' >"${SSH_CTRL}" 2>/dev/null || true
set +e
wait "${SSH_BG}"
set -e
rm -f "${SSH_CTRL}"
for _ in $(seq 1 40); do
  after="$(docker exec "${CONTAINER}" sh -c \
    "grep -c 'pam_ezkey: attempt ' /var/log/secure 2>/dev/null || true" | tr -d ' \r')"
  after="${after:-0}"
  [ "$((after - before))" -ge 1 ] && break
  sleep 0.1
done
ATTEMPT="$(docker exec "${CONTAINER}" sh -c \
  "grep 'pam_ezkey: attempt ' /var/log/secure | tail -n 1" || true)"
ERR="$(docker exec "${CONTAINER}" sh -c 'tail -n 8 /var/log/ezkey-pam/debug.err' || true)"
echo "enter>25 attempt=${ATTEMPT}" | tee -a "${PROOF}"
echo "enter>25 err=${ERR}" | tee -a "${PROOF}"
if echo "${ATTEMPT}" | grep -q 'result=UNAVAIL' \
  && echo "${ERR}" | grep -Eqi 'exceeded_max_duration|Enter_exceeded'; then
  ok "Enter >25s → UNAVAIL + attempt line (monotonic duration; no signals)"
else
  fail "Enter >25s expected UNAVAIL+duration reason: ${ATTEMPT} / ${ERR}"
fi
stop_stub
restore_conf

# --- api_timeout cap: conf 60 but max 10 → slow_create 12 must UNAVAIL (~10s, not 60) ---
start_stub slow_create 12
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^api_timeout=.*/api_timeout=60/' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=false/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'; \
   : > /var/log/ezkey-pam/debug.err"
before="$(docker exec "${CONTAINER}" sh -c \
  "grep -c 'pam_ezkey: attempt ' /var/log/secure 2>/dev/null || true" | tr -d ' \r')"
before="${before:-0}"
start_ts="$(date +%s)"
set +e
ssh -tt -p "${PORT}" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  -o PreferredAuthentications=keyboard-interactive -o PubkeyAuthentication=no \
  -o PasswordAuthentication=no -o ConnectTimeout=20 \
  "${SSH_USER}@127.0.0.1" 'echo X' >/dev/null 2>&1
set -e
end_ts="$(date +%s)"
elapsed=$((end_ts - start_ts))
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
echo "api_timeout=60 cap elapsed=${elapsed}s attempt=${ATTEMPT}" | tee -a "${PROOF}"
echo "api_timeout=60 err=${ERR}" | tee -a "${PROOF}"
# Cap at 10: must fail well under 60s (allow ~10–20s wall for curl+SSH).
if echo "${ATTEMPT}" | grep -q 'result=UNAVAIL' \
  && echo "${ERR}" | grep -Eqi 'Failed_to_POST|timeout|Timeout|timed_out' \
  && [ "${elapsed}" -lt 35 ]; then
  ok "api_timeout conf=60 capped at 10: UNAVAIL in ${elapsed}s (stub sleep 12)"
else
  fail "api_timeout cap 60→10: elapsed=${elapsed} ${ATTEMPT} / ${ERR}"
fi
stop_stub
restore_conf

# --- constants linkage ---
if grep -q 'EZKEY_AUTH_BUDGET_SECS 105' "${SCRIPT_DIR}/../src/ezkey_config.h" \
  && grep -q 'EZKEY_MAX_WAIT_TIMEOUT 90' "${SCRIPT_DIR}/../src/ezkey_config.h" \
  && grep -q 'EZKEY_MAX_API_TIMEOUT 10' "${SCRIPT_DIR}/../src/ezkey_config.h" \
  && grep -q 'EZKEY_MAX_ENTER_SECS 25' "${SCRIPT_DIR}/../src/ezkey_config.h" \
  && ! grep -E '\bsigaction\s*\(|\balarm\s*\(' "${SCRIPT_DIR}/../src/pam_ezkey.c" \
  && grep -q 'LoginGraceTime 120' "${SCRIPT_DIR}/../sshd_config"; then
  ok "budget 105 / max wait 90 / max api 10 / max enter 25; no signals in pam_ezkey.c"
else
  fail "missing budget constants or sigaction/alarm still present"
fi

if grep -q '^wait_polling=2' "${SCRIPT_DIR}/../config/pam_ezkey.conf"; then
  ok "wait_polling default 2 in sample conf"
else
  fail "wait_polling default missing from sample conf"
fi

echo "" | tee -a "${PROOF}"
echo "=== Results: $PASS passed, $FAIL failed ===" | tee -a "${PROOF}"
[ "${FAIL}" -eq 0 ]
