# Tracer Bullet Brief — `TB-2026-06-28` Nightly retroactive integrity validation batch

## Metadata

- **ID:** `TB-2026-06-28-nightly-integrity-validation-batch`
- **Status:** `ready`
- **Related idea:** `I-2026-0006-nightly-integrity-validation-batch`
- **Parent context:** Wave B integrity cluster; design pack
  [`integrity-cluster-design-pack.md`](../../integrity-cluster-design-pack.md)
- **Feature:** `F-audit-chain`
- **Lane:** `A` (admin-api / core scheduler + alert producer)
- **Posture:** `single-pass` for B1 scope; manipulation remediation deferred to B2 (`I-2026-0005`)
- **GitHub issue:** #269 (Wave B program umbrella)
- **Created at:** `2026-06-28`

## Objective

Deliver the **detective layer** (`V-2026-0004`): a scheduled nightly job that retroactively validates
audit HMAC integrity and checkpoint chain continuity over a configurable window (default **24 h**),
records **last-run status** in a shared job registry, and raises an operator alert on integrity
rupture — without blocking on dashboard widgets (`I-2026-0007`, B3).

## Current state

- **Rolling attach:** `AuditChainScheduler` — shipped.
- **Verification engine:** `AuditChainVerificationService.verifyChain(from, to)` — shipped.
- **Alerts:** gap + heartbeat types — shipped; **no** integrity-rupture type yet.
- **Batch last-run table:** **not** shipped.

## In scope

1. **Flyway:** `ezkey_scheduled_job_last_run` (or agreed name) + seed rows for R1 jobs.
2. **Properties:** `ezkey.audit.integrity.nightly.*` (`enabled`, `cron`, `window-hours`).
3. **Scheduler:** nightly job with ShedLock; calls verification orchestration over retroactive window.
4. **Skip logic:** exclude resolved gap/heartbeat/manipulation periods (minimal R1: gap + heartbeat;
   extend when B2 lands if needed).
5. **Classifier (C8-6):** do not emit manipulation-family alert when heartbeat explains the window.
6. **Alert type:** `AUDIT_INTEGRITY_RUPTURE` (or equivalent) + JSON payload (fail boundary, summary).
7. **Registry updates:** `SUCCESS` / `FAILED` + `last_run_scope` on each run; checkpoint scheduler
   updates its row on successful tick (lightweight hook in `AuditChainScheduler`).
8. **Audit meta-event** for batch completion.
9. **CONFIGURATION.md** (core + admin-api) for new properties.
10. **Tests:** unit tests for orchestration + registry; integration test on fixture data where practical.

## Out of scope

- Dashboard widgets (`I-2026-0007` / TB B3).
- Full manipulation remediation UI/API (`I-2026-0005` / TB B2).
- Snooze endpoints.
- Alert when nightly job has not run (widget-only per C9).
- Lookback bounds validation (optional small follow-up in same PR if trivial).

## Design decisions (from design pack)

| Decision | Choice |
|----------|--------|
| Window | Default 24 h retroactive |
| Schedule | Cron, default off-peak daily |
| Failure modes | Integrity fail → alert; batch infra fail → registry `FAILED` only |
| Record cap | None (R1) |
| Dev disable | `nightly.enabled=false` allowed in dev profile |

## Implementation sequence

1. Migration + entity/repository for job registry.
2. `NightlyIntegrityValidationService` (orchestration).
3. Scheduler + properties + ShedLock.
4. New `AlertType` + producer wiring + i18n keys for Admin UI raw/typed payload (minimal).
5. Checkpoint scheduler registry hook.
6. Tests + CONFIGURATION.md.

## Validation

- [ ] Maven baseline (root reactor).
- [ ] Unit tests for nightly orchestration and registry.
- [ ] clean-start: scheduler enabled; manual trigger or shortened cron in test profile.
- [ ] Manual lab: induce audit tampering in window → alert after batch (maintainer).
- [ ] Manual lab: Admin API stop → heartbeat path unchanged (regression).

## Links

- Idea: [`ideas/I-2026-0006-nightly-integrity-validation-batch.md`](ideas/I-2026-0006-nightly-integrity-validation-batch.md)
- Design pack: [`../../integrity-cluster-design-pack.md`](../../integrity-cluster-design-pack.md)
- Grill: [`grill-sessions/integrity-cluster-D4-D6-grill-me.md`](grill-sessions/integrity-cluster-D4-D6-grill-me.md)
- Vision: [`../../vision/V-2026-0004-integrity-validation-strategy.md`](../../vision/V-2026-0004-integrity-validation-strategy.md)
- Release compass: [`../../operational-readiness-prioritization-2026-09.md`](../../operational-readiness-prioritization-2026-09.md)
- Runtime alerts: [`../../../docs/ALERTS.md`](../../../docs/ALERTS.md)
