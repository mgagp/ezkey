# Admin Zero with System Tenant - Option B Implementation

## Overview

This document describes the implementation of **Option B**: Admin Global with System Tenant. This design aligns with Ezkey's positioning for local hosting where each instance belongs to a specific organization.

## Rationale

### Why Option B?

1. **Self-Hosted Focus**: Ezkey is designed for local deployment
   - Each instance = one organization
   - System tenant identifies **who owns this instance**

2. **Clear Semantics**:
   ```
   Tenant = Isolated organization space
   - System Tenant = Organization hosting the instance  
   - Application Tenants = Departments/divisions within the organization
   ```

3. **Future Features**:
   - Organization branding (logo, colors)
   - Custom instance naming
   - Global configuration per organization
   - Multi-instance management in SaaS mode

4. **Audit & Traceability**:
   - `ezkey_tenant.tenant_name = "Acme Corp"` → identifies instance owner
   - `ezkey_admin.tenant_id = 1` → links admin to organization

## Database Changes

### Migration V5 - Allow Global Admin Tenant

**File**: `ezkey-migration/src/main/resources/db/migration/V5__allow_global_admin_tenant.sql`

```sql
-- Drop existing constraint
ALTER TABLE ezkey_admin DROP CONSTRAINT check_admin_hierarchy;

-- Create new constraint allowing optional tenant_id for GLOBAL_ADMIN
ALTER TABLE ezkey_admin ADD CONSTRAINT check_admin_hierarchy CHECK (
    (admin_type = 'GLOBAL_ADMIN' AND integration_id IS NULL) OR  -- tenant_id now optional
    (admin_type = 'TENANT_ADMIN' AND tenant_id IS NOT NULL AND integration_id IS NULL) OR
    (admin_type = 'INTEGRATION_ADMIN' AND tenant_id IS NOT NULL AND integration_id IS NOT NULL)
);

-- Link existing admin zero to system tenant (if exists)
UPDATE ezkey_admin 
SET tenant_id = (SELECT tenant_id FROM ezkey_tenant WHERE tenant_name = 'Ezkey System' LIMIT 1)
WHERE username = 'admin' 
  AND admin_type = 'GLOBAL_ADMIN'
  AND tenant_id IS NULL
  AND EXISTS (SELECT 1 FROM ezkey_tenant WHERE tenant_name = 'Ezkey System');
```

**Key Changes**:
- ✅ GLOBAL_ADMIN can have `tenant_id` (optional)
- ✅ Links existing admin zero to system tenant
- ✅ Adds performance index for queries

## Application Changes

### 1. Organization Configuration Properties

**File**: `ezkey-admin-api/src/main/java/org/ezkey/admin/config/OrganizationProperties.java`

```java
@Component
@ConfigurationProperties(prefix = "ezkey.organization")
public class OrganizationProperties {
    private String name = "Ezkey System";
    private String description = "Default system tenant for global administrators";
    
    // Getters and setters
}
```

**Configuration** (`application.properties`):
```properties
# Organization Configuration
ezkey.organization.name=Acme Corporation
ezkey.organization.description=Acme Corp Ezkey MFA Instance
```

**Purpose**:
- Customizable organization name for the system tenant
- Defaults to "Ezkey System" if not specified
- Used during bootstrap initialization

### 2. Tenant Repository

**File**: `ezkey-core/src/main/java/org/ezkey/integration/domain/repository/TenantRepository.java`

New repository for tenant operations:
- `findByTenantName(String)` - Find tenant by name
- `findByTenantNameAndActiveTrue(String)` - Find active tenant by name
- `existsByTenantName(String)` - Check tenant name uniqueness

### 3. Admin Password Initialization Service

**File**: `ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminPasswordInitializationService.java`

**Enhanced `createAdminZero()` method**:

```java
private void createAdminZero() {
    // Step 1: Create or find system tenant
    Tenant systemTenant = createOrFindSystemTenant();
    
    // Step 2: Create admin zero with placeholder password
    String placeholderHash = "$2a$10$placeholder.defined.at.first.execution";
    EzkeyAdmin adminZero = new EzkeyAdmin("admin", placeholderHash, EzkeyAdmin.AdminType.GLOBAL_ADMIN);
    adminZero.setTenant(systemTenant); // Link to system tenant (Option B)
    
    // Step 3: Save admin zero
    adminRepository.save(adminZero);
    
    // Step 4: Update tenant with creator admin
    systemTenant.setCreatedByAdmin(adminZero);
    tenantRepository.save(systemTenant);
    
    // Step 5: Trigger password initialization
    initializeAdminPassword();
}
```

