# Backlog Idea — `I-2026-0028` Admin UI operator experience — post–Tier A follow-up

## Metadata

- **ID:** `I-2026-0028-admin-ui-operator-experience-follow-up`
- **Status:** `ready`
- **Priority:** `P2`
- **Created at:** `2026-06-20`
- **Updated at:** `2026-06-20`
- **Last reviewed at:** `2026-06-20`
- **Phase tags:** `P1-operability`
- **Component tags:** `admin-ui`, `admin-api`
- **Lane:** `A` / `B` (mixed — see priority table)
- **Captured by:** Marc (session closeout after #236 / `feat/admin-ui-operator-lists-tier-a`)
- **GitHub issue:** _(none by design — product-docs canon; reopen GitHub only when a slice ships)_

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
| **P1** | **Integration detail → enrollments** list alignment | A | UI (+ reuse list patterns) | Small | Low | Visible drift: embedded list still shows Active / Verified / Key tier while main `/enrollments` uses Tier A columns + quick actions. API already returns enriched DTO. |
| **P1** | **Tenants** list — analysis + polish | A | Analysis → small UI | Small | Low | Last Tier A top-level row still `draft` in matrix. List already readable; work is operator review (trim/reorder?) not heavy joins. |
| **P2** | **Auth attempts** — integration + tenant labels | B | API (core DTO) + UI | Medium | Medium | List column Integration is a **placeholder** (`via #enrollmentId`). Needs `AuthAttemptDto` enrichment + perf-aware join. Separate TB. |
| **P2** | **Audit logs** — selective name joins | B | API + UI | Large | High | High volume; detail modal still shows `#integrationId`. Requires indexed join strategy per `I-2026-0014`. |
| **P3** | **Encryption keys** + **re-encryption batches** | A | Analysis | Small–medium | Low | Operator surface exists; matrix rows `draft`. Crypto-ops domain, not FK-label pattern. |
| **P3** | **Alerts** list | A | Analysis | Small | Low | List already usable; resolve/snooze stays detail per quick-actions canon. |
| **P3** | **Tenant detail → admins** embedded list | A | UI consistency | Small | Low | Low ROI vs main `/admins`; align only if touching tenant detail anyway. |
| **P4** | **Retire « More details / Plus de détails »** (`RelatedDetailsButton`, `useExpandableRelatedDetails`) | A/B | UI removal + doc | Medium | Medium | **End-state cleanup** after P1–P2 reduce FK-only surfaces. Interim pattern documented in `help.json` `patterns.fkRelatedDetails`. Do **not** start until list/detail labels cover daily operator paths. See § Related details retirement. |

### Explicit non-goals (keep out of this queue)

- Enrollment **revoke/delete** in list (detail-only by lifecycle governance)
- Tenant **deactivate** in list (detail-only — blast radius)
- Integration **retire** in list

## Related details retirement (interim → remove)

**Current mechanism:** `RelatedDetailsButton` + `useExpandableRelatedDetails` on several detail
pages; on-demand API fetch + TanStack Query cache reuse; EN **More details**, FR **Plus de détails**.

**Why it existed:** Paginated lists and detail panels showed FK IDs; operators needed a second click
to resolve names/links (`I-2026-0014` Tier A/B grill).

**Why it should go away:** As list APIs and detail headers expose **integrationName**, **tenantName**,
and links (Tier A program + follow-ups), the button adds cognitive load and duplicate fetching.
Operator daily path should be **scannable labels in the list**, not expand-in-place on detail.

**Retirement criteria (all should be true before removal):**

1. Matrix Tier A bounded lists used daily are `implemented` with labels (including Tenants review).
2. Auth attempts Tier B slice shipped OR honest stub column removed.
3. Audit logs: at least filter context + detail paths show names where IDs appear today.
4. Help copy (`help.json` `fkRelatedDetails`) and any tooltip strings removed or rewritten.
5. Playwright spot-check on one detail page per former call site.

**Call sites (2026-06-20):** `integration-detail.tsx`, `admins.tsx` (detail panel), `api-key-detail.tsx`,
`enrollment-detail.tsx` (verify), `audit-logs.tsx` (verify). Grep `RelatedDetailsButton` before TB.

## Scope

- **In scope:** priority table above; promotion to `TB-*` per row when execution starts; matrix row
  updates; cross-links to #236 delivered TBs.
- **Out of scope:** new GitHub issue until operator chooses a row to implement; mobile; non-Admin UI.

## Promotion notes

When starting a row:

1. Create `TB-YYYY-MM-DD-admin-ui-<slug>.md` from template.
2. Update matrix row status.
3. Optional GitHub issue for PR visibility only.

Suggested **first TB candidates:** integration-detail enrollments alignment; Tenants analysis slice.

## Links

- Closed program: GitHub #236
- Matrix: [`../../admin-ui-paginated-screens-matrix.md`](../../admin-ui-paginated-screens-matrix.md)
- Quick actions: [`../../admin-ui-list-quick-security-actions.md`](../../admin-ui-list-quick-security-actions.md)
- Parent: [`I-2026-0013`](I-2026-0013-paginated-screens-functional-review.md),
  [`I-2026-0014`](I-2026-0014-paginated-screens-display-strategy.md)
- Delivered TBs: `TB-2026-06-18-admin-ui-lists-tier-a-{integrations,enrollments,api-keys}.md`,
  `TB-2026-06-20-admin-ui-enrollments-list-quick-actions.md`
- Historical plan: `.cursor/plans/archived/2026-04/fk_more_details_ux_04799c66.plan.md`
