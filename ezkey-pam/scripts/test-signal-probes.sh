#!/usr/bin/env bash
# #1011 B3 — sshd-level SIGTERM / client-kill probes (real Rocky sshd + stub).
# Asserts the PAM child exits quickly and authpriv never records SUCCESS after
# the client/process is gone. Must FAIL on broken head 8272d360 (hang / SUCCESS).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
. "${SCRIPT_DIR}/common.sh"

CONTAINER="${EZKEY_PAM_CONTAINER:-ezkey-pam-ssh}"
PORT="${EZKEY_PAM_SSH_PORT:-${SSH_PORT:-2222}}"
SSH_USER="${EZKEY_SSH_USER:-${SSH_USER:-testuser}}"
CONF="/etc/security/pam_ezkey.conf"
BACKUP="/tmp/pam_ezkey.conf.signal-probes.bak"
STUB_PORT=18080
STUB_PID_FILE="/tmp/ezkey-stub.pid"
STUB_REQ_LOG="/tmp/ezkey-stub-requests.log"
ARTIFACT_DIR="${EZKEY_PAM_ARTIFACT_DIR:-/opt/cursor/artifacts}"
mkdir -p "${ARTIFACT_DIR}"
PROOF="${ARTIFACT_DIR}/pam-signal-probes.log"

PASS=0
FAIL=0
PAM_GONE_MAX_MS="${EZKEY_PAM_GONE_MAX_MS:-2000}"

ok() {
  echo "[PASS] $*" | tee -a "${PROOF}"
  PASS=$((PASS + 1))
}
fail() {
  echo "[FAIL] $*" | tee -a "${PROOF}"
  FAIL=$((FAIL + 1))
}

if ! docker ps --format '{{.Names}}' | grep -qx "${CONTAINER}"; then
  echo "${CONTAINER} is not running" >&2
  exit 1
fi

echo "=== PAM SIGTERM / client-kill probes (#1011 B3) ===" | tee "${PROOF}"
echo "pam_gone budget: ${PAM_GONE_MAX_MS} ms" | tee -a "${PROOF}"

docker exec "${CONTAINER}" cp -a "${CONF}" "${BACKUP}"
restore_conf() {
  docker exec "${CONTAINER}" sh -c \
    "if [ -f '${BACKUP}' ]; then cp -a '${BACKUP}' '${CONF}'; chown root:root '${CONF}'; chmod 600 '${CONF}'; fi" \
    >/dev/null 2>&1 || true
}
stop_stub() {
  docker exec "${CONTAINER}" sh -c \
    "if [ -f '${STUB_PID_FILE}' ]; then kill \"\$(cat '${STUB_PID_FILE}')\" 2>/dev/null || true; rm -f '${STUB_PID_FILE}'; fi" \
    >/dev/null 2>&1 || true
}
cleanup() {
  stop_stub
  restore_conf
}
trap cleanup EXIT

start_stub() {
  local mode="$1"
  local extra="${2:-}"
  stop_stub
  docker exec "${CONTAINER}" sh -c "rm -f '${STUB_REQ_LOG}'; : >'${STUB_REQ_LOG}'" || true
  docker exec -d "${CONTAINER}" sh -c \
    "python3 /opt/pam-ezkey/scripts/integration-api-stub.py \
      --bind 127.0.0.1 --port ${STUB_PORT} --mode ${mode} ${extra} \
      --request-log '${STUB_REQ_LOG}' --pid-file '${STUB_PID_FILE}' \
      >/tmp/ezkey-stub.out 2>/tmp/ezkey-stub.err"
  local i
  for i in $(seq 1 50); do
    docker exec "${CONTAINER}" sh -c "ss -ltn | grep -q ':${STUB_PORT}'" 2>/dev/null && return 0
    sleep 0.1
  done
  fail "stub did not listen on ${STUB_PORT}"
  return 1
}

point_conf() {
  local wait_to="${1:-30}"
  local chal="${2:-true}"
  docker exec "${CONTAINER}" sh -c \
    "sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
     sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
     sed -i 's/^wait_timeout=.*/wait_timeout=${wait_to}/' '${CONF}'; \
     sed -i 's/^api_timeout=.*/api_timeout=10/' '${CONF}'; \
     sed -i 's/^challenge_requested=.*/challenge_requested=${chal}/' '${CONF}'; \
     sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
     chown root:root '${CONF}'; chmod 600 '${CONF}'"
}

# PIDs of sshd PAM children for SSH_USER (OpenSSH titles them "... [pam]").
pam_pids() {
  docker exec "${CONTAINER}" sh -c \
    "ps -eo pid=,args= | grep -E '\\[pam\\]' | grep -v grep | awk '{print \$1}'" \
    2>/dev/null | tr -d '\r' || true
}

