# API Controllers Registry

## Purpose

Canonical **high-level index** of every REST controller across Ezkey APIs — purpose, audience, volume tier, sensitivity, and rate-limit posture. Supports cross-cutting decisions (rate-limit baseline, pagination matrix, versioning, DoS limits) without re-discovering the surface from code each time.

**Grilled:** Blitz 2026-05-08-2 D1 ([`backlog/grill-sessions/blitz-2026-05-08-2-D1-rate-limit-registry-grill-me.md`](backlog/grill-sessions/blitz-2026-05-08-2-D1-rate-limit-registry-grill-me.md)).

**Backlog:** [`I-2026-0009`](backlog/ideas/I-2026-0009-global-controllers-registry.md) (authoring), [`I-2026-0008`](backlog/ideas/I-2026-0008-rate-limit-baseline-analysis.md) (baseline policy — consumes this registry).

**Not a substitute for:** OpenAPI specs, generated clients, or component functional-flow deep dives.

## Maintenance

- **Manual curation** for initial fill and ongoing updates.
- Future optional skill: **stub diff** against known controller class list — targeted reads only; no whole-repo scan.
- When a controller ships or changes materially, update the row in the same change set (or linked follow-up).

## Row schema

| Column | Meaning |
|--------|---------|
| **Module** | Boot API module (`admin-api`, `auth-api`, …) |
| **Controller** | Java class or logical name |
| **Base path** | REST prefix |
| **Purpose** | One-line operator/dev description |
| **Audience** | `admin-session` / `api-key` / `device` / `public` / mixed |
| **Volume tier** | `A` bounded / `B` high (align with paginated matrix) |
| **Sensitivity** | `low` / `medium` / `high` (security/audit impact) |
| **Rate-limit note** | Current posture or `baseline` / `specialized` / `none` |
| **Reference** | Link to component flow, CONFIGURATION.md, or OpenAPI tag |

## Registry

| Module | Controller | Base path | Purpose | Audience | Tier | Sensitivity | Rate-limit | Reference | Status |
|--------|------------|-----------|---------|----------|------|-------------|------------|-----------|--------|
| *TBD* | | | | | | | | | `draft` |

*Populate per `I-2026-0009` — start with admin-api, then auth, integration, crypto-api.*

## Related documents

| Document | Role |
|----------|------|
| [`admin-ui-paginated-screens-matrix.md`](admin-ui-paginated-screens-matrix.md) | Admin UI list ↔ API mapping |
| [`docs/ENDPOINT.md`](../../docs/ENDPOINT.md) | Legacy endpoint hub (retrofit pointer) |
| Component `functional-flows.md` | Per-controller detail |

## Retrofit pointer

Gradual consolidation under product-docs/global; legacy scattered docs remain linked until migrated.
