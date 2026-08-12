# Multi-Tenancy Strategy Analysis - Ezkey

> **Historical analysis (Jan 2025).** Broader Org/Unit/Party hierarchy ideas in this file were **not**
> adopted. **Shipped Phase 1** is the flat GlobalAdmin / TenantAdmin / API-key model — see
> [`docs/features/SECURITY_MULTI_TENANT.md`](../features/SECURITY_MULTI_TENANT.md),
> [`docs/LIFECYCLE_GOVERNANCE.md`](../LIFECYCLE_GOVERNANCE.md), and
> [`ezkey-tests/reference/MULTI_TENANT.md`](../../ezkey-tests/reference/MULTI_TENANT.md). Do not
> treat Org/Unit sections below as living product direction.

**Version:** 1.0  
**Date:** January 2025  
**Status:** Historical analysis (superseded for execution by Phase 1 ship)  
**Purpose:** Original comprehensive multi-tenancy strategy exploration for Ezkey

---

## Document Context

This analysis has been prepared after comprehensive review of:

- ✅ **Project Documentation**: README.md, PRD.md, ENDPOINT.md
- ✅ **Current Database Schema**: Migrations V1-V13, entity relationships
- ✅ **API Endpoints**: Complete Admin API and Auth API endpoint documentation
- ✅ **Security Implementation**: Admin API security guide, authentication flows
- ✅ **Compliance Requirements**: SOC 2 controls, privacy law considerations

