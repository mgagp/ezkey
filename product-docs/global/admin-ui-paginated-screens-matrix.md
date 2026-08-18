# Admin UI — Paginated Screens Matrix

## Purpose

Canonical **operator-first** decisions for every paginated Admin UI list: what the operator needs to see, how list APIs should enrich data (joins vs FK IDs), and volume tier. Fills the gap between API mechanics (`docs/PAGINATION_GUIDELINES.md`, `docs/PAGINATION_AUDIT_REPORT.md`) and day-to-day operability.

**Grilled:** Blitz 2026-05-08-2 D8 + D9 ([`backlog/grill-sessions/blitz-2026-05-08-2-D8-D9-pagination-grill-me.md`](backlog/grill-sessions/blitz-2026-05-08-2-D8-D9-pagination-grill-me.md)).

**Backlog:** [`I-2026-0013`](backlog/ideas/I-2026-0013-paginated-screens-functional-review.md) (analysis), [`I-2026-0014`](backlog/ideas/I-2026-0014-paginated-screens-display-strategy.md) (API/UI implementation). **Post–Tier A follow-up queue:** [`I-2026-0028`](backlog/ideas/I-2026-0028-admin-ui-operator-experience-follow-up.md).

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
| **A — bounded** | PME-scale row counts (integrations, enrollments, …) | Prefer **labels in list DTO** via query joins; **keep primary `ID` column**; avoid FK-only columns with no label (e.g. bare `integrationId`) |
| **B — high volume** | Audit logs, auth attempts, large checkpoints | **Selective** indexed joins; prioritize glance fields; defer heavy enrichment |

**Reference screen (both D8 + D9):** Administrators list — use as quality bar.

## Timestamp display (cross-cutting)

**Operational clarity first:** list and investigation surfaces show an **absolute** timestamp
(timezone-aware where the screen already uses display timezone). Relative time (`2 m ago`) is optional
**secondary** hint only — never the sole value on a column meant for forensics or correlation.

Tier B audit logs may keep absolute + muted relative on **Time** as a visual comfort; auth attempts
and Tier A list **Created** columns use absolute only. Detail surfaces (e.g. last login) may still
use relative in parentheses when it aids scanning without replacing the primary fact.

## List navigation patterns (cross-cutting)

Tier A lists use a **consistent primary-key column** (`**ID**` first, monospace) alongside human
labels from joins. Cross-screen links must match how each entity opens detail:

| Entity | List route | Detail navigation | Link helper / pattern |
|--------|------------|-------------------|------------------------|
| Integration | `/integrations` | `/integrations/:id` page | path param |
| Enrollment | `/enrollments` | `/enrollments/:id` page | path param |
| Tenant | `/tenants` | `/tenants/:id` page | path param |
| API key | `/api-keys` | list + dialog (query) | follow existing screen |
| **Administrator** | `/admins` | list + dialog (`?adminId=`) | `adminListDetailHref(id)` in `@/lib/list-detail-navigation` |

**Do not** link to `/admins/:id` — that route does not exist. Audit logs, enrollment detail, and
tenant detail embedded lists must use `adminListDetailHref` (or equivalent `?adminId=` URL).

## Matrix

