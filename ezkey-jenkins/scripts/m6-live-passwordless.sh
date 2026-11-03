#!/usr/bin/env bash
# Passwordless live check (#1184): Sign in with Ezkey, type the challenge on Demo Device.
# Usage: ./scripts/m6-live-passwordless.sh approve|deny|wrong-code
# A wrong code must not create a Jenkins session.
# Wait 30s between runs for the same user. A second send inside the cooldown
# shows a decoy code and does not create an attempt, so Demo Device has nothing to answer.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export EZKEY_JENKINS_ROOT="${ROOT}"
# shellcheck source=lib/demo-device-respond.sh
source "${ROOT}/scripts/lib/demo-device-respond.sh"
# shellcheck source=lib/lab-temp.sh
source "${ROOT}/scripts/lib/lab-temp.sh"

# Windows Python otherwise decodes Jenkins HTML as cp1252 and dies on the page.
export PYTHONUTF8=1

MODE="${1:?usage: $0 approve|deny|wrong-code}"
BASE="${EZKEY_JENKINS_URL:-http://127.0.0.1:28080}"
USER="${EZKEY_JENKINS_MFA_USER:-labuser}"
ENROLLMENT_ID="${EZKEY_JENKINS_ENROLLMENT_ID:-1}"
JAR="$(ezkey_lab_mktemp)"
INDEX_HTML="$(ezkey_lab_mktemp)"
WAIT_HTML="$(ezkey_lab_mktemp)"
WHOAMI="$(ezkey_lab_mktemp)"
trap 'rm -f "$JAR" "$INDEX_HTML" "$WAIT_HTML" "$WHOAMI"' EXIT

curl -fsS -c "$JAR" -b "$JAR" "${BASE}/ezkey-login/" -o "${INDEX_HTML}" >/dev/null
CRUMB="$(python3 -c "import re; h=open('${INDEX_HTML}').read(); m=re.search(r'name=\"Jenkins-Crumb\"[^>]*value=\"([^\"]+)\"', h); print(m.group(1) if m else '')")"
test -n "${CRUMB}"
if ! grep -q 'Send Ezkey sign-in request' "${INDEX_HTML}"; then
  echo "sign-in page is missing the English send button" >&2
  exit 1
fi

CODE="$(curl -sS -c "$JAR" -b "$JAR" -o /dev/null -D /tmp/m6-send.hdr -w '%{http_code}' -X POST "${BASE}/ezkey-login/send" \
  -H "Jenkins-Crumb: ${CRUMB}" \
  --data-urlencode "Jenkins-Crumb=${CRUMB}" \
  --data-urlencode "username=${USER}")"
test "${CODE}" = "302"

curl -fsS -c "$JAR" -b "$JAR" "${BASE}/ezkey-login/wait" -o "${WAIT_HTML}"
CHAL="$(python3 -c "
import re
h=open('${WAIT_HTML}').read()
m=re.search(r'data-testid=\"ezkey-lab-challenge\"[\\s\\S]*?<strong[^>]*>\\s*(\\d{2})\\s*</strong>', h)
print(m.group(1) if m else '')
")"
test -n "${CHAL}" || { echo "expected a challenge code on the wait page" >&2; exit 1; }
echo "challenge_displayed=${CHAL}"

TYPED="${CHAL}"
expect=ACCEPTED
approved=true
case "${MODE}" in
  approve) ;;
  deny)
    approved=false
    expect=REJECTED
    ;;
  wrong-code)
    if [[ "${CHAL}" == "00" ]]; then TYPED="11"; else TYPED="00"; fi
    approved=true
    expect=INVALID
    ;;
  *)
    echo "unknown mode ${MODE}" >&2
    exit 1
    ;;
esac

demo_device_respond "${ENROLLMENT_ID}" "${approved}" "${TYPED}"

STATUS=PENDING
for _ in $(seq 1 40); do
  STATUS="$(curl -fsS -c "$JAR" -b "$JAR" "${BASE}/ezkey-login/poll" || true)"
  echo "poll=${STATUS}"
  case "${STATUS}" in ACCEPTED|REJECTED|EXPIRED|INVALID|CANCELLED) break ;; esac
  sleep 1
done
test "${STATUS}" = "${expect}"

curl -fsS -c "$JAR" -b "$JAR" "${BASE}/whoAmI/api/json" -o "${WHOAMI}" || true
if [[ "${MODE}" == "approve" ]]; then
  grep -q "\"name\":\"${USER}\"" "${WHOAMI}" || grep -q "\"${USER}\"" "${WHOAMI}"
else
  if grep -q "\"name\":\"${USER}\"" "${WHOAMI}"; then
    echo "Jenkins session was created for a ${MODE} result" >&2
    exit 1
  fi
fi
echo "M6 ${MODE} OK status=${STATUS}"
