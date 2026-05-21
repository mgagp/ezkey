# SQL Business Limits Policy

## Purpose

Canonical policy for **repository-layer business limits** on high-volume queries — complement to pagination (list APIs) and rate limiting (frequency). Bounds per-call cost against abuse and accidental overload.

**Grilled:** Blitz 2026-05-08-2 D10 ([`backlog/grill-sessions/blitz-2026-05-08-2-D6-D10-guards-grill-me.md`](backlog/grill-sessions/blitz-2026-05-08-2-D6-D10-guards-grill-me.md)).

**Backlog:** [`I-2026-0015`](backlog/ideas/I-2026-0015-business-limits-large-volume-sql.md).

## Principles

- **Not a substitute for pagination** on list endpoints (`docs/PAGINATION_GUIDELINES.md`).
- Limits set **high enough** for legitimate operator workflows (#14 beautiful problems).
- Enforce at **repository** layer; prefer **externalized** properties in CONFIGURATION.md.
- Inventory aided by [`api-controllers-registry.md`](api-controllers-registry.md) and paginated matrix Tier B.

## Priority tables (Tier B)

| Domain | Examples | Policy note |
|--------|----------|-------------|
| Audit logs | scans, exports, integrity helpers | Cap row fetch windows; document property keys |
| Auth attempts | analytics, bulk lookups | Cap without breaking paginated list APIs |

## Query inventory

| Module | Repository / method | Table | Limit type | Config key | Status |
|--------|---------------------|-------|------------|------------|--------|
| *TBD* | | | `LIMIT` / window | | `open` |

## Related

- [`I-2026-0009`](backlog/ideas/I-2026-0009-global-controllers-registry.md)
- [`admin-ui-paginated-screens-matrix.md`](admin-ui-paginated-screens-matrix.md)
