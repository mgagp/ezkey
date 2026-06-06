# Tracer Bullet Brief - `TB-2026-06-05` Admin enrollment vs admin login UX clarity (first cut)

## Metadata

- **ID:** `TB-2026-06-05-admin-enrollment-vs-admin-login-ux-clarity-first-cut`
- **Status:** `under-review`
- **Related idea:** `I-2026-06-05-admin-enrollment-vs-admin-login-ux-clarity`
- **Related vision:** `V-2026-06-05-admin-enrollment-vs-admin-login-state-model`
- **Grill session:** `product-docs/global/backlog/grill-sessions/2026-06-05-admin-enrollment-vs-login-state-clarification-grill-me.md`
- **GitHub issue:** `#191`
- **Lane:** `D`
- **Posture:** `single-pass`
- **Created at:** `2026-06-05`
- **Updated at:** `2026-06-05`
- **Captured by:** Marc + Copilot session synthesis

## Objective

Deliver the smallest end-to-end slice that removes the highest-impact confusion:

- "Enrollment interaction succeeded" is not equivalent to
- "Admin login completed and admin-security actions are now eligible."

The slice must improve operator comprehension without changing security semantics.

## Boundaries in scope

- **Admin UI (`ezkey-admin-ui`)**
  - Admin detail state framing for enrollment status vs admin login status.
  - Recovery-code initial issuance action behavior when first login is missing.
  - Activation/recovery microcopy consistency where confusion is likely.
- **Admin API contract usage (`ezkey-admin-api`)**
  - No endpoint semantic change required for first cut.
  - Ensure UI wording maps to existing gate conditions (`lifecycleStatus`, `lastLoginAt`, `hasRecoveryCodes`).
- **Documentation**
  - Short operator-facing clarification in lifecycle/operational docs if needed.

## Out of scope

- Recovery-code policy changes.
- Auto-issuance at activation.
- Enrollment protocol or auth attempt lifecycle changes.
- Broad redesign of admin screens.

## First executable slice

1. In Admin detail, show explicit two-state framing:
   - Enrollment: technical credential state.
   - Admin login: business/session completion state.
2. If initial recovery-code issuance is ineligible because first login is missing, show disabled action with clear rationale (not silent absence).
3. Add one concise, repeated statement in activation/recovery surfaces clarifying distinction.
4. Validate on live stack with one representative user journey:
   - verified enrollment + never logged in -> issuance unavailable with reason,
   - after first successful admin login -> issuance becomes available.

## Rollback or fallback posture

- If dual-state presentation adds too much visual noise, keep only:
  - disabled action + reason,
  - one high-signal explanatory note in detail view.
- Preserve all current backend eligibility gates unchanged.

## Critical flows

### Nominal path

1. Operator opens admin detail for an account with verified enrollment and no `lastLoginAt`.
2. UI clearly indicates enrollment is valid but login completion is pending.
3. Recovery-code initial issuance action is visible but blocked, with reason.
4. After first successful admin login, UI reflects eligibility and action can proceed.

### Critical exception path

- Account has active lifecycle and verified enrollment but remains non-eligible due to missing first login.
- Operator must not infer this as backend bug; UI must explain gating condition directly.

## Evidence plan

- **UI evidence:** one targeted Admin UI validation path (manual or Playwright) covering before/after first login eligibility transition.
- **API/behavior evidence:** confirm existing backend gates remain unchanged and messages remain contract-accurate.
- **Documentation evidence:** short note added/updated where operators troubleshoot recovery-code availability.

## Quality gates

- **Semantics gate:** wording must match true backend conditions and avoid oversimplification.
- **Clarity gate:** message should reduce confusion in < 10 seconds reading time for first-time operators.
- **Safety gate:** no accidental relaxation of recovery-code issuance eligibility.
- **Scope gate:** keep first cut minimal and focused on this confusion class.

## Exit criteria

- [ ] Operators can distinguish enrollment validity from admin login completion in detail view.
- [ ] Ineligible initial issuance is explicit and reasoned (not hidden).
- [ ] One cross-surface explanatory statement is present and consistent.
- [ ] Validation evidence confirms reduced ambiguity without contract changes.

## Links

- `product-docs/global/vision/V-2026-06-05-admin-enrollment-vs-admin-login-state-model.md`
- `product-docs/global/backlog/ideas/I-2026-06-05-admin-enrollment-vs-admin-login-ux-clarity.md`
- `product-docs/global/backlog/grill-sessions/2026-06-05-admin-enrollment-vs-login-state-clarification-grill-me.md`
- `docs/LIFECYCLE_GOVERNANCE.md`
