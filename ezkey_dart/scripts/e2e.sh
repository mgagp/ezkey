#!/usr/bin/env bash
# Runs the ezkey_dart end-to-end protocol suite (bin/ezkey_e2e.dart) against the local Docker
# stack. Host requirement: Docker (the Dart toolchain runs in a container on the stack network).
#
# Usage:
#   ezkey_dart/scripts/e2e.sh                 # stack already up (clean-start done)
#   ezkey_dart/scripts/e2e.sh --clean-start   # run ezkey-tests/clean-start.sh first
#   ezkey_dart/scripts/e2e.sh -- --skip-expiry  # extra args go to bin/ezkey_e2e.dart
#
# Environment:
#   EZKEY_DOCKER_NETWORK   stack network (default: ezkey_ezkey-network)
#   EZKEY_DEMO_DEVICE_CONTAINER  (default: ezkey-demo-device)
#   EZKEY_*_URL            override service URLs (defaults: Docker service hostnames)
#
# Git Bash (MSYS/MINGW): Docker Desktop cannot mount an MSYS /tmp path, and Git Bash
# rewrites arguments that start with / (the /secrets mount and /app/... inside
# docker exec). This script sets MSYS_NO_PATHCONV=1 and, when TMPDIR is unset or
# /tmp, points TMPDIR at a Windows path (cygpath -m of %LOCALAPPDATA%\Temp, else
# TEMP/TMP) before mktemp.
set -euo pipefail

shell_uname() {
  printf '%s\n' "${EZKEY_UNAME_S:-$(uname -s 2>/dev/null || true)}"
}

prepare_git_bash_docker() {
  local uname_s
  uname_s="$(shell_uname)"
  case "${uname_s}" in
    MINGW*|MSYS*|CYGWIN*) ;;
    *)
      return 0
      ;;
  esac
  export MSYS_NO_PATHCONV=1
  if [[ -n "${TMPDIR:-}" && "${TMPDIR}" != /tmp && "${TMPDIR}" != /tmp/ ]]; then
    return 0
  fi
  local win_tmp=""
  local raw=""
  if [[ -n "${LOCALAPPDATA:-}" ]]; then
    raw="${LOCALAPPDATA}/Temp"
  elif [[ -n "${TEMP:-}" ]]; then
    raw="${TEMP}"
  elif [[ -n "${TMP:-}" ]]; then
    raw="${TMP}"
  fi
  if [[ -n "${raw}" ]] && command -v cygpath >/dev/null 2>&1; then
    win_tmp="$(cygpath -m "${raw}")"
  elif [[ "${raw}" =~ ^[A-Za-z]:[/\\] ]]; then
    win_tmp="${raw//\\//}"
  fi
  if [[ -z "${win_tmp}" || ! "${win_tmp}" =~ ^[A-Za-z]:/ ]]; then
    echo "e2e.sh: Git Bash needs a Windows TMPDIR that Docker can mount (C:/...)." >&2
    echo "       set TMPDIR, or install cygpath and set LOCALAPPDATA, TEMP, or TMP." >&2
    exit 1
  fi
  mkdir -p "${win_tmp}"
  export TMPDIR="${win_tmp}"
}

prepare_git_bash_docker

if [[ "${EZKEY_E2E_PREPARE_ONLY:-}" == 1 ]]; then
  printf 'MSYS_NO_PATHCONV=%s\n' "${MSYS_NO_PATHCONV-}"
  printf 'TMPDIR=%s\n' "${TMPDIR-}"
  exit 0
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_DIR="$(cd "${MODULE_DIR}/.." && pwd)"
NETWORK="${EZKEY_DOCKER_NETWORK:-ezkey_ezkey-network}"
DEMO_DEVICE="${EZKEY_DEMO_DEVICE_CONTAINER:-ezkey-demo-device}"
BOOTSTRAP_ENROLLMENT_ID="${EZKEY_BOOTSTRAP_ENROLLMENT_ID:-1}"

clean_start=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --clean-start)
      clean_start=true
      shift
      ;;
    --)
      shift
      break
      ;;
    *)
      echo "unknown option: $1 (pass suite options after --)" >&2
      exit 2
      ;;
  esac
done

if [[ "$clean_start" == true ]]; then
  (cd "${REPO_DIR}/ezkey-tests" && ./clean-start.sh)
fi

wait_healthy() {
  local container="$1"
  for _ in $(seq 1 90); do
    if [[ "$(docker inspect -f '{{.State.Health.Status}}' "$container" 2>/dev/null)" == "healthy" ]]; then
      return 0
    fi
    sleep 2
  done
  echo "container $container is not healthy" >&2
  return 1
}

for c in ezkey-admin-api ezkey-auth-api ezkey-integration-api ezkey-crypto-api "$DEMO_DEVICE"; do
  wait_healthy "$c"
done

# The Demo Device record holds the bootstrap admin's device private key and proof token: keep it
# in an owner-only temp file, mount it read-only, delete it on exit, never print it.
secret_dir="$(mktemp -d)"
chmod 700 "$secret_dir"
trap 'rm -rf "$secret_dir"' EXIT
record="${secret_dir}/demo-device-enrollment.json"
umask 077
for _ in $(seq 1 30); do
  if docker exec "$DEMO_DEVICE" cat "/app/data/enrollments/${BOOTSTRAP_ENROLLMENT_ID}.json" >"$record" 2>/dev/null; then
    break
  fi
  sleep 2
done
if [[ ! -s "$record" ]]; then
  echo "Demo Device has no enrollment ${BOOTSTRAP_ENROLLMENT_ID} (bootstrap bind not done?)" >&2
  exit 1
fi

"${SCRIPT_DIR}/dart-docker.sh" pub get >/dev/null
# Not exec: the EXIT trap must still delete the secret copy.
"${SCRIPT_DIR}/dart-docker.sh" \
  --network "$NETWORK" \
  --mount "${secret_dir}:/secrets:ro" \
  -- run bin/ezkey_e2e.dart --demo-device-enrollment /secrets/demo-device-enrollment.json "$@"
