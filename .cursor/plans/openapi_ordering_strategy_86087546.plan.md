---
name: OpenAPI ordering strategy
overview: Phase 1 produced canonical docs; Phase 2 implements OpenApiCustomizer + Swagger UI alignment for Admin/Auth/Integration APIs.
todos:
  - id: baseline-order-audit
    content: Capture current tag and operation ordering behavior across admin/auth/integration generated specs and public ReDoc pages.
    status: completed
  - id: ideal-order-guides
    content: Define conceptual ideal order/grouping and canonical tag taxonomy per API (Admin/Auth/Integration).
    status: completed
  - id: mechanism-mapping
    content: Build mechanism-capability mapping (annotations, Springdoc sorters, customizers, x-tagGroups) and choose preferred strategy.
    status: completed
  - id: phase2-ready-backlog
    content: Produce implementation-ready Phase 2 backlog with file targets, risks, and validation checklist for commit.
    status: completed
isProject: false
---

# OpenAPI presentation order — workspace plan

**Status:** Closed — delivered via PR `#181` (2026-06-03). Issue `#180` closed.

## Canonical artifacts

| Artifact | Path |
| --- | --- |
| Design (Phase 1 complete) | [product-docs/global/openapi-presentation-order-design.md](../product-docs/global/openapi-presentation-order-design.md) |
| Vision | [product-docs/global/vision/V-2026-06-02-openapi-api-reference-presentation-order.md](../product-docs/global/vision/V-2026-06-02-openapi-api-reference-presentation-order.md) |
| Backlog idea | [product-docs/global/backlog/ideas/I-2026-06-02-openapi-api-reference-presentation-order.md](../product-docs/global/backlog/ideas/I-2026-06-02-openapi-api-reference-presentation-order.md) |
| Tracer bullet (Phase 2) | [product-docs/global/backlog/ideas/TB-2026-06-02-openapi-presentation-order-phase2.md](../product-docs/global/backlog/ideas/TB-2026-06-02-openapi-presentation-order-phase2.md) |
| Working plan prompt | [.github/prompts/plan-openApiPresentationOrder.prompt.md](../.github/prompts/plan-openApiPresentationOrder.prompt.md) |

## Canonical materialization

- Materialization lane: `Lane B` — plan incubation → canonical `product-docs`.
- Status: materialized on `2026-06-02`; **delivered** via PR `#181` merged `2026-06-03`.
- GitHub issue / branch: `#180` (closed), `feature/180-i-2026-06-02-openapi-api-reference-presentation-order` (merged)
- Methodology gate: `product-docs/methodology/decisions/2026-06-02-plan-incubation-bidirectional-traceability.md`
- Plan role after materialization: **retained Cursor scaffold**; slice closed on `main`.
