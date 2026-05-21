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

Add **two** Dashboard widget groups for **Global Admin only**:

1. **System / integrity** — checkpoint scheduler, nightly integrity validation (`I-2026-0006`); normative center of historical trust.
2. **Other operational jobs** — e.g. re-encryption (initial R1 set per [`I-2026-0022`](I-2026-0022-admin-api-scheduled-jobs-catalog.md) catalog).

Generalized **batch last-run** model: table row per job (`last_execution_at`, `last_status`, **`last_run_scope`** e.g. validated window). Dashboard API section feeds widgets. Integrity jobs may use STANDOUT styling within the system/integrity widget.

**Dashboard-level banner** when open actionable alerts exist: shows **count** (e.g. « 3 resolutions pending ») and links to the **existing alerts screen** — no duplicate alert UI on widgets.

## Problem and value

- **Problem:** Integrity and batch health invisible; operators need 3-second « ok or not ok » with honest scope (C9/D5/D6 grilling). See [`operator-alignment-guide.md`](../../operator-alignment-guide.md).
- **Expected value:** Self-contained operational picture without Grafana; supports `I-2026-0005` / `I-2026-0006`.

## Scope

- **In scope (R1):**
  - Batch last-run persistence: `last_execution_at`, `last_status` (`SUCCESS` / `FAILED` / `NEVER_RUN`), **`last_run_scope`** (human-readable window validated).
  - Active **config summary** on widget (e.g. lookback 60 min, retroactive 24 h) from known properties.
  - **NEVER_RUN** — grey badge; row visible once job is registered.
  - Two widgets (layout B); badges pattern consistent with existing dashboard.
  - **Global Admin only** — Tenant Admin does not see integrity/alert ops widgets.
  - Banner: open alert **count** → navigate to alerts list / resolution flow.
  - Widget click: deep-link to **appropriate resolution context** (alerts or integrity/checkpoints) when intuitive — same pattern as entity widgets → filtered views.
  - Honest copy: derived convenience data; ad hoc integrity check if tampering suspected.
- **R1 batch rows (minimum):** checkpoint scheduler, nightly integrity validation, re-encryption — expand via `I-2026-0022` catalog.
- **Out of scope:**
  - Alert when batch stale (C9 — widget only).
  - Snooze UI (Global Admin action elsewhere).
  - Run history timeline.
  - Tenant Admin visibility.

## Grilling decisions (2026-05-19)

| Topic | Decision |
|-------|----------|
| **Layout** | **B** — two widgets: system/integrity + other jobs |
| **Scope on widget** | **R1 mandatory** — last run + status + scope |
| **Never run** | Grey **Never run** badge, row visible |
| **Open alerts** | **Dashboard banner** with **count**, link to alerts screen |
| **Roles** | **Global Admin only** |
| **Config line** | **R1 yes** — active parameters on widget |
| **Click** | Deep-link to alerts / integrity when intuitive |
| **3-second test** | Ok or not ok; if not, obvious path to act |

## Key assumptions

- [`I-2026-0022`](I-2026-0022-admin-api-scheduled-jobs-catalog.md) will provide authoritative job list for registry and copy.
- Alert screen already exists; banner is routing only.

## Promotion notes

D6 express grilling complete. Promote to `TB-*` with `I-2026-0006` + batch table schema. Design pack: wire banner to alert count API.

## Links

- Grill session: [`../grill-sessions/integrity-cluster-D4-D6-grill-me.md`](../grill-sessions/integrity-cluster-D4-D6-grill-me.md) (D6, cluster closed)
- [`../../operator-alignment-guide.md`](../../operator-alignment-guide.md)
- Related: `I-2026-0005`, `I-2026-0006`, `I-2026-0022`
- Vision: `V-2026-0004`
