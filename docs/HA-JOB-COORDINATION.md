# High Availability Job Coordination Strategy

## Overview

This document outlines the strategy for coordinating scheduled jobs across multiple instances of Admin API in a High Availability (HA) deployment. The goal is to ensure that only one instance executes a given scheduled job at any time, while providing automatic failover if the active instance becomes unavailable.

## Problem Statement

In a distributed deployment with multiple Admin API instances:

- Scheduled jobs (key rotation, re-encryption, cleanup) would execute on ALL instances
- This causes duplicate operations, race conditions, and potential data corruption
- Need a mechanism to prevent concurrent execution of the same job
- Must provide automatic failover if an instance becomes unavailable

## Current Scheduled Jobs Analysis

| Job | Frequency | Criticality | Nature | Daily Operations |
|-----|-----------|-------------|--------|------------------|
| `checkAndPromotePendingKeys()` (`KEY_PROMOTION`) | **5 seconds** | Critical | Short, transactional | ~17,280 |
| `checkAndRotate()` (`KEY_ROTATION`) | Daily 2 AM | Important | Short, transactional | 1 |
| `processReencryptionBatches()` (`REENCRYPTION`) | Daily 3 AM | Important | Batch, transactional | 1 |
| `purgeLifecycleEligibleAuditLogs()` (`AUDIT_LIFECYCLE_PURGE`) | Daily 2 AM | Maintenance | Short, transactional | 1 |
| `createNextMonthPartitions()` (`DB_PARTITION_CREATION`) | Daily | Maintenance | DDL via SECURITY DEFINER | 1 |
| `cleanupExpiredTokens()` (`ADMIN_TOKEN_CLEANUP`) | Scheduled | Maintenance | Short | — |
| Other Admin-scheduled jobs (`AUTH_ATTEMPT_EXPIRY`, `ENROLLMENT_EXPIRED_CLEANUP`, audit chain / integrity, …) | Varies | — | Each has `@SchedulerLock` | — |

**Key Observations:**
- All jobs are **short-lived** (seconds, not minutes)
- All jobs are **transactional** (atomicity via `@Transactional`)
- All jobs are **idempotent** (can be safely retried without side effects)
- Jobs do NOT require **leadership continuity** between executions

---

## Decision: ShedLock (shipped)

After critical analysis of the requirements and ezkey's philosophy, **ShedLock** is the solution
for HA job coordination. It is **implemented** in Admin API (`ShedLockConfiguration`,
`@SchedulerLock` on schedulers, programmatic `ADMIN_STARTUP_BOOTSTRAP` for startup MFA/global
admin). Lock rows live in PostgreSQL table **`ezkey_shedlock`** (created in Flyway
`V5__operations_tenant_integration_enrollment.sql`, not a separate V26). HA exercise:
[`docker/README-HA.md`](../docker/README-HA.md), `ShedLockDistributedTest`.

### Rationale

| Criterion | ShedLock | Custom Leader Election |
|-----------|----------|------------------------|
| **Reliability** | ✅ 8+ years in production, battle-tested | ⚠️ New code, untested |
| **Complexity** | ✅ ~30 lines configuration | ❌ ~600 lines custom code |
| **Time to implement** | ✅ 1-2 hours | ❌ 2-3 days |
| **Maintenance** | ✅ Dependency updates only | ❌ Ongoing code maintenance |
| **SOC2 Auditability** | ✅ Database table + logs | ✅ Database table + logs |
| **Philosophy alignment** | ✅ "Pragmatic, simple" | ⚠️ Over-engineering risk |

### Why "Lock per Execution" is Correct for Ezkey

The custom leader election pattern was designed for **long-running jobs** where one instance must maintain leadership across multiple consecutive executions. **This is not ezkey's use case.**

Ezkey's jobs are:
1. **Short** - Complete in seconds
2. **Transactional** - Database atomicity handles consistency
3. **Idempotent** - Safe to retry on any instance

ShedLock's "lock per execution" model means:
- Instance A executes job, acquires lock
- Instance A completes, releases lock
- Next execution: ANY healthy instance can acquire lock

