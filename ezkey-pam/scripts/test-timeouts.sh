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

# #941 / #982: prompts use PAM_PROMPT_ECHO_ON — auto-ok/Enter via PTY helper.
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
  echo "  Enter measure               pre-create EZKEY_MAX_ENTER_SECS 25; challenge=rem budget (#982 B1)"
  echo "  wait SIGTERM                absorbed only while ezkey_in_wait=1 (flag→cancel)"
  echo "  non-wait SIGTERM            SIGNAL emit + SIG_DFL + raise (prompts/create) #1011"
  echo "  EZKEY_DIAG_LINE_MAX 768     ezkey_diag.h (worst-case attempt line ~416)"
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
stop_stub
restore_conf

# --- #941 F1: Approve line visible BEFORE /wait (catches pam_info regression) ---
# Create succeeds, prompt blocks Enter, stub must not see GET /wait until Enter.
start_stub ok
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=30/' '${CONF}'; \
   sed -i 's/^api_timeout=.*/api_timeout=5/' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=false/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'"
reset_case_debug "941_before_wait"
SSH_LOG_941="${ARTIFACT_DIR}/pam-timeouts-941-before-wait.log"
SSH_CTRL_941="$(ezkey_mk_ctrl_fifo)"
SSH_CTRL_941_DIR="$(dirname "${SSH_CTRL_941}")"
: >"${SSH_LOG_941}"
set +e
python3 "${SCRIPT_DIR}/ssh-pty-capture.py" "${SSH_LOG_941}" "${SSH_CTRL_941}" \
  ssh -tt -p "${PORT}" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  -o PreferredAuthentications=keyboard-interactive -o PubkeyAuthentication=no \
  -o PasswordAuthentication=no -o ConnectTimeout=15 \
  "${SSH_USER}@127.0.0.1" 'echo PAM_941_OK; exit 0' \
  >/dev/null 2>&1 &
SSH_BG_941=$!
set -e
# Confirm-before-create: Approve/ok must be visible with **zero** stub creates.
BEFORE_CREATE_OK=0
for _ in $(seq 1 80); do
  CREATES_941="$(docker exec "${CONTAINER}" sh -c \
    "n=\$(grep 'POST .*/auth-attempts' '${STUB_REQ_LOG}' 2>/dev/null | grep -v cancel | wc -l)
     echo \"\${n:-0}\"" | tr -d ' \r' | head -n1)"
  CREATES_941="${CREATES_941:-0}"
  WAITS_941="$(docker exec "${CONTAINER}" sh -c \
    "n=\$(grep -c 'GET .*/wait' '${STUB_REQ_LOG}' 2>/dev/null || true)
     echo \"\${n:-0}\"" | tr -d ' \r' | head -n1)"
  WAITS_941="${WAITS_941:-0}"
  if [ "${CREATES_941}" -eq 0 ] && [ "${WAITS_941}" -eq 0 ] \
    && ezkey_ssh_capture_has_prewait_prompt "${SSH_LOG_941}"; then
    BEFORE_CREATE_OK=1
    break
  fi
  # Create already happened before Approve visible → confirm-before-create broken.
  if [ "${CREATES_941}" -ge 1 ] && ! ezkey_ssh_capture_has_prewait_prompt "${SSH_LOG_941}"; then
    break
  fi
  sleep 0.25
done
if [ "${BEFORE_CREATE_OK}" -eq 1 ]; then
  ok "#941/#975 pre-create Type ok visible BEFORE create (confirm-before-create)"
  {
    echo "=== #941 before-create capture excerpt ==="
    tr -cd '\11\12\15\40-\176' <"${SSH_LOG_941}" \
      | grep -E 'SSH login for user |Type ok|Approve on your phone' || true
  } | tee -a "${PROOF}"
else
  fail "#941/#975 pre-create prompt missing (creates=${CREATES_941:-?} waits=${WAITS_941:-?})"
  tr -cd '\11\12\15\40-\176' <"${SSH_LOG_941}" | tee -a "${PROOF}" || true
  docker exec "${CONTAINER}" cat "${STUB_REQ_LOG}" 2>/dev/null | tee -a "${PROOF}" || true
fi
printf 'ok\n' >"${SSH_CTRL_941}" 2>/dev/null || true
set +e
wait "${SSH_BG_941}"
set -e
rm -rf "${SSH_CTRL_941_DIR}"
FLAT_941="$(tr -cd '\11\12\15\40-\176' <"${SSH_LOG_941}" 2>/dev/null || true)"
# Patrick B2 (#982): ECHO_ON must actually echo the typed token in the PTY capture
# (Makefile string grep alone can pass on comments).
if echo "${FLAT_941}" | grep -qE 'to request device approval:[[:space:]]*ok'; then
  ok "#982 B2 PTY capture echoes typed ok (PAM_PROMPT_ECHO_ON)"
else
  fail "#982 B2 expected echoed 'ok' after pre-create prompt in PTY capture"
  echo "${FLAT_941}" | tee -a "${PROOF}" || true
fi
# After ok: exactly one create under Match MaxAuthTries 1.
CREATES_941_END="$(docker exec "${CONTAINER}" sh -c \
  "n=\$(grep 'POST .*/auth-attempts' '${STUB_REQ_LOG}' 2>/dev/null | grep -v cancel | wc -l)
   echo \"\${n:-0}\"" | tr -d ' \r' | head -n1)"
CREATES_941_END="${CREATES_941_END:-0}"
if [ "${CREATES_941_END}" -eq 1 ]; then
  ok "#975 exactly one create after pre-create ok (MaxAuthTries 1)"
else
  fail "#975 expected exactly 1 create after ok, got ${CREATES_941_END}"
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

wait_prewait_visible() {
  local log="$1"
  local i
  for i in $(seq 1 80); do
    if ezkey_ssh_capture_has_prewait_prompt "${log}"; then
      return 0
    fi
    sleep 0.25
  done
  return 1
}

SSH_LOG="${ARTIFACT_DIR}/pam-timeouts-delayed-enter.log"
SSH_CTRL="$(ezkey_mk_ctrl_fifo)"
SSH_CTRL_DIR="$(dirname "${SSH_CTRL}")"
run_chal_ssh "${SSH_LOG}" "${SSH_CTRL}"
# Confirm-before-create: ok #1 creates; challenge Enter continues to /wait (#982).
wait_prewait_visible "${SSH_LOG}" || true
printf 'ok\n' >"${SSH_CTRL}" 2>/dev/null || true
wait_chal_visible "${SSH_LOG}" || true
DELAY_SECS=8
echo "Delayed Enter ${DELAY_SECS}s after challenge visible (2nd prompt)" | tee -a "${PROOF}"
sleep "${DELAY_SECS}"
printf 'enter\n' >"${SSH_CTRL}" 2>/dev/null || true
set +e
wait "${SSH_BG}"
SSH_RC=$?
set -e
rm -rf "${SSH_CTRL_DIR}"

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

