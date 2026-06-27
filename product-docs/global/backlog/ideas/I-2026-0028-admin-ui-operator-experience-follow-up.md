# Backlog Idea — `I-2026-0028` Admin UI operator experience — post–Tier A follow-up

## Metadata

- **ID:** `I-2026-0028-admin-ui-operator-experience-follow-up`
- **Status:** `ready`
- **Priority:** `P2`
- **Created at:** `2026-06-20`
- **Updated at:** `2026-06-23`
- **Last reviewed at:** `2026-06-23`
- **Phase tags:** `P1-operability`
- **Component tags:** `admin-ui`, `admin-api`
- **Lane:** `A` / `B` (mixed — see priority table)
- **Captured by:** Marc (session closeout after #236 / `feat/admin-ui-operator-lists-tier-a`)
- **GitHub issue:** #252 (Tier A completion slice — embedded enrollments + tenants)

## Session restart phrase

Use this to resume prioritization with an agent or in plan mode:

> **Analyse les priorités documentées dans le backlog en ce qui concerne les améliorations à
> l'expérience UI pour la console administration.**

Expected anchor documents (read in order):

1. This file (`I-2026-0028`) — prioritized queue
2. [`../../admin-ui-paginated-screens-matrix.md`](../../admin-ui-paginated-screens-matrix.md) — per-screen status
3. [`../../admin-ui-list-quick-security-actions.md`](../../admin-ui-list-quick-security-actions.md) — list action rules
4. Parent ideas [`I-2026-0013`](I-2026-0013-paginated-screens-functional-review.md) /
   [`I-2026-0014`](I-2026-0014-paginated-screens-display-strategy.md)

Delivered program (closed): GitHub **#236**, branch `feat/admin-ui-operator-lists-tier-a`, TBs
`TB-2026-06-18-*` (integrations, enrollments, API keys) + `TB-2026-06-20-admin-ui-enrollments-list-quick-actions`.

**P2 follow-up (closed):** auth attempts #257 / PR #253; audit logs #258 / PR #259; admin ID column +
navigation fixes in PR #259.

## Intent

After the **Tier A operator lists** program (#236), capture the **remaining Admin UI operator
experience** work in one prioritized backlog so the next slice can be chosen without re-deriving
context. Includes list polish, embedded-list alignment, Tier B investigation surfaces, and
**retirement of the interim « More details / Plus de détails » pattern** once list/detail DTOs carry
enough labels.

## Problem and value

- **Problem:** Tier A delivered joins + column hygiene on integrations, enrollments, and API keys,
  but other paginated surfaces, embedded lists, and detail-page FK affordances remain inconsistent.
  The expandable **Related details** button was a bridge when lists exposed raw IDs; it becomes noise
  once labels are in list APIs and detail headers.
- **Expected value:** One canonical queue; explicit priority; clear “promote to `TB-*` when starting”
  path; no duplicate GitHub issues until execution begins.

## Prioritized follow-up queue

Order is **recommended execution sequence**, not strict dependency. Re-triage when operator feedback
or incident patterns change.

| Rank | Item | Tier | Type | Effort | Risk | Rationale |
|------|------|------|------|--------|------|-----------|
| **P1** | **Integration detail → enrollments** list alignment | A | UI (+ reuse list patterns) | Small | Low | **Done** — TB `TB-2026-06-23-admin-ui-tier-a-completion-embedded-tenants`, #252 |
| **P1** | **Tenants** list — analysis + polish | A | Analysis → small UI | Small | Low | **Done** — same TB; country trimmed from list |
| **P2** | **Auth attempts** — integration + tenant labels | B | API (core DTO) + UI | Medium | Medium | **Done** — TB `TB-2026-06-23-admin-ui-auth-attempts-tier-b-labels`, #257, PR #253 |
| **P2** | **Audit logs** — selective name joins | B | API + UI | Large | High | **Done** — TB `TB-2026-06-23-admin-ui-audit-logs-tier-b-labels`, #258, PR #259 |
| **P3** | **Encryption keys** + **re-encryption batches** | A | Analysis | Small–medium | Low | Operator surface exists; matrix rows `draft`. Crypto-ops domain, not FK-label pattern. |
| **P3** | **Alerts** list | A | Analysis | Small | Low | List already usable; resolve/snooze stays detail per quick-actions canon. |
| **P3** | **Tenant detail → admins** embedded list | A | UI consistency | Small | Low | **Done** — ID column + `adminListDetailHref` (PR #259) |
| **P4** | **Retire « More details / Plus de détails »** (`RelatedDetailsButton`, `useExpandableRelatedDetails`) | A/B | UI removal + doc | Medium | Medium | **Done** — TB [`TB-2026-06-23-admin-ui-retire-related-details`](../TB-2026-06-23-admin-ui-retire-related-details.md) |

### Explicit non-goals (keep out of this queue)

- Enrollment **revoke/delete** in list (detail-only by lifecycle governance)
- Tenant **deactivate** in list (detail-only — blast radius)
- Integration **retire** in list

## Related details retirement (shipped P4)

**Removed (2026-06-23):** `RelatedDetailsButton`, `useExpandableRelatedDetails`, and related help/i18n.
Detail surfaces now rely on **server-side labels** from list/detail DTOs; when enrichment is null but
an FK ID is present, the UI shows **`#id`** only (no on-demand expand).

**Retirement criteria:** all five items satisfied — see TB
[`TB-2026-06-23-admin-ui-retire-related-details`](../TB-2026-06-23-admin-ui-retire-related-details.md).

**Former call sites (now inline labels/links):** `integration-detail.tsx`, `admins.tsx`,
`api-key-detail.tsx`, `enrollment-detail.tsx`, `audit-logs.tsx`, `auth-attempts.tsx`.

## Scope

- **In scope:** priority table above; promotion to `TB-*` per row when execution starts; matrix row
  updates; cross-links to #236 delivered TBs.
- **Out of scope:** new GitHub issue until operator chooses a row to implement; mobile; non-Admin UI.

## Promotion notes

When starting a row:

1. Create `TB-YYYY-MM-DD-admin-ui-<slug>.md` from template.
2. Update matrix row status.
3. Optional GitHub issue for PR visibility only.

Suggested **next TB:** P3 matrix rows (encryption keys, alerts, audit chain checkpoints) when operator chooses.

## Links

- Closed program: GitHub #236
- P2 closed: #257 (auth attempts), #258 / PR #259 (audit logs)
- Matrix: [`../../admin-ui-paginated-screens-matrix.md`](../../admin-ui-paginated-screens-matrix.md)
- Quick actions: [`../../admin-ui-list-quick-security-actions.md`](../../admin-ui-list-quick-security-actions.md)
- Parent: [`I-2026-0013`](I-2026-0013-paginated-screens-functional-review.md),
  [`I-2026-0014`](I-2026-0014-paginated-screens-display-strategy.md)
- Delivered TBs: `TB-2026-06-18-admin-ui-lists-tier-a-{integrations,enrollments,api-keys}.md`,
  `TB-2026-06-20-admin-ui-enrollments-list-quick-actions.md`,
  `TB-2026-06-23-admin-ui-tier-a-completion-embedded-tenants.md`,
  `TB-2026-06-23-admin-ui-auth-attempts-tier-b-labels.md`,
  `TB-2026-06-23-admin-ui-audit-logs-tier-b-labels.md`
- Next: P3 matrix rows (encryption keys, alerts) — see priority table
- P4 closeout: [`TB-2026-06-23-admin-ui-retire-related-details`](../TB-2026-06-23-admin-ui-retire-related-details.md)
- Closeout: [`method-logs/ML-2026-06-23-admin-ui-audit-logs-tier-b-closeout.md`](../method-logs/ML-2026-06-23-admin-ui-audit-logs-tier-b-closeout.md)
