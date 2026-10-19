#!/usr/bin/env bash
# Upload an already-built signed AAB to the Google Play *internal* track.
#
# Usage (from ezkey_mobile/, Git Bash on Windows OK):
#   export EZKEY_PLAY_SERVICE_ACCOUNT_JSON="/path/to/key.json"   # outside the repo
#   ./scripts/play-upload-internal.sh [--dry-run] [--status draft|completed] <aab>
#   ./scripts/play-upload-internal.sh --self-test
#
# Default release status is *draft* (review in Play Console before rolling out).
# Hard-refuses any track other than internal. Never prints the service-account JSON.
#
# Prerequisites (host Python, not app Gradle):
#   pip install -r scripts/requirements-play-upload.txt
#
# Build the AAB first with ./scripts/bundle-release.sh (JDK 17 via resolve-android-jdk.sh).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON_HELPER="${SCRIPT_DIR}/lib/play_upload_internal.py"
PACKAGE_NAME="org.ezkey.mobile"
ALLOWED_TRACK="internal"

DRY_RUN=false
STATUS="draft"
SELF_TEST=false
AAB_PATH=""

usage() {
  sed -n '2,16p' "$0"
}

die() {
  echo "ERROR: $*" >&2
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      usage
      exit 0
      ;;
    --self-test)
      SELF_TEST=true
      shift
      ;;
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    --status)
      [[ $# -ge 2 ]] || die "--status requires draft or completed"
      STATUS="$2"
      shift 2
      ;;
    --track)
      [[ $# -ge 2 ]] || die "--track requires a value"
      if [[ "$2" != "${ALLOWED_TRACK}" ]]; then
        die "Refusing track '$2'. This script only uploads to '${ALLOWED_TRACK}'."
      fi
      shift 2
      ;;
    --*)
      die "Unknown option: $1 (see --help)"
      ;;
    *)
      if [[ -n "${AAB_PATH}" ]]; then
        die "Unexpected extra argument: $1"
      fi
      AAB_PATH="$1"
      shift
      ;;
  esac
done

if [[ "${STATUS}" != "draft" && "${STATUS}" != "completed" ]]; then
  die "--status must be 'draft' or 'completed' (got '${STATUS}')"
fi

resolve_python() {
  if command -v python3 >/dev/null 2>&1; then
    command -v python3
    return 0
  fi
  if command -v python >/dev/null 2>&1; then
    command -v python
    return 0
  fi
  # Windows py launcher (Git Bash)
  if command -v py >/dev/null 2>&1; then
    echo "py -3"
    return 0
  fi
  return 1
}

PYTHON_BIN="$(resolve_python)" || die "Python 3 is required (python3, python, or py -3)"

run_python() {
  # shellcheck disable=SC2086
  ${PYTHON_BIN} "$@"
}

if [[ "${SELF_TEST}" == true ]]; then
  echo "[play-upload-internal] offline self-test"
  run_python "${PYTHON_HELPER}" --self-test

  # Bash-layer: env gate messages (no real key / no API)
  unset EZKEY_PLAY_SERVICE_ACCOUNT_JSON || true
  if out="$(bash "${SCRIPT_DIR}/play-upload-internal.sh" /tmp/no-such.aab 2>&1)"; then
    die "expected failure when EZKEY_PLAY_SERVICE_ACCOUNT_JSON is unset"
  fi
  echo "${out}" | grep -q "EZKEY_PLAY_SERVICE_ACCOUNT_JSON" \
    || die "unset-env error should mention EZKEY_PLAY_SERVICE_ACCOUNT_JSON"

  if out="$(bash "${SCRIPT_DIR}/play-upload-internal.sh" --track production /tmp/x.aab 2>&1)"; then
    die "expected refusal for --track production"
  fi
  echo "${out}" | grep -qi "internal" \
    || die "track refusal should mention internal"

  if out="$(bash "${SCRIPT_DIR}/play-upload-internal.sh" --status shipped /tmp/x.aab 2>&1)"; then
    die "expected refusal for bad --status"
  fi

  echo "[play-upload-internal] self-test OK"
  exit 0
fi

[[ -n "${AAB_PATH}" ]] || die "AAB path is required (see --help)"

if [[ -z "${EZKEY_PLAY_SERVICE_ACCOUNT_JSON:-}" ]]; then
  die "EZKEY_PLAY_SERVICE_ACCOUNT_JSON is unset.
Set it to the Play service-account JSON key path (outside the repo), for example:
  export EZKEY_PLAY_SERVICE_ACCOUNT_JSON=\"\$HOME/.gradle/your-play-sa.json\"
Never commit the key. Package: ${PACKAGE_NAME}; track: ${ALLOWED_TRACK} only."
fi

JSON_PATH="${EZKEY_PLAY_SERVICE_ACCOUNT_JSON}"
if [[ ! -f "${JSON_PATH}" ]]; then
  die "Service account JSON not found at:
  ${JSON_PATH}
Fix EZKEY_PLAY_SERVICE_ACCOUNT_JSON (file must exist; contents are never printed)."
fi

if [[ ! -f "${AAB_PATH}" ]]; then
  die "AAB not found: ${AAB_PATH}"
fi

[[ -f "${PYTHON_HELPER}" ]] || die "Missing helper: ${PYTHON_HELPER}"

echo "Play upload (internal only)"
echo "  package: ${PACKAGE_NAME}"
echo "  track:   ${ALLOWED_TRACK}"
echo "  status:  ${STATUS}"
echo "  dry-run: ${DRY_RUN}"
echo "  aab:     ${AAB_PATH}"
echo "  sa json: (path set, not printed)"

PY_ARGS=("${PYTHON_HELPER}" --json "${JSON_PATH}" --status "${STATUS}")
if [[ "${DRY_RUN}" == true ]]; then
  PY_ARGS+=(--dry-run)
fi
PY_ARGS+=("${AAB_PATH}")

run_python "${PY_ARGS[@]}"
