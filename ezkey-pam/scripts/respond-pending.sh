#!/usr/bin/env bash
# Poll Demo Device for a pending auth request and approve or reject it.
# Usage: ./scripts/respond-pending.sh [approve|reject] [enrollmentId] [challengeCode]
# challengeCode is required when the pending card asks for number-matching.
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
    echo "Usage: $0 [approve|reject] [enrollmentId] [challengeCode]" >&2
    exit 1
    ;;
esac

ENROLLMENT_ID="${2:-${EZKEY_PAM_ENROLLMENT_ID:-}}"
if [ -z "${ENROLLMENT_ID}" ] && [ -f "${RUNTIME_DIR}/provision.json" ]; then
  ENROLLMENT_ID="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["enrollmentId"])' "${RUNTIME_DIR}/provision.json")"
fi
if [ -z "${ENROLLMENT_ID}" ]; then
  echo "Usage: $0 [approve|reject] [enrollmentId] [challengeCode]" >&2
  exit 1
fi

CHALLENGE="${3:-${EZKEY_CHALLENGE_CODE:-}}"

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
    need_chal=0
    if echo "${html}" | grep -q 'data-testid="demo-device-challenge-input"'; then
      need_chal=1
    fi
    if [ "${need_chal}" -eq 1 ] && [ -z "${CHALLENGE}" ]; then
      echo "Pending auth requires a challenge code (pass as arg 3 or EZKEY_CHALLENGE_CODE)" >&2
      exit 1
    fi
    echo "Responding (${ACTION}) auth attempt ${attempt_id} for enrollment ${ENROLLMENT_ID}"
    POST_ARGS=(
      -X POST
      -H "Content-Type: application/x-www-form-urlencoded"
      --data-urlencode "authAttemptId=${attempt_id}"
      --data-urlencode "authAttemptProofToken=${proof}"
      --data-urlencode "approved=${APPROVED}"
    )
    if [ -n "${CHALLENGE}" ]; then
      POST_ARGS+=(--data-urlencode "challengeResponse=${CHALLENGE}")
    fi
    result="$(curl -fsS -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" \
      "${POST_ARGS[@]}" \
      "${DEMO_DEVICE_URL}/phone/ezkey/enrollments/${ENROLLMENT_ID}/auth/respond")"
    if [ "${ACTION}" = "approve" ]; then
      if echo "${result}" | grep -q 'data-testid="demo-device-result-success"'; then
        echo "Demo Device approval succeeded"
        exit 0
      fi
    else
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
