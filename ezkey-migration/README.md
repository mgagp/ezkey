# Ezkey Database Migrations

**Migration Tool:** Flyway  
**Database:** PostgreSQL 17+  
**Version:** 2.0 (Passwordless-Only)

---

## Overview

Ezkey uses Flyway for database schema versioning and migration management. Migrations are organized by logical subject for clarity and maintainability.

---

## Migration Structure

### Current Migrations (V1-V3)

| Version | Name | Purpose | Lines | Status |
|---------|------|---------|-------|--------|
| **V1** | `initial_schema` | Core tables (enrollments, auth attempts, integrations) | 108 | ✅ Stable |
| **V2** | `add_multi_tenant_security` | Multi-tenant tables (tenants, admins with password schema, tokens) | 79 | ✅ Stable |
| **V3** | `create_system_tenant_and_admin_zero` | Transform to passwordless schema + system tenant + initial global admin | 150 | ✅ Stable |
| **V13** | `add_admin_email_for_soc2` | Add email column to ezkey_admin for SOC 2 compliance | 70 | ✅ Stable |

**Total:** 3 migrations, ~337 lines

**Note:** V3 consolidates what was originally V3-V9 in the planning phase:
- Removes password infrastructure (`password_hash`, `mfa_enabled`, `mfa_required`, `password_change_required`)
- Adds passwordless infrastructure (`challenge_required`, `recovery_codes`)
- Drops `ezkey_admin_temp_tokens` table
- Creates system tenant "Ezkey System"
- Creates initial global admin with placeholder username (bootstrap service updates with configured credentials)

**V13:** Adds email column for SOC 2 compliance:
- Adds `email` column to `ezkey_admin` table
- Required for GLOBAL_ADMIN type (SOC 2 CC6.1, CC7.2)
- Unique constraint on email
- Email format validation

---

## Migration Details

### V1: Initial Schema

**Purpose:** Establish core Ezkey tables

**Tables Created:**
- `ezkey_integration` - Applications using Ezkey
- `ezkey_enrollment` - User-device bindings
- `ezkey_auth_attempt` - Authentication requests
- `ezkey_tenant` - Multi-tenant isolation
- `ezkey_admin` - Administrative users (passwordless schema)
- `ezkey_admin_tokens` - Bearer tokens for API access

**Key Features:**
- Ed25519 cryptographic keys for integrations
- Enrollment proof tokens for device binding
- Auth attempt proof tokens for authentication
- Challenge support (6-digit codes)
- Admin types: GLOBAL_ADMIN, TENANT_ADMIN, INTEGRATION_ADMIN

### V2: Multi-Tenant Security

**Purpose:** Add tenant isolation and admin hierarchy

**Tables Created:**
- `ezkey_tenant` - Multi-tenant isolation
- `ezkey_admin` - Admin users (with password-based schema initially)
- `ezkey_admin_tokens` - Bearer tokens for API access
- `ezkey_admin_temp_tokens` - Temporary MFA tokens (removed in V3)

**Enhancements:**
- Foreign keys linking admins to tenants
- Tenant assignment for integrations
- Admin hierarchy constraints
- Indexes for performance

**Admin Types:**
- **GLOBAL_ADMIN:** System-wide access
- **TENANT_ADMIN:** Tenant-scoped access
- **INTEGRATION_ADMIN:** Integration-scoped access

### V3: Passwordless Schema Transformation + Initial Global Admin

**Purpose:** Transform to passwordless-only authentication and create initial global admin

**Schema Transformations:**
- **REMOVED:** `password_hash`, `mfa_enabled`, `mfa_required`, `password_change_required`, `last_password_change`
- **ADDED:** `challenge_required` (6-digit challenge support), `recovery_codes` (emergency access)
- **DROPPED:** `ezkey_admin_temp_tokens` table (no longer needed)

**Data Created:**
- System tenant "Ezkey System" (default description: deployment-oriented MFA instance blurb; hosts global administrators)
- Initial global admin account (username: `admin` placeholder, passwordless-ready)
- Updated admin hierarchy constraint (allows GLOBAL_ADMIN to have tenant_id)

**Bootstrap Completion:**
Bootstrap services will complete initial global admin setup on first startup:
- InitialGlobalAdminService: Update admin with configured username and email (SOC 2 compliance)
- AdminBootstrapService: Create system integration (for global admin authentication)
- AdminBootstrapService: Create global admin enrollment (device binding)
- Generate 10 recovery codes (32-digit, 106-bit entropy)
- Display credentials in logs (one-time opportunity)

### V13: Add Email Column for SOC 2 Compliance

**Purpose:** Add email column to ezkey_admin table for SOC 2 compliance requirements

**Schema Changes:**
- **ADDED:** `email` column (VARCHAR(255), nullable, unique)
- **ADDED:** Email format validation constraint
- **ADDED:** Unique constraint on email

**SOC 2 Requirements:**
- Email required for GLOBAL_ADMIN type (CC6.1, CC7.2)
- Enables proper audit trail and accountability
- Individual identification (not generic accounts)

