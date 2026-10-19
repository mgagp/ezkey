#!/usr/bin/env bash
# Fake-only check: local compose / HA / start-manage scripts / Dockerfile keep
# management actuator ports aligned (PR B / ADR-0015). No Docker daemon required.
#
# Override tree root for unit tests: CHECK_LOCAL_MGMT_ROOT=/path/to/fixture
set -euo pipefail

ROOT="${CHECK_LOCAL_MGMT_ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}"
COMPOSE="${ROOT}/docker/docker-compose.yml"
DEV="${ROOT}/docker/docker-compose.docker-dev.yml"
HA="${ROOT}/docker/docker-compose.ha.yml"
DOCKERFILE="${ROOT}/docker/Dockerfile"
HYBRID="${ROOT}/experimental-hybrid/local/docker-compose.yml"

fail=0
pass=0

need_publish() {
  local file="$1"
  local port="$2"
  local label="$3"
  if grep -Eq "127\.0\.0\.1:${port}:${port}" "$file"; then
    echo "PASS: ${label} publishes 127.0.0.1:${port}:${port}"
    pass=$((pass + 1))
  else
    echo "FAIL: missing 127.0.0.1:${port}:${port} in ${label}" >&2
    fail=$((fail + 1))
  fi
}

need_health_127() {
  local file="$1"
  local port="$2"
  local label="$3"
  if grep -Fq "http://127.0.0.1:${port}/actuator/health" "$file"; then
    echo "PASS: ${label} healthcheck uses 127.0.0.1:${port}"
    pass=$((pass + 1))
  else
    echo "FAIL: ${label} healthcheck must use http://127.0.0.1:${port}/actuator/health" >&2
    fail=$((fail + 1))
  fi
}

need_health_localhost() {
  local file="$1"
  local port="$2"
  local label="$3"
  if grep -Eq "http://localhost:${port}/actuator/health" "$file"; then
    echo "PASS: ${label} actuator health uses localhost:${port}"
    pass=$((pass + 1))
  else
    echo "FAIL: ${label} must probe localhost:${port}/actuator/health" >&2
    fail=$((fail + 1))
  fi
}

