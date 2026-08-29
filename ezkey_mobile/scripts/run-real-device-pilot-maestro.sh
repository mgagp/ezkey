#!/usr/bin/env bash
# Runs Maestro pilot flows on a connected Android device (TB-2026-0002).
# Usage: ENROLLMENT_ID=123 ./scripts/run-real-device-pilot-maestro.sh
#
# Output:
#   - Console: preflight steps + Maestro (verbose by default).
#   - JUnit:   maestro/reports/maestro-pilot-<UTC>.xml
#   - Log:     maestro/reports/maestro-pilot-<UTC>.log (full transcript)
# Optional env:
#   MAESTRO_VERBOSE=0        — disable maestro --verbose (quieter CI-style runs).
#   MAESTRO_DEBUG_OUTPUT=1   — also pass --debug-output (maestro.log under a debug subfolder).
#   MAESTRO_LOGCAT=1         — clear logcat, then tee adb logcat (ReactNative tags) to maestro-pilot-<UTC>-logcat.txt during the run.
#   EZKEY_ANDROID_STAY_AWAKE=0 — skip stay-awake (also set by campaign --no-stay-awake).
#   EZKEY_ANDROID_STAY_AWAKE_OWNED=1 — parent campaign already applied stay-awake; do not restore on exit.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MOBILE_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPORT_DIR="${REPORT_DIR:-${MOBILE_ROOT}/maestro/reports}"
# shellcheck source=lib/android-stay-awake.sh
source "${SCRIPT_DIR}/lib/android-stay-awake.sh"

LOGCAT_PID=""
maestro_cleanup() {
  if [[ -n "${LOGCAT_PID}" ]] && kill -0 "${LOGCAT_PID}" 2>/dev/null; then
    kill "${LOGCAT_PID}" 2>/dev/null || true
  fi
  ezkey_android_stay_awake_restore
}

echo "== Ezkey Maestro pilot (TB-2026-0002) =="

if [[ -z "${ENROLLMENT_ID:-}" ]]; then
  echo "ENROLLMENT_ID is required (must match Home row testID ezkey.e2e.home.enrollment.<id>)." >&2
  exit 1
fi

if [[ -n "${CHALLENGE_CODE:-}" ]]; then
  FLOW="${FLOW:-${MOBILE_ROOT}/maestro/flows/pilot_pending_respond_with_challenge.yaml}"
else
  FLOW="${FLOW:-${MOBILE_ROOT}/maestro/flows/pilot_pending_respond.yaml}"
fi

echo "  ENROLLMENT_ID=${ENROLLMENT_ID}"
[[ -n "${CHALLENGE_CODE:-}" ]] && echo "  CHALLENGE_CODE=${CHALLENGE_CODE}"
echo "  FLOW=${FLOW}"
echo "  REPORT_DIR=${REPORT_DIR}"

echo "== Preflight =="

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found in PATH." >&2
  exit 1
fi
echo "  [ok] adb: $(command -v adb)"

if ! adb devices 2>/dev/null | grep -v '^List' | grep -E '[[:space:]]device$' >/dev/null; then
  echo "No Android device in 'device' state. Run 'adb devices'." >&2
  exit 1
fi
echo "  [ok] at least one adb device in state 'device'"
trap maestro_cleanup EXIT INT TERM
ezkey_android_stay_awake_begin

if ! command -v maestro >/dev/null 2>&1; then
  echo "Maestro CLI not found in PATH. See https://docs.maestro.dev/getting-started/installing-maestro" >&2
  exit 1
fi
MAESTRO_VERSION_LINE="$(maestro --version 2>/dev/null || echo 'unknown')"
echo "  [ok] maestro: $(command -v maestro) (${MAESTRO_VERSION_LINE})"

echo "  [info] normalizing device UI state (wake + unlock swipe + collapse notifications)..."
adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
adb shell input swipe 540 2100 540 400 250 >/dev/null 2>&1 || true
adb shell cmd statusbar collapse >/dev/null 2>&1 || true
adb shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
sleep 1

