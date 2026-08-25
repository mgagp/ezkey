# Initial Global Administrator — identity bootstrap

## Overview

Ezkey Admin API automatically initializes the initial global administrator when the database is empty or when a placeholder admin exists. This ensures that the system can always be accessed and managed, even after a fresh installation or database reset.

The first Global Admin must be an **identifiable operator identity**: a username that names a person (not a generic account such as `admin`) plus email, first name, and last name so audit rows can attribute actions. Mapping vocabulary for that discipline lives in [`product-docs/global/normative-posture.md`](../product-docs/global/normative-posture.md); this bootstrap is not a certification claim.

## How It Works

The `InitialGlobalAdminService` runs automatically when the application starts up (before MFA bootstrap). It performs the following checks:

1. **Validate configuration**: Ensures username, email, first name, and last name are configured and the username is not generic
2. **Check if global admin exists**: Looks for admin with configured username
3. **Update placeholder admin**: If placeholder admin (username "admin") exists, updates it with configured credentials
4. **Create new admin**: If no admin exists, creates initial global admin with configured credentials

### Relationship with Flyway Migrations

The initial global admin creation works in tandem with Flyway migrations:

- **V1** (consolidated core domain): Creates placeholder admin with username "admin" (updated by this service)
- **V2** (admin identity columns): Adds email, first name, and last name on `ezkey_admin` (historical slice names in git: `V13__add_admin_email_for_soc2`, `V14` name columns)
- **InitialGlobalAdminService**: Updates placeholder admin with configured identifiable identity

## Configuration (REQUIRED)

The initial global admin must be configured via application properties. Startup **fails closed** if identity is missing or generic:

```properties
# REQUIRED: Username must identify a specific individual (not generic)
ezkey.admin.initial.username=john.doe

# REQUIRED: Email for operator-visible audit trail and accountability
ezkey.admin.initial.email=john.doe@example.com

# REQUIRED: Given name and family name for identifiable Global Admin identity
ezkey.admin.initial.first-name=John
ezkey.admin.initial.last-name=Doe
```

### Identity rules

1. **Username**: Must identify a specific individual
   - Valid: "john.doe", "jane.smith", "admin.john"
   - Invalid: "admin", "administrator", "root", "superuser"

2. **Email**: Required for audit trail and accountability
   - Must be valid email format
   - Must be unique
   - Required for GLOBAL_ADMIN type

3. **First name and last name**: Required for GLOBAL_ADMIN so audit and display can name a person

## Initial Global Admin Properties

When the initial global admin is created or updated, it has the following properties:

- **Username**: Configured via `ezkey.admin.initial.username` (identifiable, not generic)
- **Email**: Configured via `ezkey.admin.initial.email`
- **First name / last name**: Configured via `ezkey.admin.initial.first-name` / `last-name`
- **Admin Type**: `GLOBAL_ADMIN` (full system access)
- **Tenant**: System tenant (Ezkey System)
- **Passwordless Authentication**: Enabled via System Integration and Global Admin Enrollment
- **Recovery Codes**: 10 single-use codes generated during enrollment
- **Active**: `true`

## Bootstrap Process

On first application startup:

1. **InitialGlobalAdminService** (Order 1):
   - Validates configuration
   - Creates or updates initial global admin with configured identity

2. **AdminBootstrapService** (Order 2):
   - Creates System Integration (for global admin authentication)
   - Creates Global Admin Enrollment (if auto-enrollment enabled)
   - Generates recovery codes
   - Logs enrollment credentials

## Security Considerations

### Identifiable operator identity

The initial global admin configuration enforces individual accountability:

- **Individual Accountability**: Username must identify a specific person
- **Audit Trail**: Email and name required so operators can see who performed actions
- **No Generic Accounts**: System fails to start if username is generic

### Initial Credentials Logging

The enrollment credentials are **logged to the console** during creation. This is intentional for development and initial setup:

```
╔════════════════════════════════════════════════════════════╗
║     GLOBAL ADMIN PASSWORDLESS ENROLLMENT                  ║
╠════════════════════════════════════════════════════════════╣
║  Username: john.doe                                       ║
║  Email: john.doe@example.com                               ║
║                                                            ║
║  ⚠️  IMPORTANT SECURITY NOTICE:                            ║
║  - Save enrollment credentials securely                    ║
║  - Bind enrollment before first login                     ║
║  - Recovery codes are single-use emergency access only    ║
╚════════════════════════════════════════════════════════════╝
```

### Production Deployment

For production deployments:

