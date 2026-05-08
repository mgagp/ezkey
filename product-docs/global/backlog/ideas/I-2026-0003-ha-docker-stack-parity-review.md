# Backlog Idea — `I-2026-0003` High-availability Docker stack: parity review with `cleanstart.sh`

## Metadata

- **ID:** `I-2026-0003`
- **Status:** `triaged`
- **Priority:** `P2`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-05-08`
- **Last reviewed at:** `2026-05-08`
- **Phase tags:** `P1-operability`, `P3-distribution`
- **Component tags:** `infra`, `admin-api`, `auth-api`, `integration-api`

## Intent

Review and refresh the high-availability Docker stack (one or two `admin-api`, two `integration-api`, two `auth-api` instances) to confirm functional parity with the daily-driver `cleanstart.sh` baseline and restore reliability and simplicity.

## Problem and value

- **Problem:** The HA mode has not been exercised for a while. There is a real risk of drift relative to the `cleanstart.sh` baseline (functional behavior, configuration defaults, observable status). Without periodic exercise it becomes silent infrastructure debt that erodes the credibility of the HA story.
- **Expected value:** Restore HA mode as a trustworthy reference deployment for scaling discussions and the hardening narrative; keep it close enough to `cleanstart.sh` that operators can expect equivalent functional behavior with multi-instance characteristics layered on top.

## Scope

- **In scope:**
  - Run the HA stack from a clean checkout, identify breakage or drift.
  - Compare functional behavior against the `cleanstart.sh` baseline (auth, enrolment, admin workflows, audit, checkpoints, rate limiting).
  - Fix or document drift; favor simplicity and minimum extra surface relative to the baseline.
  - Confirm reliability across a few representative scenarios (instance restart, loss of one replica).
- **Out of scope:**
  - New HA features (for example, cross-region replication or automatic failover beyond Docker compose level).
  - Production-grade deployment guidance (separate work, future profile-based docs).

## Key assumptions

- `cleanstart.sh` remains the canonical local baseline; HA stack behavior should be a strict superset, not a fork.
- HA characteristics in scope are limited to local Docker multi-instance posture, not full cloud HA topology.

## Risks and exceptions

- Drift may be larger than expected. If so, scope the fix to functional parity and defer non-essential cleanups.
- Multi-instance interactions (heartbeat and checkpoint behavior with two `admin-api` candidates) may surface design questions worth raising as separate items.

## Promotion notes

Move to `triaged` after the first parity gap inventory is captured. Promote pieces requiring design decisions into separate ideas if they emerge.

## Links

- Future link: deployment-profile catalog `V-2026-0002`
- Related principles: `#1` (simplicity), `#5` (operator-first)