# --- #982 Patrick B1: challenge Enter @30s with request already approved → SUCCESS ---
# Formerly froze Enter>25s → UNAVAIL+cancel on the code prompt (race: phone already
# ACCEPTED, late Enter cancelled it). Challenge path keeps only rem-budget bound.
start_stub ok
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=90/' '${CONF}'; \
   sed -i 's/^api_timeout=.*/api_timeout=5/' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=true/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'; \
   : > /var/log/ezkey-pam/debug.err; : > '${STUB_REQ_LOG}'"
SSH_LOG="${ARTIFACT_DIR}/pam-timeouts-chal-enter-30-approved.log"
SSH_CTRL="$(ezkey_mk_ctrl_fifo)"
SSH_CTRL_DIR="$(dirname "${SSH_CTRL}")"
run_chal_ssh "${SSH_LOG}" "${SSH_CTRL}"
wait_prewait_visible "${SSH_LOG}" || true
printf 'ok\n' >"${SSH_CTRL}" 2>/dev/null || true
wait_chal_visible "${SSH_LOG}" || true
ENTER_LATE=30
echo "Challenge-prompt Enter after ${ENTER_LATE}s (stub already ACCEPTED; rem budget only)" \
  | tee -a "${PROOF}"
sleep "${ENTER_LATE}"
printf 'enter\n' >"${SSH_CTRL}" 2>/dev/null || true
set +e
wait "${SSH_BG}"
set -e
rm -rf "${SSH_CTRL_DIR}"
ATTEMPT="$(last_attempt_line)"
CANCEL_N="$(stub_saw_cancel)"
FLAT_B1="$(tr -cd '\11\12\15\40-\176' <"${SSH_LOG}" 2>/dev/null || true)"
echo "chal-enter@30 attempt=${ATTEMPT} cancel=${CANCEL_N}" | tee -a "${PROOF}"
# Ordering: create done before the 30s dwell; stub ok → ACCEPTED on first /wait.
# Proves late Enter after phone approve does not cancel (B1 + M2).
if echo "${ATTEMPT}" | grep -q 'result=SUCCESS' \
  && echo "${ATTEMPT}" | grep -q 'cancel=skipped' \
  && echo "${FLAT_B1}" | grep -q 'Approve on your phone, then press Enter' \
  && [ "${CANCEL_N}" -eq 0 ]; then
  ok "#982 B1 challenge Enter@30s + already-approved → SUCCESS (no 25s Enter cap; no cancel)"
else
  fail "#982 B1 Enter@30s expected SUCCESS cancel=skipped: ${ATTEMPT} cancel=${CANCEL_N}"
fi
stop_stub
restore_conf

# --- #982 Patrick B3: challenge Enter after >88 s → rem-budget refusal + cancel=ok ---
# Bound: rem <= CURL_WAIT_SLACK(15)+MIN_WAIT(2)=17 ⇒ elapsed >= 88 from auth start.
# Create stays PENDING (no /wait); cancel succeeds → cancel=ok. If the device had
# already ACCEPTED, cancel would be HTTP 409 → cancel=final; login still refused
# (documented in STATUS / INTERACTION-MODEL — stub cannot race ACCEPTED without /wait).
start_stub ok
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=90/' '${CONF}'; \
   sed -i 's/^api_timeout=.*/api_timeout=5/' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=true/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'; \
   : > /var/log/ezkey-pam/debug.err; : > '${STUB_REQ_LOG}'"
SSH_LOG="${ARTIFACT_DIR}/pam-timeouts-chal-enter-over-88.log"
SSH_CTRL="$(ezkey_mk_ctrl_fifo)"
SSH_CTRL_DIR="$(dirname "${SSH_CTRL}")"
run_chal_ssh "${SSH_LOG}" "${SSH_CTRL}"
wait_prewait_visible "${SSH_LOG}" || true
printf 'ok\n' >"${SSH_CTRL}" 2>/dev/null || true
wait_chal_visible "${SSH_LOG}" || true
ENTER_OVER_BUDGET=89
echo "Challenge-prompt Enter after ${ENTER_OVER_BUDGET}s (>88s rem-budget bound)" \
  | tee -a "${PROOF}"
sleep "${ENTER_OVER_BUDGET}"
printf 'enter\n' >"${SSH_CTRL}" 2>/dev/null || true
set +e
wait "${SSH_BG}"
set -e
rm -rf "${SSH_CTRL_DIR}"
ATTEMPT="$(last_attempt_line)"
CANCEL_N="$(stub_saw_cancel)"
ERR="$(docker exec "${CONTAINER}" sh -c 'tail -n 8 /var/log/ezkey-pam/debug.err' || true)"
echo "chal-enter>88 attempt=${ATTEMPT} cancel=${CANCEL_N}" | tee -a "${PROOF}"
echo "chal-enter>88 err=${ERR}" | tee -a "${PROOF}"
# debug.err sanitizes spaces → underscores (same as other Enter-bound cases).
if echo "${ATTEMPT}" | grep -q 'result=UNAVAIL' \
  && echo "${ATTEMPT}" | grep -q 'cancel=ok' \
  && echo "${ERR}" | grep -Eqi 'auth_budget_exhausted_after_challenge|budget_exhausted_after_challenge' \
  && [ "${CANCEL_N}" -ge 1 ]; then
  ok "#982 B3 challenge Enter>88s → UNAVAIL + cancel=ok (rem-budget bound)"
else
  fail "#982 B3 Enter>88s expected UNAVAIL cancel=ok: ${ATTEMPT} cancel=${CANCEL_N} / ${ERR}"
fi
stop_stub
restore_conf

# --- M1 (#956): delayed Enter on default path (no-challenge) ---
start_stub ok
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=90/' '${CONF}'; \
   sed -i 's/^api_timeout=.*/api_timeout=5/' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=false/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'"
