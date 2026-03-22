# Database Partitioning Implementation Summary

## Overview

This document summarizes the implementation of database table partitioning for EZKEY, focusing on high-volume tables that benefit from monthly range partitioning.

**Implementation Date:** January 2025  
**Status:** ✅ Completed  
**Tables Partitioned:** 2 (`ezkey_auth_attempt`, `ezkey_audit_log`)

---

## Implementation Summary

### Tables Partitioned

#### 1. ezkey_auth_attempt

- **Partitioning Strategy:** Monthly range partitioning by `created_at`
- **Migration:** V23__partition_auth_attempt_by_month.sql
- **Partition Naming:** `ezkey_auth_attempt_YYYY_MM` (e.g., `ezkey_auth_attempt_2025_01`)
- **Indexes Created:**
  - `idx_auth_attempt_enrollment_created` - for enrollment queries with created_at
  - `idx_auth_attempt_expires_at` - for expiration cleanup jobs
  - `idx_auth_attempt_status` - for status filtering

#### 2. ezkey_audit_log

- **Partitioning Strategy:** Composite: RANGE by `created_at` (monthly), then LIST by `api_name` per month (ADMIN_API, AUTH_API, INTEGRATION_API)
- **Migration:** V24__partition_audit_log_by_month.sql
- **Partition Naming:** `ezkey_audit_log_YYYY_MM` (monthly parent), then `ezkey_audit_log_YYYY_MM_admin`, `_auth`, `_integration` (sub-partitions)
- **Indexes Created:**
  - `idx_audit_log_event_type` - for event type queries
  - `idx_audit_log_status` - for status queries
  - `idx_audit_log_ip_address` - for IP address queries
  - `idx_audit_log_created_at` - for date range queries
  - `idx_audit_log_api_event` - for API + event type queries
  - `idx_audit_log_enrollment` - for enrollment tracking
  - `idx_audit_log_admin` - for admin tracking

---

## Automatic Partition Creation

### Security Design: SECURITY DEFINER Function

**Migration:** `V25__create_partition_management_function.sql`

**Approach:** Uses PostgreSQL `SECURITY DEFINER` function to maintain role separation:

- **Function:** `create_monthly_partition()` owned by owner role (EZKEY_owner)
- **Application Role:** Only needs EXECUTE privilege (not CREATE TABLE)
- **Security:** Function executes with owner privileges, maintaining separation of duties

**Why This Approach:**
- ✅ Maintains security best practices (principle of least privilege)
- ✅ Supports SOC2 compliance (separation of DDL vs DML privileges)
- ✅ Application role doesn't need DDL privileges
- ✅ Function validates inputs to prevent SQL injection

### PartitionSchedulerService

**Location:** `ezkey-core/src/main/java/org/ezkey/database/service/PartitionSchedulerService.java`

**Functionality:**
- Automatically creates partitions for the next month
- Runs daily at 1 AM (configurable)
- Uses SECURITY DEFINER function (no DDL privileges needed)
- Idempotent - function checks if partition exists before creating
- For `ezkey_auth_attempt`: creates one monthly partition
- For `ezkey_audit_log`: creates one monthly partition plus three LIST(api_name) sub-partitions (_admin, _auth, _integration)

**Configuration Properties:**
```properties
# Enable/disable partition scheduler (default: true)
ezkey.database.partition.scheduler.enabled=true

# Cron expression for schedule (default: 0 0 1 * * ? - daily at 1 AM)
ezkey.database.partition.scheduler.cron=0 0 1 * * ?
```

**How It Works:**
1. Runs daily at configured time
2. Calculates next month's date range
3. Checks if partition already exists
4. Creates partition if it doesn't exist
5. Logs success/failure

---

## Migration Steps Executed

### V23: Partition ezkey_auth_attempt

1. ✅ Created partitioned table structure (`ezkey_auth_attempt_partitioned`)
2. ✅ Created initial partitions for current month and next month
3. ✅ Migrated existing data from old table
4. ✅ Created indexes on partitioned table
5. ✅ Swapped tables (renamed old to backup, new to original name)
6. ✅ Dropped old table

### V24: Partition ezkey_audit_log (composite)

1. ✅ Created partitioned table structure (RANGE by created_at)
2. ✅ Created initial monthly partitions for current month and next month, each with LIST(api_name) sub-partitions (_admin, _auth, _integration)
3. ✅ Created indexes on partitioned table (propagated to all sub-partitions)

---

## Verification Steps

