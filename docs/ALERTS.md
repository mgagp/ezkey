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
- Alerts are **auto-resolved** by their natural resolution path. For example, `AUDIT_CHAIN_GAP_PENDING`
  resolves via gap declaration (`POST /api/v1/audit-logs/lifecycle/declare-gap`, reason **`GAP_DECLARED`**),
  and `AUDIT_CHAIN_HEARTBEAT_STALE` resolves automatically when checkpoints advance again (reason **`HEARTBEAT_RESTORED`**).
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

Producers include **automated lifecycle checks** (`AuditChainScheduler`) and **checkpoint heartbeat supervision** (`AuditChainHeartbeatGuardService`):

| Alert type | Raised by | `dedupeKey` | Severity | Typical resolution |
|------------|-----------|-------------|---------|---------------------|
| `AUDIT_CHAIN_GAP_PENDING` | `AuditChainScheduler` detects an undeclared temporal gap (`window_start`/`window_end` holes) spanning completed audit epochs | `"AUDIT_CHAIN_GAP_PENDING:" + anchorCheckpointId` | `WARNING` | Global Admin declares the gap (`POST /api/v1/audit-logs/lifecycle/declare-gap`). Auto-resolution reason `GAP_DECLARED`. |
| `AUDIT_CHAIN_HEARTBEAT_STALE` | `AuditChainHeartbeatGuardService` observes peripheral-ready fail-closing because Admin API checkpoints stalled past grace semantics | Stable singleton **`AUDIT_CHAIN_HEARTBEAT_STALE`** (see [`AuditChainHeartbeatGuardService`](../ezkey-core/src/main/java/org/ezkey/audit/integrity/AuditChainHeartbeatGuardService.java)) | `WARNING` | A fresh advancing checkpoint restores heartbeat; subsystem auto-resolves with reason `HEARTBEAT_RESTORED`. Operators subsequently close **`RECOVERED_PENDING_DECLARATION`** incidents via lifecycle APIs. |
| `AUDIT_INTEGRITY_RUPTURE` | `NightlyIntegrityValidationService` detects per-entry HMAC or checkpoint chain rupture in the retroactive nightly window | `"AUDIT_INTEGRITY_RUPTURE:" + windowStartEpochMillis` | `CRITICAL` | Remediation flows owned by `I-2026-0005` (Wave B B2). R1 raises/touches only; no auto-resolve in B1. |

`AUDIT_CHAIN_GAP_PENDING` payload (JSON string): `anchorCheckpointId`, `gapStart`, `estimatedGapEnd`, `estimatedGapMinutes`, `message`.

`AUDIT_CHAIN_HEARTBEAT_STALE` payload (JSON string): `phase`, `anchorCheckpointId`, `latestWindowEnd`.

`AUDIT_INTEGRITY_RUPTURE` payload (JSON string): `windowStart`, `windowEnd`, `chainStatus`, `violationCount`, `entryHmacViolationCount`, `failBoundary`, `resumeBoundary`, `message`.

`AuditLifecycleService.declareGap` resolves matching `AUDIT_CHAIN_GAP_PENDING` alerts by anchor dedupe key when a gap is formally declared.

**C8-6 classifier:** when the only chain finding in the nightly window is undeclared gaps and an OPEN
`AUDIT_CHAIN_HEARTBEAT_STALE` alert already explains the outage, B1 defers raising
`AUDIT_INTEGRITY_RUPTURE` for that window.

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
