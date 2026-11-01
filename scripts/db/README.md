# Database role scripts

Canonical matrix: [`docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md`](../../docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md).

| Script | Purpose |
|--------|---------|
| [`create-roles.sh`](create-roles.sh) | Bootstrap/rotate LOGIN roles on an existing Postgres; requires `postgres` or equivalent role-management authority |
| [`ensure-crypto-role.sql`](ensure-crypto-role.sql) | Idempotent `ezkey_crypto` create for upgrade volumes (invoked by `apply-grants.sh`; password via `set_config`, no default) |
| [`apply-grants.sql`](apply-grants.sql) | Idempotent runtime DML grants and default privileges |
| [`apply-grants.sh`](apply-grants.sh) | Superuser wrapper: ensure crypto role + grants; `--docker` / `--in-container`; **requires** `EZKEY_DB_CRYPTO_PASSWORD` |
| [`test-upgrade-crypto-role.sh`](test-upgrade-crypto-role.sh) | Ephemeral Docker proof: pre-PR volume (no crypto role) → grants succeed (#961 B1) |
| [`verify-grants.sh`](verify-grants.sh) | Smoke-check allowed/denied privileges; `--docker` + `EZKEY_POSTGRES_CONTAINER`; role-login when `EZKEY_DB_*` passwords are set, else catalog fallback as admin/owner (no passwords on argv) |

Docker first-boot role creation: [`docker/postgres/init/01-create-roles.sh`](../../docker/postgres/init/01-create-roles.sh).

## Local lifecycle

```bash
# 1. Provision roles (new database or credential rotation).
./scripts/db/create-roles.sh

# 2. Apply schema changes as ezkey_migrate.
./scripts/ezkey-flyway.sh

# 3. Reconcile the runtime grant matrix after Flyway.
./scripts/db/apply-grants.sh

# 4. Optional boundary smoke test.
# Prefer exporting EZKEY_DB_* role passwords for per-role login smoke.
# If those are unset, the script falls back to catalog checks as PGUSER/PGPASSWORD
# (or POSTGRES_USER/POSTGRES_PASSWORD). Credentials are passed via environment only.
./scripts/db/verify-grants.sh
# Docker / Lightsail:
# EZKEY_POSTGRES_CONTAINER=ezkey-exp-postgres ./scripts/db/verify-grants.sh --docker
```

The Flyway CLI and grant scripts are intentionally separate:

- `ezkey-migration` owns DDL and `flyway_schema_history`;
- `apply-grants` owns runtime authorization policy;
- APIs connect only as `ezkey_admin`, `ezkey_auth`, or `ezkey_integration`.

Run `apply-grants` after a migration that creates or changes database objects. It is unnecessary
after read-only Flyway commands such as `--info` or `--validate`. Docker Compose performs the
migrate-then-grant sequence automatically through the `migration` and `db-grants` one-shot
services.

## Environment

The role bootstrap recognizes:

- `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`
- `EZKEY_DB_MIGRATE_PASSWORD`
- `EZKEY_DB_ADMIN_PASSWORD`
- `EZKEY_DB_AUTH_PASSWORD`
- `EZKEY_DB_INTEGRATION_PASSWORD`
- `EZKEY_DB_CRYPTO_PASSWORD` — **required** by `apply-grants.sh` (no default on the grants/upgrade path)

`verify-grants.sh` also accepts Compose-style `POSTGRES_USER` / `POSTGRES_PASSWORD` as
fallbacks for the admin/owner login, and `EZKEY_POSTGRES_CONTAINER` in `--docker` mode.
Role passwords are optional for verify: when all five `EZKEY_DB_*` passwords are present it
connects as each role; otherwise it verifies via `has_table_privilege` /
`information_schema.role_table_grants` (including `ezkey_keyset_blob`) as the admin/owner.
Passwords are never placed on process argv (`docker exec -e PGPASSWORD` name-only form).

Do not use runtime API credentials for Flyway. Do not use `ezkey_migrate` as an API datasource.

**Note (role creation / PL/pgSQL):** a failing dynamic `CREATE ROLE … PASSWORD` can echo the
SQL (including the password literal) in PostgreSQL `CONTEXT` on stderr or in the server log.
`apply-grants.sh` uses `PERFORM set_config` + `format(%L)` and does not print query results; still
treat DB server logs as sensitive when diagnosing role-create failures.
