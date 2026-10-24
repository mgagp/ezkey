#!/usr/bin/env bash
# Smoke-check Ezkey role privileges against a running database.
# Expects roles + apply-grants already applied.
#
# Usage:
#   ./scripts/db/verify-grants.sh
#   ./scripts/db/verify-grants.sh --docker

set -euo pipefail

export PGHOST="${PGHOST:-localhost}"
export PGPORT="${PGPORT:-5432}"
export PGDATABASE="${PGDATABASE:-ezkey_db}"

MIGRATE_PASS="${EZKEY_DB_MIGRATE_PASSWORD:-ezkey_migrate}"
ADMIN_PASS="${EZKEY_DB_ADMIN_PASSWORD:-ezkey_admin}"
AUTH_PASS="${EZKEY_DB_AUTH_PASSWORD:-ezkey_auth}"
INTEGRATION_PASS="${EZKEY_DB_INTEGRATION_PASSWORD:-ezkey_integration}"
CRYPTO_PASS="${EZKEY_DB_CRYPTO_PASSWORD:-ezkey_crypto}"
SUPER_USER="${PGUSER:-postgres}"
SUPER_PASS="${PGPASSWORD:-ezkey}"

VERIFY_OUT="$(mktemp "${TMPDIR:-/tmp}/ezkey-verify-grants.out.XXXXXX")"
VERIFY_ERR="$(mktemp "${TMPDIR:-/tmp}/ezkey-verify-grants.err.XXXXXX")"
trap 'rm -f "${VERIFY_OUT}" "${VERIFY_ERR}"' EXIT

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
  if "$@" >"${VERIFY_OUT}" 2>"${VERIFY_ERR}"; then
    echo "FAIL: expected denial for: ${label}" >&2
    cat "${VERIFY_ERR}" >&2 || true
    exit 1
  fi
  echo "OK (denied): ${label}"
}

