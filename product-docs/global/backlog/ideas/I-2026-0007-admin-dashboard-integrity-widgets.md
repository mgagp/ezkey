# Backlog Idea — `I-2026-0007` Admin Dashboard: batch health and integrity widgets

## Metadata

- **ID:** `I-2026-0007`
- **Status:** `incubating`
- **Priority:** `P1`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-05-19`
- **Last reviewed at:** `2026-05-19`
- **Phase tags:** `P1-operability`, `P2-hardening`
- **Component tags:** `admin-ui`, `admin-api`

## Intent

Add Dashboard widgets that give the Global Admin an honest, self-contained operational picture of **background batch health** and **integrity validation**, without building Grafana/PRTG inside Ezkey. Generalized model: each registered batch reports **last execution time** and **last status** (success / failed) into a simple persistence row; the Dashboard API exposes a section consumed by one or two widgets. The **integrity validation batch** may be visually emphasized (STANDOUT) relative to other batches (checkpoints, re-encryption, etc.).

When **open resolution alerts** exist, widgets must **not** imply global “all green” — show a proximate caveat (e.g. resolution actions pending) even if the latest batch run succeeded.

## Problem and value

- **Problem:** Integrity work is invisible on the primary operator surface. Stale or failed batches must not require “alerts on alerts” (rejected in C9 grilling). Operators also need to know that recent batch success does not mean all integrity ruptures are resolved.
- **Expected value:** Daily spot-check confidence; clear last-run truth; supports `I-2026-0005` / `I-2026-0006` without external observability stack; aligns with `#1` simplicity, `#5` operator-first, `#12` security posture.

## Scope

- **In scope:**
  - **Batch last-run table** (single-purpose, generalized): per batch job identifier — `last_execution_at`, `last_status` (at minimum `SUCCESS` / `FAILED`; design may distinguish “never run”).
  - Each batch updates its row at end of run (try/catch around job → FAILED on exception).
  - **Dashboard API** section listing all registered batches with last run + status.
  - **One or two widgets** using existing health-badge pattern; integrity batch widget may be STANDOUT.
  - **Open-alert caveat** on widget(s) when actionable integrity/heartbeat/manipulation alerts are open (C9-10).
  - Honest copy: operational reporting, not cryptographic proof; on-demand integrity check remains available if operator suspects tampering of report rows.
- **Out of scope:**
  - Alerts when a batch has not run recently (explicitly rejected — C9).
  - Escalation ladders, snooze UI (snooze is Global Admin action elsewhere — `I-2026-0005` cluster).
  - Full history timeline (R1 = last run per batch only).
  - Generic multi-tenant observability platform.

## Grilling decisions (2026-05-19)

| Topic | Decision |
|-------|----------|
| **C9 stale batches** | Widget last-run replaces alert-on-missing-batch |
| **Failed vs absent** | Include in design pack when feasible |
| **Open alerts** | Widget caveat when resolutions pending |
| **Priority** | Raised to **P1** — high leverage for integrity cluster |

## Key assumptions

- Batch jobs are a bounded, enumerable set in Admin API for R1.
- Derived status rows are convenience data (light integrity acceptable per D6 blitz); primary trust remains audit chain + validator.
- `I-2026-0006` integrity batch registers in the same table as checkpoint and other jobs.

## Risks and exceptions

- Tampering with last-run rows is possible but low impact if copy is honest; validator re-run disproves false comfort.
- Widget must not contradict open alert queue state.

## Promotion notes

Moved to `incubating` after C9 grilling. Promote to `TB-*` with `I-2026-0006` once batch registry list and table schema are fixed. Design pack should define batch job registry and Dashboard contract together.

## Links

- Grill session: [`../grill-sessions/integrity-cluster-D4-D6-grill-me.md`](../grill-sessions/integrity-cluster-D4-D6-grill-me.md) (C9)
- Related vision: `V-2026-0004`
- Related backlog: `I-2026-0005`, `I-2026-0006`
- Related features: `F-admin-ui-workflows`, `F-audit-chain`
- Related principles: `#1`, `#2`, `#5`, `#10`, `#12`, `#13`
