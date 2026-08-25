# Database Partitioning Strategy - Design Summary

**Purpose:** A pragmatic reference for developers and AI agents making design decisions about database tables that might grow in ezkey.

**Last Updated:** January 29, 2025  
**Status:** Implemented and Production-Ready

---

## TL;DR - What You Need to Know

### When to Use Partitioning

✅ **Use partitioning for:**
- Tables with **high insert volume** (e.g., authentication attempts, audit logs)
- Tables with **time-based access patterns** (recent data accessed more frequently)
- Tables requiring **compliance retention** (7-year audit logs, etc.)
- Tables where **old data archival** simplifies operations

❌ **Don't use partitioning for:**
- Small reference tables (tenants, integrations, enrollments)
- Tables with random access patterns (no time-based queries)
- Tables with complex relationships requiring frequent joins across partitions

### Key Design Decision

**Monthly range partitioning by `created_at`** - Simple, effective, and works for 90% of use cases.

---

## Overview: The Problem We're Solving

### Context

Ezkey has two high-volume tables that grow continuously in production:

1. **`ezkey_auth_attempt`** - Every authentication request creates a new row
2. **`ezkey_audit_log`** - Every significant event creates an audit log entry

**Challenge:** These tables will grow to millions/billions of rows over time, causing:
- Slow queries (full table scans)
- Large indexes (expensive to maintain)
- Complex data lifecycle management (archival, deletion)
- operator-visible audit challenges (7-year audit log retention)

### Solution: Monthly Range Partitioning

**Approach:** Split tables by month using PostgreSQL's native partitioning feature.

**Result:**
- Each month's data lives in its own physical table (partition)
- Queries with date filters automatically skip irrelevant partitions (partition pruning)
- Old partitions can be archived/dropped as a unit
- Indexes are smaller and faster (local to each partition)

---

## Design: How It Works

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                       Ezkey Application                          │
│  ┌──────────────────┐           ┌──────────────────┐            │
│  │   Admin API      │           │    Auth API      │            │
│  │   (port 9080)    │           │   (port 8080)    │            │
│  └────────┬─────────┘           └────────┬─────────┘            │
│           │                               │                       │
│           │  INSERT/SELECT                │  INSERT/SELECT        │
│           ▼                               ▼                       │
└───────────┼───────────────────────────────┼───────────────────────┘
            │                               │
            ▼                               ▼
