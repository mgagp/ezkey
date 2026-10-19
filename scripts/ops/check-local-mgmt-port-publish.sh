#!/usr/bin/env bash
# Fake-only check: local docker-compose.yml publishes management ports on 127.0.0.1
# and healthchecks use 127.0.0.1 (PR B / ADR-0015). No Docker daemon required.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
COMPOSE="${ROOT}/docker/docker-compose.yml"
DEV="${ROOT}/docker/docker-compose.docker-dev.yml"

fail=0
pass=0

need_publish() {
  local port="$1"
  if grep -Eq "127\.0\.0\.1:${port}:${port}" "$COMPOSE"; then
    echo "PASS: compose publishes 127.0.0.1:${port}:${port}"
    pass=$((pass + 1))
  else
    echo "FAIL: missing 127.0.0.1:${port}:${port} in docker-compose.yml" >&2
    fail=$((fail + 1))
  fi
}

need_health() {
  local port="$1"
  if grep -Fq "http://127.0.0.1:${port}/actuator/health" "$COMPOSE"; then
    echo "PASS: healthcheck uses 127.0.0.1:${port}"
    pass=$((pass + 1))
  else
    echo "FAIL: healthcheck must use http://127.0.0.1:${port}/actuator/health" >&2
    fail=$((fail + 1))
  fi
}

# Must NOT bind JVM management to 127.0.0.1 in local compose (PAM peers need Docker network).
if grep -Eq 'MANAGEMENT_SERVER_ADDRESS:[[:space:]]*"?127\.0\.0\.1"?' "$COMPOSE"; then
  echo "FAIL: local compose must not set MANAGEMENT_SERVER_ADDRESS=127.0.0.1" >&2
  fail=$((fail + 1))
else
  echo "PASS: local compose does not set MANAGEMENT_SERVER_ADDRESS=127.0.0.1"
  pass=$((pass + 1))
fi

for p in 9081 8085 7081 8084 9091 8086; do
  need_publish "$p"
  need_health "$p"
done

if grep -Eq '127\.0\.0\.1:9081:9081' "$DEV" \
  && grep -Eq '127\.0\.0\.1:8085:8085' "$DEV" \
  && grep -Eq '127\.0\.0\.1:7081:7081' "$DEV"; then
  echo "PASS: docker-dev overlay keeps 127.0.0.1 management publishes"
  pass=$((pass + 1))
else
  echo "FAIL: docker-compose.docker-dev.yml must publish management on 127.0.0.1" >&2
  fail=$((fail + 1))
fi

# Admin windows profile must not expose heapdump/threaddump.
WIN="${ROOT}/ezkey-admin-api/config/application-windows.properties"
if grep -Eq 'exposure\.include=.*heapdump|exposure\.include=.*threaddump' "$WIN"; then
  echo "FAIL: admin windows profile still exposes heapdump or threaddump" >&2
  fail=$((fail + 1))
else
  echo "PASS: admin windows profile has no heapdump/threaddump"
  pass=$((pass + 1))
fi

echo
echo "Results: ${pass} passed, ${fail} failed"
[[ "${fail}" -eq 0 ]]
