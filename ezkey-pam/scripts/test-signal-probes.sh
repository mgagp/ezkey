#!/usr/bin/env bash
# #1011 B3 — sshd-level SIGTERM / client-kill probes (real Rocky sshd + stub).
# Asserts the PAM child exits quickly on non-wait kills (SIGNAL) and that the
# wait-path abort path does not hang. #1016 D1 caveat: SUCCESS remains possible
# if TERM is absorbed during /wait after ACCEPTED already arrived in the same
# chunk (result reaches an sshd whose client has left — not an elevation).
# Must FAIL on broken head 8272d360 (hang at prompt).
#
# #1054 probe 4 flake (harness): after a fast pam exit the capture helper may
# already be gone; writing "quit" with a blocking O_WRONLY open on the control
# FIFO hangs until an external timeout. Use ezkey_ctrl_write (bounded) and
# force-kill pam + capture after the wall budget.
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
STUB_PID_FILE="/tmp/ezkey-pam-stub-signal.pid"
STUB_REQ_LOG="/tmp/ezkey-pam-stub-signal-reqs.log"
ARTIFACT_DIR="$(ezkey_pam_artifact_dir)"
mkdir -p "${ARTIFACT_DIR}"
PROOF="${ARTIFACT_DIR}/pam-signal-probes.log"

PASS=0
FAIL=0
PAM_GONE_MAX_MS="${EZKEY_PAM_GONE_MAX_MS:-2000}"
# #1016 P11: per-probe wall budget (loud fail if pam child / probe hangs).
PROBE_WALL_SECS="${EZKEY_PAM_PROBE_WALL_SECS:-90}"

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
  # Kill by pidfile and by port (CLI args are not supported by the stub —
  # it uses EZKEY_STUB_* env vars, same as test-timeouts.sh).
  docker exec "${CONTAINER}" sh -c \
    "if [ -f '${STUB_PID_FILE}' ]; then kill \"\$(cat '${STUB_PID_FILE}')\" 2>/dev/null || true; rm -f '${STUB_PID_FILE}'; fi
     fuser -k ${STUB_PORT}/tcp >/dev/null 2>&1 || true
     pkill -f 'integration-api-stub.py' 2>/dev/null || true
     sleep 0.15" \
    >/dev/null 2>&1 || true
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
     >/tmp/ezkey-pam-stub-signal.log 2>&1 & echo \$! >'${STUB_PID_FILE}'"
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

# #1054: dump state BEFORE force-kill so a flake can be classified product vs harness.
capture_stuck_state() {
  local label="$1"
  local dump="${ARTIFACT_DIR}/pam-signal-${label}-stuck.txt"
  {
    echo "=== stuck state label=${label} epoch=$(date +%s) ==="
    echo "--- host: capture helpers ---"
    ps -eo pid=,args= 2>/dev/null | grep -E 'ssh-pty-capture|test-signal' | grep -v grep || true
    echo "--- container: processes ---"
    docker exec "${CONTAINER}" sh -c 'ps -eo pid,ppid,stat,etime,args' 2>/dev/null || true
    echo "--- container: listeners ---"
    docker exec "${CONTAINER}" sh -c 'ss -ltnp 2>/dev/null || ss -ltn' 2>/dev/null || true
    echo "--- container: pam children /proc summary ---"
    docker exec "${CONTAINER}" sh -c '
      for p in $(ps -eo pid=,args= | grep -E "\[pam\]" | grep -v grep | awk "{print \$1}"); do
        echo "pam_pid=$p"
        ls -l /proc/$p/fd 2>/dev/null | head -n 40 || true
        tr "\0" " " < /proc/$p/cmdline 2>/dev/null; echo
        cat /proc/$p/wchan 2>/dev/null; echo
        cat /proc/$p/stack 2>/dev/null | head -n 20 || true
      done
    ' 2>/dev/null || true
    echo "--- container: last attempt lines ---"
    docker exec "${CONTAINER}" sh -c \
      "grep 'pam_ezkey: attempt ' /var/log/secure 2>/dev/null | tail -n 5" || true
    echo "--- stub req log ---"
    docker exec "${CONTAINER}" sh -c "tail -n 30 '${STUB_REQ_LOG}' 2>/dev/null" || true
  } >"${dump}" 2>&1 || true
  echo "stuck state written: ${dump}" | tee -a "${PROOF}"
}

