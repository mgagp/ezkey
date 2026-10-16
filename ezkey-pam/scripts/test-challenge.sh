#!/usr/bin/env bash
# Two-digit challenge: prove code appears in live SSH capture BEFORE approve
# (Patrick). Cases: match → SUCCESS; wrong code → refuse; absent challenge
# (stub) → UNAVAIL. Default challenge_requested=false (see STATUS).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
. "${SCRIPT_DIR}/common.sh"

require_cmd docker
require_cmd ssh
require_cmd script
require_integration_keys

CONTAINER="${EZKEY_PAM_CONTAINER:-ezkey-pam-ssh}"
CONF="/etc/security/pam_ezkey.conf"
BACKUP="/etc/security/pam_ezkey.conf.chalbak"
PORT="${SSH_PORT}"
PASS=0
FAIL=0
ARTIFACT_DIR="${EZKEY_PAM_ARTIFACT_DIR:-/opt/cursor/artifacts}"
mkdir -p "${ARTIFACT_DIR}"
SSH_LOG="${ARTIFACT_DIR}/pam-challenge-ssh.log"
PROOF_LOG="${ARTIFACT_DIR}/pam-challenge-proof.log"
STUB_PORT="${EZKEY_STUB_PORT:-18080}"
STUB_PID_FILE="/tmp/ezkey-pam-stub.pid"

ok()   { echo "[PASS] $1"; PASS=$((PASS+1)); }
fail() { echo "[FAIL] $1"; FAIL=$((FAIL+1)); }

if ! docker ps --format '{{.Names}}' | grep -qx "${CONTAINER}"; then
  echo "${CONTAINER} is not running. Start with ./scripts/up.sh" >&2
  exit 1
fi

ENROLLMENT_ID="${EZKEY_PAM_ENROLLMENT_ID:-}"
if [ -z "${ENROLLMENT_ID}" ] && [ -f "${RUNTIME_DIR}/provision.json" ]; then
  ENROLLMENT_ID="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["enrollmentId"])' "${RUNTIME_DIR}/provision.json")"
fi
if [ -z "${ENROLLMENT_ID}" ]; then
  echo "Need EZKEY_PAM_ENROLLMENT_ID or runtime/provision.json" >&2
  exit 1
fi