| Screen / route | API (list) | Tier | Operator question (1 line) | Target list columns (draft) | Join / display decision | Global vs Tenant | Status | Notes |
|----------------|------------|------|------------------------------|----------------------------|-------------------------|------------------|--------|-------|
| Admins | `GET /api/v1/admins` | A | Who can operate this instance and in what role? | **ID**, username, name, type, tenant (GA), status, last login, created | **Done** — joins + **ID** first column; detail via list `?adminId=` | Both (scoped) | `implemented` | Reference for joins; navigation pattern below |
| Tenants | `GET /api/v1/tenants` | A | Which tenants exist and are they active? | **ID**, name, status (+ system badge), organization, domain, created | No joins — root entity; country on detail only | Global Admin | `implemented` | TB `TB-2026-06-23-admin-ui-tier-a-completion-embedded-tenants` |
| Integrations | `GET /api/v1/integrations` | A | Which apps are protected and under which tenant? | **ID**, code, name, status (+ operational warning), **tenant name** (Global Admin), created | **Done** — `tenantName` via list join; ID kept as primary key | Both (tenant col GA only) | `implemented` | First operator-lists TB (`TB-2026-06-18-admin-ui-lists-tier-a-integrations`) |
| Enrollments | `GET /api/v1/enrollments` | A | Which devices/users are enrolled and in what state? | **ID**, name, status (+ warning), **integration name**, user ID, **tenant name** (GA), created, last used, **deactivate/reactivate** | **Done** — order: identity → state → scope → time; batch integration+tenant join; quick lifecycle actions | Both (tenant col GA only) | `implemented` | TB enrollments + `TB-2026-06-20-admin-ui-enrollments-list-quick-actions` |
| API keys | `GET /api/v1/api-keys/...` | A | Which keys exist and for which integration? | **ID**, description, status (+ warning), **integration name**, **tenant name** (GA), created, expires, last used, revoke action | **Done** — batch integration+tenant join; order identity → state → scope → time | Both | `implemented` | TB `TB-2026-06-18-admin-ui-lists-tier-a-api-keys` |
| Encryption keys | `GET /api/v1/encryption-keys` | A | Key lifecycle and migration backlog? | **ID**, status, lifecycle, algorithm, records, introduced, primary since, created by, actions | Bounded; labels from key metadata | Global Admin | `implemented` | Async manual re-encryption triggers (202) via batches section |
| Re-encryption batches | `GET .../reencryption-batches` | A | Batch progress and failures? | **ID**, table, column, shard, keys, status, progress, records, perf, actions | Bounded | Global Admin | `implemented` | Create/trigger/resume; progress via embedded table + polling |
| Alerts | `GET /api/v1/alerts` | A | What needs operator action now? | **ID**, type, severity, status, **summary** (payload one-liner), occurrences, **raised** (absolute), **last seen** (absolute); **resolved** when status filter ≠ OPEN | Bounded | Global Admin | `implemented` | TB `TB-2026-07-03-admin-ui-alerts-list-polish` (Wave C); default filter OPEN |
| Auth attempts | `GET /api/v1/auth-attempts` | B | What auth flows happened and outcomes? | **ID**, status, **enrollment name**, **integration name**, **tenant name** (GA), challenge, **created** (absolute), **expires** (absolute) | Batch enrollment → integration join per page | Both | `implemented` | TB `TB-2026-06-23-admin-ui-auth-attempts-tier-b-labels`; Created/Expires = `formatDate` |
| Audit logs | `GET /api/v1/audit-logs` | B | Who did what, when, with what result? | time, event type, **actor (username)**, status, API; detail: integration/enrollment/tenant names | Selective batch joins (max 4/page); `#id` when entity gone | Both | `implemented` | TB `TB-2026-06-23-admin-ui-audit-logs-tier-b-labels`, #258, PR #259 |
| Audit chain checkpoints | `GET .../chain-checkpoints` | B | Integrity windows healthy? | **ID**, **window start** / **window end** (absolute), **entries**, **type** (all four), **lifecycle**, **notes** (truncated) | No joins; optional `entryCountMin=1` hide-empty; default sort `windowStart,ASC` for inline gap rows | Global Admin | `implemented` | Embedded in audit-logs Integrity panel; TB `TB-2026-07-05-admin-ui-audit-chain-checkpoints-polish` (Wave C) |
| Tenant detail → admins | scoped admins list | A | Admins for this tenant? | **ID**, username, name, type, status, last login, created (same as admins minus tenant col) | Same as admins Tier A | Global Admin | `implemented` | Embedded list; row opens `/admins?adminId=` |
| Integration detail → enrollments | scoped enrollments | A | Enrollments for this integration? | **ID**, name, status (+ warning), user ID, created, last used, **deactivate/reactivate** | Same as enrollments Tier A minus integration/tenant columns | Both | `implemented` | TB `TB-2026-06-23-admin-ui-tier-a-completion-embedded-tenants` |

*Analysis fills TBD rows; add rows if new paginated surfaces ship.*

## Detail FK links (post–P4 editorial parity)

Detail pages and list dialogs must match Tier A/B **label + link** posture. No client-side expand
fetch.

| Pattern | When | UI |
|---------|------|-----|
| Enriched | API returns display name | `{name}` link + `(ID n)` mono suffix |
| Fallback link | FK present, name null, entity likely exists | `{Entity} #n` linked (auth-attempt detail, admin enrollment row) |
| Gone | FK present, name null, entity removed | `#n` plain text, no link (audit logs, `AdminFkLink` on enrollment detail for deleted admins) |

**Implemented surfaces (2026-06-23):** admin detail → enrollment (`enrollmentName` on
`AdminResponseDto`); enrollment detail → created/deactivated/revoked admin usernames;
integration/api-key detail → tenant/integration fallbacks. Shared components:
`fk-detail-links.tsx`. TB: `TB-2026-06-23-admin-ui-detail-fk-label-parity`.

## Related documents

| Document | Role |
|----------|------|
| [`docs/PAGINATION_GUIDELINES.md`](../../docs/PAGINATION_GUIDELINES.md) | Spring pagination mechanics |
| [`docs/PAGINATION_AUDIT_REPORT.md`](../../docs/PAGINATION_AUDIT_REPORT.md) | Endpoint pagination audit (legacy hub) |
| [`operator-alignment-guide.md`](operator-alignment-guide.md) | 3-second test, deployment geometries |
| [`design-principles.md`](design-principles.md) | #5 operator-first, #10 sobriety, #14 beautiful problems |

## Retrofit pointer

When a row reaches `implemented`, update the Admin UI page and Admin API list DTO in the same change set; link the PR to the matrix row.
