#!/usr/bin/env bash
# Fake-only check: local compose / HA / start-manage scripts / Dockerfile keep
# management actuator ports aligned (PR B / ADR-0015). No Docker daemon required.
#
# Override tree root for unit tests: CHECK_LOCAL_MGMT_ROOT=/path/to/fixture
set -euo pipefail

ROOT="${CHECK_LOCAL_MGMT_ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}"
COMPOSE="${ROOT}/docker/docker-compose.yml"
DEV="${ROOT}/docker/docker-compose.docker-dev.yml"
HA_DEV="${ROOT}/docker/docker-compose.ha.docker-dev.yml"
JMX_DEV="${ROOT}/docker/docker-compose.docker-dev.jmx.yml"
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

# #905: Docker profiles must override IDE 127.0.0.1 so HAProxy/peers can health-check.
need_docker_mgmt_bind_all() {
  local file="$1"
  local label="$2"
  if grep -Eq '^[[:space:]]*management\.server\.address[[:space:]]*=[[:space:]]*0\.0\.0\.0[[:space:]]*$' "$file"; then
    echo "PASS: ${label} sets management.server.address=0.0.0.0"
    pass=$((pass + 1))
  else
    echo "FAIL: ${label} must set management.server.address=0.0.0.0 (#905)" >&2
    fail=$((fail + 1))
  fi
  if grep -Eq '^[[:space:]]*management\.server\.address[[:space:]]*=[[:space:]]*127\.0\.0\.1[[:space:]]*$' "$file"; then
    echo "FAIL: ${label} must not set management.server.address=127.0.0.1" >&2
    fail=$((fail + 1))
  fi
}

need_docker_mgmt_bind_all \
  "${ROOT}/ezkey-admin-api/config/application-docker.properties" "admin-api docker profile"
need_docker_mgmt_bind_all \
  "${ROOT}/ezkey-auth-api/config/application-docker.properties" "auth-api docker profile"
need_docker_mgmt_bind_all \
  "${ROOT}/ezkey-integration-api/config/application-docker.properties" "integration-api docker profile"
need_docker_mgmt_bind_all \
  "${ROOT}/ezkey-crypto-api/config/application-docker.properties" "crypto-api docker profile"
need_docker_mgmt_bind_all \
  "${ROOT}/ezkey-demo-device/src/main/resources/application-docker.properties" "demo-device docker profile"

# Local + HA compose must pin MANAGEMENT_SERVER_ADDRESS=0.0.0.0 (env beats IDE default).
need_compose_mgmt_address_all() {
  local file="$1"
  local label="$2"
  local count
  count="$(grep -cE 'MANAGEMENT_SERVER_ADDRESS:[[:space:]]*"?0\.0\.0\.0"?' "$file" || true)"
  if [[ "${count}" -ge 1 ]]; then
    echo "PASS: ${label} sets MANAGEMENT_SERVER_ADDRESS=0.0.0.0 (${count}×)"
    pass=$((pass + 1))
  else
    echo "FAIL: ${label} must set MANAGEMENT_SERVER_ADDRESS=0.0.0.0 (#905)" >&2
    fail=$((fail + 1))
  fi
  if grep -Eq 'MANAGEMENT_SERVER_ADDRESS:[[:space:]]*"?127\.0\.0\.1"?' "$file"; then
    echo "FAIL: ${label} must not set MANAGEMENT_SERVER_ADDRESS=127.0.0.1" >&2
    fail=$((fail + 1))
  fi
}

need_compose_mgmt_address_all "$COMPOSE" "docker-compose.yml"
need_compose_mgmt_address_all "$HA" "docker-compose.ha.yml"

# HA API replicas must not publish management ports on the host (expose only; HAProxy owns 9081/8085/7081 stats).
if awk '
  /^  [a-z0-9-]+:/ { svc=$1; gsub(":","",svc); in_ports=0 }
  /^    ports:/ { in_ports=1; next }
  /^    [a-z]/ { in_ports=0 }
  in_ports && /"(9081|8085|7081):(9081|8085|7081)"/ {
    if (svc ~ /^(admin-api|auth-api|integration-api)/) { bad=1; print svc }
  }
  END { exit bad ? 1 : 0 }