┌───────────────────────────────────────────────────────────────────┐
│                     PostgreSQL Database                           │
│                                                                   │
│  ┌────────────────────────────────────────────────────────┐     │
│  │       ezkey_auth_attempt (partitioned parent)          │     │
│  ├────────────────────────────────────────────────────────┤     │
│  │  ▶ ezkey_auth_attempt_2025_01  (Jan 2025 partition)   │     │
│  │  ▶ ezkey_auth_attempt_2025_02  (Feb 2025 partition)   │     │
│  │  ▶ ezkey_auth_attempt_2025_03  (Mar 2025 partition)   │     │
│  │  ▶ ...                                                 │     │
│  └────────────────────────────────────────────────────────┘     │
│                                                                   │
│  ┌────────────────────────────────────────────────────────┐     │
│  │   ezkey_audit_log (RANGE created_at, LIST api_name)   │     │
│  ├────────────────────────────────────────────────────────┤     │
│  │  ▶ ezkey_audit_log_2025_01 (+ _admin, _auth, _integration)    │     │
│  │  ▶ ezkey_audit_log_2025_02 (+ _admin, _auth, _integration)   │     │
│  │  ▶ ...                                                 │     │
│  └────────────────────────────────────────────────────────┘     │
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ Automatic Partition Creation (PartitionSchedulerService) │   │
│  │  - Runs daily at 1 AM                                    │   │
│  │  - Creates next month's partitions                       │   │
│  │  - Uses SECURITY DEFINER function (secure approach)      │   │
│  └──────────────────────────────────────────────────────────┘   │
└───────────────────────────────────────────────────────────────────┘
```

### Tables Partitioned

| Table | Partition Key | Naming Convention | Rationale |
|-------|----------------|-------------------|-----------|
| `ezkey_auth_attempt` | `created_at` | `ezkey_auth_attempt_YYYY_MM` | High insert volume, time-based queries |
| `ezkey_audit_log` | `created_at` then `api_name` | `ezkey_audit_log_YYYY_MM` + `_admin`, `_auth`, `_integration` | 7-year retention; sub-partition by API for pruning |

### Key Design Principles

1. **Range partitioning by `created_at`** - Natural partition key for time-series data
2. **Monthly boundaries** - Balance between too many partitions (daily) and too few (yearly)
3. **Immutable data** - Auth attempts and audit logs are never updated after creation
4. **Partition pruning** - Most queries include date filters (recent data)
5. **Forward-looking** - Always pre-create next month's partition to avoid runtime errors

---

## Role Separation: Flyway vs Scheduled Batch

### Critical Security Design: No DDL Privileges for Application

**Problem:** Creating partitions requires `CREATE TABLE` privilege (DDL operation).  
**Best Practice:** Application role should only have DML privileges (SELECT, INSERT, UPDATE, DELETE).  
**Solution:** Use PostgreSQL `SECURITY DEFINER` function with role separation.

### Flyway Migrations (DDL - One-Time Setup)

**Runs as:** `EZKEY_owner` (database owner role with DDL privileges)  
**When:** During deployment/migration  
**Migrations:**
- **V4** - Create `ezkey_auth_attempt` / `ezkey_audit_log` partitioned tables + initial
  partitions + `create_monthly_partition()` SECURITY DEFINER function (`RETURNS BOOLEAN`)

**Key Files:**
```
ezkey-core/src/main/resources/db/migration/
└── V4__partitioning_auth_audit_and_function.sql
```

**What Flyway Does:**
1. Creates partitioned table structures
2. Creates initial partitions (current month + next month)
3. Creates indexes on partitioned tables
4. Creates secure partition management function with input validation

### Scheduled Batch (DML - Runtime Operation)

**Runs as:** `EZKEY_app` (application role with DML privileges only)  
**When:** Daily at 1 AM (configurable via cron)  
**Service:** `PartitionSchedulerService.java`  
**Location:** `ezkey-core/src/main/java/org/ezkey/database/service/`

**What the Scheduled Batch Does:**
1. Calculates next month's date range
2. Calls `create_monthly_partition()` function via JDBC
3. Function executes with owner privileges (SECURITY DEFINER)
4. Creates partition if it doesn't exist (idempotent)
5. Logs success/failure

**Configuration:**
```properties
# Enable/disable partition scheduler (default: true)
ezkey.database.partition.scheduler.enabled=true

# Cron expression (default: daily at 1 AM)
ezkey.database.partition.scheduler.cron=0 0 1 * * ?
```

### Role Separation Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                     Flyway (Migration Time)                      │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  EZKEY_owner role (DDL privileges)                       │   │
│  │  - Creates partitioned tables                            │   │
│  │  - Creates SECURITY DEFINER function                     │   │
│  │  - Grants EXECUTE to EZKEY_app                           │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ Creates function owned by EZKEY_owner
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│              create_monthly_partition() Function                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  SECURITY DEFINER (executes with owner privileges)       │   │
│  │  - Validates table name (whitelist)                      │   │
│  │  - Validates partition name format                       │   │
│  │  - Checks if partition exists (idempotent)               │   │
│  │  - Creates partition (DDL operation)                     │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ Calls function with EXECUTE privilege
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│              PartitionSchedulerService (Runtime)                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  EZKEY_app role (DML privileges only - no CREATE TABLE) │   │
│  │  - Calculates next month's date range                    │   │
│  │  - Calls create_monthly_partition() function             │   │
│  │  - Logs result                                           │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

**Security Benefits:**
- ✅ Application never has DDL privileges (principle of least privilege)
- ✅ Function validates inputs to prevent SQL injection
- ✅ Separation of duties
- ✅ Auditable (function calls can be logged)

---

## Lifecycle Management: How Tables Grow and Shrink

### Partition Creation Lifecycle

#### Timeline View

```
Month: Dec 2024    Jan 2025         Feb 2025         Mar 2025
       ─────────────┼────────────────┼────────────────┼─────────────
                    │                │                │
                    │                │                │
       ┌────────────┴────────┐      │                │
       │  V23/V24 Migration  │      │                │
       │  Creates:           │      │                │
       │  - *_2025_01 ✓      │      │                │
       │  - *_2025_02 ✓      │      │                │
       └─────────────────────┘      │                │
                                     │                │
                    ┌────────────────┴─────────┐     │
                    │ Scheduler runs daily     │     │
                    │ Creates:                 │     │
                    │ - *_2025_03 ✓            │     │
                    └──────────────────────────┘     │
                                                      │
                                     ┌────────────────┴─────────┐
                                     │ Scheduler runs daily     │
                                     │ Creates:                 │
                                     │ - *_2025_04 ✓            │
                                     └──────────────────────────┘
