---
name: Phase 1 multi-tenant
overview: Introduce a simple 3-actor multi-tenant model (GlobalAdmin, TenantAdmin, APIKey) with pragmatic scope enforcement, configurable admin duplication limits, and complete auditability—without enterprise over-engineering.
todos:
  - id: principal-and-roles
    content: Introduce AdminPrincipal and update bearer-token authentication to set adminId/adminType/tenantId/integrationId + role per admin type.
    status: completed
  - id: scope-access-control
    content: Make AccessControlService scope-aware for GlobalAdmin vs TenantAdmin vs APIKey across integration/enrollment/authAttempt/APIKey/audit access.
    status: completed
    dependencies:
      - principal-and-roles
  - id: tenant-admin-provisioning
    content: Add tenant + admin provisioning endpoints (create tenant, create peer TenantAdmins, create peer GlobalAdmins) with configurable max/min limits and full audit logging.
    status: completed
    dependencies:
      - scope-access-control
  - id: tenant-scope-filtering
    content: Apply tenant scoping to existing admin-api endpoints (integrations/enrollments/api-keys/auth-attempts) without breaking API contracts.
    status: completed
    dependencies:
      - scope-access-control
  - id: deactivation-revocation
    content: Implement admin deactivation rules (no hard-delete), token revocation, safety floors (cannot deactivate below min), and tenant deactivation (required in Phase 1).
    status: pending
    dependencies:
      - tenant-admin-provisioning
  - id: cli-update
    content: "Update CLI in 2 passes: (1) after backend is validated, export OpenAPI spec; (2) update CLI to manage tenants/admins using the new spec (create/list/deactivate + onboarding outputs)."
    status: pending
    dependencies:
      - tenant-admin-provisioning
  - id: security-tests
    content: Add integration/security tests that validate tenant isolation, admin limits, and deactivation semantics.
    status: pending
    dependencies:
      - tenant-scope-filtering
      - deactivation-revocation
  - id: docs-update
    content: Update docs (multi-tenancy strategy, security multi-tenant, ENDPOINT) to reflect actual Phase 1 model and endpoints.
    status: pending
    dependencies:
      - tenant-admin-provisioning
---

# Phase 1 Multi-Tenant Plan (Simple + Pragmatic)

## Why keep opaque DB tokens (and why JWT is optional)

- **Chosen (Phase 1): Opaque bearer token stored in DB**: already implemented end-to-end (login, validation, logout, rotation), enables **immediate revocation** and simple incident response.
- **JWT (optional later)** is mainly interesting when you need:
- **High request volume** and want to avoid a DB lookup per request.
- **Stateless-ish auth** across many horizontally scaled API instances.
- A standardized token format for external tooling.
- **Why JWT is borderline over-engineering for Ezkey Phase 1**:
- You still need a **revocation strategy** (blacklist table, short TTL, rotation keys, etc.) or you accept weaker revocation.
- Adds **key management**, signing/rotation policy, claim design, and new failure modes.
- You already have the DB token infrastructure and tests; switching now yields little value.

## Target model (Phase 1)

### Actors

- **GlobalAdmin**: system-wide, can create tenants, manage system-wide endpoints, and create peer GlobalAdmins (limited).
- **TenantAdmin**: scoped to exactly one tenant, can manage integrations + operational resources for that tenant (integrations, enrollments, API keys, auth attempts). Can create **peer TenantAdmins for the same tenant** (limited).
- **APIKey**: machine-to-machine scoped to **one integration** (already implemented), used for day-to-day auth attempt creation/wait/read.

### Duplication limits (externalized)

- **Global admins max**: `ezkey.security.admin.max-global-admins` (default: 3).
- **Tenant admins max per tenant**: `ezkey.security.admin.max-tenant-admins-per-tenant` (default: 3).
- **Safety floor** (avoid lockout):
- `ezkey.security.admin.min-global-admins` (default: 1).
- `ezkey.security.admin.min-tenant-admins-per-tenant` (default: 1).

