# Tracer Bullet Brief — `TB-2026-06-20` Enrollments list quick lifecycle actions

## Metadata

- **ID:** `TB-2026-06-20-admin-ui-enrollments-list-quick-actions`
- **Status:** `done`
- **Posture:** `single-pass`
- **Related ideas:** `I-2026-0013`, `I-2026-0014`
- **GitHub:** #236
- **Lane:** `A`
- **Created at:** `2026-06-20`

## Objective

Follow-on to Tier A enrollments columns: add **trailing deactivate/reactivate** on the enrollments
list per [`admin-ui-list-quick-security-actions.md`](../admin-ui-list-quick-security-actions.md).

## Operator question

*During incident response, can I suspend or restore an enrollment without opening the detail page?*

## In scope

- Trailing actions column on `enrollments.tsx` (paginated list and dashboard bucket views)
- Labeled **Deactivate** / **Reactivate** buttons with `stopPropagation`
- Reuse `lifecycleDialog` copy + `ReasonFieldRow` (`enrollment_lifecycle` presets)
- Orval `useDeactivate` / `useReactivate`; invalidate `['enrollments']` on success
- Canon doc enrollment row → implemented

## Out of scope

- Backend API changes (existing `POST .../deactivate` and `.../reactivate`)
- List inline revoke/delete (detail-only per lifecycle governance)
- Playwright extension (low-risk reuse of existing detail mutations; manual exploratory sufficient)

## Acceptance evidence

- Active non-revoked rows show **Deactivate**; inactive non-revoked rows show **Reactivate**
- Revoked rows show no action
- Dialog confirms with optional reason (min 10 when provided)
- List refreshes after success; row click still navigates to detail

## Links

- [`../admin-ui-list-quick-security-actions.md`](../admin-ui-list-quick-security-actions.md)
- [`TB-2026-06-18-admin-ui-lists-tier-a-enrollments.md`](TB-2026-06-18-admin-ui-lists-tier-a-enrollments.md)
