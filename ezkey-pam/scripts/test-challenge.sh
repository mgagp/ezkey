#!/usr/bin/env bash
# M1 two-digit challenge (number matching): SSH shows Ezkey challenge code via
# pam_info/PAM_TEXT_INFO; Demo Device approve with the same digits → SUCCESS.
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
trap restore_conf EXIT

# Latest PENDING/READ attempt challenge for this enrollment (lab DB; not a product API).
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

echo "=== M1 two-digit challenge (${CONTAINER}, enrollment ${ENROLLMENT_ID}) ===" \
  | tee "${PROOF_LOG}"

clear_stale_pending

docker exec "${CONTAINER}" cp -a "${CONF}" "${BACKUP}"
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^challenge_requested=.*/challenge_requested=true/' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=60/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'; grep -E 'challenge_requested|wait_timeout' '${CONF}'"

: >"${SSH_LOG}"

# Start SSH under a flushing script PTY (captures PAM_TEXT_INFO).
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

# Wait until DB shows a challenged pending attempt (create completed).
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
  restore_conf
  echo "=== Results: $PASS passed, $FAIL failed ===" | tee -a "${PROOF_LOG}"
  exit 1
fi
echo "db: authAttemptId=${ATTEMPT_ID} challenge=${CHALLENGE}" | tee -a "${PROOF_LOG}"

echo "Approving with challenge=${CHALLENGE}" | tee -a "${PROOF_LOG}"
"${SCRIPT_DIR}/respond-pending.sh" approve "${ENROLLMENT_ID}" "${CHALLENGE}" \
  >"${ARTIFACT_DIR}/pam-challenge-approve.log" 2>&1
ok "Demo Device approve with matching challenge submitted"

set +e
wait "${SSH_BG}"
SSH_RC=$?
set -e
restore_conf

FLAT="$(tr -d '\r' <"${SSH_LOG}" 2>/dev/null || true)"
{
  echo "--- SSH typescript ---"
  echo "${FLAT}"
  echo "--- end ---"
  echo "ssh_rc=${SSH_RC}"
} >>"${PROOF_LOG}"

# Assert conversation after the session so script(1) flush is complete.
SHOWN=""
if echo "${FLAT}" | grep -qE 'Ezkey challenge code: [0-9]+'; then
  raw="$(echo "${FLAT}" | grep -oE 'Ezkey challenge code: [0-9]+' | tail -n 1 | awk '{print $NF}')"
  if [ "$((10#${raw}))" -eq "$((10#${CHALLENGE}))" ]; then
    SHOWN="Ezkey challenge code: ${raw}"
  fi
fi
if [ -n "${SHOWN}" ]; then
  ok "SSH conversation shows challenge matching DB (${SHOWN})"
else
  fail "SSH conversation missing challenge ${CHALLENGE}"
fi

if [ "${SSH_RC}" -eq 0 ] && echo "${FLAT}" | grep -q PAM_CHALLENGE_OK; then
  ok "SSH session succeeded after challenge match + approve"
else
  fail "SSH did not complete PAM_CHALLENGE_OK (rc=${SSH_RC})"
  cat "${ARTIFACT_DIR}/pam-challenge-approve.log" >>"${PROOF_LOG}" || true
fi

ATTEMPT="$(docker exec "${CONTAINER}" sh -c \
  "grep 'pam_ezkey: attempt ' /var/log/secure | tail -n 1" || true)"
echo "attempt_line=${ATTEMPT}" >>"${PROOF_LOG}"
if echo "${ATTEMPT}" | grep -q "authAttemptId=${ATTEMPT_ID}" \
  && echo "${ATTEMPT}" | grep -q 'result=SUCCESS'; then
  ok "attempt line SUCCESS for authAttemptId=${ATTEMPT_ID}"
else
  fail "expected SUCCESS for id=${ATTEMPT_ID}: ${ATTEMPT}"
fi
if echo "${ATTEMPT}" | grep -q 'lab_http=true'; then
  ok "attempt line includes lab_http=true (Docker lab)"
else
  fail "expected lab_http=true on attempt line: ${ATTEMPT}"
fi
if echo "${ATTEMPT}" | grep -Eiq 'challenge code'; then
  fail "challenge UX text leaked into attempt syslog line"
else
  ok "attempt syslog line has no challenge UX text"
fi

# Lab DB proof (Marc): ACCEPTED + stored challenge digits — no physical phone.
DB_PROOF="${ARTIFACT_DIR}/pam-challenge-db.txt"
docker exec -e PGPASSWORD=ezkey ezkey-postgres \
  psql -U postgres -d ezkey_db -c \
  "SELECT auth_attempt_id, enrollment_id, auth_attempt_status, auth_attempt_challenge,
          context_title, created_at
   FROM ezkey_auth_attempt
   WHERE auth_attempt_id = ${ATTEMPT_ID};" \
  | tee "${DB_PROOF}" | tee -a "${PROOF_LOG}"
DB_ROW="$(docker exec -e PGPASSWORD=ezkey ezkey-postgres \
  psql -U postgres -d ezkey_db -At -c \
  "SELECT auth_attempt_status || '|' || COALESCE(auth_attempt_challenge::text,'')
   FROM ezkey_auth_attempt WHERE auth_attempt_id = ${ATTEMPT_ID};" 2>/dev/null || true)"
if [ "${DB_ROW}" = "ACCEPTED|${CHALLENGE}" ]; then
  ok "DB row ACCEPTED with challenge=${CHALLENGE} (authAttemptId=${ATTEMPT_ID})"
else
  fail "DB expected ACCEPTED|${CHALLENGE}, got '${DB_ROW}'"
fi

echo "" | tee -a "${PROOF_LOG}"
echo "=== Results: $PASS passed, $FAIL failed ===" | tee -a "${PROOF_LOG}"
echo "Artifacts: ${SSH_LOG} ${PROOF_LOG} ${ARTIFACT_DIR}/pam-challenge-approve.log ${DB_PROOF}" \
  | tee -a "${PROOF_LOG}"
[ "${FAIL}" -eq 0 ]
