# Cursor Plans Archive

**Purpose:** Archive completed and validated implementation plans for historical reference.

---

## 📋 Archive Organization

Plans are organized by completion date (YYYY-MM format) to maintain chronological order and easy retrieval.

---

## ✅ Archived Plans

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
