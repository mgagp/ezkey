#!/bin/bash
# Basic installation and connectivity checks for the Ezkey PAM demo.
set -euo pipefail

PASS=0
FAIL=0
PAM_SO_CANDIDATES=(
  "${EZKEY_PAM_SO:-}"
  /usr/lib64/security/pam_ezkey.so
  /lib64/security/pam_ezkey.so
)
PAM_SO=""
for candidate in "${PAM_SO_CANDIDATES[@]}"; do
  if [ -n "$candidate" ] && [ -f "$candidate" ]; then
    PAM_SO="$candidate"
    break
  fi
done
PAM_CONF="/etc/security/pam_ezkey.conf"
PAM_SSHD="/etc/pam.d/sshd"
API_URL="${EZKEY_INTEGRATION_API_URL:-${EZKEY_M2M_API_URL:-http://localhost:7080}}"

ok()   { echo "[PASS] $1"; PASS=$((PASS+1)); }
fail() { echo "[FAIL] $1"; FAIL=$((FAIL+1)); }

echo "=== Ezkey PAM Module Tests ==="

LOG_DIR="/var/log/ezkey-pam"

if [ -n "$PAM_SO" ]; then
    ok "pam_ezkey.so exists at $PAM_SO"
else
    fail "pam_ezkey.so not found under /usr/lib64/security or /lib64/security"
fi

if [ -d "$LOG_DIR" ]; then
    mode=$(stat -c '%a' "$LOG_DIR" 2>/dev/null || stat -f '%OLp' "$LOG_DIR" 2>/dev/null || echo "")
    if [ "$mode" = "700" ]; then
        ok "$LOG_DIR exists with mode 700"
    else
        fail "$LOG_DIR exists but mode is '${mode:-unknown}' (expected 700)"
    fi
else
    fail "$LOG_DIR missing (debug file logs require it; #818)"
fi

if [ -f "$PAM_CONF" ]; then
    ok "pam_ezkey.conf exists at $PAM_CONF"
    owner=$(stat -c '%u:%g' "$PAM_CONF" 2>/dev/null || stat -f '%u:%g' "$PAM_CONF" 2>/dev/null || echo "")
    mode=$(stat -c '%a' "$PAM_CONF" 2>/dev/null || stat -f '%OLp' "$PAM_CONF" 2>/dev/null || echo "")
    if [ "$owner" = "0:0" ] && [ "$mode" = "600" ]; then
        ok "pam_ezkey.conf is root:root mode 600"
    else
        fail "pam_ezkey.conf owner/mode is '${owner:-?}'/'${mode:-?}' (expected 0:0/600)"
    fi
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

HEALTH_URL="${EZKEY_INTEGRATION_HEALTH_URL:-http://integration-api:7081/actuator/health}"
echo ""
echo "Checking Integration API connectivity at $HEALTH_URL ..."
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "$HEALTH_URL" || echo "000")
if [ "$HTTP_STATUS" = "200" ]; then
    ok "Integration API is reachable ($API_URL) - HTTP $HTTP_STATUS"
else
    fail "Integration API not reachable at $API_URL (HTTP $HTTP_STATUS)"
fi

echo ""
echo "=== Results: $PASS passed, $FAIL failed ==="
[ "$FAIL" -eq 0 ]
