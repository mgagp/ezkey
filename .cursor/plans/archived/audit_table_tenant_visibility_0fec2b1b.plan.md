---
name: Audit Table Tenant Visibility
overview: "Plan to fix audit log tenant visibility: enforce tenant-scoped filtering for Tenant Admins, populate tenant_id when creating audit entries, with tests and build verification after each phase."
todos:
  - { id: phase1-filter, content: "Phase 1: Tenant filtering (Controller, Service, index) + unit/IT tests + mvn verify" }
  - { id: phase2-tenantId, content: "Phase 2: Populate tenant_id at audit creation sites + tests + mvn verify" }
  - { id: phase3-optional, content: "Phase 3: Optional tenantId filter for Global Admin + TUI" }
  - { id: phase4-docs, content: "Phase 4: Update ENDPOINT.md" }
isProject: false
---

# Audit Log Tenant Visibility and Search Criteria Improvement

## Context

The EZKey audit table (`ezkey_audit_log`) is well-structured (V6, V19, V24 migrations) with entity references (tenant_id, integration_id, enrollment_id, auth_attempt_id, admin_id). However, **tenant-based visibility is not enforced**: a Tenant Admin currently sees all audit logs from all tenants when querying via the Admin API or CLI TUI.

## Current State (Findings)

### Critical Gap: No Tenant Filtering

- [AuditLogController.java](ezkey-admin-api/src/main/java/org/ezkey/admin/controller/AuditLogController.java): Uses `@PreAuthorize("hasRole('ADMIN')")` but does not distinguish Global vs Tenant Admin
- [AuditLogService.findByFilters()](ezkey-core/src/main/java/org/ezkey/audit/service/AuditLogService.java): Applies eventType, eventStatus, apiName, enrollmentId, adminId filters but **no tenantId filter**
- Result: Tenant Admin sees other tenants' audits and global-admin-only events (encryption keys, tenant creation)

### tenant_id Often Not Populated

- [AuditHelper.createAdminAudit()](ezkey-admin-api/src/main/java/org/ezkey/admin/util/AuditHelper.java): Does not set `tenantId`; controllers add enrollmentId, integrationId, adminId but **never tenantId**
- Encryption key audits (EncryptionKeyController) are built with `AuditLog.builder()` directly and do not set tenantId (correct for system-level events)

### Existing Patterns

- [TenantController](ezkey-admin-api/src/main/java/org/ezkey/admin/controller/TenantController.java), [AdminProvisioningService.listAdmins()](ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminProvisioningService.java): Use `AdminPrincipal.tenantId()` and `principal.isGlobalAdmin()` to scope results
- [AccessControlService](ezkey-admin-api/src/main/java/org/ezkey/admin/security/AccessControlService.java): Validates integration/enrollment access by tenant

---

## Visibility Rules (Target)


| Actor            | Scope                                                                                                                     |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------- |
| **Global Admin** | All audit logs (no additional filter)                                                                                     |
| **Tenant Admin** | Only audits where `tenant_id = principal.tenantId()` OR tenant can be inferred from integration/enrollment/admin in scope |


### Gray Zone: Global-Level Events Concerning a Tenant

**Question**: Should a Tenant Admin see certain global-level events if they concern their tenant?

**Recommendation (pragmatic, 80/20)**:

- **No** to system-level events (KEY_*, REENCRYPTION_*, tenant creation): Tenant Admin has no operational need; these are Global Admin responsibilities. Exposing them adds complexity and potential information leakage.
- **Yes** (implicit) to tenant-scoped events: ENROLLMENT_*, AUTH_ATTEMPT_*, API_KEY_* where integration belongs to tenant; ADMIN_LOGIN for Tenant Admins of that tenant (admin.tenant_id = X).
- **Simplification**: For Tenant Admin, filter strictly by `tenant_id` when populated, and when null, exclude the row (or use JOIN to infer tenant from integration/enrollment). Do not add "global events concerning tenant" — keeps logic simple and aligned with competitor practice.

**Competitor alignment**:

- **Duo**: Tenant isolation via API hostname; logs are tenant-scoped by design.
- **PrivacyIDEA**: Filter by user; SUPERUSER_REALM for full visibility. No explicit multi-tenant audit doc, but role-based segmentation is standard.

---

## Development Context

- **No production deployment**: EZKey is in active development. No data migration or backfill strategy is needed. Existing audit rows with `tenant_id = NULL` are simply excluded from Tenant Admin results.
- **Phased implementation with verification**: After each logical phase, run `mvn clean verify` to ensure build and tests pass. User will provide compilation feedback before proceeding.

---

## Implementation Plan

