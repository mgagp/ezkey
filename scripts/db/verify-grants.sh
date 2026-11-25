#!/usr/bin/env bash
# Smoke-check Ezkey role privileges against a running database.
# Expects roles + apply-grants already applied.
#
# Usage:
#   ./scripts/db/verify-grants.sh
#   ./scripts/db/verify-grants.sh --docker
#
# Credentials (never put password values on process argv, and never `source` / `.` a .env):
#   PGUSER / POSTGRES_USER, PGPASSWORD / POSTGRES_PASSWORD — admin/owner login
#   EZKEY_DB_MIGRATE_PASSWORD, EZKEY_DB_ADMIN_PASSWORD, EZKEY_DB_AUTH_PASSWORD,
#   EZKEY_DB_INTEGRATION_PASSWORD, EZKEY_DB_CRYPTO_PASSWORD — optional role logins
#   EZKEY_POSTGRES_CONTAINER — docker container name (default: ezkey-postgres)
#     --docker exits 2 with "container not found or not running" before any grant
#     check when that container is absent or stopped (#1490).
#
# Env file: when `./.env` exists (or `--env-file PATH` is set), only the keys above
# plus PGHOST / PGPORT / PGDATABASE are read, as literal text. Unquoted spaces and
# cron expressions are not executed. Variables already set in the environment win.
# `--no-env-file` skips that read. Do not `. ./.env`.
#
# When all five EZKEY_DB_* role passwords are set, connects as each role (stronger
# smoke: real SQL allow/deny). When any are absent, falls back to catalog checks
# (has_table_privilege / information_schema.role_table_grants) as the admin/owner.

set -euo pipefail

# Keys this script may copy out of an env file. Anything else is ignored.
ENV_FILE_KEYS=(
  PGHOST
  PGPORT
  PGDATABASE
  PGUSER
  PGPASSWORD
  POSTGRES_USER
  POSTGRES_PASSWORD
  EZKEY_DB_MIGRATE_PASSWORD
  EZKEY_DB_ADMIN_PASSWORD
  EZKEY_DB_AUTH_PASSWORD
  EZKEY_DB_INTEGRATION_PASSWORD
  EZKEY_DB_CRYPTO_PASSWORD
  EZKEY_POSTGRES_CONTAINER
)

env_file_key_allowed() {
  local key="$1"
  local allowed
  for allowed in "${ENV_FILE_KEYS[@]}"; do
    if [[ "${allowed}" == "${key}" ]]; then
      return 0
    fi
  done
  return 1
}

