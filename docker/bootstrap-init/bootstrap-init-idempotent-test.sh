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

echo "All bootstrap-init idempotent checks passed."