---

## Migration Strategy

### Consolidation Approach

**Original Plan:** V3-V9 separate migrations for passwordless transformation
**Final Implementation:** V3 consolidates all passwordless changes into single migration

**Benefits:**
- ✅ **Simpler deployment** - Single migration handles complete transformation
- ✅ **Atomic operation** - All passwordless changes applied together
- ✅ **Easier rollback** - Single point of failure/recovery
- ✅ **Cleaner history** - Fewer migration files to maintain

### Responsibility Split

#### Flyway Migrations (Structural)

**What belongs in migrations:**
- Table schemas (CREATE TABLE)
- Column definitions (data types, constraints)
- Foreign keys and indexes
- Structural data (system tenant, initial global admin placeholder)
- Schema evolution (ALTER TABLE, DROP TABLE)
- Data transformation (password → passwordless)

**What does NOT belong:**
- Dynamic secrets (recovery codes, crypto keys)
- Environment-specific data (configurable names, usernames, emails)
- Runtime-generated data (enrollments)

#### Bootstrap Services (Dynamic)

**InitialGlobalAdminService (Order 1):**
- Update placeholder admin with configured username and email (SOC 2 compliance)
- Validate configuration meets SOC 2 requirements

**AdminBootstrapService (Order 2):**
- Generate recovery codes (10 × 32-digit, 106-bit entropy)
- Create system integration (for global admin authentication)
- Create global admin enrollment (device binding)
- Generate cryptographic key pairs
- Link global admin to enrollment
- Display initial credentials in logs

**Bootstrap Execution:**
- Runs automatically on first application startup
- Detects missing global admin enrollment
- Creates complete passwordless infrastructure
- Logs credentials for one-time capture

---

## Migration History (Pre-Consolidation)

**Note:** Versions V4-V9 were consolidated into V3 (October 2025)

**Original Evolution:**
- V3: Create admin with password
- V4: Reset password (test iteration)
- V5: Allow global admin tenant
- V6: Add passwordless columns
- V7: Add recovery codes
- V8: Deprecate passwords
- V9: Remove passwords

**Rationale for Consolidation:**
- No production deployments exist yet
- Simplified story: Passwordless from day 1
- Removed password infrastructure artifacts
- Clearer migration purpose per version

**Impact:** Developers must reset database when pulling latest code.

---

## Migration Validation

### Schema Consistency Check

**All migrations are validated for:**
- ✅ **English documentation** - All comments and descriptions in English
- ✅ **Comprehensive comments** - Every table and column documented
- ✅ **Consistent naming** - Follows project conventions
- ✅ **Foreign key integrity** - All relationships properly defined
- ✅ **Index optimization** - Performance indexes for common queries
- ✅ **Constraint validation** - Business rules enforced at database level

### Testing Process

**Migration Testing:**
1. **Clean database** - Start with empty PostgreSQL instance
2. **Run all migrations** - Execute V1 → V2 → V3 in sequence
3. **Validate schema** - Verify all tables, columns, constraints created
4. **Test data insertion** - Confirm initial global admin and system tenant created
5. **Bootstrap validation** - Verify bootstrap service can complete setup

**Result:** ✅ All migrations pass validation and create consistent passwordless schema

---

## Running Migrations

### Fresh Installation

```bash
# 1. Ensure PostgreSQL is running (Docker or native)
docker ps  # Verify postgres container

# 2. Ensure database exists
# Database: ezkey_db
# User: postgres
# Password: ezkey (configurable)

# 3. Run migrations
cd ezkey-migration
mvn spring-boot:run

# Expected output:
# Successfully applied 3 migrations to schema "public", now at version v3
```

### Migration Reset (Development)

**When needed:**
- After pulling consolidated migrations
- When Flyway checksum fails
- When testing clean install

**Command:**
```sql
-- Connect to PostgreSQL
docker exec -it [postgres-container] psql -U postgres -d ezkey_db

-- Reset schema
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;

-- Exit psql
\q
```

Then run migrations again.

### Verifying Migration State

**Check Flyway history:**
```sql
SELECT version, description, installed_on, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

**Expected result:**
```
version | description                              | success
--------+------------------------------------------+---------
1       | initial schema                           | true
2       | add multi tenant security                | true
3       | create system tenant and admin zero      | true
```

**Verify initial global admin:**
```sql
SELECT admin_id, username, email, admin_type, 
       tenant_id IS NOT NULL as has_tenant,
       challenge_required,
       mfa_enrollment_id IS NULL as needs_bootstrap
FROM ezkey_admin
WHERE admin_type = 'GLOBAL_ADMIN';
```

**Expected (after InitialGlobalAdminService):**
- username: configured username (e.g., 'john.doe', not 'admin')
- email: configured email (e.g., 'john.doe@example.com')
- admin_type: 'GLOBAL_ADMIN'
- has_tenant: true (linked to system tenant)
- challenge_required: false
- needs_bootstrap: true (before MFA bootstrap runs)

---

## Bootstrap Integration

### Bootstrap Service Behavior

After V3 migration completes, bootstrap services run on first Admin API startup:

**InitialGlobalAdminService (Order 1) - SOC 2 Compliance:**
```java
// Validate configuration
validateConfiguration(); // Ensures username and email are configured

