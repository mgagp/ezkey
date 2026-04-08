---
name: Tenant toggle hardening
overview: Fix two confirmed bugs in the tenant activate/deactivate flow (double-submit UX bug and duplicate audit log on idempotent calls), and document the reason field contract decision aligned with EZKey's SME market positioning.
todos:
  - id: fix-ui-double-submit
    content: "Fix ToggleActiveDialog: update disabled prop to mutation.isPending || mutation.isSuccess || (reason.length > 0 && reason.length < 10)"
    status: completed
  - id: fix-service-return
    content: Change TenantService.activateTenant and deactivateTenant to return boolean (true = state changed, false = no-op)
    status: completed
  - id: fix-controller-audit
    content: Update TenantController to only emit audit log when service returned true (state actually changed); keep @RequestBody(required = false) as-is
    status: completed
  - id: fix-tests
    content: Add test asserting no audit log entry is produced when activate is called on an already-active tenant
    status: completed
isProject: false
---

# Tenant Activate/Deactivate — Hardening Plan

## 1. Root cause diagnosis: the double-submit bug

### Primary cause — `disabled ?? isLoading` nullish-coalescing trap

The `Button` component (`[button.tsx` line 42](ezkey-admin-ui/src/components/ui/button.tsx)):

```40:42:ezkey-admin-ui/src/components/ui/button.tsx
    <button
      ref={ref}
      disabled={disabled ?? isLoading}
```

The `??` operator only falls through to `isLoading` when `disabled` is `null` or `undefined`.
In `ToggleActiveDialog`, `disabled` is **always** explicitly `true` or `false`:

```289:296:ezkey-admin-ui/src/pages/tenant-detail.tsx
          <Button
            variant={isActive ? 'destructive' : 'primary'}
            isLoading={mutation.isPending}
            onClick={() => {
              if (isActive) deactivateMutation.mutate({ id: tenant.tenantId!, data: body });
              else activateMutation.mutate({ id: tenant.tenantId!, data: body });
            }}
            disabled={reason.length > 0 && reason.length < 10}
```

With an empty reason: `disabled = false`. Then `false ?? isPending = false` — the button is **never disabled by `isLoading`**. The spinner renders visually, but the `<button>` element is fully clickable. Every click during the in-flight API call fires another `mutate()`.

### Secondary cause — async gap between mutation success and dialog close

`onSuccess` is `async` and awaits two `invalidateQueries` calls before calling `onClose()`. During this window, `mutation.isPending = false`, `mutation.isSuccess = true`, and the dialog is still visible. The button loses its loading state and becomes interactive again before the dialog has closed.

### Root cause of duplicate audit entries — controller emits log unconditionally

The service correctly short-circuits when the tenant is already in the desired state:

```271:274:ezkey-admin-api/src/main/java/org/ezkey/admin/service/TenantService.java
    if (tenant.getActive()) {
      logger.info("Tenant {} is already active", tenantId);
      return;
    }
```

No DB write occurs. But the controller **always** calls `auditLogService.log(...)` after the service returns, regardless of whether a state change took place. A no-op call produces a misleading audit entry claiming the tenant was activated/deactivated when it was not.

---

## 2. Reason field — decision with market context

### Current contract

- Optional in both UI and backend.
- If provided: `@Size(min = 10, max = 500)` enforced in both layers.
- UI disables submit if reason is 1–9 chars.
- Both deactivation and activation share the same rule: already symmetric.

### Three options evaluated

**Option A — Keep optional (current behavior, no change)**
The audit log already captures who, what, and when. The reason enriches the why but is not structurally required to reconstruct what happened. For a small team where a single admin manages their own deployment, mandatory justification adds friction without commensurate value. Aligned with the project values (pragmatism, 80-20 rule).

**Option B — Make always required**
Closes the "why" gap in the audit trail. Correct for compliance-conscious operators. But it imposes a non-trivial workflow change on informal SME deployments: the admin is now required to type a justification every time, even for routine actions. This is counter to EZKey's "simple and practical" positioning for the SME/developer market confirmed in PRD section 2. Wrong for the default experience.

**Option C — Configurable policy property (default: optional)**
A property such as `ezkey.admin.audit.require-reason-for-tenant-toggle=false` would let compliance-focused operators enforce mandatory reasons without code changes. However, full implementation requires: a new `@ConfigurationProperties` class, a lightweight config API endpoint so the UI can adapt its labels and validation, and UI context/fetch logic. This is non-trivial complexity for a feature that the majority of EZKey's target deployments will never enable. Applies YAGNI; better deferred until there is actual demand.

### Decision: keep optional (Option A)

EZKey targets SMEs and developer teams that self-host for simplicity and low cost. The PRD explicitly names pragmatism and developer-friendliness as core values. SOC2 is a useful design reference for security hygiene, not a certification requirement for this market. The audit trail already satisfies the "who/what/when" triad that SOC2 CC6.3 minimally requires; the reason enriches the trail but is not mandatory even under SOC2 Type II.