**New `createOrFindSystemTenant()` method**:

```java
private Tenant createOrFindSystemTenant() {
    String tenantName = organizationProperties.getName();
    String tenantDescription = organizationProperties.getDescription();
    
    // Try to find existing tenant
    Optional<Tenant> existingTenant = tenantRepository.findByTenantName(tenantName);
    if (existingTenant.isPresent()) {
        return existingTenant.get();
    }
    
    // Create new tenant
    Tenant systemTenant = new Tenant(tenantName, tenantDescription);
    tenantRepository.save(systemTenant);
    return systemTenant;
}
```

**Flow**:
1. Check if system tenant exists → return if found
2. Create system tenant with configured name
3. Save admin zero linked to tenant
4. Update tenant with creator admin reference
5. Initialize password and log credentials

## Scenarios

### Scenario 1: Fresh Installation with Flyway

```bash
# Run migrations (V1, V2, V3, V4, V5)
mvn flyway:migrate -pl ezkey-migration

# V3 creates tenant "Ezkey System"
# V3 creates admin with tenant_id=NULL
# V5 links admin to tenant

# Start application
mvn spring-boot:run -pl ezkey-admin-api

# Service detects placeholder → Generates password → Logs
```

**Result**:
- Tenant "Ezkey System" created by V3
- Admin zero linked to tenant by V5
- Password initialized on startup

### Scenario 2: Manual Database Clear

```sql
-- Clear all data
DELETE FROM ezkey_admin_tokens WHERE admin_id = 1;
DELETE FROM ezkey_admin WHERE username = 'admin';
DELETE FROM ezkey_tenant;

-- Start application
```

**Service behavior**:
1. Detects no admin exists
2. Creates system tenant (configured name or default)
3. Creates admin zero linked to tenant
4. Updates tenant with creator admin
5. Generates and logs password

### Scenario 3: Custom Organization Name

**Configuration**:
```properties
ezkey.organization.name=Acme Corporation
ezkey.organization.description=Acme Corp Ezkey MFA Instance
```

**Result**:
```
🏢 Creating system tenant: 'Acme Corporation'
✅ System tenant created successfully - ID: 1, Name: 'Acme Corporation'
✅ Admin zero created successfully - ID: 1, Tenant: Acme Corporation
```

## Data Model

### Before Option B (V2-V4)
```
ezkey_tenant:
  tenant_id=1, tenant_name='Ezkey System', created_by_admin_id=1

ezkey_admin:
  admin_id=1, username='admin', admin_type='GLOBAL_ADMIN', tenant_id=NULL ❌
```

**Problem**: Tenant exists but not linked to admin zero

### After Option B (V5+)
```
ezkey_tenant:
  tenant_id=1, tenant_name='Acme Corporation', created_by_admin_id=1

ezkey_admin:
  admin_id=1, username='admin', admin_type='GLOBAL_ADMIN', tenant_id=1 ✅
```

**Solution**: Admin zero linked to system tenant

## Semantics

### System Tenant Represents

1. **Instance Owner**: The organization hosting this Ezkey instance
2. **Global Scope**: Scope of the global administrator
3. **Branding Context**: Organization identity for UI customization
4. **Audit Root**: Top-level organization for audit trails

### Application Tenants

Created later by global admin:
- **HR Department** → tenant_id=2
- **IT Department** → tenant_id=3
- **Finance** → tenant_id=4

Each with their own tenant admins and integrations.

## Use Cases

### Use Case 1: Single Organization, Multiple Departments

```
Instance: Acme Corp Ezkey
├── Tenant "Acme Corp" (tenant_id=1, system tenant)
│   └── Admin "admin" (GLOBAL_ADMIN) → manages entire instance
├── Tenant "HR Department" (tenant_id=2)
│   ├── Admin "hr_admin" (TENANT_ADMIN)
│   └── Integration "HR Portal" (INTEGRATION_ADMIN)
└── Tenant "IT Department" (tenant_id=3)
    ├── Admin "it_admin" (TENANT_ADMIN)
    └── Integration "VPN Access" (INTEGRATION_ADMIN)
```