expect_ok() {
  local label="$1"
  shift
  if ! "$@" >"${VERIFY_OUT}" 2>"${VERIFY_ERR}"; then
    echo "FAIL: expected success for: ${label}" >&2
    cat "${VERIFY_ERR}" >&2 || true
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

# Auth has zero DML on admin tokens — TEMP supersede is admin-api only.
expect_fail "auth cannot SELECT ezkey_admin_tokens" \
  run_psql ezkey_auth "${AUTH_PASS}" -c "SELECT 1 FROM ezkey_admin_tokens LIMIT 0;"

expect_fail "auth cannot UPDATE ezkey_admin_tokens" \
  run_psql ezkey_auth "${AUTH_PASS}" -c \
  "UPDATE ezkey_admin_tokens SET active = active WHERE false;"

expect_fail "auth cannot INSERT ezkey_admin_tokens" \
  run_psql ezkey_auth "${AUTH_PASS}" -c \
  "INSERT INTO ezkey_admin_tokens DEFAULT VALUES;"

expect_fail "auth cannot DELETE ezkey_admin_tokens" \
  run_psql ezkey_auth "${AUTH_PASS}" -c \
  "DELETE FROM ezkey_admin_tokens WHERE false;"

expect_ok "admin can SELECT ezkey_admin_tokens" \
  run_psql ezkey_admin "${ADMIN_PASS}" -c "SELECT 1 FROM ezkey_admin_tokens LIMIT 0;"

expect_ok "admin can SELECT ezkey_shedlock" \
  run_psql ezkey_admin "${ADMIN_PASS}" -c "SELECT 1 FROM ezkey_shedlock LIMIT 0;"

expect_ok "admin can SELECT ezkey_integrity_async_job" \
  run_psql ezkey_admin "${ADMIN_PASS}" -c "SELECT 1 FROM ezkey_integrity_async_job LIMIT 0;"

expect_fail "auth cannot SELECT ezkey_integrity_async_job" \
  run_psql ezkey_auth "${AUTH_PASS}" -c "SELECT 1 FROM ezkey_integrity_async_job LIMIT 0;"

expect_fail "integration cannot SELECT ezkey_integrity_async_job" \
  run_psql ezkey_integration "${INTEGRATION_PASS}" -c "SELECT 1 FROM ezkey_integrity_async_job LIMIT 0;"

admin_async_ins="$(run_psql ezkey_admin "${ADMIN_PASS}" -tAc \
  "SELECT has_table_privilege('ezkey_admin', 'ezkey_integrity_async_job', 'INSERT');" \
  | tr -d '[:space:]')"
if [[ "${admin_async_ins}" != "t" ]]; then
  echo "FAIL: ezkey_admin should INSERT ezkey_integrity_async_job (got '${admin_async_ins}')" >&2
  exit 1
fi
echo "OK: admin can INSERT ezkey_integrity_async_job"

admin_async_upd="$(run_psql ezkey_admin "${ADMIN_PASS}" -tAc \
  "SELECT has_table_privilege('ezkey_admin', 'ezkey_integrity_async_job', 'UPDATE');" \
  | tr -d '[:space:]')"
if [[ "${admin_async_upd}" != "t" ]]; then
  echo "FAIL: ezkey_admin should UPDATE ezkey_integrity_async_job (got '${admin_async_upd}')" >&2
  exit 1
fi
echo "OK: admin can UPDATE ezkey_integrity_async_job"

expect_ok "auth can SELECT ezkey_encryption_key" \
  run_psql ezkey_auth "${AUTH_PASS}" -c "SELECT 1 FROM ezkey_encryption_key LIMIT 0;"

expect_ok "auth can SELECT ezkey_keyset_blob" \
  run_psql ezkey_auth "${AUTH_PASS}" -c "SELECT 1 FROM ezkey_keyset_blob LIMIT 0;"

expect_ok "integration can SELECT ezkey_encryption_key" \
  run_psql ezkey_integration "${INTEGRATION_PASS}" -c "SELECT 1 FROM ezkey_encryption_key LIMIT 0;"

expect_ok "integration can SELECT ezkey_keyset_blob" \
  run_psql ezkey_integration "${INTEGRATION_PASS}" -c "SELECT 1 FROM ezkey_keyset_blob LIMIT 0;"

auth_enc_ins="$(run_psql ezkey_auth "${AUTH_PASS}" -tAc \
  "SELECT has_table_privilege('ezkey_auth', 'ezkey_encryption_key', 'INSERT');" \
  | tr -d '[:space:]')"
if [[ "${auth_enc_ins}" == "t" ]]; then
  echo "FAIL: ezkey_auth must not INSERT ezkey_encryption_key" >&2
  exit 1
fi
echo "OK (denied): auth cannot INSERT ezkey_encryption_key"

auth_enc_upd="$(run_psql ezkey_auth "${AUTH_PASS}" -tAc \
  "SELECT has_table_privilege('ezkey_auth', 'ezkey_encryption_key', 'UPDATE');" \
  | tr -d '[:space:]')"
if [[ "${auth_enc_upd}" == "t" ]]; then
  echo "FAIL: ezkey_auth must not UPDATE ezkey_encryption_key" >&2
  exit 1
fi
echo "OK (denied): auth cannot UPDATE ezkey_encryption_key"

int_enc_ins="$(run_psql ezkey_integration "${INTEGRATION_PASS}" -tAc \
  "SELECT has_table_privilege('ezkey_integration', 'ezkey_encryption_key', 'INSERT');" \
  | tr -d '[:space:]')"
if [[ "${int_enc_ins}" == "t" ]]; then
  echo "FAIL: ezkey_integration must not INSERT ezkey_encryption_key" >&2
  exit 1
fi
echo "OK (denied): integration cannot INSERT ezkey_encryption_key"

int_enc_upd="$(run_psql ezkey_integration "${INTEGRATION_PASS}" -tAc \
  "SELECT has_table_privilege('ezkey_integration', 'ezkey_encryption_key', 'UPDATE');" \
  | tr -d '[:space:]')"
if [[ "${int_enc_upd}" == "t" ]]; then
  echo "FAIL: ezkey_integration must not UPDATE ezkey_encryption_key" >&2
  exit 1
fi
echo "OK (denied): integration cannot UPDATE ezkey_encryption_key"

auth_blob_ins="$(run_psql ezkey_auth "${AUTH_PASS}" -tAc \
  "SELECT has_table_privilege('ezkey_auth', 'ezkey_keyset_blob', 'INSERT');" \
  | tr -d '[:space:]')"
if [[ "${auth_blob_ins}" == "t" ]]; then
  echo "FAIL: ezkey_auth must not INSERT ezkey_keyset_blob" >&2
  exit 1
fi
echo "OK (denied): auth cannot INSERT ezkey_keyset_blob"

auth_blob_upd="$(run_psql ezkey_auth "${AUTH_PASS}" -tAc \
  "SELECT has_table_privilege('ezkey_auth', 'ezkey_keyset_blob', 'UPDATE');" \
  | tr -d '[:space:]')"
if [[ "${auth_blob_upd}" == "t" ]]; then
  echo "FAIL: ezkey_auth must not UPDATE ezkey_keyset_blob" >&2
  exit 1
fi
echo "OK (denied): auth cannot UPDATE ezkey_keyset_blob"

int_blob_ins="$(run_psql ezkey_integration "${INTEGRATION_PASS}" -tAc \
  "SELECT has_table_privilege('ezkey_integration', 'ezkey_keyset_blob', 'INSERT');" \
  | tr -d '[:space:]')"
if [[ "${int_blob_ins}" == "t" ]]; then
  echo "FAIL: ezkey_integration must not INSERT ezkey_keyset_blob" >&2
  exit 1
fi
echo "OK (denied): integration cannot INSERT ezkey_keyset_blob"

int_blob_upd="$(run_psql ezkey_integration "${INTEGRATION_PASS}" -tAc \
  "SELECT has_table_privilege('ezkey_integration', 'ezkey_keyset_blob', 'UPDATE');" \
  | tr -d '[:space:]')"
if [[ "${int_blob_upd}" == "t" ]]; then
  echo "FAIL: ezkey_integration must not UPDATE ezkey_keyset_blob" >&2
  exit 1
fi
echo "OK (denied): integration cannot UPDATE ezkey_keyset_blob"

admin_enc_ins="$(run_psql ezkey_admin "${ADMIN_PASS}" -tAc \
  "SELECT has_table_privilege('ezkey_admin', 'ezkey_encryption_key', 'INSERT');" \
  | tr -d '[:space:]')"
if [[ "${admin_enc_ins}" != "t" ]]; then
  echo "FAIL: ezkey_admin should INSERT ezkey_encryption_key (got '${admin_enc_ins}')" >&2
  exit 1
fi
echo "OK: admin can INSERT ezkey_encryption_key"

admin_blob_ins="$(run_psql ezkey_admin "${ADMIN_PASS}" -tAc \
  "SELECT has_table_privilege('ezkey_admin', 'ezkey_keyset_blob', 'INSERT');" \
  | tr -d '[:space:]')"
if [[ "${admin_blob_ins}" != "t" ]]; then
  echo "FAIL: ezkey_admin should INSERT ezkey_keyset_blob (got '${admin_blob_ins}')" >&2
  exit 1
fi
echo "OK: admin can INSERT ezkey_keyset_blob"

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

# ---------------------------------------------------------------------------
# ezkey_crypto — SELECT-only on ezkey_keyset_blob (ADR-0016 / #952)
# ---------------------------------------------------------------------------
expect_ok "crypto can SELECT ezkey_keyset_blob" \
  run_psql ezkey_crypto "${CRYPTO_PASS}" -c "SELECT 1 FROM ezkey_keyset_blob LIMIT 0;"

crypto_blob_ins="$(run_psql ezkey_crypto "${CRYPTO_PASS}" -tAc \
  "SELECT has_table_privilege('ezkey_crypto', 'ezkey_keyset_blob', 'INSERT');" \
  | tr -d '[:space:]')"
if [[ "${crypto_blob_ins}" == "t" ]]; then
  echo "FAIL: ezkey_crypto must not INSERT ezkey_keyset_blob" >&2
  exit 1
fi
echo "OK (denied): crypto cannot INSERT ezkey_keyset_blob"

crypto_blob_upd="$(run_psql ezkey_crypto "${CRYPTO_PASS}" -tAc \
  "SELECT has_table_privilege('ezkey_crypto', 'ezkey_keyset_blob', 'UPDATE');" \
  | tr -d '[:space:]')"
if [[ "${crypto_blob_upd}" == "t" ]]; then
  echo "FAIL: ezkey_crypto must not UPDATE ezkey_keyset_blob" >&2
  exit 1
fi
echo "OK (denied): crypto cannot UPDATE ezkey_keyset_blob"

crypto_blob_del="$(run_psql ezkey_crypto "${CRYPTO_PASS}" -tAc \
  "SELECT has_table_privilege('ezkey_crypto', 'ezkey_keyset_blob', 'DELETE');" \
  | tr -d '[:space:]')"
if [[ "${crypto_blob_del}" == "t" ]]; then
  echo "FAIL: ezkey_crypto must not DELETE ezkey_keyset_blob" >&2
  exit 1
fi
echo "OK (denied): crypto cannot DELETE ezkey_keyset_blob"

expect_fail "crypto cannot SELECT ezkey_encryption_key" \
  run_psql ezkey_crypto "${CRYPTO_PASS}" -c "SELECT 1 FROM ezkey_encryption_key LIMIT 0;"

expect_fail "crypto cannot SELECT ezkey_enrollment" \
  run_psql ezkey_crypto "${CRYPTO_PASS}" -c "SELECT 1 FROM ezkey_enrollment LIMIT 0;"

expect_fail "crypto cannot SELECT ezkey_audit_log" \
  run_psql ezkey_crypto "${CRYPTO_PASS}" -c "SELECT 1 FROM ezkey_audit_log LIMIT 0;"

expect_fail "crypto cannot SELECT ezkey_admin_tokens" \
  run_psql ezkey_crypto "${CRYPTO_PASS}" -c "SELECT 1 FROM ezkey_admin_tokens LIMIT 0;"

# ---------------------------------------------------------------------------
# ezkey_crypto — catalog enumeration / attributes (Christophe C-M2 / #961)
# Superuser reads only; never print role passwords.
# ---------------------------------------------------------------------------
crypto_table_grants="$(run_psql "${SUPER_USER}" "${SUPER_PASS}" -tAc \
  "SELECT coalesce(string_agg(table_name || ':' || privilege_type, ',' ORDER BY 1), '')
   FROM information_schema.role_table_grants
   WHERE grantee = 'ezkey_crypto';" | tr -d '[:space:]')"
if [[ "${crypto_table_grants}" != "ezkey_keyset_blob:SELECT" ]]; then
  echo "FAIL: ezkey_crypto table grants must be exactly ezkey_keyset_blob:SELECT; got: ${crypto_table_grants}" >&2
  exit 1
fi
echo "OK: ezkey_crypto table grants enumeration is ezkey_keyset_blob:SELECT only"

crypto_dangerous_attrs="$(run_psql "${SUPER_USER}" "${SUPER_PASS}" -tAc \
  "SELECT (rolsuper OR rolcreaterole OR rolcreatedb OR rolbypassrls OR rolreplication)::text
   FROM pg_roles WHERE rolname = 'ezkey_crypto';" | tr -d '[:space:]')"
if [[ "${crypto_dangerous_attrs}" != "f" ]]; then
  echo "FAIL: ezkey_crypto must not have superuser/createrole/createdb/bypassrls/replication; got: ${crypto_dangerous_attrs}" >&2
  exit 1
fi
echo "OK: ezkey_crypto has no elevated role attributes"

crypto_memberships="$(run_psql "${SUPER_USER}" "${SUPER_PASS}" -tAc \
  "SELECT count(*)::text FROM pg_auth_members m
   JOIN pg_roles r ON r.oid = m.member
   WHERE r.rolname = 'ezkey_crypto';" | tr -d '[:space:]')"
if [[ "${crypto_memberships}" != "0" ]]; then
  echo "FAIL: ezkey_crypto must not be a member of other roles; membership count=${crypto_memberships}" >&2
  exit 1
fi
echo "OK: ezkey_crypto has no role memberships"

crypto_usage_grants="$(run_psql "${SUPER_USER}" "${SUPER_PASS}" -tAc \
  "SELECT count(*)::text FROM information_schema.role_usage_grants
   WHERE grantee = 'ezkey_crypto' AND object_type <> 'TYPE';" | tr -d '[:space:]')"
if [[ "${crypto_usage_grants}" != "0" ]]; then
  echo "FAIL: ezkey_crypto must have no sequence/function USAGE grants; count=${crypto_usage_grants}" >&2
  exit 1
fi
echo "OK: ezkey_crypto has no sequence/function USAGE grants"

echo "All grant checks passed."
