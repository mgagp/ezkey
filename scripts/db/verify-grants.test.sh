#!/usr/bin/env bash
# Locks verify-grants.sh credential handling and catalog fail-closed checks (#1121).
#
# No Docker and no Postgres. Fake psql/docker record argv and environment so a
# regression that puts role passwords on process argv fails here.
#
# Run from anywhere:
#   ./scripts/db/verify-grants.test.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT="${ROOT}/scripts/db/verify-grants.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

BIN="${TMP}/bin"
mkdir -p "$BIN"

cat >"${BIN}/psql" <<'EOF'
#!/usr/bin/env bash
# Deterministic stand-in for the privilege matrix verify-grants.sh asserts.
set -euo pipefail

log="${FAKE_PSQL_LOG:?}"
user=""
sql=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    -U)
      user="${2:-}"
      shift 2
      ;;
    -c|-tAc)
      sql="${2:-}"
      shift 2
      ;;
    *)
      shift
      ;;
  esac
done

{
  printf 'USER=%s\n' "${user}"
  printf 'PW=%s\n' "${PGPASSWORD-}"
  printf '%s\n' '---'
} >>"${log}"

if [[ -z "${sql}" ]]; then
  echo "fake-psql: missing SQL" >&2
  exit 2
fi

pad() {
  if [[ "${FAKE_PRIV_PAD:-0}" == "1" ]]; then
    printf ' %s \n' "$1"
  else
    printf '%s\n' "$1"
  fi
}

deny() {
  echo "denied by fake-psql" >&2
  exit 1
}

