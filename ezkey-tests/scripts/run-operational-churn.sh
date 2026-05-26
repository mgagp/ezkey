#!/usr/bin/env bash
#
# Ezkey Operational Churn Runner
#
# Runs sustained operational-churn tests (ezkey-tests). These tests are excluded from
# default Surefire runs and must be invoked explicitly (see docs/plan/operational-churn-ezkey.plan.md).
#
# Prerequisites:
#   - Docker stack running (e.g. ./clean-start.sh from project root)
#   - Maven
#
# Usage:
#   ./ezkey-tests/scripts/run-operational-churn.sh --profile light --minutes 30
#   ./ezkey-tests/scripts/run-operational-churn.sh --init   # one-shot peer Global Admin + state file
#
# From ezkey-tests directory:
#   scripts/run-operational-churn.sh
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EZKEY_TESTS_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PROJECT_ROOT="$(cd "$EZKEY_TESTS_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

PROFILE="${CHURN_PROFILE:-medium}"
MINUTES="${CHURN_MAX_MINUTES:-120}"
ITERATIONS=""
LOG_FILE=""
SEED=""
INIT_ONLY=false
EXTRA_MVN=()

usage() {
  cat <<'EOF'
Usage: ./ezkey-tests/scripts/run-operational-churn.sh [options] [-- extra mvn args]

Options:
  --init                   One-shot: provision peer Global Admin (mvn -P operational-churn-init); then exit
  --profile light|medium   Intensity (default: medium, or CHURN_PROFILE)
  --minutes N              Max duration in minutes (default: 120, or CHURN_MAX_MINUTES)
  --iterations N           Stop after N full iterations (optional)
  --log-file PATH          Log file (default: logs/operational-churn-<timestamp>-<pid>.log under project root)
  --seed N                 -Dchurn.random.seed for reproducibility
  -h, --help               This help

Environment:
  SPRING_PROFILES_ACTIVE   Recommended docker,docker-test on services (see docker/README.md)
  EZKEY_ADMIN_TOKEN        Optional injected Global Admin token

Example:
  ./ezkey-tests/scripts/run-operational-churn.sh --profile light --minutes 30
  ./ezkey-tests/scripts/run-operational-churn.sh --init
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h | --help)
      usage
      exit 0
      ;;
    --init)
      INIT_ONLY=true
      shift
      ;;
    --profile)
      PROFILE="$2"
      shift 2
      ;;
    --minutes)
      MINUTES="$2"
      shift 2
      ;;
    --iterations)
      ITERATIONS="$2"
      shift 2
      ;;
    --log-file)
      LOG_FILE="$2"
      shift 2
      ;;
    --seed)
      SEED="$2"
      shift 2
      ;;
    --)
      shift
      EXTRA_MVN+=("$@")
      break
      ;;
    *)
      EXTRA_MVN+=("$1")
      shift
      ;;
  esac
done

if [[ "$INIT_ONLY" == true ]]; then
  echo "Operational churn init: mvn test -pl ezkey-tests -P operational-churn-init"
  mkdir -p logs
  LOG_FILE="${LOG_FILE:-logs/operational-churn-init-$(date +%Y%m%d-%H%M%S)-$$.log}"
  echo "Logging Maven output to: ${LOG_FILE}"
  set +e
  mvn test -pl ezkey-tests -P operational-churn-init 2>&1 | tee "${LOG_FILE}"
  MVN_EXIT=${PIPESTATUS[0]}
  set -e
  exit "${MVN_EXIT}"
fi

mkdir -p logs
if [[ -z "$LOG_FILE" ]]; then
  LOG_FILE="logs/operational-churn-$(date +%Y%m%d-%H%M%S)-$$.log"
fi

echo "Operational churn: profile=${PROFILE} maxDurationMinutes=${MINUTES}"
echo "Logging Maven output to: ${LOG_FILE}"
echo "Project root: ${PROJECT_ROOT}"

MVN_ARGS=(
  test
  -pl
  ezkey-tests
  -P
  operational-churn-tests
  "-Dchurn.profile=${PROFILE}"
  "-Dchurn.maxDurationMinutes=${MINUTES}"
)
if [[ -n "${ITERATIONS}" ]]; then
  MVN_ARGS+=("-Dchurn.maxIterations=${ITERATIONS}")
fi
if [[ -n "${SEED}" ]]; then
  MVN_ARGS+=("-Dchurn.random.seed=${SEED}")
fi
MVN_ARGS+=("${EXTRA_MVN[@]}")

set +e
mvn "${MVN_ARGS[@]}" 2>&1 | tee "${LOG_FILE}"
MVN_EXIT=${PIPESTATUS[0]}
set -e
exit "${MVN_EXIT}"