forbid_stale_actuator() {
  local file="$1"
  local stale="$2"
  local label="$3"
  if grep -Eq "localhost:${stale}/actuator/health|127\.0\.0\.1:${stale}/actuator/health" "$file"; then
    echo "FAIL: ${label} still probes stale actuator port ${stale}" >&2
    fail=$((fail + 1))
  else
    echo "PASS: ${label} has no stale actuator port ${stale}"
    pass=$((pass + 1))
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
  need_publish "$COMPOSE" "$p" "docker-compose.yml"
  need_health_127 "$COMPOSE" "$p" "docker-compose.yml"
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

# HA compose: crypto/demo management publish + in-container health on 9091/8084.
need_publish "$HA" "9091" "docker-compose.ha.yml"
need_publish "$HA" "8084" "docker-compose.ha.yml"
need_health_localhost "$HA" "9091" "docker-compose.ha.yml crypto"
need_health_localhost "$HA" "8084" "docker-compose.ha.yml demo-device"
forbid_stale_actuator "$HA" "9090" "docker-compose.ha.yml"
forbid_stale_actuator "$HA" "8083" "docker-compose.ha.yml"

# Host-facing start/manage scripts must wait on management ports.
need_health_localhost "${ROOT}/docker/start.sh" "9091" "start.sh crypto"
need_health_localhost "${ROOT}/docker/start-ha.sh" "9091" "start-ha.sh crypto"
need_health_localhost "${ROOT}/docker/start-ha.sh" "8084" "start-ha.sh demo-device"
need_health_localhost "${ROOT}/docker/manage.sh" "9091" "manage.sh crypto"
need_health_localhost "${ROOT}/docker/manage.sh" "8084" "manage.sh demo-device"
need_health_localhost "${ROOT}/docker/manage-ha.sh" "9091" "manage-ha.sh crypto"
need_health_localhost "${ROOT}/docker/manage-ha.sh" "8084" "manage-ha.sh demo-device"
forbid_stale_actuator "${ROOT}/docker/start.sh" "9090" "start.sh"
forbid_stale_actuator "${ROOT}/docker/start-ha.sh" "9090" "start-ha.sh"
forbid_stale_actuator "${ROOT}/docker/start-ha.sh" "8083" "start-ha.sh"
forbid_stale_actuator "${ROOT}/docker/manage.sh" "9090" "manage.sh"
forbid_stale_actuator "${ROOT}/docker/manage.sh" "8083" "manage.sh"
forbid_stale_actuator "${ROOT}/docker/manage-ha.sh" "9090" "manage-ha.sh"
forbid_stale_actuator "${ROOT}/docker/manage-ha.sh" "8083" "manage-ha.sh"

# Dockerfile HEALTHCHECK + EXPOSE for crypto/demo management ports.
if grep -Eq '^EXPOSE[[:space:]]+9090[[:space:]]+9091' "$DOCKERFILE" \
  && grep -Fq 'http://localhost:9091/actuator/health' "$DOCKERFILE"; then
  echo "PASS: Dockerfile crypto-api EXPOSE/HEALTHCHECK use 9091"
  pass=$((pass + 1))
else
  echo "FAIL: Dockerfile crypto-api must EXPOSE 9090 9091 and HEALTHCHECK :9091" >&2
  fail=$((fail + 1))
fi
if grep -Eq '^EXPOSE[[:space:]]+8083[[:space:]]+8084' "$DOCKERFILE" \
  && grep -Fq 'http://localhost:8084/actuator/health' "$DOCKERFILE"; then
  echo "PASS: Dockerfile demo-device EXPOSE/HEALTHCHECK use 8084"
  pass=$((pass + 1))
else
  echo "FAIL: Dockerfile demo-device must EXPOSE 8083 8084 and HEALTHCHECK :8084" >&2
  fail=$((fail + 1))
fi
forbid_stale_actuator "$DOCKERFILE" "9090" "Dockerfile"
forbid_stale_actuator "$DOCKERFILE" "8083" "Dockerfile"

# Hybrid local companions (in-container health).
need_health_localhost "$HYBRID" "9091" "experimental-hybrid local crypto"
need_health_localhost "$HYBRID" "8084" "experimental-hybrid local demo-device"
forbid_stale_actuator "$HYBRID" "9090" "experimental-hybrid local"
forbid_stale_actuator "$HYBRID" "8083" "experimental-hybrid local"

# PAM / quality-gate host probes.
need_health_localhost "${ROOT}/ezkey-pam/scripts/common.sh" "8084" "ezkey-pam common.sh"
if grep -Fq 'http://127.0.0.1:8084/actuator/health' "${ROOT}/scripts/quality-gate.sh"; then
  echo "PASS: quality-gate.sh demo-device health uses 127.0.0.1:8084"
  pass=$((pass + 1))
else
  echo "FAIL: quality-gate.sh must probe 127.0.0.1:8084/actuator/health" >&2
  fail=$((fail + 1))
fi
forbid_stale_actuator "${ROOT}/ezkey-pam/scripts/common.sh" "8083" "ezkey-pam common.sh"
forbid_stale_actuator "${ROOT}/scripts/quality-gate.sh" "8083" "quality-gate.sh"

# Admin windows profile must not expose heapdump/threaddump.
WIN="${ROOT}/ezkey-admin-api/config/application-windows.properties"
if grep -Eq 'exposure\.include=.*heapdump|exposure\.include=.*threaddump' "$WIN"; then
  echo "FAIL: admin windows profile still exposes heapdump or threaddump" >&2
  fail=$((fail + 1))
else
  echo "PASS: admin windows profile has no heapdump/threaddump"
  pass=$((pass + 1))
fi

# docker-dev profiles must not expose threaddump.
for f in \
  "${ROOT}/ezkey-admin-api/config/application-docker-dev.properties" \
  "${ROOT}/ezkey-auth-api/config/application-docker-dev.properties"; do
  if grep -Eq 'exposure\.include=.*threaddump' "$f"; then
    echo "FAIL: $(basename "$f") still exposes threaddump" >&2
    fail=$((fail + 1))
  else
    echo "PASS: $(basename "$f") has no threaddump"
    pass=$((pass + 1))
  fi
done

echo
echo "Results: ${pass} passed, ${fail} failed"
[[ "${fail}" -eq 0 ]]
