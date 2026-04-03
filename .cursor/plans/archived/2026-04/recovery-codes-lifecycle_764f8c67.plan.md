---
name: recovery-codes-lifecycle
overview: Analyze Ezkey’s current administrator recovery-code lifecycle and produce a pragmatic recommendation for exhaustion, regeneration, audit, and operator UX while minimizing API surface expansion.
status: implemented-tested
todos:
  - id: map-current-state
    content: Document the current recovery-code lifecycle, including generation, consumption, reset, depletion, and audit behavior.
    status: completed
  - id: compare-options
    content: Compare replace-all, top-up, and no-change lifecycle options against Ezkey’s simplicity and security goals.
    status: completed
  - id: api-shape
    content: Recommend whether to reuse existing APIs or add one narrow regeneration endpoint with clear permissions.
    status: completed
  - id: operator-policy
    content: Define the recommended operator process, UI warnings, and audit posture for regeneration and exhaustion handling.
    status: completed
isProject: false
---

# Recovery Codes Lifecycle Analysis

## Objective

Produce a decision-ready analysis for administrator recovery codes in Ezkey: how they are currently consumed, what happens when they are depleted, whether existing APIs can reasonably support regeneration, and what simple lifecycle policy best fits the product.

## Confirmed Current State

- Recovery codes are generated at admin creation and bootstrap, shown once, then stored only as BCrypt hashes in `[C:\github\ezkey\ezkey-admin-api\src\main\java\org\ezkey\admin\service\AdminRecoveryService.java](C:\github\ezkey\ezkey-admin-api\src\main\java\org\ezkey\admin\service\AdminRecoveryService.java)` and `[C:\github\ezkey\ezkey-admin-api\src\main\java\org\ezkey\admin\service\AdminProvisioningService.java](C:\github\ezkey\ezkey-admin-api\src\main\java\org\ezkey\admin\service\AdminProvisioningService.java)`.
- Consumption today is: `POST /api/v1/admin/auth/recover` -> temporary recovery token -> `POST /api/v1/admin/enrollments/reset` -> rebind device. This is documented in `[C:\github\ezkey\docs\ENDPOINT.md](C:\github\ezkey\docs\ENDPOINT.md)` and `[C:\github\ezkey\docs\ADMIN_UI_RECOVERY.md](C:\github\ezkey\docs\ADMIN_UI_RECOVERY.md)`.
- Recovery count is configurable and currently defaults to 10 via `[C:\github\ezkey\ezkey-admin-api\src\main\java\org\ezkey\admin\config\AdminRecoveryProperties.java](C:\github\ezkey\ezkey-admin-api\src\main\java\org\ezkey\admin\config\AdminRecoveryProperties.java)` and application properties.
- A replacement operation already exists internally: `rotateRecoveryCodes(EzkeyAdmin)` in `[C:\github\ezkey\ezkey-admin-api\src\main\java\org\ezkey\admin\service\AdminRecoveryService.java](C:\github\ezkey\ezkey-admin-api\src\main\java\org\ezkey\admin\service\AdminRecoveryService.java)`, but it is not exposed by any controller or UI.
- The Admin UI shows codes once at create success and shows `codesRemaining` only during an active recovery session; there is no ongoing lifecycle screen in `[C:\github\ezkey\ezkey-admin-ui\src\pages\admins.tsx](C:\github\ezkey\ezkey-admin-ui\src\pages\admins.tsx)` and `[C:\github\ezkey\ezkey-admin-ui\src\components\feature\login-recovery-section.tsx](C:\github\ezkey\ezkey-admin-ui\src\components\feature\login-recovery-section.tsx)`.

## Analysis Work

1. Map the exact lifecycle and failure states.
  - Document creation, one-time display, storage, single-use consumption, depletion behavior (`no_codes_remaining`), audit events, and recovery-token semantics.
  - Call out the gap between current implementation and operational expectations once an admin approaches zero remaining codes.
2. Evaluate lifecycle policy options.
  - Option A: no change, rely on rare depletion and peer-admin help only.
  - Option B: explicit regeneration action that replaces the full set and invalidates all remaining codes.
  - Option C: top-up model that preserves unused codes and adds more.
  - Compare them on simplicity, operator clarity, security, auditability, and API coherence.
3. Evaluate API reuse versus new endpoint.
  - Test the design argument for reusing existing provisioning/onboarding endpoints versus adding one targeted admin-recovery rotation endpoint.
  - Recommendation should prefer reuse only if semantics stay clean; otherwise propose one narrow endpoint rather than overloading provisioning APIs.
4. Define the recommended operator process.
  - Decide who can regenerate: likely peer-admin initiated for another admin first, with self-service considered only if it does not weaken break-glass posture.
  - Decide when regeneration is allowed: on demand, not only at zero.
  - Decide what happens to remaining codes: likely invalidate-and-replace to match common practice and avoid partial-set ambiguity.
  - Decide UI feedback: one-time display, explicit warning that prior unused codes are revoked, no email/SMS dependency.
  - Decide audit behavior: audit the regeneration action itself, but avoid noisy alert-style events just because stock is low.
5. Compare with product norms and external practice.
  - Use GitHub/Google/Microsoft guidance as comparables: generating a new set invalidates the old set, codes remain one-time, and users are expected to store them securely.
  - Translate those patterns to Ezkey’s admin/tenant model and peer-admin operational reality.

