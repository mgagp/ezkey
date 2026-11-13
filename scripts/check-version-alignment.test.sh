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
assert_eq "product revision constant" "${EZKEY_PRODUCT_REVISION}" "0.2.0"
assert_eq "dotnet assembly strips SNAPSHOT" "$(ezkey_dotnet_assembly_file_version '0.2.0-SNAPSHOT')" "0.2.0.0"
assert_eq "dotnet assembly strips alpha" "$(ezkey_dotnet_assembly_file_version '0.2.0-alpha.1')" "0.2.0.0"

# mvn-branch resolve: main → passthrough
IFS=$'\t' read -r r c m < <(ezkey_resolve_mvn_branch_versions "0.2.0" "-SNAPSHOT" "main" "abc1234")
assert_eq "mvn-branch main mode" "$m" "passthrough"
assert_eq "mvn-branch main revision" "$r" "0.2.0"

# Off main: always -SNAPSHOT even when pom changelist is empty
IFS=$'\t' read -r r c m < <(ezkey_resolve_mvn_branch_versions "0.2.0" "" "feature/foo" "abc1234")
assert_eq "mvn-branch feature mode" "$m" "override"
assert_eq "mvn-branch feature revision" "$r" "0.2.0-feature-foo"
assert_eq "mvn-branch feature changelist keeps SNAPSHOT" "$c" "-SNAPSHOT"

IFS=$'\t' read -r r c m < <(ezkey_resolve_mvn_branch_versions "0.2.0" "" "HEAD" "deadbee")
assert_eq "mvn-branch detached revision" "$r" "0.2.0-deadbee"
assert_eq "mvn-branch detached changelist" "$c" "-SNAPSHOT"

# Refuse empty changelist (explicit helper used by override path)
if ezkey_require_nonempty_changelist "" "unit-test" 2>/tmp/ezkey-changelist-refuse.err; then
  echo "FAIL: empty changelist should be refused" >&2
  failures=$((failures + 1))
else
  echo "PASS: empty changelist refused"
fi

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
