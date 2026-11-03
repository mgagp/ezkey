#!/bin/sh
# Patrick B1 / #961 — upgrade path on a pre-PR Postgres volume (no ezkey_crypto).
#
# Spins an ephemeral Postgres 18 container, creates only the legacy four roles
# (simulating a volume initialized from main before ADR-0016), then proves:
#   1) apply-grants.sql alone fails (role missing)
#   2) apply-grants.sh (ensure + grants) succeeds with EZKEY_DB_CRYPTO_PASSWORD
#   3) ezkey_crypto can SELECT ezkey_keyset_blob after a minimal table create
#
# Usage (repo root, Docker required):
#   ./scripts/db/test-upgrade-crypto-role.sh
#
# No secrets are printed. Exit 0 on success.

set -eu

ROOT="$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)"
CONTAINER="ezkey-b1-upgrade-$$"
IMAGE="postgres:18-alpine"
CRYPTO_PASS="b1-upgrade-test-crypto-pass"
PGPASSWORD_SUPER=ezkey

cleanup() {
  docker rm -f "${CONTAINER}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "B1 upgrade test: starting ephemeral Postgres (${CONTAINER})..."
docker run -d --name "${CONTAINER}" \
  -e POSTGRES_PASSWORD="${PGPASSWORD_SUPER}" \
  -e POSTGRES_DB=ezkey_db \
  "${IMAGE}" >/dev/null

# Wait until ready
i=0
while [ "$i" -lt 60 ]; do
  if docker exec -e PGPASSWORD="${PGPASSWORD_SUPER}" "${CONTAINER}" \
    pg_isready -U postgres -d ezkey_db >/dev/null 2>&1; then
    break
  fi
  i=$((i + 1))
  sleep 0.5
done
docker exec -e PGPASSWORD="${PGPASSWORD_SUPER}" "${CONTAINER}" \
  pg_isready -U postgres -d ezkey_db >/dev/null

echo "B1: creating pre-PR roles (no ezkey_crypto)..."
docker exec -i -e PGPASSWORD="${PGPASSWORD_SUPER}" "${CONTAINER}" \
  psql -v ON_ERROR_STOP=1 -U postgres -d ezkey_db <<'EOSQL'
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'ezkey_migrate') THEN
    CREATE ROLE ezkey_migrate LOGIN PASSWORD 'ezkey_migrate';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'ezkey_admin') THEN
    CREATE ROLE ezkey_admin LOGIN PASSWORD 'ezkey_admin';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'ezkey_auth') THEN
    CREATE ROLE ezkey_auth LOGIN PASSWORD 'ezkey_auth';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'ezkey_integration') THEN
    CREATE ROLE ezkey_integration LOGIN PASSWORD 'ezkey_integration';
  END IF;
END
$$;
GRANT CONNECT ON DATABASE ezkey_db TO ezkey_migrate, ezkey_admin, ezkey_auth, ezkey_integration;
GRANT ALL ON SCHEMA public TO ezkey_migrate;
ALTER SCHEMA public OWNER TO ezkey_migrate;

-- Stub partition helper referenced by apply-grants.sql.
CREATE OR REPLACE FUNCTION create_monthly_partition(
  TEXT, TEXT, TIMESTAMPTZ, TIMESTAMPTZ
) RETURNS VOID LANGUAGE sql AS $$ SELECT NULL $$;