reset_case_debug "delayed_enter_nochal"
SSH_LOG="${ARTIFACT_DIR}/pam-timeouts-delayed-enter-nochal.log"
SSH_CTRL="$(ezkey_mk_ctrl_fifo)"
SSH_CTRL_DIR="$(dirname "${SSH_CTRL}")"
run_chal_ssh "${SSH_LOG}" "${SSH_CTRL}"
wait_prewait_visible "${SSH_LOG}" || true
DELAY_SECS=8
echo "No-challenge delayed Enter ${DELAY_SECS}s after waiting prompt" | tee -a "${PROOF}"
sleep "${DELAY_SECS}"
printf 'ok\n' >"${SSH_CTRL}" 2>/dev/null || true
set +e
wait "${SSH_BG}"
SSH_RC=$?
set -e
rm -rf "${SSH_CTRL_DIR}"
ATTEMPT="$(docker exec "${CONTAINER}" sh -c \
  "grep 'pam_ezkey: attempt ' /var/log/secure | tail -n 1" || true)"
GOT_TO="$(stub_wait_timeout_param)"
GOT_TTL="$(stub_create_ttl)"
OUT="$(docker exec "${CONTAINER}" sh -c 'cat /var/log/ezkey-pam/debug.out' || true)"
WAIT_LEFT="$(echo "${OUT}" | grep -oE 'wait_left_[0-9]+' | tail -n1 | cut -d_ -f3 || true)"
echo "nochal-delayed-enter chunk=${GOT_TO} wait_left=${WAIT_LEFT} sent_ttl=${GOT_TTL} ssh_rc=${SSH_RC} attempt=${ATTEMPT}" \
  | tee -a "${PROOF}"
# B1: TTL is computed after ok, so ~8s typing shrinks both wait_left and sent ttl.
if echo "${ATTEMPT}" | grep -Eq 'result=(SUCCESS|UNAVAIL|DENIED|TIMEOUT|EXPIRED)' \
  && [ -n "${WAIT_LEFT}" ] && [ "${WAIT_LEFT}" -lt 90 ] && [ "${WAIT_LEFT}" -ge 70 ] \
  && [ -n "${GOT_TTL}" ] && [ "${GOT_TTL}" -le 85 ] && [ "${GOT_TTL}" -ge 70 ] \
  && [ -n "${GOT_TO}" ] && [ "${GOT_TO}" -le 5 ]; then
  ok "no-challenge delayed Enter ${DELAY_SECS}s → wait_left=${WAIT_LEFT} sent_ttl=${GOT_TTL} (both shrunk) + chunk≤5"
else
  fail "no-challenge delayed Enter expected wait_left/ttl in [70,85] + chunk≤5, got wait_left='${WAIT_LEFT}' ttl='${GOT_TTL}' chunk='${GOT_TO}' ${ATTEMPT}"
fi
stop_stub
restore_conf

# --- M1 (#956): Enter >25s on pre-create confirm (no-challenge) ---
# Confirm-before-create: slow ok never reaches POST → zero creates, no cancel.
start_stub ok
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=90/' '${CONF}'; \
   sed -i 's/^api_timeout=.*/api_timeout=5/' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=false/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'; \
   : > /var/log/ezkey-pam/debug.err; : > '${STUB_REQ_LOG}'"
SSH_LOG="${ARTIFACT_DIR}/pam-timeouts-enter-over-25-nochal.log"
SSH_CTRL="$(ezkey_mk_ctrl_fifo)"
SSH_CTRL_DIR="$(dirname "${SSH_CTRL}")"
before="$(docker exec "${CONTAINER}" sh -c \
  "grep -c 'pam_ezkey: attempt ' /var/log/secure 2>/dev/null || true" | tr -d ' \r')"
before="${before:-0}"
run_chal_ssh "${SSH_LOG}" "${SSH_CTRL}"
wait_prewait_visible "${SSH_LOG}" || true
ENTER_OVER=26
echo "No-challenge pre-create Enter after ${ENTER_OVER}s (> EZKEY_MAX_ENTER_SECS 25)" \
  | tee -a "${PROOF}"
sleep "${ENTER_OVER}"
printf 'ok\n' >"${SSH_CTRL}" 2>/dev/null || true
set +e
wait "${SSH_BG}"
set -e
rm -rf "${SSH_CTRL_DIR}"
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
CREATES_SLOW="$(docker exec "${CONTAINER}" sh -c \
  "n=\$(grep 'POST .*/auth-attempts' '${STUB_REQ_LOG}' 2>/dev/null | grep -v cancel | wc -l)
   echo \"\${n:-0}\"" | tr -d ' \r' | head -n1)"
CREATES_SLOW="${CREATES_SLOW:-0}"
echo "nochal-enter>25 attempt=${ATTEMPT} cancel=${CANCEL_N} creates=${CREATES_SLOW}" \
  | tee -a "${PROOF}"
echo "nochal-enter>25 err=${ERR}" | tee -a "${PROOF}"
if echo "${ATTEMPT}" | grep -q 'result=UNAVAIL' \
  && echo "${ERR}" | grep -Eqi 'exceeded_max_duration|Enter_exceeded|pre-create confirm' \
  && [ "${CREATES_SLOW}" -eq 0 ] && [ "${CANCEL_N}" -eq 0 ]; then
  ok "no-challenge Enter >25s → UNAVAIL + zero creates (confirm-before-create)"
else
  fail "no-challenge Enter >25s expected UNAVAIL+0 creates: ${ATTEMPT} cancel=${CANCEL_N} creates=${CREATES_SLOW} / ${ERR}"
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

# --- #975: enrollment-required challenge with challenge_requested=false ---
# Stub forces authAttemptChallenge even though create body says challengeRequested=false.
# Regression: pre-2.2.12 dropped the code and showed the waiting line instead.
start_stub enrollment_challenge
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=30/' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=false/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'"
SSH_LOG_975="${ARTIFACT_DIR}/pam-timeouts-975-enrollment-chal.log"
SSH_CTRL_975="$(ezkey_mk_ctrl_fifo)"
SSH_CTRL_975_DIR="$(dirname "${SSH_CTRL_975}")"
: >"${SSH_LOG_975}"
set +e
python3 "${SCRIPT_DIR}/ssh-pty-capture.py" "${SSH_LOG_975}" "${SSH_CTRL_975}" \
  ssh -tt -p "${PORT}" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  -o PreferredAuthentications=keyboard-interactive -o PubkeyAuthentication=no \
  -o PasswordAuthentication=no -o ConnectTimeout=15 \
  "${SSH_USER}@127.0.0.1" 'echo PAM_975_OK; exit 0' \
  >/dev/null 2>&1 &
SSH_BG_975=$!
set -e
# Pre-create ok → create → enrollment challenge is the second prompt.
for _ in $(seq 1 80); do
  if ezkey_ssh_capture_has_prewait_prompt "${SSH_LOG_975}"; then
    break
  fi
  sleep 0.25