## Expected Recommendation Direction

- Prefer a simple lifecycle: keep current recovery consumption flow unchanged, add a dedicated regeneration capability, and treat regeneration as full rotation of the set.
- Favor peer-admin initiated regeneration for another admin as the primary operational path, since Ezkey already supports admin-to-admin provisioning and avoids adding weak side channels.
- Avoid low-stock alerts for now; keep visibility in-platform and audit the actual regeneration action instead of generating passive warning noise.
- If implementation follows later, likely touch `[C:\github\ezkey\ezkey-admin-api\src\main\java\org\ezkey\admin\service\AdminRecoveryService.java](C:\github\ezkey\ezkey-admin-api\src\main\java\org\ezkey\admin\service\AdminRecoveryService.java)`, `[C:\github\ezkey\ezkey-admin-api\src\main\java\org\ezkey\admin\controller\AdminProvisioningController.java](C:\github\ezkey\ezkey-admin-api\src\main\java\org\ezkey\admin\controller\AdminProvisioningController.java)` or a dedicated admin controller, `[C:\github\ezkey\docs\ENDPOINT.md](C:\github\ezkey\docs\ENDPOINT.md)`, and the admin UI admin-management flow.

## Decision Notes To Produce

- Recommended lifecycle policy and rationale.
- Recommended API shape and permissions.
- Recommended UI/operator wording and warning text.
- Recommended audit event scope.
- Explicitly rejected alternatives and why.

## Post-Implementation Manual QE Tests

These manual functional checks are intended for a **clean start** environment that has just been
reset and booted successfully.

### Environment Preconditions

- Start from a clean local stack using the standard Bash clean start workflow.
- Confirm the Admin API, Admin UI, and backing database are all running normally.
- Log in as the initial Global Admin created by the clean start.
- Use the current default recovery-code count of `5` as the baseline expectation.

### Manual Test Cases

1. **Theme badge: `Routine key rotation`**
   Verify initial provisioning returns exactly five one-time recovery codes.
   - Open the administrator management screen as Global Admin.
   - Create a new Tenant Admin from the clean-start instance.
   - Confirm the success dialog shows exactly `5` recovery codes.
   - Confirm the dialog warns that the codes are shown once and must be saved immediately.
   - Close the dialog only after confirming the explicit saved-codes acknowledgement flow works.
   - Re-open onboarding credentials for the new admin and confirm plaintext recovery codes are no longer retrievable there.

2. **Theme badge: `Scheduled key rotation`**
   Verify explicit recovery-code regeneration replaces the full previous set.
   - Open the detail view for the newly created active administrator.
   - Trigger `Regenerate recovery codes`.
   - Confirm the warning states that previous unused recovery codes are revoked immediately.
   - Confirm the success state returns a fresh set of exactly `5` codes.
   - Confirm the UI again requires the operator to acknowledge secure storage before closing.
   - Confirm the regenerated codes differ from the original set captured during create.

3. **Theme badge: `Compliance and audit`**
   Verify audit visibility for successful recovery-code regeneration.
   - After a successful regeneration, open the audit log UI as Global Admin.
   - Locate the event type `ADMIN_RECOVERY_CODES_REGENERATED`.
   - Confirm the action label matches recovery-code regeneration success.
   - Confirm the audit row links the acting admin and the target admin correctly.
   - Confirm structured details show the target admin, previous code count, new code count, and replacement semantics.

4. **Theme badge: `Security policy update`**
   Verify tenant-scoped authorization for peer-admin regeneration.
   - In the clean-start instance, create one tenant and at least two Tenant Admins under that tenant.
   - Log in as one Tenant Admin.
   - Open the second Tenant Admin detail view inside the same tenant.
   - Trigger recovery-code regeneration.
   - Confirm the operation succeeds and returns a new one-time set.
   - If a second tenant is available, verify that attempting the same action outside the caller tenant boundary is rejected.

5. **Theme badge: `End of access / offboarding`**
   Verify inactive administrators cannot receive regenerated recovery codes.
   - As Global Admin, deactivate a target administrator.
   - Open or refresh that administrator detail view.
   - Confirm the recovery-code regeneration action is no longer offered in the normal active-admin flow.
   - If the endpoint is invoked indirectly or from stale UI state, confirm the backend rejects the request with the inactive-admin rule.
   - Confirm the failure is audited as a regeneration failure, not as a success.

6. **Theme badge: `Revoked for security reasons`**
   Verify old recovery codes stop working immediately after regeneration.
   - Before regeneration, capture one unused recovery code from the original set.
   - Regenerate the administrator recovery codes successfully.
   - Attempt the login recovery flow with one code from the old set.
   - Confirm recovery is rejected.
   - Then retry with one code from the new set and confirm recovery succeeds, issuing a temporary recovery token.
   - Complete the enrollment reset flow and confirm the normal recovery funnel still works after the regeneration feature was introduced.

### Optional Regression Checks

- Verify Global Admin can regenerate its own recovery codes through the same UI flow.
- Verify the audit-log translations display the new event type and action labels in both supported UI locales.
- Verify the documented default of `5` is reflected consistently in the create flow, regenerate flow, and operator-facing documentation.

