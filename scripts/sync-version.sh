#!/usr/bin/env bash
# Align npm / Python / .NET / Android version fields from the parent POM product SemVer
# (ADR-0017 / #1211 step 2). Does not publish artifacts.
#
# Usage (from repo root):
#   ./scripts/sync-version.sh
#   ./scripts/sync-version.sh --dry-run
#
# Mapping rules (documented in docs/VERSIONING_AND_DEPLOY_TRACEABILITY.md):
#   - Maven parent <revision>+<changelist> is the source of truth (e.g. 0.2.0-SNAPSHOT).
#   - npm packages (Admin UI, JS SDK, generated clients) use the Maven string as-is.
#   - .NET PackageVersion uses the Maven string as-is (NuGet prerelease label).
#   - Python (PEP 440) maps trailing -SNAPSHOT → .dev0 (e.g. 0.2.0.dev0).
#   - Mobile package.json / Android versionName strip trailing -SNAPSHOT only.
#   - Android versionCode is raised to at least EZKEY_ANDROID_VERSION_CODE_MIN (never lowered).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/product-version.sh
source "${ROOT}/scripts/lib/product-version.sh"

DRY_RUN=false
for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=true ;;
    -h|--help)
      sed -n '2,20p' "$0"
      exit 0
      ;;
    *)
      echo "error: unknown argument: $arg" >&2
      exit 2
      ;;
  esac
done

POM="${ROOT}/pom.xml"
MAVEN_VERSION="$(ezkey_maven_product_version "$POM")"
NPM_VERSION="$(ezkey_npm_product_version "$MAVEN_VERSION")"
PYTHON_VERSION="$(ezkey_python_product_version "$MAVEN_VERSION")"
ANDROID_NAME="$(ezkey_android_version_name "$MAVEN_VERSION")"

echo "Product Maven version: ${MAVEN_VERSION}"
echo "npm / .NET version:    ${NPM_VERSION}"
echo "Python (PEP 440):      ${PYTHON_VERSION}"
echo "Android versionName:   ${ANDROID_NAME}"
echo "Android versionCode floor: ${EZKEY_ANDROID_VERSION_CODE_MIN}"

maybe_write() {
  local label="$1"
  local current="$2"
  local expected="$3"
  local writer="$4"
  shift 4
  if [[ "$current" == "$expected" ]]; then
    echo "OK (unchanged): ${label} = ${expected}"
    return 0
  fi
  if [[ "$DRY_RUN" == true ]]; then
    echo "DRY-RUN: would set ${label} ${current} → ${expected}"
    return 0
  fi
  "$writer" "$@"
  echo "Updated: ${label} ${current} → ${expected}"
}

# --- npm package.json (+ package-lock when present) ---
NPM_FILES=(
  "${ROOT}/ezkey-admin-ui/package.json"
  "${ROOT}/ezkey-sdk/javascript/package.json"
  "${ROOT}/ezkey-sdk/javascript/generated/admin/package.json"
  "${ROOT}/ezkey-sdk/javascript/generated/auth/package.json"
)
for f in "${NPM_FILES[@]}"; do
  rel="${f#"$ROOT"/}"
  cur="$(ezkey_read_json_version "$f")"
  if [[ "$cur" == "$NPM_VERSION" ]]; then
    echo "OK (unchanged): ${rel} = ${NPM_VERSION}"
  elif [[ "$DRY_RUN" == true ]]; then
    echo "DRY-RUN: would set ${rel} ${cur} → ${NPM_VERSION}"
  else
    ezkey_set_json_version "$f" "$NPM_VERSION"
    echo "Updated: ${rel} ${cur} → ${NPM_VERSION}"
    lock="${f%package.json}package-lock.json"
    # Only the package root lockfiles (not under generated/).
    if [[ -f "$lock" && "$rel" != *generated* ]]; then
      ezkey_set_package_lock_version "$lock" "$NPM_VERSION"
      echo "Updated: ${lock#"$ROOT"/} → ${NPM_VERSION}"
    fi
  fi
done

