# Database role scripts

Canonical matrix: [`docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md`](../../docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md).

| Script | Purpose |
|--------|---------|
| [`create-roles.sh`](create-roles.sh) | Bootstrap/rotate LOGIN roles on an existing Postgres; requires `postgres` or equivalent role-management authority |
| [`apply-grants.sql`](apply-grants.sql) | Idempotent runtime DML grants and default privileges; also used by Compose `db-grants` |
| [`apply-grants.sh`](apply-grants.sh) | Superuser wrapper for the grant policy; `--docker` uses `docker exec` |
| [`verify-grants.sh`](verify-grants.sh) | Connect as each application role and smoke-check allowed/denied privileges; `--docker` supported |

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
./scripts/db/verify-grants.sh
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

Do not use runtime API credentials for Flyway. Do not use `ezkey_migrate` as an API datasource.
