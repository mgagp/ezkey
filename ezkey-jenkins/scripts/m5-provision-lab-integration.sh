#!/usr/bin/env bash
# M5: provision a *dedicated* Jenkins lab integration + enrollment (not system enrollment #1).
# Requires Ezkey stack + Demo Device. Uses Admin two-call login (admin.docker on enrollment 1)
# only to obtain an Admin API token — lab MFA uses the new enrollment.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export EZKEY_JENKINS_ROOT="${ROOT}"
# shellcheck source=lib/demo-device-respond.sh
source "${ROOT}/scripts/lib/demo-device-respond.sh"
# shellcheck source=lib/demo-device-enroll.sh
source "${ROOT}/scripts/lib/demo-device-enroll.sh"
# shellcheck source=lib/lab-temp.sh
source "${ROOT}/scripts/lib/lab-temp.sh"

OUT_DIR="${ROOT}/.ezkey"
OUT_FILE="${OUT_DIR}/m5-integration.env"
ADMIN_API="${EZKEY_ADMIN_API_URL:-http://127.0.0.1:9080}"
USERNAME="${EZKEY_JENKINS_MFA_USER:-labuser}"
INTEGRATION_CODE="${EZKEY_JENKINS_INTEGRATION_CODE:-jenkins-lab}"
INTEGRATION_NAME="${EZKEY_JENKINS_INTEGRATION_NAME:-Jenkins Lab}"
ENROLLMENT_NAME="${EZKEY_JENKINS_ENROLLMENT_NAME:-Jenkins Lab ${USERNAME}}"
# Admin MFA enrollment (system) — used only for Admin API login, never for Jenkins MFA.
ADMIN_ENROLLMENT_ID="${EZKEY_ADMIN_ENROLLMENT_ID:-1}"
# Default true = matrix B (demo). Set false for matrix A (no-code enrollment).
CHALLENGE_REQUIRED_RAW="${EZKEY_JENKINS_ENROLLMENT_CHALLENGE_REQUIRED:-true}"
case "$(printf '%s' "${CHALLENGE_REQUIRED_RAW}" | tr '[:upper:]' '[:lower:]')" in
  0|false|no|off) CHALLENGE_REQUIRED=false ;;
  *) CHALLENGE_REQUIRED=true ;;
esac

mkdir -p "${OUT_DIR}"
echo "Enrollment challenge required: ${CHALLENGE_REQUIRED} (matrix $([[ "${CHALLENGE_REQUIRED}" == true ]] && echo B || echo A))"

echo "Admin two-call login (approve on Demo Device enrollment ${ADMIN_ENROLLMENT_ID}) ..."
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
test -n "${TOKEN}"
echo "Admin token acquired."

auth_hdr=(-H "Authorization: Bearer ${TOKEN}" -H 'Content-Type: application/json')

# --- Dedicated integration (reuse by code if already present) ---
INTEGRATION_ID="$(
  curl -fsS -H "Authorization: Bearer ${TOKEN}" \
    "${ADMIN_API}/api/v1/integrations?size=100" \
    | python3 -c "
import json,sys
code='${INTEGRATION_CODE}'
page=json.load(sys.stdin)
for row in page.get('content') or []:
    if row.get('code')==code:
        print(row['id']); break
"
)"

if [[ -z "${INTEGRATION_ID}" ]]; then
  CREATE_BODY="$(ezkey_lab_mktemp)"
  CODE="$(curl -sS -o "${CREATE_BODY}" -w '%{http_code}' -X POST "${ADMIN_API}/api/v1/integrations" \
    "${auth_hdr[@]}" \
    -d "{\"code\":\"${INTEGRATION_CODE}\",\"name\":\"${INTEGRATION_NAME}\",\"description\":\"Jenkins lab MFA (#972 M5)\"}")"
  if [[ "${CODE}" != "201" && "${CODE}" != "200" ]]; then
    echo "Failed to create integration HTTP ${CODE}" >&2
    cat "${CREATE_BODY}" >&2 || true
    rm -f "${CREATE_BODY}"
    exit 1
  fi
  INTEGRATION_ID="$(python3 -c "import json; print(json.load(open(r'''${CREATE_BODY}'''))['id'])")"
  rm -f "${CREATE_BODY}"
  echo "Created integration id=${INTEGRATION_ID} code=${INTEGRATION_CODE}"
else
  echo "Reusing integration id=${INTEGRATION_ID} code=${INTEGRATION_CODE}"
fi

# --- Dedicated enrollment (reuse VERIFIED labuser if present) ---
EXISTING="$(
  curl -fsS -H "Authorization: Bearer ${TOKEN}" \
    "${ADMIN_API}/api/v1/enrollments?integrationId=${INTEGRATION_ID}&size=50" \
    | python3 -c "
import json,sys
user='${USERNAME}'
page=json.load(sys.stdin)
for row in page.get('content') or []:
    if row.get('userIdentifier')==user and row.get('enrollmentStatus')=='VERIFIED':
        print(row['enrollmentId']); break
"
)"

