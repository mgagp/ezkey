# Retrofit Slice — `R-2026-07-25-audit-vs-auth-expiry` Audit logs vs passwordless wait expiry

## Metadata

- **ID:** `R-2026-07-25-audit-vs-auth-expiry`
- **Status:** `integrated`
- **Source type:** `plan`
- **Capture date:** `2026-07-25`
- **Owner:** product + AI collaboration
- **Confidence:** `high`

## Source batch

- `.cursor/plans/audit_vs_auth_expiry.plan.md`

## Search scope

- Searched: `.cursor/plans/`, `product-docs/components/admin-api/`,
  `product-docs/components/admin-ui/`, `docs/AUDIT_ADMIN_LOGIN_ACTIONS.md`,
  `product-docs/global/legacy-retrofit/`
- Found relevant:
  - `.cursor/plans/audit_vs_auth_expiry.plan.md`
  - `product-docs/components/admin-api/functional-flows.md`
  - `product-docs/components/admin-api/exception-and-error-model.md`
  - `product-docs/components/admin-api/api-and-boundary-mappings.md`
  - `product-docs/components/admin-ui/screens-and-wireflow.md`
  - `docs/AUDIT_ADMIN_LOGIN_ACTIONS.md`
- Excluded:
  - Deep implementation TODOs inside the source plan (already represented in code/docs or tied to
    delivered behavior, not canonical methodology signal)

## Trigger

The source plan is a point-in-time working document with implementation framing, option branches,
and EN/FR copy drafts. This retrofit extracts durable methodology signal and avoids long-term drift
 from a historical planning artifact.

## Extracted decisions and invariants

- The `EventType` / `EventStatus` model remains stable and sufficient for this scope:
  `EventType` describes nature, `EventStatus` describes step outcome (`SUCCESS`, `FAILURE`,
  `ERROR`).
- In two-call admin login, a pending MFA request is a successful first step and must stay
  interpretable as such (`login_mfa_requested` with `SUCCESS`) while remaining distinct from
  session issuance.
- Legacy `login_pending` rows remain historical evidence and should be considered in transition-era
  analytics.
- For audit-log usage, column interpretation is role-agnostic: role changes visibility scope, not
  semantic reading of type/status/action.
- `event_action` remains the stable automation/SIEM field for filtering and export semantics.

## Mapping to canonical destinations

| Canonical destination | Mapping action | Status |
|-----------------------|----------------|--------|
| `product-docs/components/admin-api/api-and-boundary-mappings.md` | Added explicit pending-branch audit semantic and legacy cutover note. | integrated |
| `product-docs/components/admin-ui/screens-and-wireflow.md` | Added audit-log reading model and role-agnostic interpretation rule. | integrated |
| `product-docs/components/admin-api/functional-flows.md` | Reviewed; already captures terminal wait outcomes and no unique residual remained for this slice. | deferred (no change needed) |
| `product-docs/components/admin-api/exception-and-error-model.md` | Reviewed; already captures rejected/timeout problem mapping and audit ties. | deferred (no change needed) |

## Changes applied

- [x] `product-docs/components/admin-api/api-and-boundary-mappings.md` — added pending-step
  audit semantic and legacy compatibility note.
- [x] `product-docs/components/admin-ui/screens-and-wireflow.md` — added audit-log reading model
  and role interpretation invariant.
- [ ] `product-docs/components/admin-api/functional-flows.md` — deferred; covered already.
- [ ] `product-docs/components/admin-api/exception-and-error-model.md` — deferred; covered already.
- [x] `.cursor/plans/audit_vs_auth_expiry.plan.md` — annotated with `retrofitted_by` and
  `retrofitted_at`.

## Confidence and residual gaps

- **Confidence high** that backend-side semantics from the plan were already canonized before this
  retrofit.
- **Residual gap (optional follow-up):** if desired, extract the full EN/FR contextual-help draft
  from the source plan into a dedicated Admin UI documentation note. This is useful only if the
  team still wants copy-level canon, not just behavior-level canon.

## Next action

- The source plan can be safely removed once the operator confirms no additional copy-level
  extraction is required.

## Links

- Source plan: `.cursor/plans/audit_vs_auth_expiry.plan.md`
- Methodology: `product-docs/methodology/legacy-retrofit-workflow.md`