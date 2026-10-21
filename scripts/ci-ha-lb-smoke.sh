#!/usr/bin/env bash
#
# CI / local smoke: start the HA Docker stack, then call each load balancer
# with an expected HTTP status (must not be 503 — regression #905 / PR #889).
#
# Usage (repo root):
#   ./scripts/ci-ha-lb-smoke.sh
#   ./scripts/ci-ha-lb-smoke.sh --skip-start   # probe an already-running HA stack
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# shellcheck source=lib/ci-ha-lb-smoke-lib.sh
source "${ROOT}/scripts/lib/ci-ha-lb-smoke-lib.sh"

SKIP_START=0
KEEP_STACK=0

usage() {
  cat <<'EOF'
Usage: ./scripts/ci-ha-lb-smoke.sh [--skip-start] [--keep-stack]

  --skip-start   Do not run clean-start; probe localhost HA LB ports only
  --keep-stack   Leave the HA stack running after probes (default: tear down)
  -h, --help     Show this help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-start) SKIP_START=1; shift ;;
    --keep-stack) KEEP_STACK=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

cleanup() {
  if [[ "${KEEP_STACK}" -eq 1 ]]; then
    echo "Keeping HA stack (--keep-stack)"
    return 0
  fi
  if [[ "${SKIP_START}" -eq 1 ]]; then
    return 0
  fi
  echo "==> Tearing down HA stack"
  (cd "${ROOT}/docker" && docker compose -f docker-compose.ha.yml -p ezkey-ha down --remove-orphans) || true
}

trap cleanup EXIT

if [[ "${SKIP_START}" -eq 0 ]]; then
  echo "==> HA clean-start (no Caddy proxy)"
  # Encryption keys + full HA compose via the canonical entrypoint.
  bash "${ROOT}/ezkey-tests/clean-start.sh" --ha --no-proxy
fi

echo "==> Probing HA load balancers (expect no 503; ${PROBE_ATTEMPTS}×${PROBE_SLEEP_S}s budget)"
# Admin + Auth: unsigned public instance-info through HAProxy business ports.
probe_lb "admin-lb" "http://127.0.0.1:9080/api/v1/public/instance-info" "200"
probe_lb "auth-lb" "http://127.0.0.1:8080/api/v1/public/instance-info" "200"
# Integration: no public instance-info; OpenAPI docs are anonymous 200 when backends are up.
probe_lb "integration-lb" "http://127.0.0.1:7080/api-docs" "200"
# Crypto / Demo Device are not behind HAProxy; host management publish must still answer (#905).
probe_lb "crypto-mgmt" "http://127.0.0.1:9091/actuator/health/liveness" "200"
probe_lb "demo-device-mgmt" "http://127.0.0.1:8084/actuator/health" "200"

echo "HA LB smoke PASSED"
