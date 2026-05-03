#!/usr/bin/env bash

set -euo pipefail

PACKAGE_NAME="${1:-org.ezkey.mobile}"
ADB_CMD=(env MSYS_NO_PATHCONV=1 adb)

if ! command -v adb >/dev/null 2>&1; then
  echo "Error: adb is required." >&2
  exit 1
fi

if command -v py >/dev/null 2>&1; then
  PYTHON_CMD=(py -3)
elif command -v python3 >/dev/null 2>&1; then
  PYTHON_CMD=(python3)
elif command -v python >/dev/null 2>&1; then
  PYTHON_CMD=(python)
else
  echo "Error: python3, python, or py -3 is required." >&2
  exit 1
fi

if ! "${ADB_CMD[@]}" get-state >/dev/null 2>&1; then
  echo "Error: no connected Android device visible to adb." >&2
  exit 1
fi

if ! "${ADB_CMD[@]}" shell run-as "$PACKAGE_NAME" true >/dev/null 2>&1; then
  cat >&2 <<EOF
Error: adb run-as failed for package '$PACKAGE_NAME'.
This usually means:
- the app is not installed
- the connected build is not debuggable
- or the package name is wrong
EOF
  exit 1
fi

echo "== Ezkey Android sensitive storage verification =="
echo "Package: $PACKAGE_NAME"
echo

echo "-- App sandbox directories --"
"${ADB_CMD[@]}" shell run-as "$PACKAGE_NAME" ls -la "/data/user/0/$PACKAGE_NAME"
echo

echo "-- AsyncStorage rows and key checks --"
"${ADB_CMD[@]}" exec-out run-as "$PACKAGE_NAME" cat "/data/user/0/$PACKAGE_NAME/databases/RKStorage" | "${PYTHON_CMD[@]}" -c '
import sys
import sqlite3
import tempfile
import os
import json

blob = sys.stdin.buffer.read()
tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".db")
tmp.write(blob)
tmp.close()

try:
    conn = sqlite3.connect(tmp.name)
    rows = conn.execute("select key, value from catalystLocalStorage order by key").fetchall()
    print("row_count={}".format(len(rows)))
    for key, value in rows:
        print("key={}".format(key))
        if key == "ezkey-mobile/enrollments":
            print("  contains_enrollmentProofToken={}".format("enrollmentProofToken" in value))
            print("  contains_integrationPublicKey={}".format("integrationPublicKey" in value))
            print("  contains_secure_storage_key_name={}".format("ezkey-mobile/enrollment-proof-token" in value))
            parsed = json.loads(value)
            print("  enrollment_count={}".format(len(parsed)))
            for idx, item in enumerate(parsed):
                print("  enrollment[{}].id={}".format(idx, item.get("id")))
                print("  enrollment[{}].has_enrollmentProofToken={}".format(idx, "enrollmentProofToken" in item))
                print("  enrollment[{}].has_integrationPublicKey={}".format(idx, "integrationPublicKey" in item))
        else:
            print("  value={}".format(value))
finally:
    try:
        conn.close()
    except Exception:
        pass
    os.unlink(tmp.name)
'
echo

echo "-- Secure-storage datastore checks --"
"${ADB_CMD[@]}" exec-out run-as "$PACKAGE_NAME" cat "/data/user/0/$PACKAGE_NAME/files/datastore/RN_KEYCHAIN.preferences_pb" | "${PYTHON_CMD[@]}" -c '
import sys
import re

blob = sys.stdin.buffer.read()
patterns = [
    b"enrollmentProofToken",
    b"ezkey-mobile/enrollment-proof-token",
    b"integrationPublicKey",
    b"proof",
    b"token",
]
for pattern in patterns:
    print("contains_{}={}".format(pattern.decode("utf-8", "ignore"), pattern in blob))

print("ascii_snippets_with_security_keywords:")
for match in re.finditer(rb"[ -~]{6,}", blob):
    snippet = match.group().decode("utf-8", "ignore")
    lowered = snippet.lower()
    if any(keyword in lowered for keyword in ("ezkey", "keychain", "token", "proof", "enrollment", "secure")):
        print("  {}".format(snippet))
'
echo

echo "-- Logcat leak check (best effort, current buffer) --"
if "${ADB_CMD[@]}" logcat -d | grep -Ei 'enrollmentProofToken|ezkey-mobile/enrollment-proof-token|authAttemptProofToken|deviceProofToken' >/dev/null 2>&1; then
  echo "Potential sensitive-token log entries detected in current logcat buffer."
  "${ADB_CMD[@]}" logcat -d | grep -Ei 'enrollmentProofToken|ezkey-mobile/enrollment-proof-token|authAttemptProofToken|deviceProofToken'
else
  echo "No obvious sensitive-token strings found in current logcat buffer."
fi
echo

cat <<EOF
Interpretation:
- AsyncStorage should report contains_enrollmentProofToken=False for ezkey-mobile/enrollments.
- The secure datastore may contain the logical secure-storage key name.
- The secure datastore should not reveal the plaintext enrollment proof token.
- Logcat should not contain raw sensitive proof-token strings.
EOF
