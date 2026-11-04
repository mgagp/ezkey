#!/usr/bin/env bash
# Shared product-version helpers for sync-version.sh and check-version-alignment.sh.
# Source only — do not execute. Parent POM is the single SemVer source (ADR-0017 / #1211).
#
# shellcheck shell=bash

# Minimum Android versionCode for product line 0.2.0 (Play already shipped vc6 = 0.1.2-alpha).
# Must stay strictly greater than the highest shipped Play versionCode.
readonly EZKEY_ANDROID_VERSION_CODE_MIN=7

ezkey_repo_root() {
  local here
  here="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
  echo "${here}"
}

# Read <revision> / <changelist> from the parent pom properties block.
# Prints: revision<TAB>changelist
ezkey_read_pom_ci_versions() {
  local pom="${1:?pom path required}"
  python3 - "$pom" <<'PY'
import re
import sys

path = sys.argv[1]
text = open(path, encoding="utf-8").read()
props = re.search(r"<properties>(.*?)</properties>", text, re.S)
if not props:
    sys.stderr.write(f"error: no <properties> in {path}\n")
    sys.exit(2)
block = props.group(1)

def one(tag: str, default: str | None = None) -> str:
    m = re.search(rf"<{tag}>([^<]*)</{tag}>", block)
    if not m:
        if default is not None:
            return default
        sys.stderr.write(f"error: missing <{tag}> in {path} properties\n")
        sys.exit(2)
    return m.group(1)

revision = one("revision")
changelist = one("changelist", "")
print(f"{revision}\t{changelist}")
PY
}

# Maven product coordinate: revision + changelist (e.g. 0.2.0-SNAPSHOT).
ezkey_maven_product_version() {
  local pom="${1:?}"
  local revision changelist
  IFS=$'\t' read -r revision changelist < <(ezkey_read_pom_ci_versions "$pom")
  printf '%s' "${revision}${changelist}"
}

# Android / Play versionName: strip trailing -SNAPSHOT only (keep -alpha.N etc.).
ezkey_android_version_name() {
  local maven_version="${1:?}"
  if [[ "${maven_version}" == *-SNAPSHOT ]]; then
    printf '%s' "${maven_version%-SNAPSHOT}"
  else
    printf '%s' "${maven_version}"
  fi
}

# npm / NuGet form: keep Maven product string (0.2.0-SNAPSHOT is a valid prerelease label).
ezkey_npm_product_version() {
  printf '%s' "${1:?}"
}

# PEP 440 form for Python: map trailing -SNAPSHOT → .dev0 (e.g. 0.2.0.dev0).
ezkey_python_product_version() {
  local maven_version="${1:?}"
  if [[ "${maven_version}" == *-SNAPSHOT ]]; then
    printf '%s' "${maven_version%-SNAPSHOT}.dev0"
  else
    printf '%s' "${maven_version}"
  fi
}

ezkey_read_json_version() {
  local file="${1:?}"
  python3 -c 'import json,sys; print(json.load(open(sys.argv[1], encoding="utf-8")).get("version",""))' "$file"
}

ezkey_set_json_version() {
  local file="${1:?}"
  local version="${2:?}"
  python3 - "$file" "$version" <<'PY'
import json
import sys

path, version = sys.argv[1], sys.argv[2]
with open(path, encoding="utf-8") as f:
    data = json.load(f)
data["version"] = version
with open(path, "w", encoding="utf-8", newline="\n") as f:
    json.dump(data, f, indent=2, ensure_ascii=False)
    f.write("\n")
PY
}

# Update package-lock.json top-level + packages[""].version when present (npm lockfile v2/v3).
ezkey_set_package_lock_version() {
  local file="${1:?}"
  local version="${2:?}"
  if [[ ! -f "$file" ]]; then
    return 0
  fi
  python3 - "$file" "$version" <<'PY'
import json
import sys

path, version = sys.argv[1], sys.argv[2]
with open(path, encoding="utf-8") as f:
    data = json.load(f)
data["version"] = version
packages = data.get("packages")
if isinstance(packages, dict) and "" in packages and isinstance(packages[""], dict):
    packages[""]["version"] = version
with open(path, "w", encoding="utf-8", newline="\n") as f:
    json.dump(data, f, indent=2, ensure_ascii=False)
    f.write("\n")
PY
}

ezkey_read_pyproject_version() {
  local file="${1:?}"
  python3 - "$file" <<'PY'
import re
import sys

text = open(sys.argv[1], encoding="utf-8").read()
m = re.search(r'(?m)^version\s*=\s*"([^"]+)"\s*$', text)
if not m:
    sys.stderr.write(f"error: version not found in {sys.argv[1]}\n")
    sys.exit(2)
print(m.group(1))
PY
}

