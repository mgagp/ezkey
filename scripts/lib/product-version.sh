#!/usr/bin/env bash
# Shared product-version helpers for sync-version.sh and check-version-alignment.sh.
# Source only — do not execute. Parent POM is the single SemVer source (ADR-0017 / #1211).
#
# shellcheck shell=bash

# Product-train base revision — must match parent pom <revision> (ADR-0017).
# Bump this constant when advancing the product SemVer train; the alignment gate
# compares parent <revision> to this value (do not hard-code the train in the gate).
readonly EZKEY_PRODUCT_REVISION="0.2.0"

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

revision = one("revision").replace("\r", "").strip()
changelist = one("changelist", "").replace("\r", "").strip()
# LF only. Windows CPython print() emits CRLF, and bash `read` keeps the CR.
sys.stdout.buffer.write(f"{revision}\t{changelist}\n".encode("utf-8"))
PY
}

# Maven product coordinate: revision + changelist (e.g. 0.2.0-SNAPSHOT).
ezkey_maven_product_version() {
  local pom="${1:?}"
  local revision changelist
  IFS=$'\t' read -r revision changelist < <(ezkey_read_pom_ci_versions "$pom")
  revision="${revision//$'\r'/}"
  changelist="${changelist//$'\r'/}"
  printf '%s' "${revision}${changelist}"
}

# Resolve -Drevision / -Dchangelist for scripts/mvn-branch.sh.
# Args: base_revision, pom_changelist, branch_name, short_sha
# Prints: revision TAB changelist TAB mode
#   mode=passthrough — main/master: caller runs plain mvn (pom defaults)
#   mode=override    — feature / detached: revision=base-<branch|sha>, changelist always -SNAPSHOT
# Off main/master, empty changelist is refused (never emit a release-looking coordinate).
ezkey_resolve_mvn_branch_versions() {
  local base_revision="${1:?}"
  local _pom_changelist="${2-}"
  local branch_name="${3:?}"
  local short_sha="${4:-unknown}"

  local qualifier=""
  if [[ -z "${branch_name}" || "${branch_name}" == "HEAD" ]]; then
    qualifier="-${short_sha}"
  else
    local sanitized
    sanitized="$(echo "${branch_name}" | sed 's/[^A-Za-z0-9_.-]/-/g' | sed 's/--*/-/g' | sed 's/^-\|-$//g')"
    if [[ "${sanitized}" == "main" || "${sanitized}" == "master" ]]; then
      printf '%s\t%s\t%s\n' "${base_revision}" "${_pom_changelist}" "passthrough"
      return 0
    fi
    qualifier="-${sanitized}"
  fi

  # Always keep -SNAPSHOT off main (e.g. 0.2.0-<branch>-SNAPSHOT), even if pom changelist is empty.
  local changelist="-SNAPSHOT"
  if ! ezkey_require_nonempty_changelist "${changelist}" "off-main branch override"; then
    return 1
  fi
  printf '%s\t%s\t%s\n' "${base_revision}${qualifier}" "${changelist}" "override"
}

# Refuse empty Maven changelist (would emit a release-looking coordinate).
ezkey_require_nonempty_changelist() {
  local changelist="${1-}"
  local context="${2:-changelist}"
  if [[ -z "${changelist}" ]]; then
    echo "error: empty changelist is forbidden (${context})" >&2
    return 1
  fi
  return 0
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

# .NET AssemblyVersion / FileVersion: numeric major.minor.patch.revision only.
# Never include -SNAPSHOT or other SemVer labels (those belong on PackageVersion only).
# No csproj in tree today (#1232); keep the mapping for when a client returns (#1219).
ezkey_dotnet_assembly_file_version() {
  local maven_version="${1:?}"
  local name core
  name="$(ezkey_android_version_name "${maven_version}")"
  if [[ "${name}" =~ ^([0-9]+\.[0-9]+\.[0-9]+) ]]; then
    core="${BASH_REMATCH[1]}"
  else
    core="${name}"
  fi
  printf '%s.0' "${core}"
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

# App-target MARKETING_VERSION only (bundle id org.ezkey.mobile). Debug and Release.
# CURRENT_PROJECT_VERSION is the Apple build integer and is not rewritten here.
ezkey_read_ios_marketing_versions() {
  local pbx="${1:?}"
  python3 - "$pbx" <<'PY'
import sys

path = sys.argv[1]
text = open(path, encoding="utf-8", newline="").read()
app_id = "PRODUCT_BUNDLE_IDENTIFIER = org.ezkey.mobile;"
found = 0
start = 0
key = "buildSettings = {"
while True:
    i = text.find(key, start)
    if i < 0:
        break
    end = text.find("\n", i)
    pos = end + 1
    close = None
    while pos < len(text):
        nl = text.find("\n", pos)
        if nl < 0:
            nl = len(text)
        if text[pos:nl].strip() == "};":
            close = nl
            break
        pos = nl + 1
    if close is None:
        sys.stderr.write(f"error: unclosed buildSettings in {path}\n")
        sys.exit(2)
    block = text[i:close]
    if app_id in block:
        marker = "MARKETING_VERSION = "
        m = block.find(marker)
        if m < 0:
            sys.stderr.write(f"error: app target missing MARKETING_VERSION in {path}\n")
            sys.exit(2)
        value = block[m + len(marker):].split(";", 1)[0].strip().replace("\r", "")
        sys.stdout.buffer.write(f"{value}\n".encode("utf-8"))
        found += 1
    start = close + 1
if found != 2:
    sys.stderr.write(
        f"error: expected 2 app MARKETING_VERSION entries in {path}, got {found}\n"
    )
    sys.exit(2)
PY
}

ezkey_set_ios_marketing_version() {
  local pbx="${1:?}"
  local version="${2:?}"
  python3 - "$pbx" "$version" <<'PY'
import sys

path, version = sys.argv[1], sys.argv[2]
text = open(path, encoding="utf-8", newline="").read()
app_id = "PRODUCT_BUNDLE_IDENTIFIER = org.ezkey.mobile;"
out = []
start = 0
key = "buildSettings = {"
updated = 0
while True:
    i = text.find(key, start)
    if i < 0:
        out.append(text[start:])
        break
    out.append(text[start:i])
    end = text.find("\n", i)
    pos = end + 1
    close = None
    while pos < len(text):
        nl = text.find("\n", pos)
        if nl < 0:
            nl = len(text)
        if text[pos:nl].strip() == "};":
            close = nl
            break
        pos = nl + 1
    if close is None:
        sys.stderr.write(f"error: unclosed buildSettings in {path}\n")
        sys.exit(2)
    block = text[i:close]
    if app_id in block:
        marker = "MARKETING_VERSION = "
        m = block.find(marker)
        if m < 0:
            sys.stderr.write(f"error: app target missing MARKETING_VERSION in {path}\n")
            sys.exit(2)
        semi = block.find(";", m)
        block = block[: m + len(marker)] + version + block[semi:]
        updated += 1
    out.append(block)
    start = close
new = "".join(out)
if updated != 2:
    sys.stderr.write(
        f"error: expected to set 2 app MARKETING_VERSION entries in {path}, set {updated}\n"
    )
    sys.exit(2)
open(path, "w", encoding="utf-8", newline="").write(new)
PY
}
