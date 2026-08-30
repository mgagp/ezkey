#!/usr/bin/env bash
# Forced Auth-first keyset readiness (ADR-0012 / TB-2026-08-28).
# Starts Auth without Admin (compose --no-deps). A lucky clean-start is not proof.
#
# Usage:
#   ./scripts/repro-auth-keyset-readiness.sh
#   ./scripts/repro-auth-keyset-readiness.sh negative
#   ./scripts/repro-auth-keyset-readiness.sh positive
#   ./scripts/repro-auth-keyset-readiness.sh --build
#
# Prerequisites: Docker; images that include this change; existing master key in
# ezkey_encryption-secrets. Does not generate keys and does not TRUNCATE
# ezkey_encryption_key. Admin upserts the blob from the existing file keyset.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
DOCKER_DIR="${REPO_ROOT}/docker"
COMPOSE_FILE="${DOCKER_DIR}/docker-compose.yml"
AUTH_CONTAINER="${EZKEY_AUTH_CONTAINER:-ezkey-auth-api}"
POSTGRES_CONTAINER="${EZKEY_POSTGRES_CONTAINER:-ezkey-postgres}"
MASTER_VOLUME="${EZKEY_ENCRYPTION_VOLUME:-ezkey_encryption-secrets}"
NEGATIVE_WAIT="${EZKEY_REPRO_NEGATIVE_WAIT:-PT15S}"
POSITIVE_WAIT="${EZKEY_REPRO_POSITIVE_WAIT:-PT90S}"

RUN_NEGATIVE=1
RUN_POSITIVE=1
DO_BUILD=0

for arg in "$@"; do
  case "${arg}" in
    negative|--negative-only)
      RUN_NEGATIVE=1
      RUN_POSITIVE=0
      ;;
    positive|--positive-only)
      RUN_NEGATIVE=0
      RUN_POSITIVE=1
      ;;
    --build)
      DO_BUILD=1
      ;;
    --help|-h)
      sed -n '2,14p' "$0"
      exit 0
      ;;
    *)
      echo "Unknown option: ${arg}" >&2
      echo "Usage: $0 [negative|positive] [--build]" >&2
      exit 1
      ;;
  esac
done

compose() {
  docker compose --project-directory "${DOCKER_DIR}" -f "${COMPOSE_FILE}" "$@"
}

auth_logs() {
  docker logs "${AUTH_CONTAINER}" 2>&1 || true
}

assert_no_illegal_write() {
  local logs="$1"
  if echo "${logs}" | grep -Fq "permission denied for table ezkey_encryption_key"; then
    echo "FAIL: Auth logged permission denied on ezkey_encryption_key" >&2
    echo "${logs}" | tail -n 80 >&2
    exit 1
  fi
  if echo "${logs}" | grep -Fq "UnexpectedRollbackException"; then
    echo "FAIL: Auth logged UnexpectedRollbackException" >&2
    echo "${logs}" | tail -n 80 >&2
    exit 1
  fi
}

wait_for_postgres() {
  local i
  for i in $(seq 1 60); do
    if docker exec "${POSTGRES_CONTAINER}" pg_isready -U postgres >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  echo "FAIL: postgres did not become ready" >&2
  exit 1
}

delete_keyset_blob_only() {
  docker exec "${POSTGRES_CONTAINER}" \
    psql -U postgres -d ezkey_db -v ON_ERROR_STOP=1 \
    -c "DELETE FROM ezkey_keyset_blob;"
}

master_key_present() {
  # Git Bash rewrites /etc/... mounts unless MSYS_NO_PATHCONV=1.
  MSYS_NO_PATHCONV=1 docker run --rm -v "${MASTER_VOLUME}:/etc/ezkey" alpine \
    test -f /etc/ezkey/secrets/master.key
}

ensure_infra() {
  compose up -d postgres
  wait_for_postgres
  if ! master_key_present; then
    echo "FAIL: master key missing in volume ${MASTER_VOLUME}." >&2
    echo "Run docker/generate-encryption-keys.sh yourself; this script will not rotate keys." >&2
    exit 1
  fi
}

stop_apis() {
  compose stop admin-api auth-api integration-api crypto-api >/dev/null 2>&1 || true
  docker rm -f "${AUTH_CONTAINER}" >/dev/null 2>&1 || true
}

disable_auth_restart() {
  docker update --restart=no "${AUTH_CONTAINER}" >/dev/null 2>&1 || true
}

run_negative() {
  echo "=== Negative: Auth first, Admin never arrives ==="
  stop_apis
  delete_keyset_blob_only
  EZKEY_ENCRYPTION_KEYSET_BOOTSTRAP_WAIT="${NEGATIVE_WAIT}" \
    compose up --no-deps --force-recreate -d auth-api
  disable_auth_restart

  # Wait past bootstrap-wait plus JVM start.
  sleep 25

  local logs
  logs="$(auth_logs)"
  assert_no_illegal_write "${logs}"
  if ! echo "${logs}" | grep -Eq "Timed out waiting for keyset blob|not the keyset writer"; then
    echo "FAIL: expected wait / non-writer fail-closed log" >&2
    echo "${logs}" | tail -n 80 >&2
    exit 1
  fi
  if curl -sf "http://localhost:8085/actuator/health" >/dev/null 2>&1; then
    echo "FAIL: Auth became healthy without a keyset blob" >&2
    exit 1
  fi
  echo "OK: Auth failed closed without illegal INSERT"
}

run_positive() {
  echo "=== Positive: Auth first, Admin arrives inside bootstrap-wait ==="
  stop_apis
  delete_keyset_blob_only
  EZKEY_ENCRYPTION_KEYSET_BOOTSTRAP_WAIT="${POSITIVE_WAIT}" \
    compose up --no-deps --force-recreate -d auth-api
  disable_auth_restart
  sleep 3
  # --no-deps: do not wait on migration/db-grants (that would burn Auth's wait window).
  compose up --no-deps --force-recreate -d admin-api

  local i
  for i in $(seq 1 90); do
    if curl -sf "http://localhost:8085/actuator/health" >/dev/null 2>&1; then
      break
    fi
    sleep 2
  done

  if ! curl -sf "http://localhost:8085/actuator/health" >/dev/null 2>&1; then
    echo "FAIL: Auth did not become healthy after Admin arrived" >&2
    echo "--- auth logs (tail) ---" >&2
    auth_logs | tail -n 80 >&2
    echo "--- admin logs (tail) ---" >&2
    docker logs ezkey-admin-api 2>&1 | tail -n 40 >&2
    exit 1
  fi

  local logs blob_count
  logs="$(auth_logs)"
  assert_no_illegal_write "${logs}"
  blob_count="$(docker exec "${POSTGRES_CONTAINER}" \
    psql -U postgres -d ezkey_db -tAc "SELECT count(*) FROM ezkey_keyset_blob;" \
    | tr -d '[:space:]')"
  if [[ "${blob_count}" != "1" ]]; then
    echo "FAIL: expected one keyset blob row, got '${blob_count}'" >&2
    exit 1
  fi
  echo "OK: Auth loaded blob after Admin materialization"
}

cd "${REPO_ROOT}"

if [[ "${DO_BUILD}" -eq 1 ]]; then
  compose build auth-api admin-api
fi

ensure_infra

if [[ "${RUN_NEGATIVE}" -eq 1 ]]; then
  run_negative
fi
if [[ "${RUN_POSITIVE}" -eq 1 ]]; then
  run_positive
fi

echo "Auth-first keyset readiness repro finished."
