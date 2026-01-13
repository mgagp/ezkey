# Cross-Tenant Isolation Test Failures – Investigation & Clarification Document

**Date:** January 12, 2026
**Context:** 4 test failures out of 11 in TenantCrossIsolationSecurityTest
**Purpose:** Clarify business rules and expected API behaviors for multi-tenant isolation

---

## Failure #1: testTenantAdminACannotCreateIntegrationForTenantB

**Status Code Mismatch:**
- Expected: 400 or 403
- Actual: 201 (Created Successfully)

**Test Intent:**
TenantAdmin A should NOT be able to create an integration for Tenant B.

**Observations:**
- TenantAdmin A is using their token to POST `/integrations` with `tenantId=B` in the request body.
- The API is returning 201, meaning the integration WAS created successfully.
- Based on `IntegrationService.createIntegration()`, when a TenantAdmin creates an integration, it is auto-assigned to their own tenant (tenantId from `createdByAdmin.getTenant()`), regardless of the `tenantId` field in the request.
- **Question:** Is the current behavior (auto-assign to own tenant) acceptable, or should TenantAdmin be explicitly forbidden from specifying a different tenantId?

**Scenarios to Clarify:**
1. **Auto-assign behavior (current):** TenantAdmin A's request is "downgraded" to tenantId=A; returns 201 (integration created for A). Test should expect 201 and verify integration landed in A, not B.
2. **Strict rejection (alternative):** TenantAdmin attempts to specify tenantId != own tenant; returns 400/403. API must validate and reject the mismatch.

**Recommendation:**
Need clarification on **business rule**: Should TenantAdmin be allowed to specify a different tenantId (with auto-correction), or should the API reject it outright?

---

## Failure #2: testTenantAdminACannotDeleteTenantBIntegration

**Status Code Mismatch:**
- Expected: 403 or 404
- Actual: 400 (Bad Request)

**Test Intent:**
TenantAdmin A should NOT be able to delete an integration belonging to Tenant B.

**Observations:**
- TenantAdmin A is calling DELETE `/integrations/{integrationBId}` (where integrationBId belongs to Tenant B).
- The API returns 400 (Bad Request) instead of 403 (Forbidden) or 404 (Not Found).
- A 400 typically indicates malformed request data, not access denial. This suggests the API may be validating something about the request payload or parameters, not access control.
- Possible causes:
  - Integration doesn't exist in A's tenant scope (should be 403/404 semantically).
  - API validation error unrelated to access control.

**Recommendation:**
Need clarification: Should the API return **403 (Forbidden)** to indicate "you don't have access to this resource" or **404 (Not Found)** to hide cross-tenant resources from unauthorized admins? If 400 is the current behavior, should it be changed to 403/404 for better semantics?

---

## Failure #3: testTenantAdminACannotDeleteTenantBEnrollment

**Status Code Mismatch:**
- Expected: 403 or 404
- Actual: 204 (No Content – successful deletion)

**Test Intent:**
TenantAdmin A should NOT be able to delete an enrollment belonging to an integration in Tenant B.

**Observations:**
- TenantAdmin A is calling DELETE `/enrollments/{enrollmentBId}` (where enrollmentBId is tied to integrationB in Tenant B).
- The API returns 204, indicating the enrollment WAS deleted successfully.
- This is a **critical isolation breach**: a TenantAdmin can delete resources from another tenant.
- Likely cause: Enrollment deletion does not enforce tenant-scoped access control; it only checks if the enrollment exists.

**Recommendation:**
**CRITICAL:** This must be fixed. The API must validate that the requesting TenantAdmin's tenant matches the enrollment's tenant (via its integration). Expected behavior:
- If TenantAdmin A requests DELETE on enrollmentB (which belongs to integrationB in Tenant B), return **403 Forbidden** or **404 Not Found**.
- Requires code review and fix in `EnrollmentController.delete()` or underlying service.

---

## Failure #4: testTenantAdminACanListOnlyOwnApiKeys

**Status Code Mismatch:**
- Expected: 200
- Actual: 500 (Internal Server Error)

**Test Intent:**
TenantAdmin A should list API keys; filtering should show only keys for integrations in their tenant.

**Observations:**
- TenantAdmin A is calling GET `/api-keys` (or similar listing endpoint).
- The API returns 500, indicating an unhandled exception (likely NPE, constraint violation, or assertion error).
- Possible causes:
  1. **API key creation failed silently** in test setup (used GlobalAdmin token; may have landed in wrong tenant or failed).
  2. **Listing endpoint has a bug** (e.g., NPE when filtering by tenant, missing field, or constraint violation).
  3. **API key DTOs missing fields** (similar to integrations lacking tenantId).

**Immediate Investigation Steps:**
1. Check Docker logs: `docker logs ezkey-admin-api --since 10m` for exception stacktrace.
2. Check DB: Query `ezkey_api_key` table to see if keys exist, what tenant they're assigned to, and what the integration_id references.
3. Verify test setup: Confirm API keys were created for integrations A and B (and that integrations exist in correct tenants).

**Recommendation:**
Likely requires **code fix in API key listing endpoint** to:
- Handle tenant-scoped filtering correctly.
- Verify no NPE or constraint issues.
- Ensure test setup properly creates API keys for the correct integrations.

---

## Summary Table

| # | Test | Expected Status | Actual Status | Type | Priority |
|---|------|-----------------|---------------|------|----------|
| 1 | Cannot Create Integration for B | 400/403 | 201 | Business Rule Clarity | Medium |
| 2 | Cannot Delete Integration B | 403/404 | 400 | Semantics/Access Control | Medium |
| 3 | Cannot Delete Enrollment B | 403/404 | **204** | **CRITICAL ISOLATION BREACH** | **CRITICAL** |
| 4 | Cannot List Only Own API Keys | 200 | 500 | Backend Bug/Test Setup | High |

---

## Next Discussion Points

1. **Failure #1:** Define policy for TenantAdmin specifying a different tenantId in integration creation requests.
2. **Failure #2:** Decide on 403 vs 404 semantics for cross-tenant resource access denial.
3. **Failure #3:** Confirm this is a bug; schedule code fix for enrollment delete access control.
4. **Failure #4:** Investigate stacktrace and test setup; likely quick win after investigation.
