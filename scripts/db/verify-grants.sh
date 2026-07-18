#!/usr/bin/env bash
# Smoke-check Ezkey role privileges against a running database.
# Expects roles + apply-grants already applied.
#
# Usage:
#   ./scripts/db/verify-grants.sh
#   ./scripts/db/verify-grants.sh --docke

set -euo pipefail

export PGHOST="${PGHOST:-localhost}"
export PGPORT="${PGPORT:-5432}"
export PGDATABASE="${PGDATABASE:-ezkey_db}"

MIGRATE_PASS="${EZKEY_DB_MIGRATE_PASSWORD:-ezkey_migrate}"
ADMIN_PASS="${EZKEY_DB_ADMIN_PASSWORD:-ezkey_admin}"
AUTH_PASS="${EZKEY_DB_AUTH_PASSWORD:-ezkey_auth}"
INTEGRATION_PASS="${EZKEY_DB_INTEGRATION_PASSWORD:-ezkey_integration}"

DOCKER_MODE=0
if [[ "${1:-}" == "--docker" ]]; then
  DOCKER_MODE=1
  CONTAINER="${EZKEY_POSTGRES_CONTAINER:-ezkey-postgres}"
fi

run_psql() {
  local user="$1"
  local pass="$2"
  shift 2
  if [[ "${DOCKER_MODE}" -eq 1 ]]; then
    docker exec -e PGPASSWORD="${pass}" -i "${CONTAINER}" \
      psql -v ON_ERROR_STOP=1 -U "${user}" -d ezkey_db "$@"
  else
    PGPASSWORD="${pass}" psql -v ON_ERROR_STOP=1 -h "${PGHOST}" -p "${PGPORT}" \
      -U "${user}" -d "${PGDATABASE}" "$@"
  fi
}

expect_fail() {
  local label="$1"
  shift
  if "$@" >/tmp/ezkey-verify-grants.out 2>/tmp/ezkey-verify-grants.err; then
    echo "FAIL: expected denial for: ${label}" >&2
    cat /tmp/ezkey-verify-grants.err >&2 || true
    exit 1
  fi
  echo "OK (denied): ${label}"
}

expect_ok() {
  local label="$1"
  shift
  if ! "$@" >/tmp/ezkey-verify-grants.out 2>/tmp/ezkey-verify-grants.err; then
    echo "FAIL: expected success for: ${label}" >&2
    cat /tmp/ezkey-verify-grants.err >&2 || true
    exit 1
  fi
  echo "OK: ${label}"
}

echo "Verifying Ezkey database role grants..."

expect_ok "auth can SELECT ezkey_enrollment" \
  run_psql ezkey_auth "${AUTH_PASS}" -c "SELECT 1 FROM ezkey_enrollment LIMIT 0;"

expect_fail "auth cannot DELETE ezkey_audit_log" \
  run_psql ezkey_auth "${AUTH_PASS}" -c "DELETE FROM ezkey_audit_log WHERE false;"

expect_fail "auth cannot UPDATE ezkey_audit_log (append-only)" \
  run_psql ezkey_auth "${AUTH_PASS}" -c "UPDATE ezkey_audit_log SET api_name = api_name WHERE false;"

# Use a real EventType enum value; roll back so smoke rows do not pollute integrity checks.
expect_ok "auth can INSERT ezkey_audit_log" \
  run_psql ezkey_auth "${AUTH_PASS}" -v ON_ERROR_STOP=1 -c \
  "BEGIN;
   INSERT INTO ezkey_audit_log (event_type, event_action, event_status, api_name)
   VALUES ('ADMIN_LOGIN', 'verify_grants_insert', 'SUCCESS', 'AUTH_API');
   ROLLBACK;"

expect_fail "integration cannot UPDATE ezkey_audit_log (append-only)" \
  run_psql ezkey_integration "${INTEGRATION_PASS}" -c \
  "UPDATE ezkey_audit_log SET api_name = api_name WHERE false;"

expect_ok "integration can INSERT ezkey_audit_log" \
  run_psql ezkey_integration "${INTEGRATION_PASS}" -v ON_ERROR_STOP=1 -c \
  "BEGIN;
   INSERT INTO ezkey_audit_log (event_type, event_action, event_status, api_name)
   VALUES ('ADMIN_LOGIN', 'verify_grants_insert', 'SUCCESS', 'INTEGRATION_API');
   ROLLBACK;"

expect_fail "integration cannot UPDATE ezkey_enrollment" \
  run_psql ezkey_integration "${INTEGRATION_PASS}" -c "UPDATE ezkey_enrollment SET name = name WHERE false;"

expect_fail "integration cannot SELECT ezkey_admin_tokens" \
  run_psql ezkey_integration "${INTEGRATION_PASS}" -c "SELECT 1 FROM ezkey_admin_tokens LIMIT 0;"

expect_ok "admin can SELECT ezkey_shedlock" \
  run_psql ezkey_admin "${ADMIN_PASS}" -c "SELECT 1 FROM ezkey_shedlock LIMIT 0;"

admin_exec="$(run_psql ezkey_admin "${ADMIN_PASS}" -tAc \
  "SELECT has_function_privilege('ezkey_admin', 'create_monthly_partition(text,text,timestamptz,timestamptz)', 'EXECUTE');" \
  | tr -d '[:space:]')"
if [[ "${admin_exec}" != "t" ]]; then
  echo "FAIL: ezkey_admin should have EXECUTE on create_monthly_partition (got '${admin_exec}')" >&2
  exit 1
fi
echo "OK: admin has EXECUTE on create_monthly_partition"

auth_exec="$(run_psql ezkey_auth "${AUTH_PASS}" -tAc \
  "SELECT has_function_privilege('ezkey_auth', 'create_monthly_partition(text,text,timestamptz,timestamptz)', 'EXECUTE');" \
  | tr -d '[:space:]')"
if [[ "${auth_exec}" == "t" ]]; then
  echo "FAIL: ezkey_auth must not have EXECUTE on create_monthly_partition" >&2
  exit 1
fi
echo "OK (denied): auth has no EXECUTE on create_monthly_partition"

expect_ok "migrate can see flyway_schema_history" \
  run_psql ezkey_migrate "${MIGRATE_PASS}" -c "SELECT 1 FROM flyway_schema_history LIMIT 0;"

echo "All grant checks passed."