The proposed strategy aligns with Ezkey's existing architecture, API patterns, and operational requirements while introducing enhanced multi-tenancy capabilities.

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Current State Analysis](#current-state-analysis)
3. [Proposed Tenant Hierarchy](#proposed-tenant-hierarchy)
4. [Administrator Types and Responsibilities](#administrator-types-and-responsibilities)
5. [Capability Matrix](#capability-matrix)
6. [Scope and Delegation Analysis](#scope-and-delegation-analysis)
7. [Traceability and Audit Considerations](#traceability-and-audit-considerations)
8. [SOC 2 Compliance Considerations](#soc-2-compliance-considerations)
9. [Personal Information Management](#personal-information-management)
10. [Privacy Compliance: Loi 25 Québec](#privacy-compliance-loi-25-québec)
11. [Implementation Recommendations](#implementation-recommendations)
12. [Visual Representations](#visual-representations)
13. [Next Steps](#next-steps)

---

## Executive Summary

### Current State

Ezkey currently implements a simple two-level access model:

1. **Global Admin**: System-wide technical administration
   - Full access to all system resources
   - Can create integrations, enrollments, auth attempts
   - Can manage all administrative functions
   - Uses passwordless authentication (Ezkey's own MFA system)

2. **API Key (Integration-level)**: Machine-to-machine authentication
   - Restricted to specific integration
   - Can create authentication attempts (`POST /api/v1/auth-attempts`)
   - Can view authentication attempt status (`GET /api/v1/auth-attempts/{id}`)
   - Can wait for authentication completion (`GET /api/v1/auth-attempts/{id}/wait`)
   - Cannot list or delete auth attempts
   - Cannot manage enrollments
   - Uses HTTP Basic Authentication (integration_key:secret_key)

**Note**: There is currently **no Tenant Admin** or **Integration Admin** level. The API Key provides integration-scoped access but is not an administrator role.

### Proposed Evolution

This document proposes an expanded hierarchy that better aligns with enterprise organizational structures while maintaining Ezkey's core principles of **80% pragmatism and 20% complexity**. The new structure introduces:

1. **Organization-level tenants** for enterprise customers
2. **Unit-level tenants** for departments or business units within organizations
3. **Integration-level administrators** with full management capabilities (beyond API Key)
4. **Enhanced delegation capabilities** with proper scope control
5. **Improved traceability** for compliance and audit requirements
6. **Personal information management** with support for administrators with multiple roles
7. **Privacy compliance** considerations (SOC 2, Loi 25 Québec)

### Key Design Principles

- **Separation of Concerns**: Technical administration vs. business operations
- **Least Privilege**: Administrators have minimum necessary permissions
- **Delegation Support**: Higher-level admins can create and manage lower-level admins
- **Audit Trail**: All actions are traceable with proper attribution
- **SOC 2 Compliance**: Design supports compliance requirements from the start
- **Privacy by Design**: Minimal personal information collection aligned with operational needs
- **Multi-Role Support**: Same individual can have multiple administrative roles

---

## Current State Analysis

### Existing Architecture

The current Ezkey implementation includes:

```mermaid
erDiagram
    Tenant ||--o{ Integration : "has"
    Tenant ||--o{ EzkeyAdmin : "has"
    Integration ||--o{ Enrollment : "has"
    Integration ||--o{ ApiKey : "has"
    
    Tenant {
        int tenant_id PK
        string tenant_name
        string tenant_description
        int created_by_admin_id FK
        boolean active
    }
    
    EzkeyAdmin {
        int admin_id PK
        string username
        enum admin_type
        int tenant_id FK
        int integration_id FK
    }
    
    Integration {
        int integration_id PK
        int tenant_id FK
        string name
        boolean active
    }
    
    ApiKey {
        int api_key_id PK
        int integration_id FK
        string integration_key
        string secret_key
        boolean active
    }
```

**Note**: The `ezkey_admin` table has `admin_type` enum with values `GLOBAL_ADMIN`, `TENANT_ADMIN`, `INTEGRATION_ADMIN`, but currently only `GLOBAL_ADMIN` is used. The tenant and integration admin types exist in the schema but are not actively used in the current implementation.

### Current Access Levels

| Type | Scope | Current Capabilities | Authentication |
|------|-------|---------------------|---------------|
| **GLOBAL_ADMIN** | System-wide | Full access: create/manage integrations, enrollments, auth attempts, API keys, tenants | Passwordless (Ezkey MFA) |
| **API Key** | Single integration | Limited: create auth attempts, view status, wait for completion | HTTP Basic Auth (integration_key:secret_key) |

**Key Distinction**:
- **Global Admin** = Human administrator with full capabilities
- **API Key** = Machine-to-machine authentication, not an administrator role

### Limitations of Current Design

1. **Two-Level Only**: Only Global Admin and API Key - no intermediate administrative levels
2. **No Delegation**: Global Admin is the only administrator type - no way to delegate to tenant or integration admins
3. **Flat Tenant Structure**: No support for organizational hierarchies (e.g., Company → Department → Application)
4. **API Key Limitations**: API Keys can only create/wait for auth attempts - cannot manage enrollments or list/delete attempts
5. **No Multi-Tenant Admin**: Cannot have tenant-specific or integration-specific administrators
6. **Scope Ambiguity**: All administrative operations require Global Admin access
7. **Traceability Gaps**: No way to attribute actions to tenant or integration-level administrators

---

## Proposed Tenant Hierarchy

**⚠️ IMPORTANT**: This section describes a **proposed evolution** of Ezkey's multi-tenancy model. 

**Current Reality**:
- Only **Global Admin** exists as an administrator type
- **API Keys** provide integration-scoped machine-to-machine authentication
- No tenant-level or integration-level administrators exist
- All administrative operations require Global Admin access

**Proposed Enhancement**:
This hierarchy would be introduced as part of a future enhancement to support enterprise organizational structures while maintaining Ezkey's 80/20 pragmatism principle.

### Proposed Four-Level Hierarchy

```mermaid
graph TB
    subgraph "Level 1: Global"
        GA[Global Admin<br/>Technical Administration]
    end
    
    subgraph "Level 2: Organization"
        OA1[Organization Admin<br/>Company A]
        OA2[Organization Admin<br/>Company B]
    end
    
    subgraph "Level 3: Unit"
        UA1[Unit Admin<br/>Consumer Portal]
        UA2[Unit Admin<br/>Admin Console]
        UA3[Unit Admin<br/>Internal Tools]
    end
    
    subgraph "Level 4: Integration"
        IA1[Integration Admin<br/>Portal Web App]
        IA2[Integration Admin<br/>Portal Mobile]
        IA3[Integration Admin<br/>Admin Web]
        IA4[Integration Admin<br/>Admin API]
    end
    
    subgraph "Level 5: API Key"
        AK1[API Key<br/>M2M Auth]
        AK2[API Key<br/>M2M Auth]
    end
    
    GA --> OA1
    GA --> OA2
    OA1 --> UA1
    OA1 --> UA2
    OA2 --> UA3
    UA1 --> IA1
    UA1 --> IA2
    UA2 --> IA3
    UA2 --> IA4
    IA1 --> AK1
    IA2 --> AK2
    
    style GA fill:#ffcccc
    style OA1 fill:#ccffcc
    style OA2 fill:#ccffcc
    style UA1 fill:#ccccff
    style UA2 fill:#ccccff
    style UA3 fill:#ccccff
    style IA1 fill:#ffffcc
    style IA2 fill:#ffffcc
    style IA3 fill:#ffffcc
    style IA4 fill:#ffffcc
    style AK1 fill:#ffccff
    style AK2 fill:#ffccff
```

### Tenant Level Definitions

#### Level 1: Global Administration
**Purpose**: Technical system administration  
**Typical Role**: IT Operations, DevOps  
**Responsibilities**:
- System-wide configuration
- Cryptographic key rotation
- Global administrator management
- System monitoring and health
- Database maintenance

**Key Characteristics**:
- No business context
- Technical focus only
- Can create other global admins for redundancy
- Manages organization-level tenants

#### Level 2: Organization Tenant
**Purpose**: Represent a company or organization  
**Typical Role**: Organization IT Manager, CISO  
**Responsibilities**:
- Manage organization-wide settings
- Create and manage unit-level tenants
- Create organization-level administrators
- View organization-wide metrics

**Key Characteristics**:
- Business entity (e.g., "Acme Corp", "TechStart Inc")
- Can contain multiple units
- Organization admins can create peer admins for redundancy

#### Level 3: Unit Tenant
**Purpose**: Represent a department or business unit  
**Typical Role**: Department Manager, Product Owner  
**Responsibilities**:
- Manage unit-level integrations
- Create and manage integration-level administrators
- View unit-level metrics
- Coordinate multiple integrations

**Key Characteristics**:
- Business unit within an organization (e.g., "Consumer Portal", "Admin Console")
- Can contain multiple integrations
- Unit admins can create peer admins for redundancy

#### Level 4: Integration Tenant
**Purpose**: Manage a specific application integration  
**Typical Role**: Application Owner, Integration Manager  
**Responsibilities**:
- Manage enrollments for the integration
- Create and manage auth attempts
- List and delete auth attempts
- Manage API keys for the integration
- View integration metrics

**Key Characteristics**:
- Specific application (e.g., "Portal Web App", "Mobile App")
- Integration admins can create peer admins for redundancy
- Limited to single integration scope

#### Level 5: API Key
**Purpose**: Machine-to-machine authentication  
**Typical Role**: Application Service Account  
**Responsibilities**:
- Create authentication attempts
- Validate authentication attempts
- Read-only access to integration data

**Key Characteristics**:
- Very granular, application-specific
- No administrative capabilities
- Cryptographic authentication only

---

## Administrator Types and Responsibilities

**⚠️ IMPORTANT**: This section describes the **proposed** administrator types for the enhanced multi-tenancy model. 

**Current Reality**:
- Only **Global Admin** exists as an administrator type (fully implemented)
- **API Keys** provide integration-scoped machine-to-machine access (not administrators)
- No Organization Admin, Unit Admin, or Integration Admin types exist currently

**Proposed Enhancement**:
The administrator types described below (Organization Admin, Unit Admin, Integration Admin) would be introduced as part of a future enhancement.

### Proposed Detailed Capability Matrix

| Administrator Type | Can Create Peers | Can Create Lower Level | Can Manage Own Level | Can Operate on Lower Levels | Scope |
|-------------------|------------------|------------------------|---------------------|----------------------------|-------|
| **Global Admin** | ✅ Yes | ✅ Organizations | ✅ Global config | ⚠️ Limited (see below) | System-wide |
| **Organization Admin** | ✅ Yes | ✅ Units | ✅ Organization settings | ⚠️ Limited (see below) | Single organization |
| **Unit Admin** | ✅ Yes | ✅ Integrations | ✅ Unit settings | ⚠️ Limited (see below) | Single unit |
| **Integration Admin** | ✅ Yes | ✅ API Keys | ✅ Integration settings | ✅ Yes (full) | Single integration |
| **API Key** | ❌ No | ❌ No | ❌ No | ✅ Yes (read-only) | Single integration |

### Administrator Type Definitions

#### 1. Global Administrator (`GLOBAL_ADMIN`) - **CURRENTLY IMPLEMENTED**

**Scope**: System-wide technical administration

**Current Capabilities** (Implemented):
- ✅ Create other global administrators (for redundancy)
- ✅ Create tenants
- ✅ Create integrations
- ✅ Create authentication attempts (`POST /api/v1/auth-attempts`)
- ✅ List authentication attempts (`GET /api/v1/auth-attempts`)
- ✅ View authentication attempt details (`GET /api/v1/auth-attempts/{id}`)
- ✅ Wait for authentication completion (`GET /api/v1/auth-attempts/{id}/wait`)
- ✅ Delete authentication attempts (`DELETE /api/v1/auth-attempts/{id}`)
- ✅ Create enrollments (`POST /api/v1/enrollments`)
- ✅ List enrollments (`GET /api/v1/enrollments`)
- ✅ Delete enrollments (`DELETE /api/v1/enrollments/{id}`)
- ✅ Create API keys (`POST /api/v1/api-keys`)
- ✅ Manage API keys (list, revoke)
- ✅ Manage system-wide configuration
- ✅ Rotate cryptographic keys
- ✅ View system-wide metrics

**Proposed Future Capabilities** (if hierarchy is implemented):
- ✅ Create organization tenants
- ✅ Create organization administrators
- ⚠️ **Question**: Should Global Admin directly create auth attempts, or delegate to Integration Admin/API Key? (see [Scope Analysis](#scope-and-delegation-analysis))

**Use Cases**:
- IT operations team managing Ezkey infrastructure
- Security team managing cryptographic keys
- System administrators handling technical maintenance

**SOC 2 Considerations**:
- Must have identifiable username (not "admin")
- Email address required for audit trail
- All actions logged with admin ID

#### 2. Organization Administrator (`ORGANIZATION_ADMIN`)

**Scope**: Single organization (company)

**Capabilities**:
- ✅ Create other organization administrators (for redundancy)
- ✅ Create unit tenants within organization
- ✅ Create unit administrators
- ✅ Manage organization-level settings
- ✅ View organization-wide metrics
- ⚠️ **Limited**: Cannot directly create auth attempts (must use API key or integration admin)

**Use Cases**:
- Company IT manager managing multiple departments
- CISO overseeing security across business units
- Organization-level coordinator

**Example Scenario**:
```
Acme Corp (Organization)
├── Consumer Portal (Unit)
│   ├── Web App (Integration)
│   └── Mobile App (Integration)
└── Admin Console (Unit)
    ├── Web Admin (Integration)
    └── API Admin (Integration)
```

#### 3. Unit Administrator (`UNIT_ADMIN`)

**Scope**: Single unit (department/business unit)

**Capabilities**:
- ✅ Create other unit administrators (for redundancy)
- ✅ Create integrations within unit
- ✅ Create integration administrators
- ✅ Manage unit-level settings
- ✅ View unit-level metrics
- ⚠️ **Question**: Can directly create/delete auth attempts? (see [Scope Analysis](#scope-and-delegation-analysis))

**Use Cases**:
- Department manager coordinating multiple applications
- Product owner managing related integrations
- Business unit coordinator

**Example Scenario**:
```
Consumer Portal (Unit)
├── Web App (Integration)
│   ├── Integration Admin
│   └── API Keys
└── Mobile App (Integration)
    ├── Integration Admin
    └── API Keys
```

#### 4. Integration Administrator (`INTEGRATION_ADMIN`)

**Scope**: Single integration (application)

**Capabilities**:
- ✅ Create other integration administrators (for redundancy)
- ✅ Create API keys for the integration
- ✅ Create authentication attempts (`POST /api/v1/auth-attempts`)
- ✅ List authentication attempts (`GET /api/v1/auth-attempts`)
- ✅ View authentication attempt details (`GET /api/v1/auth-attempts/{id}`)
- ✅ Wait for authentication completion (`GET /api/v1/auth-attempts/{id}/wait`)
- ✅ Delete authentication attempts (`DELETE /api/v1/auth-attempts/{id}`)
- ✅ Create enrollments (`POST /api/v1/enrollments`)
- ✅ List enrollments (`GET /api/v1/enrollments`)
- ✅ View enrollment details (`GET /api/v1/enrollments/{id}`)
- ✅ Delete enrollments (`DELETE /api/v1/enrollments/{id}`)
- ✅ View integration metrics

**Use Cases**:
- Application owner managing specific integration
- Developer managing application's MFA integration
- Integration-specific coordinator

**Difference from API Key**:
- Can list and delete auth attempts (API keys can only create)
- Can manage enrollments (API keys cannot)
- Can use wait API for synchronous authentication flows
- Has full administrative capabilities for the integration

#### 5. API Key (`API_KEY`)

**Scope**: Single integration (application-specific)

**Capabilities**:
- ✅ Create authentication attempts (`POST /api/v1/auth-attempts` with Basic Auth)
- ✅ View authentication attempt status (`GET /api/v1/auth-attempts/{id}`)
- ✅ Wait for authentication completion (`GET /api/v1/auth-attempts/{id}/wait`)
- ❌ Cannot list all auth attempts (`GET /api/v1/auth-attempts` - admin only)
- ❌ Cannot delete auth attempts (`DELETE /api/v1/auth-attempts/{id}` - admin only)
- ❌ Cannot manage enrollments (all enrollment endpoints are admin-only)
- ❌ No administrative capabilities

**Use Cases**:
- Application service account for M2M authentication
- Automated systems creating auth attempts
- Server-to-server authentication flows
- CI/CD pipelines requiring MFA integration

**Key Characteristics**:
- HTTP Basic Authentication (integration_key:secret_key)
- Cryptographic authentication only
- Very granular permissions
- No administrative overhead
- Rate limited: 1000 requests/hour per integration key

---

## Capability Matrix

### Current State (Implemented)

| Operation | Global Admin | API Key |
|-----------|--------------|---------|
| **Administrator Management** |
| Create Global Admin | ✅ | ❌ |
| **Tenant Management** |
| Create Tenant | ✅ | ❌ |
| Create Integration | ✅ | ❌ |
| **Integration Operations** |
| Create Auth Attempt | ✅ (`POST /api/v1/auth-attempts`) | ✅ (`POST /api/v1/auth-attempts` with Basic Auth) |
| List Auth Attempts | ✅ (`GET /api/v1/auth-attempts`) | ❌ |
| View Auth Attempt | ✅ (`GET /api/v1/auth-attempts/{id}`) | ✅ (`GET /api/v1/auth-attempts/{id}`) |
| Wait for Completion | ✅ (`GET /api/v1/auth-attempts/{id}/wait`) | ✅ (`GET /api/v1/auth-attempts/{id}/wait`) |
| Delete Auth Attempt | ✅ (`DELETE /api/v1/auth-attempts/{id}`) | ❌ |
| Create Enrollment | ✅ (`POST /api/v1/enrollments`) | ❌ |
| List Enrollments | ✅ (`GET /api/v1/enrollments`) | ❌ |
| View Enrollment | ✅ (`GET /api/v1/enrollments/{id}`) | ❌ |
| Delete Enrollment | ✅ (`DELETE /api/v1/enrollments/{id}`) | ❌ |
| Create API Keys | ✅ (`POST /api/v1/api-keys`) | ❌ |
| List API Keys | ✅ (`GET /api/v1/api-keys/integration/{id}`) | ❌ |
| Revoke API Keys | ✅ (`DELETE /api/v1/api-keys/{id}`) | ❌ |
| **Monitoring & Metrics** |
| View System Metrics | ✅ | ❌ |

**Key Points**:
- **Global Admin**: Full access to all operations
- **API Key**: Limited to creating auth attempts, viewing status, and waiting for completion
- **API Key cannot**: List/delete auth attempts, manage enrollments, or perform any administrative operations

### Proposed Permission Matrix (Future Enhancement)

| Operation | Global Admin | Organization Admin | Unit Admin | Integration Admin | API Key |
|-----------|--------------|-------------------|------------|------------------|---------|
| **Administrator Management** |
| Create Global Admin | ✅ | ❌ | ❌ | ❌ | ❌ |
| Create Organization Admin | ✅ | ✅ (peers) | ❌ | ❌ | ❌ |
| Create Unit Admin | ✅ | ✅ | ✅ (peers) | ❌ | ❌ |
| Create Integration Admin | ✅ | ✅ | ✅ | ✅ (peers) | ❌ |
| **Tenant Management** |
| Create Organization | ✅ | ❌ | ❌ | ❌ | ❌ |
| Create Unit | ✅ | ✅ | ❌ | ❌ | ❌ |
| Create Integration | ✅ | ✅ | ✅ | ❌ | ❌ |
| **Integration Operations** |
| Create Auth Attempt | ⚠️ Via API Key | ⚠️ Via API Key | ⚠️ **TBD** | ✅ | ✅ |
| List Auth Attempts | ⚠️ **TBD** | ⚠️ **TBD** | ⚠️ **TBD** | ✅ | ❌ |
| Delete Auth Attempt | ⚠️ **TBD** | ⚠️ **TBD** | ⚠️ **TBD** | ✅ | ❌ |
| Validate Auth Attempt | ⚠️ Via API Key | ⚠️ Via API Key | ⚠️ Via API Key | ✅ | ✅ |
| Manage Enrollments | ⚠️ **TBD** | ⚠️ **TBD** | ⚠️ **TBD** | ✅ | ❌ |
| Create API Keys | ✅ | ✅ | ✅ | ✅ | ❌ |
| **Monitoring & Metrics** |
| View System Metrics | ✅ | ❌ | ❌ | ❌ | ❌ |
| View Organization Metrics | ✅ | ✅ | ❌ | ❌ | ❌ |
| View Unit Metrics | ✅ | ✅ | ✅ | ❌ | ❌ |
| View Integration Metrics | ✅ | ✅ | ✅ | ✅ | ❌ |

**Legend**:
- ✅ Full permission
- ❌ No permission
- ⚠️ **TBD** - Requires analysis (see [Scope Analysis](#scope-and-delegation-analysis))

---

## Scope and Delegation Analysis

### Key Question: Can Higher-Level Admins Operate on Lower-Level Entities?

This is the central question that requires careful analysis from both **pragmatic** and **compliance** perspectives.

#### Option A: Strict Separation (Most Restrictive)

**Principle**: Administrators can only manage entities at their own level and create entities at lower levels.

**Example**: Unit Admin can:
- ✅ Create integrations
- ✅ Create integration admins
- ❌ Cannot create auth attempts directly
- ❌ Cannot list/delete auth attempts
- ❌ Must delegate to integration admin or API key

**Pros**:
- Clear separation of responsibilities
- Strong SOC 2 compliance (clear audit trail)
- Prevents privilege escalation
- Easier to reason about permissions

**Cons**:
- Less flexible for operational needs
- Requires more delegation
- May slow down operations

**SOC 2 Alignment**: ✅ Strong - Clear separation of duties (CC6.1)

#### Option B: Delegated Operations (Moderate)

**Principle**: Administrators can operate on lower-level entities, but actions are clearly attributed.

**Example**: Unit Admin can:
- ✅ Create integrations
- ✅ Create integration admins
- ✅ Create auth attempts (attributed to unit admin, scoped to integration)
- ✅ List/delete auth attempts (with proper filtering and attribution)

**Pros**:
- More flexible for operations
- Allows intervention when needed
- Better for emergency situations

**Cons**:
- More complex permission model
- Requires careful audit trail design
- Potential for confusion about scope

**SOC 2 Alignment**: ⚠️ Moderate - Requires careful audit trail design

#### Option C: Full Delegation (Most Flexible)

**Principle**: Administrators have full capabilities for all entities under their scope.

**Example**: Unit Admin can:
- ✅ Everything Integration Admin can do
- ✅ Plus unit-level operations

**Pros**:
- Maximum flexibility
- Simple to understand
- Fast operations

**Cons**:
- Weak separation of duties
- Harder to audit
- Potential security concerns

**SOC 2 Alignment**: ❌ Weak - Poor separation of duties

### Recommended Approach: **Option B with Constraints**

We recommend **Option B (Delegated Operations)** with the following constraints:

1. **Attribution**: All actions must clearly identify:
   - Who performed the action (admin ID)
   - What level they operate at (admin type)
   - What entity the action targets (integration ID, unit ID, etc.)

2. **Scope Filtering**: When listing entities, results are filtered by admin's scope:
   - Unit Admin sees only integrations in their unit
   - Organization Admin sees only units in their organization

3. **Audit Trail**: Every action includes:
   - `performed_by_admin_id`: The admin who performed the action
   - `performed_by_admin_type`: The type of admin
   - `target_entity_type`: What was acted upon (integration, unit, etc.)
   - `target_entity_id`: The specific entity ID
   - `delegated_from_level`: The level from which action was delegated

4. **Emergency Operations**: Higher-level admins can intervene, but:
   - Actions are clearly marked as "delegated"
   - Audit logs show both the performing admin and the target entity's admin
   - Notifications can be sent to integration admins when unit admins act on their integrations

### Specific Question: Unit Admin and Auth Attempts

**Question**: Can a Unit Admin directly create/delete auth attempts for integrations under their unit?

**Analysis**:

**Arguments FOR allowing**:
- Operational flexibility (integration admin unavailable)
- Emergency intervention capability
- Pragmatic approach (80/20 rule)
- Common enterprise pattern (manager can act on behalf of team)

**Arguments AGAINST allowing**:
- Separation of duties (SOC 2 requirement)
- Clear accountability (who is responsible?)
- Potential for abuse
- Complexity in audit trail

**Recommendation**: ✅ **YES, with proper attribution**

**Implementation**:
```sql
-- Auth Attempt table includes delegation fields
CREATE TABLE ezkey_auth_attempt (
    auth_attempt_id INT PRIMARY KEY,
    integration_id INT NOT NULL,
    enrollment_id INT NOT NULL,
    -- ... other fields ...
    
    -- Delegation tracking
    created_by_admin_id INT REFERENCES ezkey_admin(admin_id),
    created_by_admin_type VARCHAR(20), -- GLOBAL_ADMIN, UNIT_ADMIN, etc.
    delegated_from_level VARCHAR(20), -- NULL if created by integration admin
    created_at TIMESTAMPTZ NOT NULL
);
```

**Audit Log Example**:
```json
{
  "action": "CREATE_AUTH_ATTEMPT",
  "performed_by": {
    "admin_id": 5,
    "admin_type": "UNIT_ADMIN",
    "username": "unit_manager"
  },
  "target": {
    "entity_type": "INTEGRATION",
    "entity_id": 12,
    "integration_name": "Web App"
  },
  "delegation": {
    "delegated_from_level": "UNIT",
    "normal_admin": {
      "admin_id": 8,
      "admin_type": "INTEGRATION_ADMIN",
      "username": "integration_owner"
    }
  },
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

## Traceability and Audit Considerations

### Audit Trail Requirements

Every administrative action must be logged with:

1. **Who**: Administrator who performed the action
   - Admin ID
   - Admin type
   - Username
   - Email (for global admins)

2. **What**: Action performed
   - Action type (CREATE, UPDATE, DELETE, etc.)
   - Entity type (TENANT, INTEGRATION, AUTH_ATTEMPT, etc.)
   - Entity ID

3. **When**: Timestamp
   - UTC timestamp with timezone
   - Immutable (cannot be modified)

4. **Where**: Context
   - Target entity scope
   - Delegation information (if applicable)

5. **Why**: (Optional, for future)
   - Reason code
   - Business justification

### Delegation Attribution

When a higher-level admin performs an action on a lower-level entity:

**Option 1: Single Attribution**
- Action attributed to the performing admin
- Target entity's admin is noted in metadata

**Option 2: Dual Attribution** (Recommended)
- Primary attribution: Performing admin
- Secondary attribution: Target entity's admin (for notification/audit)
- Clear indication of delegation

**Recommendation**: **Option 2 (Dual Attribution)**

### Audit Log Schema

```sql
CREATE TABLE ezkey_audit_log (
    audit_log_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    
    -- Who performed the action
    performed_by_admin_id INT NOT NULL REFERENCES ezkey_admin(admin_id),
    performed_by_admin_type VARCHAR(20) NOT NULL,
    performed_by_username VARCHAR(50) NOT NULL,
    
    -- What action was performed
    action_type VARCHAR(50) NOT NULL, -- CREATE_AUTH_ATTEMPT, DELETE_ENROLLMENT, etc.
    entity_type VARCHAR(50) NOT NULL, -- AUTH_ATTEMPT, ENROLLMENT, INTEGRATION, etc.
    entity_id INT,
    
    -- Target context (for delegated actions)
    target_admin_id INT REFERENCES ezkey_admin(admin_id), -- Admin normally responsible
    target_admin_type VARCHAR(20),
    target_entity_type VARCHAR(50), -- What was acted upon
    target_entity_id INT,
    
    -- Delegation information
    is_delegated BOOLEAN DEFAULT FALSE,
    delegated_from_level VARCHAR(20), -- UNIT, ORGANIZATION, etc.
    
    -- Additional context
    request_metadata JSONB, -- IP address, user agent, etc.
    response_metadata JSONB, -- Status code, error details, etc.
    
    -- When
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    
    -- Indexing for queries
    INDEX idx_audit_admin (performed_by_admin_id, created_at),
    INDEX idx_audit_entity (entity_type, entity_id, created_at),
    INDEX idx_audit_target (target_entity_type, target_entity_id, created_at),
    INDEX idx_audit_delegated (is_delegated, delegated_from_level, created_at)
);
```

### Querying Audit Logs

**Example Queries**:

```sql
-- All actions by a specific admin
SELECT * FROM ezkey_audit_log 
WHERE performed_by_admin_id = 5 
ORDER BY created_at DESC;

-- All delegated actions on a specific integration
SELECT * FROM ezkey_audit_log 
WHERE target_entity_type = 'INTEGRATION' 
  AND target_entity_id = 12 
  AND is_delegated = TRUE
ORDER BY created_at DESC;

-- Actions performed on behalf of an integration admin
SELECT * FROM ezkey_audit_log 
WHERE target_admin_id = 8 
  AND is_delegated = TRUE
ORDER BY created_at DESC;
```

---

## SOC 2 Compliance Considerations

### Relevant SOC 2 Controls

#### CC6.1 - Logical and Physical Access Controls
**Requirement**: Implement logical access security software, infrastructure, and architectures over protected information assets.

**Ezkey Implementation**:
- ✅ Hierarchical access control (admin types)
- ✅ Scope-based filtering (admins see only their scope)
- ✅ Audit trail for all actions
- ⚠️ **Requires**: Clear separation of duties documentation

#### CC6.2 - Access Credentials
**Requirement**: Prior to issuing system credentials and granting system access, the entity registers and authorizes new internal and external users.

**Ezkey Implementation**:
- ✅ Admin creation requires higher-level admin approval
- ✅ Admin types enforce scope restrictions
- ✅ Username must be identifiable (not generic)

#### CC6.3 - Removal of Access
**Requirement**: The entity authorizes, modifies, or removes access to data, software, and systems based on roles.

**Ezkey Implementation**:
- ✅ Admin deactivation (soft delete)
- ✅ Scope-based access removal
- ✅ Audit trail for access changes

#### CC7.2 - System Communications
**Requirement**: The entity restricts the transmission, movement, and removal of information to authorized internal and external users.

**Ezkey Implementation**:
- ✅ Cryptographic authentication (API keys, admin tokens)
- ✅ Scope-based data filtering
- ✅ Audit trail for data access

### SOC 2 Compliance Recommendations

1. **Separation of Duties**:
   - Document which admin types can perform which operations
   - Implement role-based access control (RBAC) clearly
   - Ensure no single admin has excessive privileges

2. **Audit Trail**:
   - All administrative actions must be logged
   - Logs must be immutable and tamper-proof
   - Logs must be queryable for compliance audits

3. **Access Management**:
   - Regular review of admin access (quarterly)
   - Automatic deactivation of inactive admins (configurable)
   - Notification when admins are created/modified

4. **Delegation Controls**:
   - Clear documentation of when delegation is allowed
   - Notification to target admins when actions are delegated
   - Regular review of delegated actions

5. **Monitoring and Alerting**:
   - Alert on unusual admin activity
   - Monitor for privilege escalation attempts
   - Track delegation frequency

---

## Implementation Recommendations

**Note**: These recommendations are for implementing the **proposed** enhanced multi-tenancy hierarchy. The current implementation only requires Global Admin and API Key support, which is already in place.

### Phase 1: Database Schema Updates

#### New Tables (Proposed)

```sql
-- Organization tenant (Level 2) - NEW TABLE
CREATE TABLE ezkey_organization (
    organization_id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_name VARCHAR(100) NOT NULL UNIQUE,
    organization_description TEXT,
    created_by_admin_id INT NOT NULL REFERENCES ezkey_admin(admin_id),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    active BOOLEAN DEFAULT TRUE NOT NULL
);

-- Unit tenant (Level 3) - NEW TABLE (would complement or replace current "tenant" concept)
CREATE TABLE ezkey_unit (
    unit_id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    unit_name VARCHAR(100) NOT NULL,
    unit_description TEXT,
    organization_id INT NOT NULL REFERENCES ezkey_organization(organization_id),
    created_by_admin_id INT NOT NULL REFERENCES ezkey_admin(admin_id),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    active BOOLEAN DEFAULT TRUE NOT NULL,
    UNIQUE(organization_id, unit_name) -- Unique within organization
);

-- Update Integration to reference Unit (PROPOSED - would complement existing tenant_id)
ALTER TABLE ezkey_integration 
    ADD COLUMN unit_id INT REFERENCES ezkey_unit(unit_id);
    
-- Note: Existing tenant_id would remain for backward compatibility during migration
-- Constraint would allow either tenant_id OR unit_id (not both)
ALTER TABLE ezkey_integration 
    ADD CONSTRAINT check_integration_tenant CHECK (
        (tenant_id IS NULL AND unit_id IS NOT NULL) OR
        (tenant_id IS NOT NULL AND unit_id IS NULL) OR
        (tenant_id IS NULL AND unit_id IS NULL) -- Allow null during transition
    );
```

#### Updated Admin Types (Proposed)

**Note**: Currently, `ezkey_admin` table has `admin_type` enum with `GLOBAL_ADMIN`, `TENANT_ADMIN`, `INTEGRATION_ADMIN`, but only `GLOBAL_ADMIN` is used.

```sql
-- Update admin_type enum to add new types
ALTER TABLE ezkey_admin 
    DROP CONSTRAINT IF EXISTS check_admin_hierarchy;

-- Add new admin types to enum (if enum type exists)
-- ALTER TYPE admin_type_enum ADD VALUE 'ORGANIZATION_ADMIN';
-- ALTER TYPE admin_type_enum ADD VALUE 'UNIT_ADMIN';

-- Update constraint to support new hierarchy
ALTER TABLE ezkey_admin 
    ADD CONSTRAINT check_admin_hierarchy CHECK (
        (admin_type = 'GLOBAL_ADMIN' AND tenant_id IS NULL AND organization_id IS NULL AND unit_id IS NULL AND integration_id IS NULL) OR
        (admin_type = 'ORGANIZATION_ADMIN' AND organization_id IS NOT NULL AND unit_id IS NULL AND integration_id IS NULL) OR
        (admin_type = 'TENANT_ADMIN' AND tenant_id IS NOT NULL AND integration_id IS NULL) OR -- Keep for backward compatibility
        (admin_type = 'UNIT_ADMIN' AND unit_id IS NOT NULL AND integration_id IS NULL) OR
        (admin_type = 'INTEGRATION_ADMIN' AND integration_id IS NOT NULL)
    );

-- Add new columns for proposed hierarchy
ALTER TABLE ezkey_admin 
    ADD COLUMN IF NOT EXISTS organization_id INT REFERENCES ezkey_organization(organization_id),
    ADD COLUMN IF NOT EXISTS unit_id INT REFERENCES ezkey_unit(unit_id);
```

### Phase 2: Service Layer Updates

#### Admin Service Enhancements

1. **Scope Resolution Service**:
   - Determine admin's effective scope
   - Filter queries based on scope
   - Validate permissions before operations

2. **Delegation Service**:
   - Track delegated actions
   - Send notifications to target admins
   - Generate audit logs with proper attribution

3. **Permission Service**:
   - Centralized permission checking
   - Role-based access control (RBAC)
   - Scope-based filtering

### Phase 3: API Layer Updates

#### Endpoint Modifications

1. **List Endpoints**: Automatically filter by admin scope
2. **Create Endpoints**: Validate permissions and scope
3. **Delete Endpoints**: Check delegation rules
4. **Audit Endpoints**: Query audit logs with proper filtering

### Phase 4: Migration Strategy

1. **Backward Compatibility**:
   - Support existing tenant-based structure during transition
   - Migrate existing tenants to organizations/units
   - Preserve all existing data and relationships

2. **Gradual Migration**:
   - Phase 1: Add new tables (non-breaking)
   - Phase 2: Migrate data (with rollback capability)
   - Phase 3: Update application code
   - Phase 4: Deprecate old tenant structure

---

## Visual Representations

### Complete Hierarchy Diagram

```mermaid
graph TB
    subgraph "Level 1: Global Administration"
        GA1[Global Admin 1<br/>IT Operations]
        GA2[Global Admin 2<br/>Security Team]
        GA3[Global Admin 3<br/>Backup Admin]
    end
    
    subgraph "Level 2: Organization A"
        OA1[Org Admin 1<br/>Acme Corp]
        OA2[Org Admin 2<br/>Acme Corp Backup]
        
        subgraph "Level 3: Consumer Portal Unit"
            UA1[Unit Admin 1]
            UA2[Unit Admin 2]
            
            subgraph "Level 4: Web App Integration"
                IA1[Integration Admin 1]
                IA2[Integration Admin 2]
                AK1[API Key 1]
                AK2[API Key 2]
            end
            
            subgraph "Level 4: Mobile App Integration"
                IA3[Integration Admin 1]
                AK3[API Key 1]
            end
        end
        
        subgraph "Level 3: Admin Console Unit"
            UA3[Unit Admin 1]
            
            subgraph "Level 4: Web Admin Integration"
                IA4[Integration Admin 1]
                AK4[API Key 1]
            end
        end
    end
    
    subgraph "Level 2: Organization B"
        OB1[Org Admin 1<br/>TechStart Inc]
        
        subgraph "Level 3: Internal Tools Unit"
            UB1[Unit Admin 1]
            
            subgraph "Level 4: HR Portal Integration"
                IB1[Integration Admin 1]
                AB1[API Key 1]
            end
        end
    end
    
    GA1 --> OA1
    GA1 --> OB1
    GA2 --> OA1
    GA3 --> OA1
    
    OA1 --> UA1
    OA1 --> UA3
    OA2 --> UA1
    
    UA1 --> IA1
    UA1 --> IA3
    UA2 --> IA1
    
    IA1 --> AK1
    IA1 --> AK2
    IA3 --> AK3
    
    OB1 --> UB1
    UB1 --> IB1
    IB1 --> AB1
    
    style GA1 fill:#ffcccc
    style GA2 fill:#ffcccc
    style GA3 fill:#ffcccc
    style OA1 fill:#ccffcc
    style OA2 fill:#ccffcc
    style OB1 fill:#ccffcc
    style UA1 fill:#ccccff
    style UA2 fill:#ccccff
    style UA3 fill:#ccccff
    style UB1 fill:#ccccff
    style IA1 fill:#ffffcc
    style IA2 fill:#ffffcc
    style IA3 fill:#ffffcc
    style IA4 fill:#ffffcc
    style IB1 fill:#ffffcc
    style AK1 fill:#ffccff
    style AK2 fill:#ffccff
    style AK3 fill:#ffccff
    style AK4 fill:#ffccff
    style AB1 fill:#ffccff
```

### Permission Flow Diagram

```mermaid
flowchart TD
    A[Admin Action Request] --> B{Check Admin Type}
    
    B -->|GLOBAL_ADMIN| C[System-wide Scope]
    B -->|ORGANIZATION_ADMIN| D[Organization Scope]
    B -->|UNIT_ADMIN| E[Unit Scope]
    B -->|INTEGRATION_ADMIN| F[Integration Scope]
    B -->|API_KEY| G[Integration Read-only]
    
    C --> H{Action Type?}
    D --> H
    E --> H
    F --> H
    G --> I{Read-only?}
    
    H -->|Create Entity| J{Target Level?}
    H -->|Operate on Entity| K{Delegation Allowed?}
    H -->|List Entities| L[Filter by Scope]
    
    J -->|Lower Level| M[✅ Allowed]
    J -->|Same Level| N{Create Peer?}
    J -->|Higher Level| O[❌ Denied]
    
    N -->|Yes| M
    N -->|No| O
    
    K -->|Yes| P[✅ Allowed with Attribution]
    K -->|No| Q{Own Entity?}
    
    Q -->|Yes| M
    Q -->|No| O
    
    I -->|Yes| R[✅ Read Allowed]
    I -->|No| O
    
    L --> S[Return Filtered Results]
    M --> T[Execute Action]
    P --> U[Execute with Audit Log]
    R --> V[Return Data]
    
    T --> W[Generate Audit Log]
    U --> W
    V --> W
    S --> W
    
    W --> X[Return Response]
    
    style M fill:#ccffcc
    style O fill:#ffcccc
    style P fill:#ffffcc
    style R fill:#ccffcc
```

### Delegation Flow Diagram

```mermaid
sequenceDiagram
    participant UA as Unit Admin
    participant PS as Permission Service
    participant AS as Audit Service
    participant NS as Notification Service
    participant IA as Integration Admin
    participant DB as Database
    
    UA->>PS: Create Auth Attempt (Integration 12)
    PS->>PS: Check: Unit Admin scope includes Integration 12?
    PS->>PS: Check: Delegation allowed?
    
    alt Delegation Allowed
        PS->>AS: Log Action (delegated=true)
        AS->>DB: Store Audit Log
        AS->>NS: Notify Integration Admin
        NS->>IA: Send Notification
        PS->>DB: Create Auth Attempt
        DB-->>PS: Auth Attempt Created
        PS-->>UA: Success (with delegation info)
    else Delegation Not Allowed
        PS-->>UA: Error: Permission Denied
    end
```

---

## Personal Information Management

### Current State

The current `ezkey_admin` table includes:
- `username` (required, unique)
- `email` (required for GLOBAL_ADMIN, optional for others)
- `first_name` (present in entity, optional)
- `last_name` (present in entity, optional)

### Requirements Analysis

**Operational Needs:**
1. **Unique Identification**: Identify administrators uniquely for audit and accountability
2. **Communication**: Contact administrators for security alerts, notifications, and operational needs
3. **Multi-Role Support**: Same individual may have multiple administrative roles (e.g., Unit Admin for one unit, Integration Admin for another)
4. **Compliance**: Support SOC 2 and privacy law requirements (Loi 25 Québec)

**Information to Collect:**
- ✅ **Email**: Required for communication and notifications
- ✅ **Full Name** (first_name, last_name): Required for identification and audit trail
- ✅ **Phone Number**: Optional but recommended for emergency contact
- ❌ **Physical Address**: Not required for operational needs
- ❌ **Date of Birth**: Not required
- ❌ **Other Personal Details**: Minimize collection per privacy principles

### Proposed Solution: Separate Party Information Table

**Design Rationale:**
- **Separation of Concerns**: Personal information separate from role/access information
- **Multi-Role Support**: One person can have multiple admin accounts linked to same party record
- **Privacy Compliance**: Clear separation allows for better privacy management
- **Data Minimization**: Only collect what's necessary for operations

#### Schema Design

```sql
-- Party information table (represents a person/organization)
CREATE TABLE ezkey_party (
    party_id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    
    -- Identification
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(20), -- Optional, format: +1-555-123-4567
    
    -- Metadata
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    active BOOLEAN DEFAULT TRUE NOT NULL,
    
    -- Constraints
    CONSTRAINT chk_party_email_format 
        CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'),
    CONSTRAINT chk_party_phone_format 
        CHECK (phone_number IS NULL OR phone_number ~* '^\+?[1-9]\d{1,14}$')
);

-- Update ezkey_admin to reference party
ALTER TABLE ezkey_admin 
    ADD COLUMN party_id INT REFERENCES ezkey_party(party_id);

-- Index for performance
CREATE INDEX idx_admin_party ON ezkey_admin(party_id) WHERE active = TRUE;
CREATE INDEX idx_party_email ON ezkey_party(email) WHERE active = TRUE;
```

#### Relationship Model

```mermaid
erDiagram
    Party ||--o{ EzkeyAdmin : "has"
    
    Party {
        int party_id PK
        string email UK
        string first_name
        string last_name
        string phone_number
        timestamp created_at
        boolean active
    }
    
    EzkeyAdmin {
        int admin_id PK
        int party_id FK
        string username UK
        enum admin_type
        int tenant_id FK
        int integration_id FK
    }
```

#### Use Cases

**Scenario 1: Single Role**
```
John Doe (party_id: 1)
├── Unit Admin for "Consumer Portal" (admin_id: 10)
```

**Scenario 2: Multiple Roles**
```
Jane Smith (party_id: 2)
├── Unit Admin for "Consumer Portal" (admin_id: 11)
├── Integration Admin for "Web App" (admin_id: 12)
└── Integration Admin for "Mobile App" (admin_id: 13)
```

**Scenario 3: Role Changes**
```
Bob Wilson (party_id: 3)
├── Integration Admin for "Web App" (admin_id: 14) [created 2025-01-01]
└── Unit Admin for "Admin Console" (admin_id: 15) [created 2025-06-01]
```

### API Design for Party Management

#### Endpoints

```
GET    /api/v1/admin/parties                    # List all parties
GET    /api/v1/admin/parties/{partyId}          # Get party details
POST   /api/v1/admin/parties                    # Create new party
PUT    /api/v1/admin/parties/{partyId}           # Update party information
DELETE /api/v1/admin/parties/{partyId}           # Deactivate party (soft delete)
GET    /api/v1/admin/parties/{partyId}/admins    # List all admin roles for a party
```

#### Example: Create Party with Admin

```http
POST /api/v1/admin/parties
Authorization: Bearer ezkey_admin_token...
Content-Type: application/json

{
  "email": "john.doe@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "phoneNumber": "+1-555-123-4567"
}
```

**Response:**
```json
{
  "partyId": 1,
  "email": "john.doe@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "phoneNumber": "+1-555-123-4567",
  "createdAt": "2025-01-15T10:00:00Z",
  "active": true
}
```

#### Example: Create Admin Linked to Existing Party

```http
POST /api/v1/admin/admins
Authorization: Bearer ezkey_admin_token...
Content-Type: application/json

{
  "partyId": 1,
  "username": "jdoe_unit_admin",
  "adminType": "UNIT_ADMIN",
  "unitId": 5
}
```

### Privacy Considerations

#### Data Minimization

**Collected Information:**
- Email: Required for notifications and account recovery
- Name: Required for audit trail and identification (SOC 2)
- Phone: Optional, for emergency contact only

**Not Collected:**
- Physical address
- Date of birth
- Government ID numbers
- Other personal identifiers

#### Retention Policy

- **Active Parties**: Retained while active
- **Inactive Parties**: Retained for audit purposes (7 years recommended)
- **Deletion**: Soft delete only (set `active = false`), hard delete requires legal/compliance approval

#### Access Control

- **View Own Information**: Administrators can view their own party information
- **View Others**: Only higher-level admins can view party information for admins in their scope
- **Edit Own Information**: Administrators can update their own email and phone
- **Edit Others**: Only higher-level admins can update party information for admins in their scope

---

## Privacy Compliance: Loi 25 Québec

### Overview

**Loi 25** (Loi modernisant des dispositions législatives en matière de protection des renseignements personnels) is Québec's privacy law that came into effect progressively from 2022-2024. It applies to organizations that collect, use, or communicate personal information about Québec residents.

### Key Requirements Relevant to Ezkey

#### 1. Consent and Transparency

**Requirement**: Organizations must obtain clear and explicit consent for collection, use, or communication of personal information.

**Ezkey Implementation:**
- ✅ **Clear Purpose**: Collect only email, name, and optional phone for operational needs
- ✅ **Explicit Consent**: Admin creation process requires explicit acknowledgment
- ✅ **Transparency**: Privacy policy explains what data is collected and why
- ✅ **Withdrawal**: Admins can request deletion of their party information (with constraints)

#### 2. Data Minimization

**Requirement**: Collect only personal information necessary for the stated purpose.

**Ezkey Implementation:**
- ✅ **Minimal Collection**: Only email, name, phone (optional)
- ✅ **Purpose Limitation**: Data used only for:
  - Account identification and authentication
  - Audit trail and accountability
  - Security notifications
  - Emergency contact
- ❌ **No Excessive Data**: No address, date of birth, or other unnecessary information

#### 3. Right to Access and Rectification

**Requirement**: Individuals have the right to access and correct their personal information.

**Ezkey Implementation:**
- ✅ **Access**: Admins can view their own party information via API
- ✅ **Rectification**: Admins can update their email and phone number
- ✅ **API Endpoints**: `GET /api/v1/admin/parties/{partyId}` and `PUT /api/v1/admin/parties/{partyId}`

#### 4. Right to Withdrawal of Consent

**Requirement**: Individuals can withdraw consent, subject to legal obligations.

**Ezkey Implementation:**
- ⚠️ **Constraints**: Cannot withdraw if admin account is active (operational requirement)
- ✅ **Deactivation**: Can deactivate admin account, which deactivates party information
- ✅ **Deletion Request**: Can request hard deletion (requires compliance review)

#### 5. Privacy Impact Assessment (EFVP)

**Requirement**: Organizations must conduct privacy impact assessments for projects involving personal information.

**Ezkey Implementation:**
- ✅ **Documentation**: This analysis document serves as initial EFVP
- ✅ **Risk Assessment**: Identifies privacy risks and mitigation strategies
- ✅ **Ongoing Review**: Regular review of data collection and usage practices

#### 6. Data Breach Notification

**Requirement**: Organizations must notify affected individuals and authorities of data breaches.

**Ezkey Implementation:**
- ✅ **Breach Detection**: Audit logs and monitoring for unauthorized access
- ✅ **Notification Process**: Defined process for notifying affected parties
- ✅ **Email Contact**: Party email used for breach notifications

#### 7. Cross-Border Data Transfer

**Requirement**: Organizations must inform individuals when data is transferred outside Québec.

**Ezkey Implementation:**
- ⚠️ **Self-Hosted**: Ezkey is self-hosted, so data location depends on deployment
- ✅ **Documentation**: Deployment documentation should specify data location
- ✅ **Configuration**: Admins can configure where data is stored

### Compliance Checklist

| Requirement | Status | Implementation |
|------------|--------|----------------|
| **Consent** | ✅ | Explicit consent during admin creation |
| **Data Minimization** | ✅ | Only email, name, phone (optional) |
| **Access Rights** | ✅ | API endpoints for viewing/editing own data |
| **Rectification** | ✅ | PUT endpoint for updating party information |
| **Withdrawal** | ⚠️ | Deactivation supported, deletion requires review |
| **Privacy Policy** | 📋 | To be created/updated |
| **Breach Notification** | 📋 | Process to be documented |
| **EFVP** | ✅ | This document serves as initial assessment |

### Recommendations

1. **Privacy Policy**: Create/update privacy policy explaining:
   - What personal information is collected
   - Why it's collected (operational needs)
   - How it's used (authentication, audit, notifications)
   - How to access/rectify/delete information

2. **Consent Mechanism**: Implement explicit consent checkbox during admin creation:
   ```
   ☑ I consent to the collection and use of my personal information 
     (email, name, phone) for account management, audit trail, and 
     security notifications as described in the Privacy Policy.
   ```

3. **Data Retention**: Document retention policy:
   - Active admins: Retain while active
   - Inactive admins: Retain for 7 years (audit compliance)
   - Hard deletion: Requires legal/compliance approval

4. **Breach Response Plan**: Document process for:
   - Detecting breaches
   - Notifying affected parties (via email)
   - Reporting to authorities (if required)

5. **Regular Review**: Annual review of:
   - Data collection practices
   - Privacy policy accuracy
   - Compliance with Loi 25 requirements

### Applicability to Ezkey

**Question**: Does Loi 25 apply to Ezkey?

**Analysis**:
- **Self-Hosted**: Ezkey is self-hosted, so applicability depends on:
  - Where Ezkey is deployed
  - Whether it processes personal information of Québec residents
- **If Deployed in Québec**: Loi 25 applies if processing personal information of Québec residents
- **If Deployed Outside Québec**: May still apply if processing personal information of Québec residents

**Recommendation**: 
- **Design for Compliance**: Implement privacy-by-design principles regardless of deployment location
- **Documentation**: Clearly document data collection and usage practices
- **Flexibility**: Design allows for compliance with various privacy laws (Loi 25, GDPR, etc.)

---

## Next Steps

### Immediate Actions

1. **Review and Validate**: Review this analysis with stakeholders
2. **Clarify Open Questions**: Resolve scope and delegation questions
3. **Design Database Schema**: Finalize schema changes including party table
4. **Create Implementation Plan**: Break down into phases
5. **Privacy Policy**: Draft privacy policy for Loi 25 compliance

### Documentation Updates

1. **Architecture Documentation**: Update with new hierarchy and party information model
2. **API Documentation**: Document new endpoints and permissions
3. **Admin Guide**: Create guide for administrators
4. **Developer Guide**: Update integration documentation
5. **Privacy Policy**: Create/update privacy policy

### Implementation Phases

1. **Phase 1**: Database schema updates (non-breaking)
   - Add party table
   - Update admin table with party_id
   - Migrate existing admin data
2. **Phase 2**: Service layer implementation
   - Party management service
   - Multi-role support
   - Privacy compliance features
3. **Phase 3**: API layer updates
   - Party management endpoints
   - Permission updates
   - Audit enhancements
4. **Phase 4**: Migration and testing
   - Data migration scripts
   - Integration tests
   - Privacy compliance validation
5. **Phase 5**: Documentation and training
   - Privacy policy
   - Admin guides
   - Compliance documentation

---

## Conclusion

This analysis proposes a comprehensive multi-tenancy strategy for Ezkey that:

1. **Supports Enterprise Needs**: Four-level hierarchy (Global → Organization → Unit → Integration)
2. **Maintains Pragmatism**: 80/20 approach with sensible defaults
3. **Ensures Compliance**: SOC 2 considerations built-in from the start
4. **Provides Flexibility**: Delegation with proper attribution
5. **Enables Traceability**: Complete audit trail for all actions

The recommended approach balances operational flexibility with security and compliance requirements, positioning Ezkey as a production-ready, enterprise-grade MFA solution.

---

**Document Status**: Analysis Complete - Awaiting Review  
**Next Review**: After stakeholder feedback  
**Related Documents**: 
- `docs/implementation/MULTI_TENANT_P2.md`
- `docs/ADMIN_API_SECURITY_GUIDE.md`
- `docs/ENDPOINT.md`
- `PRD.md`

---

## Context Verification

This analysis document has been prepared after reviewing:

✅ **Project Context**:
- README.md - Project overview and architecture
- PRD.md - Product requirements and vision
- ENDPOINT.md - Complete API endpoint documentation and interactions

✅ **Current Implementation**:
- Database schema (V1, V2, V3, V13 migrations)
- Entity structure (EzkeyAdmin, Tenant, Integration)
- Admin API endpoints and authentication flows
- API Key authentication mechanisms

✅ **Security and Compliance**:
- SOC 2 requirements and implementation
- Admin API security guide
- Multi-tenant security features

✅ **Operational Context**:
- Admin operations (create auth attempts, manage enrollments, etc.)
- Delegation patterns and scope management
- Audit trail requirements

The proposed multi-tenancy strategy aligns with:
- Ezkey's 80/20 pragmatism principle
- Existing database schema and entity relationships
- Current API endpoint patterns and capabilities
- SOC 2 compliance requirements
- Privacy law considerations (Loi 25 Québec)
