#!/usr/bin/env bash
# Claim pending auth on Demo Device and Approve or Deny (lab helper).
# Usage: demo_device_respond <enrollmentId> <true|false> [challengeDigits]
demo_device_respond() {
  local enrollment_id="$1"
  local approved="$2"
  local challenge="${3:-}"
  local base="${EZKEY_DEMO_DEVICE_URL:-http://127.0.0.1:8083}"
  local jar
  jar="$(mktemp)"
  local html
  html="$(mktemp)"
  # shellcheck disable=SC2064
  trap "rm -f '$jar' '$html'" RETURN

  local i
  for i in $(seq 1 40); do
    curl -fsS -m 10 -c "$jar" -b "$jar" \
      "${base}/phone/ezkey/enrollments/${enrollment_id}/auth" -o "$html" || true
    if grep -q 'name="authAttemptId"' "$html"; then
      break
    fi
    sleep 1
  done
  if ! grep -q 'name="authAttemptId"' "$html"; then
    echo "demo-device: no pending attempt for enrollment ${enrollment_id}" >&2
    return 1
  fi

  local attempt_id proof action
  attempt_id="$(sed -n 's/.*name="authAttemptId" value="\([^"]*\)".*/\1/p' "$html" | head -1)"
  proof="$(sed -n 's/.*name="authAttemptProofToken" value="\([^"]*\)".*/\1/p' "$html" | head -1)"
  action="$(sed -n 's/.*action="\([^"]*auth\/respond[^"]*\)".*/\1/p' "$html" | head -1)"
  if [[ -z "$attempt_id" || -z "$proof" || -z "$action" ]]; then
    echo "demo-device: failed to parse respond form" >&2
    return 1
  fi
  # action may be relative
  if [[ "$action" != http* ]]; then
    action="${base}${action}"
  fi

  local -a data
  data=(
    -d "authAttemptId=${attempt_id}"
    -d "authAttemptProofToken=${proof}"
    -d "approved=${approved}"
  )
  if [[ -n "$challenge" ]]; then
    data+=(-d "challengeResponse=${challenge}")
  fi

  curl -fsS -m 30 -c "$jar" -b "$jar" -X POST "$action" "${data[@]}" -o /tmp/dd-respond.html -w 'respond:%{http_code}\n'
  echo "demo-device: responded approved=${approved} authAttemptId=${attempt_id}"
}
