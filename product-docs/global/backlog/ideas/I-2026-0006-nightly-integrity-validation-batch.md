# Backlog Idea — `I-2026-0006` Nightly retroactive integrity validation batch

## Metadata

- **ID:** `I-2026-0006`
- **Status:** `incubating`
- **Priority:** `P1`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-05-19`
- **Last reviewed at:** `2026-05-19`
- **Phase tags:** `P2-hardening`
- **Component tags:** `admin-api`, `audit`, `infra`

## Intent

Introduce a periodic (typically **daily**) retroactive integrity validation batch over a **fixed configurable window** (default **24 hours**), complementing the rolling checkpoint scheduler. The rolling layer **attaches** what is observed in its lookback window without promising manipulation detection during that window. The nightly batch **detects** anomalies: every audit HMAC valid and checkpoint chain continuous; any failure triggers the standard alert model (`I-2026-0005`). Register last run + status in the generalized batch table (`I-2026-0007`).

## Problem and value

- **Problem:** Rolling lookback (default 60 min) covers mini-outages by chaining checkpoints but leaves a blind spot for tampering older than the lookback or manipulation during the attach window that is only caught later. On-demand validation exists but is not proactive.
- **Expected value:** Predictable daily detection aligned with operator spot-check cadence; normative honesty about rolling vs detective layers; consistency with `V-2026-0004`.

## Scope

- **In scope:**
  - Configurable schedule (default: once per 24 h, off-peak; cron/property).
  - Configurable fixed retroactive window (default **24 h**).
  - Validate **both** per-audit HMAC integrity and checkpoint chain continuity in window.
  - On failure → alert per C7 (`I-2026-0005`); skip periods already resolved via gap/heartbeat/manipulation conciliation.
  - Batch last-run row: `last_execution_at`, `last_status` (`SUCCESS` / `FAILED`); batch failure does **not** emit rupture alert (C9).
  - No per-run audit count cap in R1 — full window scan; scale limits deferred per **Design Principle #14** (beautiful problems).
  - Document rolling vs nightly contract and config bounds for rolling lookback (see grill session D5).
- **Out of scope:**
  - Volume-based effort modulation (not R1).
  - Weekly/monthly mega-windows.
  - Alert when batch has not run (widget only — `I-2026-0007`).
  - Dashboard UI (`I-2026-0007`).

## Grilling decisions (2026-05-19)

| Topic | Decision |
|-------|----------|
| **vs rolling** | Rolling = attach; nightly = detect |
| **Default window** | 24 h |
| **Checks** | Audit HMAC + checkpoint chain |
| **Resolved periods** | Skip in validation |
| **Record cap** | None in R1 |
| **Future** | Email on alert (R2+); not R1 |

## Key assumptions

- Operator reviews dashboard roughly each business day.
- Rolling `lookbackMinutes` remains separate property (default 60; bounds 15 min–8 h enforced).

## Risks and exceptions

- Large audit volume may slow run — beautiful problem, not pre-optimized.
- Tampering older than retroactive window needs ad hoc check.

## Promotion notes

Moved to `incubating` after D5 grilling. Promote to `TB-*` with `I-2026-0007` batch table schema. Close D5-13/14/15 in design pack.

## Links

- Grill session: [`../grill-sessions/integrity-cluster-D4-D6-grill-me.md`](../grill-sessions/integrity-cluster-D4-D6-grill-me.md) (D5)
- Related vision: `V-2026-0004`
- Related backlog: `I-2026-0005`, `I-2026-0007`
- Code reference (rolling attach today): `ezkey-core/.../AuditChainScheduler.java`, `AuditChainProperties.lookbackMinutes`
- Related principles: `#1`, `#2`, `#12`, `#14`
