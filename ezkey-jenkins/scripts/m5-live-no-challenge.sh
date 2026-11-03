#!/usr/bin/env bash
# Live matrix A: enrollment without challenge required + realm forceChallenge=false.
# Requires existing m5 env (dedicated integration/enrollment). Patches enrollment then demos approve.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export EZKEY_JENKINS_ROOT="${ROOT}"
# shellcheck source=lib/demo-device-respond.sh
source "${ROOT}/scripts/lib/demo-device-respond.sh"
# shellcheck source=lib/lab-temp.sh
source "${ROOT}/scripts/lib/lab-temp.sh"

ENV_FILE="${ROOT}/.ezkey/m5-integration.env"
if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Missing ${ENV_FILE}. Run ./scripts/m5-provision-lab-integration.sh first." >&2
  exit 1
fi
# shellcheck disable=SC1090
source "${ENV_FILE}"

ADMIN_API="${EZKEY_ADMIN_API_URL:-http://127.0.0.1:9080}"
ADMIN_ENROLLMENT_ID="${EZKEY_ADMIN_ENROLLMENT_ID:-1}"
ENROLLMENT_ID="${EZKEY_JENKINS_ENROLLMENT_ID:?}"
USERNAME="${EZKEY_JENKINS_MFA_USER:-labuser}"

echo "Admin two-call login (system enrollment ${ADMIN_ENROLLMENT_ID}) ..."
RESP="$(curl -fsS -m 30 -X POST "${ADMIN_API}/api/v1/admin/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin.docker","challengeRequested":true}')"
AID="$(printf '%s' "$RESP" | python3 -c "import json,sys; print(json.load(sys.stdin)['authAttemptId'])")"
CHAL="$(printf '%s' "$RESP" | python3 -c "import json,sys; print(json.load(sys.stdin)['challengeCode'])")"
WAIT="$(printf '%s' "$RESP" | python3 -c "import json,sys; print(json.load(sys.stdin)['waiterSecret'])")"
demo_device_respond "${ADMIN_ENROLLMENT_ID}" true "${CHAL}"
WAIT_JSON="$(curl -fsS -m 60 -X POST "${ADMIN_API}/api/v1/admin/auth/passwordless-wait" \
  -H 'Content-Type: application/json' \
  -d "{\"authAttemptId\":${AID},\"challengeCode\":${CHAL},\"waiterSecret\":\"${WAIT}\"}")"
TOKEN="$(printf '%s' "$WAIT_JSON" | python3 -c "import json,sys; print(json.load(sys.stdin)['token'])")"

ENROLL_JSON="$(curl -fsS -H "Authorization: Bearer ${TOKEN}" \
  "${ADMIN_API}/api/v1/enrollments/${ENROLLMENT_ID}")"
VER="$(printf '%s' "$ENROLL_JSON" | python3 -c "import json,sys; print(json.load(sys.stdin)['version'])")"
curl -fsS -X PATCH "${ADMIN_API}/api/v1/enrollments/${ENROLLMENT_ID}" \
  -H "Authorization: Bearer ${TOKEN}" -H 'Content-Type: application/json' \
  -d "{\"version\":${VER},\"authAttemptChallengeRequired\":false}" >/dev/null
echo "Enrollment ${ENROLLMENT_ID} authAttemptChallengeRequired=false (matrix A)."

export EZKEY_JENKINS_FORCE_CHALLENGE=false
./scripts/configure-mock-auth.sh

export EZKEY_JENKINS_EXPECT_CHALLENGE=false
export EZKEY_JENKINS_ENROLLMENT_ID="${ENROLLMENT_ID}"
export EZKEY_JENKINS_MFA_USER="${USERNAME}"
./scripts/m4-live-demo.sh approve

echo "M5 live no-challenge (matrix A) OK"
