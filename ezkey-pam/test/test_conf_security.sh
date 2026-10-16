#!/bin/bash
# Conf security checks inside the PAM SSH demo container (M1).
# Expects pam_ezkey.conf at /etc/security/pam_ezkey.conf.
set -euo pipefail

CONF="${EZKEY_CONFIG_FILE:-/etc/security/pam_ezkey.conf}"
PASS=0
FAIL=0

ok()   { echo "[PASS] $1"; PASS=$((PASS+1)); }
fail() { echo "[FAIL] $1"; FAIL=$((FAIL+1)); }

echo "=== Ezkey PAM conf security (M1) ==="

if [ ! -f "${CONF}" ]; then
  fail "conf missing at ${CONF}"
  echo "=== Results: $PASS passed, $FAIL failed ==="
  exit 1
fi

owner="$(stat -c '%u:%g' "${CONF}" 2>/dev/null || stat -f '%u:%g' "${CONF}")"
mode="$(stat -c '%a' "${CONF}" 2>/dev/null || stat -f '%OLp' "${CONF}")"

if [ "${owner}" = "0:0" ]; then
  ok "conf owner is root:root (${owner})"
else
  fail "conf owner is ${owner}, expected 0:0"
fi

# Exact owner-readable modes only (0600 or 0400). Demo entrypoint writes 600.
if [ "${mode}" = "600" ] || [ "${mode}" = "400" ]; then
  ok "conf mode is ${mode} (0600 or 0400)"
else
  fail "conf mode is ${mode}, expected 600 or 400"
fi

for key in integration_api_url integration_key secret_key; do
  if grep -qE "^${key}=.+" "${CONF}"; then
    ok "${key} is present and non-empty"
  else
    fail "${key} missing or empty in conf"
  fi
done

echo ""
echo "=== Results: $PASS passed, $FAIL failed ==="
[ "${FAIL}" -eq 0 ]