1. **Configure identifiable username**: Set `ezkey.admin.initial.username` to identify a specific individual
2. **Configure email and name**: Set `ezkey.admin.initial.email`, `first-name`, and `last-name`
3. **Configure logging**: Set logging level appropriately for production
4. **Save credentials**: Store enrollment credentials securely (password manager)
5. **Bind enrollment**: Complete enrollment binding before first login
6. **Monitor admin creation**: Set up alerts for admin creation events

## MFA Enrollment Bootstrap

If `ezkey.admin.mfa.bootstrap.enabled=true`, the Admin API also bootstraps System Integration and Global Admin Enrollment for the Ezkey mobile application.

- Startup logs include an **ASCII QR code** containing `enrollmentId|enrollmentProofToken`
- Scan the QR with the Ezkey Mobile wizard to bind the first device
- The textual values (enrollment ID, proof token, challenge) remain in the logs as a fallback
- Store them securely if you rely on copy/paste

## Passwordless Authentication

Ezkey uses passwordless authentication exclusively. The initial global admin:

- **No password**: Authentication is via cryptographic signatures only
- **Device binding required**: Must bind enrollment before first login
- **Recovery codes**: Single-use emergency access when device is lost
- **Challenge codes**: Optional 6-digit challenge for enhanced security

## Manual Reset

### Option 1: Update Configuration

Change the configuration and restart:

```properties
ezkey.admin.initial.username=new.username
ezkey.admin.initial.email=new.email@example.com
ezkey.admin.initial.first-name=New
ezkey.admin.initial.last-name=Name
```

The service will update the existing admin on next startup.

### Option 2: Complete Reset

Delete admin and let the service recreate it:

```sql
-- Delete tokens first (foreign key constraint)
DELETE FROM ezkey_admin_tokens
WHERE admin_id = (SELECT admin_id FROM ezkey_admin WHERE admin_type = 'GLOBAL_ADMIN' LIMIT 1);

-- Delete global admin
DELETE FROM ezkey_admin WHERE admin_type = 'GLOBAL_ADMIN';

-- Restart application - admin will be recreated with configured credentials
```

### Option 3: Clean Database with Flyway

For a complete fresh start:

```bash
# Run Flyway clean (removes all objects)
mvn flyway:clean -pl ezkey-migration

# Run all migrations
mvn flyway:migrate -pl ezkey-migration

# Start application — placeholder admin is created, then this service updates it
```

## Troubleshooting

### Configuration Validation Failed

**Problem**: Application fails to start with "Initial global admin username is REQUIRED"

**Solution**:
- Set `ezkey.admin.initial.username` in application.properties
- Ensure username is not generic (not "admin", "administrator", etc.)
- Set `ezkey.admin.initial.email`, `first-name`, and `last-name`

### Generic Username Error

**Problem**: Application fails with "Username cannot be a generic identifier"

**Solution**:
- Change `ezkey.admin.initial.username` to identify a specific individual
- Examples: "john.doe", "jane.smith", "admin.john"
- Cannot be: "admin", "administrator", "root", "superuser"

### Email Validation Failed

**Problem**: Application fails with "Initial global admin email is REQUIRED"

**Solution**:
- Set `ezkey.admin.initial.email` in application.properties
- Ensure email is valid format (e.g., "user@example.com")
- Email is required so audit rows can name a person

### Global Admin Not Found

**Problem**: Bootstrap service fails with "Initial global admin not found"

**Possible causes**:
1. InitialGlobalAdminService failed to create/update admin
2. Username mismatch between configuration and database
3. Database connection issues

**Solution**:
- Check application logs for InitialGlobalAdminService errors
- Verify configuration is correct
- Check database connectivity
- Ensure migrations have run

## Related Files

- **Service**: `org.ezkey.admin.service.InitialGlobalAdminService`
- **Configuration**: `org.ezkey.admin.config.InitialGlobalAdminProperties`
- **Entity**: `org.ezkey.integration.domain.entity.EzkeyAdmin`
- **Repository**: `org.ezkey.integration.domain.repository.EzkeyAdminRepository`

## "Eat Your Own Dog Food" Integration

The initial global admin is secured with Ezkey's own MFA solution. This follows the "eat your own dog food" principle where Ezkey uses its own authentication system to secure its admin API.

**Implementation**:
1. Initial global admin created with identifiable username, email, and name
2. System Integration created for global admin authentication
3. Global Admin Enrollment created with device keys
4. Passwordless authentication enabled
5. Recovery codes generated for emergency access

## See Also

- [Rate Limiting Documentation](README_RATE_LIMITING.md)
- [Multi-Tenant Security](../docs/features/SECURITY_MULTI_TENANT.md)
- [Normative posture](../product-docs/global/normative-posture.md)