```

#### Creation Process

1. **Initial Setup (Flyway Migration)**
   - Migration runs on first deployment
   - Creates partitioned table structure
   - Creates partitions for current month and next month
   - Creates SECURITY DEFINER function

2. **Ongoing Creation (Scheduled Batch)**
   - Runs daily at 1 AM
   - Checks if next month's partition exists
   - Creates partition if it doesn't exist
   - Idempotent operation (safe to run multiple times)

3. **High Availability (HA) Support**
   - Uses **ShedLock** to coordinate multiple instances
   - Ensures only one instance creates partitions at a time
   - Lock timeout: 10 minutes
   - Prevents duplicate partition creation in clustered environments

### Partition Archival Lifecycle

**Status:** Not yet implemented (future feature)

**Planned Approach:**
1. **Retention Policy Definition**
   - Auth attempts: 90 days retention (operational data)
   - Audit logs: 7 years retention (operator-visible audit)

2. **Archival Process**
   - Identify partitions older than retention period
   - Check for legal hold (if applicable)
   - Archive partition to cold storage (e.g., S3, tape)
   - Drop partition from database

3. **Implementation Strategy**
   - Scheduled job (separate from creation)
   - Runs monthly (less frequently than creation)
   - Archives partitions by date range
   - Maintains compliance requirements

**Example Query (Future Implementation):**
```sql
-- Identify partitions to archive (older than 7 years for audit_log)
SELECT tablename 
FROM pg_tables 
WHERE tablename LIKE 'ezkey_audit_log_%'
  AND tablename < 'ezkey_audit_log_' || 
      to_char(CURRENT_DATE - INTERVAL '7 years', 'YYYY_MM')
ORDER BY tablename;
```

### Data Flow Through Partitions

```
                     ┌──────────────────┐
                     │  New Data Arrives │
                     └─────────┬─────────┘
                               │
                               ▼
                     ┌──────────────────────┐
                     │ PostgreSQL Routes to  │
                     │ Current Month Partition│
                     └─────────┬──────────────┘
                               │
        ┌──────────────────────┼──────────────────────┐
        │                      │                      │
        ▼                      ▼                      ▼
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│ 2025_01       │    │ 2025_02       │    │ 2025_03       │
│ (Old Data)    │    │ (Recent Data) │    │ (Current)     │
│               │    │               │    │               │
│ Status:       │    │ Status:       │    │ Status:       │
│ - Low traffic │    │ - Medium      │    │ - High traffic│
│ - Ready for   │    │ - Active      │    │ - Active      │
│   archival    │    │               │    │               │
└───────────────┘    └───────────────┘    └───────────────┘
        │                      │                      │
        │                      │                      │
        ▼                      ▼                      ▼
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│ Future:       │    │ Future:       │    │ Future:       │
│ Archive to S3 │    │ Stay online   │    │ Stay online   │
│ Drop partition│    │               │    │               │
└───────────────┘    └───────────────┘    └───────────────┘
```

---

## Rationale: Why We Made These Choices

### Why Monthly Partitioning?

**Considered Options:**
1. **Daily partitioning** - Too many partitions (365 per year), complex management
2. **Weekly partitioning** - Doesn't align with business reporting (monthly)
3. **Monthly partitioning** ✅ - Sweet spot: balance between too many and too few
4. **Yearly partitioning** - Partitions too large, archival too infrequent

**Decision:** Monthly partitioning balances partition count with operational simplicity.

### Why Range Partitioning (vs List or Hash)?

**Considered Options:**
1. **Range partitioning by `created_at`** ✅ - Natural for time-series data
2. **List partitioning by status** - No archival benefits, complex queries
3. **Hash partitioning by ID** - No partition pruning benefits, can't archive by date

**Decision:** Range partitioning by `created_at` enables partition pruning and simplifies archival.

### Why SECURITY DEFINER Function?

**Considered Options:**
1. **Grant CREATE TABLE to application** ❌ - Security risk, violates least privilege
2. **PostgreSQL pg_cron extension** - Requires superuser, not available everywhere
3. **External scheduler (OS cron)** - Requires separate deployment, credential management
4. **SECURITY DEFINER function** ✅ - Secure, portable, maintainable

**Decision:** SECURITY DEFINER function maintains security best practices while enabling application-driven partition creation.

### Why Pre-Create Next Month?

**Problem:** If current month's partition doesn't exist when data arrives, INSERT fails.

**Solution:** Always pre-create next month's partition to ensure seamless transition.

**Implementation:**
- Flyway creates current + next month during migration
- Scheduler creates next month daily (idempotent)
- Result: Next month's partition always ready before month boundary

---

## Lessons Learned & Best Practices

### 1. Partition Key Must Be in Primary Key

**Issue:** PostgreSQL requires partition key to be part of primary key/unique constraints.

**Solution:**
```sql
-- WRONG: Primary key doesn't include partition key
PRIMARY KEY (auth_attempt_id)  -- ❌ Will fail for partitioned table

