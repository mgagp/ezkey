# Cursor Plans Archive

**Purpose:** Archive completed and validated implementation plans for historical reference.

---

## 📋 Archive Organization

Plans are organized by completion date (YYYY-MM format) to maintain chronological order and easy retrieval.

---

## ✅ Archived Plans

### 2026-04 - Dashboard badge drill-downs (clickable stats → lists / audit)

**Completion Date:** April 14, 2026  
**Status:** ✅ **COMPLETED** (implemented; validated in manual testing)

| File | Purpose | Status |
|------|---------|--------|
| **2026-04/dashboard_badge_drill-downs_175c29d4.plan.md** | Dashboard stat badges link to entity lists with matching filters (rolling 24h for auth attempts); `dashboard-drilldown-links.ts`; enrollments bucket merge for CREATED+BOUND / INVALID+REVOKED; integrations `lifecycleFilter` in URL; optional audit trail; aria-labels EN/FR | ✅ Completed |

**What was implemented:**

- `ezkey-admin-ui`: [`dashboard-drilldown-links.ts`](../../../ezkey-admin-ui/src/lib/dashboard-drilldown-links.ts), [`dashboard-stat-badge-link.tsx`](../../../ezkey-admin-ui/src/components/feature/dashboard-stat-badge-link.tsx), [`dashboard.tsx`](../../../ezkey-admin-ui/src/pages/dashboard.tsx), [`auth-attempts.tsx`](../../../ezkey-admin-ui/src/pages/auth-attempts.tsx), [`enrollments.tsx`](../../../ezkey-admin-ui/src/pages/enrollments.tsx), [`integrations.tsx`](../../../ezkey-admin-ui/src/pages/integrations.tsx); locales (`dashboard`, `auth-attempts`, `enrollments`).

---

### 2026-04 - Dashboard enrollment widget (integrity + operator story)

**Completion Date:** April 14, 2026  
**Status:** ✅ **COMPLETED** (implemented)

| File | Purpose | Status |
|------|---------|--------|
| **2026-04/dashboard_enrollment_widget_1aaa3560.plan.md** | Dashboard enrollments: single `GROUP BY status` aggregation for `active=true`; DTO buckets (`verified`, `inProgress`, `expired`, `unavailable`); Admin UI badges sum to headline total; aligns with auth-attempt dashboard discipline | ✅ Completed |

**What was implemented:**

- `ezkey-core`: `EnrollmentDashboardStats`, `EnrollmentService.aggregateDashboardEnrollmentStats`, `EnrollmentDashboardStatsTest`.
- `ezkey-admin-api`: `DashboardEnrollmentStatsDto` revised; `DashboardService` wired to aggregation.
- `ezkey-admin-ui`: enrollments `StatCard` + `dashboard.json` EN/FR; hand-updated `dashboardEnrollmentStatsDto.ts` until OpenAPI / Orval regen.

---

### 2026-04 - Auth API public `instance-info`

**Completion Date:** April 13, 2026  
**Status:** ✅ **COMPLETED** (implemented; Postman validated)

| File | Purpose | Status |
|------|---------|--------|
| **2026-04/auth_api_public_instance-info_79f69132.plan.md** | Same `GET /api/v1/public/instance-info` JSON on Auth API as Admin API; shared DTO/properties/service in `ezkey-core`; Docker env on `auth-api`; docs + mobile README | ✅ Completed |

**What was implemented:**

- `ezkey-core`: `PublicInstanceInfoService`, `PublicInstanceInfoResponseDto`, `QrCodeProperties`, `OrganizationProperties`; Admin and Auth thin controllers.
- `ezkey-auth-api`: `PublicInstanceInfoController` with OpenAPI `security = {}` for the GET.
- Docker: `EZKEY_QR_AUTH_BASE_URL` / `EZKEY_ORGANIZATION_ABOUT_URL` on `auth-api` (and HA/native variants); `application-docker*.properties` for Auth API.
- Docs: `docs/ENDPOINT.md` §1, `docs/OPERATIONAL.md`, `ezkey_mobile/README.md`.
- Postman: `EZ Key Public auth` collection; `EZ Key Public admin` URL as string for correct import.

**Canonical reference:** [`docs/ENDPOINT.md`](../../../docs/ENDPOINT.md).

---

### 2026-04 - Tenant timezone strategy (Admin UI / API)

**Completion Date:** April 13, 2026  
**Status:** ✅ **FULLY IMPLEMENTED AND VALIDATED**

| File | Purpose | Status |
|------|---------|--------|
| **2026-04/tenant_timezone_strategy_08096020.plan.md** | IANA validation on tenant `timezone`; session `tenantId` / `adminId`; display timezone preference (local vs tenant) with header controls; formatters + date-range API bounds aligned | ✅ Completed |