## Implementation approach (minimal complexity, maximum correctness)

## Flyway end-state verification gate (mandatory)

- Treat `ezkey-core/src/main/resources/db/migration/` as the source of truth (avoid “V1 vs V12 confusion”).
- Before implementing scoping/permissions, validate we are coding against the latest end-state of:
- `ezkey_admin` columns + `check_admin_hierarchy` constraint (notably post-`V3__create_system_tenant_and_admin_zero.sql`)
- `ezkey_integration.tenant_id`
- `ezkey_audit_log.tenant_id`

### 1) Represent “who is calling” once (AdminPrincipal)

**Problem today**: `AdminTokenAuthenticationFilter` sets `principal=username` and only `ROLE_ADMIN`, and `AccessControlService` treats all admins as “full access”. Tenant scoping cannot be implemented safely with that.**Plan**:

- Introduce a small immutable principal (record) carrying:
- `adminId`, `adminType`, `tenantId`, `integrationId`.
- Update the bearer-token auth path so Spring Security has:
- **Authorities**: always `ROLE_ADMIN`, plus exactly one of `ROLE_GLOBAL_ADMIN` or `ROLE_TENANT_ADMIN` (we will *not* activate integration-admin in Phase 1).
- **Principal**: `AdminPrincipal`.

Files to change:

- [`ezkey-admin-api/src/main/java/org/ezkey/admin/security/AdminTokenAuthenticationFilter.java`](ezkey-admin-api/src/main/java/org/ezkey/admin/security/AdminTokenAuthenticationFilter.java)
- [`ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminTokenValidationService.java`](ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminTokenValidationService.java)
- (New) `ezkey-admin-api/src/main/java/org/ezkey/admin/security/AdminPrincipal.java`

### 2) Replace “admins have full access” with scope-aware checks

**Current state** (not sufficient for multi-tenant): `AccessControlService` returns `true` for any admin role.**Plan**:

- Extend/replace `AccessControlService` so it can answer:
- **GlobalAdmin**: access to any tenant.
- **TenantAdmin**: access only where `resource.tenant_id == principal.tenantId`.
- **APIKey**: existing behavior (integration-scoped).
- Implement checks for:
- integration access
- enrollment access (via integration)
- auth attempt access (via enrollment → integration)
- API key access (integration)
- audit log access (tenant-scoped view for TenantAdmin, full for GlobalAdmin)

Files to change:

- [`ezkey-admin-api/src/main/java/org/ezkey/admin/security/AccessControlService.java`](ezkey-admin-api/src/main/java/org/ezkey/admin/security/AccessControlService.java)
- Controllers that already call `@accessControlService` (e.g. auth-attempt read/wait) will immediately benefit.

### 3) Add missing management endpoints needed for a CLI-first Phase 1

You explicitly rely on CLI (no admin UI). So Phase 1 must provide REST endpoints for:

#### Tenants

- **GlobalAdmin only**:
- `POST /api/v1/tenants` create tenant.
- `GET /api/v1/tenants` list tenants.
- Phase 1 required: `POST /api/v1/tenants/{id}/deactivate`.

#### Tenant admin provisioning (peer admins)

- **GlobalAdmin**: can create tenant admins for any tenant.
- **TenantAdmin**: can create **peer TenantAdmins** for same tenant only.
- Endpoint shape (simple, pragmatic):
- `POST /api/v1/admins/tenant` (body includes tenantId + identity fields).

#### Global admin provisioning (peer admins)

- **GlobalAdmin only**:
- `POST /api/v1/admins/global`.

#### Admin lifecycle controls (deactivation)

- **GlobalAdmin only**:
- `POST /api/v1/admins/{id}/deactivate`
- Guardrails:
- cannot deactivate below min-admin thresholds.
- deactivation revokes active tokens.

Provisioning output (critical for passwordless onboarding):

