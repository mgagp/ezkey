#!/usr/bin/env bash
# Drive Jenkins /ezkey-lab-login/ confirm → challenge → Demo Device approve|deny.
# Usage: ./scripts/m4-live-demo.sh approve|deny
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=lib/demo-device-respond.sh
source "${ROOT}/scripts/lib/demo-device-respond.sh"

MODE="${1:?usage: $0 approve|deny}"
BASE="${EZKEY_JENKINS_URL:-http://127.0.0.1:28080}"
USER="${EZKEY_JENKINS_MFA_USER:-labuser}"
ENROLLMENT_ID="${EZKEY_JENKINS_ENROLLMENT_ID:-1}"
JAR="$(mktemp)"
trap 'rm -f "$JAR"' EXIT

curl -fsS -c "$JAR" -b "$JAR" "${BASE}/ezkey-lab-login/" -o /tmp/m4-index.html >/dev/null
CRUMB="$(python3 -c "import re; h=open('/tmp/m4-index.html').read(); m=re.search(r'name=\"Jenkins-Crumb\"[^>]*value=\"([^\"]+)\"', h); print(m.group(1) if m else '')")"
test -n "${CRUMB}"

curl -sS -c "$JAR" -b "$JAR" -X POST "${BASE}/ezkey-lab-login/confirm" \
  -H "Jenkins-Crumb: ${CRUMB}" \
  --data-urlencode "Jenkins-Crumb=${CRUMB}" \
  --data-urlencode "username=${USER}" \
  --data-urlencode "confirm=ok" \
  -o /tmp/m4-confirm.html -D /tmp/m4-confirm.hdr -w '%{http_code}' >/tmp/m4-confirm.code
test "$(cat /tmp/m4-confirm.code)" = "302"

curl -fsS -c "$JAR" -b "$JAR" "${BASE}/ezkey-lab-login/wait" -o /tmp/m4-wait.html
CHAL="$(python3 -c "import re; h=open('/tmp/m4-wait.html').read(); m=re.search(r'data-testid=\"ezkey-lab-challenge\"[\\s\\S]*?<strong[^>]*>\\s*(\\d{2})\\s*</strong>', h); assert m, 'challenge missing'; print(m.group(1))")"
echo "challenge_displayed=${CHAL}"

if [[ "${MODE}" == approve ]]; then
  demo_device_respond "${ENROLLMENT_ID}" true "${CHAL}"
  expect=ACCEPTED
else
  demo_device_respond "${ENROLLMENT_ID}" false "${CHAL}"
  expect=REJECTED
fi

STATUS=PENDING
for _ in $(seq 1 40); do
  STATUS="$(curl -fsS -c "$JAR" -b "$JAR" "${BASE}/ezkey-lab-login/poll")"
  echo "poll=${STATUS}"
  case "${STATUS}" in ACCEPTED|REJECTED|EXPIRED|INVALID) break ;; esac
  sleep 1
done
test "${STATUS}" = "${expect}"
echo "M4 ${MODE} OK status=${STATUS}"
if [[ "${MODE}" == approve ]]; then
  curl -fsS -c "$JAR" -b "$JAR" "${BASE}/whoAmI/api/json"
  echo
fi