**What was implemented:**

- Backend: Bean Validation `@IanaTimezone` on tenant create/update DTOs; login response includes `adminId` and `tenantId` where applicable for tenant-scoped UI.
- Admin UI: `DisplayTimezoneProvider`, `DisplayTimezoneMenu` (clock + short label) and `HeaderLogoutButton` (icon + label at first level); `display-timezone-pref`, resolver + `utils` / `date-range-presets` / list pages using effective zone.

**Note:** The “Current State” section inside the archived plan file is a **historical pre-implementation snapshot**; behavior has since changed as above.

---

### 2026-03 - Admin UI token security (HTTP hardening)

**Completion Date:** March 27, 2026  
**Status:** ✅ **FULLY IMPLEMENTED AND VALIDATED**

| File | Purpose | Status |
|------|---------|--------|
| **2026-03/admin_ui_token_security.plan.md** | sessionStorage + Bearer transport; Caddy headers (Admin UI + API proxy); clean-start default proxy; docs (split deploy, mkcert, two-path dev/QA); HttpOnly cookie design sketch | ✅ Completed |

**What was implemented:**

- Admin UI: CSP and baseline security headers in `ezkey-admin-ui/docker/Caddyfile` (Path B / `start.sh`); HSTS gated on TLS (mkcert path documented).
- Dev stack: Caddy in front of APIs by default in `ezkey-tests/clean-start.sh` (`docker/caddy/Caddyfile`, ports 19080/18080/17080).
- Documentation: `docs/admin-ui-security.md`, `docs/admin-ui-security-validation.md`, `docs/LOCAL_STACK_PORTS.md`; Postman `local (via Caddy proxy)` environment.

**Canonical reference going forward:** [`docs/admin-ui-security.md`](../../../docs/admin-ui-security.md) — not the archived plan file.

---

### 2026-03 - Admin API pagination uniformity

**Completion Date:** March 27, 2026  
**Status:** ✅ **FULLY IMPLEMENTED AND VALIDATED**

| File | Purpose | Status |
|------|---------|--------|
| **2026-03/admin_api_pagination_uniformity_afd2226c.plan.md** | Standardize Admin list/search APIs on server-side pagination (`Page`, `page`/`size`/`sort`); align Admin UI on `usePaginatedFromOrval` | Completed |

**What was implemented:**

- Tenants, API keys, encryption keys, and re-encryption batches list endpoints return Spring Data `Page` responses; Admin UI uses `usePaginatedFromOrval` + `DataTable` + `Pagination` for those screens.
- Re-encryption batches pagination was completed in a follow-up change after the main plan (same uniformity goal).

**Code references (illustrative):**

- [TenantController.java](ezkey-admin-api/src/main/java/org/ezkey/admin/controller/TenantController.java), [ApiKeyController.java](ezkey-admin-api/src/main/java/org/ezkey/admin/controller/ApiKeyController.java), [EncryptionKeyController.java](ezkey-admin-api/src/main/java/org/ezkey/admin/controller/EncryptionKeyController.java) (`listKeys`, `listBatches`)
- [tenants.tsx](ezkey-admin-ui/src/pages/tenants.tsx), [api-keys.tsx](ezkey-admin-ui/src/pages/api-keys.tsx), [encryption-keys.tsx](ezkey-admin-ui/src/pages/encryption-keys.tsx)

The archived plan file preserves the original analysis and phased plan; the “current state” table at the top of that document is a **historical pre-migration snapshot**.

---

### 2025-03 - Admin UI Garage du coin placeholders

**Completion Date:** March 8, 2025
**Status:** ✅ **FULLY IMPLEMENTED AND VALIDATED**

| File | Purpose | Status |
|------|---------|--------|
| **admin_ui_garage_du_coin_placeholders.plan.md** | Replace ACME-style form placeholders with "Garage du coin" theme (Oscar Dupont / Marie Dupont personae) | ✅ Completed |

**What Was Implemented:**
- ✅ tenants.tsx: Garage du coin, Oscar Dupont contact, garageducoin.com, Porsche/Mercedes/Audi hints
- ✅ admins.tsx: Marie Dupont generic persona (marie.dupont, Marie, Dupont, marie@garageducoin.com)
- ✅ enrollments.tsx: Marie Dupont — iPhone 15, user@garageducoin.com

**Key Conventions:**
- Garage contact (tenants only): Oscar Dupont / oscar@garageducoin.com
- Generic persona (admins, enrollments): Marie Dupont; brands-only subtle luxury hint (no "auto luxueuse" wording)

---

### 2026-01 - Maven POM Versioning (CI-Friendly Versions)

