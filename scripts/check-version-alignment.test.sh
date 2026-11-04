#!/usr/bin/env bash
# Unit checks for product-version helpers + alignment gate.
#   ./scripts/check-version-alignment.test.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/product-version.sh
source "${ROOT}/scripts/lib/product-version.sh"

failures=0
assert_eq() {
  local label="$1"
  local actual="$2"
  local expected="$3"
  if [[ "$actual" != "$expected" ]]; then
    echo "FAIL: ${label}: got '${actual}' expected '${expected}'" >&2
    failures=$((failures + 1))
  else
    echo "PASS: ${label}"
  fi
}

assert_eq "android strips SNAPSHOT" "$(ezkey_android_version_name '0.2.0-SNAPSHOT')" "0.2.0"
assert_eq "android keeps alpha" "$(ezkey_android_version_name '0.2.0-alpha.1')" "0.2.0-alpha.1"
assert_eq "python SNAPSHOT→dev0" "$(ezkey_python_product_version '0.2.0-SNAPSHOT')" "0.2.0.dev0"
assert_eq "python release passthrough" "$(ezkey_python_product_version '0.2.0')" "0.2.0"
assert_eq "npm passthrough" "$(ezkey_npm_product_version '0.2.0-SNAPSHOT')" "0.2.0-SNAPSHOT"
assert_eq "versionCode floor" "${EZKEY_ANDROID_VERSION_CODE_MIN}" "7"

# Live tree must pass the gate after sync (called by CI / agents after sync-version).
if ! bash "${ROOT}/scripts/check-version-alignment.sh" >/tmp/ezkey-version-align-test.out 2>&1; then
  echo "FAIL: check-version-alignment.sh on current tree" >&2
  cat /tmp/ezkey-version-align-test.out >&2
  failures=$((failures + 1))
else
  echo "PASS: check-version-alignment.sh on current tree"
fi

if [[ "$failures" -ne 0 ]]; then
  echo "check-version-alignment.test.sh FAILED (${failures})" >&2
  exit 1
fi
echo "check-version-alignment.test.sh passed"
