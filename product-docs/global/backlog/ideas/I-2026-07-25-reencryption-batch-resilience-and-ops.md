# Backlog Idea — `I-2026-07-25-reencryption-batch-resilience-and-ops` Re-encryption batch resilience and ops hardening

## Metadata

- **ID:** `I-2026-07-25-reencryption-batch-resilience-and-ops`
- **Status:** `incubating`
- **Priority:** `P2`
- **Created at:** `2026-07-25`
- **Updated at:** `2026-07-25`
- **Last reviewed at:** `2026-07-25`
- **Progression markers:** `P2-hardening`, `P3-distribution`
- **Component tags:** `core`, `admin-api`, `admin-ui`, `infra`, `docs`, `security`
- **Lane:** `B`
- **Captured by:** Marc
- **GitHub issue:** none

## Intent

Preserve and converge the next-wave hardening signals for re-encryption batches after sharding
throughput improvements: scheduler resilience, retry semantics, progress observability, and
operator-facing controls.

## Problem and value

- **Problem:** Throughput improvements are in place (auth-attempt shard batches), but resilience and
  operational visibility still rely on behavior that can be confusing under crash/pressure scenarios
  (status transitions, retry semantics, stale in-progress work, page-scoped UI polling).
- **Expected value:** Keep one canonical future lane for high-signal hardening items so future
  sessions do not rediscover them from historical plans.

## Scope

- **In scope:**
  - Scheduler behavior for `IN_PROGRESS` and stale-work handling (policy or watchdog).
  - Explicit semantics for `autoRetryFailed` (wire or remove; no dead config ambiguity).
  - Progress visibility expectations when long-running processing commits are delayed by current
    transaction boundaries.
  - Admin UI operational ergonomics for batches-only refresh and polling when active work is
    off-page.
  - Ops documentation for crash recovery and intervention playbooks.
- **Out of scope:**
  - Replacing the current cryptographic format or rotation model.
  - Reversing sharding decisions already delivered.
  - Broad architecture replacement (new queue platform, new orchestrator stack) without measured
    need.

## Baseline context (already delivered)

- Auth-attempt sharding (`auth-attempt-shard-count`, shard batches, per-shard runner mutex) is
  delivered and documented.
- Re-encryption list/filter surfaces and shard visibility are present in Admin API/UI.
- Current behavior should be treated as the baseline to harden, not as a blank slate redesign.

## Candidate hardening tracks retained

1. Scheduler/work-state resilience (`IN_PROGRESS` pickup policy, stale detection).
2. Retry contract (`autoRetryFailed` operationally meaningful or removed).
3. Progress semantics and optional chunk-level visibility improvements.
4. UI operator controls (batches-only refresh, polling strategy for off-page active work).
5. Recovery/ops docs with explicit fail-open/fail-closed behavior for batch processing paths.

## Key assumptions

- The project prefers incremental hardening over heavyweight orchestration redesign.
- Throughput and reliability changes must remain auditable in existing batch entities and APIs.
- Operational posture remains self-hosted and pragmatic; controls should be proportionate.

## Risks and exceptions

- Hardening without preserving clear status semantics can increase operator confusion.
- Over-engineering this lane could add accidental complexity without measurable reliability gains.
- UI-only polish without scheduler semantics can hide true recovery limitations.

## Promotion notes

Keep this idea `incubating` until:

- scheduler and retry decisions are explicit,
- operator recovery path is codified in docs,
- first bounded implementation slice is promoted to a `TB-*`.

## Links

- Retrofit source capture:
  [`R-2026-07-25-auth-attempt-reencrypt-sharding`](../../legacy-retrofit/R-2026-07-25-auth-attempt-reencrypt-sharding.md)
- Re-encryption operations reference:
  [`docs/REENCRYPTION_OPERATIONS.md`](../../../../docs/REENCRYPTION_OPERATIONS.md)
- Related indexed-discovery lane:
  [`I-2026-0029-reencryption-indexed-encryption-key-id-columns`](I-2026-0029-reencryption-indexed-encryption-key-id-columns.md)
- Prior async trigger lane (done):
  [`I-2026-0002-reencryption-batch-async-button`](I-2026-0002-reencryption-batch-async-button.md)