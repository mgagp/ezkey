#!/bin/sh
# Creates Ezkey PostgreSQL LOGIN roles on first database init (empty volume).
# Invoked by the official postgres image via /docker-entrypoint-initdb.d/.
# Passwords come from container env (see docker-compose.yml / docker/.env.example).
#
# Roles: ezkey_migrate (DDL/Flyway), ezkey_admin, ezkey_auth, ezkey_integration.
# DML grants are applied later by scripts/db/apply-grants.sh after Flyway.
# Use /bin/sh (Alpine postgres image has no bash).
# Must be executable (git mode 100755). Docker Desktop on macOS can report -x on a
# 644 bind-mount, then the official postgres image execs the script and fails with
# /bin/sh: bad interpreter: Permission denied — roles never exist, APIs stay Created.

set -eu

MIGRATE_PASS="${EZKEY_DB_MIGRATE_PASSWORD:-ezkey_migrate}"
ADMIN_PASS="${EZKEY_DB_ADMIN_PASSWORD:-ezkey_admin}"
AUTH_PASS="${EZKEY_DB_AUTH_PASSWORD:-ezkey_auth}"
INTEGRATION_PASS="${EZKEY_DB_INTEGRATION_PASSWORD:-ezkey_integration}"

echo "Creating Ezkey database roles (migrate, admin, auth, integration)..."

# Passwords are passed via shell expansion into format(%L) — operator-controlled env vars only.
psql -v ON_ERROR_STOP=1 \
  --username "${POSTGRES_USER}" \
  --dbname "${POSTGRES_DB}" \
  <<EOSQL
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

GRANT CONNECT ON DATABASE ${POSTGRES_DB} TO ezkey_migrate, ezkey_admin, ezkey_auth, ezkey_integration;
GRANT ALL ON SCHEMA public TO ezkey_migrate;
GRANT USAGE ON SCHEMA public TO ezkey_admin, ezkey_auth, ezkey_integration;
ALTER SCHEMA public OWNER TO ezkey_migrate;
EOSQL

echo "Ezkey database roles ready."
