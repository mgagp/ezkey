#!/usr/bin/env bash
# Portable temp files for Git Bash (Windows) + Linux/macOS.
# Workspace-local under .ezkey/tmp — avoids /tmp MSYS paths that Windows-native
# curl cannot open, and removes the need for TMPDIR="$(cygpath -m "$TEMP")".
#
# Usage (from a script that has ROOT=ezkey-jenkins):
#   # shellcheck source=lib/lab-temp.sh
#   source "${ROOT}/scripts/lib/lab-temp.sh"
#   jar="$(ezkey_lab_mktemp)"
ezkey_lab_tmpdir() {
  local root="${1:-}"
  if [[ -z "${root}" ]]; then
    root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
  fi
  local d="${root}/.ezkey/tmp"
  mkdir -p "${d}"
  # Best-effort cleanup of stale lab temps older than 1 day (ignore errors).
  find "${d}" -type f -name 'lab.*' -mtime +1 -delete 2>/dev/null || true
  printf '%s' "${d}"
}

# Create a temp file under .ezkey/tmp. Optional suffix arg (e.g. .html).
ezkey_lab_mktemp() {
  local root="${EZKEY_JENKINS_ROOT:-}"
  if [[ -z "${root}" ]]; then
    root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
  fi
  local d suffix="${1:-}"
  d="$(ezkey_lab_tmpdir "${root}")"
  if [[ -n "${suffix}" ]]; then
    mktemp "${d}/lab.XXXXXX${suffix}"
  else
    mktemp "${d}/lab.XXXXXX"
  fi
}
