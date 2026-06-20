# Tracer Bullet Brief — `TB-2026-06-18` Admin UI operator lists — Tier A API keys

## Metadata

- **ID:** `TB-2026-06-18-admin-ui-lists-tier-a-api-keys`
- **Status:** `draft`
- **Posture:** `single-pass`
- **Related ideas:** `I-2026-0013`, `I-2026-0014`
- **GitHub:** #236
- **Lane:** `A`
- **Created at:** `2026-06-18`

## Objective

Third Tier A slice of the **operator lists** program: list API keys answer *which keys exist, for
which integration, and are they usable?* with **integration labels from the list API** (not client
lookup), optional **tenant name** for Global Admin, column order aligned with Enrollments, and
**trailing Revoke action preserved** per
[`admin-ui-list-quick-security-actions.md`](../admin-ui-list-quick-security-actions.md).

## Operator question

*Which machine credentials exist, for which integration, and can they call the Integration API?*

## Target list columns

Column order (left → right): **identity → state → scope → time → quick security action**.

| Column | Global Admin | Tenant Admin | Notes |
|--------|--------------|--------------|-------|
| ID (`apiKeyId`) | yes | yes | Entity anchor |
| Description | yes | yes | Primary human label |
| Status (+ operational warning, IP shield) | yes | yes | Includes expiring-soon / revoked |
| Integration name (link) | yes | yes | From list API join — replaces client lookup |
| Tenant name (link) | yes | hidden | After integration when shown |
| Created | yes | yes | |
| Expires | yes | yes | Lifecycle signal — keep with Created/Last used |
| Last used | yes | yes | Operational health — trailing time column before actions |
| **Revoke** (actions column) | yes | yes | Icon + tooltip; dialog on click — **do not remove** |

Removed from list (detail retains): `integrationKey`, `revokedAt`, `revokedByUsername`, full IP
whitelist text.

## Boundaries in scope

- `ApiKeyResponseDto` enrichment: `integrationName`, `tenantId` / `tenantName` on list endpoints
- Batch integration+tenant join (same pattern as Enrollments)
- Admin UI `api-keys.tsx` column reorder + API labels; keep revoke column
- Matrix row API keys → `implemented`

## Out of scope

- Enrollment list inline deactivate (separate follow-on; see quick-security-actions doc)
- Changing revoke semantics or reason policy

## Acceptance evidence

- List API returns `integrationName` (+ tenant fields for GA)
- UI shows integration link; GA shows tenant link
- Revoke column unchanged in behavior
- Maven / functional tests for touched modules
- OpenAPI refresh when authorized

## Links

- [`../admin-ui-paginated-screens-matrix.md`](../admin-ui-paginated-screens-matrix.md)
- [`../admin-ui-list-quick-security-actions.md`](../admin-ui-list-quick-security-actions.md)
- [`TB-2026-06-18-admin-ui-lists-tier-a-enrollments.md`](TB-2026-06-18-admin-ui-lists-tier-a-enrollments.md)
