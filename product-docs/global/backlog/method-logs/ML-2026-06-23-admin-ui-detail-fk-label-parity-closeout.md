# Method log — Detail FK label parity closeout

**Date:** 2026-06-23  
**Slice:** `TB-2026-06-23-admin-ui-detail-fk-label-parity`  
**Parent:** `I-2026-0028`

## Signal

Post-P4 editorial review found two detail surfaces still showing bare `#id` links where Tier A/B
lists already expose human labels: admin detail → enrollment, enrollment detail → created-by admin.

## Delivered

- Admin API: `enrollmentName` on `AdminResponseDto`; admin usernames on enrollment GET/PATCH detail
- Admin UI: `fk-detail-links.tsx`, updated admin/enrollment/integration/api-key detail pages
- Matrix § Detail FK links; TB + `I-2026-0028` cross-links

## Validation

- Maven baseline + targeted admin-api tests
- Admin UI `npm run build`
- OpenAPI refresh pending stack availability

## Residual

- None for this slice; P3 matrix rows (encryption keys, alerts) remain separate backlog.
