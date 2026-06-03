# V-2026-06-03-admin-api-openapi-intra-tag-journey-order

## Metadata

- **ID:** `V-2026-06-03-admin-api-openapi-intra-tag-journey-order`
- **Status:** `draft`
- **Lane:** `D` — post-delivery evolution from deferred closeout of OpenAPI presentation order
- **Created at:** `2026-06-03`
- **Updated at:** `2026-06-02`
- **Captured by:** Marc

## Intent

Extend Ezkey's **journey-oriented OpenAPI presentation** from **tag-level** navigation (delivered
in PR `#181`) to **intra-tag operation order** on the **Admin API** where narrative flow matters
more than HTTP-verb grouping.

Readers who already land in the right section (e.g. Admin Authentication) should still encounter
operations in a **cognitive comfort order**—login before recovery before logout—not in Springdoc
discovery order or Swagger's method sorter alone.

## Motivation

PR `#181` closed the macro navigation gap: curated tags, Admin `x-tagGroups`, Auth/Integration
path lifecycles. During implementation, the same `OpenApiPresentationCustomizer` pattern that
reordered Auth paths (`bind` → `verify`, `pending` → `respond`) and Integration paths
(`create` → `wait` → `cancel`) exposed an analogous gap on Admin: **inside each tag**, path order
still reflects generation defaults.

That gap was **deferred pragmatically**—large surface, maintenance cost—without rejecting the
product intent. This vision captures that intent so a future slice can start from explicit
positioning rather than rediscovering the trade-off in chat.

## Relationship to the delivered slice

| Layer | Admin API (after `#181`) | This follow-on |
| --- | --- | --- |
| Tag order + ReDoc groups | Done — see [`V-2026-06-02`](V-2026-06-02-openapi-api-reference-presentation-order.md), design doc §3.0 | Unchanged |
| Path order within tag | Default / `operationsSorter=method` on Swagger | Curate where journey beats CRUD |
| Auth / Integration | Path order done | Out of scope (already delivered) |

## Principles

1. **Same curated-pack posture** — intra-tag order is a product choice when it aids comprehension,
   not a universal reorder of every Admin endpoint.
2. **Phased by tag value** — prioritize tags with **lifecycle narratives** over pure CRUD resources
   where GET/POST/PATCH sorting is already acceptable.
3. **Single source in the generated spec** — extend `OpenApiPresentationCustomizer` (partial path
   reorder), then `update-specs`; no hand-editing `specs/`.
4. **Swagger/ReDoc parity** — where path order is curated, avoid client-only sorters that hide it
   (review `operationsSorter=method` per affected tag or globally on Admin).
5. **Maintainability rule** — new endpoints in a curated tag must declare their placement in the
   customizer (or documented default bucket), same as new tags require `x-tagGroups` updates today.

## Candidate tags (refined — see design doc §3.5)

| Priority | Tag | Ideal operation story (summary) |
| --- | --- | --- |
| P1 | Admin Authentication | `activate` → `login` → `passwordless-wait` → `me` → `recover` → `logout` |
| P1 | Admin Enrollment Management | `reset` (single op; follows `recover` cross-tag) |
| P1 | Public | `instance-info` → `evaluator-signup` (Getting started group) |
| P2 | Auth Attempts | `create` → `wait` → `cancel` → get → list → `pending-count` |
| P3 | Tenants, Integrations, Enrollments, API Keys | List-first CRUD default |
| Defer | Audit Logs, Encryption Keys, Alerts, Dashboard | Read/query heavy; lower return on custom path order |

Canonical tables, baseline friction, and cross-tag handoffs:
[`openapi-presentation-order-design.md` §3.5](../openapi-presentation-order-design.md#35-admin-api--intra-tag-operation-order-lane-d-follow-on).

## Non-goals

- Re-sorting the entire Admin `paths` map in one shot without tag-level prioritization.
- Changing OpenAPI **contracts** (paths, operationIds, schemas)—presentation order only.
- Crypto API; Auth/Integration (already addressed).
- French translation of OpenAPI descriptions.

## Success signals (when promoted to `accepted`)

- [x] Design guidance documents ideal operation order for P1/P2 tags (design doc §3.5,
  2026-06-02).
- [ ] At least one high-value Admin tag shows curated path order in canonical spec and ReDoc/Swagger.
- [ ] Maintenance rule is documented for new endpoints in curated tags (extend §3.5 when implemented).

## Related documents

- Parent direction: [`V-2026-06-02-openapi-api-reference-presentation-order.md`](V-2026-06-02-openapi-api-reference-presentation-order.md) (`accepted`)
- Delivered design: [`openapi-presentation-order-design.md`](../openapi-presentation-order-design.md) (§8 closeout deferred items)
- Closed delivery: [`I-2026-06-02-openapi-api-reference-presentation-order.md`](../backlog/ideas/I-2026-06-02-openapi-api-reference-presentation-order.md), PR `#181`
- Backlog follow-on: [`I-2026-06-03-admin-api-openapi-intra-tag-journey-order.md`](../backlog/ideas/I-2026-06-03-admin-api-openapi-intra-tag-journey-order.md)
- Reference implementation pattern: `ezkey-auth-api/.../OpenApiPresentationCustomizer.java` (path reorder)
