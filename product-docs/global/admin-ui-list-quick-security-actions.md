# Admin UI — List quick security actions

## Purpose

Canonical rule for **trailing action columns** on paginated Admin UI lists: when a one-click
control belongs in the list row versus only on the detail page. Complements
[`admin-ui-paginated-screens-matrix.md`](admin-ui-paginated-screens-matrix.md) (what columns to
show) and [`docs/LIFECYCLE_GOVERNANCE.md`](../../docs/LIFECYCLE_GOVERNANCE.md) (what each action
means).

**Grilled:** operator discussion 2026-06-18 (Ezkey is never the operator's main job; breach response
must stay low-friction).

## Product rationale

Ezkey is **MFA and credential governance**, not the PME's core business. Operators visit the Admin
UI briefly — to confirm health, or to **act during a suspected compromise** (real or false
positive).

Design consequences:

- **Speed over ceremony** when cutting exposure matters.
- **Low cognitive load:** the right control should be findable without opening a detail page, *when*
  the action is a normal incident response step.
- **Do not duplicate full lifecycle UI** in every list — exceptional or high-blast-radius actions
  stay on detail with reason dialogs and guardrails.

This aligns with **Design Principle #5** (operator-first), **#10** (Admin UI sobriety), **#11**
(reversible before irreversible), and **#12** (security as posture). It does **not** justify inline
delete, retire, or other rare terminal actions.

## The rule

Add a **trailing actions column** (right edge) only when **all** of the following hold:

1. **Incident-relevant** — operators routinely need this action during security response or
   credential hygiene (not configuration, not investigation-only surfaces).
2. **List-scannable context is enough** — the row already identifies the target; no extra fields
   are required to decide safely at list depth.
3. **Confirmations contain risk** — destructive or irreversible steps still use a dialog, reason
   when required by lifecycle policy, and `stopPropagation` so row navigation is not triggered.

**Reversibility is not the gate.** API key **revoke** is irreversible and still qualifies because
machine credentials are cheap to re-issue and ambiguity about a "paused" key is worse than a clean
revoke (see lifecycle governance). **Reversible** suspend/deactivate actions qualify when they
reduce exposure quickly during investigation.

**Icon vs labeled button:** prefer a **labeled button** when the action is reversible and wording
disambiguates intent (`Deactivate`). Prefer **icon + tooltip** when space is tight and the action is
already unambiguous in security ops (`Revoke` with `ShieldOff`). Do not mix patterns on the same
screen without reason.

## Entity eligibility

| Surface | Quick list action? | Action | Reversible | Status |
|---------|-------------------|--------|------------|--------|
| Dashboard | No | — | — | Not a list |
| Auth attempts | No | — | — | Read-only investigation |
| Audit logs | No | — | — | Read-only investigation |
| Alerts | No | — | — | Resolve/snooze on detail or dedicated flow |
| Encryption keys | No* | Re-encrypt (maintenance) | — | *Existing row action is **ops maintenance**, not incident response; out of scope for this pattern |
| Re-encryption batches | No | — | — | Progress monitoring |
| Integrations | No | Retire | No | Too exceptional; detail + reason only |
| Tenants | No (default) | Deactivate | Yes | High blast radius; detail + confirmation preferred |
| Enrollments | **Yes (planned)** | Deactivate / Reactivate | Yes | Strong candidate — not yet in list UI |
| API keys | **Yes** | Revoke | No | Implemented — icon + tooltip |
| Admins | **Yes** | Deactivate | Yes | Implemented — labeled button (Global Admin, active rows) |

### Enrollment (follow-on)

List inline **deactivate** (and reactivate where applicable) should mirror admins: trailing column,
dialog confirmation, row click still opens detail. **Revoke** and **delete** remain detail-only
with required reason per lifecycle governance.

### API keys (normative)

Keep the trailing **Revoke** control in the list. Column order and labels follow the Tier A API
keys TB; the actions column is **not** trimmed for column-count hygiene.

## Anti-patterns

- Inline **Retire** on integrations or **tenant deactivate** without strong extra guards — blast
  radius too large for one-click list depth.
- Quick actions on **Tier B** high-volume lists (auth attempts, audit logs) — wrong operator mode.
- Multiple unrelated actions per row without hierarchy — at most one primary security action;
  secondary actions (e.g. admin onboarding credentials) may share the column when role-gated.

## Related documents

| Document | Role |
|----------|------|
| [`admin-ui-paginated-screens-matrix.md`](admin-ui-paginated-screens-matrix.md) | Per-screen list columns |
| [`docs/LIFECYCLE_GOVERNANCE.md`](../../docs/LIFECYCLE_GOVERNANCE.md) | Action semantics and reasons |
| [`operator-alignment-guide.md`](operator-alignment-guide.md) | Operator posture and 3-second test |
| [`design-principles.md`](design-principles.md) | Principles #5, #10, #11, #12 |
| [`backlog/TB-2026-06-18-admin-ui-lists-tier-a-api-keys.md`](backlog/TB-2026-06-18-admin-ui-lists-tier-a-api-keys.md) | Next Tier A list slice |
