## Plan: OpenAPI API Reference Presentation Order

TL;DR: improve cognitive order and grouping of Admin, Auth, and Integration API documentation in
Swagger UI (runtime) and ReDoc (public `ezkey.org` portal) by curating the generated OpenAPI
artifact—not by hand-editing `specs/`. Phase 1 materializes research and target order; Phase 2
implements `OpenApiCustomizer`, tag taxonomy fixes, Swagger UI alignment, and optional `x-tagGroups`.

**Origin:** plan incubation (Cursor Plan mode), materialized 2026-06-02.

**Scope:** `admin-api`, `auth-api`, `integration-api`, `specs/`, `sites/ezkey-org` ReDoc pages.
**Out of scope:** `crypto-api` (dev-only, not on public portal).

**Canonical artifacts (this incubation):**

- Design (incl. Admin reader-journey §3.0): [`product-docs/global/openapi-presentation-order-design.md`](../../product-docs/global/openapi-presentation-order-design.md)
- Vision: [`product-docs/global/vision/V-2026-06-02-openapi-api-reference-presentation-order.md`](../../product-docs/global/vision/V-2026-06-02-openapi-api-reference-presentation-order.md)
- Backlog: [`product-docs/global/backlog/ideas/I-2026-06-02-openapi-api-reference-presentation-order.md`](../../product-docs/global/backlog/ideas/I-2026-06-02-openapi-api-reference-presentation-order.md)
- Tracer bullet (Phase 2): [`product-docs/global/backlog/ideas/TB-2026-06-02-openapi-presentation-order-phase2.md`](../../product-docs/global/backlog/ideas/TB-2026-06-02-openapi-presentation-order-phase2.md)

**Current findings**

- `springdoc.swagger-ui.tagsSorter=alpha` and `operationsSorter=method` are set on all three APIs;
  Swagger UI re-sorts client-side and ignores curated `tags[]` order in the spec.
- ReDoc CE on `sites/ezkey-org/*-api-reference.html` loads static JSON with no equivalent sorters;
  navigation follows spec `tags[]` order and path object order.
- Admin spec has duplicate root tag `Public` (two descriptions) from two controllers.
- Admin `tags[]` order in generated spec follows Springdoc discovery order, not product journey
  (e.g. `Encryption Keys` first, `Public` mid-list and duplicated).
- Auth spec tag order: `Enrollments`, `Authentication Attempts`, `Public` — enrollment before
  public metadata; operations under Enrollments appear as `verify` then `bind` (path map order).
- Integration API has a single tag; operation order in spec: `create`, `cancel`, `waitForResponse`
  (lifecycle should be create → wait → cancel).

**Recommended strategy (Phase 2)**

1. Per-API `OpenApiCustomizer` bean: canonical ordered `tags[]`, merge duplicate tag metadata,
   inject `x-tagGroups` for ReDoc (Admin only initially if complexity warrants phasing).
2. Align Swagger UI: remove `tagsSorter=alpha` (preserve spec tag order) or set explicit custom
   order consistent with spec; keep `operationsSorter=method` only where it helps, else path
   reorder in customizer for journey order.
3. Unify `@Tag(name = "Public")` descriptions on Admin API controllers.
4. Maintainer runs `scripts/update-specs.sh` and verifies Swagger + static ReDoc copies under
   `sites/ezkey-org/api-specs/`.

**Phase 1 steps (documentation — done in design doc)**

1. Baseline audit of tag and operation order per API.
2. Ideal conceptual order and `x-tagGroups` sketch per API.
3. Mechanism capability matrix and chosen approach.
4. Phase 2 task list with file targets and verification checklist.

**Phase 2 steps (implementation — done on branch `#180`)**

See `TB-2026-06-02-openapi-presentation-order-phase2.md`.

**Related**

- [`plan-openApiSpecLifecycle.prompt.md`](plan-openApiSpecLifecycle.prompt.md) — host-neutral specs
  (orthogonal; do not break tag/path curation when normalizing servers).
- [`product-docs/global/openapi-public-portal-first-cut-design.md`](../../product-docs/global/openapi-public-portal-first-cut-design.md)
- Methodology decision precedent: curated conceptual ordering
  (`product-docs/methodology/decisions/2026-05-29-curated-public-pack-ordering.md`)

**Canonical materialization**

- Materialization lane: `Lane B` — plan incubation → canonical `product-docs`.
- Status: materialized on `2026-06-02`; **delivered** via PR `#181` merged `2026-06-03` (closes `#180`).
- Vision: [`product-docs/global/vision/V-2026-06-02-openapi-api-reference-presentation-order.md`](../../product-docs/global/vision/V-2026-06-02-openapi-api-reference-presentation-order.md)
- Backlog idea: [`product-docs/global/backlog/ideas/I-2026-06-02-openapi-api-reference-presentation-order.md`](../../product-docs/global/backlog/ideas/I-2026-06-02-openapi-api-reference-presentation-order.md)
- Tracer bullet: [`product-docs/global/backlog/ideas/TB-2026-06-02-openapi-presentation-order-phase2.md`](../../product-docs/global/backlog/ideas/TB-2026-06-02-openapi-presentation-order-phase2.md)
- Design: [`product-docs/global/openapi-presentation-order-design.md`](../../product-docs/global/openapi-presentation-order-design.md)
- Deleted Cursor plan (materialized); canonical direction lives in linked V/I/TB and design doc above
- GitHub issue / branch: `#180`, `feature/180-i-2026-06-02-openapi-api-reference-presentation-order`
- Methodology gate: [`product-docs/methodology/decisions/2026-06-02-plan-incubation-bidirectional-traceability.md`](../../product-docs/methodology/decisions/2026-06-02-plan-incubation-bidirectional-traceability.md)
- Plan role after materialization: **retained source**; slice closed — canonical direction and Admin reader-journey principles live in linked artifacts; implementation on `main` since PR `#181`.
