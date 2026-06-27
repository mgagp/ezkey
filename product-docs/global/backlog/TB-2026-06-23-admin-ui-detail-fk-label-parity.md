# Tracer Bullet Brief — `TB-2026-06-23` Detail FK label parity

## Metadata

- **ID:** `TB-2026-06-23-admin-ui-detail-fk-label-parity`
- **Status:** `done`
- **Related idea:** `I-2026-0028` (post-P4 editorial closeout)
- **Lane:** `A` / `B` (Admin API enrichment + Admin UI detail surfaces)
- **Posture:** `single-pass`
- **Created at:** `2026-06-23`

## Objective

Align **detail-page foreign-key links** with Tier A/B list posture: human label + `(ID n)` when the
API enriches the FK; friendly fallback text (still linked) when the entity exists but the display
name is absent; plain `#id` (no link) when the referenced row is gone (audit-log semantics for
admin actors on enrollment detail).

## In scope

### Admin API

| DTO / endpoint | Enrichment |
|----------------|------------|
| `AdminResponseDto` | `enrollmentName` on GET admin (detail dialog) |
| `EnrollmentResponseDto` | `createdByAdminUsername`, `deactivatedByAdminUsername`, `revokedByAdminUsername` on GET/PATCH detail |
| `EnrollmentController` | Batch `resolveAdminUsernames()` for detail mapping |

### Admin UI

| Surface | Change |
|---------|--------|
| `admins.tsx` detail | `EnrollmentFkLink` with `enrollmentName` |
| `enrollment-detail.tsx` | `AdminFkLink` for created / deactivated / revoked-by |
| `integration-detail.tsx` | Tenant fallback link when name missing |
| `api-key-detail.tsx` | Integration name + `(ID n)` or list fallback |
| `fk-detail-links.tsx` | Shared `AdminFkLink` / `EnrollmentFkLink` |

### i18n

- EN/FR: `admins.detail.enrollmentFallback`, `enrollments.list.adminFallback`

## Out of scope

- Client-side on-demand FK fetch (retired with P4)
- New list-tier work; encryption keys / alerts matrix rows

## Validation

- Maven: `ezkey-admin-api` unit tests (mapper, controller)
- Admin UI: `npm run build`
- OpenAPI refresh + `npm run generate:api` when stack available
- Manual smoke: admin detail enrollment row; enrollment detail created-by; integration tenant row

## Links

- Matrix: [`../../admin-ui-paginated-screens-matrix.md`](../../admin-ui-paginated-screens-matrix.md) § Detail FK links
- P4: [`TB-2026-06-23-admin-ui-retire-related-details.md`](TB-2026-06-23-admin-ui-retire-related-details.md)
- Parent: [`ideas/I-2026-0028-admin-ui-operator-experience-follow-up.md`](ideas/I-2026-0028-admin-ui-operator-experience-follow-up.md)
