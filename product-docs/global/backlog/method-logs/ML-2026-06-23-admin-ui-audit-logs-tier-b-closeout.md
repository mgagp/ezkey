# Method log — `ML-2026-06-23-admin-ui-audit-logs-tier-b-closeout`

## Metadata

- **Date:** `2026-06-23`
- **Slice:** Audit logs Tier B selective name joins
- **TB:** `TB-2026-06-23-admin-ui-audit-logs-tier-b-labels`
- **Idea:** `I-2026-0028` (P2 audit logs row)
- **GitHub:** #258 (issue), PR #259 (merged)

## Summary

Closed P2 audit logs after merge of PR #259:

1. **Admin API:** batch enrichment on list + context (`adminUsername`, `targetAdminUsername`,
   `integrationName`, `enrollmentName`, `tenantName`); max four extra queries per page.
2. **Admin UI:** list **Admin** column; detail modal primary labels + links; conditional « More details »
   only when server labels missing; `adminListDetailHref` for admin deep links.
3. **Hygiene fixes in same PR:** Admins list **ID** column restored; tenant embedded admins aligned;
   GitHub issue label workflow (`github-issue-promote` skill).

## Validation

- Maven admin-api tests + `AuditLogMapperEnrichmentTest`
- `npm run build` (Admin UI)
- Operator manual validation (audit log admin link, Admins ID column)

## Residual

- **P4 next:** retire `RelatedDetailsButton` / `useExpandableRelatedDetails` — criteria 1–3 satisfied;
  promote `TB-2026-06-23-admin-ui-retire-related-details`.
- Matrix / TB / `I-2026-0028` updated in hygiene closeout on `main`.
- OpenAPI dispatched copies refreshed in PR #259.
