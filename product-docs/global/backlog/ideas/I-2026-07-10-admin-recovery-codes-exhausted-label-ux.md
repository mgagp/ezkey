# Backlog Idea — `I-2026-07-10` Admin recovery codes: exhausted-set vs initial-issuance label UX

## Metadata

- **ID:** `I-2026-07-10-admin-recovery-codes-exhausted-label-ux`
- **Status:** `captured`
- **Priority:** `P3`
- **Created at:** `2026-07-09`
- **Updated at:** `2026-07-09`
- **Last reviewed at:** `2026-07-09`
- **Phase tags:** `P3-polish`
- **Component tags:** `admin-ui`, `admin-api`, `docs`
- **Lane:** `D`
- **Captured by:** Marc (operator observation during recovery-code replenishment)

## Intent

Clarify Admin UI copy and/or API signals when an administrator has **previously received** recovery codes but the **stored set is now empty** because every code was consumed. Today the detail action reads like a first-time issuance ("Issue initial recovery codes"), which is technically aligned with the backend endpoint but misleading for operators who already managed a code set.

## Problem and value

- **Problem:** After consuming all single-use recovery codes, the Admin detail screen shows **"Issue initial recovery codes"** instead of **"Regenerate recovery codes"**. The action works (new codes are issued via `POST .../recovery-codes/issue-initial`), but the label suggests the administrator never had codes before.
- **Expected value:** Operator-facing wording matches lifecycle intent (replenish / rotate) without changing security behavior. Reduces micro-confusion during break-glass operations.

## Code analysis (2026-07-09)

### Confirmed behavior — not a hallucination

| Layer | Mechanism |
|-------|-----------|
| **API `hasRecoveryCodes`** | `true` only when `admin.getRecoveryCodes().length > 0` — i.e. **at least one unused hashed code remains**. See `AdminProvisioningController` mapping and OpenAPI description on `AdminResponseDto`. |
| **Consumption** | Each successful recovery use removes one hash from the array (`AdminRecoveryService.validateRecoveryCode`). When the last code is used, the array becomes empty → `hasRecoveryCodes = false`. |
| **Admin UI gating** | `canRegenerateAdminRecoveryCodes` requires `hasRecoveryCodes === true`. `canShowIssueInitialAdminRecoveryCodesAction` requires `hasRecoveryCodes !== true`. See `ezkey-admin-ui/src/pages/admins.tsx`. |
| **Backend endpoints** | `regenerate` rejects empty array ("use initial issuance instead"). `issue-initial` accepts empty array when `lastLoginAt` is set. Exhausted-set replenishment **must** call `issue-initial` today. |

### Lifecycle semantics gap

`docs/LIFECYCLE_GOVERNANCE.md` §3.8 describes recovery codes as a **generate-and-replace** consumable set. Operators who exhausted a set expect **regeneration / replenishment**, not "initial" issuance. The platform distinguishes:

1. **Never issued** — deferred onboarding; no codes in DB yet.
2. **Issued, some unused** — `hasRecoveryCodes = true` → UI shows Regenerate.
3. **Issued, all consumed** — array empty but admin has `lastLoginAt` → UI shows Issue initial (same API path as case 1 from the server's perspective).

Cases 1 and 3 share the same API surface and UI branch; only case 3 is confusing for operators.

### Functional impact

- **No security or correctness bug** observed: replenishment succeeds; audit uses `ADMIN_RECOVERY_CODES_ISSUED` for `issue-initial` and `ADMIN_RECOVERY_CODES_REGENERATED` for `regenerate` — exhausted replenishment is audited as "issued", not "regenerated".
- **Low severity** copy/mental-model issue; related to but distinct from closed `I-2026-06-05` (enrollment vs first-login eligibility).

## Scope

- **In scope (candidate fixes — pick smallest viable):**
  - **UI copy split:** When `lastLoginAt != null` and `hasRecoveryCodes !== true`, show operator wording such as "Issue new recovery codes" or "Replenish recovery codes" (EN/FR), while still calling `issue-initial` if backend unchanged.
  - **API clarity (optional):** Rename or supplement `hasRecoveryCodes` (e.g. `hasUnusedRecoveryCodes`) and/or expose `recoveryCodesEverIssued` or derive from audit — only if UI-only copy is insufficient.
  - **Endpoint unification (optional, larger):** Allow `regenerate` when the set is empty but the admin previously had codes — requires persistence or audit lookup; higher scope.
  - Align `docs/ENDPOINT.md`, Postman pre-check notes, and `docs/ADMIN_UI_RECOVERY.md` if contract or labels change.
- **Out of scope:**
  - Changing single-use consumption model.
  - Auto-issuing codes on exhaustion.
  - Broad admin lifecycle redesign.

## Key assumptions

- Current `issue-initial` vs `regenerate` split remains intentional for true first issuance (never had a set).
- A UI-only copy branch keyed on `lastLoginAt` + `!hasRecoveryCodes` may be enough for R1 polish.
- Audit event differentiation (issued vs regenerated) may matter for SOC narratives; document if UI calls `issue-initial` for exhausted replenishment.

## Risks and exceptions

- Mislabeling "regenerate" while calling `issue-initial` could confuse integrators reading OpenAPI — prefer honest copy ("issue new set") over false "regenerate" if endpoints stay split.
- Renaming `hasRecoveryCodes` is a contract change (Orval, SDK, Postman).

## Candidate first slice (TB candidate)

1. Admin detail: third label state for `ACTIVE` + `lastLoginAt` set + `hasRecoveryCodes === false` → "Issue new recovery codes" (not "initial"); keep disabled tooltip for missing first login unchanged.
2. Dialog title/intro for that state: mention replenishment after all codes were used.
3. EN/FR parity in `admins.json`.
4. Optional one-line note in `docs/ADMIN_UI_RECOVERY.md`.

## Promotion notes

Move to `ready` when fix approach is chosen (UI-only vs API flag vs endpoint unify) and audit/copy implications are agreed. Priority `P3` — polish lane; no release blocker.

## Links

- Operator observation: 2026-07-09 (all recovery codes consumed; Admin detail showed "Issue initial"; after success, label returned to "Regenerate" when unused codes existed again).
- Lifecycle canon: `docs/LIFECYCLE_GOVERNANCE.md` §3.8 Recovery Codes
- Related (done): `I-2026-06-05-admin-enrollment-vs-admin-login-ux-clarity`
- Code anchors:
  - `ezkey-admin-api/src/main/java/org/ezkey/admin/controller/AdminProvisioningController.java` (`hasRecoveryCodes` mapping)
  - `ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminProvisioningService.java` (`issueInitialRecoveryCodes`, `regenerateRecoveryCodes`)
  - `ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminRecoveryService.java` (single-use removal)
  - `ezkey-admin-ui/src/pages/admins.tsx` (`canRegenerateAdminRecoveryCodes`, `canShowIssueInitialAdminRecoveryCodesAction`)
  - `ezkey-admin-ui/src/locales/en/admins.json`, `fr/admins.json`
