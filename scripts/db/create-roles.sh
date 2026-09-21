#!/usr/bin/env bash
# Create Ezkey PostgreSQL LOGIN roles against an existing database.
# Use for local IDE Postgres or when docker-entrypoint-initdb.d did not run.
#
# Usage (from repo root):
#   ./scripts/db/create-roles.sh
#
# Env (optional):
#   PGHOST PGPORT PGDATABASE PGUSER PGPASSWORD
#   EZKEY_DB_MIGRATE_PASSWORD EZKEY_DB_ADMIN_PASSWORD
#   EZKEY_DB_AUTH_PASSWORD EZKEY_DB_INTEGRATION_PASSWORD

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

export PGHOST="${PGHOST:-localhost}"
export PGPORT="${PGPORT:-5432}"
export PGDATABASE="${PGDATABASE:-ezkey_db}"
export PGUSER="${PGUSER:-postgres}"
export PGPASSWORD="${PGPASSWORD:-ezkey}"

MIGRATE_PASS="${EZKEY_DB_MIGRATE_PASSWORD:-ezkey_migrate}"
ADMIN_PASS="${EZKEY_DB_ADMIN_PASSWORD:-ezkey_admin}"
AUTH_PASS="${EZKEY_DB_AUTH_PASSWORD:-ezkey_auth}"
INTEGRATION_PASS="${EZKEY_DB_INTEGRATION_PASSWORD:-ezkey_integration}"

echo "Creating Ezkey roles on ${PGHOST}:${PGPORT}/${PGDATABASE} as ${PGUSER}..."

psql -v ON_ERROR_STOP=1 <<EOSQL
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'ezkey_migrate') THEN
    EXECUTE format('CREATE ROLE ezkey_migrate LOGIN PASSWORD %L', '${MIGRATE_PASS}');
  ELSE
    EXECUTE format('ALTER ROLE ezkey_migrate PASSWORD %L', '${MIGRATE_PASS}');
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'ezkey_admin') THEN
    EXECUTE format('CREATE ROLE ezkey_admin LOGIN PASSWORD %L', '${ADMIN_PASS}');
  ELSE
    EXECUTE format('ALTER ROLE ezkey_admin PASSWORD %L', '${ADMIN_PASS}');
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'ezkey_auth') THEN
    EXECUTE format('CREATE ROLE ezkey_auth LOGIN PASSWORD %L', '${AUTH_PASS}');
  ELSE
    EXECUTE format('ALTER ROLE ezkey_auth PASSWORD %L', '${AUTH_PASS}');
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'ezkey_integration') THEN
    EXECUTE format('CREATE ROLE ezkey_integration LOGIN PASSWORD %L', '${INTEGRATION_PASS}');
  ELSE
    EXECUTE format('ALTER ROLE ezkey_integration PASSWORD %L', '${INTEGRATION_PASS}');
  END IF;
END
\$\$;

GRANT CONNECT ON DATABASE ${PGDATABASE} TO ezkey_migrate, ezkey_admin, ezkey_auth, ezkey_integration;
GRANT ALL ON SCHEMA public TO ezkey_migrate;
GRANT USAGE ON SCHEMA public TO ezkey_admin, ezkey_auth, ezkey_integration;
ALTER SCHEMA public OWNER TO ezkey_migrate;
EOSQL

echo "Roles created. Next: run Flyway as ezkey_migrate, then ${REPO_ROOT}/scripts/db/apply-grants.sh"
