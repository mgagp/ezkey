# Tracer Bullet Brief — `TB-2026-06-18` Admin UI operator lists — Tier A integrations

## Metadata

- **ID:** `TB-2026-06-18-admin-ui-lists-tier-a-integrations`
- **Status:** `done`
- **Posture:** `single-pass`
- **Related ideas:** `I-2026-0013`, `I-2026-0014`
- **GitHub:** #236
- **Created at:** `2026-06-18`

## Objective

First executable slice of the **operator lists** program: make the Integrations list answer the
operator question in ~3 seconds by surfacing **tenant labels** (not raw FK IDs) for Global Admins,
while keeping the **primary integration ID** column visible.

## Operator question

*Which applications are protected, under which tenant, and are they operational?*

## Target list columns

| Column | Global Admin | Tenant Admin | Notes |
|--------|--------------|--------------|-------|
| ID (primary key) | yes | yes | Always visible — entity anchor |
| Code | yes | yes | Business identifier |
| Name | yes | yes | Primary scan column |
| Status (+ operational warning) | yes | yes | Lifecycle + parent-chain block |
| Tenant name (link) | yes | hidden | Scoped tenant is implicit for TA |
| Created | yes | yes | Sortable audit anchor |

## Boundaries in scope

- `IntegrationResponseDto.tenantName` (Admin API list + detail mapping)
- `IntegrationRepository.findAll(Specification, Pageable)` with `@EntityGraph(tenant)`
- `IntegrationControllerMapper` tenant name mapping
- Admin UI `integrations.tsx` tenant column (Global Admin only)
- Matrix row Integrations → `implemented`
- Unit tests (mapper + DTO)

## Out of scope

- Enrollments, API keys, Tier B lists (follow-on TBs)
- Postman collection update (no request shape change beyond list DTO field)

## Acceptance evidence

- List API returns `tenantName` when tenant is joined
- Global Admin UI shows tenant name link; Tenant Admin does not see redundant tenant column
- Primary integration `id` column unchanged
- Maven tests green for touched modules
- Functional regression: `IntegrationManagementSecurityTest` (list + getById `tenantName`) ✅
- OpenAPI: clean-start + `./scripts/update-specs.sh --admin-only` + `npm run generate:api` ✅
- Agent recipe: [`docs/testing/AGENT_UI_VALIDATION.md`](../../../docs/testing/AGENT_UI_VALIDATION.md)

## Links

- [`../admin-ui-paginated-screens-matrix.md`](../admin-ui-paginated-screens-matrix.md)
- [`ideas/I-2026-0013-paginated-screens-functional-review.md`](ideas/I-2026-0013-paginated-screens-functional-review.md)
- [`ideas/I-2026-0014-paginated-screens-display-strategy.md`](ideas/I-2026-0014-paginated-screens-display-strategy.md)