# Mobile package.json feeds Android versionName — no -SNAPSHOT.
MOBILE_PKG="${ROOT}/ezkey_mobile/package.json"
cur="$(ezkey_read_json_version "$MOBILE_PKG")"
maybe_write "ezkey_mobile/package.json" "$cur" "$ANDROID_NAME" \
  ezkey_set_json_version "$MOBILE_PKG" "$ANDROID_NAME"

# --- Python ---
PYPROJECT="${ROOT}/ezkey-sdk/python/pyproject.toml"
cur="$(ezkey_read_pyproject_version "$PYPROJECT")"
maybe_write "ezkey-sdk/python/pyproject.toml" "$cur" "$PYTHON_VERSION" \
  ezkey_set_pyproject_version "$PYPROJECT" "$PYTHON_VERSION"

SETUP_PY="${ROOT}/ezkey-cli-python/setup.py"
cur="$(ezkey_read_setup_py_version "$SETUP_PY")"
maybe_write "ezkey-cli-python/setup.py" "$cur" "$PYTHON_VERSION" \
  ezkey_set_setup_py_version "$SETUP_PY" "$PYTHON_VERSION"

# --- .NET ---
CSPROJ="${ROOT}/ezkey-sdk/dotnet/src/EzkeySdk/EzkeySdk.csproj"
cur="$(ezkey_read_csproj_package_version "$CSPROJ")"
maybe_write "ezkey-sdk/dotnet/.../EzkeySdk.csproj PackageVersion" "$cur" "$NPM_VERSION" \
  ezkey_set_csproj_package_version "$CSPROJ" "$NPM_VERSION"

# --- Jenkins lab: consume Java SDK at the product Maven version (plugin own SemVer stays separate) ---
JENKINS_AUTH_POM="${ROOT}/ezkey-jenkins/ezkey-lab-auth/pom.xml"
if [[ -f "$JENKINS_AUTH_POM" ]]; then
  cur="$(python3 - "$JENKINS_AUTH_POM" <<'PY'
import re,sys
t=open(sys.argv[1],encoding="utf-8").read()
m=re.search(r"<ezkey\.sdk\.version>([^<]+)</ezkey\.sdk\.version>",t)
print(m.group(1) if m else "")
PY
)"
  if [[ "$cur" != "$MAVEN_VERSION" ]]; then
    if [[ "$DRY_RUN" == true ]]; then
      echo "DRY-RUN: would set ezkey-jenkins ezkey.sdk.version ${cur} → ${MAVEN_VERSION}"
    else
      python3 - "$JENKINS_AUTH_POM" "$MAVEN_VERSION" <<'PY'
import re,sys
path,ver=sys.argv[1],sys.argv[2]
t=open(path,encoding="utf-8").read()
n,t2=re.subn(r"(<ezkey\.sdk\.version>)[^<]+(</ezkey\.sdk\.version>)",rf"\g<1>{ver}\2",t,count=1)
assert n==1
open(path,"w",encoding="utf-8",newline="\n").write(t2)
PY
      echo "Updated: ezkey-jenkins/ezkey-lab-auth ezkey.sdk.version ${cur} → ${MAVEN_VERSION}"
    fi
  else
    echo "OK (unchanged): ezkey-jenkins ezkey.sdk.version = ${MAVEN_VERSION}"
  fi
fi

STANDALONE_POM="${ROOT}/ezkey-jenkins/maven/ezkey-sdk-standalone.pom"
if [[ -f "$STANDALONE_POM" ]]; then
  cur="$(python3 - "$STANDALONE_POM" <<'PY'
import re,sys
t=open(sys.argv[1],encoding="utf-8").read()
m=re.search(r"<artifactId>ezkey-sdk</artifactId>\s*<version>([^<]+)</version>",t)
print(m.group(1) if m else "")
PY
)"
  if [[ "$cur" == "$MAVEN_VERSION" ]]; then
    echo "OK (unchanged): ezkey-jenkins/maven/ezkey-sdk-standalone.pom = ${MAVEN_VERSION}"
  elif [[ "$DRY_RUN" == true ]]; then
    echo "DRY-RUN: would set ezkey-sdk-standalone.pom ${cur} → ${MAVEN_VERSION}"
  else
    python3 - "$STANDALONE_POM" "$MAVEN_VERSION" <<'PY'
