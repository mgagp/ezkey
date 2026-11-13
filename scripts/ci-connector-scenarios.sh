#!/usr/bin/env bash
#
# Replays the delivery connector scenarios against a real stack, with no human (#1206 step 4):
#   1. No connector: Ezkey works as before.
#   2. Rejected connector credential: the connector is treated as unavailable.
#   3. Mock connector: every outcome, slow connector, daily cap, audit.
#   4. Canary: logs of every container hold no recipient, token, PNG or base64 block.
#   5. SMTP connector: a real send lands in Mailpit with the QR inline (#1206 step 5).
#   6. Mail provider down: the send is rejected and the QR stays on screen.
#   7. Canary again, with the SMTP connector.
#
# Usage (repo root):
#   ./scripts/ci-connector-scenarios.sh [--skip-start] [--keep-stack] [--smtp-only]
#
# It runs a clean start, which wipes the volumes of the Docker Compose project in use. Container
# names are fixed, so stop any other Ezkey stack first. COMPOSE_PROJECT_NAME picks another project,
# which keeps the default project's volumes.
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

SKIP_START=0
KEEP_STACK=0
SMTP_ONLY=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-start) SKIP_START=1; shift ;;
    --keep-stack) KEEP_STACK=1; shift ;;
    --smtp-only) SMTP_ONLY=1; shift ;;
    -h|--help) sed -n '3,17p' "$0"; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

export SPRING_PROFILES_ACTIVE="docker,docker-dev,docker-test"
MVN_ARGS=(-q -B -pl ezkey-tests -am test -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true)

compose() {
  (cd "${ROOT}/docker" && docker compose \
    -f docker-compose.yml -f docker-compose.docker-dev.yml -f docker-compose.with-proxy.yml \
    "$@")
}

compose_with_mock() {
  (cd "${ROOT}/docker" && docker compose \
    -f docker-compose.yml -f docker-compose.docker-dev.yml -f docker-compose.with-proxy.yml \
    -f docker-compose.connector-mock.yml \
    "$@")
}

# The SMTP overlay alone: the mock overlay's low tenant cap must not apply to the real send.
compose_with_smtp() {
  (cd "${ROOT}/docker" && docker compose \
    -f docker-compose.yml -f docker-compose.docker-dev.yml -f docker-compose.with-proxy.yml \
    -f docker-compose.connector-smtp.yml \
    "$@")
}

# Every overlay, so tear-down removes every container either path started.
compose_all() {
  (cd "${ROOT}/docker" && docker compose \
    -f docker-compose.yml -f docker-compose.docker-dev.yml -f docker-compose.with-proxy.yml \
    -f docker-compose.connector-mock.yml -f docker-compose.connector-smtp.yml \
    "$@")
}

cleanup() {
  local status=$?
  if [[ "${status}" -ne 0 ]]; then
    echo "==> Failed. Admin API and mock logs, last lines:"
    docker logs --tail 40 ezkey-admin-api 2>&1 || true
    docker logs --tail 40 ezkey-connector-mock 2>&1 || true
    docker logs --tail 40 ezkey-connector-smtp 2>&1 || true
  fi
  if [[ "${KEEP_STACK}" -eq 0 && "${SKIP_START}" -eq 0 ]]; then
    echo "==> Tearing down the stack"
    compose_all down -v --remove-orphans || true
  fi
  return "${status}"
}
trap cleanup EXIT

if [[ "${SKIP_START}" -eq 0 ]]; then
  echo "==> Clean start"
  bash "${ROOT}/ezkey-tests/clean-start.sh"
fi

if [[ "${SMTP_ONLY}" -eq 0 ]]; then
  echo "==> Scenario 1: no connector"
  mvn "${MVN_ARGS[@]}" -Dtest=QrDeliveryUnavailableFunctionalTest

  echo "==> Scenario 2: rejected connector credential"
  EZKEY_ADMIN_CONNECTOR_DELIVERY_TOKEN=wrong-token \
    compose_with_mock up -d --build --no-deps --wait --wait-timeout 240 connector-mock admin-api
  mvn "${MVN_ARGS[@]}" -Dtest=QrDeliveryUnavailableFunctionalTest

  echo "==> Scenario 3: mock connector"
  compose_with_mock up -d --no-build --no-deps --wait --wait-timeout 240 admin-api
  mvn "${MVN_ARGS[@]}" -Pconnector-mock -Dtest=QrDeliveryScenariosFunctionalTest

  echo "==> Scenario 4: log canary"
  mvn "${MVN_ARGS[@]}" -Pconnector-mock -Dtest=QrDeliveryLogCanaryFunctionalTest
fi

echo "==> Scenario 5: SMTP connector and Mailpit"
compose_with_smtp up -d --build --no-deps --wait --wait-timeout 300 mailpit connector-smtp admin-api
mvn "${MVN_ARGS[@]}" -Pconnector-smtp -Dtest=QrDeliverySmtpFunctionalTest

echo "==> Scenario 6: mail provider down"
docker stop ezkey-mailpit >/dev/null
mvn "${MVN_ARGS[@]}" -Pconnector-smtp -Dtest=QrDeliverySmtpProviderDownFunctionalTest \
  -Dezkey.tests.mailpit.down=true
docker start ezkey-mailpit >/dev/null

echo "==> Scenario 7: log canary with the SMTP connector"
mvn "${MVN_ARGS[@]}" -Pconnector-smtp -Dtest=QrDeliveryLogCanaryFunctionalTest \
  -Dezkey.tests.connector.container=ezkey-connector-smtp

echo "==> All connector scenarios passed"
