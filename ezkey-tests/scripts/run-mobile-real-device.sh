#!/usr/bin/env bash
#
# Ezkey real-device mobile campaign runner (JUnit API truth + Maestro UI + compact RCA).
#
# Usage:
#   ./ezkey-tests/scripts/run-mobile-real-device.sh --enrollment-id 12 --iterations 3
#   ./ezkey-tests/scripts/run-mobile-real-device.sh --bootstrap-f2a --auth-url http://192.168.1.10:8080
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EZKEY_TESTS_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
PROJECT_ROOT="$(cd "${EZKEY_TESTS_DIR}/.." && pwd)"
MOBILE_ROOT="${PROJECT_ROOT}/ezkey_mobile"
# shellcheck source=lib/mobile-real-device-rca.sh
source "${SCRIPT_DIR}/lib/mobile-real-device-rca.sh"
# shellcheck source=../../ezkey_mobile/scripts/lib/android-stay-awake.sh
source "${MOBILE_ROOT}/scripts/lib/android-stay-awake.sh"

ENROLLMENT_ID=""
BOOTSTRAP_F2A=false
AUTH_URL="${EZKEY_ENROLLMENT_AUTH_URL:-${EZKEY_API_BASE_URL:-}}"
ITERATIONS=1
SCENARIOS="approve"
SEED=""
SESSION_DIR=""
LOGCAT_ON=true
STAY_AWAKE=true
MAESTRO_VERBOSE="${MAESTRO_VERBOSE:-0}"
LOGCAT_PID=""

cleanup_logcat() {
  if [[ -n "${LOGCAT_PID}" ]] && kill -0 "${LOGCAT_PID}" 2>/dev/null; then
    kill "${LOGCAT_PID}" 2>/dev/null || true
    wait "${LOGCAT_PID}" 2>/dev/null || true
  fi
  LOGCAT_PID=""
}
campaign_cleanup() {
  cleanup_logcat
  ezkey_android_stay_awake_restore
}

usage() {
  cat <<'EOF'
Usage: ./ezkey-tests/scripts/run-mobile-real-device.sh [options]

JUnit creates enrollments/auth attempts; Maestro drives the Pixel; Bash writes
session artifacts. On failure, read SESSION-table.md then iterations/<n>/rca.md
before opening maestro.log.

Options:
  --enrollment-id N       Reuse an enrollment already on the phone Home list
  --bootstrap-f2a         Create a fresh enrollment (JUnit) and bind via F2a Maestro
  --auth-url URL          Auth API base URL reachable FROM THE PHONE (required with --bootstrap-f2a)
  --iterations N          Loop count (default 1)
  --scenarios LIST        Comma list: approve,approve-challenge,deny,skip-consume
  --seed N                Deterministic shuffle of --scenarios before cycling
  --session-dir PATH      Session root (default: logs/mobile-churn/<utc>)
  --no-logcat             Skip per-iteration logcat capture
  --no-stay-awake         Do not change stay_on_while_plugged_in / screen_off_timeout
  -h, --help              This help

Agent RCA read-order:
  1. SESSION.md and SESSION-table.md
  2. iterations/<n>/rca.md
  3. iterations/<n>/logcat-filtered.txt
  4. Full maestro.log / logcat.txt only if still inconclusive

Examples:
  ./ezkey-tests/scripts/run-mobile-real-device.sh --enrollment-id 2 --iterations 3
  ./ezkey-tests/scripts/run-mobile-real-device.sh --bootstrap-f2a --auth-url http://192.168.1.10:8080 --scenarios approve,deny --iterations 4 --seed 42
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h | --help)
      usage
      exit 0
      ;;
    --enrollment-id)
      ENROLLMENT_ID="$2"
      shift 2
      ;;
    --bootstrap-f2a)
      BOOTSTRAP_F2A=true
      shift
      ;;
    --auth-url)
      AUTH_URL="$2"
      shift 2
      ;;
    --iterations)
      ITERATIONS="$2"
      shift 2
      ;;
    --scenarios)
      SCENARIOS="$2"
      shift 2
      ;;
    --seed)
      SEED="$2"
      shift 2
      ;;
    --session-dir)
      SESSION_DIR="$2"
      shift 2
      ;;
    --no-logcat)
      LOGCAT_ON=false
      shift
      ;;
    --no-stay-awake)
      STAY_AWAKE=false
      shift
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ -z "${JAVA_HOME:-}" ]]; then
  if [[ -d "/c/Tools/jdk-25.0.3+9" ]]; then
    export JAVA_HOME="/c/Tools/jdk-25.0.3+9"
  elif [[ -d "C:/Tools/jdk-25.0.3+9" ]]; then
    export JAVA_HOME="C:/Tools/jdk-25.0.3+9"
  fi
  if [[ -n "${JAVA_HOME:-}" ]]; then
    export PATH="${JAVA_HOME}/bin:${PATH}"
  fi