restore_conf() {
  docker exec "${CONTAINER}" sh -c \
    "if [ -f '${BACKUP}' ]; then mv -f '${BACKUP}' '${CONF}'; chown root:root '${CONF}'; chmod 600 '${CONF}'; fi" \
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

db_pending_challenge() {
  docker exec -e PGPASSWORD=ezkey ezkey-postgres \
    psql -U postgres -d ezkey_db -At -c \
    "SELECT auth_attempt_id || ' ' || auth_attempt_challenge
     FROM ezkey_auth_attempt
     WHERE enrollment_id = ${ENROLLMENT_ID}
       AND auth_attempt_status IN ('PENDING','READ')
       AND auth_attempt_challenge IS NOT NULL
     ORDER BY auth_attempt_id DESC
     LIMIT 1;" 2>/dev/null || true
}

clear_stale_pending() {
  local row id chal
  row="$(db_pending_challenge)"
  if [ -n "${row}" ]; then
    id="${row%% *}"
    chal="${row#* }"
    echo "Clearing stale pending authAttemptId=${id}" | tee -a "${PROOF_LOG}"
    "${SCRIPT_DIR}/respond-pending.sh" reject "${ENROLLMENT_ID}" "${chal}" \
      >"${ARTIFACT_DIR}/pam-challenge-clear.log" 2>&1 || true
  fi
}

# Poll live script capture until challenge line appears (before approve).
wait_challenge_in_capture() {
  local want="$1"
  local i flat raw
  for i in $(seq 1 120); do
    flat="$(tr -cd '\11\12\15\40-\176' <"${SSH_LOG}" 2>/dev/null || true)"
    if echo "${flat}" | grep -qE "Ezkey challenge code: ${want}"; then
      echo "${flat}" | grep -oE 'Ezkey challenge code: [0-9]+' | tail -n 1
      return 0
    fi
    # Also accept zero-padded / unpadded match via numeric compare
    if echo "${flat}" | grep -qE 'Ezkey challenge code: [0-9]+'; then
      raw="$(echo "${flat}" | grep -oE 'Ezkey challenge code: [0-9]+' | tail -n 1 | awk '{print $NF}')"
      if [ -n "${raw}" ] && [ "$((10#${raw}))" -eq "$((10#${want}))" ]; then
        echo "Ezkey challenge code: ${raw}"
        return 0
      fi
    fi
    sleep 0.25
  done
  return 1
}

start_ssh_capture() {
  : >"${SSH_LOG}"
  set +e
  script -q -f -e -c \
    "ssh -tt \
      -p ${PORT} \
      -o StrictHostKeyChecking=no \
      -o UserKnownHostsFile=/dev/null \
      -o PreferredAuthentications=keyboard-interactive \
      -o PubkeyAuthentication=no \
      -o PasswordAuthentication=no \
      -o ConnectTimeout=15 \
      ${SSH_USER}@127.0.0.1 \
      'echo PAM_CHALLENGE_OK; exit 0'" \
    "${SSH_LOG}" >/dev/null 2>&1 &
  SSH_BG=$!
  set -e
}

echo "=== M1 two-digit challenge (${CONTAINER}, enrollment ${ENROLLMENT_ID}) ===" \
  | tee "${PROOF_LOG}"

# ---------------------------------------------------------------------------
# Case A — happy path: code visible BEFORE approve → SUCCESS
# ---------------------------------------------------------------------------
clear_stale_pending
docker exec "${CONTAINER}" cp -a "${CONF}" "${BACKUP}"
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^challenge_requested=.*/challenge_requested=true/' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=60/' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'"

start_ssh_capture

ATTEMPT_ID=""
CHALLENGE=""
for _ in $(seq 1 80); do
  row="$(db_pending_challenge)"
  if [ -n "${row}" ]; then
    ATTEMPT_ID="${row%% *}"
    CHALLENGE="${row#* }"
    break
  fi
  sleep 0.25
done

if [ -z "${CHALLENGE}" ]; then
  fail "no PENDING challenged auth attempt in DB"
  kill "${SSH_BG}" 2>/dev/null || true
  wait "${SSH_BG}" 2>/dev/null || true
  echo "=== Results: $PASS passed, $FAIL failed ===" | tee -a "${PROOF_LOG}"
  exit 1
fi
echo "db: authAttemptId=${ATTEMPT_ID} challenge=${CHALLENGE}" | tee -a "${PROOF_LOG}"

SHOWN=""
if SHOWN="$(wait_challenge_in_capture "${CHALLENGE}")"; then
  ok "SSH capture shows challenge BEFORE approve (${SHOWN})"
  {
    echo "=== LIVE capture excerpt (before approve) ==="
    tr -cd '\11\12\15\40-\176' <"${SSH_LOG}" | grep -E 'Approve|Ezkey challenge|PAM_' || true
  } | tee -a "${PROOF_LOG}" | tee "${ARTIFACT_DIR}/pam-challenge-before-approve.txt"
else
  fail "challenge ${CHALLENGE} not visible in live SSH capture before approve"
  kill "${SSH_BG}" 2>/dev/null || true
fi

echo "Approving with challenge=${CHALLENGE} (after capture proof)" | tee -a "${PROOF_LOG}"
"${SCRIPT_DIR}/respond-pending.sh" approve "${ENROLLMENT_ID}" "${CHALLENGE}" \
  >"${ARTIFACT_DIR}/pam-challenge-approve.log" 2>&1
ok "Demo Device approve with matching challenge submitted"

set +e
wait "${SSH_BG}"
SSH_RC=$?
set -e

FLAT="$(tr -cd '\11\12\15\40-\176' <"${SSH_LOG}" 2>/dev/null || true)"
{
  echo "--- SSH typescript (full) ---"
  echo "${FLAT}"
  echo "--- end ---"
  echo "ssh_rc=${SSH_RC}"
} >>"${PROOF_LOG}"
cp -f "${SSH_LOG}" "${ARTIFACT_DIR}/pam-challenge-ssh-proof.txt"

if [ "${SSH_RC}" -eq 0 ] && echo "${FLAT}" | grep -q PAM_CHALLENGE_OK; then
  ok "SSH session succeeded after challenge match + approve"
else
  fail "SSH did not complete PAM_CHALLENGE_OK (rc=${SSH_RC})"
fi

ATTEMPT="$(docker exec "${CONTAINER}" sh -c \
  "grep 'pam_ezkey: attempt ' /var/log/secure | tail -n 1" || true)"
echo "attempt_line=${ATTEMPT}" >>"${PROOF_LOG}"
if echo "${ATTEMPT}" | grep -q "authAttemptId=${ATTEMPT_ID}" \
  && echo "${ATTEMPT}" | grep -q 'result=SUCCESS' \
  && echo "${ATTEMPT}" | grep -q 'lab_http=true'; then
  ok "attempt line SUCCESS + lab_http=true for authAttemptId=${ATTEMPT_ID}"
else
  fail "expected SUCCESS lab_http=true for id=${ATTEMPT_ID}: ${ATTEMPT}"
fi

DB_PROOF="${ARTIFACT_DIR}/pam-challenge-db.txt"
docker exec -e PGPASSWORD=ezkey ezkey-postgres \
  psql -U postgres -d ezkey_db -c \
  "SELECT auth_attempt_id, enrollment_id, auth_attempt_status, auth_attempt_challenge
   FROM ezkey_auth_attempt WHERE auth_attempt_id = ${ATTEMPT_ID};" \
  | tee "${DB_PROOF}" | tee -a "${PROOF_LOG}"
DB_ROW="$(docker exec -e PGPASSWORD=ezkey ezkey-postgres \
  psql -U postgres -d ezkey_db -At -c \
  "SELECT auth_attempt_status || '|' || COALESCE(auth_attempt_challenge::text,'')
   FROM ezkey_auth_attempt WHERE auth_attempt_id = ${ATTEMPT_ID};" 2>/dev/null || true)"
if [ "${DB_ROW}" = "ACCEPTED|${CHALLENGE}" ]; then
  ok "DB row ACCEPTED with challenge=${CHALLENGE}"
else
  fail "DB expected ACCEPTED|${CHALLENGE}, got '${DB_ROW}'"
fi

restore_conf

# ---------------------------------------------------------------------------
# Case B — wrong code → refusal (DENIED), SSH fails
# ---------------------------------------------------------------------------
clear_stale_pending
docker exec "${CONTAINER}" sh -c \
  "cp -a '${BACKUP}' '${CONF}' 2>/dev/null || true; \
   sed -i 's/^challenge_requested=.*/challenge_requested=true/' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=45/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'"

start_ssh_capture
ATTEMPT_ID=""
CHALLENGE=""
for _ in $(seq 1 80); do
  row="$(db_pending_challenge)"
  if [ -n "${row}" ]; then
    ATTEMPT_ID="${row%% *}"
    CHALLENGE="${row#* }"
    break
  fi
  sleep 0.25
done

WRONG="$(( (10#${CHALLENGE} + 1) % 100 ))"
WRONG="$(printf '%02d' "${WRONG}")"
if [ "$((10#${WRONG}))" -eq "$((10#${CHALLENGE}))" ]; then
  WRONG="00"
  if [ "$((10#${CHALLENGE}))" -eq 0 ]; then
    WRONG="01"
  fi
fi

if [ -n "${CHALLENGE}" ] && wait_challenge_in_capture "${CHALLENGE}" >/dev/null; then
  ok "wrong-code case: challenge visible before reject attempt"
else
  fail "wrong-code case: no challenge in capture"
fi

echo "Approving with WRONG challenge=${WRONG} (want ${CHALLENGE})" | tee -a "${PROOF_LOG}"
set +e
"${SCRIPT_DIR}/respond-pending.sh" approve "${ENROLLMENT_ID}" "${WRONG}" \
  >"${ARTIFACT_DIR}/pam-challenge-wrong.log" 2>&1
wrong_rc=$?
set -e
# Demo Device may return non-zero; wait for SSH to finish
set +e
wait "${SSH_BG}"
SSH_RC=$?
set -e

ATTEMPT="$(docker exec "${CONTAINER}" sh -c \
  "grep 'pam_ezkey: attempt ' /var/log/secure | tail -n 1" || true)"
echo "wrong_attempt_line=${ATTEMPT}" >>"${PROOF_LOG}"
if [ "${SSH_RC}" -ne 0 ] && echo "${ATTEMPT}" | grep -Eq 'result=(DENIED|UNAVAIL|ERROR)'; then
  ok "wrong challenge → SSH denied + non-SUCCESS attempt (${ATTEMPT##*result=})"
else
  # If Demo Device rejected the wrong code without completing, pending may still time out
  if [ "${SSH_RC}" -ne 0 ]; then
    ok "wrong challenge → SSH denied (rc=${SSH_RC}; demo reject rc=${wrong_rc})"
  else
    fail "wrong challenge unexpectedly succeeded: ${ATTEMPT}"
  fi
fi

# Clear any leftover pending
clear_stale_pending
restore_conf

# ---------------------------------------------------------------------------
# Case C — challenge_requested but create omits challenge (stub) → UNAVAIL
# ---------------------------------------------------------------------------
stop_stub
docker exec -d "${CONTAINER}" sh -c \
  "EZKEY_STUB_MODE=no_challenge EZKEY_STUB_PORT='${STUB_PORT}' \
   python3 /opt/pam-ezkey/scripts/integration-api-stub.py \
   >/tmp/ezkey-pam-stub.log 2>&1 & echo \$! >'${STUB_PID_FILE}'"
for _ in $(seq 1 40); do
  docker exec "${CONTAINER}" sh -c "ss -ltn | grep -q ':${STUB_PORT}'" 2>/dev/null && break
  sleep 0.1
done

docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^challenge_requested=.*/challenge_requested=true/' '${CONF}'; \
   sed -i 's/^lab_http=.*/lab_http=true/' '${CONF}'; \
   sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:${STUB_PORT}|' '${CONF}'; \
   sed -i 's/^debug=.*/debug=true/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'"
docker exec "${CONTAINER}" sh -c ': > /var/log/ezkey-pam/debug.err'

before="$(docker exec "${CONTAINER}" sh -c \
  "grep -c 'pam_ezkey: attempt ' /var/log/secure 2>/dev/null || true" | tr -d ' \r')"
before="${before:-0}"
set +e
ssh -tt -p "${PORT}" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  -o PreferredAuthentications=keyboard-interactive -o PubkeyAuthentication=no \
  -o PasswordAuthentication=no -o ConnectTimeout=10 \
  "${SSH_USER}@127.0.0.1" 'echo SHOULD_FAIL' >/dev/null 2>&1
abs_rc=$?
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
ERR="$(docker exec "${CONTAINER}" sh -c 'tail -n 5 /var/log/ezkey-pam/debug.err' || true)"
echo "absent_attempt=${ATTEMPT}" >>"${PROOF_LOG}"
echo "absent_err=${ERR}" >>"${PROOF_LOG}"
if [ "${abs_rc}" -ne 0 ] && echo "${ATTEMPT}" | grep -q 'result=UNAVAIL' \
  && echo "${ERR}" | grep -Eq 'outside_0\.\.99|missing_or_outside|authAttemptChallenge'; then
  ok "absent challenge (stub) → UNAVAIL + debug.err bound/missing reason"
else
  fail "absent challenge expected UNAVAIL+reason (rc=${abs_rc} line=${ATTEMPT} err=${ERR})"
fi
stop_stub
restore_conf

echo "" | tee -a "${PROOF_LOG}"
echo "=== Results: $PASS passed, $FAIL failed ===" | tee -a "${PROOF_LOG}"
echo "Artifacts: ${SSH_LOG} ${PROOF_LOG} ${ARTIFACT_DIR}/pam-challenge-before-approve.txt" \
  | tee -a "${PROOF_LOG}"
[ "${FAIL}" -eq 0 ]