**This is exactly what ezkey needs** - no instance affinity, maximum availability.

---

## Architecture with ShedLock

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    SHEDLOCK DISTRIBUTED LOCKING                          │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  Admin API 1                Admin API 2                Admin API 3       │
│  ┌──────────┐               ┌──────────┐               ┌──────────┐     │
│  │ Instance │               │ Instance │               │ Instance │     │
│  │   ID: A  │               │   ID: B  │               │   ID: C  │     │
│  │ RUNNING  │               │ WAITING  │               │ WAITING  │     │
│  └────┬─────┘               └────┬─────┘               └────┬─────┘     │
│       │                          │                          │           │
│       │ Acquire lock             │ Lock busy                │           │
│       ▼                          ▼                          ▼           │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                   PostgreSQL: ezkey_shedlock                     │   │
│  │  ┌─────────────────────────────────────────────────────────────┐│   │
│  │  │ name              │ lock_until      │ locked_at     │locked_by│   │
│  │  ├───────────────────┼─────────────────┼───────────────┼────────┤│   │
│  │  │ KEY_PROMOTION     │ 12:00:05        │ 12:00:00      │ A      ││   │
│  │  │ KEY_ROTATION      │ 02:01:00        │ 02:00:00      │ B      ││   │
│  │  │ REENCRYPTION      │ 03:01:00        │ 03:00:00      │ C      ││   │
│  │  │ AUDIT_LIFECYCLE_PURGE │ 02:01:00    │ 02:00:00      │ A      ││   │
│  │  └─────────────────────────────────────────────────────────────┘│   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Implementation Plan

### Phase 1: Dependencies (pom.xml)

Add to `ezkey-core/pom.xml`:

```xml
<!-- ShedLock for distributed job locking -->
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-spring</artifactId>
    <version>5.16.0</version>
</dependency>
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-jdbc-template</artifactId>
    <version>5.16.0</version>
</dependency>
```

### Phase 2: Database Migration

Create `V_XX__create_shedlock_table.sql`:

```sql
-- ============================================================================
-- ShedLock Table for Distributed Job Coordination
-- ============================================================================
-- Used by ShedLock library to prevent concurrent execution of scheduled jobs
-- across multiple Admin API instances in HA deployments.
--
-- Locking Model:
-- - Each job acquires a lock before execution
-- - Lock is held for duration of job (plus safety margin)
-- - If instance crashes, lock auto-expires after lock_until
-- - Any instance can acquire lock for next execution
--
-- SOC2 Auditability:
-- - locked_by: identifies which instance executed the job
-- - locked_at: timestamp of lock acquisition
-- - Query this table to audit job execution history
-- ============================================================================

CREATE TABLE ezkey_shedlock (
    -- Unique job identifier (matches @SchedulerLock name)
    name VARCHAR(64) NOT NULL PRIMARY KEY,
    
    -- When the lock expires (allows failover if instance crashes)
    lock_until TIMESTAMPTZ NOT NULL,
    
    -- When the lock was acquired (audit trail)
    locked_at TIMESTAMPTZ NOT NULL,
    
    -- Instance that holds the lock (audit trail)
    locked_by VARCHAR(255) NOT NULL
);

-- Comments for documentation
COMMENT ON TABLE ezkey_shedlock IS 
    'Distributed lock table for scheduled job coordination (ShedLock library)';
COMMENT ON COLUMN ezkey_shedlock.name IS 
    'Job identifier: KEY_PROMOTION, KEY_ROTATION, REENCRYPTION, AUDIT_LIFECYCLE_PURGE';
COMMENT ON COLUMN ezkey_shedlock.lock_until IS 
    'Lock expiry timestamp - allows automatic failover if instance crashes';
COMMENT ON COLUMN ezkey_shedlock.locked_at IS 
    'Lock acquisition timestamp - for SOC2 audit trail';
COMMENT ON COLUMN ezkey_shedlock.locked_by IS 
    'Instance identifier that holds the lock - for SOC2 audit trail';
```

### Phase 3: Configuration Class

