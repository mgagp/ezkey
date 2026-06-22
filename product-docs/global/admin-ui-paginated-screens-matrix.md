# Admin UI — Paginated Screens Matrix

## Purpose

Canonical **operator-first** decisions for every paginated Admin UI list: what the operator needs to see, how list APIs should enrich data (joins vs FK IDs), and volume tier. Fills the gap between API mechanics (`docs/PAGINATION_GUIDELINES.md`, `docs/PAGINATION_AUDIT_REPORT.md`) and day-to-day operability.

**Grilled:** Blitz 2026-05-08-2 D8 + D9 ([`backlog/grill-sessions/blitz-2026-05-08-2-D8-D9-pagination-grill-me.md`](backlog/grill-sessions/blitz-2026-05-08-2-D8-D9-pagination-grill-me.md)).

**Backlog:** [`I-2026-0013`](backlog/ideas/I-2026-0013-paginated-screens-functional-review.md) (analysis), [`I-2026-0014`](backlog/ideas/I-2026-0014-paginated-screens-display-strategy.md) (API/UI implementation).

**Methodology note:** This file is the **living canonical home** for screen-level operator/display choices. Legacy docs under `docs/` remain linked for mechanics until gradually retrofitted into product-docs.

## How to use

1. **One row per paginated surface** (top-level page or embedded list worth operator review).
2. Fill **operator question** and **target columns** during analysis (`I-2026-0013`).
3. Set **tier** and **join strategy** per grill decisions.
4. Mark **status**: `draft` → `reviewed` → `implemented`.
5. Promote implementation as **`TB-*` per screen group**, not a single mega-change.

## Volume tiers (normative)

| Tier | Expectation | Join posture |
|------|-------------|--------------|
| **A — bounded** | PME-scale row counts (integrations, enrollments, …) | Prefer **labels in list DTO** via query joins; avoid raw FK IDs in list columns |
| **B — high volume** | Audit logs, auth attempts, large checkpoints | **Selective** indexed joins; prioritize glance fields; defer heavy enrichment |

**Reference screen (both D8 + D9):** Administrators list — use as quality bar.

## Matrix

| Screen / route | API (list) | Tier | Operator question (1 line) | Target list columns (draft) | Join / display decision | Global vs Tenant | Status | Notes |
|----------------|------------|------|------------------------------|----------------------------|-------------------------|------------------|--------|-------|
| Admins | `GET /api/v1/admins` | A | Who can operate this instance and in what role? | username, admin type, tenant, status, … | **Done** — joins; reference implementation | Both (scoped) | `implemented` | Gold standard |
| Tenants | `GET /api/v1/tenants` | A | Which tenants exist and are they active? | *TBD analysis* | Labels not FK IDs | Global Admin | `draft` | |
| Integrations | `GET /api/v1/integrations` | A | Which apps are protected and under which tenant? | **ID**, code, name, status (+ operational warning), **tenant name** (Global Admin), created | **Done** — `tenantName` via list join; ID kept as primary key | Both (tenant col GA only) | `implemented` | First operator-lists TB (`TB-2026-06-18-admin-ui-lists-tier-a-integrations`) |
| Enrollments | `GET /api/v1/enrollments` | A | Which devices/users are enrolled and in what state? | **ID**, name, status (+ warning), **integration name**, user ID, **tenant name** (GA), created, last used, **deactivate/reactivate** | **Done** — order: identity → state → scope → time; batch integration+tenant join; quick lifecycle actions | Both (tenant col GA only) | `implemented` | TB enrollments + `TB-2026-06-20-admin-ui-enrollments-list-quick-actions` |
| API keys | `GET /api/v1/api-keys/...` | A | Which keys exist and for which integration? | **ID**, description, status (+ warning), **integration name**, **tenant name** (GA), created, expires, last used, revoke action | **Done** — batch integration+tenant join; order identity → state → scope → time | Both | `implemented` | TB `TB-2026-06-18-admin-ui-lists-tier-a-api-keys` |
| Encryption keys | `GET /api/v1/encryption-keys` | A | Key lifecycle and migration backlog? | *TBD* | Bounded; joins as needed for labels | Global Admin | `draft` | |
| Re-encryption batches | `GET .../reencryption-batches` | A | Batch progress and failures? | *TBD* | Bounded | Global Admin | `draft` | |
| Alerts | `GET /api/v1/alerts` (or equivalent) | A | What needs operator action now? | *TBD* | Bounded | Global Admin | `draft` | |
| Auth attempts | `GET /api/v1/auth-attempts` | B | What auth flows happened and outcomes? | status, time, **integration name**, **tenant name** (Global Admin) | Selective indexed joins; **malleable** | Both | `draft` | Grill default |
| Audit logs | `GET /api/v1/audit-logs` | B | Who did what, when, with what result? | time, event type, actor, status; names if indexed | Selective joins only | Both | `draft` | |
| Audit chain checkpoints | `GET .../chain-checkpoints` | B | Integrity windows healthy? | *TBD* | Careful on volume | Global Admin | `draft` | Embedded in audit-logs UI |
| Tenant detail → admins | scoped admins list | A | Admins for this tenant? | *TBD* | Same as admins tier A | Global Admin | `draft` | Embedded list |
| Integration detail → enrollments | scoped enrollments | A | Enrollments for this integration? | *TBD* | Same as enrollments tier A | Both | `draft` | Embedded list |

*Analysis fills TBD rows; add rows if new paginated surfaces ship.*

## Related documents

| Document | Role |
|----------|------|
| [`docs/PAGINATION_GUIDELINES.md`](../../docs/PAGINATION_GUIDELINES.md) | Spring pagination mechanics |
| [`docs/PAGINATION_AUDIT_REPORT.md`](../../docs/PAGINATION_AUDIT_REPORT.md) | Endpoint pagination audit (legacy hub) |
| [`operator-alignment-guide.md`](operator-alignment-guide.md) | 3-second test, deployment geometries |
| [`design-principles.md`](design-principles.md) | #5 operator-first, #10 sobriety, #14 beautiful problems |

## Retrofit pointer

When a row reaches `implemented`, update the Admin UI page and Admin API list DTO in the same change set; link the PR to the matrix row.
