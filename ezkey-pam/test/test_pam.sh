#!/bin/bash
# ==================================================
# test/test_pam.sh - Ezkey PAM module basic tests
# ==================================================
# Run from within the container or on the target host after installation.
# Usage: ./test/test_pam.sh

set -e

PASS=0
FAIL=0
PAM_SO="/lib64/security/pam_ezkey.so"
PAM_CONF="/etc/security/pam_ezkey.conf"
PAM_SSHD="/etc/pam.d/sshd"
M2M_URL="${EZKEY_M2M_API_URL:-http://localhost:7080}"

ok()   { echo "[PASS] $1"; PASS=$((PASS+1)); }
fail() { echo "[FAIL] $1"; FAIL=$((FAIL+1)); }

echo "=== Ezkey PAM Module Tests ==="
echo ""

# Test 1: pam_ezkey.so is installed
if [ -f "$PAM_SO" ]; then
    ok "pam_ezkey.so exists at $PAM_SO"
else
    fail "pam_ezkey.so not found at $PAM_SO"
fi

# Test 2: pam_ezkey.conf is installed
if [ -f "$PAM_CONF" ]; then
    ok "pam_ezkey.conf exists at $PAM_CONF"
else
    fail "pam_ezkey.conf not found at $PAM_CONF"
fi

# Test 3: PAM sshd config includes pam_ezkey
if grep -q "pam_ezkey" "$PAM_SSHD" 2>/dev/null; then
    ok "pam_ezkey is referenced in $PAM_SSHD"
else
    fail "pam_ezkey not found in $PAM_SSHD"
fi

# Test 4: EZKEY_INTEGRATION_KEY is set
if [ -n "$EZKEY_INTEGRATION_KEY" ]; then
    ok "EZKEY_INTEGRATION_KEY is set"
else
    fail "EZKEY_INTEGRATION_KEY is not set (PAM will reject all auth attempts)"
fi

# Test 5: EZKEY_SECRET_KEY is set
if [ -n "$EZKEY_SECRET_KEY" ]; then
    ok "EZKEY_SECRET_KEY is set"
else
    fail "EZKEY_SECRET_KEY is not set (PAM will reject all auth attempts)"
fi

# Test 6: Integration API health check
echo ""
echo "Checking Integration API connectivity at $M2M_URL ..."
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    --max-time 5 "$M2M_URL/actuator/health" 2>/dev/null || echo "000")
if [ "$HTTP_STATUS" = "200" ]; then
    ok "Integration API is reachable ($M2M_URL) - HTTP $HTTP_STATUS"
else
    fail "Integration API not reachable at $M2M_URL (HTTP $HTTP_STATUS) - ensure the stack is running"
fi

# Test 7: pamtester (if available)
if command -v pamtester &>/dev/null; then
    echo ""
    echo "Running pamtester for user 'testuser' (will trigger real MFA flow)..."
    echo "Approve in the demo-device UI to complete the test."
    if pamtester sshd testuser authenticate; then
        ok "pamtester authentication succeeded"
    else
        fail "pamtester authentication failed"
    fi
fi

echo ""
echo "=== Results: $PASS passed, $FAIL failed ==="
[ "$FAIL" -eq 0 ] && exit 0 || exit 1
