# V-2026-06-05-admin-enrollment-vs-admin-login-state-model

## Metadata

- **ID:** `V-2026-06-05-admin-enrollment-vs-admin-login-state-model`
- **Status:** `accepted`
- **Lane:** `D` - post-delivery evolution from observed operator confusion in real stack usage
- **Created at:** `2026-06-05`
- **Updated at:** `2026-06-06`
- **Captured by:** Marc + Copilot session synthesis

## Intent

Make the product model explicit in operator-facing UX and docs: **cryptographic enrollment validation** and **admin authentication completion** are related but distinct outcomes with different lifecycle effects.

This direction aims to prevent a high-probability mental shortcut: "device interaction succeeded, therefore admin login is complete." In Ezkey, that shortcut is false and operationally costly.

## Motivation

A live stack investigation (Docker + PostgreSQL + code-path correlation) exposed a recurrent ambiguity:

1. On phone, enrollment test and admin login can feel operationally indistinguishable.
2. In backend state, they are not equivalent:
   - enrollment verification updates enrollment lifecycle,
   - admin login completion updates admin session semantics and `last_login_at`.
3. Recovery-code initial issuance is intentionally gated by first authenticated admin sign-in.

The result is a predictable confusion even for expert operators. If this ambiguity exists for maintainers, it will affect adopters even more.

## Potential impact

- **Product milestones:** operator adoption quality, admin security workflow clarity
- **Components:** `admin-ui`, `admin-api`, `docs`
- **User segments:** global admins, tenant admins, first-time adopters, evaluators

## Signals and constraints

### Session-derived signals

- Target account (`marie.dupont`) had verified enrollment and active lifecycle, but no recovery codes.
- DB state confirmed `last_login_at` not set when the confusion occurred.
- Audit trail around the target did not show recovery-code issuance.
- Service/controller/UI gating aligns on the same model: first authenticated admin sign-in must be completed before initial recovery-code issuance.

### Product constraints

- Preserve security posture: no recovery-code exposure in unauthenticated activation bootstrap.
- Keep lifecycle semantics aligned with `docs/LIFECYCLE_GOVERNANCE.md`.
- Favor clear, low-friction UX cues over additional operational complexity.

## Directional decisions (orientation level)

1. Treat this as a **mental-model and workflow-clarity** concern, not a cryptographic defect.
2. Represent state as two explicit operator dimensions where relevant:
   - Enrollment capability state (binding/verification),
   - Admin login state (first login complete or not, last login timestamp).
3. Prefer explicit blocked-action reasons over hidden actions for sensitive admin-security steps.
4. Add narrative continuity across activation, enrollment test, and admin detail surfaces.

## Promotion criteria

Promote this vision note when all of the following are true:

1. Backlog idea defines concrete UX copy, state indicators, and gating behavior.
2. Test strategy captures at least one UI/user-flow validation proving confusion reduction.
3. Canonical operator guidance is updated to explain the two-state model in practical terms.

**Reached 2026-06-06** via PR `#192` / `I-2026-06-05-admin-enrollment-vs-admin-login-ux-clarity` first cut.

## Closeout

First slice delivered in Admin UI (dual-state indicators, blocked-action rationale, cross-flow copy).
Operator-facing lifecycle semantics remain aligned with `docs/LIFECYCLE_GOVERNANCE.md`. Further
broad admin lifecycle redesign is not implied by this closeout.

## Related documents

- `docs/LIFECYCLE_GOVERNANCE.md`
- `ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminProvisioningService.java`
- `ezkey-admin-api/src/main/java/org/ezkey/admin/service/AdminAuthService.java`
- `ezkey-admin-ui/src/pages/admins.tsx`
- `product-docs/global/backlog/ideas/I-2026-06-05-admin-enrollment-vs-admin-login-ux-clarity.md`
- `product-docs/global/backlog/grill-sessions/2026-06-05-admin-enrollment-vs-login-state-clarification-grill-me.md`
- [`V-2026-09-26-evaluator-bootstrap-admin-session`](V-2026-09-26-evaluator-bootstrap-admin-session.md) — community bootstrap session must keep enrollment ≠ login distinct
