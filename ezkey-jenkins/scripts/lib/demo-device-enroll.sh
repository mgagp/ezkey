#!/usr/bin/env bash
# Bind + verify a pending enrollment on Demo Device (lab helper).
# Usage: demo_device_enroll <enrollmentId> <enrollmentProofToken> <enrollmentChallenge>
# Does not echo proof token or challenge.
# shellcheck source=lab-temp.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lab-temp.sh"
demo_device_enroll() {
  local enrollment_id="$1"
  local proof_token="$2"
  local challenge="$3"
  local base="${EZKEY_DEMO_DEVICE_URL:-http://127.0.0.1:8083}"
  local jar html
  jar="$(ezkey_lab_mktemp)"
  html="$(ezkey_lab_mktemp)"
  # shellcheck disable=SC2064
  trap "rm -f '$jar' '$html'" RETURN

  if [[ -z "${enrollment_id}" || -z "${proof_token}" || -z "${challenge}" ]]; then
    echo "demo-device-enroll: enrollmentId, proof token, and challenge required" >&2
    return 1
  fi

  curl -fsS -m 30 -c "$jar" -b "$jar" -X POST "${base}/phone/ezkey/enrollment/bind" \
    --data-urlencode "enrollmentId=${enrollment_id}" \
    --data-urlencode "enrollmentProofToken=${proof_token}" \
    -o "$html" || {
    echo "demo-device-enroll: bind HTTP failed for enrollment ${enrollment_id}" >&2
    return 1
  }
  if ! grep -q 'data-testid="demo-device-verify-enrollment-form"' "$html"; then
    echo "demo-device-enroll: bind did not reach verify form (enrollment ${enrollment_id})" >&2
    return 1
  fi

  curl -fsS -m 30 -c "$jar" -b "$jar" -X POST "${base}/phone/ezkey/enrollment/verify" \
    --data-urlencode "enrollmentId=${enrollment_id}" \
    --data-urlencode "enrollmentProofToken=${proof_token}" \
    --data-urlencode "challengeResponse=${challenge}" \
    -o "$html" || {
    echo "demo-device-enroll: verify HTTP failed for enrollment ${enrollment_id}" >&2
    return 1
  }
  if grep -q 'data-testid="demo-device-bind-error"' "$html"; then
    echo "demo-device-enroll: verify rejected for enrollment ${enrollment_id}" >&2
    return 1
  fi
  echo "demo-device-enroll: bound+verified enrollment ${enrollment_id}"
}
