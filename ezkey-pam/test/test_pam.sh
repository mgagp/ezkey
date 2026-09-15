#!/bin/bash
# Basic installation and connectivity checks for the Ezkey PAM demo.
set -euo pipefail

PASS=0
FAIL=0
PAM_SO="/lib64/security/pam_ezkey.so"
PAM_CONF="/etc/security/pam_ezkey.conf"
PAM_SSHD="/etc/pam.d/sshd"
API_URL="${EZKEY_INTEGRATION_API_URL:-${EZKEY_M2M_API_URL:-http://localhost:7080}}"

ok()   { echo "[PASS] $1"; PASS=$((PASS+1)); }
fail() { echo "[FAIL] $1"; FAIL=$((FAIL+1)); }

echo "=== Ezkey PAM Module Tests ==="

if [ -f "$PAM_SO" ]; then
    ok "pam_ezkey.so exists at $PAM_SO"
else
    fail "pam_ezkey.so not found at $PAM_SO"
fi

if [ -f "$PAM_CONF" ]; then
    ok "pam_ezkey.conf exists at $PAM_CONF"
else
    fail "pam_ezkey.conf not found at $PAM_CONF"
fi

if grep -q "pam_ezkey" "$PAM_SSHD" 2>/dev/null; then
    ok "pam_ezkey is referenced in $PAM_SSHD"
else
    fail "pam_ezkey not found in $PAM_SSHD"
fi

if grep -q '^integration_key=.\+' "$PAM_CONF" 2>/dev/null || [ -n "${EZKEY_INTEGRATION_KEY:-}" ]; then
    ok "integration key is configured"
else
    fail "integration_key is empty (PAM will reject all auth attempts)"
fi

if grep -q '^secret_key=.\+' "$PAM_CONF" 2>/dev/null || [ -n "${EZKEY_SECRET_KEY:-}" ]; then
    ok "secret key is configured"
else
    fail "secret_key is empty (PAM will reject all auth attempts)"
fi

echo ""
echo "Checking Integration API connectivity at $API_URL ..."
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "$API_URL/actuator/health" || echo "000")
if [ "$HTTP_STATUS" = "200" ]; then
    ok "Integration API is reachable ($API_URL) - HTTP $HTTP_STATUS"
else
    fail "Integration API not reachable at $API_URL (HTTP $HTTP_STATUS)"
fi

echo ""
echo "=== Results: $PASS passed, $FAIL failed ==="
[ "$FAIL" -eq 0 ]