ezkey_set_pyproject_version() {
  local file="${1:?}"
  local version="${2:?}"
  python3 - "$file" "$version" <<'PY'
import re
import sys

path, version = sys.argv[1], sys.argv[2]
text = open(path, encoding="utf-8").read()
new, n = re.subn(
    r'(?m)^(version\s*=\s*")[^"]+("\s*)$',
    rf"\g<1>{version}\2",
    text,
    count=1,
)
if n != 1:
    sys.stderr.write(f"error: could not replace version in {path}\n")
    sys.exit(2)
open(path, "w", encoding="utf-8", newline="\n").write(new)
PY
}

ezkey_read_setup_py_version() {
  local file="${1:?}"
  python3 - "$file" <<'PY'
import re
import sys

text = open(sys.argv[1], encoding="utf-8").read()
m = re.search(r'(?m)^\s*version\s*=\s*"([^"]+)"\s*,\s*$', text)
if not m:
    sys.stderr.write(f"error: version= not found in {sys.argv[1]}\n")
    sys.exit(2)
print(m.group(1))
PY
}

ezkey_set_setup_py_version() {
  local file="${1:?}"
  local version="${2:?}"
  python3 - "$file" "$version" <<'PY'
import re
import sys

path, version = sys.argv[1], sys.argv[2]
text = open(path, encoding="utf-8").read()
new, n = re.subn(
    r'(?m)^(\s*version\s*=\s*")[^"]+("\s*,\s*)$',
    rf"\g<1>{version}\2",
    text,
    count=1,
)
if n != 1:
    sys.stderr.write(f"error: could not replace version in {path}\n")
    sys.exit(2)
open(path, "w", encoding="utf-8", newline="\n").write(new)
PY
}

ezkey_read_csproj_package_version() {
  local file="${1:?}"
  python3 - "$file" <<'PY'
import re
import sys

text = open(sys.argv[1], encoding="utf-8").read()
m = re.search(r"<PackageVersion>([^<]+)</PackageVersion>", text)
if not m:
    sys.stderr.write(f"error: PackageVersion not found in {sys.argv[1]}\n")
    sys.exit(2)
print(m.group(1))
PY
}

ezkey_set_csproj_package_version() {
  local file="${1:?}"
  local version="${2:?}"
  python3 - "$file" "$version" <<'PY'
import re
import sys

path, version = sys.argv[1], sys.argv[2]
text = open(path, encoding="utf-8").read()
new, n = re.subn(
    r"(<PackageVersion>)[^<]+(</PackageVersion>)",
    rf"\g<1>{version}\2",
    text,
    count=1,
)
if n != 1:
    sys.stderr.write(f"error: could not replace PackageVersion in {path}\n")
    sys.exit(2)
open(path, "w", encoding="utf-8", newline="\n").write(new)
PY
}

ezkey_read_android_version_code() {
  local gradle="${1:?}"
  python3 - "$gradle" <<'PY'
import re
import sys

text = open(sys.argv[1], encoding="utf-8").read()
m = re.search(r"(?m)^\s*versionCode\s+(\d+)\s*$", text)
if not m:
    sys.stderr.write(f"error: versionCode not found in {sys.argv[1]}\n")
    sys.exit(2)
print(m.group(1))
PY
}

ezkey_set_android_version_code_and_comment() {
  local gradle="${1:?}"
  local code="${2:?}"
  local name="${3:?}"
  python3 - "$gradle" "$code" "$name" <<'PY'
import re
import sys

path, code, name = sys.argv[1], sys.argv[2], sys.argv[3]
text = open(path, encoding="utf-8").read()
new, n = re.subn(
    r"(?m)^(\s*versionCode\s+)\d+(\s*)$",
    rf"\g<1>{code}\2",
    text,
    count=1,
)
if n != 1:
    sys.stderr.write(f"error: could not replace versionCode in {path}\n")
    sys.exit(2)
new2, n2 = re.subn(
    r"(?m)^(\s*// Next Play upload: versionCode ).*$",
    rf"\g<1>{code} / versionName {name} (see MOBILE_PLAY_PUBLISHING.md).",
    new,
    count=1,
)
if n2 == 0:
    new2, _ = re.subn(
        r"(?m)^(\s*)versionCode\s+\d+\s*$",
        rf"\1// Next Play upload: versionCode {code} / versionName {name} (see MOBILE_PLAY_PUBLISHING.md).\n\1versionCode {code}",
        new,
        count=1,
    )
open(path, "w", encoding="utf-8", newline="\n").write(new2)
PY
}
