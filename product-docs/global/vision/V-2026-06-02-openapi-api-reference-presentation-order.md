# V-2026-06-02-openapi-api-reference-presentation-order

## Metadata

- **ID:** `V-2026-06-02-openapi-api-reference-presentation-order`
- **Status:** `accepted`
- **Lane:** `B` — plan incubation materialized into canonical direction
- **Created at:** `2026-06-02`
- **Updated at:** `2026-06-03`
- **Captured by:** Marc

## Intent

Ezkey API reference surfaces (runtime Swagger UI and public ReDoc on `ezkey.org`) should present
endpoints in a **stable, journey-oriented order** that matches how integrators and operators
discover the platform—not in incidental Springdoc discovery or alphabetical tag order.

For the **Admin API**, the full reader-journey principles (why each layer exists, what to avoid,
and ReDoc group intent) live in
[`openapi-presentation-order-design.md` §3.0](../openapi-presentation-order-design.md#30-admin-api--presentation-principles-reader-journey).
This vision note stays at direction level; the design doc is the canonical operational guide.

## Motivation

OpenAPI annotations already describe the full API surface, but default generation and Swagger UI
sorting produce a flat, alphabetized view. That increases cognitive friction for onboarding,
multi-tenant setup, and lifecycle flows (enrollment, authentication, audit). The public portal
uses static specs and ReDoc, which do not apply the same client-side sorters as Swagger UI, so
runtime and public documentation can diverge unless the **canonical spec** carries the intended
order.

## Principles

1. **Curated packs, not file-browser order** — same posture as methodology explorer ordering
   ([`2026-05-29-curated-public-pack-ordering.md`](../methodology/decisions/2026-05-29-curated-public-pack-ordering.md)):
   documentation navigation is a product choice.
2. **Single source in the generated contract** — order is enforced at OpenAPI generation time
   (`OpenApiCustomizer` + annotations), then propagated via `update-specs` to SDK, mobile, and
   `sites/ezkey-org`.
3. **Parity across consumers** — Swagger UI and ReDoc should read the same tag order; avoid
   relying on UI-only sorters that ReDoc does not share.
4. **Scope discipline** — Admin, Auth, Integration APIs and public portal; Crypto API excluded.

When adding or renaming Admin API tags, apply the **reader journey** in design doc §3.0 and update
`OpenApiPresentationCustomizer` plus `x-tagGroups` in the same change.

## Potential impact

- Components: `admin-api`, `auth-api`, `integration-api`, `specs/`, `sites/ezkey-org`, `scripts/`.
- Audiences: integrators, evaluators, operators, maintainers regenerating clients.

## Promotion criteria

Promoted to `accepted` on **2026-06-03** after PR `#181` demonstrated:

- curated tag order in canonical specs for all three APIs;
- no duplicate Admin `Public` tag entry;
- Swagger UI and ReDoc portal show the same tag sequence (local validation);
- documented ideal order guide remains accurate after `update-specs`.

## Related documents

- Design: [`openapi-presentation-order-design.md`](../openapi-presentation-order-design.md)
- Backlog: [`I-2026-06-02-openapi-api-reference-presentation-order`](../backlog/ideas/I-2026-06-02-openapi-api-reference-presentation-order.md) (`done`)
- Tracer bullet: [`TB-2026-06-02-openapi-presentation-order-phase2`](../backlog/ideas/TB-2026-06-02-openapi-presentation-order-phase2.md) (`done`)
- Delivery: GitHub PR [`#181`](https://github.com/mgagp/ezkey/pull/181) (merged 2026-06-03)
- Follow-on (Lane D): [`V-2026-06-03-admin-api-openapi-intra-tag-journey-order`](../vision/V-2026-06-03-admin-api-openapi-intra-tag-journey-order.md) — intra-tag Admin path order (`draft`)
- Spec lifecycle (orthogonal): [`V-2026-06-02-openapi-spec-lifecycle.md`](V-2026-06-02-openapi-spec-lifecycle.md)
- Portal posture: [`V-2026-0014-api-docs-exposure-portal.md`](V-2026-0014-api-docs-exposure-portal.md)

## Incubation sources

- Working plan (GitHub): [`.github/prompts/plan-openApiPresentationOrder.prompt.md`](../../../.github/prompts/plan-openApiPresentationOrder.prompt.md)
- Working plan (Cursor): [`.cursor/plans/openapi_ordering_strategy_86087546.plan.md`](../../../.cursor/plans/openapi_ordering_strategy_86087546.plan.md)
- Lane: `B` — plan incubation, materialized `2026-06-02`
- Methodology gate: [`2026-06-02-plan-incubation-bidirectional-traceability.md`](../methodology/decisions/2026-06-02-plan-incubation-bidirectional-traceability.md)
