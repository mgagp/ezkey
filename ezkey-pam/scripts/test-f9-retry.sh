#!/usr/bin/env bash
# #907 F9 proof (Patrick B3 / B2b / option A): real sshd behaviour.
# A) Without Match MaxAuthTries 1 + client NumberOfPasswordPrompts=3 → ≥2 creates
# B) With demo Match MaxAuthTries 1 → exactly 1 create
# C) Kill SSH client during /wait → stub sees cancel within ≤5 s (B2b)
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
BACKUP="/etc/security/pam_ezkey.conf.f9"
SSHD_CFG="/etc/ssh/sshd_config"
SSHD_BAK="/etc/ssh/sshd_config.f9bak"
PORT="${SSH_PORT}"
STUB_PORT="${EZKEY_STUB_PORT:-18082}"
STUB_PID_FILE="/tmp/ezkey-pam-stub-f9.pid"
STUB_REQ_LOG="/tmp/ezkey-pam-stub-f9-reqs.log"
ARTIFACT_DIR="${EZKEY_PAM_ARTIFACT_DIR:-/opt/cursor/artifacts}"
mkdir -p "${ARTIFACT_DIR}"
PROOF="${ARTIFACT_DIR}/pam-f9-retry.log"
PASS=0
FAIL=0

ok()   { echo "[PASS] $1" | tee -a "${PROOF}"; PASS=$((PASS+1)); }
fail() { echo "[FAIL] $1" | tee -a "${PROOF}"; FAIL=$((FAIL+1)); }

if ! docker ps --format '{{.Names}}' | grep -qx "${CONTAINER}"; then
  echo "${CONTAINER} is not running" >&2
  exit 1
fi

: >"${PROOF}"
echo "== test-f9-retry.sh (#907 B3 + B2b) ==" | tee -a "${PROOF}"

restore_all() {
  docker exec "${CONTAINER}" sh -c \
    "if [ -f '${SSHD_BAK}' ]; then cp -a '${SSHD_BAK}' '${SSHD_CFG}'; fi
     if [ -f '${BACKUP}' ]; then cp -a '${BACKUP}' '${CONF}'; chown root:root '${CONF}'; chmod 600 '${CONF}'; fi
     if [ -f '${STUB_PID_FILE}' ]; then kill \"\$(cat '${STUB_PID_FILE}')\" 2>/dev/null || true; rm -f '${STUB_PID_FILE}'; fi
     pkill -HUP sshd 2>/dev/null || true" || true
}
trap restore_all EXIT

docker exec "${CONTAINER}" sh -c \
  "cp -a '${SSHD_CFG}' '${SSHD_BAK}'; cp -a '${CONF}' '${BACKUP}'"

stop_stub() {
  docker exec "${CONTAINER}" sh -c \
    "if [ -f '${STUB_PID_FILE}' ]; then kill \"\$(cat '${STUB_PID_FILE}')\" 2>/dev/null || true; rm -f '${STUB_PID_FILE}'; fi" \
    || true
}

start_stub() {
  local mode="$1"
  stop_stub
  docker exec "${CONTAINER}" sh -c "rm -f '${STUB_REQ_LOG}'; : >'${STUB_REQ_LOG}'
     : > /var/log/ezkey-pam/debug.out; : > /var/log/ezkey-pam/debug.err
     printf '%s\n' '____EZKEY_CASE_f9_${mode}____' >> /var/log/ezkey-pam/debug.out" || true
  docker exec -d "${CONTAINER}" sh -c \
    "EZKEY_STUB_MODE='${mode}' EZKEY_STUB_PORT='${STUB_PORT}' EZKEY_STUB_SLOW_SECS='1' \
     EZKEY_STUB_REQ_LOG='${STUB_REQ_LOG}' \
     python3 /opt/pam-ezkey/scripts/integration-api-stub.py \
     >/tmp/ezkey-pam-stub-f9.log 2>&1 & echo \$! >'${STUB_PID_FILE}'"
  local i
  for i in $(seq 1 40); do
    docker exec "${CONTAINER}" sh -c "ss -ltn | grep -q ':${STUB_PORT}'" 2>/dev/null && return 0
    sleep 0.1
  done
  fail "stub :${STUB_PORT} not listening"
  return 1
}

