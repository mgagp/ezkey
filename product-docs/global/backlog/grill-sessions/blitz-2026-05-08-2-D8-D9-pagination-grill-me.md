# Grill Me — Blitz 2026-05-08-2, D8 + D9 (paginated Admin UI — operator review + display strategy)

## Session control

| Field | Value |
|-------|--------|
| **Blitz source** | `product-docs/global/backlog/blitz-archive/blitz-2026-05-08-2.md` (D8, D9) |
| **Backlog** | `I-2026-0013`, `I-2026-0014` |
| **Canonical output** | [`../../admin-ui-paginated-screens-matrix.md`](../../admin-ui-paginated-screens-matrix.md) |
| **Companion (later)** | `I-2026-0015` (D10 — volume limits; separate grill) |
| **Status** | `complete` |
| **Date** | `2026-05-19` |

## Settled decisions

### Combined review (D8D9-1)

One pass per screen: **operator question + columns + volume tier + join/display strategy** in a single matrix row.

### Volume tiers (D8D9-2)

| Tier | Screens / tables | Display strategy |
|------|------------------|------------------|
| **A — bounded (PME)** | Tenants, integrations, enrollments, admins, API keys, encryption keys, re-encryption batches, alerts (operational volume) | **Joins in list API**; show **labels**, not raw FK IDs in list columns |
| **B — high volume** | Audit logs, auth attempts, audit chain checkpoints (where listed) | **Selective** indexed joins only where glance-useful; no blanket joins |

### Operator test (D8D9-3)

Per screen: one-line **operator question**; columns must answer it within **~3 seconds** (`operator-alignment-guide`). Note **Global Admin vs Tenant Admin** when visibility differs.

### Join placement (D8D9-4, D8D9-5)

- Enrichment in **Admin API list DTOs** (query-time join), not N+1 UI fetches.
- **Tier A:** demote « More information » as the daily path; detail/drill-down remains OK.
- **Tier B:** lazy enrichment acceptable where joins deferred.

### Indexes (D8D9-6)

Add **targeted index** when a join is adopted — essential complexity (#2).

### Tier B defaults (D8D9-7, D8D9-8)

- **Auth attempts (list):** **integration name** (+ **tenant name** for Global Admin) — validated starting point; **malleable** for future revision. Goal: exit FK-only presentation mindset.
- **Audit logs (list):** event type, time, actor, status first; tenant/integration names **if** index-backed and glance-useful.

### Canonical documentation (D8D9-9)

- Living matrix under **`product-docs/global/admin-ui-paginated-screens-matrix.md`** — canonical for operator/display decisions.
- **`docs/PAGINATION_*`** remain **mechanics** (complementary); link from matrix, do not duplicate.
- Broader method: **gradual retrofit** of scattered docs into product-docs structure (methodological catch-up).

### Execution (D8D9-10, D8D9-11)

- **Phase 1:** complete matrix analysis (`I-2026-0013` leads).
- **Phase 2:** implementation via **`TB-*` per screen group** (`I-2026-0014`), not one monolith.
- **Principle:** readability and operability win where PME volume makes joins cheap; serious performance discipline on Tier B only (#14).

## Links

- [`../blitz-archive/blitz-2026-05-08-2.md`](../blitz-archive/blitz-2026-05-08-2.md)
- [`../ideas/I-2026-0013-paginated-screens-functional-review.md`](../ideas/I-2026-0013-paginated-screens-functional-review.md)
- [`../ideas/I-2026-0014-paginated-screens-display-strategy.md`](../ideas/I-2026-0014-paginated-screens-display-strategy.md)
- [`../../admin-ui-paginated-screens-matrix.md`](../../admin-ui-paginated-screens-matrix.md)