-- Minimal tables referenced by apply-grants.sql (no full Flyway).
CREATE TABLE ezkey_keyset_blob (
  id INTEGER PRIMARY KEY DEFAULT 1,
  keyset_data BYTEA NOT NULL,
  last_updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(100) DEFAULT 'SYSTEM',
  version BIGINT DEFAULT 1,
  CONSTRAINT single_row CHECK (id = 1)
);
CREATE TABLE ezkey_tenant (id INT PRIMARY KEY);
CREATE TABLE ezkey_admin (id INT PRIMARY KEY);
CREATE TABLE ezkey_encryption_key (key_id BIGINT PRIMARY KEY);
CREATE TABLE ezkey_reencryption_batch (id INT PRIMARY KEY);
CREATE TABLE ezkey_audit_chain_incident (id INT PRIMARY KEY);
CREATE TABLE ezkey_audit_entry_integrity_conciliation (id INT PRIMARY KEY);
CREATE TABLE ezkey_alert (id INT PRIMARY KEY);
CREATE TABLE ezkey_admin_tokens (id INT PRIMARY KEY);
CREATE TABLE ezkey_integration (id INT PRIMARY KEY);
CREATE TABLE ezkey_enrollment (id INT PRIMARY KEY);
CREATE TABLE ezkey_api_key (id INT PRIMARY KEY);
CREATE TABLE ezkey_audit_chain_checkpoint (id INT PRIMARY KEY);
CREATE TABLE ezkey_shedlock (name VARCHAR(64) PRIMARY KEY);
CREATE TABLE ezkey_auth_attempt (id INT PRIMARY KEY);
CREATE TABLE ezkey_audit_log (id BIGINT PRIMARY KEY);
CREATE TABLE ezkey_scheduled_job_last_run (job_name VARCHAR(64) PRIMARY KEY);
CREATE TABLE ezkey_integrity_async_job (job_id UUID PRIMARY KEY);
INSERT INTO ezkey_keyset_blob (id, keyset_data, updated_by, version)
  VALUES (1, '\x00'::bytea, 'B1_TEST', 1);
EOSQL

echo "B1: proving raw apply-grants.sql fails without ezkey_crypto..."
if docker exec -i -e PGPASSWORD="${PGPASSWORD_SUPER}" "${CONTAINER}" \
  psql -v ON_ERROR_STOP=1 -U postgres -d ezkey_db -f - <"${ROOT}/scripts/db/apply-grants.sql" \
  >/tmp/ezkey-b1-raw-grants.log 2>&1; then
  echo "error: expected raw apply-grants.sql to fail when ezkey_crypto is absent" >&2
  cat /tmp/ezkey-b1-raw-grants.log >&2 || true
  exit 1
fi

echo "P-M5: apply-grants.sh must fail when EZKEY_DB_CRYPTO_PASSWORD is absent..."
EZKEY_POSTGRES_CONTAINER="${CONTAINER}"
PGPASSWORD="${PGPASSWORD_SUPER}"
export EZKEY_POSTGRES_CONTAINER PGPASSWORD
unset EZKEY_DB_CRYPTO_PASSWORD || true
if env -u EZKEY_DB_CRYPTO_PASSWORD \
  EZKEY_POSTGRES_CONTAINER="${CONTAINER}" PGPASSWORD="${PGPASSWORD_SUPER}" \
  "${ROOT}/scripts/db/apply-grants.sh" --docker \
  >/tmp/ezkey-b1-absent-crypto-pass.log 2>&1; then
  echo "error: expected apply-grants.sh to fail without EZKEY_DB_CRYPTO_PASSWORD" >&2
  cat /tmp/ezkey-b1-absent-crypto-pass.log >&2 || true
  exit 1
fi
if ! grep -q 'EZKEY_DB_CRYPTO_PASSWORD must be set' /tmp/ezkey-b1-absent-crypto-pass.log; then
  echo "error: absent-password failure must mention EZKEY_DB_CRYPTO_PASSWORD" >&2
  cat /tmp/ezkey-b1-absent-crypto-pass.log >&2 || true
  exit 1
fi
echo "P-M5: absent EZKEY_DB_CRYPTO_PASSWORD correctly fails closed."

echo "B1: apply-grants.sh --docker must succeed with EZKEY_DB_CRYPTO_PASSWORD..."
EZKEY_DB_CRYPTO_PASSWORD="${CRYPTO_PASS}"
export EZKEY_DB_CRYPTO_PASSWORD
"${ROOT}/scripts/db/apply-grants.sh" --docker
echo "B1: verifying ezkey_crypto exists and can SELECT keyset blob..."
docker exec -i -e PGPASSWORD="${CRYPTO_PASS}" "${CONTAINER}" \
  psql -v ON_ERROR_STOP=1 -U ezkey_crypto -d ezkey_db -c \
  'SELECT version FROM ezkey_keyset_blob WHERE id = 1;' >/dev/null

echo "B1 upgrade test PASSED (pre-PR volume → ensure crypto role → grants; P-M5 absent password)."
