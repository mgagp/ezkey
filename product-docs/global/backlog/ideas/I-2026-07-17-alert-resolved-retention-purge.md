# Backlog Idea — `I-2026-07-17` Alert resolved-row retention and purge

## Metadata

- **ID:** `I-2026-07-17-alert-resolved-retention-purge`
- **Status:** `captured`
- **Priority:** `P3`
- **Created at:** `2026-07-17`
- **Updated at:** `2026-07-17`
- **Last reviewed at:** `2026-07-17`
- **Phase tags:** `P3-future` (post–September-2026 R1 operable release)
- **Component tags:** `core`, `admin-api`, `admin-ui`, `infra`, `docs`, `audit`
- **Lane:** `C`
- **Captured by:** Marc (operator review of PostgreSQL role grants)

## Intent

Define and later implement an **operator-visible retention policy** for `ezkey_alert` rows that
have transitioned to `RESOLVED`, including a controlled **purge** path. Today alerts are never
hard-deleted: resolution is an `UPDATE` (status + reason + timestamps). That is intentional for
traceability, but it implies unbounded growth of the alerts table over long-running deployments.

## Problem and value

- **Problem:** During the PostgreSQL application-role split (`I-2026-0021` /
  `TB-2026-07-16`), runtime roles receive **SELECT / INSERT / UPDATE** on `ezkey_alert` and
  **no DELETE**. That matches current product semantics (`OPEN` → `RESOLVED` via update; see
  [`docs/ALERTS.md`](../../../../docs/ALERTS.md) and
  [`docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md`](../../../../docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md)).
  There is **no** scheduled or operator purge. Over months/years of integrity and heartbeat
  events, resolved rows will accumulate with no documented retention answer.
- **Expected value:** A deliberate retention model so long-lived instances stay lean without
  weakening the “resolve keeps history” story for the operational window that still matters; when
  purge is introduced, grants and jobs must be designed together (today’s no-DELETE is a feature,
  not an oversight).

## Scope

- **In scope (when promoted):**
  - Retention policy design for **`RESOLVED`** alerts (age, severity, resolution reason, or
    combination — to be grilled).
  - Purge mechanism (scheduled job under admin-api / ShedLock, and/or Global Admin action) with
    audit of the purge itself.
  - Explicit grant change: add **DELETE** (or a constrained purge path) for the role that owns the
    job — almost certainly `ezkey_admin` only — and document it in the permissions matrix and
    `apply-grants.sql`.
  - Admin UI visibility of retention posture (at least config/docs; UI polish optional).
- **Out of scope (for this idea):**
  - Soft-delete / “archive status” as a substitute for purge (unless analysis proves better).
  - Immutable external archive SPI for alerts (do not conflate with
    `I-2026-06-28-audit-archive-export-spi` for audit log batches).
  - Purging **OPEN** alerts.
  - Pulling this into the September 2026 R1 critical path.
  - Changing raise-or-touch / auto-resolve semantics.

## Key assumptions

- R1 and near-term EXP1 volumes do not force purge; capturing the debt now is enough.
- Resolve-by-UPDATE remains the primary lifecycle; purge is a **second lifecycle** for aged
  resolved rows only.
- Any DELETE grant must stay admin-scoped and justified by a named retention job — not a general
  “delete alert” operator API unless grill proves otherwise.

## Risks and exceptions

- Premature purge could erase operator-visible evidence that operators still need for recent
  incidents — retention window must be conservative and configurable.
- Coupling alert purge to audit-log archive SPI would over-scope; keep them separate unless a later
  design pack unifies retention vocabulary.
- Accidental complexity: avoid a second soft-delete flag if hard purge of aged `RESOLVED` rows is
  enough.

## Promotion notes

Keep `captured` / low urgency (`P3`). Move to `incubating` when a real volume or operator-hygiene
signal appears (e.g. EXP1 soak, multi-month resolved backlog). Promote to `ready` + `TB-*` only
after grill of: retention window, who may purge, audit of purge, and exact grant change for
`ezkey_admin`.

## Links

- Surfaced during: operator review of `ezkey_admin` grants on `ezkey_alert` after role split
- Role matrix: [`docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md`](../../../../docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md)
  (`ezkey_alert` — Resolve = UPDATE; no hard DELETE)
- Alerts model: [`docs/ALERTS.md`](../../../../docs/ALERTS.md)
- Related TB: [`../TB-2026-07-16-postgresql-application-role-split.md`](../TB-2026-07-16-postgresql-application-role-split.md)
- Related idea (DB roles): [`I-2026-0021-postgresql-application-role-permissions-matrix.md`](I-2026-0021-postgresql-application-role-permissions-matrix.md)
- Sibling retention theme (audit, not alerts): [`I-2026-06-28-audit-archive-export-spi.md`](I-2026-06-28-audit-archive-export-spi.md)
- Code: `org.ezkey.alert.service.AlertService` (`resolveByDedupeKey`; no delete path)
