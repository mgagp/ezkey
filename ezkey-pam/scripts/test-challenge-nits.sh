#!/usr/bin/env bash
# Christophe nits: challenge 0..99 (unit harness in make test); no-conv → UNAVAIL;
# lab_http= on attempt line (also asserted in test-challenge / test-conf-security).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
. "${SCRIPT_DIR}/common.sh"

require_cmd docker
require_integration_keys

CONTAINER="${EZKEY_PAM_CONTAINER:-ezkey-pam-ssh}"
CONF="/etc/security/pam_ezkey.conf"
BACKUP="/etc/security/pam_ezkey.conf.noconvbak"
ARTIFACT_DIR="${EZKEY_PAM_ARTIFACT_DIR:-/opt/cursor/artifacts}"
mkdir -p "${ARTIFACT_DIR}"
PROOF="${ARTIFACT_DIR}/pam-challenge-nits.log"
PASS=0
FAIL=0

ok()   { echo "[PASS] $1"; PASS=$((PASS+1)); }
fail() { echo "[FAIL] $1"; FAIL=$((FAIL+1)); }

if ! docker ps --format '{{.Names}}' | grep -qx "${CONTAINER}"; then
  echo "${CONTAINER} is not running" >&2
  exit 1
fi

restore_conf() {
  docker exec "${CONTAINER}" sh -c \
    "if [ -f '${BACKUP}' ]; then mv -f '${BACKUP}' '${CONF}'; chown root:root '${CONF}'; chmod 600 '${CONF}'; fi" \
    || true
}
trap restore_conf EXIT

echo "=== Christophe challenge nits (${CONTAINER}) ===" | tee "${PROOF}"

docker exec "${CONTAINER}" test -x /opt/pam-ezkey/test/test_no_conv_client \
  || { echo "test_no_conv_client missing — rebuild image" >&2; exit 1; }
docker exec "${CONTAINER}" test -f /etc/pam.d/ezkey-chal-noconv \
  || { echo "pam.d/ezkey-chal-noconv missing — rebuild image" >&2; exit 1; }

docker exec "${CONTAINER}" cp -a "${CONF}" "${BACKUP}"
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^challenge_requested=.*/challenge_requested=true/' '${CONF}'; \
   sed -i 's/^wait_timeout=.*/wait_timeout=30/' '${CONF}'; \
   chown root:root '${CONF}'; chmod 600 '${CONF}'"

before="$(docker exec "${CONTAINER}" sh -c \
  "grep -c 'pam_ezkey: attempt ' /var/log/secure 2>/dev/null || true" | tr -d ' \r')"
before="${before:-0}"

set +e
out="$(docker exec "${CONTAINER}" /opt/pam-ezkey/test/test_no_conv_client testuser \
  2>&1 | tee -a "${PROOF}")"
noconv_rc=$?
set -e

echo "no_conv_rc=${noconv_rc}" | tee -a "${PROOF}"
echo "${out}" | tee -a "${PROOF}"

if [ "${noconv_rc}" -eq 0 ] && echo "${out}" | grep -q 'pam_authenticate_rc=9'; then
  ok "no PAM conversation → UNAVAIL (pam_rc=9)"
else
  fail "expected UNAVAIL from failing conv (rc=${noconv_rc}): ${out}"
fi

# Wait for attempt line
attempt=""
for _ in $(seq 1 40); do
  after="$(docker exec "${CONTAINER}" sh -c \
    "grep -c 'pam_ezkey: attempt ' /var/log/secure 2>/dev/null || true" | tr -d ' \r')"
  after="${after:-0}"
  if [ "${after}" -gt "${before}" ]; then
    attempt="$(docker exec "${CONTAINER}" sh -c \
      "grep 'pam_ezkey: attempt ' /var/log/secure | tail -n 1" || true)"
    break
  fi
  sleep 0.25
done

echo "attempt_line=${attempt}" | tee -a "${PROOF}"
if echo "${attempt}" | grep -q 'result=UNAVAIL' \
  && echo "${attempt}" | grep -q 'lab_http=true'; then
  ok "no-conv attempt line UNAVAIL + lab_http=true"
else
  fail "expected UNAVAIL lab_http=true: ${attempt}"
fi

echo "" | tee -a "${PROOF}"
echo "=== Results: $PASS passed, $FAIL failed ===" | tee -a "${PROOF}"
[ "${FAIL}" -eq 0 ]