done
printf 'ok\n' >"${SSH_CTRL_975}" 2>/dev/null || true
CHAL_975_OK=0
for _ in $(seq 1 80); do
  if tr -cd '\11\12\15\40-\176' <"${SSH_LOG_975}" 2>/dev/null \
    | grep -qE 'Ezkey challenge code: 42'; then
    CHAL_975_OK=1
    break
  fi
  sleep 0.25
done
if [ "${CHAL_975_OK}" -eq 1 ]; then
  ok "#975 enrollment challenge visible with challenge_requested=false"
  {
    echo "=== #975 capture excerpt ==="
    tr -cd '\11\12\15\40-\176' <"${SSH_LOG_975}" \
      | grep -E 'Ezkey challenge|Approve on your phone|Type ok' || true
  } | tee -a "${PROOF}"
else
  fail "#975 expected Ezkey challenge code: 42 with challenge_requested=false"
  tr -cd '\11\12\15\40-\176' <"${SSH_LOG_975}" | tee -a "${PROOF}" || true
fi
printf 'enter\n' >"${SSH_CTRL_975}" 2>/dev/null || true
set +e
wait "${SSH_BG_975}"
set -e
rm -rf "${SSH_CTRL_975_DIR}"
ATTEMPT_975="$(docker exec "${CONTAINER}" sh -c \
  "grep 'pam_ezkey: attempt ' /var/log/secure | tail -n 1" || true)"
echo "975_attempt=${ATTEMPT_975}" | tee -a "${PROOF}"
# Challenge digits / challenge fields must not land in authpriv (#750).
# Avoid matching pam_code= (false positive on older 'code=' greps).
if echo "${ATTEMPT_975}" | grep -q 'result=SUCCESS' \
  && ! echo "${ATTEMPT_975}" | grep -qi 'challenge'; then
  ok "#975 SUCCESS + challenge digits absent from attempt line (#750)"
else
  fail "#975 expected SUCCESS without challenge digits in authpriv: ${ATTEMPT_975}"
fi
stop_stub
restore_conf

# --- #975 D4: ssh -T </dev/null → CONFIRM + **zero** stub creates (#1011) ---
# Confirm-before-create: empty EOF never reaches POST /auth-attempts.
start_stub ok
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=30/' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=false/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'; \
   : > /var/log/ezkey-pam/debug.err; : > '${STUB_REQ_LOG}'"
before_d4="$(docker exec "${CONTAINER}" sh -c \
  "grep -c 'pam_ezkey: attempt ' /var/log/secure 2>/dev/null || true" | tr -d ' \r')"
before_d4="${before_d4:-0}"
set +e
ssh -T -p "${PORT}" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  -o PreferredAuthentications=keyboard-interactive -o PubkeyAuthentication=no \
  -o PasswordAuthentication=no -o ConnectTimeout=15 -o NumberOfPasswordPrompts=1 \
  "${SSH_USER}@127.0.0.1" 'echo PAM_D4_SHOULD_FAIL; exit 0' </dev/null \
  >"${ARTIFACT_DIR}/pam-timeouts-d4-notty.log" 2>&1
d4_rc=$?
set -e
for _ in $(seq 1 40); do
  after_d4="$(docker exec "${CONTAINER}" sh -c \
    "grep -c 'pam_ezkey: attempt ' /var/log/secure 2>/dev/null || true" | tr -d ' \r')"
  after_d4="${after_d4:-0}"
  [ "$((after_d4 - before_d4))" -ge 1 ] && break
  sleep 0.1
done
ATTEMPT_D4="$(docker exec "${CONTAINER}" sh -c \
  "grep 'pam_ezkey: attempt ' /var/log/secure | tail -n 1" || true)"
ERR_D4="$(docker exec "${CONTAINER}" sh -c \
  'tail -n 8 /var/log/ezkey-pam/debug.err' || true)"
CREATES_D4="$(docker exec "${CONTAINER}" sh -c \
  "n=\$(grep 'POST .*/auth-attempts' '${STUB_REQ_LOG}' 2>/dev/null | grep -v cancel | wc -l)
   echo \"\${n:-0}\"" | tr -d ' \r' | head -n1)"
CREATES_D4="${CREATES_D4:-0}"
echo "d4_rc=${d4_rc} creates=${CREATES_D4} attempt=${ATTEMPT_D4}" | tee -a "${PROOF}"
echo "d4_err=${ERR_D4}" | tee -a "${PROOF}"
if [ "${d4_rc}" -ne 0 ] \
  && [ "${CREATES_D4}" -eq 0 ] \
  && echo "${ATTEMPT_D4}" | grep -q 'result=CONFIRM' \
  && echo "${ATTEMPT_D4}" | grep -q 'reason=empty_confirm' \
  && echo "${ATTEMPT_D4}" | grep -q 'stage=confirm' \
  && echo "${ERR_D4}" | grep -Eq 'pre-wait confirm missing|empty/EOF|not ok'; then
  ok "#975 D4 ssh -T </dev/null → CONFIRM + zero creates (#1011)"
else
  fail "#975 D4 expected CONFIRM+0 creates (rc=${d4_rc} creates=${CREATES_D4}): ${ATTEMPT_D4}"
fi
# Bare Enter on a PTY (empty) → CONFIRM + zero creates.
docker exec "${CONTAINER}" sh -c ": >'${STUB_REQ_LOG}'"
SSH_LOG_D4E="${ARTIFACT_DIR}/pam-timeouts-d4-bare-enter.log"
SSH_CTRL_D4E="$(ezkey_mk_ctrl_fifo)"
SSH_CTRL_D4E_DIR="$(dirname "${SSH_CTRL_D4E}")"
: >"${SSH_LOG_D4E}"
set +e
python3 "${SCRIPT_DIR}/ssh-pty-capture.py" "${SSH_LOG_D4E}" "${SSH_CTRL_D4E}" \
  ssh -tt -p "${PORT}" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  -o PreferredAuthentications=keyboard-interactive -o PubkeyAuthentication=no \
  -o PasswordAuthentication=no -o ConnectTimeout=15 \
  "${SSH_USER}@127.0.0.1" 'echo PAM_D4_ENTER_SHOULD_FAIL; exit 0' \
  >/dev/null 2>&1 &
SSH_BG_D4E=$!
set -e
for _ in $(seq 1 80); do
  if ezkey_ssh_capture_has_prewait_prompt "${SSH_LOG_D4E}"; then
    break
  fi
  sleep 0.25