wait_pam_gone() {
  local before_pids="$1"
  local start_ms="$2"
  local deadline_ms=$((start_ms + PAM_GONE_MAX_MS))
  local now_ms pam_gone_ms=-1
  local p

  while true; do
    now_ms="$(date +%s%3N)"
    local still=0
    for p in ${before_pids}; do
      if docker exec "${CONTAINER}" sh -c "kill -0 ${p} 2>/dev/null"; then
        still=1
        break
      fi
    done
    if [ "${still}" -eq 0 ]; then
      pam_gone_ms=$((now_ms - start_ms))
      echo "${pam_gone_ms}"
      return 0
    fi
    if [ "${now_ms}" -ge "${deadline_ms}" ]; then
      echo "-1"
      return 1
    fi
    sleep 0.05
  done
}

count_attempt_matches() {
  local pattern="$1"
  docker exec "${CONTAINER}" sh -c \
    "grep -cE '${pattern}' /var/log/secure 2>/dev/null || true" | tr -d ' \r'
}

last_attempt() {
  docker exec "${CONTAINER}" sh -c \
    "grep 'pam_ezkey: attempt ' /var/log/secure | tail -n 1" 2>/dev/null || true
}

# --- Probe 1: client kill at pre-create ok prompt ---
probe_kill_at_ok() {
  echo "" | tee -a "${PROOF}"
  echo "--- Probe 1: client kill at ok prompt ---" | tee -a "${PROOF}"
  start_stub ok
  point_conf 30 true
  local before_n
  before_n="$(count_attempt_matches 'pam_ezkey: attempt ')"
  before_n="${before_n:-0}"

  local log="${ARTIFACT_DIR}/pam-signal-probe-ok.log"
  local ctrl
  ctrl="$(ezkey_mk_ctrl_fifo)"
  local ctrl_dir
  ctrl_dir="$(dirname "${ctrl}")"
  : >"${log}"
  set +e
  python3 "${SCRIPT_DIR}/ssh-pty-capture.py" "${log}" "${ctrl}" \
    ssh -tt -p "${PORT}" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
    -o PreferredAuthentications=keyboard-interactive -o PubkeyAuthentication=no \
    -o PasswordAuthentication=no -o NumberOfPasswordPrompts=1 -o ConnectTimeout=15 \
    "${SSH_USER}@127.0.0.1" 'echo SHOULD_NOT_RUN; exit 0' \
    >/dev/null 2>&1 &
  local ssh_pid=$!
  set -e

  local i
  for i in $(seq 1 80); do
    if ezkey_ssh_capture_has_prewait_prompt "${log}"; then
      break
    fi
    sleep 0.1
  done
  local pids
  pids="$(pam_pids)"
  echo "probe1 pam_pids=${pids:-none}" | tee -a "${PROOF}"
  if [ -z "${pids}" ]; then
    printf 'quit\n' >"${ctrl}" 2>/dev/null || true
    kill "${ssh_pid}" 2>/dev/null || true
    wait "${ssh_pid}" 2>/dev/null || true
    rm -rf "${ctrl_dir}"
    fail "probe1: no [pam] child at ok prompt"
    stop_stub
    return
  fi
  local t0
  t0="$(date +%s%3N)"
  kill "${ssh_pid}" 2>/dev/null || true
  local gone
  gone="$(wait_pam_gone "${pids}" "${t0}" || true)"
  wait "${ssh_pid}" 2>/dev/null || true
  rm -rf "${ctrl_dir}"

  sleep 0.3
  local attempt
  attempt="$(last_attempt)"
  local signal_n success_n
  signal_n="$(docker exec "${CONTAINER}" sh -c \
    "grep 'pam_ezkey: attempt ' /var/log/secure | tail -n +$((before_n + 1)) | grep -c 'result=SIGNAL' || true" \
    | tr -d ' \r')"
  success_n="$(docker exec "${CONTAINER}" sh -c \
    "grep 'pam_ezkey: attempt ' /var/log/secure | tail -n +$((before_n + 1)) | grep -c 'result=SUCCESS' || true" \
    | tr -d ' \r')"
  signal_n="${signal_n:-0}"
  success_n="${success_n:-0}"
  echo "probe1 gone_ms=${gone} signal_n=${signal_n} success_n=${success_n} attempt=${attempt}" \
    | tee -a "${PROOF}"

  if [ "${gone}" != "-1" ] && [ "${gone}" -lt "${PAM_GONE_MAX_MS}" ] \
    && [ "${signal_n}" -eq 1 ] && [ "${success_n}" -eq 0 ] \
    && echo "${attempt}" | grep -q 'result=SIGNAL' \
    && echo "${attempt}" | grep -q 'stage=confirm'; then
    ok "probe1 ok-prompt kill: pam gone ${gone}ms + one SIGNAL"
  else
    fail "probe1 expected pam<${PAM_GONE_MAX_MS}ms + 1 SIGNAL (gone=${gone} signal=${signal_n} success=${success_n})"
  fi
  stop_stub
}