# Literal dotenv value: strip one pair of matching quotes. No eval, no globs,
# no parameter expansion. Unquoted text keeps spaces. A trailing " # comment"
# is dropped only when the value is unquoted.
dotenv_unquote() {
  local raw="$1"
  raw="${raw#"${raw%%[![:space:]]*}"}"
  local len=${#raw}
  if [[ "${len}" -ge 2 && "${raw:0:1}" == '"' ]]; then
    local i=1
    local inner=""
    local c
    while [[ "${i}" -lt "${len}" ]]; do
      c="${raw:i:1}"
      if [[ "${c}" == '\' && $((i + 1)) -lt "${len}" ]]; then
        local nxt="${raw:i+1:1}"
        if [[ "${nxt}" == '"' || "${nxt}" == '\' ]]; then
          inner+="${nxt}"
          i=$((i + 2))
          continue
        fi
      fi
      if [[ "${c}" == '"' ]]; then
        printf '%s' "${inner}"
        return 0
      fi
      inner+="${c}"
      i=$((i + 1))
    done
    printf '%s' "${raw}"
    return 0
  fi
  if [[ "${len}" -ge 2 && "${raw:0:1}" == "'" ]]; then
    local inner="${raw:1}"
    local end="${inner%%\'*}"
    if [[ "${inner}" == *"'"* ]]; then
      printf '%s' "${end}"
      return 0
    fi
  fi
  local unquoted="${raw%%[[:space:]]#*}"
  unquoted="${unquoted%"${unquoted##*[![:space:]]}"}"
  printf '%s' "${unquoted}"
}

# Fill unset keys from PATH. Does not override the environment. Does not eval.
apply_env_file() {
  local file="$1"
  if [[ ! -f "${file}" ]]; then
    echo "error: env file not found: ${file}" >&2
    exit 1
  fi
  if [[ ! -r "${file}" ]]; then
    echo "error: env file is not readable: ${file}" >&2
    exit 1
  fi
  local line key raw val
  local first=1
  while IFS= read -r line || [[ -n "${line}" ]]; do
    if [[ "${first}" -eq 1 ]]; then
      line="${line#$'\xef\xbb\xbf'}"
      first=0
    fi
    line="${line%$'\r'}"
    [[ "${line}" =~ ^[[:space:]]*$ ]] && continue
    [[ "${line}" =~ ^[[:space:]]*# ]] && continue
    if [[ ! "${line}" =~ ^[[:space:]]*(export[[:space:]]+)?([A-Za-z_][A-Za-z0-9_]*)[[:space:]]*=(.*)$ ]]; then
      continue
    fi
    key="${BASH_REMATCH[2]}"
    raw="${BASH_REMATCH[3]}"
    if ! env_file_key_allowed "${key}"; then
      continue
    fi
    if [[ -n "${!key+x}" ]]; then
      continue
    fi
    val="$(dotenv_unquote "${raw}")"
    printf -v "${key}" '%s' "${val}"
  done <"${file}"
}

DOCKER_MODE=0
ENV_FILE=""
ENV_FILE_SET=0
NO_ENV_FILE=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --docker)
      DOCKER_MODE=1
      shift
      ;;
    --env-file)
      if [[ -z "${2:-}" || "${2}" == --* ]]; then
        echo "error: --env-file requires a path" >&2
        exit 2
      fi
      ENV_FILE="$2"
      ENV_FILE_SET=1
      shift 2
      ;;
    --no-env-file)
      NO_ENV_FILE=1
      shift
      ;;
    *)
      echo "error: unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

if [[ "${NO_ENV_FILE}" -eq 1 && "${ENV_FILE_SET}" -eq 1 ]]; then
  echo "error: --no-env-file cannot be combined with --env-file" >&2
  exit 2
fi

if [[ "${NO_ENV_FILE}" -eq 0 ]]; then
  if [[ "${ENV_FILE_SET}" -eq 0 && -f ./.env ]]; then
    ENV_FILE="./.env"
  fi
  if [[ -n "${ENV_FILE}" ]]; then
    apply_env_file "${ENV_FILE}"
  fi
fi

export PGHOST="${PGHOST:-localhost}"
export PGPORT="${PGPORT:-5432}"
export PGDATABASE="${PGDATABASE:-ezkey_db}"

# Admin/owner: prefer libpq names, then Compose .env names.
SUPER_USER="${PGUSER:-${POSTGRES_USER:-postgres}}"
SUPER_PASS="${PGPASSWORD:-${POSTGRES_PASSWORD:-ezkey}}"

# Role passwords: empty means absent (no silent lab defaults here — community .env
# often omits them; catalog fallback covers that case).
MIGRATE_PASS="${EZKEY_DB_MIGRATE_PASSWORD:-}"
ADMIN_PASS="${EZKEY_DB_ADMIN_PASSWORD:-}"
AUTH_PASS="${EZKEY_DB_AUTH_PASSWORD:-}"
INTEGRATION_PASS="${EZKEY_DB_INTEGRATION_PASSWORD:-}"
CRYPTO_PASS="${EZKEY_DB_CRYPTO_PASSWORD:-}"

if [[ "${DOCKER_MODE}" -eq 1 ]]; then
  CONTAINER="${EZKEY_POSTGRES_CONTAINER:-ezkey-postgres}"
  # Stop before any grant check: a missing container would otherwise surface as
  # grant FAIL lines that read like a privilege regression (#1490).
  container_running="$(docker inspect --type container -f '{{.State.Running}}' \
    "${CONTAINER}" 2>/dev/null || true)"
  if [[ "${container_running}" != "true" ]]; then
    echo "error: container not found or not running: ${CONTAINER}" >&2
    echo "hint: set EZKEY_POSTGRES_CONTAINER to the Postgres container name (community host: ezkey-exp-postgres)" >&2
    exit 2
  fi
fi

VERIFY_OUT="$(mktemp "${TMPDIR:-/tmp}/ezkey-verify-grants.out.XXXXXX")"
VERIFY_ERR="$(mktemp "${TMPDIR:-/tmp}/ezkey-verify-grants.err.XXXXXX")"
trap 'rm -f "${VERIFY_OUT}" "${VERIFY_ERR}"' EXIT

# Pass credentials via environment only. For docker exec, use -e NAME (no =value)
# so the password never appears on argv / process listings.
run_psql() {
  local user="$1"
  local pass="$2"
  shift 2
  if [[ "${DOCKER_MODE}" -eq 1 ]]; then
    PGPASSWORD="${pass}" docker exec -e PGPASSWORD -i "${CONTAINER}" \
      psql -v ON_ERROR_STOP=1 -U "${user}" -d ezkey_db "$@"
  else
    PGPASSWORD="${pass}" psql -v ON_ERROR_STOP=1 -h "${PGHOST}" -p "${PGPORT}" \
      -U "${user}" -d "${PGDATABASE}" "$@"
  fi
}

# Catalog queries always run as admin/owner.
run_admin_psql() {
  run_psql "${SUPER_USER}" "${SUPER_PASS}" "$@"
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

# ---------------------------------------------------------------------------
# Catalog helpers (admin/owner session): has_table_privilege / has_function_privilege
# ---------------------------------------------------------------------------

# Assert has_table_privilege(role, table, priv) equals expect (t|f).
assert_table_priv() {
  local label="$1"
  local role="$2"
  local table="$3"
  local priv="$4"
  local expect="$5"
  local got
  got="$(run_admin_psql -tAc \
    "SELECT has_table_privilege('${role}', '${table}', '${priv}');" \
    | tr -d '[:space:]')"
  if [[ "${got}" != "${expect}" ]]; then
    if [[ "${expect}" == "t" ]]; then
      echo "FAIL: expected success for: ${label} (got '${got}')" >&2
    else
      echo "FAIL: expected denial for: ${label} (got '${got}')" >&2
    fi
    exit 1
  fi
  if [[ "${expect}" == "t" ]]; then
    echo "OK: ${label}"
  else
    echo "OK (denied): ${label}"
  fi
}

assert_function_priv() {
  local label="$1"
  local role="$2"
  local signature="$3"
  local priv="$4"
  local expect="$5"
  local got
  got="$(run_admin_psql -tAc \
    "SELECT has_function_privilege('${role}', '${signature}', '${priv}');" \
    | tr -d '[:space:]')"
  if [[ "${got}" != "${expect}" ]]; then
    if [[ "${expect}" == "t" ]]; then
      echo "FAIL: expected success for: ${label} (got '${got}')" >&2
    else
      echo "FAIL: expected denial for: ${label} (got '${got}')" >&2
    fi
    exit 1
  fi
  if [[ "${expect}" == "t" ]]; then
    echo "OK: ${label}"
  else
    echo "OK (denied): ${label}"
  fi
}

# Shared crypto catalog assertions (also used at end of role-login mode).
verify_crypto_catalog() {
  local crypto_table_grants crypto_dangerous_attrs crypto_memberships crypto_usage_grants

  crypto_table_grants="$(run_admin_psql -tAc \
    "SELECT coalesce(string_agg(table_name || ':' || privilege_type, ',' ORDER BY 1), '')
     FROM information_schema.role_table_grants
     WHERE grantee = 'ezkey_crypto';" | tr -d '[:space:]')"
  if [[ "${crypto_table_grants}" != "ezkey_keyset_blob:SELECT" ]]; then
    echo "FAIL: ezkey_crypto table grants must be exactly ezkey_keyset_blob:SELECT; got: ${crypto_table_grants}" >&2
    exit 1
  fi
  echo "OK: ezkey_crypto table grants enumeration is ezkey_keyset_blob:SELECT only"

  # Boolean without ::text so psql -tAc prints f/t (not true/false) — matches other checks.
  crypto_dangerous_attrs="$(run_admin_psql -tAc \
    "SELECT rolsuper OR rolcreaterole OR rolcreatedb OR rolbypassrls OR rolreplication
     FROM pg_roles WHERE rolname = 'ezkey_crypto';" | tr -d '[:space:]')"
  if [[ "${crypto_dangerous_attrs}" != "f" ]]; then
    echo "FAIL: ezkey_crypto must not have superuser/createrole/createdb/bypassrls/replication; got: ${crypto_dangerous_attrs}" >&2
    exit 1
  fi
  echo "OK: ezkey_crypto has no elevated role attributes"

  crypto_memberships="$(run_admin_psql -tAc \
    "SELECT count(*)::text FROM pg_auth_members m
     JOIN pg_roles r ON r.oid = m.member
     WHERE r.rolname = 'ezkey_crypto';" | tr -d '[:space:]')"
  if [[ "${crypto_memberships}" != "0" ]]; then
    echo "FAIL: ezkey_crypto must not be a member of other roles; membership count=${crypto_memberships}" >&2
    exit 1
  fi
  echo "OK: ezkey_crypto has no role memberships"

  crypto_usage_grants="$(run_admin_psql -tAc \
    "SELECT count(*)::text FROM information_schema.role_usage_grants
     WHERE grantee = 'ezkey_crypto' AND object_type <> 'TYPE';" | tr -d '[:space:]')"
  if [[ "${crypto_usage_grants}" != "0" ]]; then
    echo "FAIL: ezkey_crypto must have no sequence/function USAGE grants; count=${crypto_usage_grants}" >&2
    exit 1
  fi
  echo "OK: ezkey_crypto has no sequence/function USAGE grants"
}

# Catalog-mode verification: same labels as role-login path, via privilege catalogs.
verify_grants_via_catalog() {
  echo "Mode: catalog (role passwords absent — checking grants as ${SUPER_USER} via has_table_privilege / role_table_grants)."
  echo "Set EZKEY_DB_MIGRATE_PASSWORD, EZKEY_DB_ADMIN_PASSWORD, EZKEY_DB_AUTH_PASSWORD, EZKEY_DB_INTEGRATION_PASSWORD, and EZKEY_DB_CRYPTO_PASSWORD to run per-role login smoke instead."

  assert_table_priv "auth can SELECT ezkey_enrollment" \
    ezkey_auth ezkey_enrollment SELECT t
  assert_table_priv "auth cannot DELETE ezkey_audit_log" \
    ezkey_auth ezkey_audit_log DELETE f
  assert_table_priv "auth cannot UPDATE ezkey_audit_log (append-only)" \
    ezkey_auth ezkey_audit_log UPDATE f
  assert_table_priv "auth can INSERT ezkey_audit_log" \
    ezkey_auth ezkey_audit_log INSERT t

  assert_table_priv "integration cannot UPDATE ezkey_audit_log (append-only)" \
    ezkey_integration ezkey_audit_log UPDATE f
  assert_table_priv "integration can INSERT ezkey_audit_log" \
    ezkey_integration ezkey_audit_log INSERT t
  assert_table_priv "integration cannot UPDATE ezkey_enrollment" \
    ezkey_integration ezkey_enrollment UPDATE f
  assert_table_priv "integration cannot SELECT ezkey_admin_tokens" \
    ezkey_integration ezkey_admin_tokens SELECT f

  assert_table_priv "auth cannot SELECT ezkey_admin_tokens" \
    ezkey_auth ezkey_admin_tokens SELECT f
  assert_table_priv "auth cannot UPDATE ezkey_admin_tokens" \
    ezkey_auth ezkey_admin_tokens UPDATE f
  assert_table_priv "auth cannot INSERT ezkey_admin_tokens" \
    ezkey_auth ezkey_admin_tokens INSERT f
  assert_table_priv "auth cannot DELETE ezkey_admin_tokens" \
    ezkey_auth ezkey_admin_tokens DELETE f

  assert_table_priv "admin can SELECT ezkey_admin_tokens" \
    ezkey_admin ezkey_admin_tokens SELECT t
  assert_table_priv "admin can SELECT ezkey_shedlock" \
    ezkey_admin ezkey_shedlock SELECT t
  assert_table_priv "admin can SELECT ezkey_integrity_async_job" \
    ezkey_admin ezkey_integrity_async_job SELECT t
  assert_table_priv "auth cannot SELECT ezkey_integrity_async_job" \
    ezkey_auth ezkey_integrity_async_job SELECT f
  assert_table_priv "integration cannot SELECT ezkey_integrity_async_job" \
    ezkey_integration ezkey_integrity_async_job SELECT f
  assert_table_priv "admin can INSERT ezkey_integrity_async_job" \
    ezkey_admin ezkey_integrity_async_job INSERT t
  assert_table_priv "admin can UPDATE ezkey_integrity_async_job" \
    ezkey_admin ezkey_integrity_async_job UPDATE t

  assert_table_priv "auth can SELECT ezkey_encryption_key" \
    ezkey_auth ezkey_encryption_key SELECT t
  assert_table_priv "auth can SELECT ezkey_keyset_blob" \
    ezkey_auth ezkey_keyset_blob SELECT t
  assert_table_priv "integration can SELECT ezkey_encryption_key" \
    ezkey_integration ezkey_encryption_key SELECT t
  assert_table_priv "integration can SELECT ezkey_keyset_blob" \
    ezkey_integration ezkey_keyset_blob SELECT t

  assert_table_priv "auth cannot INSERT ezkey_encryption_key" \
    ezkey_auth ezkey_encryption_key INSERT f
  assert_table_priv "auth cannot UPDATE ezkey_encryption_key" \
    ezkey_auth ezkey_encryption_key UPDATE f
  assert_table_priv "integration cannot INSERT ezkey_encryption_key" \
    ezkey_integration ezkey_encryption_key INSERT f
  assert_table_priv "integration cannot UPDATE ezkey_encryption_key" \
    ezkey_integration ezkey_encryption_key UPDATE f
  assert_table_priv "auth cannot INSERT ezkey_keyset_blob" \
    ezkey_auth ezkey_keyset_blob INSERT f
  assert_table_priv "auth cannot UPDATE ezkey_keyset_blob" \
    ezkey_auth ezkey_keyset_blob UPDATE f
  assert_table_priv "integration cannot INSERT ezkey_keyset_blob" \
    ezkey_integration ezkey_keyset_blob INSERT f
  assert_table_priv "integration cannot UPDATE ezkey_keyset_blob" \
    ezkey_integration ezkey_keyset_blob UPDATE f

  assert_table_priv "admin can INSERT ezkey_encryption_key" \
    ezkey_admin ezkey_encryption_key INSERT t
  assert_table_priv "admin can INSERT ezkey_keyset_blob" \
    ezkey_admin ezkey_keyset_blob INSERT t

  assert_function_priv "admin has EXECUTE on create_monthly_partition" \
    ezkey_admin 'create_monthly_partition(text,text,timestamptz,timestamptz)' EXECUTE t
  assert_function_priv "auth has no EXECUTE on create_monthly_partition" \
    ezkey_auth 'create_monthly_partition(text,text,timestamptz,timestamptz)' EXECUTE f

  assert_table_priv "migrate can see flyway_schema_history" \
    ezkey_migrate flyway_schema_history SELECT t

  assert_table_priv "crypto can SELECT ezkey_keyset_blob" \
    ezkey_crypto ezkey_keyset_blob SELECT t
  assert_table_priv "crypto cannot INSERT ezkey_keyset_blob" \
    ezkey_crypto ezkey_keyset_blob INSERT f
  assert_table_priv "crypto cannot UPDATE ezkey_keyset_blob" \
    ezkey_crypto ezkey_keyset_blob UPDATE f
  assert_table_priv "crypto cannot DELETE ezkey_keyset_blob" \
    ezkey_crypto ezkey_keyset_blob DELETE f
  assert_table_priv "crypto cannot SELECT ezkey_encryption_key" \
    ezkey_crypto ezkey_encryption_key SELECT f
  assert_table_priv "crypto cannot SELECT ezkey_enrollment" \
    ezkey_crypto ezkey_enrollment SELECT f
  assert_table_priv "crypto cannot SELECT ezkey_audit_log" \
    ezkey_crypto ezkey_audit_log SELECT f
  assert_table_priv "crypto cannot SELECT ezkey_admin_tokens" \
    ezkey_crypto ezkey_admin_tokens SELECT f

  verify_crypto_catalog
}

# Role-login verification: connect as each application role (requires passwords).
verify_grants_via_role_login() {
  echo "Mode: role-login (all EZKEY_DB_* role passwords are set)."

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

  assert_table_priv "admin can INSERT ezkey_integrity_async_job" \
    ezkey_admin ezkey_integrity_async_job INSERT t
  assert_table_priv "admin can UPDATE ezkey_integrity_async_job" \
    ezkey_admin ezkey_integrity_async_job UPDATE t

  expect_ok "auth can SELECT ezkey_encryption_key" \
    run_psql ezkey_auth "${AUTH_PASS}" -c "SELECT 1 FROM ezkey_encryption_key LIMIT 0;"

  expect_ok "auth can SELECT ezkey_keyset_blob" \
    run_psql ezkey_auth "${AUTH_PASS}" -c "SELECT 1 FROM ezkey_keyset_blob LIMIT 0;"

  expect_ok "integration can SELECT ezkey_encryption_key" \
    run_psql ezkey_integration "${INTEGRATION_PASS}" -c "SELECT 1 FROM ezkey_encryption_key LIMIT 0;"

  expect_ok "integration can SELECT ezkey_keyset_blob" \
    run_psql ezkey_integration "${INTEGRATION_PASS}" -c "SELECT 1 FROM ezkey_keyset_blob LIMIT 0;"

  assert_table_priv "auth cannot INSERT ezkey_encryption_key" \
    ezkey_auth ezkey_encryption_key INSERT f
  assert_table_priv "auth cannot UPDATE ezkey_encryption_key" \
    ezkey_auth ezkey_encryption_key UPDATE f
  assert_table_priv "integration cannot INSERT ezkey_encryption_key" \
    ezkey_integration ezkey_encryption_key INSERT f
  assert_table_priv "integration cannot UPDATE ezkey_encryption_key" \
    ezkey_integration ezkey_encryption_key UPDATE f
  assert_table_priv "auth cannot INSERT ezkey_keyset_blob" \
    ezkey_auth ezkey_keyset_blob INSERT f
  assert_table_priv "auth cannot UPDATE ezkey_keyset_blob" \
    ezkey_auth ezkey_keyset_blob UPDATE f
  assert_table_priv "integration cannot INSERT ezkey_keyset_blob" \
    ezkey_integration ezkey_keyset_blob INSERT f
  assert_table_priv "integration cannot UPDATE ezkey_keyset_blob" \
    ezkey_integration ezkey_keyset_blob UPDATE f
  assert_table_priv "admin can INSERT ezkey_encryption_key" \
    ezkey_admin ezkey_encryption_key INSERT t
  assert_table_priv "admin can INSERT ezkey_keyset_blob" \
    ezkey_admin ezkey_keyset_blob INSERT t

  assert_function_priv "admin has EXECUTE on create_monthly_partition" \
    ezkey_admin 'create_monthly_partition(text,text,timestamptz,timestamptz)' EXECUTE t
  assert_function_priv "auth has no EXECUTE on create_monthly_partition" \
    ezkey_auth 'create_monthly_partition(text,text,timestamptz,timestamptz)' EXECUTE f

  expect_ok "migrate can see flyway_schema_history" \
    run_psql ezkey_migrate "${MIGRATE_PASS}" -c "SELECT 1 FROM flyway_schema_history LIMIT 0;"

  # ---------------------------------------------------------------------------
  # ezkey_crypto — SELECT-only on ezkey_keyset_blob (ADR-0016 / #952)
  # ---------------------------------------------------------------------------
  expect_ok "crypto can SELECT ezkey_keyset_blob" \
    run_psql ezkey_crypto "${CRYPTO_PASS}" -c "SELECT 1 FROM ezkey_keyset_blob LIMIT 0;"

  assert_table_priv "crypto cannot INSERT ezkey_keyset_blob" \
    ezkey_crypto ezkey_keyset_blob INSERT f
  assert_table_priv "crypto cannot UPDATE ezkey_keyset_blob" \
    ezkey_crypto ezkey_keyset_blob UPDATE f
  assert_table_priv "crypto cannot DELETE ezkey_keyset_blob" \
    ezkey_crypto ezkey_keyset_blob DELETE f

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
  verify_crypto_catalog
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

echo "Verifying Ezkey database role grants..."

ROLE_PASSWORDS_PRESENT=0
if [[ -n "${MIGRATE_PASS}" && -n "${ADMIN_PASS}" && -n "${AUTH_PASS}" \
  && -n "${INTEGRATION_PASS}" && -n "${CRYPTO_PASS}" ]]; then
  ROLE_PASSWORDS_PRESENT=1
fi

if [[ "${ROLE_PASSWORDS_PRESENT}" -eq 1 ]]; then
  verify_grants_via_role_login
else
  verify_grants_via_catalog
fi

echo "All grant checks passed."
