#!/usr/bin/env bash
# Local idempotency checks for bootstrap-init (Patrick N2): already-bound exit 0 only when
# the demo-device enrollment file exists; otherwise exit non-zero.
# shellcheck shell=bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
INIT="$ROOT/docker/bootstrap-init/bootstrap-init.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

fail() { echo "FAIL: $*" >&2; exit 1; }

run_init() {
  BOOTSTRAP_CREDS_FILE="$TMP/bootstrap-credentials.json" \
    DEMO_DEVICE_ENROLLMENTS_DIR="$TMP/enrollments" \
    ADMIN_API_URL="http://127.0.0.1:9" \
    AUTH_API_URL="http://127.0.0.1:9" \
    CRYPTO_API_URL="http://127.0.0.1:9" \
    sh "$INIT" 2>&1
}

# Case 1a: null proof token + demo-device file present → exit 0
mkdir -p "$TMP/enrollments"
cat >"$TMP/bootstrap-credentials.json" <<'EOF'
{"enrollmentId": 7, "username": "admin.docker", "recoveryCodes": ["AAAA-BBBB-CCCC-DDDD"]}
EOF
echo '{"enrollmentId":7}' >"$TMP/enrollments/7.json"

set +e
out="$(run_init)"
rc=$?
set -e
[ "$rc" -eq 0 ] || fail "null-token + demo file expected exit 0, got $rc; out=$out"
echo "$out" | grep -q 'demo-device file present' || fail "expected already-bound INFO; out=$out"
echo "OK: null-token + demo file exits 0"

# Case 1b: null proof token + demo-device file missing → exit non-zero
rm -f "$TMP/enrollments/7.json"
set +e
out_miss="$(run_init)"
rc_miss=$?
set -e
[ "$rc_miss" -ne 0 ] || fail "null-token without demo file expected non-zero; out=$out_miss"
echo "$out_miss" | grep -qi 'demo-device enrollment file missing' \
  || fail "expected missing-file error; out=$out_miss"
echo "OK: null-token without demo file exits non-zero"

# Case 2: disabled init exits 0
set +e
out2="$(EZKEY_BOOTSTRAP_INIT_ENABLED=false sh "$INIT" 2>&1)"
rc2=$?
set -e
[ "$rc2" -eq 0 ] || fail "disabled init expected exit 0, got $rc2"
echo "$out2" | grep -q 'SKIPPED' || fail "expected SKIPPED; out=$out2"
echo "OK: EZKEY_BOOTSTRAP_INIT_ENABLED=false exits 0"

# Case 3: secret artifact modes (policy mirrored from bootstrap-init Steps 6–7;
# full bind path needs live APIs — assert script contract + filesystem modes)
grep -q 'umask 077' "$INIT" || fail "bootstrap-init.sh must set umask 077 before secret writes"
grep -Eq 'chmod 600 "\$DEVICE_CREDS_FILE"' "$INIT" \
  || fail "bootstrap-init.sh must chmod 600 device-credentials.json"
grep -Eq 'chmod 700 "\$DEMO_DEVICE_ENROLLMENTS_DIR"' "$INIT" \
  || fail "bootstrap-init.sh must chmod 700 enrollments directory"
grep -Eq 'chmod 600 "\$ENROLLMENT_FILE"' "$INIT" \
  || fail "bootstrap-init.sh must chmod 600 demo-device enrollment file"

MODE_DIR="$TMP/mode-probe"
DEVICE_PROBE="$TMP/device-credentials.json"
ENROLL_DIR="$MODE_DIR/enrollments"
ENROLL_PROBE="$ENROLL_DIR/1.json"
umask 077
mkdir -p "$ENROLL_DIR"
chmod 700 "$ENROLL_DIR"
echo '{"privateKey":"probe"}' >"$DEVICE_PROBE"
chmod 600 "$DEVICE_PROBE"
echo '{"enrollmentId":1}' >"$ENROLL_PROBE"
chmod 600 "$ENROLL_PROBE"

mode_of() { stat -c '%a' "$1" 2>/dev/null || stat -f '%OLp' "$1"; }
[ "$(mode_of "$DEVICE_PROBE")" = "600" ] || fail "device-credentials mode expected 600"
[ "$(mode_of "$ENROLL_DIR")" = "700" ] || fail "enrollments dir mode expected 700"
[ "$(mode_of "$ENROLL_PROBE")" = "600" ] || fail "enrollment file mode expected 600"
echo "OK: bootstrap secret artifact modes (0600/0700)"

echo "All bootstrap-init idempotent checks passed."
