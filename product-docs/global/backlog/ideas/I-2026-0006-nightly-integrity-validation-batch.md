# Backlog Idea — `I-2026-0006` Nightly retroactive integrity validation batch

## Metadata

- **ID:** `I-2026-0006`
- **Status:** `captured`
- **Priority:** `P2`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-05-08`
- **Last reviewed at:** `2026-05-08`
- **Phase tags:** `P2-hardening`
- **Component tags:** `admin-api`, `audit`, `infra`

## Intent

Introduce a periodic (typically nightly) retroactive integrity validation batch covering a fixed configurable time window, to complement the existing 1-hour rolling-window mechanism. The batch validates audit-row HMAC integrity and checkpoint chain continuity over the retroactive window, raises an integrity-break incident (`I-2026-0005`) on any failure, and persists a status record consumable by the dashboard widget (`I-2026-0007`).

## Problem and value

- **Problem:** The current rolling 1-hour window covers transient outages but leaves a blind spot for integrity tampering that becomes visible only after the rolling window has passed (for example, an audit row deleted three days ago). On-demand validation exists but is not surfaced proactively. Without a periodic retroactive sweep, undetected tampering can persist indefinitely.
- **Expected value:** Proactive, predictable, low-noise security posture for the audit chain; clear status visible to the operator each morning; consistency with the `V-2026-0004` strategy.

## Scope

- **In scope:**
  - Configurable schedule (default: nightly, off-peak).
  - Configurable fixed time window (default to be picked, for example seven days).
  - Validation of audit HMAC integrity and checkpoint chain continuity within the window.
  - On detected break, trigger the incident model from `I-2026-0005`.
  - Register in the **generalized batch last-run table** (see `I-2026-0007`): `last_execution_at`, `last_status` per batch job — integrity batch is one row among Admin API jobs.
  - Documentation update describing default schedule, default window, and configuration knobs.
- **Out of scope:**
  - Volume-based effort modulation (explicitly **not retained for V1** — fixed configurable window is sufficient given stable real-world traffic profiles).
  - Generic background-process status persistence beyond this single use case (single-purpose first, generalize later if a second use case emerges — `Design Principle #2`).
  - Dashboard widget UI itself (covered by `I-2026-0007`).

## Key assumptions

- Operators know their traffic profile and can tune the fixed window directly. Real installations do not swing from one to one million authentications per day overnight, so adaptive modulation adds complexity without proportional benefit at V1.
- Single-purpose status persistence is acceptable; if a second retroactive validator emerges later, generalize then.

## Risks and exceptions

- Run cost on very large databases must remain bounded. The fixed window provides a natural cap, but a default that scales reasonably must be chosen.
- Failure during the batch itself (process crash, DB hiccup) must not corrupt the status row; treat it as a fresh write per run.

## Promotion notes

Move to `incubating` once `V-2026-0004` is stable and the schema for the status row is decided. Promote to `TB-*` once the V-* parent is firm, with a thin slice (default schedule, default window, single-purpose table, no UI).

## Links

- Related vision: `V-2026-0004` (integrity validation strategy)
- Related backlog: `I-2026-0005` (integrity break remediation), `I-2026-0007` (dashboard widget)
- Related feature: `F-audit-chain`
- Related principles: `#1` (simplicity), `#2` (essential vs accidental complexity), `#12` (security as posture)
