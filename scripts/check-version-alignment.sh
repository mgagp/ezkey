#!/usr/bin/env bash
# Fail when versioned artifacts diverge from the parent POM product SemVer (ADR-0017 / #1211).
#
# Usage (from repo root):
#   ./scripts/check-version-alignment.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/product-version.sh
source "${ROOT}/scripts/lib/product-version.sh"

failed=0
fail() {
  echo "::error::$1" >&2
  failed=1
}

ok() {
  echo "OK: $1"
}

POM="${ROOT}/pom.xml"
MAVEN_VERSION="$(ezkey_maven_product_version "$POM")"
NPM_VERSION="$(ezkey_npm_product_version "$MAVEN_VERSION")"
PYTHON_VERSION="$(ezkey_python_product_version "$MAVEN_VERSION")"
ANDROID_NAME="$(ezkey_android_version_name "$MAVEN_VERSION")"

echo "Expected Maven product version: ${MAVEN_VERSION}"
echo "Expected npm version:           ${NPM_VERSION}"
echo "Expected Python (PEP 440):      ${PYTHON_VERSION}"
echo "Expected Android versionName:   ${ANDROID_NAME}"
echo "Android versionCode floor:      ${EZKEY_ANDROID_VERSION_CODE_MIN} (must be > 6)"

# --- Parent revision train (expected value from product-version.sh, not hard-coded here) ---
IFS=$'\t' read -r revision _changelist < <(ezkey_read_pom_ci_versions "$POM")
revision="${revision//$'\r'/}"
if [[ "$revision" != "${EZKEY_PRODUCT_REVISION}" ]]; then
  fail "Parent <revision> must be ${EZKEY_PRODUCT_REVISION} for the current product train (got ${revision}; see EZKEY_PRODUCT_REVISION in scripts/lib/product-version.sh)"
else
  ok "parent <revision> = ${revision} (matches EZKEY_PRODUCT_REVISION)"
fi

