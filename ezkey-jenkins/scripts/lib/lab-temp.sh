#!/usr/bin/env bash
# Portable temp files for Git Bash (Windows) + Linux/macOS.
# Workspace-local under .ezkey/tmp — avoids /tmp MSYS paths.
# On Git Bash, returns Windows-native paths (C:/...) via cygpath -m so
# Windows curl can open --data-urlencode script@FILE / cookie jars.
#
# Usage (from a script that has ROOT=ezkey-jenkins):
#   # shellcheck source=lib/lab-temp.sh
#   source "${ROOT}/scripts/lib/lab-temp.sh"
#   jar="$(ezkey_lab_mktemp)"

# Convert a path for consumers (Windows curl). Identity on Linux/macOS.
ezkey_lab_native_path() {
  local p="${1:-}"
  if [[ -z "${p}" ]]; then
    return 0
  fi
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -m "${p}"
  else
    printf '%s' "${p}"
  fi
}

ezkey_lab_tmpdir() {
  local root="${1:-}"
  if [[ -z "${root}" ]]; then
    root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
  fi
  local d="${root}/.ezkey/tmp"
  mkdir -p "${d}"
  # Best-effort cleanup of stale lab temps older than 1 day (ignore errors).
  find "${d}" -type f -name 'lab.*' -mtime +1 -delete 2>/dev/null || true
  ezkey_lab_native_path "${d}"
}

# Create a temp file under .ezkey/tmp. Optional suffix arg (e.g. .html).
# mktemp uses the Unix path; the printed path is native for Windows curl.
ezkey_lab_mktemp() {
  local root="${EZKEY_JENKINS_ROOT:-}"
  if [[ -z "${root}" ]]; then
    root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
  fi
  local d_unix suffix="${1:-}" path
  d_unix="${root}/.ezkey/tmp"
  mkdir -p "${d_unix}"
  find "${d_unix}" -type f -name 'lab.*' -mtime +1 -delete 2>/dev/null || true
  if [[ -n "${suffix}" ]]; then
    path="$(mktemp "${d_unix}/lab.XXXXXX${suffix}")"
  else
    path="$(mktemp "${d_unix}/lab.XXXXXX")"
  fi
  ezkey_lab_native_path "${path}"
}
