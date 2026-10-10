#!/usr/bin/env bash
# Local idempotency checks for bootstrap-init (Patrick B2): null token / skip path → exit 0.
# shellcheck shell=bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
INIT="$ROOT/docker/bootstrap-init/bootstrap-init.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

fail() { echo "FAIL: $*" >&2; exit 1; }

# Case 1: redacted credentials (null proof token) must exit 0 without calling APIs.
cat >"$TMP/bootstrap-credentials.json" <<'EOF'
{"enrollmentId": 7, "username": "admin.docker", "recoveryCodes": ["AAAA-BBBB-CCCC-DDDD"]}
EOF

set +e
out="$(
  BOOTSTRAP_CREDS_FILE="$TMP/bootstrap-credentials.json" \
    DEMO_DEVICE_ENROLLMENTS_DIR="$TMP/enrollments" \
    ADMIN_API_URL="http://127.0.0.1:9" \
    AUTH_API_URL="http://127.0.0.1:9" \
    CRYPTO_API_URL="http://127.0.0.1:9" \
    sh "$INIT" 2>&1
)"
rc=$?
set -e
[ "$rc" -eq 0 ] || fail "null-token re-run expected exit 0, got $rc; out=$out"
echo "$out" | grep -q 'already past bind' || fail "expected INFO already-past-bind; out=$out"
echo "OK: null-token re-run exits 0"

# Case 2: disabled init exits 0
set +e
out2="$(EZKEY_BOOTSTRAP_INIT_ENABLED=false sh "$INIT" 2>&1)"
rc2=$?
set -e
[ "$rc2" -eq 0 ] || fail "disabled init expected exit 0, got $rc2"
echo "$out2" | grep -q 'SKIPPED' || fail "expected SKIPPED; out=$out2"
echo "OK: EZKEY_BOOTSTRAP_INIT_ENABLED=false exits 0"

echo "All bootstrap-init idempotent checks passed."
