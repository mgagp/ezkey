#!/usr/bin/env bash
# Provision a System Tenant integration, API key, and testuser enrollment
# bound to the Demo Device. Writes ezkey-pam/.env for the SSH demo VM.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
. "${SCRIPT_DIR}/common.sh"

require_cmd curl
require_cmd python3
require_cmd docker

COOKIE_JAR="${RUNTIME_DIR}/demo-device.cookies"
STATE_FILE="${RUNTIME_DIR}/provision.json"
TOKEN_FILE="${RUNTIME_DIR}/admin-token.txt"

wait_http_ok "${ADMIN_HEALTH_URL}" 90
wait_http_ok "${DEMO_DEVICE_HEALTH_URL}" 60

find_bootstrap_enrollment_id() {
  local html
  html="$(curl -fsS "${DEMO_DEVICE_URL}/phone/ezkey")"
  python3 - "$html" <<'PY'
import re, sys
html = sys.argv[1]
ids = re.findall(r'data-enrollment-id="(\d+)"', html)
if not ids:
    sys.exit(1)
print(ids[0])
PY
}

demo_approve_pending() {
  local enrollment_id="$1"
  local html
  local attempt_id
  local proof
  local i

  rm -f "${COOKIE_JAR}"
  for i in $(seq 1 20); do
    html="$(curl -fsS -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" \
      "${DEMO_DEVICE_URL}/phone/ezkey/enrollments/${enrollment_id}/auth")"
    if echo "${html}" | grep -q 'data-testid="demo-device-auth-pending"'; then
      attempt_id="$(html_input_value "${html}" authAttemptId)"
      proof="$(html_input_value "${html}" authAttemptProofToken)"
      curl -fsS -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" \
        -X POST \
        -H "Content-Type: application/x-www-form-urlencoded" \
        --data-urlencode "authAttemptId=${attempt_id}" \
        --data-urlencode "authAttemptProofToken=${proof}" \
        --data-urlencode "approved=true" \
        "${DEMO_DEVICE_URL}/phone/ezkey/enrollments/${enrollment_id}/auth/respond" \
        >/dev/null
      echo "${attempt_id}"
      return 0
    fi
    sleep 2
  done
  echo "No pending Demo Device request for enrollment ${enrollment_id}" >&2
  return 1
}

echo "== Obtaining Global Admin token via passwordless login + Demo Device =="
BOOTSTRAP_ENROLLMENT_ID="$(find_bootstrap_enrollment_id)"
echo "Bootstrap Demo Device enrollment id: ${BOOTSTRAP_ENROLLMENT_ID}"

LOGIN_JSON="$(curl -fsS -X POST "${ADMIN_API_URL}/api/v1/admin/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"admin.docker\",\"challengeRequested\":false,\"nonBlocking\":true}")"

AUTH_ATTEMPT_ID="$(json_get "${LOGIN_JSON}" authAttemptId)"
WAITER_SECRET="$(json_get "${LOGIN_JSON}" waiterSecret)"
echo "Admin login pending, authAttemptId=${AUTH_ATTEMPT_ID}"

demo_approve_pending "${BOOTSTRAP_ENROLLMENT_ID}" >/dev/null

WAIT_JSON="$(curl -fsS -X POST "${ADMIN_API_URL}/api/v1/admin/auth/passwordless-wait" \
  -H "Content-Type: application/json" \
  -d "{\"authAttemptId\":${AUTH_ATTEMPT_ID},\"waiterSecret\":\"${WAITER_SECRET}\"}")"

ADMIN_TOKEN="$(json_get "${WAIT_JSON}" token)"
if [ -z "${ADMIN_TOKEN}" ] || [ "${ADMIN_TOKEN}" = "None" ]; then
  echo "Failed to obtain admin token. Response:" >&2
  echo "${WAIT_JSON}" >&2
  exit 1
fi
printf '%s\n' "${ADMIN_TOKEN}" > "${TOKEN_FILE}"
chmod 600 "${TOKEN_FILE}"
echo "Admin token saved to ${TOKEN_FILE}"

auth_header() {
  echo "Authorization: Bearer ${ADMIN_TOKEN}"
}

echo "== Creating PAM SSH integration on the default (system) tenant =="
INTEG_CODE="pam-ssh-$(date +%Y%m%d%H%M%S)"
INTEG_JSON="$(curl -fsS -X POST "${ADMIN_API_URL}/api/v1/integrations" \
  -H "Content-Type: application/json" \
  -H "$(auth_header)" \
  -d "{\"code\":\"${INTEG_CODE}\",\"name\":\"PAM SSH Demo\",\"description\":\"Linux SSH login via Ezkey PAM\"}")"
INTEGRATION_ID="$(json_get "${INTEG_JSON}" id)"
echo "Integration id=${INTEGRATION_ID} code=${INTEG_CODE}"