if [[ "${sql}" == *"has_table_privilege"* ]]; then
  if [[ ! "${sql}" =~ has_table_privilege\(\'([^\']+)\',\ \'([^\']+)\',\ \'([^\']+)\'\) ]]; then
    echo "fake-psql: unparsed has_table_privilege" >&2
    exit 2
  fi
  allow="f"
  case "${BASH_REMATCH[1]}|${BASH_REMATCH[2]}|${BASH_REMATCH[3]}" in
    "ezkey_auth|ezkey_enrollment|SELECT"|\
    "ezkey_auth|ezkey_audit_log|INSERT"|\
    "ezkey_integration|ezkey_audit_log|INSERT"|\
    "ezkey_admin|ezkey_admin_tokens|SELECT"|\
    "ezkey_admin|ezkey_shedlock|SELECT"|\
    "ezkey_admin|ezkey_integrity_async_job|SELECT"|\
    "ezkey_admin|ezkey_integrity_async_job|INSERT"|\
    "ezkey_admin|ezkey_integrity_async_job|UPDATE"|\
    "ezkey_auth|ezkey_encryption_key|SELECT"|\
    "ezkey_auth|ezkey_keyset_blob|SELECT"|\
    "ezkey_integration|ezkey_encryption_key|SELECT"|\
    "ezkey_integration|ezkey_keyset_blob|SELECT"|\
    "ezkey_admin|ezkey_encryption_key|INSERT"|\
    "ezkey_admin|ezkey_keyset_blob|INSERT"|\
    "ezkey_migrate|flyway_schema_history|SELECT"|\
    "ezkey_crypto|ezkey_keyset_blob|SELECT")
      allow="t"
      ;;
  esac
  pad "${allow}"
  exit 0
fi

if [[ "${sql}" == *"has_function_privilege"* ]]; then
  if [[ ! "${sql}" =~ has_function_privilege\(\'([^\']+)\',\ \'([^\']+)\',\ \'([^\']+)\'\) ]]; then
    echo "fake-psql: unparsed has_function_privilege" >&2
    exit 2
  fi
  if [[ "${BASH_REMATCH[1]}" == "ezkey_admin" && "${BASH_REMATCH[3]}" == "EXECUTE" ]]; then
    pad "t"
  else
    pad "f"
  fi
  exit 0
fi

if [[ "${sql}" == *"role_table_grants"* ]]; then
  pad "${FAKE_CRYPTO_TABLE_GRANTS:-ezkey_keyset_blob:SELECT}"
  exit 0
fi

if [[ "${sql}" == *"rolsuper"* ]]; then
  pad "${FAKE_CRYPTO_DANGEROUS:-f}"
  exit 0
fi

if [[ "${sql}" == *"pg_auth_members"* ]]; then
  pad "${FAKE_MEMBERSHIPS:-0}"
  exit 0
fi

if [[ "${sql}" == *"role_usage_grants"* ]]; then
  pad "${FAKE_USAGE:-0}"
  exit 0
fi

if [[ "${sql}" == *"INSERT INTO ezkey_admin_tokens"* ]]; then
  deny
fi
if [[ "${sql}" == *"INSERT INTO ezkey_audit_log"* ]]; then
  exit 0
fi
if [[ "${sql}" == *"UPDATE "* || "${sql}" == *"DELETE FROM"* ]]; then
  deny
fi

if [[ ! "${sql}" =~ FROM[[:space:]]+([a-z_]+) ]]; then
  echo "fake-psql: unparsed operational SQL" >&2
  exit 2
fi
table="${BASH_REMATCH[1]}"
case "${user}|${table}" in
  "ezkey_crypto|ezkey_keyset_blob")
    exit 0
    ;;
  ezkey_crypto\|*)
    deny
    ;;
  "ezkey_auth|ezkey_admin_tokens"|\
  "ezkey_auth|ezkey_integrity_async_job"|\
  "ezkey_integration|ezkey_admin_tokens"|\
  "ezkey_integration|ezkey_integrity_async_job")
    deny
    ;;
esac
exit 0
EOF

cat >"${BIN}/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
log="${FAKE_DOCKER_LOG:?}"
{
  printf 'ARGV'
  for arg in "$@"; do
    printf ' %q' "${arg}"
  done
  printf '\n'
  printf 'ENV_PGPASSWORD=%q\n' "${PGPASSWORD-}"
  printf '%s\n' '---'
} >>"${log}"

if [[ "${1:-}" != "exec" ]]; then
  echo "fake-docker: expected exec, got ${1:-}" >&2
  exit 1
fi
shift
while [[ $# -gt 0 && "$1" != "psql" ]]; do
  shift
done
if [[ "${1:-}" != "psql" ]]; then
  echo "fake-docker: psql not in exec argv" >&2
  exit 1
fi
shift
exec psql "$@"
EOF

chmod 755 "${BIN}/psql" "${BIN}/docker"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

# Env assignments (KEY=value) then verify-grants.sh arguments.
# Clears inherited Postgres credentials so the workstation environment cannot leak in.
run_verify() {
  local out="$1"
  local err="$2"
  shift 2
  local -a env_kv=()
  while [[ $# -gt 0 && "$1" == *=* ]]; do
    env_kv+=("$1")
    shift
  done
  set +e
  env -u PGHOST -u PGPORT -u PGDATABASE \
    -u PGUSER -u PGPASSWORD -u POSTGRES_USER -u POSTGRES_PASSWORD \
    -u EZKEY_DB_MIGRATE_PASSWORD -u EZKEY_DB_ADMIN_PASSWORD \
    -u EZKEY_DB_AUTH_PASSWORD -u EZKEY_DB_INTEGRATION_PASSWORD \
    -u EZKEY_DB_CRYPTO_PASSWORD -u EZKEY_POSTGRES_CONTAINER \
    PATH="${BIN}:${PATH}" \
    "${env_kv[@]}" \
    bash "${SCRIPT}" "$@" >"${out}" 2>"${err}"
  local rc=$?
  set -e
  return "${rc}"
}

assert_stdout_has() {
  local file="$1"
  local needle="$2"
  if ! grep -F -q -- "${needle}" "${file}"; then
    echo "---- missing [${needle}] in ${file} ----" >&2
    cat "${file}" >&2 || true
    fail "expected text not found: ${needle}"
  fi
}

assert_file_lacks() {
  local file="$1"
  local needle="$2"
  if [[ -f "${file}" ]] && grep -F -q -- "${needle}" "${file}"; then
    echo "---- unexpected [${needle}] in ${file} ----" >&2
    cat "${file}" >&2 || true
    fail "forbidden text present: ${needle}"
  fi
}

assert_users_exact() {
  local log="$1"
  local expected="$2"
  local got
  got="$(grep '^USER=' "${log}" | sort -u | tr '\n' ' ' | sed 's/ *$//')"
  if [[ "${got}" != "${expected}" ]]; then
    echo "---- psql users ----" >&2
    grep '^USER=' "${log}" >&2 || true
    fail "psql users [${got}] != [${expected}]"
  fi
}

assert_docker_name_only() {
  local log="$1"
  shift
  local saw=0
  local line
  while IFS= read -r line || [[ -n "${line}" ]]; do
    case "${line}" in
      ARGV\ *)
        saw=1
        if [[ "${line}" != *" -e PGPASSWORD "* && "${line}" != *" -e PGPASSWORD" ]]; then
          fail "docker exec is missing name-only -e PGPASSWORD: ${line}"
        fi
        if [[ "${line}" == *"PGPASSWORD="* ]]; then
          fail "docker argv contains PGPASSWORD=value: ${line}"
        fi
        local secret
        for secret in "$@"; do
          if [[ "${line}" == *"${secret}"* ]]; then
            fail "credential appeared on docker argv"
          fi
        done
        ;;
      ENV_PGPASSWORD=\'\'|ENV_PGPASSWORD=)
        fail "docker exec ran without PGPASSWORD in the environment"
        ;;
    esac
  done <"${log}"
  if [[ "${saw}" -ne 1 ]]; then
    fail "docker was not invoked"
  fi
}

new_case() {
  local name="$1"
  CASE_OUT="${TMP}/${name}.out"
  CASE_ERR="${TMP}/${name}.err"
  CASE_PSQL="${TMP}/${name}.psql"
  CASE_DOCKER="${TMP}/${name}.docker"
  : >"${CASE_PSQL}"
  : >"${CASE_DOCKER}"
}

OWNER_PW="owner-sentinel-9f3a"
MIGRATE_PW="migrate-sentinel-9f3a"
ADMIN_PW="admin-sentinel-9f3a"
AUTH_PW="auth-sentinel-9f3a"
INTEGRATION_PW="integration-sentinel-9f3a"
CRYPTO_PW="sek rit=with&meta;"

echo "== catalog mode prefers PGUSER/PGPASSWORD over POSTGRES_* =="
new_case catalog-pg-precedence
if ! run_verify "${CASE_OUT}" "${CASE_ERR}" \
  FAKE_PSQL_LOG="${CASE_PSQL}" \
  FAKE_DOCKER_LOG="${CASE_DOCKER}" \
  PGUSER="pg-owner" \
  PGPASSWORD="${OWNER_PW}" \
  POSTGRES_USER="compose-owner" \
  POSTGRES_PASSWORD="compose-sentinel-should-not-be-used"; then
  cat "${CASE_ERR}" >&2 || true
  fail "catalog mode with PGUSER/PGPASSWORD should pass"
fi
assert_stdout_has "${CASE_OUT}" "Mode: catalog"
assert_stdout_has "${CASE_OUT}" "All grant checks passed."
assert_users_exact "${CASE_PSQL}" "USER=pg-owner"
assert_file_lacks "${CASE_PSQL}" "USER=compose-owner"
assert_file_lacks "${CASE_PSQL}" "PW=compose-sentinel-should-not-be-used"
assert_file_lacks "${CASE_OUT}" "${OWNER_PW}"
assert_file_lacks "${CASE_ERR}" "${OWNER_PW}"
if [[ -s "${CASE_DOCKER}" ]]; then
  fail "host mode must not invoke docker"
fi

echo "== catalog mode falls back to POSTGRES_USER/POSTGRES_PASSWORD =="
new_case catalog-compose-fallback
if ! run_verify "${CASE_OUT}" "${CASE_ERR}" \
  FAKE_PSQL_LOG="${CASE_PSQL}" \
  FAKE_DOCKER_LOG="${CASE_DOCKER}" \
  POSTGRES_USER="compose-owner" \
  POSTGRES_PASSWORD="compose-sentinel-9f3a"; then
  cat "${CASE_ERR}" >&2 || true
  fail "POSTGRES_* fallback should pass catalog mode"
fi
assert_stdout_has "${CASE_OUT}" "Mode: catalog"
assert_users_exact "${CASE_PSQL}" "USER=compose-owner"
if ! grep -F -q -- "PW=compose-sentinel-9f3a" "${CASE_PSQL}"; then
  fail "admin/owner password was not passed via the environment"
fi
assert_file_lacks "${CASE_OUT}" "compose-sentinel-9f3a"
assert_file_lacks "${CASE_ERR}" "compose-sentinel-9f3a"

echo "== a partial or empty role password stays in catalog mode =="
new_case catalog-partial-passwords
if ! run_verify "${CASE_OUT}" "${CASE_ERR}" \
  FAKE_PSQL_LOG="${CASE_PSQL}" \
  FAKE_DOCKER_LOG="${CASE_DOCKER}" \
  PGPASSWORD="${OWNER_PW}" \
  EZKEY_DB_MIGRATE_PASSWORD="${MIGRATE_PW}" \
  EZKEY_DB_ADMIN_PASSWORD="${ADMIN_PW}" \
  EZKEY_DB_AUTH_PASSWORD="${AUTH_PW}" \
  EZKEY_DB_INTEGRATION_PASSWORD="${INTEGRATION_PW}" \
  EZKEY_DB_CRYPTO_PASSWORD=""; then
  cat "${CASE_ERR}" >&2 || true
  fail "empty crypto password should stay in catalog mode"
fi
assert_stdout_has "${CASE_OUT}" "Mode: catalog"
assert_file_lacks "${CASE_OUT}" "Mode: role-login"
assert_users_exact "${CASE_PSQL}" "USER=postgres"
assert_file_lacks "${CASE_PSQL}" "${AUTH_PW}"
assert_file_lacks "${CASE_PSQL}" "${CRYPTO_PW}"
assert_file_lacks "${CASE_OUT}" "${AUTH_PW}"

echo "== absent role passwords do not log in as application roles with the lab default =="
new_case catalog-no-role-default
if ! run_verify "${CASE_OUT}" "${CASE_ERR}" \
  FAKE_PSQL_LOG="${CASE_PSQL}" \
  FAKE_DOCKER_LOG="${CASE_DOCKER}"; then
  cat "${CASE_ERR}" >&2 || true
  fail "default admin/owner catalog mode should pass"
fi
assert_stdout_has "${CASE_OUT}" "Mode: catalog"
assert_users_exact "${CASE_PSQL}" "USER=postgres"
if ! grep -F -q -- "PW=ezkey" "${CASE_PSQL}"; then
  fail "unset admin password should keep the documented lab default for the owner session only"
fi
assert_file_lacks "${CASE_PSQL}" "USER=ezkey_auth"
assert_file_lacks "${CASE_PSQL}" "USER=ezkey_crypto"

echo "== padded catalog cells still match after whitespace trim =="
new_case catalog-padded
if ! run_verify "${CASE_OUT}" "${CASE_ERR}" \
  FAKE_PSQL_LOG="${CASE_PSQL}" \
  FAKE_DOCKER_LOG="${CASE_DOCKER}" \
  PGPASSWORD="${OWNER_PW}" \
  FAKE_PRIV_PAD="1"; then
  cat "${CASE_ERR}" >&2 || true
  fail "whitespace-padded catalog cells should still pass"
fi
assert_stdout_has "${CASE_OUT}" "All grant checks passed."

echo "== crypto table grants that are not exactly keyset SELECT fail closed =="
new_case crypto-grants-fail
if run_verify "${CASE_OUT}" "${CASE_ERR}" \
  FAKE_PSQL_LOG="${CASE_PSQL}" \
  FAKE_DOCKER_LOG="${CASE_DOCKER}" \
  PGPASSWORD="${OWNER_PW}" \
  FAKE_CRYPTO_TABLE_GRANTS="ezkey_keyset_blob:INSERT,ezkey_keyset_blob:SELECT"; then
  fail "extra crypto table grant should fail the smoke check"
fi
assert_stdout_has "${CASE_ERR}" "ezkey_crypto table grants must be exactly ezkey_keyset_blob:SELECT"

echo "== elevated ezkey_crypto role attributes fail closed =="
new_case crypto-attrs-fail
if run_verify "${CASE_OUT}" "${CASE_ERR}" \
  FAKE_PSQL_LOG="${CASE_PSQL}" \
  FAKE_DOCKER_LOG="${CASE_DOCKER}" \
  PGPASSWORD="${OWNER_PW}" \
  FAKE_CRYPTO_DANGEROUS="t"; then
  fail "elevated crypto role attributes should fail the smoke check"
fi
assert_stdout_has "${CASE_ERR}" "ezkey_crypto must not have superuser/createrole/createdb/bypassrls/replication"

echo "== role-login uses each role password and does not print it =="
new_case role-login-host
if ! run_verify "${CASE_OUT}" "${CASE_ERR}" \
  FAKE_PSQL_LOG="${CASE_PSQL}" \
  FAKE_DOCKER_LOG="${CASE_DOCKER}" \
  PGPASSWORD="${OWNER_PW}" \
  EZKEY_DB_MIGRATE_PASSWORD="${MIGRATE_PW}" \
  EZKEY_DB_ADMIN_PASSWORD="${ADMIN_PW}" \
  EZKEY_DB_AUTH_PASSWORD="${AUTH_PW}" \
  EZKEY_DB_INTEGRATION_PASSWORD="${INTEGRATION_PW}" \
  EZKEY_DB_CRYPTO_PASSWORD="${CRYPTO_PW}"; then
  cat "${CASE_ERR}" >&2 || true
  fail "role-login mode should pass"
fi
assert_stdout_has "${CASE_OUT}" "Mode: role-login"
assert_stdout_has "${CASE_OUT}" "All grant checks passed."
for role in ezkey_migrate ezkey_admin ezkey_auth ezkey_integration ezkey_crypto; do
  if ! grep -F -q -- "USER=${role}" "${CASE_PSQL}"; then
    fail "role-login did not connect as ${role}"
  fi
done
for secret in "${OWNER_PW}" "${MIGRATE_PW}" "${ADMIN_PW}" "${AUTH_PW}" "${INTEGRATION_PW}" "${CRYPTO_PW}"; do
  if ! grep -F -q -- "PW=${secret}" "${CASE_PSQL}"; then
    fail "a role password never reached psql via the environment"
  fi
  assert_file_lacks "${CASE_OUT}" "${secret}"
  assert_file_lacks "${CASE_ERR}" "${secret}"
done
if [[ -s "${CASE_DOCKER}" ]]; then
  fail "host role-login must not invoke docker"
fi

echo "== docker exec forwards PGPASSWORD by name and keeps the secret off argv =="
new_case role-login-docker
if ! run_verify "${CASE_OUT}" "${CASE_ERR}" \
  FAKE_PSQL_LOG="${CASE_PSQL}" \
  FAKE_DOCKER_LOG="${CASE_DOCKER}" \
  PGPASSWORD="${OWNER_PW}" \
  EZKEY_POSTGRES_CONTAINER="ezkey-exp-postgres" \
  EZKEY_DB_MIGRATE_PASSWORD="${MIGRATE_PW}" \
  EZKEY_DB_ADMIN_PASSWORD="${ADMIN_PW}" \
  EZKEY_DB_AUTH_PASSWORD="${AUTH_PW}" \
  EZKEY_DB_INTEGRATION_PASSWORD="${INTEGRATION_PW}" \
  EZKEY_DB_CRYPTO_PASSWORD="${CRYPTO_PW}" \
  --docker; then
  cat "${CASE_ERR}" >&2 || true
  cat "${CASE_DOCKER}" >&2 || true
  fail "docker role-login mode should pass"
fi
assert_stdout_has "${CASE_OUT}" "Mode: role-login"
assert_docker_name_only "${CASE_DOCKER}" \
  "${OWNER_PW}" "${MIGRATE_PW}" "${ADMIN_PW}" "${AUTH_PW}" "${INTEGRATION_PW}" "${CRYPTO_PW}"
if ! grep -F -q -- "ezkey-exp-postgres" "${CASE_DOCKER}"; then
  fail "EZKEY_POSTGRES_CONTAINER was not passed to docker exec"
fi
assert_file_lacks "${CASE_DOCKER}" " ezkey-postgres "
for secret in "${OWNER_PW}" "${MIGRATE_PW}" "${ADMIN_PW}" "${AUTH_PW}" "${INTEGRATION_PW}" "${CRYPTO_PW}"; do
  assert_file_lacks "${CASE_OUT}" "${secret}"
  assert_file_lacks "${CASE_ERR}" "${secret}"
done

echo "verify-grants credential and catalog locks passed."