-- CORRECT: Composite primary key includes partition key
PRIMARY KEY (auth_attempt_id, created_at)  -- ✅ Works with partitioning
```

**Impact:** All foreign keys referencing partitioned tables must also be composite:
```sql
-- Composite foreign key to partitioned table
FOREIGN KEY (auth_attempt_id, auth_attempt_created_at) 
    REFERENCES ezkey_auth_attempt(auth_attempt_id, created_at)
```

### 2. Indexes Are Local to Each Partition

**Lesson:** When you create an index on a partitioned table, PostgreSQL automatically creates local indexes on each partition.

**Benefits:**
- Smaller indexes (faster queries)
- Easier maintenance (REINDEX on individual partitions)
- Parallel index creation (one per partition)

**Implementation:**
```sql
-- Create index on partitioned table
CREATE INDEX idx_auth_attempt_enrollment_created 
ON ezkey_auth_attempt(enrollment_id, created_at DESC);

-- Result: Local indexes created automatically
-- - ezkey_auth_attempt_2025_01_enrollment_created_idx
-- - ezkey_auth_attempt_2025_02_enrollment_created_idx
-- - etc.
```

### 3. Use `SET search_path = public` in SECURITY DEFINER Functions

**Security Risk:** `SECURITY DEFINER` functions can be vulnerable to search_path injection attacks.

**Solution:**
```sql
CREATE OR REPLACE FUNCTION create_monthly_partition(...)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public  -- ✅ Prevents search_path injection
AS $$
BEGIN
    -- Function body
END;
$$;
```

### 4. Always Validate Inputs in SECURITY DEFINER Functions

**Security Risk:** Function executes with owner privileges, must validate all inputs.

**Solution:**
```sql
-- Whitelist table names
IF p_table_name NOT IN ('ezkey_auth_attempt', 'ezkey_audit_log') THEN
    RAISE EXCEPTION 'Invalid table name: %', p_table_name;
END IF;

-- Validate partition name format
IF p_partition_name !~ '^ezkey_(auth_attempt|audit_log)_\d{4}_\d{2}$' THEN
    RAISE EXCEPTION 'Invalid partition name format: %', p_partition_name;
END IF;
```

### 5. Make Partition Creation Idempotent

**Problem:** Scheduler might run multiple times (restarts, HA coordination).

**Solution:**
```sql
-- Check if partition exists before creating
IF EXISTS (
    SELECT 1 FROM pg_class 
    WHERE relname = p_partition_name AND relkind = 'r'
) THEN
    RAISE NOTICE 'Partition % already exists, skipping', p_partition_name;
    RETURN false;  -- Indicate partition already existed
END IF;
```

### 6. Use ShedLock for HA Coordination

**Problem:** In clustered environments, multiple instances might try to create partitions simultaneously.

**Solution:** Use ShedLock to ensure only one instance creates partitions at a time.

```java
@Scheduled(cron = "${ezkey.database.partition.scheduler.cron:0 0 1 * * ?}")
@SchedulerLock(name = "DB_PARTITION_CREATION", lockAtMostFor = "PT10M")
@Transactional
public void createNextMonthPartitions() {
    // Only one instance executes this at a time
}
```

### 7. Partition Pruning Requires Filters on Partition Key

**Important:** Queries must include filters on `created_at` to benefit from partition pruning.

**Example:**
```sql
-- ✅ Good: Uses partition pruning
SELECT * FROM ezkey_auth_attempt
WHERE created_at >= '2025-02-01' AND created_at < '2025-03-01'
  AND enrollment_id = 123;