# --- Probe 2: client kill at challenge prompt ---
probe_kill_at_challenge() {
  echo "" | tee -a "${PROOF}"
  echo "--- Probe 2: client kill at challenge prompt ---" | tee -a "${PROOF}"
  start_stub ok
  point_conf 45 true
  local before_n
  before_n="$(count_attempt_matches 'pam_ezkey: attempt ')"
  before_n="${before_n:-0}"

  local log="${ARTIFACT_DIR}/pam-signal-probe-chal.log"
  local ctrl
  ctrl="$(ezkey_mk_ctrl_fifo)"
  local ctrl_dir
  ctrl_dir="$(dirname "${ctrl}")"
  : >"${log}"
  set +e
  python3 "${SCRIPT_DIR}/ssh-pty-capture.py" "${log}" "${ctrl}" \
    ssh -tt -p "${PORT}" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
    -o PreferredAuthentications=keyboard-interactive -o PubkeyAuthentication=no \
    -o PasswordAuthentication=no -o NumberOfPasswordPrompts=1 -o ConnectTimeout=15 \
    "${SSH_USER}@127.0.0.1" 'echo SHOULD_NOT_RUN; exit 0' \
    >/dev/null 2>&1 &
  local ssh_pid=$!
  set -e

  local i
  for i in $(seq 1 80); do
    if ezkey_ssh_capture_has_prewait_prompt "${log}"; then
      printf 'ok\n' >"${ctrl}" 2>/dev/null || true
      break
    fi
    sleep 0.1
  done
  # Wait for challenge digits prompt (second prompt).
  for i in $(seq 1 80); do
    if grep -Eqi 'challenge|Approve on your phone' "${log}" 2>/dev/null; then
      break
    fi
    sleep 0.1
  done
  sleep 0.2
  local pids
  pids="$(pam_pids)"
  echo "probe2 pam_pids=${pids:-none}" | tee -a "${PROOF}"
  if [ -z "${pids}" ]; then
    printf 'quit\n' >"${ctrl}" 2>/dev/null || true
    kill "${ssh_pid}" 2>/dev/null || true
    wait "${ssh_pid}" 2>/dev/null || true
    rm -rf "${ctrl_dir}"
    fail "probe2: no [pam] child at challenge prompt"
    stop_stub
    return
  fi
  local t0
  t0="$(date +%s%3N)"
  kill "${ssh_pid}" 2>/dev/null || true
  local gone
  gone="$(wait_pam_gone "${pids}" "${t0}" || true)"
  wait "${ssh_pid}" 2>/dev/null || true
  rm -rf "${ctrl_dir}"

  sleep 0.3
  local attempt
  attempt="$(last_attempt)"
  local signal_n success_n
  signal_n="$(docker exec "${CONTAINER}" sh -c \
    "grep 'pam_ezkey: attempt ' /var/log/secure | tail -n +$((before_n + 1)) | grep -c 'result=SIGNAL' || true" \
    | tr -d ' \r')"
  success_n="$(docker exec "${CONTAINER}" sh -c \
    "grep 'pam_ezkey: attempt ' /var/log/secure | tail -n +$((before_n + 1)) | grep -c 'result=SUCCESS' || true" \
    | tr -d ' \r')"
  signal_n="${signal_n:-0}"
  success_n="${success_n:-0}"
  echo "probe2 gone_ms=${gone} signal_n=${signal_n} success_n=${success_n} attempt=${attempt}" \
    | tee -a "${PROOF}"

  if [ "${gone}" != "-1" ] && [ "${gone}" -lt "${PAM_GONE_MAX_MS}" ] \
    && [ "${signal_n}" -eq 1 ] && [ "${success_n}" -eq 0 ] \
    && echo "${attempt}" | grep -q 'result=SIGNAL' \
    && echo "${attempt}" | grep -q 'stage=challenge'; then
    ok "probe2 challenge-prompt kill: pam gone ${gone}ms + one SIGNAL"
  else
    fail "probe2 expected pam<${PAM_GONE_MAX_MS}ms + 1 SIGNAL (gone=${gone} signal=${signal_n} success=${success_n})"
  fi
  stop_stub
}

