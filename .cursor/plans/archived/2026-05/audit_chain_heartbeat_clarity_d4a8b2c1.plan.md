---
name: Audit chain heartbeat clarity
overview:
  Sharpen observable semantics for audit-chain heartbeat supervision (checkpoint window, grace,
  fail-close timing), startup configuration sanity, peripheral degraded-mode logging, and aligned
  documentation—building on the core heartbeat model in audit_chain_heartbeat_20b04a41.
todos:
  - id: map-heartbeat-timing
    content:
      Confirm effective clean-start timing model across Admin, Auth, and Integration (windowMinutes,
      grace windows, stop-before-next-window → ~5m / ~9m story with defaults).
    status: completed
  - id: startup-and-transition-logs
    content:
      Add shared-core startup validation for collapsed thresholds + transition-only INFO phase logs;
      peripheral WARN before fail-closed throws with correlation fields.
    status: completed
  - id: peripheral-handlers
    content:
      Auth and Integration GlobalExceptionHandlers inject guard service; richer WARN on heartbeat 503.
    status: completed
  - id: docs-and-config
    content:
      Align AUDIT_LOG_INTEGRITY, CONFIGURATION, ALERTS, ENDPOINT, error inventory, docker README with
      clarified semantics.
    status: completed
  - id: admin-ui-copy
    content:
      Minimal EN/FR audit-logs locales for incidents/help where backend model needed operator clarity.
    status: completed
  - id: tests-validation
    content:
      Expand AuditChainHeartbeatGuardServiceTest; MVC/handler tests use mocked guard bean; note
      clean-start manual scenario for degraded mode.
    status: completed
isProject: false
archived_month: "2026-05"
completed_at: "2026-05-02"
---

> **Plan status:** **Completed** — archived under `.cursor/plans/archived/2026-05/`. Implements
> operator-facing clarity and logging on top of the original heartbeat dossier
> [.cursor/plans/archived/2026-04/audit_chain_heartbeat_20b04a41.plan.md](../2026-04/audit_chain_heartbeat_20b04a41.plan.md).
> Operational UX follow-ups may still reference
> [.cursor/plans/audit_integrity_operational_ux_closeout_notes.plan.md](../../audit_integrity_operational_ux_closeout_notes.plan.md).

# Audit chain heartbeat clarity

## Scope

Close the gap between **implemented** heartbeat behavior (DB-backed latest checkpoint vs clock,
fail-closed on selected peripheral POSTs, incidents/alerts) and what operators and integrators can
**infer** from logs, incidents text, and configuration docs—without changing the fundamental product
model defined in Plan `audit_chain_heartbeat_20b04a41`.

Non-goals:

- Replacing cryptographic gap semantics with operational incidents as the chain contract.
- New Admin API endpoints in this slice (beyond documentation and observability refinements tied to
  existing types).

## Product / timing narrative (canonical)

With defaults aligned across services (`window-minutes=5`, `grace-windows≥1`,
`stop-before-next-window≈PT1M`):

- Completed checkpoints behave as an **Admin-scheduled heartbeat** into shared persistence.
- Peripherals read **`findLatest()`** (cached briefly), compare **UTC now** to **`windowEnd`**, then
  derive phases: OK → brief **unsupervised** window → **degraded / fail-closed** on gated routes
  (Integration: `POST /api/v1/auth-attempts`; Auth: pending-claim path as per interceptor rules).
- The **~5m / ~9m** explanation in docs corresponds to **one window past `windowEnd` before
  staleness onset**, plus **grace windows × window length**, minus **`stop-before-next-window`**
  before peripheral fail-close—adjusted when configuration collapses the unsupervised wedge (logged
  at startup when detected).

## Delivered changes (references)

**Core**

- [`ezkey-core/src/main/java/org/ezkey/audit/integrity/AuditChainHeartbeatGuardService.java`](../../../../ezkey-core/src/main/java/org/ezkey/audit/integrity/AuditChainHeartbeatGuardService.java) —
  startup diagnostics, collapsed-threshold commentary, phase **transition-only** INFO logs,
  incidents/alerts sync unchanged semantically.
- [`ezkey-core/src/main/java/org/ezkey/audit/integrity/AuditChainHeartbeatPeripheralInterceptor.java`](../../../../ezkey-core/src/main/java/org/ezkey/audit/integrity/AuditChainHeartbeatPeripheralInterceptor.java) —
  WARN with structured fields immediately before degraded exception on blocked POSTs.
- [`ezkey-core/src/main/java/org/ezkey/audit/integrity/AuditChainHeartbeatProperties.java`](../../../../ezkey-core/src/main/java/org/ezkey/audit/integrity/AuditChainHeartbeatProperties.java) —
  documentation fix for `enabled=false` behavior.
- [`ezkey-core/src/test/java/org/ezkey/audit/integrity/AuditChainHeartbeatGuardServiceTest.java`](../../../../ezkey-core/src/test/java/org/ezkey/audit/integrity/AuditChainHeartbeatGuardServiceTest.java)

**Peripheral APIs**

- [`ezkey-auth-api/src/main/java/org/ezkey/exception/GlobalExceptionHandler.java`](../../../../ezkey-auth-api/src/main/java/org/ezkey/exception/GlobalExceptionHandler.java)
- [`ezkey-integration-api/src/main/java/org/ezkey/integration/api/exception/GlobalExceptionHandler.java`](../../../../ezkey-integration-api/src/main/java/org/ezkey/integration/api/exception/GlobalExceptionHandler.java)
- Admin API ticker context: [`ezkey-admin-api/src/main/java/org/ezkey/admin/scheduling/AuditChainHeartbeatEvaluateTicker.java`](../../../../ezkey-admin-api/src/main/java/org/ezkey/admin/scheduling/AuditChainHeartbeatEvaluateTicker.java)

**Docs / operator surfaces**

- [`docs/AUDIT_LOG_INTEGRITY.md`](../../../../docs/AUDIT_LOG_INTEGRITY.md), [`docs/ALERTS.md`](../../../../docs/ALERTS.md), [`docs/ENDPOINT.md`](../../../../docs/ENDPOINT.md),
  [`docs/admin-ui-admin-api-error-inventory.md`](../../../../docs/admin-ui-admin-api-error-inventory.md),
  [`ezkey-core/CONFIGURATION.md`](../../../../ezkey-core/CONFIGURATION.md), [`docker/README.md`](../../../../docker/README.md)

**Admin UI (minimal copy)**

- [`ezkey-admin-ui/src/locales/en/audit-logs.json`](../../../../ezkey-admin-ui/src/locales/en/audit-logs.json),
  [`ezkey-admin-ui/src/locales/fr/audit-logs.json`](../../../../ezkey-admin-ui/src/locales/fr/audit-logs.json)

## Validation notes

- Automated: expanded unit coverage on guard + existing peripheral handler test waves with mocked
  `AuditChainHeartbeatGuardService`.
- Manual (recommended): clean-start stack; force or simulate stalled checkpoints; confirm phase
  transitions in logs, 503 problem type on gated routes, incident/alert lifecycle, and recovery when
  Admin advances checkpoints again.

## Checkstyle / imports

Peripheral `GlobalExceptionHandler` classes must keep **blank line between `java.*`/`jakarta.*` import
block and `org.*` block**, and public constructors need a **summary sentence** before `@param` (
`ImportOrder` + `SummaryJavadoc`).
