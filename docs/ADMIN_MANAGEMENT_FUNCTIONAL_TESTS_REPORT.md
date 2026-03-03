# Admin Management Quick Wins — Functional Tests Report

Brief analysis of functional (integration/security) test impact and recommendations, aligned with project values: simplicity, pragmatism, maximum value with minimum complexity, independence, opportunism, DB helpers when needed.

---

## 1. Existing Coverage

| Area | Existing functional tests | Relevance to quick wins |
|------|---------------------------|--------------------------|
| **Admin provisioning** | `TenantBoundaryPermissionsSecurityTest`: create global/tenant admin, list admins (TenantAdmin sees only own tenant). `TenantAdminTestHelper`: POST /admins/tenant, GET /admins/{id}/onboarding. | List already exercised; create flow already used in multi-tenant and boundary tests. |
| **Tenant deactivation** | `TenantDeactivationSecurityTest`: deactivate tenant, token revocation, login blocked. | Covers tenant lifecycle, not **admin** deactivate/activate. |
| **Audit** | `AuditLogTenantVisibilityTest`, `AuditReasonJustificationTest`: GET /audit-logs with filters (eventType, eventStatus, etc.). | No test currently uses `targetAdminId` filter. |

None of the existing tests call **GET /admins/{id}**, **POST /admins/{id}/activate**, or **POST /admins/{id}/deactivate**. The new fields (**lastLoginAt**, **targetAdminId** in audit) are not asserted.

---

## 2. Risk of Breaking Existing Functional Tests

- **Admin list response**  
  Tests that parse list `content` (e.g. `response.jsonPath().getList("content")`) and read `adminId`, `tenantId`, `adminType` do **not** depend on the exact set of fields. Adding **lastLoginAt** (nullable) does not break parsing or existing assertions.
- **Audit log response**  
  No test today asserts on `targetAdminId`. Adding it is backward compatible.
- **No test** calls GET /admins/{id}, activate, or deactivate, so no existing test can fail because of these new endpoints.

**Conclusion:** No change required to existing functional tests for compatibility.

---

## 3. Value vs Complexity for New Tests

| Candidate | Value | Complexity / maintenance | Recommendation |
|-----------|--------|----------------------------|----------------|
| **New test class: “Admin lifecycle” (create → deactivate → login 403 → activate → login 200)** | E2E proof of full lifecycle. | New class, several steps, token/login timing, ordering. | **Skip for now.** Unit tests already cover service rules; lifecycle is two POSTs. Add only if a real regression appears. |
| **New test: GET /admins/{id} only** | Ensures endpoint and auth (GlobalAdmin vs TenantAdmin scope). | New test method, need an existing adminId (from list or create). | **Prefer bonifying an existing test** (see below) instead of a new test. |
| **New test: Audit with targetAdminId** | Ensures filter works and ADMIN_CREATED has target_admin_id. | New test or extra step; depends on audit format and ordering. | **Optional.** Low added value vs maintenance; filter is a single predicate. Skip unless audit regression becomes a concern. |
| **Bonification: in “TenantAdmin can list only own tenant admins”, add GET /admins/{id}** | Reuses existing list; checks that GET-by-id works and returns expected shape (e.g. adminId, lastLoginAt) for TenantAdmin scope. | One extra request + 2–3 assertions in an existing test. Does not change the test’s intent (boundary permissions). | **Recommended.** Small, opportunistic, no new test, no new class. |

---

## 4. Recommended Approach

- **Do not add** a dedicated functional test class for admin lifecycle (deactivate/activate) or for GET-by-id alone.
- **Do not add** a functional test whose main goal is to assert `targetAdminId` in audit logs, unless audit regression becomes a real need.
- **Bonify one existing test** in `TenantBoundaryPermissionsSecurityTest`: in **testTenantAdminCanListOnlyOwnTenantAdmins**, after the existing loop that checks tenant scope:
  - If the list is not empty, take the first admin’s `adminId`, call **GET /admins/{id}** with the same TenantAdmin token.
  - Assert 200 and that the response contains `adminId` and `lastLoginAt` (key present; value may be null).
- That keeps the test’s focus (TenantAdmin sees only own tenant; list + get-by-id are consistent) and adds minimal, predictable coverage for GET /admins/{id} and the new DTO shape.

---

## 5. Summary

| Question | Answer |
|----------|--------|
| Do we need new functional tests for the plan? | **No.** No mandatory new test class or new test method. |
| Do we risk breaking existing functional tests? | **No.** New fields and endpoints are additive. |
| Is there a low-cost bonification? | **Yes.** One extra request and a couple of assertions in `testTenantAdminCanListOnlyOwnTenantAdmins` (GET /admins/{id} + adminId/lastLoginAt). |
| What do we skip on purpose? | Dedicated E2E for admin deactivate/activate; dedicated test for audit `targetAdminId`. |

This stays aligned with project values: simplicity, pragmatism, no accidental complexity, independence and opportunism (reuse existing list + token), and no extra test surface unless it clearly pays off.
