# Database Partitioning - Security and Role Separation Analysis

## Context

In production environments, database security best practices require separation of roles:

- **EZKEY_owner** (or similar): Database owner role with DDL privileges (CREATE TABLE, ALTER TABLE, etc.)
  - Used by Flyway for schema migrations
  - Should NOT be used by application runtime

- **EZKEY_app** (or similar): Application role with DML privileges (SELECT, INSERT, UPDATE, DELETE)
  - Used by Spring Boot application for data operations
  - Should NOT have DDL privileges for security and compliance

**Security Principle:** Principle of Least Privilege - applications should only have the minimum privileges needed for their operations.

---

## Problem Statement

Creating partitions requires **DDL privileges** (CREATE TABLE), which the application role (`EZKEY_app`) should not have in production.

**Current Implementation Issue:**
- `PartitionSchedulerService` runs in Spring Boot application context
- Uses `EntityManager` with application database credentials
- Requires CREATE TABLE privilege to create partitions
- Violates security best practices in production

---

## Options Analysis

### Option 1: PostgreSQL Function with SECURITY DEFINER ⭐ **RECOMMENDED**

**Approach:** Create a PostgreSQL function with `SECURITY DEFINER` that executes with owner privileges.

**How It Works:**
1. Create function owned by `EZKEY_owner` with `SECURITY DEFINER`
2. Function has CREATE TABLE privilege (via owner)
3. Application role (`EZKEY_app`) only needs EXECUTE privilege on function
4. Application calls function, which executes with owner privileges

**Implementation:**

```sql
-- Create function owned by owner role (executed by Flyway)
CREATE OR REPLACE FUNCTION create_monthly_partition(
    p_table_name TEXT,
    p_partition_name TEXT,
    p_start_date TIMESTAMPTZ,
    p_end_date TIMESTAMPTZ
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER  -- Execute with owner privileges
SET search_path = public
AS $$
BEGIN
    -- Check if partition already exists
    IF EXISTS (
        SELECT 1 FROM pg_class 
        WHERE relname = p_partition_name AND relkind = 'r'
    ) THEN
        RAISE NOTICE 'Partition % already exists, skipping', p_partition_name;
        RETURN;
    END IF;
    
    -- Create partition
    EXECUTE format(
        'CREATE TABLE %I PARTITION OF %I FOR VALUES FROM (%L) TO (%L)',
        p_partition_name,
        p_table_name,
        p_start_date,
        p_end_date
    );
    
    RAISE NOTICE 'Created partition: % for table: %', p_partition_name, p_table_name;
END;
$$;

-- Grant EXECUTE to application role (no CREATE TABLE needed)
GRANT EXECUTE ON FUNCTION create_monthly_partition(TEXT, TEXT, TIMESTAMPTZ, TIMESTAMPTZ) TO EZKEY_app;

-- Revoke from public (security best practice)
REVOKE EXECUTE ON FUNCTION create_monthly_partition(TEXT, TEXT, TIMESTAMPTZ, TIMESTAMPTZ) FROM PUBLIC;
```

**Application Code:**
```java
// Application calls function (no DDL privileges needed)
entityManager.createNativeQuery(
    "SELECT create_monthly_partition(:tableName, :partitionName, :startDate, :endDate)"
)
.setParameter("tableName", "ezkey_auth_attempt")
.setParameter("partitionName", "ezkey_auth_attempt_2025_02")
.setParameter("startDate", startDateTime)
.setParameter("endDate", endDateTime)
.executeUpdate();
```

