#!/usr/bin/env bash
# Timeout inventory proofs (Patrick / #726 / P3 budget / #895 PR-B): exercise
# thresholds that stay short enough for CI/lab. Full matrix in STATUS.md.
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

# Isolate debug.out/err per case so scrapes cannot leak floor-15 wait_left etc. (#907 Patrick).
reset_case_debug() {
  local tag="${1:-case}"
  docker exec "${CONTAINER}" sh -c \
    ": > /var/log/ezkey-pam/debug.out
     : > /var/log/ezkey-pam/debug.err
     printf '%s\n' '____EZKEY_CASE_${tag}____' >> /var/log/ezkey-pam/debug.out" \
    || true
}

start_stub() {
  local mode="$1"
  local slow="${2:-3}"
  local applied="${3:-30}"
  stop_stub
  docker exec "${CONTAINER}" sh -c "rm -f '${STUB_REQ_LOG}'; : >'${STUB_REQ_LOG}'" || true
  reset_case_debug "${mode}"
  # Bind-mount host scripts into the running CI/runtime container is not
  # available — copy the stub from the image workspace path if present, else
  # from the repo bind used by up.sh. ci-m1 copies scripts into the image.
  docker exec -d "${CONTAINER}" sh -c \
    "EZKEY_STUB_MODE='${mode}' EZKEY_STUB_PORT='${STUB_PORT}' EZKEY_STUB_SLOW_SECS='${slow}' \
     EZKEY_STUB_APPLIED_TTL='${applied}' EZKEY_STUB_REQ_LOG='${STUB_REQ_LOG}' \
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

# #941: no-challenge path uses PAM_PROMPT_ECHO_OFF — auto-Enter via PTY helper.
ssh_attempt_once() {
  local log="${ARTIFACT_DIR}/pam-timeouts-ssh-once.log"
  set +e
  ezkey_ssh_kbdint_autoenter "${log}" 'echo X' 10
  set -e
}

wait_attempt_line() {
  local before="$1"
  local after
  local _
  for _ in $(seq 1 50); do
    after="$(docker exec "${CONTAINER}" sh -c \
      "grep -c 'pam_ezkey: attempt ' /var/log/secure 2>/dev/null || true" | tr -d ' \r')"
    after="${after:-0}"
    [ "$((after - before))" -ge 1 ] && return 0
    sleep 0.1
  done
  return 1
}

last_attempt_line() {
  docker exec "${CONTAINER}" sh -c \
    "grep 'pam_ezkey: attempt ' /var/log/secure | tail -n 1" || true
}

# Last wait ?timeout= (chunk size under #907 chunked wait; typically ≤5).
stub_wait_timeout_param() {
  docker exec "${CONTAINER}" sh -c \
    "grep 'GET .*/wait' '${STUB_REQ_LOG}' 2>/dev/null | tail -n 1" \
    | grep -oE 'timeout=[0-9]+' | tail -n 1 | cut -d= -f2 || true
}

# Sum of all wait ?timeout= values (covers full effective wait across chunks).
stub_wait_timeout_sum() {
  docker exec "${CONTAINER}" sh -c \
    "grep 'GET .*/wait' '${STUB_REQ_LOG}' 2>/dev/null \
     | grep -oE 'timeout=[0-9]+' | cut -d= -f2 \
     | awk '{s+=\$1} END {print s+0}'" | tr -d ' \r'
}

attempt_timeout_seconds() {
  local line="$1"
  echo "${line}" | grep -oE 'timeoutSeconds=(-|[0-9]+)' | head -n1 | cut -d= -f2 || true
}

stub_create_ttl() {
  docker exec "${CONTAINER}" sh -c \
    "grep 'POST .*/auth-attempts' '${STUB_REQ_LOG}' 2>/dev/null | grep -v cancel | tail -n 1" \
    | grep -oE '"ttlSeconds"[[:space:]]*:[[:space:]]*[0-9]+' | tail -n 1 \
    | grep -oE '[0-9]+$' || true
}

stub_saw_cancel() {
  docker exec "${CONTAINER}" sh -c \
    "grep -c 'POST .*/cancel' '${STUB_REQ_LOG}' 2>/dev/null || true" | tr -d ' \r'
}

echo "=== PAM timeout inventory tests (#895 PR-B) ===" | tee "${PROOF}"
{
  echo "Inventory (path:line — see STATUS for narrative):"
  echo "  EZKEY_AUTH_BUDGET_SECS 105  ezkey_config.h (margin under LoginGraceTime 120)"
  echo "  EZKEY_DEFAULT_WAIT_TIMEOUT 90  ezkey_config.h"
  echo "  EZKEY_MIN_TTL_SECONDS 15    create ttlSeconds floor (Integration API)"
  echo "  api_timeout max 10          ezkey_config.h + apply_config_key pam_ezkey.c"
  echo "  wait effective              min(conf, applied_ttl, rem-CURL_SLACK); chunked ≤5s (#907)"
  echo "  curl /wait slack 15         EZKEY_CURL_WAIT_SLACK (cancel margin inside)"
  echo "  Enter measure               EZKEY_MAX_ENTER_SECS 25 (CLOCK_MONOTONIC; no alarm)"
  echo "  wait SIGTERM                absorbed only during /wait (flag); restore after"
  echo "  LoginGraceTime 120          sshd_config (runbook: >= 120 s)"
  echo "  Auth /pending rate          10/min (ezkey.rate-limit.pending.*)"
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
ssh_attempt_once
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

# --- wait_timeout clamp: conf 200 → ?timeout=90 + ttlSeconds=90 ---
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
ssh_attempt_once
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
GOT_TTL="$(stub_create_ttl)"
AT_TO="$(attempt_timeout_seconds "${ATTEMPT}")"
echo "clamp wait_timeout=200 → chunk=${GOT_TO} ttlSeconds=${GOT_TTL} attempt_timeoutSeconds=${AT_TO} attempt=${ATTEMPT}" \
  | tee -a "${PROOF}"
docker exec "${CONTAINER}" cat "${STUB_REQ_LOG}" 2>/dev/null | tee -a "${PROOF}" || true
if [ "${GOT_TTL}" = "90" ] && [ "${AT_TO}" = "90" ] \
  && [ -n "${GOT_TO}" ] && [ "${GOT_TO}" -le 5 ] \
  && echo "${ATTEMPT}" | grep -q 'result=SUCCESS'; then
  ok "wait_timeout=200 clamped: ttlSeconds=90 + attempt timeoutSeconds=90 (chunked ≤5)"
else
  fail "clamp expected ttl=90 timeoutSeconds=90 chunk≤5 + SUCCESS, got ttl='${GOT_TTL}' chunk='${GOT_TO}' at='${AT_TO}' line=${ATTEMPT}"
fi
# #941 F1: waiting message must reach the PTY (prompt path, not buffered pam_info).
if ezkey_ssh_capture_has_prewait_prompt \
  "${ARTIFACT_DIR}/pam-timeouts-ssh-once.log"; then
  ok "#941 waiting prompt visible in SSH capture (Approve… / Press Enter)"
  {
    echo "=== #941 capture excerpt ==="
    tr -cd '\11\12\15\40-\176' <"${ARTIFACT_DIR}/pam-timeouts-ssh-once.log" \
      | grep -E 'Approve this SSH|Press Enter' || true
  } | tee -a "${PROOF}"
else
  fail "#941 waiting prompt missing from SSH capture"
  tr -cd '\11\12\15\40-\176' <"${ARTIFACT_DIR}/pam-timeouts-ssh-once.log" \
    | tee -a "${PROOF}" || true
fi
stop_stub
restore_conf

# --- wait_timeout floor 15: conf 2 → ttlSeconds=15 + ?timeout=15 ---
start_stub ok
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
set +e
ssh_attempt_once
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
GOT_TTL="$(stub_create_ttl)"
AT_TO="$(attempt_timeout_seconds "${ATTEMPT}")"
echo "floor wait_timeout=2 → ttl=${GOT_TTL} chunk=${GOT_TO} attempt_timeoutSeconds=${AT_TO} attempt=${ATTEMPT}" \
  | tee -a "${PROOF}"
if [ "${GOT_TTL}" = "15" ] && [ "${AT_TO}" = "15" ] \
  && [ -n "${GOT_TO}" ] && [ "${GOT_TO}" -le 5 ] \
  && echo "${ATTEMPT}" | grep -q 'result=SUCCESS'; then
  ok "wait_timeout=2 clamped to EZKEY_MIN_TTL_SECONDS 15 (ttl + attempt timeoutSeconds)"
else
  fail "floor 15 expected ttl=15 timeoutSeconds=15 chunk≤5 + SUCCESS, got ttl='${GOT_TTL}' chunk='${GOT_TO}' at='${AT_TO}' ${ATTEMPT}"
fi
stop_stub
restore_conf

# --- pending wait → TIMEOUT + cancel best-effort ---
start_stub pending
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=15/' '${CONF}'; \
   sed -i 's/^api_timeout=.*/api_timeout=5/' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=false/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'"
before="$(docker exec "${CONTAINER}" sh -c \
  "grep -c 'pam_ezkey: attempt ' /var/log/secure 2>/dev/null || true" | tr -d ' \r')"
before="${before:-0}"
start_ts="$(date +%s)"
set +e
ssh_attempt_once
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
GOT_SUM="$(stub_wait_timeout_sum)"
CANCEL_N="$(stub_saw_cancel)"
echo "pending TIMEOUT stub chunk=${GOT_TO} sum=${GOT_SUM} cancel=${CANCEL_N} elapsed=${elapsed}s attempt=${ATTEMPT}" \
  | tee -a "${PROOF}"
docker exec "${CONTAINER}" cat "${STUB_REQ_LOG}" 2>/dev/null | tee -a "${PROOF}" || true
if [ "${GOT_SUM}" = "15" ] && echo "${ATTEMPT}" | grep -q 'result=TIMEOUT' \
  && echo "${ATTEMPT}" | grep -q 'cancel=ok' \
  && [ "${CANCEL_N}" -ge 1 ] && [ "${elapsed}" -lt 60 ]; then
  ok "pending > wait_timeout → result=TIMEOUT + cancel=ok (chunk sum=15)"
else
  fail "TIMEOUT+cancel: sum='${GOT_SUM}' chunk='${GOT_TO}' cancel=${CANCEL_N} elapsed=${elapsed} line=${ATTEMPT}"
fi
stop_stub
restore_conf

# --- EXPIRED summary (distinct from DENIED) + cancel ---
start_stub expired
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=30/' '${CONF}'; \
   sed -i 's/^api_timeout=.*/api_timeout=5/' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=false/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'"
before="$(docker exec "${CONTAINER}" sh -c \
  "grep -c 'pam_ezkey: attempt ' /var/log/secure 2>/dev/null || true" | tr -d ' \r')"
before="${before:-0}"
set +e
ssh_attempt_once
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
CANCEL_N="$(stub_saw_cancel)"
echo "EXPIRED attempt=${ATTEMPT} cancel=${CANCEL_N}" | tee -a "${PROOF}"
if echo "${ATTEMPT}" | grep -q 'result=EXPIRED' \
  && echo "${ATTEMPT}" | grep -q 'cancel=ok' \
  && [ "${CANCEL_N}" -ge 1 ]; then
  ok "wait status EXPIRED → result=EXPIRED (not DENIED) + cancel=ok"
else
  fail "EXPIRED expected: ${ATTEMPT} cancel=${CANCEL_N}"
fi
stop_stub
restore_conf

# --- REJECTED → DENIED (not EXPIRED/TIMEOUT) ---
start_stub rejected
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=30/' '${CONF}'; \
   sed -i 's/^api_timeout=.*/api_timeout=5/' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=false/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'"
before="$(docker exec "${CONTAINER}" sh -c \
  "grep -c 'pam_ezkey: attempt ' /var/log/secure 2>/dev/null || true" | tr -d ' \r')"
before="${before:-0}"
set +e
ssh_attempt_once
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
echo "REJECTED→DENIED attempt=${ATTEMPT}" | tee -a "${PROOF}"
if echo "${ATTEMPT}" | grep -q 'result=DENIED' \
  && echo "${ATTEMPT}" | grep -q 'cancel=ok'; then
  ok "wait status REJECTED → result=DENIED + cancel=ok"
else
  fail "DENIED expected for REJECTED: ${ATTEMPT}"
fi
stop_stub
restore_conf

# --- delayed Enter (under 25s): shrinks ?timeout= and still emits attempt line ---
# Christophe: isolate debug.out (start_stub resets; re-truncate after conf; scrape last).
start_stub ok
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=90/' '${CONF}'; \
   sed -i 's/^api_timeout=.*/api_timeout=5/' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=true/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'"
reset_case_debug "delayed_enter"

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
OUT="$(docker exec "${CONTAINER}" sh -c 'cat /var/log/ezkey-pam/debug.out' || true)"
# Filelog sanitizes '=' → '_'; scrape this attempt's wait_left (last match after truncate).
WAIT_LEFT="$(echo "${OUT}" | grep -oE 'wait_left_[0-9]+' | tail -n1 | cut -d_ -f3 || true)"
echo "delayed-enter chunk=${GOT_TO} wait_left=${WAIT_LEFT} ssh_rc=${SSH_RC} attempt=${ATTEMPT}" \
  | tee -a "${PROOF}"
docker exec "${CONTAINER}" cat "${STUB_REQ_LOG}" 2>/dev/null | tee -a "${PROOF}" || true
echo "delayed-enter debug.out=${OUT}" | tee -a "${PROOF}"

# After ~8s Enter, rem≈97 → effective wait≈82 (± a few); URL chunk ≤5.
if echo "${ATTEMPT}" | grep -Eq 'result=(SUCCESS|UNAVAIL|DENIED|TIMEOUT|EXPIRED)' \
  && [ -n "${WAIT_LEFT}" ] && [ "${WAIT_LEFT}" -lt 90 ] && [ "${WAIT_LEFT}" -ge 70 ] \
  && [ -n "${GOT_TO}" ] && [ "${GOT_TO}" -le 5 ]; then
  ok "delayed Enter ${DELAY_SECS}s → wait_left=${WAIT_LEFT} (shrunk) + chunk≤5"
else
  fail "delayed Enter expected wait_left in [70,89] + chunk≤5, got wait_left='${WAIT_LEFT}' chunk='${GOT_TO}' ${ATTEMPT}"
fi
stop_stub
restore_conf

# --- Enter >25s → UNAVAIL + attempt line + cancel (create already done) ---
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
CANCEL_N="$(stub_saw_cancel)"
echo "enter>25 attempt=${ATTEMPT} cancel=${CANCEL_N}" | tee -a "${PROOF}"
echo "enter>25 err=${ERR}" | tee -a "${PROOF}"
if echo "${ATTEMPT}" | grep -q 'result=UNAVAIL' \
  && echo "${ERR}" | grep -Eqi 'exceeded_max_duration|Enter_exceeded' \
  && [ "${CANCEL_N}" -ge 1 ]; then
  ok "Enter >25s → UNAVAIL + cancel (monotonic duration; no signals)"
else
  fail "Enter >25s expected UNAVAIL+cancel: ${ATTEMPT} cancel=${CANCEL_N} / ${ERR}"
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
ssh_attempt_once
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
if echo "${ATTEMPT}" | grep -q 'result=UNAVAIL' \
  && echo "${ERR}" | grep -Eqi 'Failed_to_POST|timeout|Timeout|timed_out' \
  && [ "${elapsed}" -lt 35 ]; then
  ok "api_timeout conf=60 capped at 10: UNAVAIL in ${elapsed}s (stub sleep 12)"
else
  fail "api_timeout cap 60→10: elapsed=${elapsed} ${ATTEMPT} / ${ERR}"
fi
stop_stub
restore_conf

# --- B1 legacy_create: no ttlSeconds/expiresAt in response → fall back to wait_timeout ---
start_stub legacy_create
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=45/' '${CONF}'; \
   sed -i 's/^api_timeout=.*/api_timeout=5/' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=false/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'; \
   : > /var/log/ezkey-pam/debug.out"
before="$(docker exec "${CONTAINER}" sh -c \
  "grep -c 'pam_ezkey: attempt ' /var/log/secure 2>/dev/null || true" | tr -d ' \r')"
before="${before:-0}"
ssh_attempt_once
wait_attempt_line "${before}" || true
ATTEMPT="$(last_attempt_line)"
GOT_TO="$(stub_wait_timeout_param)"
GOT_TTL="$(stub_create_ttl)"
AT_TO="$(attempt_timeout_seconds "${ATTEMPT}")"
REQ_LOG_SNAP="$(docker exec "${CONTAINER}" cat "${STUB_REQ_LOG}" 2>/dev/null || true)"
echo "legacy_create ttl_sent=${GOT_TTL} chunk=${GOT_TO} attempt_timeoutSeconds=${AT_TO} attempt=${ATTEMPT}" \
  | tee -a "${PROOF}"
echo "${REQ_LOG_SNAP}" | tee -a "${PROOF}" || true
# PAM still sends ttlSeconds; legacy response has none → wait uses conf=45.
OUT="$(docker exec "${CONTAINER}" sh -c 'tail -n 20 /var/log/ezkey-pam/debug.out' || true)"
echo "legacy_create debug.out=${OUT}" | tee -a "${PROOF}"
# Filelog sanitizes '=' → '_', so look for applied_ttl_45 (not applied_ttl=45).
if [ "${GOT_TTL}" = "45" ] && [ "${AT_TO}" = "45" ] \
  && [ -n "${GOT_TO}" ] && [ "${GOT_TO}" -le 5 ] \
  && echo "${ATTEMPT}" | grep -q 'result=SUCCESS' \
  && echo "${ATTEMPT}" | grep -q 'cancel=skipped' \
  && echo "${OUT}" | grep -Eq 'applied_ttl_45'; then
  ok "legacy_create: no ttl/expiresAt → fallback wait_timeout=45 + SUCCESS + cancel=skipped"
else
  fail "legacy_create expected ttl=45 timeoutSeconds=45 applied_ttl_45 SUCCESS cancel=skipped, got ttl='${GOT_TTL}' chunk='${GOT_TO}' at='${AT_TO}' ${ATTEMPT}"
fi
stop_stub
restore_conf

# --- Nit: legacy_create + REJECTED → DENIED + cancel=ok ---
start_stub legacy_rejected
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=45/' '${CONF}'; \
   sed -i 's/^api_timeout=.*/api_timeout=5/' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=false/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'"
before="$(docker exec "${CONTAINER}" sh -c \
  "grep -c 'pam_ezkey: attempt ' /var/log/secure 2>/dev/null || true" | tr -d ' \r')"
before="${before:-0}"
ssh_attempt_once
wait_attempt_line "${before}" || true
ATTEMPT="$(last_attempt_line)"
GOT_TO="$(stub_wait_timeout_param)"
AT_TO="$(attempt_timeout_seconds "${ATTEMPT}")"
CANCEL_N="$(stub_saw_cancel)"
echo "legacy_rejected chunk=${GOT_TO} attempt_timeoutSeconds=${AT_TO} cancel=${CANCEL_N} attempt=${ATTEMPT}" \
  | tee -a "${PROOF}"
if [ "${AT_TO}" = "45" ] \
  && [ -n "${GOT_TO}" ] && [ "${GOT_TO}" -le 5 ] \
  && echo "${ATTEMPT}" | grep -q 'result=DENIED' \
  && echo "${ATTEMPT}" | grep -q 'cancel=ok' \
  && [ "${CANCEL_N}" -ge 1 ]; then
  ok "legacy_rejected: no applied TTL + REJECTED → DENIED cancel=ok"
else
  fail "legacy_rejected expected timeoutSeconds=45 DENIED cancel=ok: at='${AT_TO}' chunk='${GOT_TO}' ${ATTEMPT}"
fi
stop_stub
restore_conf

# --- Nit: legacy_create + pending timeout → TIMEOUT + cancel=ok ---
start_stub legacy_pending
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=15/' '${CONF}'; \
   sed -i 's/^api_timeout=.*/api_timeout=5/' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=false/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'"
before="$(docker exec "${CONTAINER}" sh -c \
  "grep -c 'pam_ezkey: attempt ' /var/log/secure 2>/dev/null || true" | tr -d ' \r')"
before="${before:-0}"
start_ts="$(date +%s)"
set +e
ssh_attempt_once
set -e
end_ts="$(date +%s)"
elapsed=$((end_ts - start_ts))
wait_attempt_line "${before}" || true
ATTEMPT="$(last_attempt_line)"
GOT_TO="$(stub_wait_timeout_param)"
GOT_SUM="$(stub_wait_timeout_sum)"
CANCEL_N="$(stub_saw_cancel)"
echo "legacy_pending chunk=${GOT_TO} sum=${GOT_SUM} cancel=${CANCEL_N} elapsed=${elapsed}s attempt=${ATTEMPT}" \
  | tee -a "${PROOF}"
if [ "${GOT_SUM}" = "15" ] \
  && echo "${ATTEMPT}" | grep -q 'result=TIMEOUT' \
  && echo "${ATTEMPT}" | grep -q 'cancel=ok' \
  && [ "${CANCEL_N}" -ge 1 ] \
  && [ "${elapsed}" -lt 60 ]; then
  ok "legacy_pending: no applied TTL + wait timeout → TIMEOUT cancel=ok (chunk sum=15)"
else
  fail "legacy_pending expected sum=15 TIMEOUT cancel=ok: sum='${GOT_SUM}' chunk='${GOT_TO}' ${ATTEMPT}"
fi
stop_stub
restore_conf

# --- B1 cancel_fail: cancel 503 must not change PAM EXPIRED outcome ---
start_stub cancel_fail
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=30/' '${CONF}'; \
   sed -i 's/^api_timeout=.*/api_timeout=5/' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=false/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'; \
   : > /var/log/ezkey-pam/debug.err; : > /var/log/ezkey-pam/debug.out"
before="$(docker exec "${CONTAINER}" sh -c \
  "grep -c 'pam_ezkey: attempt ' /var/log/secure 2>/dev/null || true" | tr -d ' \r')"
before="${before:-0}"
ssh_attempt_once
wait_attempt_line "${before}" || true
ATTEMPT="$(last_attempt_line)"
CANCEL_N="$(stub_saw_cancel)"
ERR="$(docker exec "${CONTAINER}" sh -c 'tail -n 12 /var/log/ezkey-pam/debug.err' || true)"
OUT="$(docker exec "${CONTAINER}" sh -c 'tail -n 12 /var/log/ezkey-pam/debug.out' || true)"
echo "cancel_fail attempt=${ATTEMPT} cancel=${CANCEL_N}" | tee -a "${PROOF}"
echo "cancel_fail err=${ERR}" | tee -a "${PROOF}"
echo "cancel_fail out=${OUT}" | tee -a "${PROOF}"
# Filelog sanitizes spaces → underscores; HTTP 503 still logged; summary stays EXPIRED.
if echo "${ATTEMPT}" | grep -q 'result=EXPIRED' \
  && echo "${ATTEMPT}" | grep -q 'cancel=fail' \
  && [ "${CANCEL_N}" -ge 1 ] \
  && echo "${OUT}${ERR}" | grep -Eqi 'cancel_best-effort.*HTTP_503|HTTP_503|cancel_best-effort_failed'; then
  ok "cancel_fail 503: result=EXPIRED unchanged + cancel=fail"
else
  fail "cancel_fail expected EXPIRED+cancel=fail: ${ATTEMPT} cancel=${CANCEL_N} / ${OUT} ${ERR}"
fi
stop_stub
restore_conf

# --- Christophe: stub cancel HTTP 409 → authpriv line must contain cancel=final ---
# Contract: pam_rc / result stay EXPIRED; cancel=final (not ok); no pam_rc effect.
start_stub cancel_final
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=30/' '${CONF}'; \
   sed -i 's/^api_timeout=.*/api_timeout=5/' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=false/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'; \
   : > /var/log/ezkey-pam/debug.err; : > /var/log/ezkey-pam/debug.out"
before="$(docker exec "${CONTAINER}" sh -c \
  "grep -c 'pam_ezkey: attempt ' /var/log/secure 2>/dev/null || true" | tr -d ' \r')"
before="${before:-0}"
ssh_attempt_once
wait_attempt_line "${before}" || true
ATTEMPT="$(last_attempt_line)"
CANCEL_N="$(stub_saw_cancel)"
OUT="$(docker exec "${CONTAINER}" sh -c 'tail -n 12 /var/log/ezkey-pam/debug.out' || true)"
echo "cancel_final attempt=${ATTEMPT} cancel=${CANCEL_N}" | tee -a "${PROOF}"
echo "cancel_final out=${OUT}" | tee -a "${PROOF}"
# Primary assertion (Christophe): stub 409 on cancel → summary line contains cancel=final.
if ! echo "${ATTEMPT}" | grep -q 'cancel=final'; then
  fail "stub cancel 409 → expected attempt line to contain cancel=final, got: ${ATTEMPT}"
elif ! echo "${ATTEMPT}" | grep -q 'result=EXPIRED'; then
  fail "cancel_final: pam_rc/result must stay EXPIRED, got: ${ATTEMPT}"
elif [ "${CANCEL_N}" -lt 1 ]; then
  fail "cancel_final: expected POST …/cancel in stub log"
elif ! echo "${OUT}" | grep -Eqi 'cancel_best-effort.*HTTP_409|HTTP_409'; then
  fail "cancel_final: expected debug HTTP_409, got: ${OUT}"
else
  ok "stub cancel 409 → attempt line contains cancel=final (result=EXPIRED unchanged)"
fi
stop_stub
restore_conf

# --- applied TTL < requested via timeoutSeconds (#898): wait aligns on applied ---
start_stub applied_shrink 3 30
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=90/' '${CONF}'; \
   sed -i 's/^api_timeout=.*/api_timeout=5/' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=false/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'"
before="$(docker exec "${CONTAINER}" sh -c \
  "grep -c 'pam_ezkey: attempt ' /var/log/secure 2>/dev/null || true" | tr -d ' \r')"
before="${before:-0}"
ssh_attempt_once
wait_attempt_line "${before}" || true
ATTEMPT="$(last_attempt_line)"
GOT_TO="$(stub_wait_timeout_param)"
GOT_TTL="$(stub_create_ttl)"
AT_TO="$(attempt_timeout_seconds "${ATTEMPT}")"
REQ_SNAP="$(docker exec "${CONTAINER}" cat "${STUB_REQ_LOG}" 2>/dev/null || true)"
echo "applied_shrink sent_ttl=${GOT_TTL} chunk=${GOT_TO} attempt_timeoutSeconds=${AT_TO} attempt=${ATTEMPT}" \
  | tee -a "${PROOF}"
echo "${REQ_SNAP}" | tee -a "${PROOF}" || true
# Request still ttlSeconds=90; response carries timeoutSeconds=30 → attempt line.
if [ "${GOT_TTL}" = "90" ] && [ "${AT_TO}" = "30" ] \
  && [ -n "${GOT_TO}" ] && [ "${GOT_TO}" -le 5 ] \
  && echo "${ATTEMPT}" | grep -q 'result=SUCCESS' \
  && echo "${ATTEMPT}" | grep -q 'cancel=skipped'; then
  ok "applied_shrink: asked ttlSeconds=90, attempt timeoutSeconds=30 + cancel=skipped"
else
  fail "applied_shrink expected sent=90 timeoutSeconds=30 SUCCESS cancel=skipped, got sent='${GOT_TTL}' at='${AT_TO}' chunk='${GOT_TO}' ${ATTEMPT}"
fi
stop_stub
restore_conf

# --- M1 applied_grow: server timeoutSeconds=120 for ask 45 → wait stays 45 ---
start_stub applied_grow
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=45/' '${CONF}'; \
   sed -i 's/^api_timeout=.*/api_timeout=5/' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=false/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'"
before="$(docker exec "${CONTAINER}" sh -c \
  "grep -c 'pam_ezkey: attempt ' /var/log/secure 2>/dev/null || true" | tr -d ' \r')"
before="${before:-0}"
ssh_attempt_once
wait_attempt_line "${before}" || true
ATTEMPT="$(last_attempt_line)"
GOT_TO="$(stub_wait_timeout_param)"
GOT_TTL="$(stub_create_ttl)"
AT_TO="$(attempt_timeout_seconds "${ATTEMPT}")"
echo "applied_grow sent_ttl=${GOT_TTL} chunk=${GOT_TO} attempt_timeoutSeconds=${AT_TO} attempt=${ATTEMPT}" \
  | tee -a "${PROOF}"
if [ "${GOT_TTL}" = "45" ] && [ "${AT_TO}" = "45" ] \
  && [ -n "${GOT_TO}" ] && [ "${GOT_TO}" -le 5 ] \
  && echo "${ATTEMPT}" | grep -q 'result=SUCCESS'; then
  ok "applied_grow: asked 45, server timeoutSeconds=120 → attempt stays 45"
else
  fail "applied_grow expected sent=45 timeoutSeconds=45 SUCCESS, got sent='${GOT_TTL}' at='${AT_TO}' chunk='${GOT_TO}' ${ATTEMPT}"
fi
stop_stub
restore_conf

# --- M1 timeoutSeconds=0 → ignore, keep conf wait ---
start_stub applied_zero
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=45/' '${CONF}'; \
   sed -i 's/^api_timeout=.*/api_timeout=5/' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=false/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'"
before="$(docker exec "${CONTAINER}" sh -c \
  "grep -c 'pam_ezkey: attempt ' /var/log/secure 2>/dev/null || true" | tr -d ' \r')"
before="${before:-0}"
ssh_attempt_once
wait_attempt_line "${before}" || true
ATTEMPT="$(last_attempt_line)"
GOT_TO="$(stub_wait_timeout_param)"
GOT_TTL="$(stub_create_ttl)"
AT_TO="$(attempt_timeout_seconds "${ATTEMPT}")"
echo "applied_zero sent_ttl=${GOT_TTL} chunk=${GOT_TO} attempt_timeoutSeconds=${AT_TO} attempt=${ATTEMPT}" \
  | tee -a "${PROOF}"
if [ "${GOT_TTL}" = "45" ] && [ "${AT_TO}" = "45" ] \
  && [ -n "${GOT_TO}" ] && [ "${GOT_TO}" -le 5 ] \
  && echo "${ATTEMPT}" | grep -q 'result=SUCCESS'; then
  ok "applied_zero: timeoutSeconds=0 ignored → wait_timeout=45"
else
  fail "applied_zero expected sent=45 timeoutSeconds=45 SUCCESS, got sent='${GOT_TTL}' at='${AT_TO}' chunk='${GOT_TO}' ${ATTEMPT}"
fi
stop_stub
restore_conf

# --- M1 timeoutSeconds=null → ignore, keep conf wait ---
start_stub applied_null
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=45/' '${CONF}'; \
   sed -i 's/^api_timeout=.*/api_timeout=5/' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=false/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'"
before="$(docker exec "${CONTAINER}" sh -c \
  "grep -c 'pam_ezkey: attempt ' /var/log/secure 2>/dev/null || true" | tr -d ' \r')"
before="${before:-0}"
ssh_attempt_once
wait_attempt_line "${before}" || true
ATTEMPT="$(last_attempt_line)"
GOT_TO="$(stub_wait_timeout_param)"
GOT_TTL="$(stub_create_ttl)"
AT_TO="$(attempt_timeout_seconds "${ATTEMPT}")"
echo "applied_null sent_ttl=${GOT_TTL} chunk=${GOT_TO} attempt_timeoutSeconds=${AT_TO} attempt=${ATTEMPT}" \
  | tee -a "${PROOF}"
if [ "${GOT_TTL}" = "45" ] && [ "${AT_TO}" = "45" ] \
  && [ -n "${GOT_TO}" ] && [ "${GOT_TO}" -le 5 ] \
  && echo "${ATTEMPT}" | grep -q 'result=SUCCESS'; then
  ok "applied_null: timeoutSeconds=null ignored → wait_timeout=45"
else
  fail "applied_null expected sent=45 timeoutSeconds=45 SUCCESS, got sent='${GOT_TTL}' at='${AT_TO}' chunk='${GOT_TO}' ${ATTEMPT}"
fi
stop_stub
restore_conf

# --- budget / planned-wait harness (replaces grep-only AUTH_BUDGET 105 check) ---
# rem-too-small → −1 is the UNAVAIL-before-create path; executable in make test-budget.
if make -C "${SCRIPT_DIR}/.." test-budget >/tmp/pam-budget-harness.out 2>&1; then
  cat /tmp/pam-budget-harness.out | tee -a "${PROOF}"
  ok "budget harness: AUTH_BUDGET 105 + rem≤30 → planned -1 (UNAVAIL before create)"
else
  cat /tmp/pam-budget-harness.out | tee -a "${PROOF}" || true
  fail "budget harness failed"
fi

if grep -q 'ttlSeconds' "${SCRIPT_DIR}/../src/pam_ezkey.c" \
  && grep -q 'timeoutSeconds' "${SCRIPT_DIR}/../src/pam_ezkey.c" \
  && grep -q '/cancel' "${SCRIPT_DIR}/../src/pam_ezkey.c" \
  && grep -q 'EZKEY_CANCEL_OK' "${SCRIPT_DIR}/../src/ezkey_config.h" \
  && grep -q 'EZKEY_CANCEL_FAIL' "${SCRIPT_DIR}/../src/ezkey_config.h" \
  && grep -q 'EZKEY_CANCEL_SKIPPED' "${SCRIPT_DIR}/../src/ezkey_config.h" \
  && grep -q 'EZKEY_CANCEL_FINAL' "${SCRIPT_DIR}/../src/ezkey_config.h" \
  && grep -q 'EZKEY_WAIT_CHUNK_SECS 5' "${SCRIPT_DIR}/../src/ezkey_config.h" \
  && grep -q 'EZKEY_INTERRUPT_CANCEL_MAX 5' "${SCRIPT_DIR}/../src/ezkey_config.h" \
  && grep -q 'EZKEY_AUTH_BUDGET_SECS 105' "${SCRIPT_DIR}/../src/ezkey_config.h" \
  && grep -q 'cancel=%s' "${SCRIPT_DIR}/../src/pam_ezkey.c" \
  && grep -q 'ezkey_sigterm_wait' "${SCRIPT_DIR}/../src/pam_ezkey.c" \
  && ! grep -E '\balarm\s*\(' "${SCRIPT_DIR}/../src/pam_ezkey.c" \
  && grep -q 'LoginGraceTime 120' "${SCRIPT_DIR}/../sshd_config" \
  && grep -q 'Match User testuser' "${SCRIPT_DIR}/../sshd_config" \
  && grep -q 'AuthenticationMethods keyboard-interactive' "${SCRIPT_DIR}/../sshd_config" \
  && grep -q '^wait_polling=2' "${SCRIPT_DIR}/../config/pam_ezkey.conf" \
  && grep -q '^wait_timeout=90' "${SCRIPT_DIR}/../config/pam_ezkey.conf"; then
  ok "#726 chunk=5 interrupt_cancel=5 budget=105 (110<120) + cancel/SIGTERM + Match AuthMethods; no alarm"
else
  fail "missing linkage guards (#726 thresholds/cancel/chunk/Match/alarm/sample conf)"
fi

echo "" | tee -a "${PROOF}"
echo "=== Results: $PASS passed, $FAIL failed ===" | tee -a "${PROOF}"
[ "${FAIL}" -eq 0 ]