The existing "optional but min-10 if provided" contract is a reasonable middle ground: it does not burden the informal operator, and it prevents meaningless single-character entries if someone does choose to document the action.

The operations are already symmetric: both deactivation and activation carry the same optional reason with the same validation. No change needed here.

**Future path:** If a paying or active community operator requests mandatory-reason enforcement, implement Option C at that point. The backend DTOs and controller are easy to harden; the main work is the UI-aware config endpoint.

---

## 3. Changes

### UI — `[ezkey-admin-ui/src/pages/tenant-detail.tsx](ezkey-admin-ui/src/pages/tenant-detail.tsx)`

Fix `disabled` on the submit button to cover both the primary double-submit and the async gap:

```tsx
disabled={mutation.isPending || mutation.isSuccess || (reason.length > 0 && reason.length < 10)}
```

- `mutation.isPending` — disables the button for the entire duration of the in-flight call (fixes the primary bug where `false ?? isPending` evaluated to `false`).
- `mutation.isSuccess` — disables the button during the async window between mutation completion and dialog close.
- `reason.length > 0 && reason.length < 10` — preserves the existing min-length guard unchanged.

The `body` construction (`reason || undefined`) stays as-is; the optional contract is unchanged.

### Backend — `[TenantService.java](ezkey-admin-api/src/main/java/org/ezkey/admin/service/TenantService.java)`

Change `activateTenant` and `deactivateTenant` from `void` to `boolean`:

- Return `true` when a state change was made and persisted.
- Return `false` on the idempotent early-exit path (already in target state).

No change to the no-op guard logic itself; it just surfaces the signal to the controller.

### Backend — `[TenantController.java](ezkey-admin-api/src/main/java/org/ezkey/admin/controller/TenantController.java)`

- Capture the `boolean` return from both service calls.
- Wrap `auditLogService.log(...)` in `if (stateChanged)` — audit events are only emitted when a real state transition occurs.
- `@RequestBody(required = false)` stays unchanged; the body remains optional.

### Tests — `ezkey-admin-api/src/test/`

- Add a unit test asserting that when `activateTenant` is called on an already-active tenant, the service returns `false` and the controller does **not** invoke `auditLogService.log(...)`.
- Mirror the same test for `deactivateTenant` on an already-inactive tenant.
- No changes required to existing tests (the reason field contract is unchanged).

---

## 4. Affected files

- `[ezkey-admin-ui/src/pages/tenant-detail.tsx](ezkey-admin-ui/src/pages/tenant-detail.tsx)`
- `[ezkey-admin-api/src/main/java/org/ezkey/admin/service/TenantService.java](ezkey-admin-api/src/main/java/org/ezkey/admin/service/TenantService.java)`
- `[ezkey-admin-api/src/main/java/org/ezkey/admin/controller/TenantController.java](ezkey-admin-api/src/main/java/org/ezkey/admin/controller/TenantController.java)`
- Relevant unit tests under `ezkey-admin-api/src/test/`

---

## 5. Phase 2 — Future work: cross-system reason-field policy

> **Purpose of this section:** The decisions made in this plan about the reason field (optional by default, configurable enforcement as a future path) apply to a single feature. The same pattern recurs across the entire admin surface. This section inventories the full scope, documents pre-existing inconsistencies, and frames the open questions that a dedicated future analysis plan must resolve before any cross-cutting implementation begins. It is intended to be used directly as the starting brief for that plan.

### 5.1 Current inventory of reason fields

The reason field appears in six controllers and six UI locale namespaces. As of this plan the state is as follows.

**Backend — controllers**

- `[AdminProvisioningController](ezkey-admin-api/src/main/java/org/ezkey/admin/controller/AdminProvisioningController.java)`: `deactivateAdmin`, `activateAdmin` — `@RequestParam(required=false)` + `@Size(min=10, max=500)`
- `[TenantController](ezkey-admin-api/src/main/java/org/ezkey/admin/controller/TenantController.java)`: `deactivateTenant`, `activateTenant` — `@RequestBody` record DTO + `@Size(min=10, max=500)` *(subject of this plan)*
- `[IntegrationController](ezkey-admin-api/src/main/java/org/ezkey/admin/controller/IntegrationController.java)`:
  - `delete` — `@RequestParam(required=false)` + `@Size(min=10, max=500)`
  - `revokeAllEnrollments` — `@RequestParam(required=false)` + `@Size(min=10, max=500)`
  - `deactivateAllEnrollments` — `@RequestParam(required=false)` + `@Size(max=500)` only (no `min`)
  - `reactivateAllEnrollments` — `@RequestParam(required=false)` + `@Size(max=500)` only (no `min`)
