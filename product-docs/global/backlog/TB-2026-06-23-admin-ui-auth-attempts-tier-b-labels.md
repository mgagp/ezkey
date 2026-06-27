# Tracer Bullet Brief — `TB-2026-06-23` Auth attempts Tier B labels

## Metadata

- **ID:** `TB-2026-06-23-admin-ui-auth-attempts-tier-b-labels`
- **Status:** `done`
- **Related idea:** `I-2026-0028` (P2 auth attempts row)
- **Lane:** `A` / `B` (Tier B list enrichment)
- **Posture:** `single-pass`
- **GitHub issue:** #257
- **GitHub branch:** `feat/admin-ui-tier-a-completion`
- **Created at:** `2026-06-23`
- **Captured by:** Marc

## Objective

Replace the auth attempts list **integration placeholder** (`via #enrollmentId`) with **server-side
labels** (integration name, enrollment name, tenant name for Global Admin) using Tier B batch joins
per page.

## In scope

- `AuthAttemptDto` optional enrichment fields
- `AuthAttemptController` search + GET by ID batch/single enrichment
- `auth-attempts.tsx` column updates + i18n EN/FR
- Mapper unit test
- Matrix row → `implemented`

## Out of scope

- Audit logs selective joins (next P2 slice)
- P4 Related details retirement
- Integration API list enrichment (DTO fields null on M2M paths)

## Column decision

| Order | Column | Notes |
|-------|--------|-------|
| 1 | ID | Primary key |
| 2 | Status | Badge |
| 3 | Enrollment | Name link; ID fallback |
| 4 | Integration | Name link |
| 5 | Tenant | Global Admin only |
| 6 | Challenge | |
| 7 | Created | Relative time |
| 8 | Expires | |

## Join path

`AuthAttempt.enrollmentId` → `Enrollment` → `Integration` (+ tenant via integration).

Batch: `findAllById(enrollmentIds)` + `findAllByIdWithTenant(integrationIds)` per page.

## Exit criteria

- List shows integration name without client-side integration lookup stub
- Global Admin sees tenant name column
- `npm run build` + Maven admin-api tests pass
- OpenAPI refreshed when stack available