# --- Probe 3: SIGTERM during POST create (slow stub) ---
probe_term_during_create() {
  echo "" | tee -a "${PROOF}"
  echo "--- Probe 3: SIGTERM during POST create ---" | tee -a "${PROOF}"
  start_stub slow_create 8
  point_conf 30 false
  local before_n
  before_n="$(count_attempt_matches 'pam_ezkey: attempt ')"
  before_n="${before_n:-0}"

  local log="${ARTIFACT_DIR}/pam-signal-probe-create.log"
  local ctrl
  ctrl="$(ezkey_mk_ctrl_fifo)"
  local ctrl_dir
  ctrl_dir="$(dirname "${ctrl}")"
  : >"${log}"
  set +e
  python3 "${SCRIPT_DIR}/ssh-pty-capture.py" "${log}" "${ctrl}" \
    ssh -tt -p "${PORT}" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
    -o PreferredAuthentications=keyboard-interactive -o PubkeyAuthentication=no \
    -o PasswordAuthentication=no -o NumberOfPasswordPrompts=1 -o ConnectTimeout=20 \
    "${SSH_USER}@127.0.0.1" 'echo SHOULD_NOT_RUN; exit 0' \
    >/dev/null 2>&1 &
  local ssh_pid=$!
  set -e

  local i
  for i in $(seq 1 80); do
    if ezkey_ssh_capture_has_prewait_prompt "${log}"; then
      printf 'ok\n' >"${ctrl}" 2>/dev/null || true
      break
    fi
    sleep 0.1
  done
  # Wait until create HTTP is in flight (stub sleeping).
  for i in $(seq 1 40); do
    if docker exec "${CONTAINER}" sh -c \
      "grep -q 'POST .*/auth-attempts' '${STUB_REQ_LOG}' 2>/dev/null"; then
      break
    fi
    sleep 0.1
  done
  sleep 0.3
  local pids
  pids="$(pam_pids)"
  echo "probe3 pam_pids=${pids:-none}" | tee -a "${PROOF}"
  if [ -z "${pids}" ]; then
    printf 'quit\n' >"${ctrl}" 2>/dev/null || true
    kill "${ssh_pid}" 2>/dev/null || true
    wait "${ssh_pid}" 2>/dev/null || true
    rm -rf "${ctrl_dir}"
    fail "probe3: no [pam] child during create"
    stop_stub
    return
  fi
  local t0 first_pid
  first_pid="$(echo "${pids}" | awk 'NR==1{print}')"
  t0="$(date +%s%3N)"
  docker exec "${CONTAINER}" kill -TERM "${first_pid}" 2>/dev/null || true
  local gone
  gone="$(wait_pam_gone "${pids}" "${t0}" || true)"
  printf 'quit\n' >"${ctrl}" 2>/dev/null || true
  kill "${ssh_pid}" 2>/dev/null || true
  wait "${ssh_pid}" 2>/dev/null || true
  rm -rf "${ctrl_dir}"

  sleep 0.3
  local attempt
  attempt="$(last_attempt)"
  local signal_n success_n
  signal_n="$(docker exec "${CONTAINER}" sh -c \
    "grep 'pam_ezkey: attempt ' /var/log/secure | tail -n +$((before_n + 1)) | grep -c 'result=SIGNAL' || true" \
    | tr -d ' \r')"
  success_n="$(docker exec "${CONTAINER}" sh -c \
    "grep 'pam_ezkey: attempt ' /var/log/secure | tail -n +$((before_n + 1)) | grep -c 'result=SUCCESS' || true" \
    | tr -d ' \r')"
  signal_n="${signal_n:-0}"
  success_n="${success_n:-0}"
  echo "probe3 gone_ms=${gone} signal_n=${signal_n} success_n=${success_n} attempt=${attempt}" \
    | tee -a "${PROOF}"
  echo "probe3 NOTE: SUCCESS-after-client-gone case — after fix expect SIGNAL, not SUCCESS" \
    | tee -a "${PROOF}"

  if [ "${gone}" != "-1" ] && [ "${gone}" -lt "${PAM_GONE_MAX_MS}" ] \
    && [ "${signal_n}" -eq 1 ] && [ "${success_n}" -eq 0 ] \
    && echo "${attempt}" | grep -q 'result=SIGNAL' \
    && echo "${attempt}" | grep -q 'stage=create'; then
    ok "probe3 TERM during create: pam gone ${gone}ms + SIGNAL (no SUCCESS)"
  else
    fail "probe3 expected pam<${PAM_GONE_MAX_MS}ms + SIGNAL no SUCCESS (gone=${gone} signal=${signal_n} success=${success_n} line=${attempt})"
  fi
  stop_stub
}