# --- Product reactor modules (parent = ezkey-parent) must inherit CI-friendly version ---
while IFS= read -r -d '' pom; do
  rel="${pom#"$ROOT"/}"
  case "$rel" in
    scripts/lib/fixtures/*) continue ;;
    pom.xml) continue ;;
  esac
  if ! grep -q '<artifactId>ezkey-parent</artifactId>' "$pom"; then
    continue
  fi
  if ! grep -qE '<version>\$\{revision\}\$\{changelist\}</version>' "$pom"; then
    fail "Maven module version is not CI-friendly \${revision}\${changelist}: ${rel}"
  fi
done < <(find "$ROOT" -name pom.xml -not -path '*/target/*' -print0)
ok "ezkey-parent reactor modules inherit \${revision}\${changelist}"

check_eq() {
  local label="$1"
  local actual="$2"
  local expected="$3"
  if [[ "$actual" != "$expected" ]]; then
    fail "${label}='${actual}' diverges from expected '${expected}' (run ./scripts/sync-version.sh)"
  else
    ok "${label} = ${actual}"
  fi
}

# --- npm ---
check_eq "ezkey-admin-ui/package.json" \
  "$(ezkey_read_json_version "${ROOT}/ezkey-admin-ui/package.json")" "$NPM_VERSION"
check_eq "ezkey-sdk/javascript/package.json (ezkey-javascript-sdk)" \
  "$(ezkey_read_json_version "${ROOT}/ezkey-sdk/javascript/package.json")" "$NPM_VERSION"
check_eq "ezkey-sdk/javascript/generated/admin/package.json (ezkey-admin-client)" \
  "$(ezkey_read_json_version "${ROOT}/ezkey-sdk/javascript/generated/admin/package.json")" "$NPM_VERSION"
check_eq "ezkey-sdk/javascript/generated/auth/package.json (ezkey-auth-client)" \
  "$(ezkey_read_json_version "${ROOT}/ezkey-sdk/javascript/generated/auth/package.json")" "$NPM_VERSION"
check_eq "ezkey_mobile/package.json" \
  "$(ezkey_read_json_version "${ROOT}/ezkey_mobile/package.json")" "$ANDROID_NAME"

LOCK="${ROOT}/ezkey-admin-ui/package-lock.json"
if [[ -f "$LOCK" ]]; then
  lock_top="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1],encoding="utf-8")).get("version",""))' "$LOCK")"
  check_eq "ezkey-admin-ui/package-lock.json" "$lock_top" "$NPM_VERSION"
  lock_pkg="$(python3 -c 'import json,sys; d=json.load(open(sys.argv[1],encoding="utf-8")); print((d.get("packages") or {}).get("",{}) .get("version",""))' "$LOCK")"
  if [[ -n "$lock_pkg" ]]; then
    check_eq "ezkey-admin-ui/package-lock.json packages[\"\"]" "$lock_pkg" "$NPM_VERSION"
  fi
fi

JS_LOCK="${ROOT}/ezkey-sdk/javascript/package-lock.json"
if [[ -f "$JS_LOCK" ]]; then
  js_lock_top="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1],encoding="utf-8")).get("version",""))' "$JS_LOCK")"
  check_eq "ezkey-sdk/javascript/package-lock.json" "$js_lock_top" "$NPM_VERSION"
fi

# --- Python ---
check_eq "ezkey-sdk/python/pyproject.toml (ezkey-python-sdk)" \
  "$(ezkey_read_pyproject_version "${ROOT}/ezkey-sdk/python/pyproject.toml")" "$PYTHON_VERSION"
check_eq "ezkey-cli-python/setup.py (ezkey-cli)" \
  "$(ezkey_read_setup_py_version "${ROOT}/ezkey-cli-python/setup.py")" "$PYTHON_VERSION"

# .NET: empty shell removed on main (#1232). Gate skips it until a client exists (#1219).
# Reserved rule: PackageVersion may carry -SNAPSHOT; AssemblyVersion / FileVersion must not.

# --- Jenkins lab ---
# ADR-0017 exception (#1211): Jenkins lab plugins (ezkey-lab-auth / ezkey-lab-hello) keep their
# own HPI SemVer outside the product line. This gate only checks that lab scripts/POMs consume
# the Java SDK at the product Maven coordinate — not that plugin <version> matches 0.2.0.
JENKINS_AUTH_POM="${ROOT}/ezkey-jenkins/ezkey-lab-auth/pom.xml"
if [[ -f "$JENKINS_AUTH_POM" ]]; then
  jver="$(python3 - "$JENKINS_AUTH_POM" <<'PY'
import re,sys
t=open(sys.argv[1],encoding="utf-8").read()
m=re.search(r"<ezkey\.sdk\.version>([^<]+)</ezkey\.sdk\.version>",t)
print(m.group(1) if m else "")
PY
)"
  check_eq "ezkey-jenkins/ezkey-lab-auth ezkey.sdk.version" "$jver" "$MAVEN_VERSION"
fi
STANDALONE_POM="${ROOT}/ezkey-jenkins/maven/ezkey-sdk-standalone.pom"
if [[ -f "$STANDALONE_POM" ]]; then
  sver="$(python3 - "$STANDALONE_POM" <<'PY'
import re,sys
t=open(sys.argv[1],encoding="utf-8").read()
m=re.search(r"<artifactId>ezkey-sdk</artifactId>\s*<version>([^<]+)</version>",t)
print(m.group(1) if m else "")
PY
)"
  check_eq "ezkey-jenkins/maven/ezkey-sdk-standalone.pom" "$sver" "$MAVEN_VERSION"
fi
for jf in \
  "${ROOT}/ezkey-jenkins/scripts/ci-plugin-test.sh" \
  "${ROOT}/ezkey-jenkins/scripts/lib/docker-plugin-build.sh" \
  "${ROOT}/ezkey-jenkins/docker/Dockerfile.plugin-build"; do
  [[ -f "$jf" ]] || continue
  rel="${jf#"$ROOT"/}"
  if grep -q "ezkey-sdk-${MAVEN_VERSION}\\.jar" "$jf" && grep -q "\-Dversion=${MAVEN_VERSION}" "$jf"; then
    ok "${rel} SDK jar pin = ${MAVEN_VERSION}"
  else
    fail "${rel} SDK jar pin diverges from ${MAVEN_VERSION} (run ./scripts/sync-version.sh)"
  fi
done

# --- Android versionCode ---
GRADLE="${ROOT}/ezkey_mobile/android/app/build.gradle"
CODE="$(ezkey_read_android_version_code "$GRADLE")"
if (( CODE <= 6 )); then
  fail "android versionCode=${CODE} must be > 6 (Play already shipped versionCode 6)"
elif (( CODE < EZKEY_ANDROID_VERSION_CODE_MIN )); then
  fail "android versionCode=${CODE} is below floor ${EZKEY_ANDROID_VERSION_CODE_MIN}"
else
  ok "android versionCode = ${CODE} (> 6, floor ${EZKEY_ANDROID_VERSION_CODE_MIN})"
fi

# Exclusions (not product-line artifacts — do not fail):
#   product-docs/site/package.json — methodology explorer
#   bruno/package.json — API collection tooling
#   product-docs/methodology-version.properties — methodology pack SemVer

if [[ "$failed" -ne 0 ]]; then
  echo "Version alignment check FAILED" >&2
  exit 1
fi
echo "Version alignment check passed"
