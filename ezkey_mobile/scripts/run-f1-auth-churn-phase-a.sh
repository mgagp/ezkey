#!/usr/bin/env bash
# F1 Phase A real-device churn harness (single deterministic iteration).
#
# Orchestrates:
# 1) JUnit creates one auth attempt for a provided enrollment id
# 2) Maestro consumes the pending attempt on the connected phone
# 3) Correlated raw artifacts are normalized into iteration files
#
# Usage:
#   ENROLLMENT_ID=123 ./scripts/run-f1-auth-churn-phase-a.sh
#
# Optional env:
#   SESSION_ROOT=/abs/path/to/session-root
#   CHALLENGE_REQUESTED=false
#   CHALLENGE_CODE=42
#   FLOW=/abs/path/to/maestro/flow.yaml
#   MAESTRO_VERBOSE=1
#   MAESTRO_DEBUG_OUTPUT=0
#   MAESTRO_LOGCAT=1
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MOBILE_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
PROJECT_ROOT="$(cd "${MOBILE_ROOT}/.." && pwd)"

if [[ -z "${ENROLLMENT_ID:-}" ]]; then
  echo "ENROLLMENT_ID is required." >&2
  exit 1
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found in PATH." >&2
  exit 1
fi

if ! adb devices 2>/dev/null | grep -v '^List' | grep -E '[[:space:]]device$' >/dev/null; then
  echo "No Android device in 'device' state. Run 'adb devices'." >&2
  exit 1
fi

if ! command -v mvn >/dev/null 2>&1; then
  echo "mvn not found in PATH." >&2
  exit 1
fi

if ! command -v maestro >/dev/null 2>&1; then
  echo "maestro not found in PATH." >&2
  exit 1
fi

CHALLENGE_REQUESTED="${CHALLENGE_REQUESTED:-false}"
MAESTRO_LOGCAT="${MAESTRO_LOGCAT:-1}"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
SESSION_ROOT="${SESSION_ROOT:-${MOBILE_ROOT}/maestro/sessions/f1-phase-a-${TIMESTAMP}}"
ITERATION_DIR="${SESSION_ROOT}/iterations/00001"
MAESTRO_RAW_DIR="${ITERATION_DIR}/maestro-raw"
ATTEMPT_JSON="${ITERATION_DIR}/junit-auth-attempt.json"
JUNIT_LOG="${ITERATION_DIR}/junit-side.log"
SUMMARY_JSONL="${SESSION_ROOT}/summary.jsonl"
SESSION_MD="${SESSION_ROOT}/SESSION.md"
META_MD="${ITERATION_DIR}/meta.md"
PREFLIGHT_FLOW="${ITERATION_DIR}/preflight-ready-check.yaml"
PREFLIGHT_LOG="${ITERATION_DIR}/preflight-maestro.log"
PREFLIGHT_XML="${ITERATION_DIR}/preflight-maestro.xml"
STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

mkdir -p "${ITERATION_DIR}" "${MAESTRO_RAW_DIR}"

DEVICE_SERIAL="$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')"
GIT_SHA="$(git -C "${PROJECT_ROOT}" rev-parse --short HEAD 2>/dev/null || echo unknown)"

cat > "${SESSION_MD}" <<EOF
# F1 Phase A session

- started_at: ${STARTED_AT}
- git_sha: ${GIT_SHA}
- device_serial: ${DEVICE_SERIAL}
- enrollment_id: ${ENROLLMENT_ID}
- challenge_requested: ${CHALLENGE_REQUESTED}
- preflight_required: home root + target enrollment row visible
- flow: ${FLOW:-${MOBILE_ROOT}/maestro/flows/pilot_pending_respond.yaml}
EOF

echo "== Phase A / Iteration 00001 =="
echo "Session root: ${SESSION_ROOT}"
echo "Running readiness preflight (Maestro)..."

cat > "${PREFLIGHT_FLOW}" <<'EOF'
appId: org.ezkey.mobile
---
- launchApp:
    appId: org.ezkey.mobile
    stopApp: true