**Pros:**
- ✅ Maintains role separation (application doesn't need DDL privileges)
- ✅ Secure - function executes with owner privileges
- ✅ No external dependencies
- ✅ Works with existing Spring Boot scheduler
- ✅ Audit trail (function calls logged)
- ✅ Can be called from application or external tools

**Cons:**
- ⚠️ Requires function creation in migration (one-time setup)
- ⚠️ Function must be maintained alongside application code

**Security Considerations:**
- Function uses `SET search_path = public` to prevent search_path injection
- Function validates inputs (table name, partition name)
- Application role only needs EXECUTE privilege
- Function can be audited via PostgreSQL logging

**Compliance:**
- ✅ SOC2: Principle of least privilege maintained
- ✅ Separation of duties (DDL vs DML)
- ✅ Audit trail available

---

### Option 2: PostgreSQL pg_cron Extension

**Approach:** Use PostgreSQL's pg_cron extension to schedule partition creation directly in database.

**How It Works:**
1. Install pg_cron extension (requires superuser)
2. Schedule cron job that runs with owner role
3. Job executes SQL directly in database
4. Application has no involvement in partition creation

**Implementation:**

```sql
-- Install extension (requires superuser, done once)
CREATE EXTENSION IF NOT EXISTS pg_cron;

-- Schedule job to create partitions (runs with role that created it)
SELECT cron.schedule(
    'create-monthly-partitions',  -- Job name
    '0 1 * * *',                 -- Daily at 1 AM
    $$
    DO $$
    DECLARE
        next_month_start TIMESTAMPTZ;
        next_month_end TIMESTAMPTZ;
    BEGIN
        next_month_start := DATE_TRUNC('month', CURRENT_TIMESTAMP + INTERVAL '1 month');
        next_month_end := next_month_start + INTERVAL '1 month';
        
        -- Create auth_attempt partition
        PERFORM create_monthly_partition(
            'ezkey_auth_attempt',
            'ezkey_auth_attempt_' || TO_CHAR(next_month_start, 'YYYY_MM'),
            next_month_start,
            next_month_end
        );
        
        -- Create audit_log partition
        PERFORM create_monthly_partition(
            'ezkey_audit_log',
            'ezkey_audit_log_' || TO_CHAR(next_month_start, 'YYYY_MM'),
            next_month_start,
            next_month_end
        );
    END;
    $$;
    $$
);
```

**Pros:**
- ✅ No application involvement (pure database solution)
- ✅ Runs with database role privileges (can be owner role)
- ✅ No application code changes needed
- ✅ Centralized scheduling in database

**Cons:**
- ⚠️ Requires pg_cron extension (may not be available in all environments)
- ⚠️ Requires superuser to install extension
- ⚠️ Less flexible than application-based scheduling
- ⚠️ Harder to test and debug
- ⚠️ Requires database access for configuration changes

**Security Considerations:**
- Cron job runs with privileges of role that created it
- Can be configured to run with owner role
- Application has no involvement

**Compliance:**
- ✅ SOC2: Separation of duties maintained
- ⚠️ May require additional documentation for extension management

---

### Option 3: External Scheduler (Cron/Systemd/Task Scheduler)

**Approach:** Use external scheduler (OS cron, systemd timer, or Windows Task Scheduler) to run SQL script.

**How It Works:**
1. Create SQL script that creates partitions
2. Schedule script execution via OS scheduler
3. Script runs with database owner credentials
4. Application has no involvement

**Implementation:**

**Script:** `scripts/create-partitions.sh`
```bash
#!/bin/bash
# Create monthly partitions for EZKEY tables
# Runs with EZKEY_owner role credentials

PGHOST=localhost
PGPORT=5432
PGDATABASE=ezkey_db
PGUSER=EZKEY_owner

psql -h $PGHOST -p $PGPORT -d $PGDATABASE -U $PGUSER <<EOF
DO \$\$
DECLARE
    next_month_start TIMESTAMPTZ;
    next_month_end TIMESTAMPTZ;
BEGIN
    next_month_start := DATE_TRUNC('month', CURRENT_TIMESTAMP + INTERVAL '1 month');
    next_month_end := next_month_start + INTERVAL '1 month';
    
    PERFORM create_monthly_partition(
        'ezkey_auth_attempt',
        'ezkey_auth_attempt_' || TO_CHAR(next_month_start, 'YYYY_MM'),
        next_month_start,
        next_month_end
    );
    
    PERFORM create_monthly_partition(
        'ezkey_audit_log',
        'ezkey_audit_log_' || TO_CHAR(next_month_start, 'YYYY_MM'),
        next_month_start,
        next_month_end
    );
END;
\$\$;
EOF
```

**Cron Entry:**
```cron
# Run daily at 1 AM
0 1 * * * /opt/ezkey/scripts/create-partitions.sh >> /var/log/ezkey/partitions.log 2>&1
```

**Pros:**
- ✅ Complete separation from application
- ✅ Runs with owner role credentials
- ✅ No application code changes needed
- ✅ Works in any environment (no database extensions needed)
- ✅ Easy to audit (script execution logged)

**Cons:**
- ⚠️ Requires external infrastructure (cron, systemd, etc.)
- ⚠️ Requires database credentials management
- ⚠️ Less integrated with application lifecycle
- ⚠️ Harder to test in development
- ⚠️ Requires separate deployment process

**Security Considerations:**
- Script credentials must be secured (use credential manager)
- Script should be read-only for non-owner users
- Execution should be logged

**Compliance:**
- ✅ SOC2: Complete separation of duties
- ⚠️ Requires credential management procedures

---

### Option 4: Application with Elevated Privileges ❌ **NOT RECOMMENDED**

**Approach:** Grant CREATE TABLE privilege to application role.

**Why NOT Recommended:**
- ❌ Violates principle of least privilege
- ❌ Application has unnecessary DDL privileges
- ❌ Security risk (compromised application could modify schema)
- ❌ Compliance issues (SOC2, PCI-DSS)
- ❌ Not aligned with security best practices

**Compliance Impact:**
- ❌ SOC2: Fails separation of duties requirement
- ❌ PCI-DSS: Violates least privilege principle
- ❌ General: Security best practice violation

---

## Recommendation: Hybrid Approach

**Best Practice:** Combine Option 1 (SECURITY DEFINER function) with Option 2 or 3 (scheduling).

### Recommended Implementation

1. **Create SECURITY DEFINER function** (via Flyway migration)
   - Owned by `EZKEY_owner`
   - Application role has EXECUTE privilege
   - Function creates partitions safely

2. **Use Spring Boot scheduler** (development/testing)
   - Application calls function
   - No DDL privileges needed
   - Easy to test and debug

3. **Use pg_cron or external scheduler** (production)
   - Calls same function
   - Runs with owner role
   - Complete separation from application

**Benefits:**
- ✅ Secure in all environments
- ✅ Flexible scheduling options
- ✅ Maintains role separation
- ✅ Easy to test in development
- ✅ Production-ready security

---

## Implementation Plan

### Phase 1: Create SECURITY DEFINER Function (Migration)

**Migration:** `V25__create_partition_management_function.sql`

```sql
-- Create function for partition creation (owned by owner role)
CREATE OR REPLACE FUNCTION create_monthly_partition(
    p_table_name TEXT,
    p_partition_name TEXT,
    p_start_date TIMESTAMPTZ,
    p_end_date TIMESTAMPTZ
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    -- Validate table name (prevent injection)
    IF p_table_name NOT IN ('ezkey_auth_attempt', 'ezkey_audit_log') THEN
        RAISE EXCEPTION 'Invalid table name: %', p_table_name;
    END IF;
    
    -- Check if partition already exists
    IF EXISTS (
        SELECT 1 FROM pg_class 
        WHERE relname = p_partition_name AND relkind = 'r'
    ) THEN
        RAISE NOTICE 'Partition % already exists, skipping', p_partition_name;
        RETURN;
    END IF;
    
    -- Create partition
    EXECUTE format(
        'CREATE TABLE %I PARTITION OF %I FOR VALUES FROM (%L) TO (%L)',
        p_partition_name,
        p_table_name,
        p_start_date,
        p_end_date
    );
    
    RAISE NOTICE 'Created partition: % for table: %', p_partition_name, p_table_name;
END;
$$;

-- Grant EXECUTE to application role
-- Note: Replace EZKEY_app with actual application role name
GRANT EXECUTE ON FUNCTION create_monthly_partition(TEXT, TEXT, TIMESTAMPTZ, TIMESTAMPTZ) TO EZKEY_app;

-- Revoke from public
REVOKE EXECUTE ON FUNCTION create_monthly_partition(TEXT, TEXT, TIMESTAMPTZ, TIMESTAMPTZ) FROM PUBLIC;

-- Add comment
COMMENT ON FUNCTION create_monthly_partition IS 
'Creates monthly partition for partitioned tables. Executes with owner privileges via SECURITY DEFINER. Application role only needs EXECUTE privilege.';
```

### Phase 2: Update Application Service

**Update:** `PartitionSchedulerService.java`

```java
// Use function instead of direct DDL
entityManager.createNativeQuery(
    "SELECT create_monthly_partition(:tableName, :partitionName, :startDate, :endDate)"
)
.setParameter("tableName", tableName)
.setParameter("partitionName", partitionName)
.setParameter("startDate", startDateTime)
.setParameter("endDate", endDateTime)
.executeUpdate();
```

### Phase 3: Production Configuration (Optional)

**For production with pg_cron:**

```sql
-- Schedule via pg_cron (optional, for production)
SELECT cron.schedule(
    'create-monthly-partitions',
    '0 1 * * *',
    $$
    SELECT create_monthly_partition(
        'ezkey_auth_attempt',
        'ezkey_auth_attempt_' || TO_CHAR(DATE_TRUNC('month', CURRENT_TIMESTAMP + INTERVAL '1 month'), 'YYYY_MM'),
        DATE_TRUNC('month', CURRENT_TIMESTAMP + INTERVAL '1 month'),
        DATE_TRUNC('month', CURRENT_TIMESTAMP + INTERVAL '1 month') + INTERVAL '1 month'
    );
    SELECT create_monthly_partition(
        'ezkey_audit_log',
        'ezkey_audit_log_' || TO_CHAR(DATE_TRUNC('month', CURRENT_TIMESTAMP + INTERVAL '1 month'), 'YYYY_MM'),
        DATE_TRUNC('month', CURRENT_TIMESTAMP + INTERVAL '1 month'),
        DATE_TRUNC('month', CURRENT_TIMESTAMP + INTERVAL '1 month') + INTERVAL '1 month'
    );
    $$
);
```

---

## Security Checklist

- [ ] Function created with SECURITY DEFINER
- [ ] Function uses SET search_path = public (prevents injection)
- [ ] Function validates table names (whitelist)
- [ ] Application role only has EXECUTE privilege (not CREATE TABLE)
- [ ] Function ownership set to owner role
- [ ] Public EXECUTE privilege revoked
- [ ] Function calls logged (PostgreSQL logging)
- [ ] Production uses pg_cron or external scheduler (optional but recommended)

---

## Compliance Considerations

### SOC2

- ✅ **CC6.1 (Logical Access Controls):** Application role has minimal privileges
- ✅ **CC6.2 (Access Authorization):** DDL privileges restricted to owner role
- ✅ **CC7.2 (System Monitoring):** Function calls can be audited

### PCI-DSS

- ✅ **Requirement 7:** Restrict access to privileged functions
- ✅ **Requirement 8:** Unique IDs and least privilege

### General Security

- ✅ Principle of Least Privilege maintained
- ✅ Separation of Duties (DDL vs DML)
- ✅ Defense in Depth (function validation + role separation)

---

## Conclusion

**Recommended Approach:** SECURITY DEFINER function (Option 1) with Spring Boot scheduler for development and pg_cron/external scheduler for production.

This approach:
- ✅ Maintains security best practices
- ✅ Supports compliance requirements
- ✅ Works in all environments
- ✅ Provides flexibility for different deployment scenarios

---

## References

- PostgreSQL SECURITY DEFINER: https://www.postgresql.org/docs/current/sql-createfunction.html
- PostgreSQL pg_cron: https://github.com/citusdata/pg_cron
- SOC2 Trust Service Criteria: https://www.aicpa.org/interestareas/frc/assuranceadvisoryservices/trustdataintegritytaskforce.html
- Principle of Least Privilege: https://owasp.org/www-community/Least_Privilege

---

**Document Version:** 1.0  
**Last Updated:** January 2025  
**Status:** Security Analysis Complete  
**Next Steps:** Implement SECURITY DEFINER function and update service