point_conf_at_stub() {
  local wait_to="${1:-15}"
  docker exec "${CONTAINER}" sh -c \
    "grep -v '^integration_api_url=' '${CONF}' | grep -v '^wait_timeout=' >'${CONF}.tmp'
     echo 'integration_api_url=http://127.0.0.1:${STUB_PORT}' >>'${CONF}.tmp'
     echo 'wait_timeout=${wait_to}' >>'${CONF}.tmp'
     if ! grep -q '^lab_http=' '${CONF}.tmp'; then echo 'lab_http=true' >>'${CONF}.tmp'; fi
     sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}.tmp'
     if ! grep -q '^api_timeout=' '${CONF}.tmp'; then echo 'api_timeout=5' >>'${CONF}.tmp'; fi
     if ! grep -q '^debug=' '${CONF}.tmp'; then echo 'debug=true' >>'${CONF}.tmp'; fi
     sed -i 's/^debug=.*/debug=true/' '${CONF}.tmp'
     install -m 600 -o root -g root '${CONF}.tmp' '${CONF}'
     rm -f '${CONF}.tmp'"
}

install_sshd_cfg() {
  local host_cfg="$1"
  docker cp "${host_cfg}" "${CONTAINER}:${SSHD_CFG}"
  docker exec "${CONTAINER}" sh -c \
    "sshd -t -f '${SSHD_CFG}' && pkill -HUP sshd"
  sleep 1
}

# Single integer only — avoid grep -vc || echo 0 doubling "0" when count is 0.
count_creates() {
  docker exec "${CONTAINER}" sh -c \
    "n=\$(grep 'POST .*/auth-attempts' '${STUB_REQ_LOG}' 2>/dev/null | grep -v cancel | wc -l)
     echo \"\${n:-0}\"" | tr -d ' \r' | head -n1
}

count_cancels() {
  docker exec "${CONTAINER}" sh -c \
    "n=\$(grep -c 'POST .*/cancel' '${STUB_REQ_LOG}' 2>/dev/null || true)
     echo \"\${n:-0}\"" | tr -d ' \r' | head -n1
}

# --- Case A: multitry sshd — F9 recreates (Match forgotten) ---
start_stub rejected
point_conf_at_stub 15
install_sshd_cfg "${SCRIPT_DIR}/../sshd_config.f9-multitry"

docker exec "${CONTAINER}" sh -c ": >'${STUB_REQ_LOG}'"
set +e
ssh -tt -p "${PORT}" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  -o PreferredAuthentications=keyboard-interactive -o PubkeyAuthentication=no \
  -o PasswordAuthentication=no -o NumberOfPasswordPrompts=3 -o ConnectTimeout=10 \
  -o ServerAliveInterval=5 -o ServerAliveCountMax=24 \
  "${SSH_USER}@127.0.0.1" 'echo X' >/tmp/ezkey-f9-multitry-ssh.log 2>&1
set -e

CREATES_MULTI="$(count_creates)"
CREATES_MULTI="${CREATES_MULTI:-0}"
echo "multitry creates=${CREATES_MULTI}" | tee -a "${PROOF}"
if [ "${CREATES_MULTI}" -ge 2 ]; then
  ok "sshd MaxAuthTries>=3 + NumberOfPasswordPrompts=3 → ${CREATES_MULTI} creates (F9 real / Match forgotten)"
  echo "WARNING: without Match MaxAuthTries 1, OpenSSH recreates auth attempts (#907)." \
    | tee -a "${PROOF}"
else
  fail "expected >=2 creates without Match MaxAuthTries 1, got ${CREATES_MULTI}"
fi

# --- Case B: Match MaxAuthTries 1 — single create ---
start_stub rejected
point_conf_at_stub 15
install_sshd_cfg "${SCRIPT_DIR}/../sshd_config"

docker exec "${CONTAINER}" sh -c ": >'${STUB_REQ_LOG}'"
set +e
ssh -tt -p "${PORT}" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  -o PreferredAuthentications=keyboard-interactive -o PubkeyAuthentication=no \
  -o PasswordAuthentication=no -o NumberOfPasswordPrompts=3 -o ConnectTimeout=10 \
  -o ServerAliveInterval=5 -o ServerAliveCountMax=24 \
  "${SSH_USER}@127.0.0.1" 'echo X' >/tmp/ezkey-f9-match-ssh.log 2>&1
