#!/usr/bin/env bash
# Drive Jenkins /ezkey-lab-login/ send → optional challenge → Demo Device approve|deny.
# Usage: ./scripts/m4-live-demo.sh approve|deny
# Env: EZKEY_JENKINS_EXPECT_CHALLENGE=true|false (default true) — assert challenge presence.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export EZKEY_JENKINS_ROOT="${ROOT}"
# shellcheck source=lib/demo-device-respond.sh
source "${ROOT}/scripts/lib/demo-device-respond.sh"
# shellcheck source=lib/lab-temp.sh
source "${ROOT}/scripts/lib/lab-temp.sh"

MODE="${1:?usage: $0 approve|deny}"
BASE="${EZKEY_JENKINS_URL:-http://127.0.0.1:28080}"
USER="${EZKEY_JENKINS_MFA_USER:-labuser}"
ENROLLMENT_ID="${EZKEY_JENKINS_ENROLLMENT_ID:-1}"
EXPECT_CHALLENGE="${EZKEY_JENKINS_EXPECT_CHALLENGE:-true}"
JAR="$(ezkey_lab_mktemp)"
INDEX_HTML="$(ezkey_lab_mktemp)"
CONFIRM_HTML="$(ezkey_lab_mktemp)"
WAIT_HTML="$(ezkey_lab_mktemp)"
trap 'rm -f "$JAR" "$INDEX_HTML" "$CONFIRM_HTML" "$WAIT_HTML"' EXIT

curl -fsS -c "$JAR" -b "$JAR" "${BASE}/ezkey-lab-login/" -o "${INDEX_HTML}" >/dev/null
CRUMB="$(python3 -c "import re; h=open('${INDEX_HTML}').read(); m=re.search(r'name=\"Jenkins-Crumb\"[^>]*value=\"([^\"]+)\"', h); print(m.group(1) if m else '')")"
test -n "${CRUMB}"

# Prefer new send action; fall back to confirm alias.
SEND_URL="${BASE}/ezkey-lab-login/send"
curl -sS -c "$JAR" -b "$JAR" -X POST "${SEND_URL}" \
  -H "Jenkins-Crumb: ${CRUMB}" \
  --data-urlencode "Jenkins-Crumb=${CRUMB}" \
  --data-urlencode "username=${USER}" \
  -o "${CONFIRM_HTML}" -D /tmp/m4-confirm.hdr -w '%{http_code}' >/tmp/m4-confirm.code || true
if [[ "$(cat /tmp/m4-confirm.code)" != "302" ]]; then
  curl -sS -c "$JAR" -b "$JAR" -X POST "${BASE}/ezkey-lab-login/confirm" \
    -H "Jenkins-Crumb: ${CRUMB}" \
    --data-urlencode "Jenkins-Crumb=${CRUMB}" \
    --data-urlencode "username=${USER}" \
    -o "${CONFIRM_HTML}" -D /tmp/m4-confirm.hdr -w '%{http_code}' >/tmp/m4-confirm.code
fi
test "$(cat /tmp/m4-confirm.code)" = "302"

curl -fsS -c "$JAR" -b "$JAR" "${BASE}/ezkey-lab-login/wait" -o "${WAIT_HTML}"
CHAL="$(python3 -c "
import re
h=open('${WAIT_HTML}').read()
m=re.search(r'data-testid=\"ezkey-lab-challenge\"[\\s\\S]*?<strong[^>]*>\\s*(\\d{2})\\s*</strong>', h)
print(m.group(1) if m else '')
")"

case "$(printf '%s' "${EXPECT_CHALLENGE}" | tr '[:upper:]' '[:lower:]')" in
  1|true|yes|on)
    test -n "${CHAL}" || { echo "expected challenge code on wait page" >&2; exit 1; }
    echo "challenge_displayed=${CHAL}"
    ;;
  *)
    if [[ -n "${CHAL}" ]]; then
      echo "challenge_displayed=${CHAL} (unexpected but continuing)"
    else
      echo "challenge_displayed=none (matrix A)"
    fi
    ;;
esac

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
  case "${STATUS}" in ACCEPTED|REJECTED|EXPIRED|INVALID|CANCELLED) break ;; esac
  sleep 1
done
test "${STATUS}" = "${expect}"
echo "M4 ${MODE} OK status=${STATUS}"
if [[ "${MODE}" == approve ]]; then
  curl -fsS -c "$JAR" -b "$JAR" "${BASE}/whoAmI/api/json"
  echo
fi