-- ❌ Bad: Scans all partitions (no created_at filter)
SELECT * FROM ezkey_auth_attempt
WHERE enrollment_id = 123;
```

**Recommendation:** Always include `created_at` filters in queries when possible.

### 8. Partition Boundaries Use TIMESTAMPTZ

**Lesson:** Always use `TIMESTAMPTZ` (timestamp with timezone) for partition boundaries.

**Reason:** Ensures correct behavior across timezones.

**Implementation:**
```sql
-- ✅ Correct: Use TIMESTAMPTZ
CREATE TABLE ezkey_auth_attempt_2025_01
PARTITION OF ezkey_auth_attempt
FOR VALUES FROM ('2025-01-01 00:00:00+00'::TIMESTAMPTZ) 
            TO ('2025-02-01 00:00:00+00'::TIMESTAMPTZ);

-- ❌ Wrong: Don't use DATE or TIMESTAMP
-- (can cause timezone-related issues)
```

### 9. Return Value Helps with Monitoring

**Lesson:** Function returns `BOOLEAN` to indicate success/already-existed.

**Benefits:**
- Enables better logging (know if partition was actually created)
- Helps with monitoring and alerting
- Useful for debugging

**Implementation:**
```sql
-- Function returns BOOLEAN
CREATE OR REPLACE FUNCTION create_monthly_partition(...) 
RETURNS BOOLEAN  -- true = created, false = already existed
AS $$
BEGIN
    -- ... validation ...
    
    IF EXISTS (...) THEN
        RETURN false;  -- Already existed
    END IF;
    
    -- Create partition
    EXECUTE format(...);
    
    RETURN true;  -- Created successfully
END;
$$;
```

---

## Quick Reference: Key Files

### SQL Migrations
```
ezkey-core/src/main/resources/db/migration/
└── V4__partitioning_auth_audit_and_function.sql  # Partitions + create_monthly_partition
```

### Java Service
```
ezkey-core/src/main/java/org/ezkey/database/service/
└── PartitionSchedulerService.java  # Scheduled partition creation
```

### Configuration
```properties
# application.properties
ezkey.database.partition.scheduler.enabled=true
ezkey.database.partition.scheduler.cron=0 0 1 * * ?
```

### Documentation
```
docs/
├── DATABASE_PARTITIONING_IMPLEMENTATION.md          # Detailed implementation guide
├── DATABASE_PARTITIONING_SECURITY_ANALYSIS.md       # Security analysis & options
├── DATABASE_PARTITIONING_COMPLIANCE_CONSIDERATIONS.md  # Compliance implications
└── DATABASE_PARTITIONING_STRATEGY_SUMMARY.md        # This document
```

---

## Decision Checklist: Should I Partition This Table?

Use this checklist when deciding whether to partition a new table:

### Consider Partitioning If:
- [ ] Table will have **high insert volume** (thousands/millions of rows per day)
- [ ] Table has **time-based access patterns** (queries typically filter by date)
- [ ] Table data is **immutable** (no updates after creation)
- [ ] Table requires **archival/retention** (compliance, cost optimization)
- [ ] Table indexes are becoming **too large** (> 100GB)
- [ ] Table has **natural partition key** (created_at, event_date, etc.)

### Don't Partition If:
- [ ] Table is **small** (< 1M rows total)
- [ ] Access patterns are **random** (no natural partition key)
- [ ] Table is frequently **joined** with non-partitioned tables
- [ ] Table data is **frequently updated** (partitioning adds complexity)
- [ ] Table has **complex relationships** across date boundaries
- [ ] Maintenance overhead **outweighs benefits**

### Implementation Checklist:
- [ ] Choose partition key (usually `created_at`)
- [ ] Decide partition interval (daily, weekly, monthly, yearly)
- [ ] Update primary key to include partition key
- [ ] Update foreign keys to use composite keys
- [ ] Create Flyway migration to create partitioned table
- [ ] Add table to `create_monthly_partition()` function whitelist
- [ ] Update `PartitionSchedulerService` to create partitions for new table
- [ ] Add indexes (will be created locally on each partition)
- [ ] Test partition pruning with `EXPLAIN ANALYZE`
- [ ] Document retention policy and archival strategy
- [ ] Update monitoring/alerting for partition size and count

---

## Verification & Monitoring

### Check Existing Partitions
```sql
-- List all partitions for auth_attempt
SELECT 
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size
FROM pg_tables
WHERE tablename LIKE 'ezkey_auth_attempt_%'
ORDER BY tablename;

