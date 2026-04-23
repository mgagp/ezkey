# Alerts (minimal subsystem)

Ezkey ships a deliberately small, operator-facing alert subsystem in the Admin API. Its goal is to give
Global Admins a single place to see and triage operational signals raised by the platform — without
re-using the immutable audit chain as an alert queue.

## Concepts

- An **alert** represents an instance-level operational signal (for example, an undeclared audit-chain gap).
- Alerts have an **alertType**, a **severity** (`INFO` | `WARNING` | `CRITICAL`), a **status** (`OPEN` | `RESOLVED`),
  a **dedupeKey** (used to deduplicate while open), an opaque JSON **payload** (alert-type-specific),
  an **occurrenceCount**, and timestamps for `createdAt`, `lastSeenAt`, and (when applicable) `resolvedAt`.
- While an alert is `OPEN`, identical conditions **touch** the existing alert (incrementing
  `occurrenceCount` and `lastSeenAt`) instead of creating duplicates.
- Alerts are **auto-resolved** by their natural resolution path. For `AUDIT_CHAIN_GAP_PENDING`,
  declaring the matching gap (`POST /api/v1/audit-logs/lifecycle/declare-gap`) resolves the alert with
  reason `GAP_DECLARED`.
- Alert lifecycle actions emit audit log entries (`ALERT_RAISED` on first creation, `ALERT_RESOLVED`
  on resolution) so the audit chain still records what happened — but the alert itself is **not** an
  audit row.

## Storage

Alerts live in `ezkey_alert` (Flyway `V11__alerts_minimal_subsystem.sql`). A partial unique index
`uq_alert_dedupe_key_open` enforces "at most one OPEN alert per `dedupeKey`", which is the contract
that makes "raise or touch" safe under concurrency. A race fallback in `AlertService.raiseOrTouch`
catches `DataIntegrityViolationException` and re-reads the existing OPEN row.

## API

All endpoints require Global Admin (`GLOBAL_ADMIN`). Tenant Admins receive `403`.

- `GET /api/v1/alerts` — paginated list with optional filters: `status`, `alertType`, `severity`,
  `dedupeKey`, `createdAfter`, `createdBefore`. Default sort: `createdAt,desc`. Default page size: 20.
- `GET /api/v1/alerts/{alertId}` — single alert detail.

The Admin UI exposes both via the **Alerts** page (Global Admin only) at `/alerts` and a detail
view at `/alerts/{alertId}`. The Dashboard alerts widget links into the same pages.

## Producers

Today the only producer is the audit-chain scheduler:

- `AUDIT_CHAIN_GAP_PENDING` — raised by `AuditChainScheduler` when an undeclared gap is detected
  before the lookback window. Severity: `WARNING`. `dedupeKey = "AUDIT_CHAIN_GAP_PENDING:" + anchorCheckpointId`.
  Payload (JSON string): `anchorCheckpointId`, `gapStart`, `estimatedGapEnd`, `estimatedGapMinutes`,
  `message`.

`AuditLifecycleService.declareGap` resolves the matching open alert by dedupe key when a gap is
formally declared.

## Adding a new alert type

1. Add a value to `org.ezkey.alert.domain.AlertType`.
2. Pick a stable `dedupeKey` shape (`"<TYPE>:<naturalId>"` is the convention).
3. From the producer site, call `alertService.raiseOrTouch(type, severity, dedupeKey, payloadJson)`.
4. Wire the resolution path: when the underlying condition is addressed, call
   `alertService.resolveByDedupeKey(dedupeKey, reason, adminId)`.
5. (Admin UI) Add a translation under `alerts:type.<TYPE>` and, if the payload deserves a structured
   view, extend the alert detail page with a typed renderer (see the `AUDIT_CHAIN_GAP_PENDING`
   renderer for a template).

## Why a dedicated alert table

The audit chain is an append-only, integrity-protected log of past events. Operator signals like
"an undeclared gap is currently pending" are mutable, deduplicated, and resolvable — semantics that
do not fit an immutable chain. Keeping alerts in their own table also lets the Admin UI offer a
direct "what needs my attention right now" view without mixing it with the historical audit feed.