### Phase 1: Backend – Tenant Filtering (High Priority)

**Code changes:**

1. **AuditLogController**
  - Extract `AdminPrincipal` from `SecurityContextHolder`
  - For Global Admin: pass `tenantId = null` (no filter)
  - For Tenant Admin: pass `principal.tenantId()` to service
2. **AuditLogService.findByFilters()**
  - Add parameter `Integer requesterTenantId`
  - When `requesterTenantId != null`: add predicate `tenant_id = requesterTenantId`
  - Audits with `tenant_id IS NULL`: excluded from Tenant Admin results (no backfill needed in dev)
3. **Database index**
  - Add `idx_audit_log_tenant` on `ezkey_audit_log(tenant_id)` via Flyway migration V31

**Tests:**

- **Unit**: `AuditLogServiceTest` — add tests for `findByFilters` with `requesterTenantId` null vs non-null; verify Tenant Admin gets only tenant-scoped rows
- **Integration**: `AuditLogController` IT — Global Admin sees all audits; Tenant Admin sees only audits with matching `tenant_id`

**Verification:** `mvn clean verify -pl ezkey-core,ezkey-admin-api`

---

### Phase 2: Backend – Populate tenant_id (Medium Priority)

**Code changes:**

1. **Audit creation sites**
  - **EnrollmentController**: Add `.tenantId(integration.getTenantId())` (load integration for tenant)
  - **AuthAttemptController**: enrollment -> integration -> tenant
  - **AdminAuthController**: admin.tenantId (null for Global Admin)
  - **ApiKeyController**: integration -> tenant
  - **EncryptionKeyController**: Keep `tenant_id = null` (system-level)
2. **Auth API audits**
  - Verify auth-api audit logging; set tenant_id via enrollment -> integration -> tenant where applicable

**Tests:**

- **Unit**: Update existing controller audit tests to assert `tenantId` is set correctly
- **Integration**: Ensure audit entries created by Tenant Admin operations have `tenant_id` populated; verify Tenant Admin can see them in Phase 1 filtering

**Verification:** `mvn clean verify -pl ezkey-core,ezkey-admin-api` (and ezkey-auth-api if modified)

---

### Phase 3: Optional Search Criteria (Lower Priority)

**Code changes:**

- Optional `tenantId` query param for Global Admin
- TUI: no changes if API applies tenant scope automatically

**Tests:**

- Integration test: Global Admin can filter by `?tenantId=X`

**Verification:** Full build

---

### Phase 4: Documentation

- Update `docs/ENDPOINT.md` with audit visibility rules

---

## Event Type → Tenant Association (Summary)


| Event Type                                    | tenant_id source        | Visible to Tenant Admin      |
| --------------------------------------------- | ----------------------- | ---------------------------- |
| ADMIN_LOGIN, ADMIN_LOGOUT, ADMIN_RECOVERY_USE | admin.tenantId          | Yes (own tenant admins only) |
| ENROLLMENT_*, AUTH_ATTEMPT_*                  | integration.tenantId    | Yes (tenant's integrations)  |
| API_KEY_*                                     | integration.tenantId    | Yes                          |
| KEY_*, REENCRYPTION_*                         | null (system)           | No                           |
| Tenant creation / deactivation                | N/A (Global Admin only) | No                           |


---

## Files to Modify

- `ezkey-admin-api/.../AuditLogController.java` — inject principal, pass tenantId to service
- `ezkey-core/.../AuditLogService.java` — add tenant filter in Specification
- `ezkey-admin-api/.../EnrollmentController.java`, `AuthAttemptController.java`, `AdminAuthController.java`, `ApiKeyController.java` — set tenantId in audit builders
- `ezkey-core/.../db/migration/V31__add_audit_tenant_index.sql` — new index
- `ezkey-auth-api` — audit logging (if any) for ENROLLMENT_BIND, ENROLLMENT_VERIFY, AUTH_ATTEMPT_* with tenant_id
- `docs/ENDPOINT.md` — document audit visibility rules

**Existing test patterns to extend:**

- `ezkey-admin-api/.../controller/AuthAttemptControllerOwnershipTest.java` — tenant-scoped access patterns
- `ezkey-admin-api/.../controller/EnrollmentControllerAuditTest.java` — audit assertion patterns

---

## Risks and Mitigations

- **tenant_id = NULL in existing rows**: In dev mode, Tenant Admin simply excludes these; no migration. New audits get `tenant_id` from Phase 2.
- **Performance**: Index on `tenant_id` avoids full scans for tenant-filtered queries.
- **Test coverage**: Each phase includes unit + integration tests; run `mvn clean verify` after each phase before continuing.

