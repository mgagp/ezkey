# Tracer Bullet Brief — `TB-2026-06-18` Admin UI operator lists — Tier A enrollments

## Metadata

- **ID:** `TB-2026-06-18-admin-ui-lists-tier-a-enrollments`
- **Status:** `done`
- **Posture:** `single-pass`
- **Related ideas:** `I-2026-0013`, `I-2026-0014`
- **GitHub:** #236
- **Lane:** `A`
- **Created at:** `2026-06-18`

## Objective

Second slice of the **operator lists** program: make the Enrollments list scannable by surfacing
**integration and tenant labels** from the list API (not client-side integration lookups), while
keeping the **primary enrollment ID** and trimming low-signal columns.

## Operator question

*Which devices/users are enrolled, for which integration, and in what state?*

## Target list columns

Column order (left → right): **identity → state → scope → time**.

| Column | Global Admin | Tenant Admin | Notes |
|--------|--------------|--------------|-------|
| ID (primary key) | yes | yes | Entity anchor |
| Name | yes | yes | Primary scan |
| Status (+ operational warning) | yes | yes | Lifecycle + exploitability |
| Integration name (link) | yes | yes | From list API join |
| User identifier | yes | yes | Integrator roster key; after integration |
| Tenant name (link) | yes | hidden | After user ID when shown |
| Created | yes | yes | Sortable audit anchor |
| Last used | yes | yes | Operational health signal; sortable |

Removed from list (detail page retains): active flag column, verified, key tier, challenge.

Status warning on **VERIFIED** when `operational === false` (covers enrollment deactivated locally
and parent-chain inactive); tooltip distinguishes local deactivation vs parent inactive. Active
filter remains in toolbar — no Active column.

## Boundaries in scope

- `EnrollmentResponseDto.tenantId` / `tenantName` + list population of `integrationName`
- `IntegrationRepository.findAllByIdWithTenant` batch load
- `EnrollmentController.search` enrichment via `toResponseWithIntegration`
- Admin UI `enrollments.tsx` column trim + labels; filter dropdown still uses `useIntegrations`
- Matrix row Enrollments → `implemented`

## Out of scope

- API keys, tenants list, Tier B lists
- Postman update (response DTO extension only)

## Acceptance evidence

- List API returns `integrationName` (+ `tenantId`/`tenantName` when integration resolves)
- Global Admin UI shows tenant name link; Tenant Admin does not
- Primary `enrollmentId` column unchanged
- Maven tests green for touched modules
- Functional regression: `EnrollmentManagementSecurityTest` list + getById enrichment assertions
- OpenAPI: clean-start + `./scripts/update-specs.sh --admin-only` + `npm run generate:api` (pending stack refresh)

## Links

- [`../admin-ui-paginated-screens-matrix.md`](../admin-ui-paginated-screens-matrix.md)
- [`TB-2026-06-18-admin-ui-lists-tier-a-integrations.md`](TB-2026-06-18-admin-ui-lists-tier-a-integrations.md)