fi

ensure_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "$1 not found in PATH." >&2
    exit 1
  fi
}

ensure_cmd adb
ensure_cmd maestro
ensure_cmd mvn
ensure_cmd curl

ADMIN_HEALTH_URL="${EZKEY_ADMIN_API_HEALTH_URL:-http://localhost:9081/actuator/health}"
if ! curl -sf "${ADMIN_HEALTH_URL}" >/dev/null; then
  echo "Admin API not healthy at ${ADMIN_HEALTH_URL} (management port 9081 by default). Start the stack (./ezkey-tests/clean-start.sh)." >&2
  exit 1
fi

# JUnit token mint signs with host .ezkey-test/device-credentials.json. After a new
# clean-start, those keys must match the stack bootstrap volume or pending bind fails.
sync_bootstrap_device_credentials() {
  local dest="${EZKEY_TESTS_DIR}/.ezkey-test/device-credentials.json"
  local container="${EZKEY_TESTS_ADMIN_API_CONTAINER:-ezkey-admin-api}"
  local src="/var/lib/ezkey/bootstrap/device-credentials.json"
  mkdir -p "${EZKEY_TESTS_DIR}/.ezkey-test"
  if ! command -v docker >/dev/null 2>&1; then
    echo "  [warn] docker not in PATH; not syncing device-credentials.json" >&2
    return 0
  fi
  if MSYS_NO_PATHCONV=1 docker exec "${container}" test -f "${src}" >/dev/null 2>&1; then
    MSYS_NO_PATHCONV=1 docker exec "${container}" cat "${src}" >"${dest}"
    rm -f "${EZKEY_TESTS_DIR}/.ezkey-test/admin-token.json"
    echo "  synced ${dest} from ${container}:${src} (cleared stale admin-token.json)"
  else
    echo "  [warn] ${container}:${src} missing; JUnit will use existing host device-credentials if present" >&2
  fi
}
sync_bootstrap_device_credentials

if ! adb devices 2>/dev/null | grep -v '^List' | grep -E '[[:space:]]device$' >/dev/null; then
  echo "No Android device in 'device' state. Run 'adb devices'." >&2
  exit 1
fi

if [[ "$STAY_AWAKE" != true ]]; then
  export EZKEY_ANDROID_STAY_AWAKE=0
fi
trap campaign_cleanup EXIT INT TERM
ezkey_android_stay_awake_begin

if [[ "$BOOTSTRAP_F2A" == false && -z "$ENROLLMENT_ID" ]]; then
  echo "Provide --enrollment-id or --bootstrap-f2a." >&2
  exit 1
fi
if [[ "$BOOTSTRAP_F2A" == true && -z "$AUTH_URL" ]]; then
  echo "--bootstrap-f2a requires --auth-url (or EZKEY_ENROLLMENT_AUTH_URL / EZKEY_API_BASE_URL)." >&2
  exit 1
fi