- assertVisible:
    id: "ezkey.e2e.home.root"
- scrollUntilVisible:
    element:
      id: "ezkey.e2e.home.enrollment.${ENROLLMENT_ID}"
    direction: DOWN
    timeout: 15000
- assertVisible:
    id: "ezkey.e2e.home.enrollment.${ENROLLMENT_ID}"
EOF

set +e
(
  cd "${MOBILE_ROOT}"
  maestro test "${PREFLIGHT_FLOW}" \
    -e "ENROLLMENT_ID=${ENROLLMENT_ID}" \
    --format junit \
    --output "${PREFLIGHT_XML}"
) 2>&1 | tee "${PREFLIGHT_LOG}"
PREFLIGHT_EXIT=${PIPESTATUS[0]}
set -e

if [[ ${PREFLIGHT_EXIT} -ne 0 ]]; then
  ENDED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  cat > "${META_MD}" <<EOF
# Iteration 00001 metadata

- iteration: 00001
- started_at: ${STARTED_AT}
- ended_at: ${ENDED_AT}
- enrollment_id: ${ENROLLMENT_ID}
- auth_attempt_id: not-created
- auth_attempt_status: not-created
- challenge_requested: ${CHALLENGE_REQUESTED}
- preflight_exit_code: ${PREFLIGHT_EXIT}
- maestro_exit_code: not-run
- outcome: blocked_missing_seed
EOF
  printf '{"iteration":"00001","startedAt":"%s","endedAt":"%s","enrollmentId":%s,"authAttemptId":null,"junitExitCode":null,"preflightExitCode":%s,"maestroExitCode":null,"outcome":"blocked_missing_seed"}\n' \
    "${STARTED_AT}" "${ENDED_AT}" "${ENROLLMENT_ID}" "${PREFLIGHT_EXIT}" >> "${SUMMARY_JSONL}"
  echo "Preflight failed: app is not in steady state for enrollment ${ENROLLMENT_ID}."
  echo "No auth attempt created. Complete/manual-seed enrollment first, then rerun."
  exit ${PREFLIGHT_EXIT}
fi

echo "Creating auth attempt via JUnit..."

set +e
(
  cd "${PROJECT_ROOT}"
  mvn test -pl ezkey-tests -P operational-churn-tests \
    -Dtest=org.ezkey.tests.churn.MobileAuthChurnPhaseAHarnessTest \
    -Df1.enrollmentId="${ENROLLMENT_ID}" \
    -Df1.challengeRequested="${CHALLENGE_REQUESTED}" \
    -Df1.outputFile="${ATTEMPT_JSON}"
) 2>&1 | tee "${JUNIT_LOG}"
JUNIT_EXIT=${PIPESTATUS[0]}
set -e

AUTH_ATTEMPT_ID=""
AUTH_ATTEMPT_STATUS=""
if [[ -f "${ATTEMPT_JSON}" ]]; then
  AUTH_ATTEMPT_ID="$(sed -n 's/.*"authAttemptId"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' "${ATTEMPT_JSON}" | head -n1)"
  AUTH_ATTEMPT_STATUS="$(sed -n 's/.*"authAttemptStatus"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "${ATTEMPT_JSON}" | head -n1)"
fi

if [[ ${JUNIT_EXIT} -ne 0 ]]; then
  ENDED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  cat > "${META_MD}" <<EOF
# Iteration 00001 metadata

