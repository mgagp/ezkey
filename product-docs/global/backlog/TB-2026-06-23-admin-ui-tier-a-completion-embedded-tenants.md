# Tracer Bullet Brief — `TB-2026-06-23` Tier A completion (embedded enrollments + tenants)

## Metadata

- **ID:** `TB-2026-06-23-admin-ui-tier-a-completion-embedded-tenants`
- **Status:** `done`
- **Related idea:** `I-2026-0028`
- **Lane:** `A`
- **Posture:** `single-pass`
- **GitHub issue:** #252
- **GitHub branch:** `feat/admin-ui-tier-a-completion`
- **Created at:** `2026-06-23`
- **Updated at:** `2026-06-23`
- **Captured by:** Marc (session after PR #237 / #236 closeout)

## Objective

Close the remaining **Tier A** operator-list gaps after #236: align the integration-detail
embedded enrollments table with the main `/enrollments` list, and finalize the **Tenants** top-level
list with a documented operator-first column set.

## Boundaries in scope

- Admin UI `integration-detail.tsx` — embedded enrollments `PaginatedTable`
- Admin UI `tenants.tsx` — top-level tenants list
- Product-docs matrix rows + `I-2026-0028` P1 queue updates

## Out of scope

- Auth attempts / audit logs (Tier B — separate TBs)
- Retire `RelatedDetailsButton` (P4 — prerequisites not met)
- Enrollment revoke/delete in list; tenant deactivate in list
- Admin API / OpenAPI changes (enriched enrollment DTO already on `search1`)

## First executable slice

1. Embedded enrollments: identity → state → scope → time columns; status operational warning;
   trailing deactivate/reactivate; drop Active / Verified / Key tier.
2. Tenants: document column decision; trim country from list (detail retains it); order
   identity → state → scope → time.

## Rollback or fallback posture

- UI-only; revert branch if column alignment regresses operator workflows.
- Embedded list can temporarily omit quick actions if lifecycle dialog reuse blocks — prefer shared
  component over duplication.

## Critical flows

- **Nominal:** Global Admin opens integration detail → Enrollments tab → scannable rows match main
  list semantics; deactivate/reactivate from embedded list refreshes table.
- **Nominal:** Global Admin opens `/tenants` → sees ID, name, status, organization, domain, created.
- **Exception:** Revoked enrollment rows show no inline lifecycle action (same as main list).

## Evidence plan

- `npm run build` in `ezkey-admin-ui`
- Manual exploratory: clean-start; integration detail enrollments + tenants list (GA)
- Matrix rows: Tenants + Integration detail → enrollments → `implemented`
- `I-2026-0028` P1 items marked done

## Quality gates

- Reuse enrollments i18n keys where possible (`enrollments` namespace for lifecycle copy)
- `stopPropagation` on row actions and scope links
- EN/FR parity only if new strings added

## Exit criteria

- Embedded enrollments column set matches Tier A enrollments list (minus redundant integration
  column; tenant column omitted on integration-scoped embed).
- Tenants matrix row `implemented` with documented target columns.
- GitHub issue closed via PR.

## Column decisions (documented)

### Integration detail → enrollments (embedded)

| Order | Column | Notes |
|-------|--------|-------|
| 1 | ID | Primary key kept |
| 2 | Name | |
| 3 | Status | Badge + operational warning when `VERIFIED && !operational` |
| 4 | User identifier | Scope |
| 5 | Created | |
| 6 | Last used | |
| 7 | Actions | Deactivate / Reactivate (not for REVOKED) |

**Removed:** Active, Verified, Key tier (detail page / main list no longer surface these).

### Tenants (top-level)

| Order | Column | Notes |
|-------|--------|-------|
| 1 | ID | Primary key |
| 2 | Name | |
| 3 | Status | Active/inactive + system tenant badge |
| 4 | Organization | Scope |
| 5 | Domain | Scope |
| 6 | Created | Time |

**Removed from list:** Country (secondary; remains on tenant detail).

## Links

- [`I-2026-0028`](ideas/I-2026-0028-admin-ui-operator-experience-follow-up.md)
- [`../admin-ui-paginated-screens-matrix.md`](../admin-ui-paginated-screens-matrix.md)
- [`../admin-ui-list-quick-security-actions.md`](../admin-ui-list-quick-security-actions.md)
- Predecessor: #236, PR #237
