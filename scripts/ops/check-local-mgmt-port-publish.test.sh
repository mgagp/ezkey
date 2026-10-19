#!/usr/bin/env bash
# Unit checks for check-local-mgmt-port-publish.sh (Edgar N2 / B1 regression).
# No Docker daemon. Run from repo root:
#   ./scripts/ops/check-local-mgmt-port-publish.test.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CHECK="${ROOT}/scripts/ops/check-local-mgmt-port-publish.sh"

pass=0
fail=0

assert_ok() {
  local name="$1"
  shift
  if "$@"; then
    echo "PASS: ${name}"
    pass=$((pass + 1))
  else
    echo "FAIL: ${name}" >&2
    fail=$((fail + 1))
  fi
}

assert_fail() {
  local name="$1"
  shift
  if "$@"; then
    echo "FAIL: ${name} (expected failure)" >&2
    fail=$((fail + 1))
  else
    echo "PASS: ${name}"
    pass=$((pass + 1))
  fi
}

assert_ok "repo tree passes check-local-mgmt-port-publish.sh" bash "${CHECK}"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# Minimal fixture that mirrors the paths the check reads. Seed from the repo,
# then reintroduce a stale crypto actuator probe in start.sh — the check must fail.
mkdir -p \
  "${TMP}/docker" \
  "${TMP}/experimental-hybrid/local" \
  "${TMP}/ezkey-pam/scripts" \
  "${TMP}/scripts" \
  "${TMP}/ezkey-admin-api/config" \
  "${TMP}/ezkey-auth-api/config"

cp "${ROOT}/docker/docker-compose.yml" "${TMP}/docker/"
cp "${ROOT}/docker/docker-compose.docker-dev.yml" "${TMP}/docker/"
cp "${ROOT}/docker/docker-compose.ha.yml" "${TMP}/docker/"
cp "${ROOT}/docker/Dockerfile" "${TMP}/docker/"
cp "${ROOT}/docker/start.sh" "${TMP}/docker/"
cp "${ROOT}/docker/start-ha.sh" "${TMP}/docker/"
cp "${ROOT}/docker/manage.sh" "${TMP}/docker/"
cp "${ROOT}/docker/manage-ha.sh" "${TMP}/docker/"
cp "${ROOT}/experimental-hybrid/local/docker-compose.yml" "${TMP}/experimental-hybrid/local/"
cp "${ROOT}/ezkey-pam/scripts/common.sh" "${TMP}/ezkey-pam/scripts/"
cp "${ROOT}/scripts/quality-gate.sh" "${TMP}/scripts/"
cp "${ROOT}/ezkey-admin-api/config/application-windows.properties" "${TMP}/ezkey-admin-api/config/"
cp "${ROOT}/ezkey-admin-api/config/application-docker-dev.properties" "${TMP}/ezkey-admin-api/config/"
cp "${ROOT}/ezkey-auth-api/config/application-docker-dev.properties" "${TMP}/ezkey-auth-api/config/"

assert_ok "fixture clone of current tree still passes" \
  env CHECK_LOCAL_MGMT_ROOT="${TMP}" bash "${CHECK}"

# B1 regression: stale host wait on business port 9090 must fail the check.
sed -i 's|localhost:9091/actuator/health|localhost:9090/actuator/health|g' "${TMP}/docker/start.sh"

assert_fail "stale start.sh crypto actuator :9090 is rejected" \
  env CHECK_LOCAL_MGMT_ROOT="${TMP}" bash "${CHECK}"

# Restore start.sh, break Dockerfile HEALTHCHECK instead.
cp "${ROOT}/docker/start.sh" "${TMP}/docker/start.sh"
sed -i 's|localhost:9091/actuator/health|localhost:9090/actuator/health|g' "${TMP}/docker/Dockerfile"

assert_fail "stale Dockerfile crypto HEALTHCHECK :9090 is rejected" \
  env CHECK_LOCAL_MGMT_ROOT="${TMP}" bash "${CHECK}"

# Restore Dockerfile, break HA healthcheck.
cp "${ROOT}/docker/Dockerfile" "${TMP}/docker/Dockerfile"
sed -i 's|localhost:9091/actuator/health|localhost:9090/actuator/health|g' "${TMP}/docker/docker-compose.ha.yml"

assert_fail "stale docker-compose.ha.yml crypto health :9090 is rejected" \
  env CHECK_LOCAL_MGMT_ROOT="${TMP}" bash "${CHECK}"

echo
echo "Test results: ${pass} passed, ${fail} failed"
[[ "${fail}" -eq 0 ]]