IFS=',' read -r -a SCENARIO_ARR <<<"${SCENARIOS}"
if [[ ${#SCENARIO_ARR[@]} -eq 0 ]]; then
  echo "--scenarios produced an empty list." >&2
  exit 1
fi
if [[ -n "$SEED" ]]; then
  RANDOM="$SEED"
  local_i=${#SCENARIO_ARR[@]}
  while ((local_i > 1)); do
    local_i=$((local_i - 1))
    local_j=$((RANDOM % (local_i + 1)))
    tmp="${SCENARIO_ARR[local_i]}"
    SCENARIO_ARR[local_i]="${SCENARIO_ARR[local_j]}"
    SCENARIO_ARR[local_j]="$tmp"
  done
fi

TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
if [[ -z "$SESSION_DIR" ]]; then
  SESSION_DIR="${PROJECT_ROOT}/logs/mobile-churn/${TIMESTAMP}"
fi
mkdir -p "${SESSION_DIR}/iterations"
STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
DEVICE_SERIAL="$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')"
GIT_SHA="$(git -C "${PROJECT_ROOT}" rev-parse --short HEAD 2>/dev/null || echo unknown)"
SESSION_EXIT=0

start_logcat() {
  local dest="$1"
  cleanup_logcat
  if [[ "$LOGCAT_ON" != true ]]; then
    : >"$dest"
    return 0
  fi
  adb logcat -c >/dev/null 2>&1 || true
  adb logcat -v time '*:S' 'ReactNativeJS:V' 'ReactNative:V' 'EzkeyCrypto:V' >"$dest" 2>&1 &
  LOGCAT_PID=$!
}

pick_scenario() {
  local idx=$((($1 - 1) % ${#SCENARIO_ARR[@]}))
  echo "${SCENARIO_ARR[$idx]}"
}

expected_status_for() {
  case "$1" in
    approve | approve-challenge) echo "ACCEPTED" ;;
    deny) echo "REJECTED" ;;
    skip-consume) echo "PENDING" ;;
    *)
      echo "Unknown scenario: $1" >&2
      exit 1
      ;;
  esac
}

run_mvn_test() {
  local class_name="$1"
  shift
  (
    cd "${PROJECT_ROOT}"
    mvn -q test -pl ezkey-tests -P mobile-real-device-tests -Dtest="${class_name}" "$@"
  )
}

run_maestro_flow() {
  local flow="$1"
  local report_dir="$2"
  mkdir -p "$report_dir"
  (
    cd "${MOBILE_ROOT}"
    ENROLLMENT_ID="${ENROLLMENT_ID}" \
      CHALLENGE_CODE="${CHALLENGE_CODE:-}" \
      ENROLLMENT_PROOF_TOKEN="${ENROLLMENT_PROOF_TOKEN:-}" \
      ENROLLMENT_AUTH_URL="${ENROLLMENT_AUTH_URL:-}" \
      ENROLLMENT_CHALLENGE="${ENROLLMENT_CHALLENGE:-}" \
      FLOW="${flow}" \
      REPORT_DIR="${report_dir}" \
      MAESTRO_VERBOSE="${MAESTRO_VERBOSE}" \
      MAESTRO_LOGCAT=0 \
      ./scripts/run-real-device-pilot-maestro.sh
  )
}

copy_latest_maestro() {
  local raw_dir="$1"
  local dest_dir="$2"
  local latest_xml latest_log
  latest_xml="$(ls -1t "${raw_dir}"/maestro-pilot-*.xml 2>/dev/null | head -n1 || true)"
  latest_log="$(ls -1t "${raw_dir}"/maestro-pilot-*.log 2>/dev/null | head -n1 || true)"
  [[ -n "${latest_xml}" ]] && cp "${latest_xml}" "${dest_dir}/maestro.xml"
  [[ -n "${latest_log}" ]] && cp "${latest_log}" "${dest_dir}/maestro.log"
  [[ -f "${dest_dir}/maestro.log" ]] || : >"${dest_dir}/maestro.log"
}

normalize_device() {
  adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
  adb shell cmd statusbar collapse >/dev/null 2>&1 || true
  adb shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
  sleep 1
}

cat >"${SESSION_DIR}/SESSION.md" <<EOF
# Mobile real-device session

- started_at: ${STARTED_AT}
- git_sha: ${GIT_SHA}
- device_serial: ${DEVICE_SERIAL}
- enrollment_id: ${ENROLLMENT_ID:-pending-f2a}
- bootstrap_f2a: ${BOOTSTRAP_F2A}
- auth_url: ${AUTH_URL:-n/a}
- scenarios: ${SCENARIOS}
- seed: ${SEED:-none}
- iterations: ${ITERATIONS}
- logcat: ${LOGCAT_ON}
- stay_awake: ${STAY_AWAKE}

## Agent RCA read-order

1. This file and SESSION-table.md
2. iterations/<n>/rca.md
3. iterations/<n>/logcat-filtered.txt
4. maestro.log / logcat.txt only if still inconclusive
EOF

echo "== Ezkey mobile real-device campaign =="
echo "  session: ${SESSION_DIR}"
echo "  scenarios: ${SCENARIO_ARR[*]}"
echo "  iterations: ${ITERATIONS}"

normalize_device

if [[ "$BOOTSTRAP_F2A" == true ]]; then
  echo "== F2a bootstrap: create enrollment =="
  SEED_JSON="${SESSION_DIR}/f2a-seed.json"
  JUNIT_LOG="${SESSION_DIR}/junit-create-enrollment.log"
  set +e
  run_mvn_test org.ezkey.tests.mobile.MobileRealDeviceCreateEnrollmentTest \
    -Dmobile.outputFile="${SEED_JSON}" \
    -Dmobile.authUrl="${AUTH_URL}" >"${JUNIT_LOG}" 2>&1
  CREATE_ENROLL_EXIT=$?
  set -e
  if [[ ${CREATE_ENROLL_EXIT} -ne 0 ]]; then
    echo "JUnit create-enrollment failed. See ${JUNIT_LOG}" >&2
    exit "${CREATE_ENROLL_EXIT}"
  fi
  ENROLLMENT_ID="$(json_get_number "${SEED_JSON}" enrollmentId)"
  ENROLLMENT_PROOF_TOKEN="$(json_get "${SEED_JSON}" enrollmentProofToken)"
  ENROLLMENT_AUTH_URL="$(json_get "${SEED_JSON}" enrollmentAuthUrl)"
  ENROLLMENT_CHALLENGE="$(json_get "${SEED_JSON}" enrollmentChallenge)"
  if [[ -z "$ENROLLMENT_ID" ]]; then
    echo "Could not parse enrollmentId from ${SEED_JSON}" >&2
    exit 1
  fi
  echo "- enrollment_id (after F2a): ${ENROLLMENT_ID}" >>"${SESSION_DIR}/SESSION.md"
  echo "  enrollment_id=${ENROLLMENT_ID}"
  echo "== F2a Maestro full runtime =="
  F2A_DIR="${SESSION_DIR}/f2a"
  mkdir -p "${F2A_DIR}"
  F2A_RAW="${F2A_DIR}/maestro-raw"
  start_logcat "${F2A_DIR}/logcat.txt"
  set +e
  run_maestro_flow "${MOBILE_ROOT}/maestro/flows/pilot_enrollment_full_runtime.yaml" "${F2A_RAW}"
  F2A_EXIT=$?
  set -e
  cleanup_logcat
  copy_latest_maestro "${F2A_RAW}" "${F2A_DIR}"
  filter_logcat "${F2A_DIR}/logcat.txt" "${F2A_DIR}/logcat-filtered.txt"
  F2A_ERR="$(maestro_last_error "${F2A_DIR}/maestro.log")"
  F2A_OUTCOME="pass"
  if [[ ${F2A_EXIT} -ne 0 ]]; then
    F2A_OUTCOME="fail_maestro"
  fi
  write_rca "${F2A_DIR}" "${F2A_OUTCOME}" "${F2A_EXIT}" "n/a" \
    "${ENROLLMENT_ID}" "f2a-enroll" "n/a" "none" "${F2A_ERR}"
  unset ENROLLMENT_PROOF_TOKEN ENROLLMENT_AUTH_URL ENROLLMENT_CHALLENGE CHALLENGE_CODE
  if [[ ${F2A_EXIT} -ne 0 ]]; then
    echo "F2a Maestro enroll failed (exit=${F2A_EXIT}). Read ${F2A_DIR}/rca.md first." >&2
    exit "${F2A_EXIT}"
  fi
fi

echo "== Preflight: enrollment ${ENROLLMENT_ID} visible on Home =="
PREFLIGHT_DIR="${SESSION_DIR}/preflight"
mkdir -p "${PREFLIGHT_DIR}"
: >"${PREFLIGHT_DIR}/logcat.txt"
PREFLIGHT_RAW="${PREFLIGHT_DIR}/maestro-raw"
set +e
run_maestro_flow "${MOBILE_ROOT}/maestro/flows/pilot_home_enrollment_visible.yaml" "${PREFLIGHT_RAW}"
PREFLIGHT_EXIT=$?
set -e
copy_latest_maestro "${PREFLIGHT_RAW}" "${PREFLIGHT_DIR}"
filter_logcat "${PREFLIGHT_DIR}/logcat.txt" "${PREFLIGHT_DIR}/logcat-filtered.txt"
PREFLIGHT_ERR="$(maestro_last_error "${PREFLIGHT_DIR}/maestro.log")"
PREFLIGHT_OUTCOME="pass"
if [[ ${PREFLIGHT_EXIT} -ne 0 ]]; then
  PREFLIGHT_OUTCOME="fail_maestro"
fi
write_rca "${PREFLIGHT_DIR}" "${PREFLIGHT_OUTCOME}" "${PREFLIGHT_EXIT}" "n/a" \
  "${ENROLLMENT_ID}" "home-visible" "n/a" "none" "${PREFLIGHT_ERR}"
if [[ ${PREFLIGHT_EXIT} -ne 0 ]]; then
  echo "Preflight failed: enrollment ${ENROLLMENT_ID} not visible on the phone Home list." >&2
  echo "Demo Device JSON does not populate the real phone. Enroll on device (or --bootstrap-f2a)." >&2
  echo "Read ${PREFLIGHT_DIR}/rca.md first." >&2
  exit "${PREFLIGHT_EXIT}"
fi

SUMMARY_JSONL="${SESSION_DIR}/summary.jsonl"
: >"${SUMMARY_JSONL}"

for ((i = 1; i <= ITERATIONS; i++)); do
  ITER="$(printf '%05d' "$i")"
  ITER_DIR="${SESSION_DIR}/iterations/${ITER}"
  mkdir -p "${ITER_DIR}"
  SCENARIO="$(pick_scenario "$i")"
  EXPECTED="$(expected_status_for "$SCENARIO")"
  ITER_STARTED="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  CHALLENGE_REQUESTED=false
  FLOW=""
  RUN_MAESTRO=true
  case "$SCENARIO" in
    approve)
      FLOW="${MOBILE_ROOT}/maestro/flows/pilot_pending_respond.yaml"
      ;;
    approve-challenge)
      CHALLENGE_REQUESTED=true
      FLOW="${MOBILE_ROOT}/maestro/flows/pilot_pending_respond_with_challenge.yaml"
      ;;
    deny)
      FLOW="${MOBILE_ROOT}/maestro/flows/pilot_pending_deny.yaml"
      ;;
    skip-consume)
      RUN_MAESTRO=false
      MAESTRO_EXIT="not-run"
      ;;
  esac

  echo "== Iteration ${ITER} scenario=${SCENARIO} =="
  ATTEMPT_JSON="${ITER_DIR}/junit-auth-attempt.json"
  ASSERT_JSON="${ITER_DIR}/junit-assert.json"
  JUNIT_CREATE_LOG="${ITER_DIR}/junit-create.log"
  JUNIT_ASSERT_LOG="${ITER_DIR}/junit-assert.log"

  set +e
  run_mvn_test org.ezkey.tests.mobile.MobileRealDeviceCreateAuthAttemptTest \
    -Dmobile.enrollmentId="${ENROLLMENT_ID}" \
    -Dmobile.challengeRequested="${CHALLENGE_REQUESTED}" \
    -Dmobile.outputFile="${ATTEMPT_JSON}" >"${JUNIT_CREATE_LOG}" 2>&1
  JUNIT_CREATE_EXIT=$?
  set -e

  AUTH_ATTEMPT_ID="$(json_get_number "${ATTEMPT_JSON}" authAttemptId)"
  CHALLENGE_CODE="$(json_get "${ATTEMPT_JSON}" challengeCode)"
  API_STATUS="$(json_get "${ATTEMPT_JSON}" authAttemptStatus)"

  if [[ ${JUNIT_CREATE_EXIT} -ne 0 || -z "${AUTH_ATTEMPT_ID}" ]]; then
    SESSION_EXIT=1
    write_rca "${ITER_DIR}" "fail_api_assert" "not-run" "${AUTH_ATTEMPT_ID:-unknown}" \
      "${ENROLLMENT_ID}" "${SCENARIO}" "${API_STATUS:-unknown}" "none" "JUnit create-attempt failed"
    printf '{"iteration":"%s","scenario":"%s","outcome":"fail_api_assert","authAttemptId":%s,"maestroExitCode":null,"apiStatusAfter":"%s"}\n' \
      "${ITER}" "${SCENARIO}" "${AUTH_ATTEMPT_ID:-null}" "${API_STATUS:-unknown}" >>"${SUMMARY_JSONL}"
    continue
  fi

  MAESTRO_EXIT="not-run"
  if [[ "$RUN_MAESTRO" == true ]]; then
    start_logcat "${ITER_DIR}/logcat.txt"
    MAESTRO_RAW="${ITER_DIR}/maestro-raw"
    set +e
    run_maestro_flow "${FLOW}" "${MAESTRO_RAW}"
    MAESTRO_EXIT=$?
    set -e
    cleanup_logcat
    copy_latest_maestro "${MAESTRO_RAW}" "${ITER_DIR}"
  else
    : >"${ITER_DIR}/logcat.txt"
    : >"${ITER_DIR}/maestro.log"
  fi

  filter_logcat "${ITER_DIR}/logcat.txt" "${ITER_DIR}/logcat-filtered.txt"
  LAST_STEP="$(last_pending_auth_trace_step "${ITER_DIR}/logcat-filtered.txt")"
  MAESTRO_ERR="$(maestro_last_error "${ITER_DIR}/maestro.log")"

  OUTCOME="pass"
  set +e
  run_mvn_test org.ezkey.tests.mobile.MobileRealDeviceAssertAuthAttemptTest \
    -Dmobile.authAttemptId="${AUTH_ATTEMPT_ID}" \
    -Dmobile.expectedStatus="${EXPECTED}" \
    -Dmobile.outputFile="${ASSERT_JSON}" >"${JUNIT_ASSERT_LOG}" 2>&1
  JUNIT_ASSERT_EXIT=$?
  set -e
  if [[ -f "${ASSERT_JSON}" ]]; then
    API_STATUS="$(json_get "${ASSERT_JSON}" authAttemptStatus)"
  fi
  if [[ "$RUN_MAESTRO" == true && "${MAESTRO_EXIT}" -ne 0 ]]; then
    OUTCOME="fail_maestro"
    SESSION_EXIT=1
  elif [[ ${JUNIT_ASSERT_EXIT} -ne 0 ]]; then
    OUTCOME="fail_api_assert"
    SESSION_EXIT=1
  fi

  ITER_ENDED="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  write_rca "${ITER_DIR}" "${OUTCOME}" "${MAESTRO_EXIT}" "${AUTH_ATTEMPT_ID}" \
    "${ENROLLMENT_ID}" "${SCENARIO}" "${API_STATUS:-unknown}" "${LAST_STEP}" "${MAESTRO_ERR}"

  cat >"${ITER_DIR}/meta.md" <<EOF