import re,sys
path,ver=sys.argv[1],sys.argv[2]
t=open(path,encoding="utf-8").read()
new,n=re.subn(r"(<artifactId>ezkey-sdk</artifactId>\s*<version>)[^<]+(</version>)",rf"\g<1>{ver}\2",t,count=1)
if n!=1:
    raise SystemExit(f"could not replace standalone pom version in {path}")
open(path,"w",encoding="utf-8",newline="\n").write(new)
PY
    echo "Updated: ezkey-jenkins/maven/ezkey-sdk-standalone.pom ${cur} → ${MAVEN_VERSION}"
  fi
fi

# Jenkins install-file scripts / Dockerfile hard-code the SDK jar coordinate.
for jf in \
  "${ROOT}/ezkey-jenkins/scripts/ci-plugin-test.sh" \
  "${ROOT}/ezkey-jenkins/scripts/lib/docker-plugin-build.sh" \
  "${ROOT}/ezkey-jenkins/docker/Dockerfile.plugin-build"; do
  [[ -f "$jf" ]] || continue
  if grep -q "ezkey-sdk-${MAVEN_VERSION}\\.jar" "$jf" && grep -q "\-Dversion=${MAVEN_VERSION}" "$jf"; then
    echo "OK (unchanged): ${jf#"$ROOT"/} SDK jar pin = ${MAVEN_VERSION}"
  else
    if [[ "$DRY_RUN" == true ]]; then
      echo "DRY-RUN: would pin ${jf#"$ROOT"/} SDK jar to ${MAVEN_VERSION}"
    else
      python3 - "$jf" "$MAVEN_VERSION" <<'PY'
import re,sys
path,ver=sys.argv[1],sys.argv[2]
t=open(path,encoding="utf-8").read()
t=re.sub(r"ezkey-sdk-[0-9][^\"'\s]*\.jar", f"ezkey-sdk-{ver}.jar", t)
t=re.sub(r"-Dversion=[0-9][^\"'\s]*", f"-Dversion={ver}", t)
open(path,"w",encoding="utf-8",newline="\n").write(t)
PY
      echo "Updated: ${jf#"$ROOT"/} SDK jar pin → ${MAVEN_VERSION}"
    fi
  fi
done

# --- Android versionCode (monotonic, > Play max) ---
GRADLE="${ROOT}/ezkey_mobile/android/app/build.gradle"
CURRENT_CODE="$(ezkey_read_android_version_code "$GRADLE")"
TARGET_CODE="$CURRENT_CODE"
if (( CURRENT_CODE < EZKEY_ANDROID_VERSION_CODE_MIN )); then
  TARGET_CODE="$EZKEY_ANDROID_VERSION_CODE_MIN"
fi
if [[ "$CURRENT_CODE" == "$TARGET_CODE" ]]; then
  echo "OK (unchanged): android versionCode = ${CURRENT_CODE}"
  # Still refresh the comment to match versionName when writing.
  if [[ "$DRY_RUN" == false ]]; then
    ezkey_set_android_version_code_and_comment "$GRADLE" "$TARGET_CODE" "$ANDROID_NAME"
  fi
else
  if [[ "$DRY_RUN" == true ]]; then
    echo "DRY-RUN: would set android versionCode ${CURRENT_CODE} → ${TARGET_CODE}"
  else
    ezkey_set_android_version_code_and_comment "$GRADLE" "$TARGET_CODE" "$ANDROID_NAME"
    echo "Updated: android versionCode ${CURRENT_CODE} → ${TARGET_CODE}"
  fi
fi

if [[ "$DRY_RUN" == true ]]; then
  echo "Dry-run complete. Re-run without --dry-run to write."
else
  echo "Sync complete. Verify with: ./scripts/check-version-alignment.sh"
fi