done
printf 'enter\n' >"${SSH_CTRL_D4E}" 2>/dev/null || true
set +e
wait "${SSH_BG_D4E}"
d4e_rc=$?
set -e
rm -rf "${SSH_CTRL_D4E_DIR}"
ATTEMPT_D4E="$(docker exec "${CONTAINER}" sh -c \
  "grep 'pam_ezkey: attempt ' /var/log/secure | tail -n 1" || true)"
CREATES_D4E="$(docker exec "${CONTAINER}" sh -c \
  "n=\$(grep 'POST .*/auth-attempts' '${STUB_REQ_LOG}' 2>/dev/null | grep -v cancel | wc -l)
   echo \"\${n:-0}\"" | tr -d ' \r' | head -n1)"
CREATES_D4E="${CREATES_D4E:-0}"
echo "d4_bare_enter_rc=${d4e_rc} creates=${CREATES_D4E} attempt=${ATTEMPT_D4E}" \
  | tee -a "${PROOF}"
if [ "${d4e_rc}" -ne 0 ] && [ "${CREATES_D4E}" -eq 0 ] \
  && echo "${ATTEMPT_D4E}" | grep -q 'result=CONFIRM' \
  && echo "${ATTEMPT_D4E}" | grep -q 'reason=empty_confirm'; then
  ok "#975 D4 bare Enter → CONFIRM + zero creates (#1011)"
else
  fail "#975 D4 bare Enter expected CONFIRM+0 creates (rc=${d4e_rc} creates=${CREATES_D4E}): ${ATTEMPT_D4E}"
fi
stop_stub
restore_conf

# --- Patrick B1: ok at 20s → create ttlSeconds from remaining budget (≤70) ---
# AUTH_BUDGET 105 − 20 − CURL_SLACK 15 → planned ≤ 70 when wait_timeout=90.
start_stub ok
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=90/' '${CONF}'; \
   sed -i 's/^api_timeout=.*/api_timeout=5/' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=false/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'; \
   : > '${STUB_REQ_LOG}'"
reset_case_debug "b1_ttl_after_ok"
SSH_LOG_B1="${ARTIFACT_DIR}/pam-timeouts-b1-ttl-after-ok.log"
SSH_CTRL_B1="$(ezkey_mk_ctrl_fifo)"
SSH_CTRL_B1_DIR="$(dirname "${SSH_CTRL_B1}")"
run_chal_ssh "${SSH_LOG_B1}" "${SSH_CTRL_B1}"
wait_prewait_visible "${SSH_LOG_B1}" || true
DELAY_B1=20
echo "B1: pre-create ok after ${DELAY_B1}s → expect sent ttlSeconds ≤70" | tee -a "${PROOF}"
sleep "${DELAY_B1}"
printf 'ok\n' >"${SSH_CTRL_B1}" 2>/dev/null || true
set +e
wait "${SSH_BG}"
set -e
rm -rf "${SSH_CTRL_B1_DIR}"
GOT_TTL_B1="$(stub_create_ttl)"
ATTEMPT_B1="$(last_attempt_line)"
echo "b1_sent_ttl=${GOT_TTL_B1} attempt=${ATTEMPT_B1}" | tee -a "${PROOF}"
if [ -n "${GOT_TTL_B1}" ] && [ "${GOT_TTL_B1}" -le 70 ] && [ "${GOT_TTL_B1}" -ge 55 ] \
  && echo "${ATTEMPT_B1}" | grep -Eq 'result=(SUCCESS|UNAVAIL|DENIED|TIMEOUT|EXPIRED)'; then
  ok "B1 ok@${DELAY_B1}s → sent ttlSeconds=${GOT_TTL_B1} (≤70, from rem after confirm)"
else
  fail "B1 expected sent ttlSeconds in [55,70], got '${GOT_TTL_B1}' ${ATTEMPT_B1}"
fi
stop_stub
restore_conf

# --- M1 S1-H: host challenge_requested=true → code visible + SUCCESS ---
start_stub ok
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=30/' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=true/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'"
SSH_LOG_S1H="${ARTIFACT_DIR}/pam-timeouts-s1h-host-chal.log"
SSH_CTRL_S1H="$(ezkey_mk_ctrl_fifo)"
SSH_CTRL_S1H_DIR="$(dirname "${SSH_CTRL_S1H}")"
: >"${SSH_LOG_S1H}"
set +e
python3 "${SCRIPT_DIR}/ssh-pty-capture.py" "${SSH_LOG_S1H}" "${SSH_CTRL_S1H}" \
  ssh -tt -p "${PORT}" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  -o PreferredAuthentications=keyboard-interactive -o PubkeyAuthentication=no \
  -o PasswordAuthentication=no -o ConnectTimeout=15 \
  "${SSH_USER}@127.0.0.1" 'echo PAM_S1H_OK; exit 0' \
  >/dev/null 2>&1 &
SSH_BG_S1H=$!
set -e
wait_prewait_visible "${SSH_LOG_S1H}" || true
printf 'ok\n' >"${SSH_CTRL_S1H}" 2>/dev/null || true
S1H_CHAL=0
for _ in $(seq 1 80); do
  if tr -cd '\11\12\15\40-\176' <"${SSH_LOG_S1H}" 2>/dev/null \
    | grep -qE 'Ezkey challenge code:'; then
    S1H_CHAL=1
    break
  fi
  sleep 0.25
done
# #982: bare Enter on challenge prompt (no second Type ok).
printf 'enter\n' >"${SSH_CTRL_S1H}" 2>/dev/null || true
set +e
wait "${SSH_BG_S1H}"
set -e
rm -rf "${SSH_CTRL_S1H_DIR}"
ATTEMPT_S1H="$(last_attempt_line)"
FLAT_S1H="$(tr -cd '\11\12\15\40-\176' <"${SSH_LOG_S1H}" 2>/dev/null || true)"
echo "s1h_attempt=${ATTEMPT_S1H}" | tee -a "${PROOF}"
if [ "${S1H_CHAL}" -eq 1 ] \
  && echo "${FLAT_S1H}" | grep -q PAM_S1H_OK \
  && echo "${FLAT_S1H}" | grep -q 'Approve on your phone, then press Enter' \
  && ! echo "${FLAT_S1H}" | grep -q 'Type ok and press Enter to wait' \
  && echo "${ATTEMPT_S1H}" | grep -q 'result=SUCCESS' \
  && ! echo "${ATTEMPT_S1H}" | grep -qi 'challenge'; then
  ok "S1-H host challenge → code visible + Enter → SUCCESS (no 2nd Type ok)"
