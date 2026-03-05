# JPA Transaction Design Notes

Brief design notes for JPA transaction and persistence context usage in Ezkey. Intended to prevent recurring issues and guide future audits.

---

## Lessons Learned (Bootstrap / Optimistic Locking Investigation)

### 1. Self-Invocation Bypasses Proxy — Extra Sessions

**Issue:** When a Spring bean calls itself (e.g. `this::privateMethod` or `this.helper()`), the call does not go through the proxy. AOP interceptors such as `@Transactional` are not applied.

**Effect:** Each repository call runs in its own auto-commit session. Shared persistence context is lost. Example: enrollment saved in one session, admin saved in another → `TransientPropertyValueException` because the enrollment is not visible when the admin is flushed.

**Mitigation:** Place `@Transactional` on the entry point (e.g. event listener) so the whole flow runs in one transaction. Avoid passing `this::method` to executors or callbacks when that method must be transactional.

**Future:** Audit for self-invocation patterns that bypass transactional boundaries. Higher risk under multi-thread and high transaction load.

---

### 2. Nested @Transactional Anti-Pattern

**Issue:** Multiple service layers each annotated with `@Transactional`. When Service A calls Service B, each creates a transaction boundary. On exception propagation, inner transaction rollback can cause "Transaction Already Rolled Back" and obscure diagnostics.

**Mitigation:** Prefer a single transactional boundary at the top-level use case (controller/facade). Inner services should not declare `@Transactional` unless they explicitly need a separate transaction (e.g. `REQUIRES_NEW` for isolated work).

**Future:** Audit for stacked `@Transactional` across service layers. Document a clear rule (e.g. "transaction at facade only" or "one boundary per request").

---

## Audit TODO

- [ ] Scan for self-invocation of `@Transactional` methods (e.g. `this::`, lambdas capturing `this`)
- [ ] Scan for `@Transactional` on multiple service layers in the same call chain
- [ ] Document transaction boundary policy (where `@Transactional` is allowed)

---

## Implementation Notes (Optimistic Locking / Partial Update — March 2026)

### Phase 0: Version Column + PATCH Admin

**Scope:** Add `@Version` to updatable entities (Tenant, EzkeyAdmin, Enrollment, ApiKey), implement `PATCH /api/v1/admins/{id}` for partial profile update with optimistic locking.

### Issues Encountered

1. **Bootstrap-init broken** — `AdminBootstrapService.bootstrapAdminMfa()` passed `this::doBootstrapAdminMfa` to `LockingTaskExecutor`. The lambda invokes the method on `this`, bypassing the proxy. If `@Transactional` was only on `doBootstrapAdminMfa`, it was never applied. Result: enrollment and admin saved in separate auto-commit sessions → `TransientPropertyValueException` when linking admin to enrollment.
   - **Fix:** Place `@Transactional` on the entry point `bootstrapAdminMfa()` so the entire flow (including the executor’s task) runs in one transaction.

2. **Enrollment status constraint** — `EnrollmentBindService` / `EnrollmentVerifyService` mark expired enrollments as `EXPIRED` via `EnrollmentTxHelper.markExpiredAndEmitAudit()`. V1’s check constraint only allowed `CREATED`, `BOUND`, `VERIFIED`, `INVALID`, `REVOKED`. Constraint violation on `UPDATE`.
   - **Fix:** V45 adds `EXPIRED` to `ezkey_enrollment_enrollment_status_check`.

3. **Entity version defaults** — New `version` columns must have `DEFAULT 0` in migration so existing rows get a valid version. JPA `@Version` expects non-null.

### Test Gaps (Phase 0)

- **Unit:** `AdminProvisioningServiceTest` covers `updateAdmin` (success, stale version, not found). ✅
- **Controller:** `AdminProvisioningControllerTest` — PATCH tests added (success, stale version, not found). ✅
- **Integration:** `AdminProvisioningSecurityTest` — happy path (200) and stale version (409). ✅

### Proposed Next Action

1. **Close Phase 0 test gaps** (before extending to other entities):
   - Add controller-level tests for `PATCH /api/v1/admins/{id}` in `AdminProvisioningControllerTest`.
   - Add at least one integration test in `ezkey-tests` (e.g. happy path + 409 on stale version).
2. **Optional:** Run the Audit TODO (scan for self-invocation, nested `@Transactional`) to baseline the codebase.
3. **Phase 1 (future):** Extend partial update + optimistic locking to Tenant, Enrollment, or ApiKey if needed.
