# Backlog Idea — `I-2026-06-02` OpenAPI API reference presentation order

## Metadata

- **ID:** `I-2026-06-02-openapi-api-reference-presentation-order`
- **Status:** `incubating`
- **Priority:** `P2`
- **Created at:** `2026-06-02`
- **Updated at:** `2026-06-02`
- **Phase tags:** `P1-operability`, `P2-maintainability`
- **Component tags:** `admin-api`, `auth-api`, `integration-api`, `docs`, `sites/ezkey-org`, `specs`
- **Captured by:** Marc
- **GitHub issue:** `#180`

## Intent

Curate OpenAPI tag and operation presentation order for Admin, Auth, and Integration APIs so
runtime Swagger UI and public ReDoc documentation follow a stable, journey-oriented navigation
model instead of alphabetical or discovery-order defaults.

## Problem and value

- **Problem:** Generated specs and `tagsSorter=alpha` hide the intended reader journey. ReDoc on
  `ezkey.org` reflects raw spec order; Admin API has duplicate `Public` tags and discovery-ordered
  tag lists. Integrators and operators waste time mapping concepts to scattered sections.
- **Expected value:** Faster onboarding, aligned public and dev documentation, and a maintainable
  rule for where new endpoints appear in the reference.

## Scope

- **In scope:**
  - Mechanism study and choice (`OpenApiCustomizer`, Swagger UI properties, `x-tagGroups`).
  - Per-API ideal order guide (Admin, Auth, Integration).
  - Implementation via Springdoc customization and spec regeneration.
  - Verification on Swagger UI and `sites/ezkey-org` ReDoc pages.
- **Out of scope:**
  - `crypto-api` reference ordering.
  - Translating OpenAPI description text to French.
  - Host-neutral spec lifecycle (see `I-2026-06-02-openapi-spec-lifecycle-and-cloudflare-validation`).

## Phase breakdown

| Phase | Goal | Status |
| --- | --- | --- |
| 1 | Research, baseline audit, ideal order, mechanism mapping (design doc) | Done (2026-06-02) |
| 2 | Implement customizers, fix tag taxonomy, align Swagger UI, regen specs | Validating locally (`TB-2026-06-02`; ReDoc confirmed; Cloudflare preview optional) |

## Reader-journey guide

Admin API presentation principles (cognitive ordering rationale) are documented in
[`openapi-presentation-order-design.md` §3.0](../../openapi-presentation-order-design.md#30-admin-api--presentation-principles-reader-journey).
Use that section when placing new endpoints or tags in the reference.

## Key assumptions

- Order is owned in backend OpenAPI generation, not by editing `specs/*.json` by hand.
- `x-tagGroups` is optional but valuable for Admin API volume; all tags must appear in a group
  when used (ReDoc constraint).
- Removing `tagsSorter=alpha` is required for Swagger/ReDoc parity when order lives in the spec.

## Links

- GitHub issue: `#180`
- GitHub branch: `feature/180-i-2026-06-02-openapi-api-reference-presentation-order`

## Incubation sources

- Working plan (GitHub): [`.github/prompts/plan-openApiPresentationOrder.prompt.md`](../../../../.github/prompts/plan-openApiPresentationOrder.prompt.md)
- Working plan (Cursor): [`.cursor/plans/openapi_ordering_strategy_86087546.plan.md`](../../../../.cursor/plans/openapi_ordering_strategy_86087546.plan.md)
- Lane: `B` — plan incubation, materialized `2026-06-02`
- Methodology gate: [`2026-06-02-plan-incubation-bidirectional-traceability.md`](../../../methodology/decisions/2026-06-02-plan-incubation-bidirectional-traceability.md)

## Related documents

- Design: [`openapi-presentation-order-design.md`](../../openapi-presentation-order-design.md)
- Vision: [`V-2026-06-02-openapi-api-reference-presentation-order.md`](../../vision/V-2026-06-02-openapi-api-reference-presentation-order.md)
- Tracer bullet: [`TB-2026-06-02-openapi-presentation-order-phase2.md`](TB-2026-06-02-openapi-presentation-order-phase2.md)