**Completion Date:** January 16, 2026
**Status:** ✅ **FULLY IMPLEMENTED AND VALIDATED**

| File | Purpose | Status |
|------|---------|--------|
| **maven_pom_versioning_no_branch_collisions.md** | Modern Maven versioning strategy with branch isolation to prevent artifact collisions | ✅ Completed |

**What Was Implemented:**
- ✅ Refactored all POMs to use CI-friendly versions: `${revision}${buildQualifier}${changelist}`
- ✅ Created `.mvn/maven.config` with default values for developer-first experience
- ✅ Created `scripts/mvn-branch.sh` (bash wrapper) with branch name detection and SHA fallback
- ✅ Created `scripts/mvn-branch.ps1` (PowerShell 7.x wrapper) with same logic
- ✅ Updated `docs/DEVELOPMENT.md` with version management documentation
- ✅ All 9 modules updated to use CI-friendly parent version

**Key Features:**
- Branch-specific artifact versions prevent collisions (e.g., `0.0.1-feature-login-SNAPSHOT`)
- Standard `mvn clean install` still works (no wrapper required, but no branch isolation)
- Wrapper scripts provide automatic branch isolation for developers
- Future-proof for Maven Central releases via tag-based CI workflow

**Documentation References:**
- Root POM: `pom.xml` (CI-friendly version properties)
- Configuration: `.mvn/maven.config` (default Maven properties)
- Wrappers: `scripts/mvn-branch.sh`, `scripts/mvn-branch.ps1`
- Documentation: `docs/DEVELOPMENT.md` (Maven Version Management section)

---

### 2026-01 - Enrollment Uniqueness Constraint

**Completion Date:** January 28, 2026
**Status:** ✅ **FULLY IMPLEMENTED AND VALIDATED**

| File | Purpose | Status |
|------|---------|--------|
| **enrollment_uniqueness_constraint_implementation.md** | Consolidated implementation plan with design evolution and test coverage | ✅ Completed |

**What Was Implemented:**
- ✅ Database partial unique index for VERIFIED enrollments
- ✅ Application-level validation in `EnrollmentService.create()`
- ✅ Application-level validation in `EnrollmentVerifyService.verify()`
- ✅ Enhanced audit logging for all scenarios (creation rejection, replacement, verification rejection)
- ✅ Comprehensive unit tests (Admin API, Auth API)
- ✅ Comprehensive integration tests (8 tests, all passing)
- ✅ PostgreSQL boolean parsing fixes in tests

**Key Features:**
- Prevents duplicate VERIFIED enrollments with same name per integration
- Allows multiple CREATED enrollments for retry scenarios
- Rejects creation when active VERIFIED exists (directs to recovery process)
- Allows replacement when inactive VERIFIED exists
- Database constraint enforcement at DB level
- Complete audit trail for SOC2 compliance

**Documentation References:**
- Migration: `V28__enrollment_unique_verified_name.sql`
- Tests: `EnrollmentUniquenessIntegrationTest.java`, `EnrollmentControllerAuditTest.java`, `EnrollmentControllerVerifyAuditTest.java`

---

## 📖 Current Plans

For active development plans, see: `.cursor/plans/`

---

## 🗄️ Why Keep Archived Plans?

**Historical Value:**
1. **Decision Record** - Documents design decisions and rationale
2. **Evolution Tracking** - Shows how features evolved during implementation
3. **Learning Resource** - Future developers can understand the thought process
4. **Audit Trail** - Compliance and security audit purposes
5. **Reference** - Can be consulted during similar feature development

**Do Not:**
- ❌ Use these for current implementation guidance
- ❌ Follow outdated workflows described here
- ❌ Reference API endpoints from these plans (may be obsolete)

**Do:**
- ✅ Consult for understanding historical context
- ✅ Learn from design decisions and trade-offs
- ✅ Reference during system refactoring discussions
- ✅ Use as examples for similar feature planning

---

## 📝 Archive Process

When archiving a completed plan:

1. **Verify Completion**: Ensure all todos are completed and tests pass
2. **Add Archive Header**: Add archive notice at top of plan file
3. **Move to Archive**: Move plan to appropriate `YYYY-MM` directory
4. **Update README**: Add entry to this README with completion date and summary
5. **Update Status**: Mark plan as archived in frontmatter if applicable

**Archive Header Template:**
```markdown
---
status: archived
archived_date: YYYY-MM-DD
completion_status: fully_implemented
---

# ⚠️ ARCHIVED PLAN

**This plan has been fully implemented and archived for historical reference.**

**Completion Date:** YYYY-MM-DD
**Status:** ✅ Fully Implemented and Validated

[Original plan content follows...]
```