Living config: `ezkey-admin-api` → `org.ezkey.admin.config.ShedLockConfiguration` (not
`ShedLockConfig` in `ezkey-core`). Default `EnableSchedulerLock` / JDBC provider with table
`ezkey_shedlock`. Illustrative snippet below is historical naming — follow the Admin API class.

```java
/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License.
 */

package org.ezkey.admin.config;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for ShedLock distributed job locking.
 *
 * <p>Enables distributed lock coordination for scheduled jobs across multiple
 * Admin API instances in HA deployments. Uses PostgreSQL as the lock provider.
 *
 * <p><b>Lock Behavior:</b>
 * <ul>
 *   <li>defaultLockAtMostFor: Maximum lock duration (prevents deadlock if instance crashes)
 *   <li>Each job can override with @SchedulerLock(lockAtMostFor, lockAtLeastFor)
 * </ul>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class ShedLockConfiguration {

  /**
   * Creates the JDBC-based lock provider using the application's DataSource.
   *
   * @param dataSource the PostgreSQL data source
   * @return configured lock provider
   */
  @Bean
  public LockProvider lockProvider(DataSource dataSource) {
    return new JdbcTemplateLockProvider(
        JdbcTemplateLockProvider.Configuration.builder()
            .withJdbcTemplate(new org.springframework.jdbc.core.JdbcTemplate(dataSource))
            .usingDbTime()  // Use database time for consistency across instances
            .build()
    );
  }
}
```

### Phase 4: Annotate Scheduled Jobs

Modify existing scheduled methods with `@SchedulerLock`:

#### KeyRotationService.java

```java
/**
 * Scheduled job to check and promote PENDING keys that are ready.
 * Runs every 5 seconds. Only one instance executes at a time.
 */
@Scheduled(fixedRateString = "${ezkey.encryption.rotation.promotion-check-interval-seconds:5}000")
@SchedulerLock(
    name = "KEY_PROMOTION",
    lockAtMostFor = "PT1M",      // Max 1 minute (failover safety)
    lockAtLeastFor = "PT4S"      // Min 4 seconds (prevent rapid re-execution)
)
@Transactional
public void checkAndPromotePendingKeys() {
    // Existing code unchanged
}

/**
 * Scheduled key rotation check. Daily at 2 AM.
 * Only one instance executes at a time.
 */
@Scheduled(cron = "${ezkey.encryption.rotation.schedule:0 0 2 * * ?}")
@SchedulerLock(
    name = "KEY_ROTATION",
    lockAtMostFor = "PT10M",     // Max 10 minutes
    lockAtLeastFor = "PT1M"      // Min 1 minute
)
@Transactional
public void checkAndRotate() {
    // Existing code unchanged
}
```

#### ReencryptionService.java

```java
/**
 * Scheduled re-encryption batch processing. Daily at 3 AM.
 * Only one instance executes at a time.
 */
@Scheduled(cron = "${ezkey.encryption.reencryption.schedule:0 0 3 * * ?}")
@SchedulerLock(
    name = "REENCRYPTION",
    lockAtMostFor = "PT30M",     // Max 30 minutes (batch processing)
    lockAtLeastFor = "PT1M"      // Min 1 minute
)
@Transactional
public void processReencryptionBatches() {
    // Existing code unchanged
}
```

#### AuditLifecyclePurgeScheduler.java

```java
/**
 * Scheduled audit lifecycle purge. Daily at 2 AM.
 * Only one instance executes at a time.
 */
@Scheduled(cron = "${ezkey.audit.archive.purge.cron:0 0 2 * * ?}")
@SchedulerLock(
    name = "AUDIT_LIFECYCLE_PURGE",
    lockAtMostFor = "PT10M",     // Max 10 minutes
    lockAtLeastFor = "PT1M"      // Min 1 minute
)
public void purgeLifecycleEligibleAuditLogs() {
    // Existing code unchanged
}
```

---

## Lock Duration Guidelines

| Job | lockAtMostFor | lockAtLeastFor | Rationale |
|-----|---------------|----------------|-----------|
| `KEY_PROMOTION` | 1 minute | 4 seconds | Runs every 5s, fast execution |
| `KEY_ROTATION` | 10 minutes | 1 minute | Daily, includes backup |
| `REENCRYPTION` | 30 minutes | 1 minute | Batch processing |
| `AUDIT_LIFECYCLE_PURGE` | 10 minutes | 1 minute | Daily maintenance |