else
  fail "S1-H expected code+Enter+SUCCESS (chal=${S1H_CHAL}): ${ATTEMPT_S1H}"
fi
stop_stub
restore_conf

# --- M2 S3: batch + enrollment challenge → 0 creates (confirm-before-create) ---
start_stub enrollment_challenge
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=30/' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=false/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'; \
   : > '${STUB_REQ_LOG}'"
set +e
ssh -T -p "${PORT}" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  -o PreferredAuthentications=keyboard-interactive -o PubkeyAuthentication=no \
  -o PasswordAuthentication=no -o ConnectTimeout=15 -o NumberOfPasswordPrompts=1 \
  "${SSH_USER}@127.0.0.1" 'echo PAM_S3_SHOULD_FAIL; exit 0' </dev/null \
  >"${ARTIFACT_DIR}/pam-timeouts-s3-batch-enrollment.log" 2>&1
s3_rc=$?
set -e
CREATES_S3="$(docker exec "${CONTAINER}" sh -c \
  "n=\$(grep 'POST .*/auth-attempts' '${STUB_REQ_LOG}' 2>/dev/null | grep -v cancel | wc -l)
   echo \"\${n:-0}\"" | tr -d ' \r' | head -n1)"
CREATES_S3="${CREATES_S3:-0}"
ATTEMPT_S3="$(last_attempt_line)"
echo "s3_rc=${s3_rc} creates=${CREATES_S3} attempt=${ATTEMPT_S3}" | tee -a "${PROOF}"
if [ "${s3_rc}" -ne 0 ] && [ "${CREATES_S3}" -eq 0 ] \
  && echo "${ATTEMPT_S3}" | grep -q 'result=CONFIRM' \
  && echo "${ATTEMPT_S3}" | grep -q 'reason=empty_confirm'; then
  ok "S3 batch + enrollment challenge → CONFIRM + zero creates (#1011)"
else
  fail "S3 expected CONFIRM+0 creates (rc=${s3_rc} creates=${CREATES_S3}): ${ATTEMPT_S3}"
fi
stop_stub
restore_conf

# --- M3: Ctrl-C (SIGINT) at pre-create prompt → 0 creates ---
start_stub ok
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=30/' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=false/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'; \
   : > '${STUB_REQ_LOG}'"
SSH_LOG_INTR="${ARTIFACT_DIR}/pam-timeouts-ctrlc-precreate.log"
SSH_CTRL_INTR="$(ezkey_mk_ctrl_fifo)"
SSH_CTRL_INTR_DIR="$(dirname "${SSH_CTRL_INTR}")"
: >"${SSH_LOG_INTR}"
set +e
python3 "${SCRIPT_DIR}/ssh-pty-capture.py" "${SSH_LOG_INTR}" "${SSH_CTRL_INTR}" \
  ssh -tt -p "${PORT}" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  -o PreferredAuthentications=keyboard-interactive -o PubkeyAuthentication=no \
  -o PasswordAuthentication=no -o ConnectTimeout=15 \
  "${SSH_USER}@127.0.0.1" 'echo PAM_INTR_SHOULD_FAIL; exit 0' \
  >/dev/null 2>&1 &
SSH_BG_INTR=$!
set -e
for _ in $(seq 1 80); do
  if ezkey_ssh_capture_has_prewait_prompt "${SSH_LOG_INTR}"; then
    break
  fi
  sleep 0.25
done
printf 'intr\n' >"${SSH_CTRL_INTR}" 2>/dev/null || true
set +e
wait "${SSH_BG_INTR}"
intr_rc=$?
set -e
rm -rf "${SSH_CTRL_INTR_DIR}"
CREATES_INTR="$(docker exec "${CONTAINER}" sh -c \
  "n=\$(grep 'POST .*/auth-attempts' '${STUB_REQ_LOG}' 2>/dev/null | grep -v cancel | wc -l)
   echo \"\${n:-0}\"" | tr -d ' \r' | head -n1)"
CREATES_INTR="${CREATES_INTR:-0}"
echo "ctrlc_rc=${intr_rc} creates=${CREATES_INTR}" | tee -a "${PROOF}"
if [ "${CREATES_INTR}" -eq 0 ]; then
  ok "M3 Ctrl-C at pre-create prompt → zero creates"
else
  fail "M3 Ctrl-C expected 0 creates, got ${CREATES_INTR}"
fi
stop_stub
restore_conf

# =============================================================================
# #982 tiers 0–1: challenge single-wait + ECHO_ON confirm
# =============================================================================

# --- #982: challenge Enter → SUCCESS (smoke; stub ok ACCEPTs on first /wait) ---
# M2: this stub cannot distinguish phone-before vs phone-after Enter (always
# ACCEPTED). Ordering / approve-vs-cancel is proven by B1 Enter@30s above.
start_stub ok
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=30/' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=true/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'; \
   : > '${STUB_REQ_LOG}'"
SSH_LOG_982A="${ARTIFACT_DIR}/pam-982-challenge-enter-success.log"
SSH_CTRL_982A="$(ezkey_mk_ctrl_fifo)"
SSH_CTRL_982A_DIR="$(dirname "${SSH_CTRL_982A}")"
run_chal_ssh "${SSH_LOG_982A}" "${SSH_CTRL_982A}"
wait_prewait_visible "${SSH_LOG_982A}" || true
printf 'ok\n' >"${SSH_CTRL_982A}" 2>/dev/null || true
wait_chal_visible "${SSH_LOG_982A}" || true
printf 'enter\n' >"${SSH_CTRL_982A}" 2>/dev/null || true
set +e
wait "${SSH_BG}"
set -e
rm -rf "${SSH_CTRL_982A_DIR}"
ATTEMPT_982A="$(last_attempt_line)"
FLAT_982A="$(tr -cd '\11\12\15\40-\176' <"${SSH_LOG_982A}" 2>/dev/null || true)"
echo "982_chal_enter_smoke attempt=${ATTEMPT_982A}" | tee -a "${PROOF}"
if echo "${FLAT_982A}" | grep -q 'Approve on your phone, then press Enter' \
  && ! echo "${FLAT_982A}" | grep -q 'Type ok and press Enter to wait' \
  && echo "${FLAT_982A}" | grep -q PAM_TMO_OK \
  && echo "${ATTEMPT_982A}" | grep -q 'result=SUCCESS'; then
  ok "#982 challenge Enter → SUCCESS (smoke; order covered by B1 Enter@30s)"