- When creating a new admin (global or tenant), return:
- enrollment credentials to bind device (enrollmentId + proof token + challenge)
- recovery codes (plain, shown once)

We will reuse proven bootstrap logic from:

- [`ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminBootstrapService.java`](ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminBootstrapService.java)

New/updated files:

- (New) `ezkey-admin-api/src/main/java/org/ezkey/admin/controller/TenantController.java`
- (New) `ezkey-admin-api/src/main/java/org/ezkey/admin/controller/AdminProvisioningController.java`
- (New) `ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminProvisioningService.java`
- (New) DTOs under `ezkey-admin-api/src/main/java/org/ezkey/admin/dto/request|response/`

### 4) Enforce the duplication limits (configurable)

We’ll enforce limits in the provisioning service using existing repository counts:

- `EzkeyAdminRepository.countByAdminTypeAndActiveTrue(GLOBAL_ADMIN)`
- `EzkeyAdminRepository.countByTenantTenantIdAndAdminType(tenantId, TENANT_ADMIN)`

Files involved:

- [`ezkey-core/src/main/java/org/ezkey/integration/domain/repository/EzkeyAdminRepository.java`](ezkey-core/src/main/java/org/ezkey/integration/domain/repository/EzkeyAdminRepository.java)
- (Potentially add) `countByTenantTenantIdAndAdminTypeAndActiveTrue(...)` for correctness.

### 5) Make existing operational endpoints tenant-aware (without changing API contracts)

Goal: do not break clients; just change authorization and filtering.

- **Critical tenant-scoping rule (applies to create/update/search)**:
- If the caller is a TenantAdmin, every request that includes an `integrationId` (or references an entity that resolves to an integration) must be validated so the target integration belongs to the caller's tenant.
- This applies to **API key creation**, **enrollment creation**, auth-attempt creation by bearer token, and any endpoint that accepts `integrationId` as a filter.
- For search endpoints, tenant scoping is implemented as an additional predicate in the JPA query/specification (`WHERE integration.tenant_id = :tenantId`).
- **Integration creation and tenant assignment (critical decision)**:
- **Global Admin creates integration**: Integration is automatically assigned to the **"Ezkey System" tenant** (system-level integrations). This ensures all integrations have a tenant, and Global Admins create system-level resources.
- **Tenant Admin creates integration**: Integration is automatically assigned to the **admin's tenant** (tenant-scoped integrations).
- **No impersonation**: Administrators cannot create resources (integrations, enrollments, API keys, auth attempts) for other tenants. If a Global Admin needs to create resources for a specific tenant, they must become a Tenant Admin for that tenant.
- **Implementation**: `IntegrationService.createIntegration()` now accepts `EzkeyAdmin createdByAdmin` parameter and automatically sets `integration.tenant` and `integration.createdByAdmin` based on admin type.
- Integrations search/get/create/delete:
- TenantAdmin sees/creates only within its tenant.
- GlobalAdmin sees all (but creates in "Ezkey System" tenant unless they are also a Tenant Admin).
- Enrollments search/get/create/delete:
- TenantAdmin restricted to enrollments whose integration is in its tenant.
- API keys:
- TenantAdmin restricted to integrations in its tenant.
- Auth attempts:
- existing `@accessControlService` checks expanded to cover tenant scope.

Files to change (examples):

- [`ezkey-admin-api/src/main/java/org/ezkey/admin/controller/IntegrationController.java`](ezkey-admin-api/src/main/java/org/ezkey/admin/controller/IntegrationController.java)
- ✅ **Updated**: Passes `createdByAdmin` to `IntegrationService.createIntegration()` to enable automatic tenant assignment.
- [`ezkey-core/src/main/java/org/ezkey/integration/service/IntegrationService.java`](ezkey-core/src/main/java/org/ezkey/integration/service/IntegrationService.java)
- ✅ **Updated**: `createIntegration()` now accepts `EzkeyAdmin createdByAdmin` parameter and automatically assigns tenant:
    - Global Admin → "Ezkey System" tenant
    - Tenant Admin → admin's tenant