### 1. Verify Partition Creation

```sql
-- Check partitions for ezkey_auth_attempt
SELECT 
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size
FROM pg_tables
WHERE tablename LIKE 'ezkey_auth_attempt_%'
ORDER BY tablename;

-- Check partitions for ezkey_audit_log (monthly parents and api_name sub-partitions)
SELECT 
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size
FROM pg_tables
WHERE tablename LIKE 'ezkey_audit_log_%'
ORDER BY tablename;
-- Expect: ezkey_audit_log_YYYY_MM, ezkey_audit_log_YYYY_MM_admin, _auth, _integration per month
```

### 2. Verify Partition Pruning

```sql
-- ezkey_auth_attempt: prune by created_at
EXPLAIN (ANALYZE, COSTS OFF)
SELECT * FROM ezkey_auth_attempt
WHERE created_at >= '2025-01-01' AND created_at < '2025-02-01';
-- Expect: Partition Pruning in plan, only the matching monthly partition scanned

-- ezkey_audit_log: prune by created_at and api_name (sub-partition pruning)
EXPLAIN (ANALYZE, COSTS OFF)
SELECT * FROM ezkey_audit_log
WHERE created_at >= '2025-01-01' AND created_at < '2025-02-01'
  AND api_name = 'AUTH_API';
-- Expect: Only the ezkey_audit_log_YYYY_MM_auth sub-partition scanned (month + api_name pruning)
```

### 3. Verify Data Integrity

```sql
-- Compare record counts (should match)
SELECT COUNT(*) FROM ezkey_auth_attempt;
SELECT COUNT(*) FROM ezkey_audit_log;

-- Verify data distribution across partitions
SELECT 
    TO_CHAR(created_at, 'YYYY-MM') AS month,
    COUNT(*) AS record_count
FROM ezkey_auth_attempt
GROUP BY TO_CHAR(created_at, 'YYYY-MM')
ORDER BY month;
```

---

## Performance Benefits

### Query Performance

- **Partition Pruning:** Queries with `created_at` filters automatically exclude irrelevant partitions
- **Smaller Indexes:** Indexes are local to each partition, improving maintenance and query performance
- **Parallel Queries:** PostgreSQL can scan multiple partitions in parallel when needed

### Data Lifecycle Management

- **Efficient Archival:** Old partitions can be archived/dropped as a unit
- **Reduced Storage Costs:** Old data can be moved to cheaper storage
- **Simplified Retention:** Enforces retention policies at partition level

---

## Maintenance Procedures

### Creating Partitions Manually

If needed, partitions can be created manually:

```sql
-- ezkey_auth_attempt: single monthly partition
CREATE TABLE ezkey_auth_attempt_2025_03
PARTITION OF ezkey_auth_attempt
FOR VALUES FROM ('2025-03-01') TO ('2025-04-01');

-- ezkey_audit_log: monthly partition + LIST(api_name) sub-partitions
CREATE TABLE ezkey_audit_log_2025_03
PARTITION OF ezkey_audit_log
FOR VALUES FROM ('2025-03-01') TO ('2025-04-01')
PARTITION BY LIST (api_name);
CREATE TABLE ezkey_audit_log_2025_03_admin PARTITION OF ezkey_audit_log_2025_03 FOR VALUES IN ('ADMIN_API');
CREATE TABLE ezkey_audit_log_2025_03_auth PARTITION OF ezkey_audit_log_2025_03 FOR VALUES IN ('AUTH_API');
CREATE TABLE ezkey_audit_log_2025_03_integration PARTITION OF ezkey_audit_log_2025_03 FOR VALUES IN ('INTEGRATION_API');
```

### Archiving Old Partitions

For SOC2 compliance (7-year retention for audit logs):

```sql
-- Example: Archive partition older than retention period
-- This would be part of a scheduled cleanup job
-- Note: Actual archival depends on your backup/archival system
```

### Monitoring Partition Sizes

```sql
-- Monitor partition sizes
SELECT 
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size,
    pg_size_pretty(pg_relation_size(schemaname||'.'||tablename)) AS table_size,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename) - pg_relation_size(schemaname||'.'||tablename)) AS indexes_size
FROM pg_tables
WHERE tablename LIKE 'ezkey_audit_log_%' OR tablename LIKE 'ezkey_auth_attempt_%'
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;
```

---

## Configuration

### Application Properties

Add to `application.properties`:

```properties
# Partition Scheduler Configuration
ezkey.database.partition.scheduler.enabled=true
ezkey.database.partition.scheduler.cron=0 0 1 * * ?
```

### Production Setup: Database Roles

**Important:** In production environments with separated database roles:

1. **Function Ownership:**
   ```sql
   -- Function must be owned by owner role
   ALTER FUNCTION create_monthly_partition(TEXT, TEXT, TIMESTAMPTZ, TIMESTAMPTZ) 
   OWNER TO EZKEY_owner;
   ```

2. **Application Role Permissions:**
   ```sql
   -- Grant EXECUTE to application role (no CREATE TABLE needed)
   GRANT EXECUTE ON FUNCTION create_monthly_partition(TEXT, TEXT, TIMESTAMPTZ, TIMESTAMPTZ) 
   TO EZKEY_app;
   ```

3. **Security:**
   - Function uses `SECURITY DEFINER` to execute with owner privileges
   - Application role only needs EXECUTE privilege
   - Function validates inputs to prevent SQL injection

### Disabling Partition Scheduler

If you need to disable automatic partition creation:

```properties
ezkey.database.partition.scheduler.enabled=false
```

**Note:** Partitions must be created manually if scheduler is disabled. In production, consider using pg_cron or external scheduler instead.

---

## Troubleshooting

### Partition Creation Fails

**Symptoms:** Logs show error creating partition

**Possible Causes:**
- Partition already exists (idempotent check should prevent this)
- Insufficient permissions
- Invalid date range

**Solution:**
- Check logs for specific error message
- Verify database user has CREATE TABLE permissions
- Manually create partition if needed

### Partition Pruning Not Working

**Symptoms:** Queries scan all partitions even with date (or api_name) filters

**Possible Causes:**
- Query doesn't include `created_at` filter (required for both tables)
- For audit_log: adding `api_name` filter prunes to a single sub-partition per month
- Date filter uses wrong format or partition boundaries don't match query range

**Solution:**
- Ensure queries include `created_at` filter; for audit_log, add `api_name` when filtering by API
- Use `EXPLAIN (ANALYZE, COSTS OFF)` to verify partition pruning in the plan

---

## Future Considerations

### Sub-Partitioning

- **ezkey_audit_log:** ✅ Implemented. Each month is sub-partitioned by LIST(api_name) into _admin, _auth, _integration for partition pruning when filtering by API.
- **ezkey_auth_attempt:** Sub-partition by `enrollment_id` (hash) only if >100M records/month; not needed initially.

### Tenant-Based Partitioning

For multi-tenant SaaS deployments:

- Partition by `tenant_id` for complete tenant isolation
- Requires application-level routing

**Status:** Documented for future evaluation, not needed initially

---

## Security and Compliance

### Role Separation

**Production Best Practice:**
- **EZKEY_owner:** DDL privileges (CREATE TABLE, ALTER TABLE) - used by Flyway
- **EZKEY_app:** DML privileges only (SELECT, INSERT, UPDATE, DELETE) - used by application

**Implementation:**
- Partition creation uses `SECURITY DEFINER` function
- Application role only needs EXECUTE privilege (not CREATE TABLE)
- Maintains SOC2 compliance (separation of duties)

**See:** `docs/DATABASE_PARTITIONING_SECURITY_ANALYSIS.md` for detailed security analysis

## References

- **Partitioning Plan:** See main partitioning analysis document
- **Compliance Considerations:** `docs/DATABASE_PARTITIONING_COMPLIANCE_CONSIDERATIONS.md`
- **Security Analysis:** `docs/DATABASE_PARTITIONING_SECURITY_ANALYSIS.md`
- **PostgreSQL Partitioning:** https://www.postgresql.org/docs/current/ddl-partitioning.html
- **PostgreSQL SECURITY DEFINER:** https://www.postgresql.org/docs/current/sql-createfunction.html
- **Migration Files:**
  - `ezkey-core/src/main/resources/db/migration/V23__partition_auth_attempt_by_month.sql`
  - `ezkey-core/src/main/resources/db/migration/V24__partition_audit_log_by_month.sql`
  - `ezkey-core/src/main/resources/db/migration/V25__create_partition_management_function.sql`
- **Service Implementation:**
  - `ezkey-core/src/main/java/org/ezkey/database/service/PartitionSchedulerService.java`

---

**Document Version:** 1.1  
**Last Updated:** March 2025  
**Status:** Implementation Complete (audit_log composite RANGE+LIST(api_name))  
**Next Review:** After production deployment