-- List all partitions for audit_log
SELECT 
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size
FROM pg_tables
WHERE tablename LIKE 'ezkey_audit_log_%'
ORDER BY tablename;
```

### Verify Partition Pruning
```sql
-- Test if query uses partition pruning
EXPLAIN ANALYZE
SELECT * FROM ezkey_auth_attempt
WHERE created_at >= '2025-02-01' AND created_at < '2025-03-01';

-- Look for "Partitions Pruned: N" in output
-- Should show that only one partition is scanned
```

### Monitor Partition Creation
```bash
# Check application logs for partition creation
grep "partition" /var/log/ezkey/admin-api.log

# Expected output:
# ✅ Created partition: ezkey_auth_attempt_2025_03 for table: ezkey_auth_attempt
# ℹ️ Partition ezkey_auth_attempt_2025_03 already exists for table: ezkey_auth_attempt
```

### Alerting Recommendations
- Alert if next month's partition doesn't exist 7 days before month boundary
- Alert if partition creation fails 3 consecutive times
- Alert if partition size exceeds expected threshold (indicates data spike)

---

## Future Enhancements

### Considered for Future Implementation

1. **Automatic Partition Archival**
   - Scheduled job to archive/drop old partitions
   - Configurable retention policies per table
   - Legal hold support for audit logs

2. **Sub-Partitioning**
   - **ezkey_audit_log:** ✅ Implemented — LIST(api_name) per month (_admin, _auth, _integration) for partition pruning by API.
   - **ezkey_auth_attempt:** Only if >100M rows/month; sub-partition by hash (enrollment_id) if needed.

3. **Partition Monitoring Dashboard**
   - Grafana dashboard showing partition sizes over time
   - Alerts for missing partitions
   - Archival status tracking

4. **Partition Statistics Collection**
   - Track row counts per partition
   - Monitor query performance per partition
   - Identify partition hotspots

5. **Non-Repudiation Signature Chaining**
   - Cryptographic signing of audit log entries
   - Chain verification across partitions
   - Full design documented in `DATABASE_PARTITIONING_COMPLIANCE_CONSIDERATIONS.md`

---

## Summary: Key Takeaways

### What Works Well

✅ **Monthly range partitioning** - Sweet spot for time-series data  
✅ **SECURITY DEFINER function** - Secure partition creation without elevated app privileges  
✅ **Pre-created partitions** - Seamless month transitions  
✅ **ShedLock coordination** - Safe in HA environments  
✅ **Idempotent operations** - Safe to run multiple times  
✅ **Local indexes** - Smaller, faster indexes per partition  
✅ **Partition pruning** - Automatic query optimization  

### What to Remember

⚠️ **Partition key in primary key** - PostgreSQL requirement  
⚠️ **Composite foreign keys** - Required for partitioned table references  
⚠️ **Date filters required** - For partition pruning to work  
⚠️ **TIMESTAMPTZ boundaries** - Always use timezone-aware timestamps  
⚠️ **Input validation** - Critical for SECURITY DEFINER functions  

### When to Revisit This Design

🔄 Review partitioning strategy if:
- Tables grow significantly faster/slower than expected
- Access patterns change (e.g., more historical queries)
- Compliance requirements change (retention periods)
- Performance issues emerge despite partitioning
- New database features become available

---

**Document Version:** 1.1  
**Last Updated:** March 2025  
**Author:** Ezkey contributors  
**License:** MIT  
**Related Documents:**
- Full implementation: `DATABASE_PARTITIONING_IMPLEMENTATION.md`
- Security analysis: `DATABASE_PARTITIONING_SECURITY_ANALYSIS.md`
- Compliance: `DATABASE_PARTITIONING_COMPLIANCE_CONSIDERATIONS.md`
