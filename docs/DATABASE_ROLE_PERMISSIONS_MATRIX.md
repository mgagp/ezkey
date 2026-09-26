# PostgreSQL Role and Table Permissions Matrix

## Metadata

- **Document ID:** `database-role-permissions-matrix`
- **Status:** `active`
- **Created at:** `2026-07-16`
- **Last reviewed at:** `2026-09-25`
- **Related backlog:** [`I-2026-0021`](../product-docs/global/backlog/ideas/I-2026-0021-postgresql-application-role-permissions-matrix.md)
- **Related TB:** [`TB-2026-07-16`](../product-docs/global/backlog/TB-2026-07-16-postgresql-application-role-split.md)
- **Companion docs:** [`DATABASE_PARTITIONING_SECURITY_ANALYSIS.md`](DATABASE_PARTITIONING_SECURITY_ANALYSIS.md), [`OPERATIONAL.md`](OPERATIONAL.md)

## Purpose

Normative matrix of PostgreSQL roles and per-table DML for Ezkey backends. This is the data-layer counterpart to lifecycle governance: least privilege at the database boundary, especially for audit immutability.

**Defense in depth:** grants complement integrity detection (`I-2026-0005`); they do not stop a DBA or `postgres` superuser.

## Role inventory

| Role | LOGIN | Purpose | Used by |
|------|-------|---------|---------|
| `postgres` | yes | Bootstrap superuser; emergency DBA; functional-test DB helpers | Docker image init only (not runtime APIs) |
| `ezkey_migrate` | yes | DDL owner — Flyway / schema evolution | `ezkey-migration` |
| `ezkey_admin` | yes | Broadest runtime DML + all schedulers | `admin-api` |
| `ezkey_auth` | yes | Device-facing Auth API DML | `auth-api` |
| `ezkey_integration` | yes | M2M Integration API DML | `integration-api` |

Crypto API has **no** datasource and **no** role.

### Provisioning (not Flyway)

| Concern | Mechanism |
|---------|-----------|
| Role creation + passwords | [`docker/postgres/init/01-create-roles.sh`](../docker/postgres/init/01-create-roles.sh) (empty volume) or [`scripts/db/create-roles.sh`](../scripts/db/create-roles.sh) |
| DDL | Flyway as `ezkey_migrate` |
| DML grants | [`scripts/db/apply-grants.sql`](../scripts/db/apply-grants.sql) via [`scripts/db/apply-grants.sh`](../scripts/db/apply-grants.sh) after migrate |
| Future objects | `ALTER DEFAULT PRIVILEGES FOR ROLE ezkey_migrate` in apply-grants |

## Decision: three runtime roles (R1)

| Criterion | Shared `ezkey_app` (auth+integration) | Separate `ezkey_auth` + `ezkey_integration` |
|-----------|----------------------------------------|-----------------------------------------------|
| Blast radius | Integration leak includes enrollment UPDATE | Integration cannot UPDATE enrollments |
| Ops cost | One shared secret | Three secrets (acceptable greenfield) |
| Matrix fit | Collapses distinct DML needs | Matches audit below |

**Decision:** use **three runtime roles** for R1. Differences are stable and small (enrollment UPDATE vs api_key UPDATE; auth does not INSERT auth attempts).

## Backend write-path audit (2026-07-16)

### Schedulers (`@SchedulerLock`) — admin-api only

`ADMIN_TOKEN_CLEANUP`, `DB_PARTITION_CREATION`, `AUDIT_CHAIN_CHECKPOINT`, `AUDIT_LIFECYCLE_PURGE`, `NIGHTLY_INTEGRITY_VALIDATION`, `ENROLLMENT_EXPIRED_CLEANUP`, `AUTH_ATTEMPT_EXPIRY`, `KEY_ROTATION` / `KEY_PROMOTION`, `REENCRYPTION`, programmatic `ADMIN_STARTUP_BOOTSTRAP`.

Auth-api and integration-api have **no** ShedLock / `@EnableScheduling`.

### Shared alert / incident path

All three APIs may **INSERT/UPDATE** `ezkey_alert` and `ezkey_audit_chain_incident` via `AuditChainHeartbeatGuardService.evaluate()` (peripheral heartbeat), not via schedulers.

### Dropped tables (no grants)

- `ezkey_integration_i18n` — dropped in V7
- `ezkey_admin_temp_tokens` — dropped in V1 (passwordless)

## Permissions matrix

Legend: **S**=SELECT · **I**=INSERT · **U**=UPDATE · **D**=DELETE · **X**=EXECUTE · **—**=no grant

