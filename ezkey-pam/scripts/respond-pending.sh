#!/usr/bin/env bash
# Poll Demo Device for a pending auth request and approve or reject it.
# Usage: ./scripts/respond-pending.sh [approve|reject] [enrollmentId]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
. "${SCRIPT_DIR}/common.sh"

require_cmd curl
require_cmd python3

ACTION="${1:-approve}"
case "${ACTION}" in
  approve|reject) ;;
  *)
    echo "Usage: $0 [approve|reject] [enrollmentId]" >&2
    exit 1
    ;;
esac

ENROLLMENT_ID="${2:-${EZKEY_PAM_ENROLLMENT_ID:-}}"
if [ -z "${ENROLLMENT_ID}" ] && [ -f "${RUNTIME_DIR}/provision.json" ]; then
  ENROLLMENT_ID="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["enrollmentId"])' "${RUNTIME_DIR}/provision.json")"
fi
if [ -z "${ENROLLMENT_ID}" ]; then
  echo "Usage: $0 [approve|reject] [enrollmentId]" >&2
  exit 1
fi

APPROVED="true"
if [ "${ACTION}" = "reject" ]; then
  APPROVED="false"
fi

COOKIE_JAR="${RUNTIME_DIR}/respond.cookies"
rm -f "${COOKIE_JAR}"

for i in $(seq 1 45); do
  html="$(curl -fsS -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" \
    "${DEMO_DEVICE_URL}/phone/ezkey/enrollments/${ENROLLMENT_ID}/auth" || true)"
  if echo "${html}" | grep -q 'data-testid="demo-device-auth-pending"'; then
    attempt_id="$(html_input_value "${html}" authAttemptId)"
    proof="$(html_input_value "${html}" authAttemptProofToken)"
    echo "Responding (${ACTION}) auth attempt ${attempt_id} for enrollment ${ENROLLMENT_ID}"
    result="$(curl -fsS -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" \
      -X POST \
      -H "Content-Type: application/x-www-form-urlencoded" \
      --data-urlencode "authAttemptId=${attempt_id}" \
      --data-urlencode "authAttemptProofToken=${proof}" \
      --data-urlencode "approved=${APPROVED}" \
      "${DEMO_DEVICE_URL}/phone/ezkey/enrollments/${ENROLLMENT_ID}/auth/respond")"
    if [ "${ACTION}" = "approve" ]; then
      if echo "${result}" | grep -q 'data-testid="demo-device-result-success"'; then
        echo "Demo Device approval succeeded"
        exit 0
      fi
    else
      # Rejected path: success page or rejected marker
      if echo "${result}" | grep -qiE 'reject|denied|demo-device-result'; then
        echo "Demo Device reject submitted"
        exit 0
      fi
    fi
    echo "Unexpected Demo Device respond page:" >&2
    echo "${result}" >&2
    exit 1
  fi
  sleep 2
done

echo "Timed out waiting for a pending request on enrollment ${ENROLLMENT_ID}" >&2
exit 1