' "$HA"; then
  echo "PASS: HA API replicas do not publish management ports on the host"
  pass=$((pass + 1))
else
  echo "FAIL: HA API replicas must not publish management ports on the host (expose only)" >&2
  fail=$((fail + 1))
fi

# HAProxy stats host publishes must be loopback-only (#905 Christophe).
need_ha_stats_loopback() {
  local port="$1"
  if grep -Eq "127\\.0\\.0\\.1:${port}:${port}" "$HA"; then
    echo "PASS: HA HAProxy stats publishes 127.0.0.1:${port}:${port}"
    pass=$((pass + 1))
  else
    echo "FAIL: docker-compose.ha.yml must publish HAProxy stats as 127.0.0.1:${port}:${port}" >&2
    fail=$((fail + 1))
  fi
  if grep -Eq "^[[:space:]]*-[[:space:]]*\"${port}:${port}\"" "$HA"; then
    echo "FAIL: HA still publishes HAProxy stats ${port}:${port} on all interfaces" >&2
    fail=$((fail + 1))
  fi
}

need_ha_stats_loopback "9081"
need_ha_stats_loopback "8085"
need_ha_stats_loopback "7081"

for p in 9081 8085 7081 8084 9091 8086; do
  need_publish "$COMPOSE" "$p" "docker-compose.yml"
  need_health_127 "$COMPOSE" "$p" "docker-compose.yml"
done

# #905 Patrick M4: any ports: publish in docker-dev overlays must be loopback-only.
# Generic rule — do not maintain an explicit port allowlist (Christophe M1 + future ports).
require_docker_dev_ports_loopback() {
  local file="$1"
  local label="$2"
  local line bare=0 ok=0
  if [[ ! -f "$file" ]]; then
    echo "FAIL: missing ${label} (${file})" >&2
    fail=$((fail + 1))
    return
  fi
  while IFS= read -r line || [[ -n "$line" ]]; do
    # Strip CR and end-of-line comments before classifying (#905 Patrick M4).
    # Do not skip the whole line merely because it contains '#' — otherwise
    # `- "18085:8085"  # dev` would be ignored and pass the check.
    line="${line//$'\r'/}"
    line="${line%%#*}"
    # Trim trailing whitespace after comment strip.
    line="${line%"${line##*[![:space:]]}"}"
    [[ -z "${line//[[:space:]]/}" ]] && continue
    case "$line" in
      *ports:*) continue ;;
    esac
    # Compose short syntax port mapping: - "host:container" / - host:container / - ip:host:container
    if printf '%s\n' "$line" | grep -Eq '^[[:space:]]*-[[:space:]]*["'\'']?[0-9]'; then
      if printf '%s\n' "$line" | grep -Eq '127\.0\.0\.1:[0-9]+:[0-9]+'; then
        ok=$((ok + 1))
      else
        echo "FAIL: ${label} ports entry is not loopback-only: ${line}" >&2
        bare=$((bare + 1))
      fi
    fi
  done <"$file"
  if [[ "$bare" -gt 0 ]]; then
    fail=$((fail + 1))
  elif [[ "$ok" -eq 0 ]]; then
    echo "FAIL: ${label} has no ports: publishes to validate" >&2
    fail=$((fail + 1))
  else
    echo "PASS: ${label} all ${ok} ports: publishes are 127.0.0.1:…"
    pass=$((pass + 1))
  fi
}

require_docker_dev_ports_loopback "$DEV" "docker-compose.docker-dev.yml"
require_docker_dev_ports_loopback "$HA_DEV" "docker-compose.ha.docker-dev.yml"
require_docker_dev_ports_loopback "$JMX_DEV" "docker-compose.docker-dev.jmx.yml"

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