if [[ -n "${EXISTING}" ]]; then
  ENROLLMENT_ID="${EXISTING}"
  echo "Reusing VERIFIED enrollment id=${ENROLLMENT_ID} userIdentifier=${USERNAME}"
  ENROLL_JSON="$(curl -fsS -H "Authorization: Bearer ${TOKEN}" \
    "${ADMIN_API}/api/v1/enrollments/${ENROLLMENT_ID}")"
  VER="$(printf '%s' "$ENROLL_JSON" | python3 -c "import json,sys; print(json.load(sys.stdin)['version'])")"
  CUR_CHAL="$(printf '%s' "$ENROLL_JSON" | python3 -c "import json,sys; print(json.load(sys.stdin).get('authAttemptChallengeRequired'))")"
  if [[ "${CUR_CHAL}" != "${CHALLENGE_REQUIRED}" ]]; then
    curl -fsS -X PATCH "${ADMIN_API}/api/v1/enrollments/${ENROLLMENT_ID}" \
      "${auth_hdr[@]}" \
      -d "{\"version\":${VER},\"authAttemptChallengeRequired\":${CHALLENGE_REQUIRED}}" >/dev/null
    echo "Patched enrollment ${ENROLLMENT_ID} authAttemptChallengeRequired=${CHALLENGE_REQUIRED}"
  fi
else
  ENROLL_BODY="$(ezkey_lab_mktemp)"
  ECODE="$(curl -sS -o "${ENROLL_BODY}" -w '%{http_code}' -X POST "${ADMIN_API}/api/v1/enrollments" \
    "${auth_hdr[@]}" \
    -d "{\"integrationId\":${INTEGRATION_ID},\"name\":\"${ENROLLMENT_NAME}\",\"authAttemptChallengeRequired\":${CHALLENGE_REQUIRED},\"userIdentifier\":\"${USERNAME}\"}")"
  if [[ "${ECODE}" != "201" && "${ECODE}" != "200" ]]; then
    echo "Failed to create enrollment HTTP ${ECODE}" >&2
    cat "${ENROLL_BODY}" >&2 || true
    rm -f "${ENROLL_BODY}"
    exit 1
  fi
  ENROLLMENT_ID="$(python3 -c "import json; print(json.load(open(r'''${ENROLL_BODY}'''))['enrollmentId'])")"
  ENROLL_CHALLENGE="$(python3 -c "import json; print(json.load(open(r'''${ENROLL_BODY}'''))['enrollmentChallenge'])")"
  rm -f "${ENROLL_BODY}"

  DETAIL="$(curl -fsS -H "Authorization: Bearer ${TOKEN}" \
    "${ADMIN_API}/api/v1/enrollments/${ENROLLMENT_ID}")"
  PROOF="$(printf '%s' "$DETAIL" | python3 -c "import json,sys; print(json.load(sys.stdin).get('enrollmentProofToken') or '')")"
  if [[ -z "${PROOF}" ]]; then
    echo "enrollmentProofToken missing from GET enrollment ${ENROLLMENT_ID}" >&2
    exit 1
  fi

  echo "Binding+verifying enrollment ${ENROLLMENT_ID} on Demo Device ..."
  demo_device_enroll "${ENROLLMENT_ID}" "${PROOF}" "${ENROLL_CHALLENGE}"
  echo "Enrollment ${ENROLLMENT_ID} VERIFIED userIdentifier=${USERNAME} authAttemptChallengeRequired=${CHALLENGE_REQUIRED}."
fi

KEY_JSON="$(curl -fsS -X POST "${ADMIN_API}/api/v1/api-keys" \
  "${auth_hdr[@]}" \
  -d "{\"integrationId\":${INTEGRATION_ID},\"description\":\"jenkins-lab-m5\",\"ipWhitelist\":[\"0.0.0.0/0\",\"172.0.0.0/8\",\"127.0.0.1/32\"]}")"
IKEY="$(printf '%s' "$KEY_JSON" | python3 -c "import json,sys; print(json.load(sys.stdin)['integrationKey'])")"
SKEY="$(printf '%s' "$KEY_JSON" | python3 -c "import json,sys; print(json.load(sys.stdin)['secretKey'])")"

umask 077
# Expect challenge on live demo when enrollment requires it (matrix B).
EXPECT_CHALLENGE="${CHALLENGE_REQUIRED}"
cat >"${OUT_FILE}" <<EOF
# Generated by m5-provision-lab-integration.sh — do not commit
export EZKEY_MOCK_BASE_URL=http://integration-api:7080
export EZKEY_MOCK_INTEGRATION_KEY=${IKEY}
export EZKEY_MOCK_SECRET=${SKEY}
export EZKEY_JENKINS_MFA_USER=${USERNAME}
export EZKEY_JENKINS_INTEGRATION_ID=${INTEGRATION_ID}
export EZKEY_JENKINS_ENROLLMENT_ID=${ENROLLMENT_ID}
export EZKEY_JENKINS_ENROLLMENT_CHALLENGE_REQUIRED=${CHALLENGE_REQUIRED}
export EZKEY_JENKINS_EXPECT_CHALLENGE=${EXPECT_CHALLENGE}
EOF

# Compat alias for older M4 docs / scripts
cp -f "${OUT_FILE}" "${OUT_DIR}/m4-integration.env"

echo "Wrote ${OUT_FILE}"
echo "Next: source ${OUT_FILE} && ./scripts/configure-mock-auth.sh"
echo "Live demo (matrix $([[ "${CHALLENGE_REQUIRED}" == true ]] && echo B || echo A)): ./scripts/m4-live-demo.sh approve"
echo "Case A provision: EZKEY_JENKINS_ENROLLMENT_CHALLENGE_REQUIRED=false ./scripts/m5-provision-lab-integration.sh"