### Use Case 2: Multi-Instance SaaS

```
Instance A: Acme Corp
├── Tenant "Acme Corp" (system)
│   └── Admin "admin_acme"
└── [Application tenants for Acme]

Instance B: Beta Inc
├── Tenant "Beta Inc" (system)
│   └── Admin "admin_beta"
└── [Application tenants for Beta]
```

Each instance has its own system tenant identifying the customer.

## Migration Path

### From Current State (V4) to Option B (V5)

1. **Run Migration V5**:
   ```bash
   mvn flyway:migrate -pl ezkey-migration
   ```

2. **V5 automatically**:
   - Drops old constraint
   - Creates new constraint (tenant_id optional for GLOBAL_ADMIN)
   - Links existing admin to system tenant
   - Adds performance index

3. **Restart application**:
   - Service recognizes admin exists with tenant
   - No duplicate creation
   - Normal operation resumes

### Backward Compatibility

- ✅ Existing admins without tenant still work (optional field)
- ✅ New admins created with tenant by default
- ✅ No breaking changes to API or services

## Configuration Examples

### Development (Default)
```properties
ezkey.organization.name=Ezkey System
ezkey.organization.description=Default system tenant for global administrators
```

### Production (Customized)
```properties
ezkey.organization.name=Acme Corporation
ezkey.organization.description=Acme Corp MFA Authentication Instance
```

### Multi-Instance SaaS
```properties
# Instance A
ezkey.organization.name=Customer A
ezkey.organization.description=Customer A Ezkey MFA Instance

# Instance B  
ezkey.organization.name=Customer B
ezkey.organization.description=Customer B Ezkey MFA Instance
```

## Benefits of Option B

1. ✅ **Clear Instance Ownership**: Each instance knows who owns it
2. ✅ **Branding Support**: Organization name/logo for UI customization
3. ✅ **Audit Trail**: Full traceability to organization level
4. ✅ **Multi-Instance Ready**: Supports SaaS deployment model
5. ✅ **Self-Hosted Alignment**: Perfect for local deployment use case
6. ✅ **Consistent Data Model**: No orphan tenants, everything linked

## Testing

### Test 1: Fresh Installation
```bash
# Clean database
psql -d ezkey_db -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"

# Run migrations
mvn flyway:migrate -pl ezkey-migration

# Start application
mvn spring-boot:run -pl ezkey-admin-api

# Verify
psql -d ezkey_db -c "SELECT * FROM ezkey_tenant;"
psql -d ezkey_db -c "SELECT admin_id, username, tenant_id FROM ezkey_admin;"
```

**Expected**:
- Tenant created by V3
- Admin linked to tenant by V5
- Password initialized and logged

### Test 2: Custom Organization Name
```properties
ezkey.organization.name=Test Corp
```

```bash
# Clean admin and tenant
psql -d ezkey_db -c "DELETE FROM ezkey_admin; DELETE FROM ezkey_tenant;"

# Start application
mvn spring-boot:run -pl ezkey-admin-api

# Verify tenant name
psql -d ezkey_db -c "SELECT tenant_name FROM ezkey_tenant WHERE tenant_id = 1;"
```

**Expected**: `tenant_name = 'Test Corp'`

## Documentation Updates

- ✅ `V5__allow_global_admin_tenant.sql` - Migration file
- ✅ `OrganizationProperties.java` - Configuration properties
- ✅ `TenantRepository.java` - Repository for tenant operations  
- ✅ `AdminPasswordInitializationService.java` - Enhanced with tenant creation
- ✅ `application.properties` - Organization configuration
- ✅ `ADMIN_ZERO_OPTION_B_IMPLEMENTATION.md` - This document

## Next Steps

1. **Test migration V5** on development database
2. **Customize organization name** in application.properties
3. **Run integration tests** to verify behavior
4. **Update admin UI** (future) to display organization name
5. **Implement tenant management endpoints** (roadmap)

## Conclusion

Option B provides a **coherent and scalable design** for Ezkey's multi-tenant architecture. It aligns perfectly with the self-hosted positioning while remaining flexible enough for future SaaS deployments.

The system tenant is no longer an orphan—it has a clear purpose: **identifying the organization hosting this Ezkey instance**.

