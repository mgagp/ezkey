#!/usr/bin/env bash
# Fail a release / production-intent native build when test-only .env flags are still enabled.
# Sourced by build-install-release-clean.sh; can also be run standalone from ezkey_mobile/.
#
# Contract: docs/MOBILE_TEST_AUTOMATION_PRODUCTION_CLEAN.md
set -euo pipefail

MOBILE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENVFILE:-${MOBILE_ROOT}/.env}"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Release production-clean check: no .env at ${ENV_FILE} (ok — defaults keep test flags off)."
  return 0 2>/dev/null || exit 0
fi

# Keys that must not be true/1/yes when assembling a release artifact.
FORBIDDEN_TRUE_KEYS=(
  EZKEY_ENROLLMENT_SEED_BYPASS_ENABLED
  EZKEY_ENROLLMENT_SEED_RAW_DUMP
  EZKEY_PENDING_AUTH_FLOW_TRACE
  EZKEY_PENDING_AUTH_DEBUG_PANEL
)

is_truthy() {
  local v
  v="$(echo "$1" | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]')"
  [[ "$v" == "1" || "$v" == "true" || "$v" == "yes" ]]
}

failures=0
while IFS= read -r line || [[ -n "$line" ]]; do
  # Strip comments and blank lines
  [[ "$line" =~ ^[[:space:]]*# ]] && continue
  [[ -z "${line//[[:space:]]/}" ]] && continue
  key="${line%%=*}"
  val="${line#*=}"
  key="$(echo "$key" | tr -d '[:space:]')"
  for forbidden in "${FORBIDDEN_TRUE_KEYS[@]}"; do
    if [[ "$key" == "$forbidden" ]] && is_truthy "$val"; then
      echo "ERROR: ${forbidden}=${val} in ${ENV_FILE}" >&2
      echo "  Release builds must keep test-only instrumentation/bypass flags off." >&2
      echo "  See docs/MOBILE_TEST_AUTOMATION_PRODUCTION_CLEAN.md" >&2
      failures=$((failures + 1))
    fi
  done
done < "${ENV_FILE}"

if [[ "$failures" -gt 0 ]]; then
  echo "Release production-clean preflight failed (${failures} flag(s))." >&2
  echo "Use a debug build for Maestro/F2a, or set the flags to false before assembleRelease." >&2
  return 1 2>/dev/null || exit 1
fi

echo "Release production-clean check: test-only flags are off in ${ENV_FILE}."
return 0 2>/dev/null || exit 0
