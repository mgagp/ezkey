# Backlog Idea — `I-2026-0022` Admin API scheduled jobs catalog (living document)

## Metadata

- **ID:** `I-2026-0022`
- **Status:** `incubating`
- **Priority:** `P2`
- **Created at:** `2026-05-19`
- **Updated at:** `2026-05-24`
- **Last reviewed at:** `2026-05-24`
- **Phase tags:** `P1-operability`
- **Component tags:** `admin-api`, `docs`
- **Captured by:** Marc

## Intent

Create and maintain a **canonical catalog** of all scheduled / background jobs centralized in Admin API (and invoked from it), as a living document under `docs/`. The catalog supports operator understanding, dashboard batch widgets (`I-2026-0007`), grill-me sessions, and future batch-registry work — without re-scanning the codebase each time.

## Problem and value

- **Problem:** Scheduled jobs (checkpoints, integrity validation, re-encryption, token cleanup, key rotation, purge, etc.) are important but scattered across Java modules. D6 grilling showed that a pre-built inventory would make widget scope and prioritization trivial (low token cost, better decisions).
- **Expected value:** Single discoverable reference; template per job (intent → why → how → config → errors); easier onboarding for operators and agents; input to batch last-run registry.

## Scope

- **Deliverable path (target):** `docs/ADMIN_API_SCHEDULED_JOBS.md` (linked from `product-docs/components/admin-api/` and `docs/` index if present).
- **In scope — document structure:**
  - Executive summary (how Admin API owns scheduling; ShedLock note).
  - **One section per job** using a stable template:
    - Intent (what it accomplishes in real life)
    - Why (product/security positioning)
    - How (high-level design summary)
    - Implementation notes (key classes, cron property, HA/idempotency)
    - Configuration table (properties, defaults, bounds)
    - Errors and edge cases (brief)
  - Summary table: job id, schedule, module, dashboard widget group (system/integrity vs other).
- **In scope — initial inventory pass (non-exhaustive seed from 2026-05-19 code scan):**
  - `AuditChainScheduler` (checkpoint chain)
  - Planned nightly integrity validation (`I-2026-0006`)
  - `ReencryptionService` / key rotation schedulers
  - `AuditChainHeartbeatEvaluateTicker`
  - `AdminTokenCleanupService`
  - `AuditLifecyclePurgeScheduler`
  - `EnrollmentExpiredCleanupScheduler`
  - `AuthAttemptExpiryScheduler`
  - `PartitionSchedulerService`
  - Others found during exhaustive pass
- **Out of scope:**
  - Auth API / Integration API schedulers (unless Admin-hosted) — note boundary in doc.
  - Implementing new jobs (documentation only in this slice).

## Key assumptions

- Jobs remain primarily in Admin API + `ezkey-core` beans invoked from Admin for R1.
- Document is **English**; updated when jobs are added/removed (same discipline as `CONFIGURATION.md`).

## Grilling decisions (2026-05-19)

See [`../grill-sessions/integrity-cluster-D4-D6-grill-me.md`](../grill-sessions/integrity-cluster-D4-D6-grill-me.md) (D6-3).

## Promotion notes

Move to `ready` when `docs/ADMIN_API_SCHEDULED_JOBS.md` exists with at least all integrity-related jobs fully filled. Link from `I-2026-0007` design pack.

## Links

- Surfaced during: [`../grill-sessions/integrity-cluster-D4-D6-grill-me.md`](../grill-sessions/integrity-cluster-D4-D6-grill-me.md) (D6-3)
- Related backlog: `I-2026-0006`, `I-2026-0007`
- Related guide: [`../../operator-alignment-guide.md`](../../operator-alignment-guide.md)
