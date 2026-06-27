# Method log — `ML-2026-06-23-admin-ui-tier-a-completion-closeout`

## Metadata

- **Date:** `2026-06-23`
- **Slice:** Tier A completion — embedded enrollments + tenants
- **TB:** `TB-2026-06-23-admin-ui-tier-a-completion-embedded-tenants`
- **Idea:** `I-2026-0028` (P1 rows)
- **GitHub:** #252

## Summary

Closed the first P1 follow-up after Admin UI operator lists Tier A (#236 / PR #237):

1. **Integration detail → enrollments:** embedded table aligned with main `/enrollments` (identity →
   state → scope → time); operational status warning; trailing deactivate/reactivate; removed Active,
   Verified, Key tier columns.
2. **Tenants list:** documented operator-first columns; reordered to identity → state → scope →
   time; removed country from list (retained on detail).

Extracted `EnrollmentListLifecycleDialog` shared component to avoid duplicating lifecycle dialog logic.

## Validation

- `npm run build` in `ezkey-admin-ui` — pass
- Browser tests: not extended — reuse of existing lifecycle mutations and column patterns; manual
  exploratory recommended (clean-start, GA, integration detail enrollments + tenants list)

## Residual

- P2 audit logs — **closed** (PR #259); see [`ML-2026-06-23-admin-ui-audit-logs-tier-b-closeout.md`](ML-2026-06-23-admin-ui-audit-logs-tier-b-closeout.md)
- **P4 next:** [`TB-2026-06-23-admin-ui-retire-related-details.md`](../TB-2026-06-23-admin-ui-retire-related-details.md)
- Auth attempts list **Created** column uses absolute `formatDate` (aligned with Expires and Tier A lists); relative-only timestamps remain on some detail/dashboard surfaces (e.g. last login) as secondary hints — not part of this PR
