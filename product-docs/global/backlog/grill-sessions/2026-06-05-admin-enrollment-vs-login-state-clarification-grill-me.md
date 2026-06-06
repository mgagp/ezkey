# Grill Me - 2026-06-05 (Admin enrollment vs admin login state clarification)

## Session control

| Field | Value |
|-------|-------|
| **Vision** | `V-2026-06-05-admin-enrollment-vs-admin-login-state-model` |
| **Backlog** | `I-2026-06-05-admin-enrollment-vs-admin-login-ux-clarity` |
| **Cross-links** | `docs/LIFECYCLE_GOVERNANCE.md`, `ezkey-admin-api/.../AdminProvisioningService.java`, `ezkey-admin-api/.../AdminAuthService.java`, `ezkey-admin-ui/src/pages/admins.tsx` |
| **Status** | `complete` |
| **Date** | `2026-06-05` |

## Preserved source signal (session)

- Operator observed a surprising state: newly created global admin with verified enrollment but no recovery codes.
- Investigation path included live stack DB inspection (`psql` in Docker PostgreSQL container) and service-flow correlation in code.
- Operator identified the mental shortcut explicitly:
  - "phone-level enrollment interaction feels indistinguishable from admin login",
  - while backend semantics differentiate them via admin-session completion (`last_login_at`) and eligibility gates.

## Evidence snapshot (session findings)

1. Target admin (`marie.dupont`) exists as `ACTIVE` with linked enrollment in `VERIFIED` state.
2. `recovery_codes` remained null for target admin.
3. `last_login_at` remained null at the time confusion occurred.
4. Audit evidence around target did not show recovery-code issuance events.
5. Service/UI gates require first authenticated admin sign-in before initial recovery-code issuance.

## Settled deductions

| ID | Deduction |
|----|-----------|
| D1 | This is not a cryptographic failure; it is a lifecycle/mental-model mismatch. |
| D2 | Enrollment verification and admin login completion must be treated as separate operator outcomes. |
| D3 | Recovery-code issuance behavior is coherent with current security posture. |
| D4 | UX currently allows an understandable but costly inference path for operators. |
| D5 | The distinction must be taught in-product, not only documented externally. |

## Design implications retained for follow-on

1. Represent two explicit states where decisions are made:
   - enrollment capability state,
   - admin login completion state.
2. For blocked security actions, show rationale inline instead of silent hiding.
3. Use consistent microcopy across activation, enrollment test, and admin detail contexts.
4. Preserve backend contract; solve primarily in state visibility and interaction guidance.

## Open questions for TB promotion

1. Should blocked actions be disabled-with-reason everywhere, or selectively in high-risk surfaces only?
2. What is the minimal copy set that improves comprehension without cluttering advanced operators?
3. Which single UI E2E path best demonstrates reduced confusion for first-time adopters?

## Outcome

Session signal captured and promoted to:

- vision orientation (`V-*`),
- actionable backlog idea (`I-*`),
- this grill record preserving reasoning fidelity.