**lockAtMostFor**: Safety valve - if instance crashes, lock auto-releases after this duration.

**lockAtLeastFor**: Minimum hold time - prevents same job from running twice in rapid succession (important for `KEY_PROMOTION` which runs every 5 seconds).

---

## Failover Scenarios

### Scenario 1: Instance Crash During Job Execution

```
T=0:    Instance A acquires lock, starts KEY_PROMOTION
T=2:    Instance A crashes mid-execution
T=5:    Instance B tries to acquire lock - BLOCKED (lock held)
T=60:   Lock expires (lockAtMostFor=1 minute)
T=65:   Instance B acquires lock, executes KEY_PROMOTION
```

**Recovery Time**: lockAtMostFor duration (configurable per job)

### Scenario 2: Normal Operation

```
T=0:    Instance A acquires lock, executes KEY_PROMOTION (200ms)
T=0.2:  Instance A completes, releases lock
T=5:    Next scheduled execution - Instance B or C may acquire lock
```

**No instance affinity** - healthy load distribution across instances.

### Scenario 3: Network Partition

```
T=0:    Instance A acquires lock
T=5:    Network partition - A loses DB connectivity
T=5:    A cannot release lock, continues holding
T=60:   Lock expires in database
T=65:   Instance B acquires lock
T=70:   Network heals - A's transaction will fail (lock lost)
```

Database time (`usingDbTime()`) ensures consistent lock expiry across instances.

---

## Monitoring and SOC2 Auditability

### Query for Audit Trail

```sql
-- Current lock status (who is running what)
SELECT name, locked_by, locked_at, lock_until, 
       CASE WHEN lock_until > NOW() THEN 'ACTIVE' ELSE 'EXPIRED' END as status
FROM ezkey_shedlock
ORDER BY locked_at DESC;

-- Job execution frequency by instance (SOC2 evidence)
SELECT locked_by, name, COUNT(*) as executions
FROM ezkey_shedlock
GROUP BY locked_by, name
ORDER BY name, executions DESC;
```

### Combined with Existing Audit Logs

ShedLock table provides **execution evidence**, while existing `AuditLogService` provides **business event details**. Together they satisfy SOC2 requirements:

- **Who**: `locked_by` column
- **What**: `name` column + audit log details
- **When**: `locked_at` column
- **Outcome**: Audit log `event_status`

### Health Endpoint Enhancement

```java
@Component
public class ShedLockHealthIndicator implements HealthIndicator {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Health health() {
        try {
            List<Map<String, Object>> locks = jdbcTemplate.queryForList(
                "SELECT name, locked_by, lock_until > NOW() as active FROM ezkey_shedlock"
            );
            return Health.up()
                .withDetail("locks", locks)
                .build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
```

---

## Configuration Properties

```properties
# ShedLock is automatically configured via ShedLockConfig.java
# No additional properties required for basic operation

# Optional: Instance identification for better audit trail
# Defaults to hostname if not set
spring.application.name=ezkey-admin-api
```

---

## Alternative: Custom Leader Election (Archived)

For future reference, a custom leader election pattern was designed but **not recommended** for current ezkey requirements. This pattern would be appropriate if ezkey evolves to have:

- **Long-running jobs** (minutes to hours)
- **Stateful processing** requiring instance affinity
- **Leadership continuity** between consecutive job runs
- **Custom failover logic** (e.g., graceful handover)

