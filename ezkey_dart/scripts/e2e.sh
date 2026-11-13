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
set -euo pipefail

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