// Find or update placeholder admin
Optional<EzkeyAdmin> placeholderAdmin = adminRepository.findByUsername("admin");
if (placeholderAdmin.isPresent()) {
    // Update placeholder with configured credentials
    admin.setUsername(configuredUsername);
    admin.setEmail(configuredEmail);
    adminRepository.save(admin);
}
```

**AdminBootstrapService (Order 2) - MFA Infrastructure:**
```java
// Check if global admin exists (using configured username)
Optional<EzkeyAdmin> globalAdmin = adminRepository.findByUsername(configuredUsername);

if (globalAdmin.isEmpty()) {
    throw new RuntimeException("Initial global admin not found");
}

// Check if admin already has enrollment
if (globalAdmin.get().getMfaEnrollment() != null) {
    logger.info("Global admin already bootstrapped");
    return;
}

// Complete bootstrap: system integration + enrollment + recovery codes
createSystemIntegration();
createGlobalAdminEnrollment();
generateRecoveryCodes();
linkAdminToEnrollment();
logCredentials();
```

**Bootstrap Output (First Run):**
```
================================================================================
📱 GLOBAL ADMIN PASSWORDLESS ENROLLMENT - SAVE CREDENTIALS NOW!
================================================================================

✅ Global Admin Created: john.doe (john.doe@example.com)
✅ System Integration created: Ezkey System Admin
✅ Global Admin Enrollment created: Global Admin MFA (ID: 1)

🔐 ENROLLMENT CREDENTIALS:
   Enrollment ID: 1
   Enrollment Proof Token: abc123xyz...def789
   Challenge Code: 654321

🔑 RECOVERY CODES (106-BIT ENTROPY):
   1. 1234-5678-9012-3456-7890-1234-5678-9012
   ... (10 codes total)
================================================================================
```

**Bootstrap Output (Subsequent Runs):**
```
✅ Global admin already has enrollment (ID: 1)
✅ Passwordless defaults configured for global admin
```

---

## Migration Best Practices

### 1. Version Numbering

- Use sequential integers: V1, V2, V3...
- Never reuse version numbers
- Never modify applied migrations (checksum verification)

### 2. Migration Content

**Do:**
- ✅ Create tables and columns
- ✅ Add indexes and constraints
- ✅ Insert structural data (system tenant)
- ✅ Add documentation comments

**Don't:**
- ❌ Insert secrets (passwords, keys, recovery codes)
- ❌ Insert user data (belongs in bootstrap/seed)
- ❌ Add environment-specific values
- ❌ Include test-only changes

### 3. Testing Migrations

**Before committing:**
1. Reset database completely
2. Run migrations from V1
3. Verify all constraints work
4. Run bootstrap service
5. Test application functionality
6. Check for SQL errors in logs

### 4. Rollback Strategy

**Flyway doesn't support automatic rollback for versioned migrations.**

**Manual rollback:**
```sql
-- Drop schema and reapply up to previous version
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;

-- Manually apply V1, V2 (skip V3)
-- Or use Flyway target: mvn flyway:migrate -Dflyway.target=2
```

---

## Troubleshooting

### "Checksum mismatch for migration V3"

**Cause:** V3 was modified after being applied

**Solution:**
```sql
-- Option 1: Reset database (recommended for development)
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;

-- Option 2: Repair Flyway (DANGEROUS - only if you understand implications)
mvn flyway:repair
```

### "Admin zero not found after V3"

**Cause:** V3 INSERT failed

**Check:**
```sql
SELECT * FROM ezkey_admin WHERE username = 'admin';
SELECT * FROM flyway_schema_history WHERE version = '3';
```

**Solution:** Check migration logs for SQL errors, verify constraints

### "Bootstrap creates duplicate admin"

**Cause:** Bootstrap logic doesn't detect V3-created admin

**Fix:** Review `AdminBootstrapService.findAdminZero()` logic

---

## Future Migrations

**V4 and beyond:** Available for future features

**Examples of future migrations:**
- V4: Add admin audit log table
- V5: Add multi-device support for admins
- V6: Add TOTP backup authentication
- V7: Add admin session management

**Naming Convention:**
```
V{number}__{descriptive_snake_case_name}.sql

Examples:
V4__add_admin_audit_log.sql
V5__add_multi_device_admin_support.sql
```

---

## References

- **Official Flyway Documentation:** https://flywaydb.org/documentation/
- **Ezkey Admin Security Guide:** `docs/ADMIN_API_SECURITY_GUIDE.md`
- **Architecture Overview:** `docs/ARCHITECTURE.md`
- **Migration Archive:** `docs/plan/README_ARCHIVE.md` (V3-V9 evolution history)

---

**Last Updated:** October 13, 2025  
**Contributors:** Ezkey Team
