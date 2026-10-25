#!/bin/sh
# Apply Ezkey DML grants (idempotent). Run after Flyway migrate.
#
# Usage:
#   ./scripts/db/apply-grants.sh
#   ./scripts/db/apply-grants.sh --docker
#   ./scripts/db/apply-grants.sh --in-container   # inside Compose db-grants (PGHOST=postgres)
#
# Env:
#   PGHOST PGPORT PGDATABASE PGUSER PGPASSWORD (superuser; default postgres/ezkey for local)
#   EZKEY_DB_CRYPTO_PASSWORD — REQUIRED, no default (Patrick B1 / #961). Used only when
#     creating a missing ezkey_crypto role (existing volume from main before crypto-api).
#
# Portable /bin/sh (Alpine postgres image has no bash).

set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"
SQL_FILE="${SCRIPT_DIR}/apply-grants.sql"
ENSURE_CRYPTO_SQL="${SCRIPT_DIR}/ensure-crypto-role.sql"

MODE="host"
if [ "${1:-}" = "--docker" ]; then
  MODE="docker"
elif [ "${1:-}" = "--in-container" ]; then
  MODE="in-container"
  PGHOST="${PGHOST:-postgres}"
  export PGHOST
fi

if [ -z "${EZKEY_DB_CRYPTO_PASSWORD:-}" ]; then
  echo "error: EZKEY_DB_CRYPTO_PASSWORD must be set (no default on the grants/upgrade path)." >&2
  echo "  Export it from your compose .env / secrets before apply-grants / db-grants." >&2
  exit 1
fi

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGDATABASE="${PGDATABASE:-ezkey_db}"
PGUSER="${PGUSER:-postgres}"
PGPASSWORD="${PGPASSWORD:-ezkey}"
export PGHOST PGPORT PGDATABASE PGUSER PGPASSWORD

if [ ! -f "${SQL_FILE}" ] || [ ! -f "${ENSURE_CRYPTO_SQL}" ]; then
  echo "Missing ${SQL_FILE} or ${ENSURE_CRYPTO_SQL}" >&2
  exit 1
fi

# Escape single quotes for safe embedding in a SQL string literal.
sql_quote() {
  printf "%s" "$1" | sed "s/'/''/g"
}

CRYPTO_SQL_LITERAL="$(sql_quote "${EZKEY_DB_CRYPTO_PASSWORD}")"

run_ensure_and_grants() {
  # PERFORM (not SELECT): set_config's return value must never hit stdout (password).
  {
    echo "DO \$ezkey_set_crypto\$ BEGIN"
    echo "  PERFORM set_config('ezkey.crypto_password', '${CRYPTO_SQL_LITERAL}', false);"
    echo "END \$ezkey_set_crypto\$;"
    cat "${ENSURE_CRYPTO_SQL}"
    cat "${SQL_FILE}"
  } | "$@" -v ON_ERROR_STOP=1 -f -
}

if [ "${MODE}" = "docker" ]; then
  CONTAINER="${EZKEY_POSTGRES_CONTAINER:-ezkey-postgres}"
  echo "Applying grants via docker exec ${CONTAINER}..."
  run_ensure_and_grants \
    docker exec -i -e PGPASSWORD="${PGPASSWORD}" "${CONTAINER}" \
    psql -U postgres -d ezkey_db
  echo "Grants applied (docker)."
  exit 0
fi

echo "Applying grants to ${PGHOST}:${PGPORT}/${PGDATABASE} as ${PGUSER}..."
run_ensure_and_grants psql
echo "Grants applied (ezkey_crypto ensured when missing)."