else
  fail "#982 challenge Enter smoke expected SUCCESS: ${ATTEMPT_982A}"
fi
stop_stub
restore_conf

# --- #982: approval after Enter (stub delayed_accept) ---
start_stub delayed_accept
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=30/' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=true/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'"
SSH_LOG_982B="${ARTIFACT_DIR}/pam-982-approve-after-enter.log"
SSH_CTRL_982B="$(ezkey_mk_ctrl_fifo)"
SSH_CTRL_982B_DIR="$(dirname "${SSH_CTRL_982B}")"
run_chal_ssh "${SSH_LOG_982B}" "${SSH_CTRL_982B}"
wait_prewait_visible "${SSH_LOG_982B}" || true
printf 'ok\n' >"${SSH_CTRL_982B}" 2>/dev/null || true
wait_chal_visible "${SSH_LOG_982B}" || true
printf 'enter\n' >"${SSH_CTRL_982B}" 2>/dev/null || true
set +e
wait "${SSH_BG}"
set -e
rm -rf "${SSH_CTRL_982B_DIR}"
ATTEMPT_982B="$(last_attempt_line)"
LAT_982B="$(echo "${ATTEMPT_982B}" | grep -oE 'latency_ms=[0-9]+' | cut -d= -f2 || true)"
echo "982_after_enter attempt=${ATTEMPT_982B}" | tee -a "${PROOF}"
# ACCEPT_DELAY default 3s → latency should reflect poll wait (≥2s, ≪ wait_timeout).
if echo "${ATTEMPT_982B}" | grep -q 'result=SUCCESS' \
  && [ -n "${LAT_982B}" ] && [ "${LAT_982B}" -ge 2000 ] && [ "${LAT_982B}" -lt 20000 ]; then
  ok "#982 approval-after-Enter → SUCCESS (latency_ms=${LAT_982B} reflects delayed_accept)"
else
  fail "#982 approval-after-Enter expected SUCCESS with delay: ${ATTEMPT_982B}"
fi
stop_stub
restore_conf

# --- #982: deny while polling after challenge Enter ---
start_stub rejected
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=30/' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=true/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'"
SSH_LOG_982D="${ARTIFACT_DIR}/pam-982-deny.log"
SSH_CTRL_982D="$(ezkey_mk_ctrl_fifo)"
SSH_CTRL_982D_DIR="$(dirname "${SSH_CTRL_982D}")"
run_chal_ssh "${SSH_LOG_982D}" "${SSH_CTRL_982D}"
wait_prewait_visible "${SSH_LOG_982D}" || true
printf 'ok\n' >"${SSH_CTRL_982D}" 2>/dev/null || true
wait_chal_visible "${SSH_LOG_982D}" || true
printf 'enter\n' >"${SSH_CTRL_982D}" 2>/dev/null || true
set +e
wait "${SSH_BG}"
set -e
rm -rf "${SSH_CTRL_982D_DIR}"
ATTEMPT_982D="$(last_attempt_line)"
echo "982_deny attempt=${ATTEMPT_982D}" | tee -a "${PROOF}"
if echo "${ATTEMPT_982D}" | grep -q 'result=DENIED' \
  && echo "${ATTEMPT_982D}" | grep -q 'cancel=ok'; then
  ok "#982 deny after challenge Enter → DENIED + cancel=ok"
else
  fail "#982 deny expected DENIED cancel=ok: ${ATTEMPT_982D}"
fi
stop_stub
restore_conf

# --- #982 M4: PAM-side wait TIMEOUT while polling after challenge Enter ---
# stub pending never ACCEPTs; PAM exhausts effective_wait → TIMEOUT (not server EXPIRED).
start_stub pending
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=15/' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=true/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'"
SSH_LOG_982T="${ARTIFACT_DIR}/pam-982-pam-timeout-poll.log"
SSH_CTRL_982T="$(ezkey_mk_ctrl_fifo)"
SSH_CTRL_982T_DIR="$(dirname "${SSH_CTRL_982T}")"
run_chal_ssh "${SSH_LOG_982T}" "${SSH_CTRL_982T}"
wait_prewait_visible "${SSH_LOG_982T}" || true
printf 'ok\n' >"${SSH_CTRL_982T}" 2>/dev/null || true
wait_chal_visible "${SSH_LOG_982T}" || true
printf 'enter\n' >"${SSH_CTRL_982T}" 2>/dev/null || true
set +e
wait "${SSH_BG}"
set -e
rm -rf "${SSH_CTRL_982T_DIR}"
ATTEMPT_982T="$(last_attempt_line)"
echo "982_pam_timeout attempt=${ATTEMPT_982T}" | tee -a "${PROOF}"
if echo "${ATTEMPT_982T}" | grep -q 'result=TIMEOUT' \
  && echo "${ATTEMPT_982T}" | grep -q 'cancel=ok'; then
  ok "#982 PAM-side wait TIMEOUT after challenge Enter → TIMEOUT + cancel=ok"
else
  fail "#982 PAM TIMEOUT poll expected TIMEOUT cancel=ok: ${ATTEMPT_982T}"
fi
stop_stub
restore_conf

# --- #982 M4: server EXPIRED while polling after challenge Enter ---
start_stub expired
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=30/' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=true/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'"
SSH_LOG_982E="${ARTIFACT_DIR}/pam-982-server-expired-poll.log"
SSH_CTRL_982E="$(ezkey_mk_ctrl_fifo)"
SSH_CTRL_982E_DIR="$(dirname "${SSH_CTRL_982E}")"
run_chal_ssh "${SSH_LOG_982E}" "${SSH_CTRL_982E}"
wait_prewait_visible "${SSH_LOG_982E}" || true
printf 'ok\n' >"${SSH_CTRL_982E}" 2>/dev/null || true
wait_chal_visible "${SSH_LOG_982E}" || true
printf 'enter\n' >"${SSH_CTRL_982E}" 2>/dev/null || true
set +e
wait "${SSH_BG}"
set -e
rm -rf "${SSH_CTRL_982E_DIR}"
ATTEMPT_982E="$(last_attempt_line)"
echo "982_server_expired attempt=${ATTEMPT_982E}" | tee -a "${PROOF}"
if echo "${ATTEMPT_982E}" | grep -q 'result=EXPIRED' \
  && echo "${ATTEMPT_982E}" | grep -q 'cancel=ok'; then
  ok "#982 server EXPIRED after challenge Enter → EXPIRED + cancel=ok"
else
  fail "#982 server EXPIRED expected EXPIRED cancel=ok: ${ATTEMPT_982E}"
