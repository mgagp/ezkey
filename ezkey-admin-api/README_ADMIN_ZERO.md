# Admin Zero - Automatic Bootstrap

## Overview

Ezkey Admin API automatically creates an initial administrator (admin zero) when the database is empty. This ensures that the system can always be accessed and managed, even after a fresh installation or database reset.

## How It Works

The `AdminPasswordInitializationService` runs automatically when the application starts up. It performs the following checks:

1. **Check if admin user exists**: Looks for a user with username "admin"
2. **Create admin zero if missing**: Creates the initial administrator with **placeholder password** (same as V3 migration)
3. **Initialize placeholder passwords**: If password hash equals `$2a$10$placeholder.defined.at.first.execution`, generates secure random password and logs it

### Relationship with Flyway Migrations

The admin zero creation works in tandem with Flyway migrations:

- **V3__create_initial_admin.sql**: Creates admin zero with placeholder password (normal flow)
- **V4__reset_admin_placeholder.sql**: Resets password to placeholder for testing
- **AdminPasswordInitializationService**: Fallback mechanism when migrations haven't run or DB was manually cleared

The service ensures consistency by using the **same placeholder hash** and **same MFA settings** as V3 migration.

## Admin Zero Properties

When admin zero is created (either by V3 migration or service fallback), it has the following properties:

- **Username**: `admin`
- **Password Hash**: `$2a$10$placeholder.defined.at.first.execution` (placeholder, replaced on first startup)
- **Admin Type**: `GLOBAL_ADMIN` (full system access)
- **Tenant ID**: `NULL` (global admins don't belong to a tenant)
- **Password Change Required**: `true` (must be changed on first login)
- **MFA Enabled**: `true` (enabled, but no enrollment initially)
- **MFA Required**: `true` (required for security, but deferred until enrollment setup)
- **Active**: `true`

### After Initialization

Once the placeholder password is detected, the service:
1. Generates a secure random UUID-based password (32 characters)
2. Hashes it with BCrypt
3. Updates the admin record
4. **Logs the password to console** for first login

## Security Considerations

### Initial Credentials Logging

The initial password is **logged to the console** during creation. This is intentional for development and initial setup, but should be configured differently in production:

```
╔════════════════════════════════════════════════════════════╗
║           ADMIN ZERO INITIAL CREDENTIALS                  ║
╠════════════════════════════════════════════════════════════╣
║  Username: admin                                           ║
║  Password: <randomly-generated-password>                   ║
║                                                            ║
║  ⚠️  IMPORTANT SECURITY NOTICE:                            ║
║  - Change this password immediately after first login      ║
║  - This password will be marked as requiring change        ║
║  - MFA is currently DISABLED for initial setup            ║
╚════════════════════════════════════════════════════════════╝
```

### Production Deployment

For production deployments:

1. **Configure logging**: Set logging level to ERROR or WARN for production to avoid password exposure
2. **Change password immediately**: After first login, change the password to a secure value
3. **Enable MFA**: Configure MFA for the admin user as soon as possible
4. **Monitor admin creation**: Set up alerts for admin zero creation events

## MFA Enrollment Bootstrap (ASCII QR)

If `ezkey.admin.mfa.bootstrap.enabled=true`, the Admin API also bootstraps Integration Zero and Enrollment Zero for the Ezkey mobile application.

- Startup logs now include an **ASCII QR code** containing `enrollmentId|enrollmentProofToken`.
- Scan the QR with the Ezkey Mobile wizard (step “Scan the QR code”) to bind the first device without typing the credentials.
- The textual values (enrollment ID, proof token, challenge) remain in the logs as a fallback; store them securely if you rely on copy/paste.
- Once the enrollment is bound, disable or rotate bootstrap credentials as part of your hardening checklist.

## Password Requirements

Admin zero is created with a secure password:

- **Length**: 32 characters (UUID without hyphens)
- **Randomness**: Cryptographically secure using Java's UUID.randomUUID()
- **Format**: Alphanumeric (0-9, a-f)
- **Hashing**: BCrypt with default strength (10 rounds)

## Password Change Flow

After first login with admin zero credentials:

1. Login with username `admin` and generated password
2. System detects `password_change_required` flag
3. User must change password before accessing other features
4. New password is validated and hashed
5. `password_change_required` flag is cleared
6. Full system access is granted

## Manual Reset

### Option 1: Reset Password Only (using V4 migration)

Run the V4 migration manually to reset password to placeholder:

```sql
-- Reset password to placeholder (same as V4__reset_admin_placeholder.sql)
UPDATE ezkey_admin 
SET password_hash = '$2a$10$placeholder.defined.at.first.execution',
    password_change_required = true
WHERE username = 'admin';

-- Restart application - new password will be generated and logged
```

### Option 2: Complete Reset (recreate admin zero)

Delete admin and let the service recreate it:

```sql
-- Delete tokens first (foreign key constraint)
DELETE FROM ezkey_admin_tokens 
WHERE admin_id = (SELECT admin_id FROM ezkey_admin WHERE username = 'admin');

-- Delete admin
DELETE FROM ezkey_admin WHERE username = 'admin';

-- Restart application - admin will be recreated with placeholder then initialized
```

### Option 3: Clean Database with Flyway

For a complete fresh start:

```bash
# Run Flyway clean (removes all objects)
mvn flyway:clean -pl ezkey-migration

# Run all migrations (V1, V2, V3, V4)
mvn flyway:migrate -pl ezkey-migration

# Start application - V3 creates admin with placeholder, service initializes it
```

## Troubleshooting

### Admin zero not created

**Problem**: Application starts but no admin zero is created

**Possible causes**:
1. Database connection issues
2. Table `ezkey_admin` doesn't exist
3. JPA configuration issues

**Solution**:
- Check database connectivity
- Run Flyway migrations to create tables
- Check application logs for errors

### Password not logged

**Problem**: Admin zero is created but password is not shown in logs

**Possible causes**:
1. Logging level is too high (ERROR only)
2. Log output is redirected

**Solution**:
- Set logging level to DEBUG or INFO
- Check application.properties: `logging.level.org.ezkey.admin=INFO`

### Cannot login with generated password

**Problem**: Password doesn't work for login

**Possible causes**:
1. Password copied incorrectly (extra spaces, line breaks)
2. Password change required but not implemented in UI

**Solution**:
- Copy password exactly as shown (no spaces)
- Ensure password change endpoint is implemented
- Reset admin zero if needed

## Related Files

- **Service**: `org.ezkey.admin.service.AdminPasswordInitializationService`
- **Entity**: `org.ezkey.integration.domain.entity.EzkeyAdmin`
- **Repository**: `org.ezkey.integration.domain.repository.EzkeyAdminRepository`
- **Migration**: `V1__create_admin_tables.sql` (in ezkey-migration module)

## "Eat Your Own Dog Food" Integration

Admin zero is the bootstrap user that will eventually be secured with Ezkey's own MFA solution. This follows the "eat your own dog food" principle where Ezkey uses its own authentication system to secure its admin API.

**Roadmap**:
1. ✅ Create admin zero with password authentication
2. 🔄 Implement password change endpoint (Phase 2)
3. 📅 Enable MFA for admin zero using Integration Zero (Phase 4)
4. 📅 Require MFA for all admin operations (Phase 4)

## See Also

- [Admin API Security Plan](../plan.md)
- [Rate Limiting Documentation](README_RATE_LIMITING.md)
- [Multi-Tenant Security](../docs/features/SECURITY_MULTI_TENANT.md)