force_kill_probe_residue() {
  # SIGKILL — TERM is absorbed during /wait (product); harness must not stall.
  pkill -9 -f 'ssh-pty-capture.py' 2>/dev/null || true
  docker exec "${CONTAINER}" sh -c \
    "ps -eo pid=,args= | grep -E '\\[pam\\]' | grep -v grep | awk '{print \$1}' | xargs -r kill -KILL
     pkill -9 -f 'ssh ' 2>/dev/null || true" \
    2>/dev/null || true
}

wait_pam_gone() {
  local before_pids="$1"
  local start_ms="$2"
  local deadline_ms=$((start_ms + PAM_GONE_MAX_MS))
  local now_ms pam_gone_ms=-1
  local p
  local wall_deadline_s

  wall_deadline_s=$(($(date +%s) + PROBE_WALL_SECS))
  while true; do
    now_ms="$(date +%s%3N)"
    if [ "$(date +%s)" -ge "${wall_deadline_s}" ]; then
      echo "[FAIL] wait_pam_gone: probe wall ${PROBE_WALL_SECS}s exceeded while pam_pids still live (${before_pids})" \
        | tee -a "${PROOF}" >&2
      echo "-1"
      return 1
    fi
    local still=0
    for p in ${before_pids}; do
      # Bound each docker exec so a stuck daemon cannot hang the probe forever.
      if timeout 2s docker exec "${CONTAINER}" sh -c "kill -0 ${p} 2>/dev/null"; then
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
      echo "[FAIL] wait_pam_gone: pam pid did not exit within ${PAM_GONE_MAX_MS}ms (pids=${before_pids})" \
        | tee -a "${PROOF}" >&2
      echo "-1"
      return 1
    fi
    sleep 0.05
  done
}

# Run one probe function under a sibling wall watchdog (#1016 P11 / #1054).
run_probe() {
  local label="$1"
  local fn="$2"
  local rc=0
  local watch

  echo "run_probe ${label} wall=${PROBE_WALL_SECS}s" | tee -a "${PROOF}"
  (
    sleep "${PROBE_WALL_SECS}"
    echo "[FAIL] ${label}: exceeded ${PROBE_WALL_SECS}s wall budget (pam may be stuck)" \
      | tee -a "${PROOF}" >&2
    capture_stuck_state "${label}"
    force_kill_probe_residue
  ) &
  watch=$!
  set +e
  "${fn}"
  rc=$?
  set -e
  kill "${watch}" 2>/dev/null || true
  wait "${watch}" 2>/dev/null || true
  if [ "${rc}" -ne 0 ]; then
    fail "${label}: probe function exit ${rc}"
  fi
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
    ezkey_ctrl_write "${ctrl}" "quit"
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
      ezkey_ctrl_write "${ctrl}" "ok"
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
    ezkey_ctrl_write "${ctrl}" "quit"
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
  start_stub slow_create 12
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
      ezkey_ctrl_write "${ctrl}" "ok"
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
    ezkey_ctrl_write "${ctrl}" "quit"
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
  ezkey_ctrl_write "${ctrl}" "quit"
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
  echo "probe3 NOTE: TERM during create (outside /wait) expects SIGNAL, not SUCCESS. Separate D1 caveat: SUCCESS remains possible if TERM is absorbed during /wait after ACCEPTED in the same chunk (not an elevation)." \
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
      ezkey_ctrl_write "${ctrl}" "ok"
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
    ezkey_ctrl_write "${ctrl}" "quit"
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
  # Bounded write: capture may already have exited after a fast pam return (#1054).
  ezkey_ctrl_write "${ctrl}" "quit"
  kill "${ssh_pid}" 2>/dev/null || true
  # Bound reaping: timeout(1) cannot wait(1) a parent-shell child.
  for i in $(seq 1 50); do
    if ! kill -0 "${ssh_pid}" 2>/dev/null; then
      break
    fi
    sleep 0.1
  done
  kill -KILL "${ssh_pid}" 2>/dev/null || true
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

run_probe "probe1-ok" probe_kill_at_ok
run_probe "probe2-challenge" probe_kill_at_challenge
run_probe "probe3-create" probe_term_during_create
run_probe "probe4-wait" probe_term_during_wait

echo "" | tee -a "${PROOF}"
echo "=== Results: ${PASS} passed, ${FAIL} failed ===" | tee -a "${PROOF}"
[ "${FAIL}" -eq 0 ]