fi
stop_stub
restore_conf

# --- #982: batch empty reply still cancels with typed error (0 creates) ---
start_stub ok
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=30/' '${CONF}'; \
   sed -i 's/^challenge_requested=.*/challenge_requested=true/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'; \
   : > '${STUB_REQ_LOG}'; \
   : > /var/log/ezkey-pam/debug.err"
set +e
ssh -T -p "${PORT}" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  -o PreferredAuthentications=keyboard-interactive -o PubkeyAuthentication=no \
  -o PasswordAuthentication=no -o ConnectTimeout=15 -o NumberOfPasswordPrompts=1 \
  "${SSH_USER}@127.0.0.1" 'echo PAM_982_BATCH_SHOULD_FAIL; exit 0' </dev/null \
  >"${ARTIFACT_DIR}/pam-982-batch-cancel.log" 2>&1
batch_982_rc=$?
set -e
CREATES_982B="$(docker exec "${CONTAINER}" sh -c \
  "n=\$(grep 'POST .*/auth-attempts' '${STUB_REQ_LOG}' 2>/dev/null | grep -v cancel | wc -l)
   echo \"\${n:-0}\"" | tr -d ' \r' | head -n1)"
CREATES_982B="${CREATES_982B:-0}"
ATTEMPT_982BATCH="$(last_attempt_line)"
ERR_982B="$(docker exec "${CONTAINER}" sh -c 'tail -n 5 /var/log/ezkey-pam/debug.err' || true)"
echo "982_batch rc=${batch_982_rc} creates=${CREATES_982B} attempt=${ATTEMPT_982BATCH}" \
  | tee -a "${PROOF}"
if [ "${batch_982_rc}" -ne 0 ] && [ "${CREATES_982B}" -eq 0 ] \
  && echo "${ATTEMPT_982BATCH}" | grep -q 'result=CONFIRM' \
  && echo "${ATTEMPT_982BATCH}" | grep -q 'reason=empty_confirm' \
  && echo "${ERR_982B}" | grep -Eqi 'confirm missing or not ok|empty/EOF'; then
  ok "#982 batch empty reply → CONFIRM + 0 creates + typed confirm error (#1011)"
else
  fail "#982 batch expected CONFIRM+0 creates+typed err (rc=${batch_982_rc} creates=${CREATES_982B}): ${ATTEMPT_982BATCH} / ${ERR_982B}"
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
  && grep -qE 'cancel=%s timeoutSeconds=%s' "${SCRIPT_DIR}/../src/ezkey_diag.c" \
  && grep -qE '"stage=%s reason=%s' "${SCRIPT_DIR}/../src/ezkey_diag.c" \
  && grep -qE 'LOG_AUTHPRIV[[:space:]]*\|[[:space:]]*LOG_INFO' "${SCRIPT_DIR}/../src/ezkey_diag.c" \
  && grep -qE 'SOCK_DGRAM[[:space:]]*\|[[:space:]]*SOCK_CLOEXEC[[:space:]]*\|[[:space:]]*SOCK_NONBLOCK' \
       "${SCRIPT_DIR}/../src/ezkey_diag.c" \
  && grep -qE 'ezkey_sigterm_handler' "${SCRIPT_DIR}/../src/pam_ezkey.c" \
  && grep -qE '^[[:space:]]*\(void\)ezkey_diag_emit_signal_safe[[:space:]]*\(' \
       "${SCRIPT_DIR}/../src/pam_ezkey.c" \
  && grep -qE 'ezkey_in_wait[[:space:]]*!=' "${SCRIPT_DIR}/../src/pam_ezkey.c" \
  && grep -qE 'raise[[:space:]]*\([[:space:]]*SIGTERM[[:space:]]*\)' "${SCRIPT_DIR}/../src/pam_ezkey.c" \
  && awk '/Chunked \/wait/,/^[[:space:]]*ezkey_in_wait[[:space:]]*=[[:space:]]*1[[:space:]]*;/' \
       "${SCRIPT_DIR}/../src/pam_ezkey.c" | grep -qE 'ezkey_wait_abort[[:space:]]*!=[[:space:]]*0' \
  && ! awk '/Chunked \/wait/,/^[[:space:]]*ezkey_in_wait[[:space:]]*=[[:space:]]*1[[:space:]]*;/' \
       "${SCRIPT_DIR}/../src/pam_ezkey.c" | grep -qE 'ezkey_wait_abort[[:space:]]*=[[:space:]]*0' \
  && grep -q 'ezkey_format_waiting_prompt' "${SCRIPT_DIR}/../src/pam_ezkey.c" \
  && grep -q 'ezkey_challenge_display_ok' "${SCRIPT_DIR}/../src/pam_ezkey.c" \
  && grep -q 'ezkey_prewait_prompt_mode' "${SCRIPT_DIR}/../src/pam_ezkey.c" \
  && grep -q 'ezkey_prewait_response_confirmed' "${SCRIPT_DIR}/../src/pam_ezkey.c" \
  && grep -qE 'pam_prompt[[:space:]]*\([^)]*PAM_PROMPT_ECHO_ON' "${SCRIPT_DIR}/../src/pam_ezkey.c" \
  && ! grep -E 'pam_prompt[[:space:]]*\([^)]*PAM_PROMPT_ECHO_OFF' "${SCRIPT_DIR}/../src/pam_ezkey.c" \
  && ! grep -E '\balarm\s*\(' "${SCRIPT_DIR}/../src/pam_ezkey.c" \
  && grep -q 'LoginGraceTime 120' "${SCRIPT_DIR}/../sshd_config" \
  && grep -q 'Match User testuser' "${SCRIPT_DIR}/../sshd_config" \
  && grep -q 'AuthenticationMethods keyboard-interactive' "${SCRIPT_DIR}/../sshd_config" \
  && grep -q '^wait_polling=2' "${SCRIPT_DIR}/../config/pam_ezkey.conf" \
  && grep -q '^wait_timeout=90' "${SCRIPT_DIR}/../config/pam_ezkey.conf"; then
  ok "#726 chunk=5 interrupt_cancel=5 budget=105 (110<120) + cancel/SIGTERM + #941 prompt guards; no alarm"
else
  fail "missing linkage guards (#726/#941 thresholds/cancel/chunk/Match/alarm/sample conf)"
fi

echo "" | tee -a "${PROOF}"
echo "=== Results: $PASS passed, $FAIL failed ===" | tee -a "${PROOF}"
[ "${FAIL}" -eq 0 ]