| Table / object | `ezkey_migrate` | `ezkey_admin` | `ezkey_auth` | `ezkey_integration` | Notes |
|----------------|-----------------|---------------|--------------|---------------------|-------|
| DDL (CREATE/ALTER/DROP) | full | — | — | — | Flyway only |
| `flyway_schema_history` | full | — | — | — | Migration module only |
| `ezkey_tenant` | owner | S I U | S | S | Writes: admin provisioning / bootstrap |
| `ezkey_admin` | owner | S I U | S | S | Auth/integration: join / eligibility reads |
| `ezkey_admin_tokens` | owner | S I U D | — | — | Cleanup DELETE in admin-api |
| `ezkey_integration` | owner | S I U D | S | S | |
| `ezkey_enrollment` | owner | S I U D | S U | S | Auth: bind/verify/`lastUsedAt`; integration: read |
| `ezkey_auth_attempt` | owner | S I U | S U | S I U | Auth does not create attempts |
| `ezkey_api_key` | owner | S I U D | — | S U | Integration: `lastUsedAt` on validate |
| `ezkey_audit_log` | owner | S I U D | S I | S I | **DELETE admin only** (lifecycle purge). Peripherals are **SELECT + INSERT** only (single-INSERT HMAC seal via named sequence `ezkey_audit_log_id_seq`; no UPDATE). `ezkey_admin` retains UPDATE/DELETE. |
| `ezkey_encryption_key` | owner | S I U | S | S | Writers: key rotation (admin) |
| `ezkey_reencryption_batch` | owner | S I U | — | — | Admin schedulers only |
| `ezkey_keyset_blob` | owner | S I U | S | S | Writers: Admin keyset materialization only (ADR-0012). Peripherals SELECT + reload. |
| `ezkey_audit_chain_checkpoint` | owner | S I U D | S | S | Writers: admin chain schedulers |
| `ezkey_audit_chain_incident` | owner | S I U | S I U | S I U | Heartbeat on all three APIs |
| `ezkey_audit_entry_integrity_conciliation` | owner | S I U | — | — | Admin reconcile only |
| `ezkey_alert` | owner | S I U | S I U | S I U | Resolve = UPDATE; no hard DELETE (intentional). Future retention/purge of aged `RESOLVED` rows: [`I-2026-07-17-alert-resolved-retention-purge`](../product-docs/global/backlog/ideas/I-2026-07-17-alert-resolved-retention-purge.md) |
| `ezkey_scheduled_job_last_run` | owner | S U | — | — | Seeded by migration; admin updates |
| `ezkey_integrity_async_job` | owner | S I U | — | — | Admin-only Integrity async operator slot. Auth/Integration must not SELECT (no runtime path; Hibernate `ddl-auto=none` + Admin-only `org.ezkey.audit.asyncjob` EntityScan). |
| `ezkey_shedlock` | owner | S I U | — | — | Admin ShedLock only |
| `create_monthly_partition(...)` | owner | X | — | — | SECURITY DEFINER; admin `PartitionSchedulerService` |

### Sequences / identity

All three runtime roles receive `USAGE, SELECT` on sequences in `public` so identity/sequence-backed inserts succeed where INSERT is granted. `ezkey_audit_log.audit_log_id` uses an explicit named sequence (`ezkey_audit_log_id_seq`) with `DEFAULT nextval(...)`; the application pre-allocates via `nextval` for the single-INSERT HMAC seal (assigned id + Persistable INSERT).

### Partitioned tables

Grants on parents `ezkey_audit_log` and `ezkey_auth_attempt` apply to partitions. New partitions created via `create_monthly_partition` are owned by `ezkey_migrate` (SECURITY DEFINER); default privileges keep app grants current.

## Explicit exceptions

1. **Audit immutability:** only `ezkey_admin` may DELETE `ezkey_audit_log` (lifecycle purge). Auth/integration must not DELETE or UPDATE — they hold **SELECT + INSERT** only. The HMAC seal is a **single INSERT** (`AuditLogService.log()`): named sequence pre-allocates `audit_log_id`, the application owns micros-truncated `created_at`, and `entry_hmac` is written on that INSERT inside `REQUIRES_NEW`. Delivered by [`TB-2026-07-18`](../product-docs/global/backlog/TB-2026-07-18-audit-log-insert-only-hmac-seal.md) / [`I-2026-07-18`](../product-docs/global/backlog/ideas/I-2026-07-18-audit-log-peripheral-insert-only-hmac-sequence.md); source signal in [`TB-2026-07-16`](../product-docs/global/backlog/TB-2026-07-16-postgresql-application-role-split.md) § Follow-up analysis.
2. **Integrity chain tables:** checkpoints/conciliation are admin-write; incidents are writable by peripherals for heartbeat sync.
3. **Partition DDL:** app roles never get CREATE TABLE; only EXECUTE on the whitelist function.
4. **RLS / column grants:** out of scope (see `I-2026-0021`).
5. **Functional tests:** `DatabaseHelper` and similar may keep using `postgres` for opportunistic DB inspection — not a runtime path.

## 2-role vs 3-role (recorded)

| Option | Recommendation |
|--------|----------------|
| `ezkey_admin` + shared `ezkey_app` | Rejected for R1 — collapses enrollment UPDATE into integration credential |
| `ezkey_admin` + `ezkey_auth` + `ezkey_integration` | **Accepted** |

## Operational notes

- **Clean-start / EXP1:** recreate `postgres-data` volume so init roles run; then migrate → apply-grants → APIs.
- **Local IDE:** run `scripts/db/create-roles.sh` then migrate as `ezkey_migrate` then `scripts/db/apply-grants.sh`.
- **Passwords:** `EZKEY_DB_MIGRATE_PASSWORD`, `EZKEY_DB_ADMIN_PASSWORD`, `EZKEY_DB_AUTH_PASSWORD`, `EZKEY_DB_INTEGRATION_PASSWORD` (see `docker/.env.example`).
- **Monitoring / Grafana:** may continue to use `postgres` or a future read-only role (not in this matrix).
- **Backup:** prefer `postgres` or `ezkey_migrate` for `pg_dump`.
- **Docs debt cleared:** [`OPERATIONAL.md`](OPERATIONAL.md) § Database Security must match this matrix (not a single full-CRUD app user).

## Verification

- [`scripts/db/verify-grants.sh`](../scripts/db/verify-grants.sh) — connect as each role; assert forbidden ops (e.g. auth DELETE on `ezkey_audit_log`; auth/integration must **not** SELECT `ezkey_integrity_async_job`).
- `clean-start` + functional suite after role wiring.

## Hibernate DDL mode (runtime APIs)

Admin, Auth, and Integration runtime profiles use `spring.jpa.hibernate.ddl-auto=none`. Schema is owned by Flyway (`ezkey_migrate`). Do **not** widen DML grants solely so Hibernate `validate` can see Admin-only tables.