echo "== Creating API key =="
KEY_JSON="$(curl -fsS -X POST "${ADMIN_API_URL}/api/v1/api-keys" \
  -H "Content-Type: application/json" \
  -H "$(auth_header)" \
  -d "{\"integrationId\":${INTEGRATION_ID},\"description\":\"Ezkey PAM SSH demo key\"}")"
INTEGRATION_KEY="$(json_get "${KEY_JSON}" integrationKey)"
SECRET_KEY="$(json_get "${KEY_JSON}" secretKey)"
API_KEY_ID="$(json_get "${KEY_JSON}" apiKeyId)"
echo "API key id=${API_KEY_ID}"

echo "== Creating enrollment userIdentifier=${SSH_USER} =="
ENROLL_CREATE="$(curl -fsS -X POST "${ADMIN_API_URL}/api/v1/enrollments" \
  -H "Content-Type: application/json" \
  -H "$(auth_header)" \
  -d "{\"integrationId\":${INTEGRATION_ID},\"name\":\"SSH ${SSH_USER} (PAM demo)\",\"authAttemptChallengeRequired\":false,\"userIdentifier\":\"${SSH_USER}\"}")"
ENROLLMENT_ID="$(json_get "${ENROLL_CREATE}" enrollmentId)"
ENROLLMENT_CHALLENGE="$(json_get "${ENROLL_CREATE}" enrollmentChallenge)"

ENROLL_GET="$(curl -fsS "${ADMIN_API_URL}/api/v1/enrollments/${ENROLLMENT_ID}" \
  -H "$(auth_header)")"
PROOF_TOKEN="$(json_get "${ENROLL_GET}" enrollmentProofToken)"
echo "Enrollment id=${ENROLLMENT_ID} challenge=${ENROLLMENT_CHALLENGE}"

echo "== Binding enrollment on Demo Device =="
rm -f "${COOKIE_JAR}"
BIND_HTML="$(curl -fsS -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" \
  -X POST \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "enrollmentId=${ENROLLMENT_ID}" \
  --data-urlencode "enrollmentProofToken=${PROOF_TOKEN}" \
  "${DEMO_DEVICE_URL}/phone/ezkey/enrollment/bind")"
if ! echo "${BIND_HTML}" | grep -q 'data-testid="demo-device-bind-success"'; then
  echo "Demo Device bind failed:" >&2
  echo "${BIND_HTML}" >&2
  exit 1
fi

VERIFY_HTML="$(curl -fsS -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" \
  -X POST \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "enrollmentId=${ENROLLMENT_ID}" \
  --data-urlencode "enrollmentProofToken=${PROOF_TOKEN}" \
  --data-urlencode "challengeResponse=${ENROLLMENT_CHALLENGE}" \
  "${DEMO_DEVICE_URL}/phone/ezkey/enrollment/verify")"
if ! echo "${VERIFY_HTML}" | grep -q 'data-testid="demo-device-verify-success"'; then
  echo "Demo Device verify failed:" >&2
  echo "${VERIFY_HTML}" >&2
  exit 1
fi
echo "Enrollment ${ENROLLMENT_ID} is VERIFIED on Demo Device"

NETWORK="$(detect_ezkey_network)"
cat > "${ENV_FILE}" <<EOF
EZKEY_INTEGRATION_API_URL=http://integration-api:7080
EZKEY_INTEGRATION_KEY=${INTEGRATION_KEY}
EZKEY_SECRET_KEY=${SECRET_KEY}
EZKEY_SSH_USER=${SSH_USER}
EZKEY_WAIT_TIMEOUT=90
EZKEY_PAM_SSH_PORT=${SSH_PORT}
EZKEY_CONTEXT_TITLE="SSH login"
EZKEY_DOCKER_NETWORK=${NETWORK}
EZKEY_PAM_ENROLLMENT_ID=${ENROLLMENT_ID}
EZKEY_PAM_INTEGRATION_ID=${INTEGRATION_ID}
EOF
chmod 600 "${ENV_FILE}"

python3 - "${STATE_FILE}" "${INTEGRATION_ID}" "${INTEG_CODE}" "${API_KEY_ID}" \
  "${ENROLLMENT_ID}" "${SSH_USER}" "${NETWORK}" <<'PY'
import json, sys
path = sys.argv[1]
data = {
  "integrationId": int(sys.argv[2]),
  "integrationCode": sys.argv[3],
  "apiKeyId": int(sys.argv[4]),
  "enrollmentId": int(sys.argv[5]),
  "userIdentifier": sys.argv[6],
  "dockerNetwork": sys.argv[7],
}
with open(path, "w", encoding="utf-8") as fh:
    json.dump(data, fh, indent=2)
    fh.write("\n")
PY
chmod 600 "${STATE_FILE}"

echo ""
echo "Provision complete."
echo "  Integration: ${INTEGRATION_ID} (${INTEG_CODE})"
echo "  Enrollment:  ${ENROLLMENT_ID} userIdentifier=${SSH_USER}"
echo "  Env file:    ${ENV_FILE}"
echo "Next: ./scripts/up.sh"