mkdir -p "${REPORT_DIR}"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
REPORT_FILE="${REPORT_DIR}/maestro-pilot-${TIMESTAMP}.xml"
LOG_FILE="${REPORT_DIR}/maestro-pilot-${TIMESTAMP}.log"
LOGCAT_FILE="${REPORT_DIR}/maestro-pilot-${TIMESTAMP}-logcat.txt"

MAESTRO_ARGS=(test "${FLOW}" -e "ENROLLMENT_ID=${ENROLLMENT_ID}")
if [[ -n "${CHALLENGE_CODE:-}" ]]; then
  MAESTRO_ARGS+=( -e "CHALLENGE_CODE=${CHALLENGE_CODE}" )
fi
if [[ -n "${ENROLLMENT_PROOF_TOKEN:-}" ]]; then
  MAESTRO_ARGS+=( -e "ENROLLMENT_PROOF_TOKEN=${ENROLLMENT_PROOF_TOKEN}" )
fi
if [[ -n "${ENROLLMENT_AUTH_URL:-}" ]]; then
  MAESTRO_ARGS+=( -e "ENROLLMENT_AUTH_URL=${ENROLLMENT_AUTH_URL}" )
fi
if [[ -n "${ENROLLMENT_CHALLENGE:-}" ]]; then
  MAESTRO_ARGS+=( -e "ENROLLMENT_CHALLENGE=${ENROLLMENT_CHALLENGE}" )
fi

EXTRA_END=(--format junit --output "${REPORT_FILE}")

if [[ "${MAESTRO_DEBUG_OUTPUT:-0}" == "1" ]]; then
  DEBUG_DIR="${REPORT_DIR}/debug-${TIMESTAMP}"
  mkdir -p "${DEBUG_DIR}"
  EXTRA_END+=(--debug-output "${DEBUG_DIR}")
  echo "  [ok] MAESTRO_DEBUG_OUTPUT=1 → ${DEBUG_DIR}"
fi

echo "== Maestro (verbose console + log) =="
echo "  JUnit → ${REPORT_FILE}"
echo "  Log   → ${LOG_FILE}"
if [[ "${MAESTRO_LOGCAT:-0}" == "1" ]]; then
  echo "  Logcat → ${LOGCAT_FILE} (MAESTRO_LOGCAT=1)"
fi
echo ""

if [[ "${MAESTRO_LOGCAT:-0}" == "1" ]]; then
  adb logcat -c >/dev/null 2>&1 || true
  # RN JS logs typically use ReactNativeJS; include ReactNative for native bridge noise if needed.
  adb logcat -v time '*:S' 'ReactNativeJS:V' 'ReactNative:V' 2>&1 | tee "${LOGCAT_FILE}" &
  LOGCAT_PID=$!
fi

VERBOSE_PREFIX=()
if [[ "${MAESTRO_VERBOSE:-1}" != "0" ]]; then
  VERBOSE_PREFIX=(--verbose)
fi

set +e
if [[ "${#VERBOSE_PREFIX[@]}" -gt 0 ]]; then
  maestro "${VERBOSE_PREFIX[@]}" "${MAESTRO_ARGS[@]}" "${EXTRA_END[@]}" 2>&1 | tee "${LOG_FILE}"
else
  maestro "${MAESTRO_ARGS[@]}" "${EXTRA_END[@]}" 2>&1 | tee "${LOG_FILE}"
fi
STATUS=${PIPESTATUS[0]}
set -e

echo ""
echo "== Result =="
echo "  Maestro exit code: ${STATUS}"
echo "  JUnit report: ${REPORT_FILE}"
echo "  Full log:     ${LOG_FILE}"
if [[ "${MAESTRO_LOGCAT:-0}" == "1" ]]; then
  echo "  Logcat:       ${LOGCAT_FILE}"
fi
if [[ "${MAESTRO_DEBUG_OUTPUT:-0}" == "1" ]]; then
  echo "  Debug dir:    ${DEBUG_DIR:-}"
fi
exit "${STATUS}"
