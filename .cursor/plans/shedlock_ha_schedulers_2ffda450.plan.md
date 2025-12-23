---
name: ShedLock HA schedulers
overview: Introduce ShedLock to prevent scheduled job conflicts when multiple Admin API instances run concurrently, using PostgreSQL as the shared lock store.
todos:
  - id: deps
    content: Add ShedLock dependencies to ezkey-core/pom.xml (align version with docs/HA-JOB-COORDINATION.md).
    status: completed
  - id: flyway
    content: Add Flyway migration V26__create_shedlock_table.sql in ezkey-core/db/migration.
    status: completed
  - id: config
    content: Add ShedLock configuration (EnableSchedulerLock + LockProvider usingDbTime) in ezkey-admin-api config package.
    status: completed
  - id: annotate
    content: Add @SchedulerLock to all Admin API scheduled jobs (key rotation, re-encryption, audit cleanup, partition creation, admin token cleanup).
    status: completed
  - id: optional-ops
    content: Optionally add a HealthIndicator / visibility for active locks.
    status: completed
---

# Add distributed locking to scheduled jobs (ShedLock)

## Library to use

- Use **ShedLock** (GitHub: `https://github.com/lukas-krecan/ShedLock`).
- Maven artifacts:
- `net.javacrumbs.shedlock:shedlock-spring`
- `net.javacrumbs.shedlock:shedlock-provider-jdbc-template`

This matches the existing project decision already documented in `docs/HA-JOB-COORDINATION.md`.

## Implementation steps

1. **Add dependencies**

- Add ShedLock dependencies to `ezkey-core/pom.xml` (version aligned with `docs/HA-JOB-COORDINATION.md`, currently `5.16.0`).

2. **Add Flyway migration for the lock table**

- Create a new migration in `ezkey-core/src/main/resources/db/migration/` as **`V26__create_shedlock_table.sql`** (next after `V25__...`).
- Create the `shedlock` table (PostgreSQL `TIMESTAMPTZ` columns) as described in `docs/HA-JOB-COORDINATION.md`.

3. **Configure ShedLock in the Admin API runtime**

- Add a Spring configuration class in the Admin API classpath (recommended location: `ezkey-admin-api/src/main/java/org/ezkey/admin/config/ShedLockConfiguration.java`).
- Provide:
- `@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")`
- a `LockProvider` bean using `JdbcTemplateLockProvider` with `usingDbTime()`.
- Keep scheduling itself controlled by `@EnableScheduling` already present in `ezkey-admin-api/src/main/java/org/ezkey/admin/AdminApplication.java`.

4. **Make startup bootstrap HA-safe (initial global admin + admin MFA bootstrap)**

- Motivation: `InitialGlobalAdminService` and `AdminBootstrapService` both run on `ApplicationReadyEvent`. In HA, without a distributed lock:
- global admin creation is mostly safe (Flyway + `UNIQUE(username)`), but can still throw on races
- **system integration creation is dangerous**: multiple instances can create multiple `is_system_integration=true` rows; repository methods returning `Optional` can fail with “multiple results”.
- Approach: reuse ShedLock’s DB-backed lock **programmatically** (not `@Scheduled`).
- Add a small helper/bean using `LockProvider` (e.g. `LockingTaskExecutor`), and guard both startup listeners with the **same lock name**, e.g. `ADMIN_STARTUP_BOOTSTRAP`.
- If lock cannot be acquired, log and skip (other node will complete bootstrap).
- Target services:
- `ezkey-admin-api/src/main/java/org/ezkey/admin/service/InitialGlobalAdminService.java`
- `ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminBootstrapService.java`

5. **Annotate all Admin API scheduled jobs with `@SchedulerLock`**

- Add `@SchedulerLock(name=..., lockAtMostFor=..., lockAtLeastFor=...)` to each scheduled method so only one instance runs it at a time.
- Target files (current schedulers found in repo):
- `ezkey-core/src/main/java/org/ezkey/security/KeyRotationService.java`
- `checkAndPromotePendingKeys()` → `KEY_PROMOTION` (e.g. `lockAtMostFor=PT1M`, `lockAtLeastFor=PT4S`)
- `checkAndRotate()` → `KEY_ROTATION` (e.g. `lockAtMostFor=PT10M`, `lockAtLeastFor=PT0S`)
- `ezkey-core/src/main/java/org/ezkey/security/ReencryptionService.java`
- `processReencryptionBatches()` → `REENCRYPTION` (set `lockAtMostFor` to exceed `ezkey.encryption.reencryption.max-duration-minutes` default 60 min; e.g. `PT2H`)
- `ezkey-core/src/main/java/org/ezkey/audit/service/AuditLogCleanupScheduler.java`
- `cleanupOldAuditLogs()` → `AUDIT_CLEANUP` (e.g. `PT10M`)
- `ezkey-core/src/main/java/org/ezkey/database/service/PartitionSchedulerService.java`
- `createNextMonthPartitions()` → `DB_PARTITION_CREATION` (e.g. `PT10M`)
- `ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminTokenCleanupService.java`
- `cleanupExpiredTokens()` → `ADMIN_TOKEN_CLEANUP` (e.g. `PT10M`)

5. **(Optional) Add lightweight operational visibility**