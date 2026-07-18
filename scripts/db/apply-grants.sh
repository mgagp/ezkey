#!/usr/bin/env bash
# Apply Ezkey DML grants (idempotent). Run after Flyway migrate.
#
# Usage:
#   ./scripts/db/apply-grants.sh
#   # or against Docker:
#   ./scripts/db/apply-grants.sh --docke
#
# Env: PGHOST PGPORT PGDATABASE PGUSER PGPASSWORD (superuser; default postgres/ezkey)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_FILE="${SCRIPT_DIR}/apply-grants.sql"

export PGHOST="${PGHOST:-localhost}"
export PGPORT="${PGPORT:-5432}"
export PGDATABASE="${PGDATABASE:-ezkey_db}"
export PGUSER="${PGUSER:-postgres}"
export PGPASSWORD="${PGPASSWORD:-ezkey}"

if [[ "${1:-}" == "--docker" ]]; then
  CONTAINER="${EZKEY_POSTGRES_CONTAINER:-ezkey-postgres}"
  echo "Applying grants via docker exec ${CONTAINER}..."
  docker exec -i "${CONTAINER}" \
    psql -v ON_ERROR_STOP=1 -U postgres -d ezkey_db < "${SQL_FILE}"
  echo "Grants applied (docker)."
  exit 0
fi

if [[ ! -f "${SQL_FILE}" ]]; then
  echo "Missing ${SQL_FILE}" >&2
  exit 1
fi

echo "Applying grants to ${PGHOST}:${PGPORT}/${PGDATABASE} as ${PGUSER}..."
psql -v ON_ERROR_STOP=1 -f "${SQL_FILE}"
echo "Grants applied."
