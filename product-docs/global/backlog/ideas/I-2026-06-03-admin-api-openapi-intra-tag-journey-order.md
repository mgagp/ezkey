# Backlog Idea — `I-2026-06-03` Admin API OpenAPI intra-tag journey order

## Metadata

- **ID:** `I-2026-06-03-admin-api-openapi-intra-tag-journey-order`
- **Status:** `captured`
- **Priority:** `P3`
- **Lane:** `D` — post-delivery evolution (deferred intent from `#181` closeout)
- **Created at:** `2026-06-03`
- **Updated at:** `2026-06-02`
- **Phase tags:** `P2-maintainability`, `P1-operability`
- **Component tags:** `admin-api`, `docs`, `specs`, `sites/ezkey-org`
- **Captured by:** Marc

## Intent

When an operator or integrator opens a curated Admin API tag in Swagger or ReDoc, operations
should appear in **journey order** where that improves comprehension—not only in the right tag
(which PR `#181` already delivers).

## Problem and value

- **Problem:** Inside Admin tags, operation order still follows Springdoc defaults and Swagger
  `operationsSorter=method`. Narrative flows (e.g. Admin Authentication) can read backwards
  relative to how an operator actually works.
- **Expected value:** Completes the cognitive-comfort arc started in `I-2026-06-02` without
  re-litigating tag-level work; targeted path curation where ROI is highest.

## Scope

- **In scope (when promoted):**
  - Vision and design for intra-tag operation order on selected Admin tags (see parent `V-*`
    candidate table).
  - Extend `OpenApiPresentationCustomizer` with partial `paths` reorder (pattern from Auth API).
  - Document ideal operation sequences per curated tag.
  - Regenerate specs; verify Swagger UI + ReDoc portal.
  - Review `operationsSorter=method` impact on curated tags.
- **Out of scope:**
  - Tag order, `x-tagGroups`, Auth/Integration APIs (already done).
  - Full Admin API path reorder in one slice.
  - Crypto API.

## Origin (Lane D)

Emerges from **explicit deferred items** recorded at closeout of
[`I-2026-06-02-openapi-api-reference-presentation-order`](../backlog/ideas/I-2026-06-02-openapi-api-reference-presentation-order.md)
and discussion after merge of PR `#181`. Not a defect fix—a **product positioning** follow-on.

## Promotion criteria (before `incubating` / `TB-*`)

- Operator or integrator signal that intra-tag navigation is friction (optional), **or**
- Explicit decision to invest in P1 tags without waiting for complaints.
- ~~Grill or short design pass confirming tag priority list and operation sequences for P1 tags.~~
  **Partially satisfied:** P1/P2 sequences documented in design doc §3.5 (2026-06-02); grill still
  useful before implementation to validate Administrator Provisioning deferral.

## Suggested execution shape (not committed yet)

1. ~~Design appendix: ideal operation order for P1/P2 tags~~ — **done** (design doc §3.5,
   2026-06-02): Admin Authentication, Admin Enrollment Management, Public, Auth Attempts.
2. `TB-*`: implement P1 tag path reorder + spec regen + verification.
3. Optional second `TB-*` for P2 tags if P1 proves maintainable.

## Links

- Vision: [`V-2026-06-03-admin-api-openapi-intra-tag-journey-order.md`](../../vision/V-2026-06-03-admin-api-openapi-intra-tag-journey-order.md)
- Parent slice (closed): [`I-2026-06-02-openapi-api-reference-presentation-order.md`](I-2026-06-02-openapi-api-reference-presentation-order.md)
- Design (macro + intra-tag ideal order): [`openapi-presentation-order-design.md`](../../openapi-presentation-order-design.md) (§3.0–3.2 delivered; §3.5 Lane D conceptual)

## Related documents

- [`V-2026-06-02-openapi-api-reference-presentation-order.md`](../../vision/V-2026-06-02-openapi-api-reference-presentation-order.md)
- [`TB-2026-06-02-openapi-presentation-order-phase2.md`](TB-2026-06-02-openapi-presentation-order-phase2.md) (closed)
