#!/usr/bin/env bash
# Poll Demo Device for a pending auth request on the PAM enrollment and approve it.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
. "${SCRIPT_DIR}/common.sh"

require_cmd curl
require_cmd python3

ENROLLMENT_ID="${1:-${EZKEY_PAM_ENROLLMENT_ID:-}}"
if [ -z "${ENROLLMENT_ID}" ] && [ -f "${RUNTIME_DIR}/provision.json" ]; then
  ENROLLMENT_ID="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["enrollmentId"])' "${RUNTIME_DIR}/provision.json")"
fi
if [ -z "${ENROLLMENT_ID}" ]; then
  echo "Usage: $0 <enrollmentId>" >&2
  exit 1
fi

COOKIE_JAR="${RUNTIME_DIR}/approve.cookies"
rm -f "${COOKIE_JAR}"

for i in $(seq 1 45); do
  html="$(curl -fsS -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" \
    "${DEMO_DEVICE_URL}/phone/ezkey/enrollments/${ENROLLMENT_ID}/auth" || true)"
  if echo "${html}" | grep -q 'data-testid="demo-device-auth-pending"'; then
    attempt_id="$(html_input_value "${html}" authAttemptId)"
    proof="$(html_input_value "${html}" authAttemptProofToken)"
    echo "Approving auth attempt ${attempt_id} for enrollment ${ENROLLMENT_ID}"
    result="$(curl -fsS -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" \
      -X POST \
      -H "Content-Type: application/x-www-form-urlencoded" \
      --data-urlencode "authAttemptId=${attempt_id}" \
      --data-urlencode "authAttemptProofToken=${proof}" \
      --data-urlencode "approved=true" \
      "${DEMO_DEVICE_URL}/phone/ezkey/enrollments/${ENROLLMENT_ID}/auth/respond")"
    if echo "${result}" | grep -q 'data-testid="demo-device-result-success"'; then
      echo "Demo Device approval succeeded"
      exit 0
    fi
    echo "Unexpected Demo Device respond page:" >&2
    echo "${result}" >&2
    exit 1
  fi
  sleep 2
done

echo "Timed out waiting for a pending request on enrollment ${ENROLLMENT_ID}" >&2
exit 1
