# Backlog Idea — `I-2026-0003` High-availability Docker stack: parity review with `cleanstart.sh`

## Metadata

- **ID:** `I-2026-0003`
- **Status:** `incubating`
- **Priority:** `P2`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-05-19`
- **Last reviewed at:** `2026-05-19`
- **Phase tags:** `P1-operability`, `P3-distribution`
- **Component tags:** `infra`, `admin-api`, `auth-api`, `integration-api`
- **Captured by:** Marc

## Intent

Review and refresh the high-availability Docker stack (one or two `admin-api`, two `integration-api`, two `auth-api` instances) to confirm functional parity with the daily-driver `cleanstart.sh` baseline and restore reliability and simplicity.

## Problem and value

- **Problem:** The HA mode has not been exercised for a while. There is a real risk of drift relative to the `cleanstart.sh` baseline (functional behavior, configuration defaults, observable status). Without periodic exercise it becomes silent infrastructure debt that erodes the credibility of the HA story.
- **Expected value:** Restore HA mode as a trustworthy reference deployment for scaling discussions and the hardening narrative; keep it close enough to `cleanstart.sh` that operators can expect equivalent functional behavior with multi-instance characteristics layered on top.

## Grilling decisions (2026-05-19)

See [`../grill-sessions/blitz-2026-05-08-1-D1-D2-grill-me.md`](../grill-sessions/blitz-2026-05-08-1-D1-D2-grill-me.md).

- **Goal:** fix blockers until HA matches baseline behavior (not inventory-only).
- **Topology:** **2×** Admin API, **2×** Integration API, **2×** Auth API (ShedLock + parallelism validation).
- **Tests:** functional suite stays sequential; no HA-specific test harness — validate via `clean-start.sh --ha` + same functional expectations.
- **Narrative:** short deployment-profile doc for local HA demos (`V-2026-0010`); QA + internal scalability story.
- **Alignment:** full parity with baseline; only modest Caddy/proxy glue expected.

## Scope

- **In scope:**
  - Run HA from clean checkout (`clean-start.sh --ha`, `docker/start-ha.sh`); fix blockers until parity with default `clean-start.sh`.
  - Mandatory **2+2+2** API topology behind HAProxy.
  - Validate ShedLock, heartbeat/checkpoints, schedulers under multi-instance load.
  - Brief deployment profile note (HA local demo / QE) linked from `V-2026-0010`.
  - Representative resilience checks (restart one replica, etc.) as needed to close blockers.
- **Out of scope:**
  - New HA product features (cross-region, cloud failover).
  - Parallel or HA-flavored functional test suite options.
  - Full production HA runbook (later profile work).

## Key assumptions

- `ezkey-tests/clean-start.sh` (no flags) remains the daily baseline; HA is a strict superset for local Docker.
- Prior HA work means drift should be small; prefer **fix and align** over documenting accepted gaps.

## Risks and exceptions

- If a gap is architectural (not glue), spin a separate idea rather than widening this slice.
- Dual Admin API + checkpoint heartbeat may surface edge cases already covered by integrity cluster docs — cross-link, do not re-grill.

## Promotion notes

Ready for **execution pass**: run `--ha`, fix compose/Caddy/keys, smoke critical paths, add profile doc stub.

## Links

- Deployment profiles: `V-2026-0010`
- Grill: `../grill-sessions/blitz-2026-05-08-1-D1-D2-grill-me.md`
- Related principles: `#1` (simplicity), `#5` (operator-first)