# Iteration ${ITER}

- iteration: ${ITER}
- started_at: ${ITER_STARTED}
- ended_at: ${ITER_ENDED}
- enrollment_id: ${ENROLLMENT_ID}
- auth_attempt_id: ${AUTH_ATTEMPT_ID}
- scenario: ${SCENARIO}
- challenge_requested: ${CHALLENGE_REQUESTED}
- maestro_exit_code: ${MAESTRO_EXIT}
- outcome: ${OUTCOME}
EOF

  printf '{"iteration":"%s","scenario":"%s","outcome":"%s","authAttemptId":%s,"maestroExitCode":"%s","apiStatusAfter":"%s"}\n' \
    "${ITER}" "${SCENARIO}" "${OUTCOME}" "${AUTH_ATTEMPT_ID}" "${MAESTRO_EXIT}" "${API_STATUS:-unknown}" >>"${SUMMARY_JSONL}"
done

ENDED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
{
  echo
  echo "- ended_at: ${ENDED_AT}"
  echo "- session_exit: ${SESSION_EXIT}"
} >>"${SESSION_DIR}/SESSION.md"

write_session_table "${SESSION_DIR}"

echo "== Done =="
echo "  session: ${SESSION_DIR}"
echo "  table: ${SESSION_DIR}/SESSION-table.md"
echo "  exit: ${SESSION_EXIT}"
exit "${SESSION_EXIT}"