- iteration: 00001
- started_at: ${STARTED_AT}
- ended_at: ${ENDED_AT}
- enrollment_id: ${ENROLLMENT_ID}
- auth_attempt_id: ${AUTH_ATTEMPT_ID:-unknown}
- auth_attempt_status: ${AUTH_ATTEMPT_STATUS:-unknown}
- challenge_requested: ${CHALLENGE_REQUESTED}
- maestro_exit_code: not-run
- outcome: fail_api_assert
EOF
  printf '{"iteration":"00001","startedAt":"%s","endedAt":"%s","enrollmentId":%s,"authAttemptId":%s,"junitExitCode":%s,"maestroExitCode":null,"outcome":"fail_api_assert"}\n' \
    "${STARTED_AT}" "${ENDED_AT}" "${ENROLLMENT_ID}" "${AUTH_ATTEMPT_ID:-null}" "${JUNIT_EXIT}" >> "${SUMMARY_JSONL}"
  echo "JUnit auth-attempt step failed (exit=${JUNIT_EXIT})."
  exit ${JUNIT_EXIT}
fi

echo "Running Maestro consume flow..."

MAESTRO_EXIT=0
set +e
(
  cd "${MOBILE_ROOT}"
  ENROLLMENT_ID="${ENROLLMENT_ID}" \
  CHALLENGE_CODE="${CHALLENGE_CODE:-}" \
  FLOW="${FLOW:-}" \
  REPORT_DIR="${MAESTRO_RAW_DIR}" \
  MAESTRO_VERBOSE="${MAESTRO_VERBOSE:-1}" \
  MAESTRO_DEBUG_OUTPUT="${MAESTRO_DEBUG_OUTPUT:-0}" \
  MAESTRO_LOGCAT="${MAESTRO_LOGCAT}" \
  ./scripts/run-real-device-pilot-maestro.sh
) 2>&1 | tee "${ITERATION_DIR}/maestro-invocation.log"
MAESTRO_EXIT=${PIPESTATUS[0]}
set -e

LATEST_XML="$(ls -1t "${MAESTRO_RAW_DIR}"/maestro-pilot-*.xml 2>/dev/null | head -n1 || true)"
LATEST_LOG="$(ls -1t "${MAESTRO_RAW_DIR}"/maestro-pilot-*.log 2>/dev/null | head -n1 || true)"
LATEST_LOGCAT="$(ls -1t "${MAESTRO_RAW_DIR}"/maestro-pilot-*-logcat.txt 2>/dev/null | head -n1 || true)"

[[ -n "${LATEST_XML}" ]] && cp "${LATEST_XML}" "${ITERATION_DIR}/maestro.xml"
[[ -n "${LATEST_LOG}" ]] && cp "${LATEST_LOG}" "${ITERATION_DIR}/maestro.log"
[[ -n "${LATEST_LOGCAT}" ]] && cp "${LATEST_LOGCAT}" "${ITERATION_DIR}/logcat.txt"

ENDED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
OUTCOME="pass"
if [[ ${MAESTRO_EXIT} -ne 0 ]]; then
  OUTCOME="fail_maestro"
fi

cat > "${META_MD}" <<EOF
# Iteration 00001 metadata

- iteration: 00001
- started_at: ${STARTED_AT}
- ended_at: ${ENDED_AT}
- enrollment_id: ${ENROLLMENT_ID}
- auth_attempt_id: ${AUTH_ATTEMPT_ID:-unknown}
- auth_attempt_status: ${AUTH_ATTEMPT_STATUS:-unknown}
- challenge_requested: ${CHALLENGE_REQUESTED}
- maestro_exit_code: ${MAESTRO_EXIT}
- outcome: ${OUTCOME}
EOF

printf '{"iteration":"00001","startedAt":"%s","endedAt":"%s","enrollmentId":%s,"authAttemptId":%s,"authAttemptStatus":"%s","junitExitCode":0,"maestroExitCode":%s,"outcome":"%s"}\n' \
  "${STARTED_AT}" "${ENDED_AT}" "${ENROLLMENT_ID}" "${AUTH_ATTEMPT_ID:-null}" "${AUTH_ATTEMPT_STATUS:-unknown}" "${MAESTRO_EXIT}" "${OUTCOME}" >> "${SUMMARY_JSONL}"

echo "Done."
echo "Session root: ${SESSION_ROOT}"
echo "Iteration: ${ITERATION_DIR}"

exit ${MAESTRO_EXIT}