# --- Probe 4: SIGTERM during /wait (abort path; UNAVAIL reason=signal) ---
probe_term_during_wait() {
  echo "" | tee -a "${PROOF}"
  echo "--- Probe 4: SIGTERM during /wait ---" | tee -a "${PROOF}"
  start_stub pending
  point_conf 30 false
  local before_n
  before_n="$(count_attempt_matches 'pam_ezkey: attempt ')"
  before_n="${before_n:-0}"

  local log="${ARTIFACT_DIR}/pam-signal-probe-wait.log"
  local ctrl
  ctrl="$(ezkey_mk_ctrl_fifo)"
  local ctrl_dir
  ctrl_dir="$(dirname "${ctrl}")"
  : >"${log}"
  set +e
  python3 "${SCRIPT_DIR}/ssh-pty-capture.py" "${log}" "${ctrl}" \
    ssh -tt -p "${PORT}" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
    -o PreferredAuthentications=keyboard-interactive -o PubkeyAuthentication=no \
    -o PasswordAuthentication=no -o NumberOfPasswordPrompts=1 -o ConnectTimeout=20 \
    "${SSH_USER}@127.0.0.1" 'echo SHOULD_NOT_RUN; exit 0' \
    >/dev/null 2>&1 &
  local ssh_pid=$!
  set -e

  local i
  for i in $(seq 1 80); do
    if ezkey_ssh_capture_has_prewait_prompt "${log}"; then
      printf 'ok\n' >"${ctrl}" 2>/dev/null || true
      break
    fi
    sleep 0.1
  done
  for i in $(seq 1 80); do
    if docker exec "${CONTAINER}" sh -c \
      "grep -q 'GET .*/wait' '${STUB_REQ_LOG}' 2>/dev/null"; then
      break
    fi
    sleep 0.1
  done
  sleep 0.2
  local pids
  pids="$(pam_pids)"
  echo "probe4 pam_pids=${pids:-none}" | tee -a "${PROOF}"
  if [ -z "${pids}" ]; then
    printf 'quit\n' >"${ctrl}" 2>/dev/null || true
    kill "${ssh_pid}" 2>/dev/null || true
    wait "${ssh_pid}" 2>/dev/null || true
    rm -rf "${ctrl_dir}"
    fail "probe4: no [pam] child during wait"
    stop_stub
    return
  fi
  local t0 first_pid
  first_pid="$(echo "${pids}" | awk 'NR==1{print}')"
  t0="$(date +%s%3N)"
  docker exec "${CONTAINER}" kill -TERM "${first_pid}" 2>/dev/null || true
  local gone
  gone="$(wait_pam_gone "${pids}" "${t0}" || true)"
  printf 'quit\n' >"${ctrl}" 2>/dev/null || true
  kill "${ssh_pid}" 2>/dev/null || true
  wait "${ssh_pid}" 2>/dev/null || true
  rm -rf "${ctrl_dir}"

  sleep 0.5
  local attempt
  attempt="$(last_attempt)"
  local success_n
  success_n="$(docker exec "${CONTAINER}" sh -c \
    "grep 'pam_ezkey: attempt ' /var/log/secure | tail -n +$((before_n + 1)) | grep -c 'result=SUCCESS' || true" \
    | tr -d ' \r')"
  success_n="${success_n:-0}"
  echo "probe4 gone_ms=${gone} success_n=${success_n} attempt=${attempt}" \
    | tee -a "${PROOF}"

  # Wait path: normal summary UNAVAIL reason=signal (not SIGNAL re-raise).
  if [ "${gone}" != "-1" ] && [ "${gone}" -lt "${PAM_GONE_MAX_MS}" ] \
    && [ "${success_n}" -eq 0 ] \
    && echo "${attempt}" | grep -q 'reason=signal' \
    && echo "${attempt}" | grep -Eq 'result=(UNAVAIL|SIGNAL)'; then
    ok "probe4 TERM during wait: pam gone ${gone}ms + reason=signal (no SUCCESS)"
  else
    fail "probe4 expected pam<${PAM_GONE_MAX_MS}ms + reason=signal no SUCCESS (gone=${gone} success=${success_n} line=${attempt})"
  fi
  stop_stub
}

probe_kill_at_ok
probe_kill_at_challenge
probe_term_during_create
probe_term_during_wait

echo "" | tee -a "${PROOF}"
echo "=== Results: ${PASS} passed, ${FAIL} failed ===" | tee -a "${PROOF}"
[ "${FAIL}" -eq 0 ]