The full design is preserved in [Appendix A](#appendix-a-custom-leader-election-design) for future consideration.

---

## Implementation Checklist

- [x] Add ShedLock dependencies (reactor / Admin API classpath)
- [x] Create database table `ezkey_shedlock` (Flyway **V5**)
- [x] Create `ShedLockConfiguration` in `ezkey-admin-api` (`EnableSchedulerLock` + JDBC lock provider)
- [x] Add `@SchedulerLock` to key rotation, re-encryption, audit purge, partition creation, admin token cleanup (and related schedulers as added)
- [x] Add `ShedLockHealthIndicator`
- [x] HA validation path: `docker/README-HA.md` + `ShedLockDistributedTest`
- [x] Optional: deeper refresh of sample snippets in this doc (class name / full job inventory) — see corpus-ablation parking

**Estimated Effort (historical):** 1-2 hours for the initial slice; shipped.

---

## References

- [ShedLock GitHub](https://github.com/lukas-krecan/ShedLock) - Official documentation
- [ShedLock with Spring Boot](https://www.baeldung.com/shedlock-spring) - Tutorial
- [Distributed Locks with Database](https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html) - Theory
- [Leader Election Patterns](https://docs.microsoft.com/en-us/azure/architecture/patterns/leader-election) - When to use leader election

---

## Appendix A: Custom Leader Election Design (Archived)

> **Note:** This design is archived for future reference. ShedLock is the recommended solution for current requirements.

### When to Consider Custom Leader Election

Consider this pattern if ezkey requirements evolve to include:

1. **Long-running jobs** that span multiple minutes/hours
2. **Stateful processing** where instance must maintain context
3. **Graceful handover** requirements for planned failovers
4. **Rich leadership metadata** for operational dashboards

### Architecture (Leader Election Model)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         JOB LEADER ELECTION MECHANISM                    │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  Admin API 1                Admin API 2                Admin API 3       │
│  ┌──────────┐               ┌──────────┐               ┌──────────┐     │
│  │ Instance │               │ Instance │               │ Instance │     │
│  │   ID: A  │               │   ID: B  │               │   ID: C  │     │
│  │  LEADER  │               │ DORMANT  │               │ DORMANT  │     │
│  └────┬─────┘               └────┬─────┘               └────┬─────┘     │
│       │                          │                          │           │
│       │ Heartbeat (10s)          │ Check (10s)              │           │
│       ▼                          ▼                          ▼           │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                     PostgreSQL: ezkey_job_leader                 │   │
│  │  ┌─────────────────────────────────────────────────────────────┐│   │
│  │  │ job_name          │ leader_id │ last_heartbeat │ expires_at ││   │
│  │  ├───────────────────┼───────────┼────────────────┼────────────┤│   │
│  │  │ KEY_ROTATION      │ A         │ 12:00:05       │ 12:00:35   ││   │
│  │  │ KEY_PROMOTION     │ A         │ 12:00:05       │ 12:00:35   ││   │
│  │  └─────────────────────────────────────────────────────────────┘│   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Database Schema (Leader Election)

```sql
CREATE TABLE ezkey_job_leader (
    job_name        VARCHAR(100) PRIMARY KEY,
    leader_id       VARCHAR(100) NOT NULL,
    acquired_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_heartbeat  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMPTZ NOT NULL,
    leader_metadata JSONB
);

CREATE INDEX idx_job_leader_expires ON ezkey_job_leader(expires_at);
```

### Implementation Components

Required code for leader election pattern:
- `JobLeader` entity (~50 lines)
- `JobLeaderRepository` with optimistic locking (~40 lines)
- `JobLeaderService` with heartbeat/takeover logic (~150 lines)
- `JobLeaderHealthIndicator` (~30 lines)
- Integration tests (~300 lines)

**Total: ~600 lines of custom code**

### Complexity vs Benefit Analysis

| Aspect | ShedLock | Custom Leader |
|--------|----------|---------------|
| Code to maintain | 0 lines | ~600 lines |
| Edge cases tested | Library handles | Must implement |
| Heartbeat overhead | None | Every job tick |
| Instance affinity | No (by design) | Yes |
| Graceful shutdown | Automatic | Manual @PreDestroy |

**Conclusion:** The additional complexity of leader election is justified only when instance affinity or stateful processing is required.

---

*Document created: 2025-12-04*
*Last updated: 2026-08-12*
*Status: Implemented — ShedLock on Admin API (JVM); native HA exercise via docker HA stack*
*Related: Key Rotation Strategy, SOC 2 Compliance, `docker/README-HA.md`*