set -e

CREATES_MATCH="$(count_creates)"
CREATES_MATCH="${CREATES_MATCH:-0}"
echo "match creates=${CREATES_MATCH}" | tee -a "${PROOF}"
if [ "${CREATES_MATCH}" -eq 1 ]; then
  ok "Match User MaxAuthTries 1 → exactly 1 create (option A)"
else
  fail "expected exactly 1 create with Match MaxAuthTries 1, got ${CREATES_MATCH}"
fi

# --- Case C (B2b): kill client during pending /wait → cancel ≤5 s ---
# H1 instrument: debug.err must show peer FIN / no SIGTERM (sshd does not signal).
start_stub pending
point_conf_at_stub 30
install_sshd_cfg "${SCRIPT_DIR}/../sshd_config"
docker exec "${CONTAINER}" sh -c \
  ": >'${STUB_REQ_LOG}'; : > /var/log/ezkey-pam/debug.err"

set +e
ssh -tt -p "${PORT}" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  -o PreferredAuthentications=keyboard-interactive -o PubkeyAuthentication=no \
  -o PasswordAuthentication=no -o NumberOfPasswordPrompts=1 -o ConnectTimeout=10 \
  -o ServerAliveInterval=2 -o ServerAliveCountMax=60 \
  "${SSH_USER}@127.0.0.1" 'echo X' >/tmp/ezkey-f9-kill-ssh.log 2>&1 &
SSH_PID=$!
set -e

# Wait until create is posted (module entered /wait soon after).
CREATED=0
for _ in $(seq 1 80); do
  CREATED="$(count_creates)"
  CREATED="${CREATED:-0}"
  if [ "${CREATED}" -ge 1 ]; then
    break
  fi
  sleep 0.1
done
echo "kill-during-wait creates_before_kill=${CREATED}" | tee -a "${PROOF}"
if [ "${CREATED}" -lt 1 ]; then
  kill "${SSH_PID}" 2>/dev/null || true
  wait "${SSH_PID}" 2>/dev/null || true
  fail "B2b: no create before kill (stub/pending path)"
else
  # Brief settle so curl is inside chunked /wait, then kill the SSH client.
  sleep 0.5
  kill "${SSH_PID}" 2>/dev/null || true
  KILL_TS="$(date +%s)"
  CANCEL_SEEN=0
  CANCEL_ELAPSED=99
  for _ in $(seq 1 60); do
    CN="$(count_cancels)"
    CN="${CN:-0}"
    if [ "${CN}" -ge 1 ]; then
      NOW="$(date +%s)"
      CANCEL_ELAPSED=$((NOW - KILL_TS))
      CANCEL_SEEN=1
      break
    fi
    sleep 0.1
  done
  wait "${SSH_PID}" 2>/dev/null || true
  echo "kill-during-wait cancel_seen=${CANCEL_SEEN} elapsed_s=${CANCEL_ELAPSED}" \
    | tee -a "${PROOF}"
  docker exec "${CONTAINER}" cat "${STUB_REQ_LOG}" 2>/dev/null | tee -a "${PROOF}" || true
  ERR="$(docker exec "${CONTAINER}" sh -c 'cat /var/log/ezkey-pam/debug.err' 2>/dev/null || true)"
  echo "B2b H1 debug.err=${ERR}" | tee -a "${PROOF}"
  # Filelog sanitizes spaces → '_' (and other chars); match the stable token.
  if [ "${CANCEL_SEEN}" -eq 1 ] && [ "${CANCEL_ELAPSED}" -le 5 ] \
    && echo "${ERR}" | grep -Eqi 'tcp.peer.FIN.*no.SIGTERM|tcp_peer_FIN.*no_SIGTERM'; then
    ok "B2b: client killed during wait → cancel in ${CANCEL_ELAPSED}s (≤5); H1 peer FIN (no SIGTERM)"
  else
    fail "B2b: expected cancel ≤5s + peer FIN debug, seen=${CANCEL_SEEN} elapsed=${CANCEL_ELAPSED} err=${ERR}"
  fi
fi

echo "== Results: pass=${PASS} fail=${FAIL} ==" | tee -a "${PROOF}"
if [ "${FAIL}" -ne 0 ]; then
  exit 1
fi
exit 0