- `[EnrollmentController](ezkey-admin-api/src/main/java/org/ezkey/admin/controller/EnrollmentController.java)`: enrollment reset — via `EnrollmentResetRequestDto`
- `[ApiKeyController](ezkey-admin-api/src/main/java/org/ezkey/admin/controller/ApiKeyController.java)`: key revocation — `@RequestParam` or body (to confirm during Phase 2)
- `[EncryptionKeyController](ezkey-admin-api/src/main/java/org/ezkey/admin/controller/EncryptionKeyController.java)`: manual key rotation — reason forwarded to audit log

**UI — locale namespaces**

- `admins.json` — `toggle.reasonHint`: `"(min 10 chars, for audit trail)"` — implies optional
- `tenants.json` — `toggle.reasonHint`: `"(min 10 chars, for audit trail)"` — implies optional *(this plan)*
- `enrollments.json` — `reasonHint`: `"(min 10 chars, for audit trail)"` — implies optional
- `api-keys.json` — `reasonHint`: `"(optional — min 10 chars for audit trail)"` — explicitly optional
- `encryption-keys.json` — `reasonLabel`: `"Reason (min 10 chars)"` — no "optional" signal, ambiguous
- `integrations.json` — has **two distinct keys**: `reasonLabelRequired` and `reasonLabelOptional`, indicating action-level differentiation already exists at the UI layer

### 5.2 Pre-existing inconsistencies to resolve

The future plan must document and resolve the following before generalizing any policy.

1. **Min-length gap:** `deactivateAllEnrollments` and `reactivateAllEnrollments` only enforce `@Size(max=500)` — no minimum. All other actions enforce `min=10`. This is likely an oversight and should be aligned.
2. **Transport inconsistency:** Tenant toggle carries the reason in a `@RequestBody` record DTO; all other actions use `@RequestParam`. There is no documented rationale for the split. The future plan should standardize or explicitly justify the difference.
3. **UI labeling inconsistency:** `api-keys.json` explicitly says "optional"; `encryption-keys.json` omits any hint; all other namespaces say "for audit trail" without stating optional or required. `integrations.json` already splits into two distinct label keys. Users see mixed signals about whether the field carries weight.
4. **Action-severity mismatch:** Not all actions are equally impactful. Deleting an integration is irreversible; reactivating an enrollment is recoverable. A blanket policy (entirely optional or entirely required) may not be appropriate — some actions may warrant a required reason by default regardless of the operator's policy setting.

### 5.3 Open questions for the future plan

1. **Policy granularity:** Should a single global flag govern all reason fields, or should the policy be action-scoped (e.g., separate toggles for `tenant-toggle`, `integration-delete`, `enrollment-revoke-all`)? A single flag is simpler but coarser; per-action flags offer precision at the cost of configuration sprawl.
2. **UI awareness mechanism:** When the policy requires a reason, the UI must adapt at runtime (label text, hint text, client-side validation). Options: a lightweight `GET /api/v1/admin/ui-config` endpoint returning a policy map; including policy flags in an existing session/profile response; or having the UI enforce hardcoded rules independently of the backend. The chosen approach has implications for the API surface and for whether behavior can change without a UI redeployment.
3. **Backward compatibility:** Existing API consumers (scripts, Postman collections, functional tests) pass no reason. Making reason required is a breaking change to the API contract. The future plan must define a migration strategy: versioned endpoints, deprecation period, or conditional enforcement with a grace period.
4. **Severity tiers:** Should certain actions always require a reason regardless of the operator's policy (e.g., integration delete, enrollment revoke-all, because they are irreversible)? A tiered model — always-required / policy-governed / always-optional — would reflect operational reality better than a binary global flag.
5. **Default for the policy:** Confirmed in this plan: the right SME default is `false` (optional). The future plan should verify this remains correct across all action types, or whether some high-severity irreversible actions should default to `true` even for SME deployments.
6. `**EnrollmentResetRequestDto` alignment:** The enrollment reset carries reason in a request body DTO rather than `@RequestParam`. Confirm whether this is intentional and document why, or align it with the majority pattern.

### 5.4 Starting brief for the future plan

When opening the Phase 2 plan, use the following as the scope statement:

> **Objective:** Audit, standardize, and optionally enforce a cross-cutting reason-field policy across all admin lifecycle and destructive actions. Six controllers and six UI namespaces currently have independent, partially inconsistent implementations. The goal is a single coherent contract: consistent validation rules, consistent transport (body vs. param), consistent UI labeling, and an optional operator-level configuration that enforces mandatory reasons for audit compliance — without breaking backward compatibility for existing SME deployments.
>
> **Key inputs:** Section 5 of the tenant toggle hardening plan, which establishes the market positioning (SME, optional-by-default), the architectural option deferred to this phase (configurable property), and the full pre-existing inventory and inconsistencies.
>
> **First task:** For each action listed in §5.1, classify its severity (irreversible/high-impact vs. recoverable/low-impact) and its current reason enforcement. Use that classification matrix as the basis for deciding which actions belong in which tier of the future policy model (always-required / policy-governed / always-optional).

