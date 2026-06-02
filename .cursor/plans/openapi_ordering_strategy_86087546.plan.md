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

**Status:** Phase 1 materialized in repo. Phase 2 = `TB-2026-06-02-openapi-presentation-order-phase2`.

## Canonical artifacts

| Artifact | Path |
| --- | --- |
| Design (Phase 1 complete) | [product-docs/global/openapi-presentation-order-design.md](../product-docs/global/openapi-presentation-order-design.md) |
| Vision | [product-docs/global/vision/V-2026-06-02-openapi-api-reference-presentation-order.md](../product-docs/global/vision/V-2026-06-02-openapi-api-reference-presentation-order.md) |
| Backlog idea | [product-docs/global/backlog/ideas/I-2026-06-02-openapi-api-reference-presentation-order.md](../product-docs/global/backlog/ideas/I-2026-06-02-openapi-api-reference-presentation-order.md) |
| Tracer bullet (Phase 2) | [product-docs/global/backlog/ideas/TB-2026-06-02-openapi-presentation-order-phase2.md](../product-docs/global/backlog/ideas/TB-2026-06-02-openapi-presentation-order-phase2.md) |
| Working plan prompt | [.github/prompts/plan-openApiPresentationOrder.prompt.md](../.github/prompts/plan-openApiPresentationOrder.prompt.md) |

## Next action

Execute `TB-2026-06-02` (implementation) when approved.