- [`ezkey-admin-api/src/main/java/org/ezkey/admin/controller/ApiKeyController.java`](ezkey-admin-api/src/main/java/org/ezkey/admin/controller/ApiKeyController.java)
- ✅ **Updated**: Added tenant scoping validation using `AccessControlService.canAccessIntegration()` before creating API keys.
- [`ezkey-admin-api/src/main/java/org/ezkey/admin/controller/EnrollmentController.java`](ezkey-admin-api/src/main/java/org/ezkey/admin/controller/EnrollmentController.java)
- [`ezkey-admin-api/src/main/java/org/ezkey/admin/controller/AuthAttemptController.java`](ezkey-admin-api/src/main/java/org/ezkey/admin/controller/AuthAttemptController.java)
- API-key endpoints/controller(s) in `ezkey-admin-api` (already present).

### 6) Deactivation / revocation rules (simple, safe)

- **Deactivate admin**:
- `ezkey_admin.active=false`
- revoke all active tokens: `AdminTokenRepository.deactivateTokensByAdmin(adminId)`
- keep everything for audit.
- **Deactivate tenant** (required in Phase 1):
- `ezkey_tenant.active=false`
- revoke all active tokens for that tenant.
- prevent further creation operations for that tenant.

### 7) Audit requirements (already supported; ensure tenantId is set consistently)

`ezkey_audit_log` already has `tenant_id` and `admin_id` (see migration V6). Phase 1 work is mostly:

- ensure tenantId is populated on events that touch tenant-scoped resources.
- ensure provisioning/deactivation events are logged.

Key schema already exists:

- [`ezkey-core/src/main/resources/db/migration/V6__create_audit_log.sql`](ezkey-core/src/main/resources/db/migration/V6__create_audit_log.sql)

### 8) CLI impact (critical)

Update the CLI in two passes (keep it for the end, after backend functional validation):

- **Pass 1 (end of backend work)**: export updated OpenAPI spec (Admin API) to drive CLI updates.
- **Pass 2 (CLI update)**: add/extend commands for:
- tenant create/list/deactivate
- tenant-admin create-peer
- global-admin create-peer
- admin deactivate
- display onboarding outputs (enrollment credentials + recovery codes “shown once”) with a safe CLI UX.

Likely files:

- [`ezkey-cli-python/ezkey_cli/commands/admin.py`](ezkey-cli-python/ezkey_cli/commands/admin.py)
- CLI tests/utilities under `ezkey-cli-python/tests/`

### 9) Tests (security-critical)

Add/extend integration tests to guarantee tenant isolation:

- TenantAdmin cannot read/list/create outside its tenant.
- TenantAdmin can manage within its tenant.
- GlobalAdmin can manage all.
- **Integration creation tenant assignment**:
- Global Admin creates integration → assigned to "Ezkey System" tenant.
- Tenant Admin creates integration → assigned to admin's tenant.
- No impersonation: cannot create resources for other tenants.
- Limits enforced:
- cannot create more than max global admins.
- cannot create more than max tenant admins per tenant.
- cannot deactivate below mins.

Likely test locations:

- `ezkey-tests/src/test/java/org/ezkey/tests/security/**`
- `ezkey-admin-api/src/test/java/**` for controller tests.

### 10) Documentation updates (post-implementation)

- Update `docs/analysis/multi-tenancy-strategy.md` to reflect the **actual Phase 1** model (no Org/Unit/Party in scope).
- Update `docs/features/SECURITY_MULTI_TENANT.md` to align with the chosen minimal model.
- Update `docs/ENDPOINT.md` with new endpoints (`/api/v1/tenants`, `/api/v1/admins/*`) and role behavior.

## Notes on Spring Security strategy (your question)

We’ll keep the current pragmatic model: